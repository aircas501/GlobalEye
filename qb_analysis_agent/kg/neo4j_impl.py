"""
@Time: 2026/4/17
@Author: LxpStu
@File: neo4j_impl.py
"""
import logging
import os

import aiohttp
from dotenv import load_dotenv

from kg.base_storage import BaseStorage

class Neo4jStorage(BaseStorage):
    """Neo4j 图数据库存储"""

    def __init__(self):
        load_dotenv()
        self.BASE_URL = os.getenv('LIGHTRAG_HOST_PORT')

    async def search(self, query, embed_fn=None, k=15, **kwargs):
        """图数据库检索

        Args:
            query: 查询文本（对应 label 参数）
            embed_fn: 向量化函数（未使用，保留接口一致）
            k: 返回条数
            **kwargs: 其他图查询参数，如 max_depth=3, max_nodes=1000
        """
        url = f"{self.BASE_URL}/graph/query"
        params = {
            "query": query,
            "top_k": kwargs.get("top_k", 3),
            "max_depth": kwargs.get("max_depth", 3),
            "max_nodes": kwargs.get("max_nodes", k),
        }

        try:
            async with aiohttp.ClientSession() as session:
                async with session.get(url, params=params, timeout=30) as response:
                    if response.status != 200:
                        logging.error(f"Neo4j 检索失败: {response.status}")
                        return []

                    data = await response.json()
                    nodes = data.get("nodes", [])
                    results = []
                    for node in nodes[:k]:
                        desc = node.get("properties", {}).get("description", "")
                        if desc:
                            results.append(desc)
                    # for i, text in enumerate(results, 1):
                    #     print(f"{i}. {text}\n")
                    logging.info(f"neo4j检索出数据:{len(results)}条, 问题是:{query}")
                    return results

        except Exception as e:
            logging.error(f"[错误] Neo4j 检索异常: {e}")
            return []


async def main():
    store = Neo4jStorage()
    results = await store.search("美军一架F-15E战斗机3日在伊朗上空被击落", k=15)
    for i, text in enumerate(results, 1):
        print(f"{i}. {text}\n")


if __name__ == "__main__":
    import asyncio
    asyncio.run(main())
