# 存储每个用户的消息队列
import asyncio
import json
from typing import Any

# user_message_queues: dict[str, asyncio.Queue] = {}
user_message_queues = {}

def get_or_create_queue(user_id: str, chat_id:str) -> asyncio.Queue:
    """安全地获取或创建用户队列"""
    key = f"{user_id}_{chat_id}"
    if key not in user_message_queues:
        user_message_queues[key] = asyncio.Queue(maxsize=1)
    return user_message_queues[key]

def del_user_queue(user_id: str, chat_id:str):
    key = f"{user_id}_{chat_id}"
    del user_message_queues[key]

def check_user_in_queue(user_id: str, chat_id:str) -> bool:
    return f"{user_id}_{chat_id}" in user_message_queues

async def publish_message(user_id: str, chat_id:str, code:str, data:Any, msg:str) -> None:
    queue = get_or_create_queue(user_id, chat_id)
    ans = {"code":code, "msg":msg, "data": data}
    # await queue.put(f"data: {json.dumps(ans, ensure_ascii=False)}\n\n")