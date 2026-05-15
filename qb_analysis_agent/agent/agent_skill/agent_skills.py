import json
import os
import re
import requests
from dotenv import load_dotenv
from langchain_core.tools import StructuredTool
from langchain_openai import ChatOpenAI
from langchain.agents import create_agent
from langgraph.checkpoint.memory import InMemorySaver
from pydantic import create_model

load_dotenv()


class AgentSkillExecutor:
    """Agent技能执行器，用于动态加载技能并执行用户请求"""

    TYPE_MAP = {
        "string": str,
        "array": list,
        "integer": int,
        "number": float,
        "boolean": bool
    }

    def __init__(self):
        # 配置
        self.LLM_MODEL = os.getenv("LLM_MODEL", "gpt-3.5-turbo")
        self.API_KEY = os.getenv("LLM_API_KEY")
        self.BASE_URL = os.getenv("LLM_BASE_URL")

        # 计算技能文件基础路径（相对于项目根目录）
        current_file_dir = os.path.dirname(os.path.abspath(__file__))
        project_root = os.path.dirname(os.path.dirname(current_file_dir))
        self.skills_base_path = os.path.join(project_root, "skills")

    def load_skill(self, skill_name: str) -> dict:
        """动态加载 skill.json 文件

        Args:
            skill_name: 技能名称，对应skills目录下的文件名

        Returns:
            dict: 技能配置

        Raises:
            FileNotFoundError: 技能文件不存在
        """
        # 支持两种路径格式
        skill_path1 = f"{self.skills_base_path}/{skill_name}.json"
        skill_path2 = f"{self.skills_base_path}/{skill_name}/skill.json"

        if os.path.exists(skill_path1):
            skill_path = skill_path1
        elif os.path.exists(skill_path2):
            skill_path = skill_path2
        else:
            raise FileNotFoundError(f"技能文件不存在：{skill_path1} 或 {skill_path2}")

        with open(skill_path, "r", encoding="utf-8") as f:
            return json.load(f)

    def build_tools(self, skill: dict) -> list:
        """根据技能配置动态构建工具列表

        Args:
            skill: 技能配置字典

        Returns:
            list: 工具列表
        """
        tools = []
        for t in skill["tools"]:
            name = t["name"]
            desc = t["description"]
            url = t["url"]
            method = t.get("method", "POST")

            # 动态构造参数模型
            fields = {}
            for p in t["parameters"]:
                py_type = self.TYPE_MAP.get(p["type"], str)
                fields[p["name"]] = (py_type, ...)
            InputModel = create_model(f"{name}_Input", **fields)

            # 工具执行函数
            def tool_func(**kwargs):
                try:
                    # 检查URL是否是占位符
                    if url.startswith("url") and url[3:].isdigit():
                        # 占位符URL，返回模拟数据
                        return {
                            "status": "success",
                            "message": f"工具 {name} 调用成功（模拟数据，URL需配置）",
                            "data": kwargs,
                            "mock": True
                        }
                    elif not url or url == "":
                        # 空URL，返回错误
                        return {
                            "status": "error",
                            "message": f"工具 {name} 未配置有效URL",
                            "data": kwargs
                        }
                    else:
                        # 真实URL，调用API
                        resp = requests.request(method, url, json=kwargs, timeout=15)
                        return resp.json()
                except Exception as e:
                    return {"tool": name, "error": str(e), "params": kwargs}

            tool = StructuredTool(
                name=name,
                description=desc,
                func=tool_func,
                args_schema=InputModel
            )
            tools.append(tool)
        return tools

    def parse_input(self, user_input: str) -> tuple:
        """解析用户输入，提取技能名称和查询内容

        格式: /skill_name query_content

        Args:
            user_input: 用户输入字符串

        Returns:
            tuple: (skill_name, query) 或 (None, None)
        """
        pattern = r"^/(\w+)\s+(.+)"
        match = re.match(pattern, user_input.strip())
        if not match:
            return None, None
        skill_name = match.group(1)
        query = match.group(2)
        return skill_name, query

    def create_agent_for_skill(self, skill_name: str) -> tuple:
        """为指定技能创建Agent

        Args:
            skill_name: 技能名称

        Returns:
            tuple: (agent, tools) - Agent实例和工具列表
        """
        skill = self.load_skill(skill_name)
        tools = self.build_tools(skill)

        # LLM配置（与项目中其他模块保持一致）
        llm = ChatOpenAI(
            model=self.LLM_MODEL,
            api_key=self.API_KEY,
            base_url=self.BASE_URL,
            temperature=0.1,
            streaming=False
        )

        system_prompt = f"""
你是专业任务执行助手。
当前技能：{skill['name']}
描述：{skill['description']}
执行规则：{skill['content']}

请严格按照规则调用工具，不要编造信息。
"""

        agent = create_agent(
            llm,
            system_prompt=system_prompt,
            middleware=[],
            checkpointer=InMemorySaver()
        )
        return agent, tools

    def execute(self, user_input: str) -> str:
        """执行用户输入，前端调用入口

        Args:
            user_input: 用户输入字符串，格式: /skill_name query_content

        Returns:
            str: 执行结果
        """
        skill_name, query = self.parse_input(user_input)
        if not skill_name:
            return "格式错误，请使用: /skill_name query_content"

        try:
            agent, tools = self.create_agent_for_skill(skill_name)

            result = agent.invoke({
                "input": query,
                "tools": tools
            })

            return result.get("output", "无结果")
        except FileNotFoundError as e:
            return f"技能 '{skill_name}' 不存在: {str(e)}"
        except Exception as e:
            return f"执行失败: {str(e)}"


# 全局执行器实例，供其他模块导入
skill_executor = AgentSkillExecutor()

