"""重导出插件路由，供 web_api.py 使用"""
from aicode.plugin_server.plugins_server import plugin_router  # noqa: F401

__all__ = ["plugin_router"]
