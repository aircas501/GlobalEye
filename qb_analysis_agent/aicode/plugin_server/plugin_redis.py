"""
插件系统 Redis 操作

key 格式: plugin:url:{username}:{plugin_name}
"""

import os

import redis
from dotenv import load_dotenv

load_dotenv()

REDIS_KEY_PREFIX = "plugin:url:"


def _get_redis():
    """获取 Redis 连接（复用项目现有配置）"""
    return redis.Redis(
        host=os.getenv("MEET_REDIS_HOST", "127.0.0.1"),
        port=int(os.getenv("MEET_REDIS_PORT", "6379")),
        password=os.getenv("MEET_REDIS_PASSWORD", ""),
        db=int(os.getenv("MEET_REDIS_DB", "0")),
        decode_responses=True,
    )


def _build_key(username: str, plugin_name: str) -> str:
    return f"{REDIS_KEY_PREFIX}{username}:{plugin_name}"


def get_url_prefix(username: str, plugin_name: str) -> str | None:
    """查询插件后端地址，未找到返回 None"""
    r = _get_redis()
    try:
        return r.get(_build_key(username, plugin_name))
    finally:
        r.close()


def set_url_prefix(username: str, plugin_name: str, url_prefix: str) -> str:
    """设置插件后端地址，返回 redis key"""
    r = _get_redis()
    try:
        key = _build_key(username, plugin_name)
        r.set(key, url_prefix)
        return key
    finally:
        r.close()


def delete_url_prefix(username: str, plugin_name: str) -> str:
    """删除插件后端地址，返回 redis key"""
    r = _get_redis()
    try:
        key = _build_key(username, plugin_name)
        r.delete(key)
        return key
    finally:
        r.close()

if __name__ == '__main__':
    set_url_prefix("admin", "data-table", "http://localhost:8081/")
    set_url_prefix("admin", "info-card", "http://localhost:8081/")