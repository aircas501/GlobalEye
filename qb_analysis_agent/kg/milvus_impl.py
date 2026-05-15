"""
@Time: 2026/4/17
@Author: LxpStu
@File: milvus_impl.py
"""
import os
import logging

from dotenv import load_dotenv
from pymilvus import connections, utility, exceptions
from pymilvus import Collection, FieldSchema, CollectionSchema, DataType

from kg.base_storage import BaseStorage


class MilvusStorage(BaseStorage):
    """Milvus 向量数据库客户端，封装连接、检索、写入等操作"""

    def __init__(self):
        load_dotenv()

        self.MILVUS_HOST = os.getenv("MILVUS_HOST")
        self.MILVUS_PORT = os.getenv("MILVUS_PORT", "19530")
        self.MILVUS_ALIAS = os.getenv("MILVUS_ALIAS", "default")
        self.COLLECTION_NAME = os.getenv("MILVUS_COLLECTION_NAME", "us_iran_intel_chinese4")

        fields = [
            FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
            FieldSchema(name="text", dtype=DataType.VARCHAR, max_length=65535),
            FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=1024)
        ]
        self.COLLECTION_SCHEMA = CollectionSchema(fields=fields, description="美伊关系情报")

    async def connect(self) -> bool:
        """连接 Milvus"""
        try:
            if connections.has_connection(self.MILVUS_ALIAS):
                try:
                    utility.list_collections(using=self.MILVUS_ALIAS)
                    return True
                except exceptions.ConnectionNotExistException:
                    connections.remove_connection(self.MILVUS_ALIAS)

            logging.info(f"[系统] 连接 Milvus: {self.MILVUS_HOST}:{self.MILVUS_PORT}")
            connections.connect(
                alias=self.MILVUS_ALIAS,
                host=self.MILVUS_HOST,
                port=self.MILVUS_PORT,
                user=os.getenv("MILVUS_USER"),
                password=os.getenv("MILVUS_PASSWORD")
            )
            logging.info("[系统] Milvus 连接成功")
            return True
        except Exception as e:
            logging.info(f"[错误] Milvus 连接失败: {e}")
            return False

    async def search(self, query: str, embed_fn, k: int = 15, **kwargs) -> list[str]:
        """完整搜索流程：自动连接 → 检查集合 → 向量化 → 检索 → 去重

        Args:
            query: 查询文本
            embed_fn: 向量化函数 embed_query(query) → list[float]
            k: 返回条数
            **kwargs: 预留，子类扩展参数

        Returns:
            文本列表
        """
        if not await self.connect():
            return []

        if not self.collection_exists():
            logging.error(f"[错误] 集合 {self.COLLECTION_NAME} 不存在")
            return []

        query_embedding = embed_fn(query)

        collection = self.get_collection()
        collection.load()

        search_params = {"metric_type": "L2", "params": {"nprobe": 10}}
        results = collection.search(
            data=[query_embedding],
            anns_field="embedding",
            param=search_params,
            limit=k,
            output_fields=["text"]
        )
        collection.release()

        seen_texts = set()
        docs = []
        for hits in results:
            for hit in hits:
                text = hit.entity.get("text", "")
                if text and text not in seen_texts:
                    seen_texts.add(text)
                    docs.append(text)
        return docs

    async def build_vector_store(self, texts: list, embed_fn, force_rebuild: bool = False) -> int | None:
        """构建向量库：连接 → 重建集合 → 批量写入 → 建索引

        Args:
            texts: 文本列表
            embed_fn: 批量向量化函数 embed_documents([str]) → [list[float]]
            force_rebuild: 是否强制重建集合

        Returns:
            入库的实体数量，失败返回 None
        """
        if not await self.connect():
            return None

        logging.info(f"[系统] 正在向 Milvus 存入 {len(texts)} 个向量片段...")

        if self.collection_exists() and force_rebuild:
            self.drop_collection()
            self.create_collection()
        elif not self.collection_exists():
            self.create_collection()

        BATCH_SIZE = 10
        for i in range(0, len(texts), BATCH_SIZE):
            batch_texts = texts[i:i + BATCH_SIZE]
            embeddings_list = embed_fn(batch_texts)
            logging.info(f"已处理第 {i // BATCH_SIZE + 1} 批，共处理 {min(i + BATCH_SIZE, len(texts))}/{len(texts)} 条文本")
            logging.info(f"[调试] 嵌入向量数量: {len(embeddings_list)}")
            if embeddings_list:
                logging.info(f"[调试] 第一个嵌入向量维度: {len(embeddings_list[0])}")
            self.insert_batch(batch_texts, embeddings_list)

        self.create_index()

        collection = self.load_collection()
        entity_count = collection.num_entities
        logging.info(f"[系统] 集合 {self.COLLECTION_NAME} 已加载，包含 {entity_count} 条数据")
        logging.info("[系统] 知识库构建完成并已持久化。\n")
        self.release_collection()
        return entity_count

    def insert_batch(self, texts: list, embeddings_list: list):
        """批量插入向量数据"""
        collection = self.get_collection()
        data = [texts, embeddings_list]
        collection.insert(data)
        collection.flush()

    def create_index(self):
        """在 embedding 字段上创建索引"""
        collection = self.get_collection()
        index_params = {
            "index_type": "IVF_FLAT",
            "metric_type": "L2",
            "params": {"nlist": 128}
        }
        collection.create_index(field_name="embedding", index_params=index_params)

    def load_collection(self):
        """加载集合到内存"""
        collection = self.get_collection()
        collection.load()
        return collection

    def release_collection(self):
        """释放集合"""
        collection = self.get_collection()
        collection.release()

    def collection_exists(self) -> bool:
        """检查集合是否存在"""
        return utility.has_collection(self.COLLECTION_NAME, using=self.MILVUS_ALIAS)

    def get_collection(self) -> Collection:
        """获取集合对象"""
        return Collection(name=self.COLLECTION_NAME, using=self.MILVUS_ALIAS)

    def drop_collection(self):
        """删除集合"""
        utility.drop_collection(self.COLLECTION_NAME, using=self.MILVUS_ALIAS)

    def create_collection(self) -> Collection:
        """按 schema 创建集合"""
        return Collection(name=self.COLLECTION_NAME, schema=self.COLLECTION_SCHEMA, using=self.MILVUS_ALIAS)
