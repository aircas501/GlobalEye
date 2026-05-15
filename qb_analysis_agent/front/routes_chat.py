"""
聊天、SSE 流、消息历史、角色保存 路由
"""
import sys
import json
import asyncio
import logging
from typing import Optional, List, Dict, Any

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from agent.multiple_meeting.multi_meet_agent import MultiMeetingAgent
from agent.multiple_meeting.meeting_redis_server import MeetingRedisServer
from agent.multiple_meeting.meeting_constants import ResponseCode
from front.sse_queue import user_message_queues, get_or_create_queue, del_user_queue

router = APIRouter()


# ── Pydantic 模型 ──

class SendMessageRequest(BaseModel):
    user_id: str
    chat_id: str = Field(default="default_chat_id")
    content: Optional[str] = None
    chat_mode: str = Field(default="simple_chat")
    role: Optional[str] = None
    # 多角色会议专用
    stage: Optional[str] = None
    event: Optional[str] = None
    roles: Optional[List[Dict[str, Any]]] = None
    debate_topic: Optional[str] = None
    turns: Optional[int] = 2
    reply_chain_length: Optional[int] = 2
    history_chat_ids: Optional[list[str]] = None
    relation_event: Optional[list[str]] = None


class UserIdChatIdRequest(BaseModel):
    """userId和chatId请求体"""
    user_id: str
    chat_id: str


class DeleteMessagesRequest(BaseModel):
    """删除消息的请求体"""
    user_id: str
    chat_id: str
    message_ids: List[str]


class SaveRoleRequest(BaseModel):
    """保存角色的请求体"""
    user_id: str
    chat_id: str
    roles: List[Dict[str, Any]]


async def generate_events(user_id, chat_id):
    """为指定用户生成SSE事件流"""
    while True:
        try:
            queue = get_or_create_queue(user_id, chat_id)
            message = await asyncio.wait_for(queue.get(), timeout=60)
            sys.stdout.flush()
            yield message
        except asyncio.TimeoutError:
            heartbeat = {"code": "2000", "msg": "heartbeat"}
            yield f"data: {json.dumps(heartbeat)}\n\n"
        except GeneratorExit:
            print(f"用户 {user_id[:8]}... 已断开连接")
            if user_id in user_message_queues:
                del_user_queue(user_id, chat_id)
            break
        except Exception as e:
            print(f"用户 {user_id[:8]}... 连接错误: {e}")
            await asyncio.sleep(1)


@router.get('/stream')
async def stream(user_id, chat_id):
    """SSE流端点"""
    if not user_id or not chat_id:
        return HTTPException(status_code=400, detail="缺少user_id或chat_id参数")

    init_msg = {
        "code": "20000",
        "data": "connect success: " + user_id + ", chat_id:" + chat_id
    }
    queue = get_or_create_queue(user_id, chat_id)
    await queue.put(f"data: {json.dumps(init_msg, ensure_ascii=False)}\n\n")

    async def event_generator():
        async for event in generate_events(user_id, chat_id):
            yield event

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            'Cache-Control': 'no-cache',
            'Connection': 'keep-alive',
            'X-Accel-Buffering': 'no',
            'Transfer-Encoding': 'chunked',
            'Access-Control-Allow-Origin': '*',
            'Content-Encoding': 'identity',
            'X-Content-Type-Options': 'nosniff',
        }
    )


