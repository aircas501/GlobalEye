"""
会议 & 技能执行路由
"""
import json

from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from agent.agent_skill.agent_skills import skill_executor
from front.sse_queue import get_or_create_queue

router = APIRouter()


@router.post('/api/execute/skill')
async def execute_skill(user_id: str, chat_id: str, query: str) -> dict:
    """执行Agent技能

    Args:
        user_id: 用户ID
        chat_id: 聊天ID
        query: 用户查询，格式: /skill_name query_content

    Returns:
        dict: 执行结果
    """
    try:
        result = skill_executor.execute(query)

        response_msg = {"code": "20000", "data": result}
        queue = get_or_create_queue(user_id, chat_id)
        await queue.put(f"data: {json.dumps(response_msg, ensure_ascii=False)}\n\n")

        return {"code": "20000", "msg": "技能执行成功", "data": result}
    except Exception as e:
        error_msg = f"技能执行失败: {str(e)}"
        response_msg = {"code": "500", "data": error_msg}
        queue = get_or_create_queue(user_id, chat_id)
        await queue.put(f"data: {json.dumps(response_msg, ensure_ascii=False)}\n\n")

        return {"code": "500", "msg": error_msg, "data": ""}
