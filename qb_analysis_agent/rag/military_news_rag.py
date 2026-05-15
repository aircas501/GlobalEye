"""
@Time: 2026/3/30 15:05
@Author: LxpStu
@File: military_news_rag.py
"""
import asyncio
import os
import time
import logging

from dotenv import load_dotenv
from fastapi import UploadFile
from langchain_community.document_loaders import DirectoryLoader, TextLoader
from langchain_core.documents import Document
from langchain_community.embeddings import DashScopeEmbeddings
import aiohttp
from openai import AsyncOpenAI

from kg.milvus_impl import MilvusStorage
from kg.neo4j_impl import Neo4jStorage
from kg.base_storage import BaseStorage

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s: %(message)s"
)

class MilitaryNewsRAG:
    def __init__(self, vector_storage: BaseStorage = None, graph_storage: BaseStorage = None):
        load_dotenv()

        # 初始化向量/图存储，允许外部注入
        self.vector_storage = vector_storage or MilvusStorage()
        self.graph_storage = graph_storage or Neo4jStorage()
        # 存储轮询列表，方便后续接入更多存储
        self.storages = [self.vector_storage, self.graph_storage]

        self.DATA_DIRECTORY = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data2")

        self.embeddings = DashScopeEmbeddings(
            dashscope_api_key=os.getenv("EMBEDDING_API_KEY"),
            model=os.getenv("EMBEDDING_MODEL", 'text-embedding-v4'),
        )

        # Rerank 配置
        self.RERANK_URL = os.getenv("RERANK_BASE_URL", "https://dashscope.aliyuncs.com/compatible-api/v1/reranks")
        self.RERANK_MODEL = os.getenv("RERANK_MODEL", "qwen3-rerank")
        self.RERANK_API_KEY = os.getenv("RERANK_API_KEY")

        # LLM 配置（用于智能去噪）
        self.BASE_URL = os.getenv("LLM_BASE_URL", "https://api.deepseek.com")
        self.MODEL = os.getenv("LLM_MODEL", "deepseek-chat")
        self.LLM_API_KEY = os.getenv("LLM_API_KEY")

        # 初始化 OpenAI 兼容客户端
        self.llm_client = None
        if self.LLM_API_KEY:
            self.llm_client = AsyncOpenAI(
                base_url=self.BASE_URL,
                api_key=self.LLM_API_KEY
            )


    async def retrieve(self, question: str, k: int = 15) -> tuple:
        """
        检索情报
        轮询所有 storage 合并结果
        返回：(拼接后的文本内容, 原始文档列表)
        """
        tasks = [s.search(question, self.embeddings.embed_query, k) for s in self.storages]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        all_docs = []
        for r in results:
            if isinstance(r, list):
                all_docs.extend(Document(page_content=item) for item in r)

        if not all_docs:
            return "未检索到相关情报", []

        all_docs = await self.rerank_documents(question, all_docs)
        retrieve_text = "\n\n---\n\n".join([d.page_content for d in all_docs])
        retrieve_docs = [{"page_content": d.page_content} for d in all_docs]

        return retrieve_text, retrieve_docs
    
    async def rerank_documents(self, query, documents, top_n=10):
        """对 Document 列表进行重排序"""
        if not self.RERANK_API_KEY or not documents:
            return documents

        doc_texts = [d.page_content for d in documents]
        try:
            headers = {
                "Authorization": f"Bearer {self.RERANK_API_KEY}",
                "Content-Type": "application/json"
            }
            payload = {
                "model": self.RERANK_MODEL,
                "query": query,
                "documents": doc_texts,
                "top_n": min(top_n, len(doc_texts)),
                "instruct": "Given a web search query, retrieve relevant passages that answer the query."
            }

            timeout = aiohttp.ClientTimeout(total=30)
            async with aiohttp.ClientSession(timeout=timeout) as session:
                async with session.post(self.RERANK_URL, headers=headers, json=payload) as response:
                    if response.status != 200:
                        error_text = await response.text()
                        logging.error(f"重排序请求失败: {response.status}, 响应: {error_text}")
                        return documents

                    result = await response.json()
                    logging.info(f"重排序返回结果: {result}")

                    reranked_texts = []
                    if "results" in result and isinstance(result["results"], list):
                        for item in result["results"]:
                            index = item.get("index", 0)
                            if 0 <= index < len(doc_texts):
                                reranked_texts.append(doc_texts[index])
                            else:
                                logging.error(f"重排序返回索引越界: {index}")
                    else:
                        logging.error(f"重排序返回格式异常: {result}")
                        return documents

                    if not reranked_texts:
                        logging.error("重排序返回空结果，使用原始排序")
                        return documents

                    text_to_doc = {d.page_content: d for d in documents}
                    reranked = []
                    for text in reranked_texts:
                        reranked.append(text_to_doc.get(text, Document(text)))
                    logging.info(f"重排序结果:{reranked_texts}")
                    return reranked

        except Exception as e:
            logging.error(f"[错误] 重排序异常: {e}")
            return documents

    async def llm_split_text(self, text: str, max_chunk_size: int = 1000) -> list[Document]:
        """
        LLM 智能语义切分
        返回与 text_splitter.split_documents(docs) 相同类型的 Document 对象列表
        """

        prompt = f"""你是专业的文本分析助手，需要完成以下两个步骤：

按照原文进行文本切分
根据你的分析，将文本切分成多个语义完整的段落，要求：
1. 每段文字不超过 {max_chunk_size} 字
2. 句子必须完整，事件必须完整，保留主要事件即可
3. 按照语义边界切分，不要切断正在讨论的话题或事件
4. 你需要在分割好的每段文本前边增加这段文本中描述的事件发生的时间
5. 输出格式：每行一个切分段，不要解释、不要标题、不要序号

请直接输出切分后的文本，每行一段：

文本：
{text}"""
        try:
            response = await self.llm_client.chat.completions.create(
                model=self.MODEL,
                messages=[
                    {"role": "system", "content": "你是一个专业的文本分析助手，擅长分析文本结构、识别主要事件和时间线，并基于语义边界进行文本切分。"},
                    {"role": "user", "content": prompt}
                ],
                temperature=0.1,
                max_tokens=3000
            )

            # 提取内容 + 按行拆分 + 过滤空行
            content = response.choices[0].message.content.strip()
            chunks = [line.strip() for line in content.splitlines() if line.strip()]

            # 将字符串列表转换为 Document 对象列表
            documents = [Document(page_content=chunk) for chunk in chunks]

            logging.info(f"[系统] 基于摘要分析的智能语义切分完成，生成 {len(documents)} 个片段")
            for doc in documents:
                logging.info(f"{doc.page_content}\n")
            return documents
        except Exception as e:
            logging.error(f"[错误] LLM切分失败: {e}")
            # 降级到基本切分
            return [Document(page_content=text)]

    async def get_vector_store(self, force_rebuild: bool = False, max_chunk_size: int = 1000):
        if not os.path.exists(self.DATA_DIRECTORY):
            os.makedirs(self.DATA_DIRECTORY)
            logging.error(f"[错误] 文件夹 '{self.DATA_DIRECTORY}' 不存在，已为你创建。")
            logging.error(f"[提示] 请将关于美伊关系的 .txt 文本文件放入 '{self.DATA_DIRECTORY}' 文件夹中，然后重新运行。")
            return None

        logging.info(f"[系统] 正在扫描文件夹: {self.DATA_DIRECTORY}")
        try:
            loader = DirectoryLoader(
                self.DATA_DIRECTORY,
                glob="**/*.txt",
                loader_cls=TextLoader,
                loader_kwargs={'encoding': 'utf-8'}
            )
            docs = loader.load()
        except Exception as e:
            logging.error(f"[错误] 读取文件失败: {e}")
            return None

        if len(docs) == 0:
            logging.error(f"[错误] '{self.DATA_DIRECTORY}' 文件夹是空的，请放入 .txt 文件后重试。")
            return None

        logging.info(f"[系统] 成功加载 {len(docs)} 个文档。")
        # 使用LLM做智能语义切分
        splits = []
        for i, doc in enumerate(docs):
            logging.info(f"[系统] 正在处理第 {i+1}/{len(docs)} 个文档...")
            doc_chunks = await self.llm_split_text(doc.page_content, max_chunk_size=max_chunk_size)
            splits.extend(doc_chunks)
        logging.info(f"[系统] LLM智能语义切分完成，共生成 {len(splits)} 个片段。")

        texts = [split.page_content for split in splits]
        return await self.vector_storage.build_vector_store(texts, self.embeddings.embed_documents, force_rebuild)

    async def reveice_upload_file_to_vextor(
            self,
            file: UploadFile,  # 直接接收上传的文件
            force_rebuild: bool = False,
            max_chunk_size: int = 1000
    ):
        """
        接收上传的文件 → 直接读取内容 → 语义切分 → 构建向量库
        不再读取本地文件夹！
        """
        try:
            # 1. 直接读取上传文件的内容（内存读取，不写磁盘）
            logging.info(f"[系统] 正在处理上传的文件: {file.filename}")
            file_content = await file.read()  # 异步读取文件二进制
            file_content = file_content.decode("utf-8")  # 转文本

            if not file_content.strip():
                logging.error("[错误] 上传的文件内容为空")
                return None

            # 2. 封装成 LangChain Document
            docs = [
                Document(
                    page_content=file_content,
                    metadata={"source": file.filename}
                )
            ]

            logging.info(f"[系统] 成功加载 1 个文档: {file.filename}")

            # 3. LLM智能语义切分
            splits = []
            for i, doc in enumerate(docs):
                logging.info(f"[系统] 正在处理第 {i + 1}/{len(docs)} 个文档...")
                doc_chunks = await self.llm_split_text(doc.page_content, max_chunk_size=max_chunk_size)
                splits.extend(doc_chunks)

            logging.info(f"[系统] LLM智能语义切分完成，共生成 {len(splits)} 个片段。")

            # 4. 构建向量库
            texts = [split.page_content for split in splits]
            return await self.vector_storage.build_vector_store(
                texts,
                self.embeddings.embed_documents,
                force_rebuild
            )

        except Exception as e:
            logging.error(f"[错误] 处理上传文件并构建向量库失败: {str(e)}")
            return None

    async def receive_text_to_vector(
            self,
            text: str,
            source_name: str = "remote_text",
            force_rebuild: bool = False,
            max_chunk_size: int = 1000
    ):
        """
        接收纯文本 → 语义切分 → 构建向量库
        """
        try:
            logging.info(f"[系统] 正在处理上传的文本，来源: {source_name}")

            if not text.strip():
                logging.error("[错误] 上传的文本内容为空")
                return None

            # 1. 封装成 LangChain Document
            docs = [
                Document(
                    page_content=text.strip(),
                    metadata={"source": source_name}
                )
            ]

            logging.info(f"[系统] 成功加载 1 个文档: {source_name}")

            # 2. LLM智能语义切分
            splits = []
            for i, doc in enumerate(docs):
                logging.info(f"[系统] 正在处理第 {i + 1}/{len(docs)} 个文档...")
                doc_chunks = await self.llm_split_text(doc.page_content, max_chunk_size=max_chunk_size)
                splits.extend(doc_chunks)

            logging.info(f"[系统] LLM智能语义切分完成，共生成 {len(splits)} 个片段。")

            # 3. 构建向量库
            texts = [split.page_content for split in splits]
            return await self.vector_storage.build_vector_store(
                texts,
                self.embeddings.embed_documents,
                force_rebuild
            )

        except Exception as e:
            logging.error(f"[错误] 处理文本并构建向量库失败: {str(e)}")
            return None

async def test_get_vector_store():
    rag = MilitaryNewsRAG(MilvusStorage(), Neo4jStorage())
    await rag.get_vector_store(force_rebuild=False)

if __name__ == "__main__":
    start = time.time()
    asyncio.run(test_get_vector_store())
    print(f"耗时：{time.time() - start}")
    pass




