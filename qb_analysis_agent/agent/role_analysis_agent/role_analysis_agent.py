"""
@Time: 2026/4/3 15:01
@Author: ZTL
@File: role_analysis_agent.py
"""
import json
import os
from typing import List, Dict

from dotenv import load_dotenv
from langchain_core.prompts import ChatPromptTemplate
from langchain_openai import ChatOpenAI
from openai import BaseModel
from pydantic import Field

from front.sse_queue import get_or_create_queue, publish_message
from agent.multiple_meeting.meeting_constants import ResponseCode
load_dotenv()

# LLM 配置
LLM_MODEL = os.getenv("LLM_MODEL", "gpt-3.5-turbo")
OPENAI_API_KEY = os.getenv("LLM_API_KEY")
BASE_URL = os.getenv("LLM_BASE_URL")

class RoleAnalysisResult(BaseModel):
    """角色分析结果模型"""
    countries: List[str] = Field(description="识别出的国家列表")




# LLM 实例（普通+流式）
llm = ChatOpenAI(
    model=LLM_MODEL,
    api_key=OPENAI_API_KEY,
    base_url=BASE_URL,
    temperature=0.3,
    streaming=False
)

class RoleAnalysisAgent:
    """角色分析智能体（彻底解决花括号变量冲突）"""

    @staticmethod
    async def analyze(retrieve_data: str, question: str, user_id: str, chat_id: str) -> List[str]:
        """基于检索数据和用户问题分析涉及的国家"""
        # 关键：所有花括号都用双重转义 {{}}，避免被LangChain识别为变量
        format_instructions = """
请严格按照以下JSON格式输出，不要添加任何额外内容（仅输出JSON，无其他文字）：
{{
  "countries": ["国家1", "国家2"]
}}
规则：
1. countries字段为数组，仅包含从问题和检索数据中提取的国家名称
2. 如果没有识别到任何国家，countries字段返回空数组 []
3. 仅输出JSON字符串，不要添加markdown标记、注释、换行或其他内容
        """.strip()
        prompt = ChatPromptTemplate.from_messages([
            ("system", f"""
你是国家识别专家，你的唯一任务是：
1. 从用户问题和检索到的情报数据中提取涉及的国家名称
2. 仅提取与问题直接相关的国家，不要编造不存在的国家
3. 严格按照指定格式输出JSON，不要添加任何额外内容
格式要求：
{format_instructions}
            """),
            ("user", "用户问题：{user_question}\n检索数据：{retrieve_info}")
        ])

        try:
            # 调用LLM（使用明确的变量名，避免歧义）
            response = llm.invoke(
                prompt.format_messages(
                    user_question=question,
                    retrieve_info=retrieve_data
                )
            )

            # 清理响应内容（移除所有无关字符）
            clean_content = response.content.strip()
            # 移除可能的markdown标记
            clean_content = clean_content.replace("```json", "").replace("```", "").replace("\n", "").replace(" ", "")

            # 解析JSON
            result_json = json.loads(clean_content)

            # 验证数据类型
            countries = result_json.get("countries", []) if isinstance(result_json.get("countries"), list) else []
            print(f"\n分析出的国家有:{countries}\n\n")
            # await publish_message(user_id, chat_id, ResponseCode.CODE_COUNTRY, countries, "countries")
            return countries

        except json.JSONDecodeError as e:
            print(f"[错误] 国家识别JSON解析失败: {e} | 原始响应: {response.content}")
            return []
        except Exception as e:
            print(f"[错误] 国家识别失败: {e}")
            return []
