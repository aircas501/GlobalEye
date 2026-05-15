"""
远程构建 RAG 知识库路由
"""
from fastapi import APIRouter, UploadFile, File
from pydantic import BaseModel, Field

from kg.milvus_impl import MilvusStorage
from kg.neo4j_impl import Neo4jStorage
from rag.military_news_rag import MilitaryNewsRAG

router = APIRouter()


class BuildRagTextRequest(BaseModel):
    """文本构建 RAG 的请求体"""
    content: str = Field(..., description="要入库的文本内容")
    source_name: str = Field(default="remote_text", description="文本来源名称")
    force_rebuild: bool = Field(default=False, description="是否强制重建向量库")
    max_chunk_size: int = Field(default=1000, description="最大分块大小")


@router.post("/receive/file")
async def receive_file(file: UploadFile = File(...)):
    """接收上传的文件 → 语义切分 → 构建向量库"""
    rag = MilitaryNewsRAG(MilvusStorage(), Neo4jStorage())
    await rag.reveice_upload_file_to_vextor(file=file, force_rebuild=False)
    return {"status": "success"}


@router.post("/receive/content")
async def receive_text(request: BuildRagTextRequest):
    """接收文本 → 语义切分 → 构建向量库"""
    rag = MilitaryNewsRAG(MilvusStorage(), Neo4jStorage())
    result = await rag.receive_text_to_vector(
        text=request.content,
        source_name=request.source_name,
        force_rebuild = False,
        max_chunk_size=1000,
    )
    if result is None:
        return {"status": "error", "msg": "文本入库失败"}
    return {"status": "success", "entity_count": result}
