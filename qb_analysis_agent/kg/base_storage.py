"""
@Time: 2026/4/17
@Author: LxpStu
@File: base_storage.py
"""
from abc import ABC, abstractmethod
from typing import List


class BaseStorage(ABC):
    """存储后端抽象基类"""

    @abstractmethod
    async def search(self, query: str, embed_fn, k: int = 15, **kwargs) -> List[str]:
        """检索接口

        Args:
            query: 查询文本
            embed_fn: 向量化函数 embed_query(query) → list[float]
            k: 返回条数

        Returns:
            文本列表
        """
        ...
