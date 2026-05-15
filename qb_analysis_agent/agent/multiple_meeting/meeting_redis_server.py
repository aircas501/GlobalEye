"""
@Time: 2026/4/1 10:29
@Author: ZTL
@File: meeting_redis_server.py
"""
import json
import os
import time
from typing import Optional, List
import redis
from dotenv import load_dotenv
from langchain_core.messages import BaseMessage

load_dotenv()

class MeetingRedisServer:
    _pool = None
    _client = None

    def __init__(self):
        if MeetingRedisServer._pool is None:
            MeetingRedisServer._pool = redis.ConnectionPool(
                host=os.getenv("MEET_REDIS_HOST"),
                port=int(os.getenv("MEET_REDIS_PORT")),
                password=os.getenv("MEET_REDIS_PASSWORD"),
                db=int(os.getenv("MEET_REDIS_DB")),
                decode_responses=True,
                socket_timeout=5,
                max_connections=10,
            )
        if MeetingRedisServer._client is None:
            MeetingRedisServer._client = redis.Redis(connection_pool=MeetingRedisServer._pool)

        self.redis_client = MeetingRedisServer._client

    def save_ai_message(self, user_id: str, chat_id: str, speak_name: str, target_name:str, msg: BaseMessage) -> None:
        index_key = f"debate:index:{user_id}:{chat_id}"
        content_key = f"debate:content:{user_id}:{chat_id}"

        msg_id = msg.id
        timestamp = time.time()
        data = {
            "message_id": msg_id,
            "speak_name": speak_name,
            "target_name": target_name,
            "content": msg.content,
            "timestamp": round(timestamp, 3)
        }
        self.redis_client.hset(content_key, msg_id, json.dumps(data, ensure_ascii=False))
        self.redis_client.zadd(index_key, {msg_id: timestamp})

    def delete_message_by_id(self, user_id: str, chat_id: str, message_id: str) -> None:
        index_key = f"debate:index:{user_id}:{chat_id}"
        content_key = f"debate:content:{user_id}:{chat_id}"
        self.redis_client.hdel(content_key, message_id)
        self.redis_client.zrem(index_key, message_id)

    def get_message_by_id(self, user_id: str, chat_id: str, message_id: str) -> Optional[dict]:
        content_key = f"debate:content:{user_id}:{chat_id}"
        data = self.redis_client.hget(content_key, message_id)
        return json.loads(data) if data else None

    def get_recent_messages(self, user_id: str, chat_id: str, limit: int = 20) -> List[dict]:
        index_key = f"debate:index:{user_id}:{chat_id}"
        content_key = f"debate:content:{user_id}:{chat_id}"
        msg_ids = self.redis_client.zrevrange(index_key, 0, limit - 1)
        if not msg_ids:
            return []
        items = self.redis_client.hmget(content_key, msg_ids)
        return [json.loads(item) for item in items if item]

    def clear_user_history(self, user_id: str, chat_id: str) -> None:
        index_key = f"debate:index:{user_id}:{chat_id}"
        content_key = f"debate:content:{user_id}:{chat_id}"
        self.redis_client.delete(index_key, content_key)
    
    def get_history_chat(self, user_id: str, speaker: str, target: str) -> str:
        """
        获取发言人对目标人的历史对话
        
        Args:
            user_id: 用户ID
            speaker: 发言人
            target: 目标人
            
        Returns:
            历史对话文本
        """
        # 从Redis获取数据
        data = self.get_recent_messages(
            user_id=user_id,
            chat_id="1",
            limit=100,
        )
        
        # 过滤出与发言人和目标人相关的消息
        relevant_messages = []
        for msg in data:
            if (msg["speak_name"] == speaker and msg["target_name"] == target) or \
            (msg["speak_name"] == target and msg["target_name"] == speaker):
                relevant_messages.append(msg)
        
        # 按时间戳排序
        relevant_messages.sort(key=lambda x: x.get("timestamp", 0))
        
        # 拼接成文本
        history_text = ""
        for msg in relevant_messages:
            if "speak_name" in msg:
                history_text += f"{msg['speak_name']}：{msg['content']}\n"
            elif "role_name" in msg:
                history_text += f"{msg['role_name']}：{msg['content']}\n"
        
        return history_text
    
    def save_event_context(self, user_id: str, chat_id: str, event_context: str) -> None:
        """
        存储事件上下文
        
        Args:
            user_id: 用户ID
            chat_id: 会话ID
            event_context: 事件上下文
        """
        key = f"debate:event_context:{user_id}:{chat_id}"
        self.redis_client.set(key, event_context)
    
    def get_event_context(self, user_id: str, chat_id: str) -> str:
        """
        获取事件上下文
        
        Args:
            user_id: 用户ID
            chat_id: 会话ID
            
        Returns:
            事件上下文
        """
        key = f"debate:event_context:{user_id}:{chat_id}"
        return self.redis_client.get(key) or ""
    
    def save_topics(self, user_id: str, chat_id: str, topics: List[str]) -> None:
        """
        存储议题列表
        
        Args:
            user_id: 用户ID
            chat_id: 会话ID
            topics: 议题列表
        """
        key = f"debate:topics:{user_id}:{chat_id}"
        self.redis_client.set(key, json.dumps(topics, ensure_ascii=False))
    
    def get_topics(self, user_id: str, chat_id: str) -> List[str]:
        """
        获取议题列表

        Args:
            user_id: 用户ID
            chat_id: 会话ID

        Returns:
            议题列表
        """
        key = f"debate:topics:{user_id}:{chat_id}"
        data = self.redis_client.get(key)
        return json.loads(data) if data else []

    def save_roles(self, user_id: str, chat_id: str, roles: List[dict]) -> None:
        """
        存储角色列表

        Args:
            user_id: 用户ID
            chat_id: 会话ID
            roles: 角色列表，每个角色是一个字典
        """
        key = f"{user_id}:{chat_id}:debeat_role"
        # 设置过期时间为1天（86400秒）
        self.redis_client.setex(key, 86400, json.dumps(roles, ensure_ascii=False))

    def get_roles(self, user_id: str, chat_id: str) -> List[dict]:
        """
        获取角色列表

        Args:
            user_id: 用户ID
            chat_id: 会话ID

        Returns:
            角色列表，每个角色是一个字典；如果不存在则返回空列表
        """
        key = f"{user_id}:{chat_id}:debeat_role"
        data = self.redis_client.get(key)
        return json.loads(data) if data else []

    def save_chat_to_history(self, user_id: str, chat_id: str, topic: str, event:str) -> None:
        """
        保存聊天会话到历史列表

        Args:
            user_id: 用户ID
            chat_id: 会话ID
            topic: 会话名称
        """

        key = f"{user_id}:chat_history_list"
        chat_data = {
            "chat_id": chat_id,
            "topic": topic,
            "event": event,
            "timestamp": time.time()
        }
        self.redis_client.hset(key, chat_id, json.dumps(chat_data, ensure_ascii=False))

    def get_chat_history_list(self, user_id: str) -> List[dict]:
        """
        获取用户的聊天会话历史列表

        Args:
            user_id: 用户ID

        Returns:
            聊天会话列表，每个会话包含 chat_id, chat_name, timestamp
        """
        key = f"{user_id}:chat_history_list"
        all_chats = self.redis_client.hgetall(key)
        if not all_chats:
            return []

        chat_list = []
        for chat_id, chat_json in all_chats.items():
            try:
                chat_data = json.loads(chat_json)
                chat_list.append(chat_data)
            except json.JSONDecodeError:
                continue

        # 按时间戳倒序排列（最新的在前）
        chat_list.sort(key=lambda x: x.get("timestamp", 0), reverse=True)
        return chat_list

    def delete_chat_from_history(self, user_id: str, chat_id: str) -> None:
        """
        从历史列表中删除指定的聊天会话

        Args:
            user_id: 用户ID
            chat_id: 会话ID
        """
        key = f"{user_id}:chat_history_list"
        self.redis_client.hdel(key, chat_id)

    def save_chat_abstract(self, user_id: str, chat_id: str, abstract_text: str) -> None:
        """
        保存聊天摘要

        Args:
            user_id: 用户ID
            chat_id: 会话ID
            abstract_text: 摘要文本
        """
        key = f"{user_id}:{chat_id}:chat_history:abstract"
        self.redis_client.set(key, abstract_text)

    def get_chat_abstract(self, user_id: str, chat_id: str) -> Optional[str]:
        """
        获取聊天摘要

        Args:
            user_id: 用户ID
            chat_id: 会话ID

        Returns:
            摘要文本，如果不存在则返回None
        """
        key = f"{user_id}:{chat_id}:chat_history:abstract"
        return self.redis_client.get(key)

    def delete_chat_abstract(self, user_id: str, chat_id: str) -> None:
        """
        删除聊天摘要

        Args:
            user_id: 用户I
            chat_id: 会话ID
        """
        key = f"{user_id}:{chat_id}:chat_history:abstract"
        self.redis_client.delete(key)