@router.post('/api/send')
async def send_message(request_data: SendMessageRequest):
    """发送消息API"""
    try:
        user_id = request_data.user_id
        chat_id = request_data.chat_id
        question = request_data.event
        chat_mode = request_data.chat_mode
        print(f"用户{user_id}提问:{question}")
        if not user_id or not chat_id:
            return HTTPException(status_code=400, detail="缺少user_id或chat_id")
        try:
            if chat_mode == "multi_role_meeting":
                agent = MultiMeetingAgent()
                return await agent.do_meeting(
                    stage=request_data.stage,
                    event=request_data.event,
                    debate_topic=request_data.debate_topic,
                    user_id=user_id,
                    chat_id=chat_id,
                    turns=request_data.turns,
                    reply_chain_length=request_data.reply_chain_length,
                    history_chat_ids=request_data.history_chat_ids,
                    relation_event=request_data.relation_event,
                )
        except Exception as e:
            print(" " * 50, end="\r")
            print(f"[错误] {e}")
    except Exception as e:
        print(f"[错误] 发送消息失败: {e}")
        return HTTPException(status_code=500, detail=f"发送消息失败: {str(e)}")
    return None


@router.post("/api/delete/messages")
async def del_messages(request_data: DeleteMessagesRequest):
    """删除Redis中的消息"""
    try:
        redis_server = MeetingRedisServer()
        for message_id in request_data.message_ids:
            redis_server.delete_message_by_id(
                user_id=request_data.user_id,
                chat_id=request_data.chat_id,
                message_id=message_id
            )
        return {
            "code": ResponseCode.CODE_DELETE_MESSAGE,
            "msg": "delete messages success",
            "data": ""
        }
    except Exception as e:
        return {
            "code": "500",
            "msg": f"删除消息失败: {str(e)}",
            "data": ""
        }


@router.get("/api/get/chat/history/list")
async def get_chat_history_list(user_id):
    """获取用户历史消息列表"""
    try:
        redis_server = MeetingRedisServer()
        return {
            "code": ResponseCode.CODE_GET_CHAT_HISTORY,
            "msg": "Get chat list success",
            "data": redis_server.get_chat_history_list(user_id=user_id)
        }
    except Exception as e:
        logging.error(f"获取用户消息列表失败: {str(e)}")
        return {
            "code": "500",
            "msg": f"获取用户消息列表失败: {str(e)}",
            "data": ""
        }


@router.get("/api/get/chat/history/context")
async def get_chat_history_context(user_id, chat_id):
    """查看历史消息会话详情"""
    try:
        redis_server = MeetingRedisServer()
        history = redis_server.get_recent_messages(user_id=user_id, chat_id=chat_id, limit=1000)
        history_final = [
            msg for msg in sorted(history, key=lambda x: x['timestamp'])
            if msg['speak_name'] not in ('system_message', 'human_message')
        ]
        return {
            "code": ResponseCode.CODE_GET_CHAT_HISTORY,
            "msg": "Get chat list success",
            "data": history_final
        }
    except Exception as e:
        logging.error(f"获取用户消息详情失败: {str(e)}")
        return {
            "code": "500",
            "msg": f"获取用户消息详情失败: {str(e)}",
            "data": ""
        }


@router.post("/api/del/chat/history")
async def del_chat_history(request_data: UserIdChatIdRequest):
    """删除用户某个历史消息列表"""
    try:
        redis_server = MeetingRedisServer()
        redis_server.delete_chat_from_history(
            user_id=request_data.user_id,
            chat_id=request_data.chat_id
        )
        return {
            "code": ResponseCode.CODE_GET_CHAT_HISTORY,
            "msg": "Delete Chat Success",
            "data": ""
        }
    except Exception as e:
        logging.error(f"删除用户消息列表失败: {str(e)}")
        return {
            "code": "500",
            "msg": f"删除用户消息列表失败: {str(e)}",
            "data": ""
        }


@router.post("/api/save/role")
async def save_role(request_data: SaveRoleRequest):
    """保存角色列表到Redis"""
    try:
        redis_server = MeetingRedisServer()
        redis_server.save_roles(
            user_id=request_data.user_id,
            chat_id=request_data.chat_id,
            roles=request_data.roles
        )
        return {
            "code": ResponseCode.CODE_SAVE_ROLES,
            "msg": "save role success",
            "data": ""
        }
    except Exception as e:
        return {
            "code": "500",
            "msg": f"保存角色失败: {str(e)}",
            "data": ""
        }