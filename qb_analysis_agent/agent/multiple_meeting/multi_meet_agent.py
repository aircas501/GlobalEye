"""
@Time: 2026/3/30 14:54
@Author: ZTL
@File: multi_meet_agent.py
"""
import os
import asyncio
import uuid
from typing import Any, Optional

from dotenv import load_dotenv
from langchain.agents.middleware import after_model
from langchain_core.messages import SystemMessage, HumanMessage, AIMessageChunk, RemoveMessage, AIMessage
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import RunnableConfig
from langchain_openai import ChatOpenAI
from langchain.agents import create_agent, AgentState
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph.message import REMOVE_ALL_MESSAGES
from langgraph.runtime import Runtime
from langgraph.types import Command
import logging
from agent.multiple_meeting.debate_scheduler import DebateScheduler
from agent.multiple_meeting.entity.role import Role
from agent.multiple_meeting.meeting_constants import ResponseCode
from agent.multiple_meeting.meeting_redis_server import MeetingRedisServer
from agent.role_analysis_agent.role_analysis_agent import RoleAnalysisAgent
from front.sse_queue import publish_message
from rag.military_news_rag import MilitaryNewsRAG
from kg.milvus_impl import MilvusStorage
from kg.neo4j_impl import Neo4jStorage

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s: %(message)s"
)

load_dotenv()

# ================= LLM 配置 =================
LLM_MODEL = os.getenv("LLM_MODEL", "gpt-3.5-turbo")
API_KEY = os.getenv("LLM_API_KEY")
BASE_URL = os.getenv("LLM_BASE_URL")


class MultMeetingAgentState(AgentState):
    user_id: str
    speak_name: str
    target_name: str
    chat_id: str
    save_system_message: bool
    save_human_message: bool


class MultiMeetingAgent:
    def __init__(self):
        self.llm = ChatOpenAI(
            model=LLM_MODEL,
            api_key=API_KEY,
            base_url=BASE_URL,
            temperature=0.1
        )
        self.checkpointer = InMemorySaver()
        self.middleware = [self.delete_old_messages]
        self.agt = create_agent(
            self.llm,
            tools=[],
            state_schema=MultMeetingAgentState,
            middleware=self.middleware,
            checkpointer=self.checkpointer,
        )

    @after_model
    def delete_old_messages(state: MultMeetingAgentState, runtime: Runtime) -> Command:
        save_system_message = state.get("save_system_message", False)
        save_human_message = state.get("save_human_message", False)
        messages = state.get("messages", [])
        cur_speak_name = state.get("speak_name", "")
        cur_target_name = state.get("target_name", "")
        cur_user_id = state.get("user_id", None)
        cur_chat_id = state.get("chat_id", None)
        if cur_user_id is None or cur_chat_id is None:
            return Command(update={
                "messages": [RemoveMessage(id=REMOVE_ALL_MESSAGES)]
            })
        redis_server = MeetingRedisServer()
        if len(messages) >= 3:
            if not save_system_message:
                save_system_message = True
                redis_server.save_ai_message(
                    user_id=cur_user_id,
                    chat_id=cur_chat_id,
                    speak_name="system_message",
                    target_name=cur_target_name,
                    msg=messages[0]
                )
            if not save_human_message:
                save_human_message = True
                redis_server.save_ai_message(
                    user_id=cur_user_id,
                    chat_id=cur_chat_id,
                    speak_name="human_message",
                    target_name=cur_target_name,
                    msg=messages[1]
                )
            redis_server.save_ai_message(
                user_id=cur_user_id,
                chat_id=cur_chat_id,
                speak_name=cur_speak_name,
                target_name=cur_target_name,
                msg=messages[2]
            )
        return Command(update={
            "save_system_message": save_system_message,
            "save_human_message": save_human_message,
            "messages": [RemoveMessage(id=REMOVE_ALL_MESSAGES)]
        })

    async def debate_topic_generator_agent(self, event: str, content: str, user_id: str, chat_id: str):
        """
        根据一个事件，生成议题
        Args:
            event: 触发讨论的事件
            content: 与 event有关的 rag上下文
            user_id: 用户id
            chat_id: 会话id
        Returns:
            一个包含5条议题的列表，格式 {"topics": ["议题1",...,"议题5"]}
        """
        # 2.定义SystemMessage和HumanMessage
        system_message = SystemMessage(
            content="""
                    你是一名专业辩论赛策划顾问，擅长根据国际冲突事件生成具有可辩性的辩论议题。
                    请严格按照要求输出，仅返回议题，不添加任何额外解释。
                    """
        )
        human_message = HumanMessage(
            content=f"""事件背景：{event}
                    相关上下文信息：{content}
                    请基于以上背景与上下文，生成5个辩论赛的议题,要求对立清晰、有深度。
                    仅输出5个议题，需要序号、无需解释、无多余内容。每个议题之间使用@符号分割,
                    议题应该是一个疑问句，议题中不要出现‘辩题：’这种字眼""".strip()
        )
        prompt = ChatPromptTemplate.from_messages([system_message, human_message])
        input_data = prompt.invoke({"event": event, "content": content})

        # 3. 定义RunnableConfig
        config: RunnableConfig = {
            "configurable": {
                "thread_id": user_id
            }
        }

        # 4.调用LLM生成讨论的议题
        topics = ""
        async for event in self.agt.astream(
                input=input_data,
                config=config,
                stream_mode="messages",
        ):
            for msg in event:
                if isinstance(msg, AIMessageChunk) and msg.content:
                    print(msg.content, flush=True, end="")
                    topics += msg.content.strip()
        result = topics.split("@")
        logging.info(f"最终生成议题：{result}");
        # await publish_message(user_id, chat_id, ResponseCode.CODE_TOPIC_GENERATION, result, "topics")
        return result

    async def role_position_agent(self,
                                  role: Role,
                                  event: str,
                                  debate_topic: str,
                                  content: str,
                                  user_id: str,
                                  chat_id: str,
                                  history_chat_abstracts: str):
        """
            各个角色陈述自己的观点
            Args:
              role: 角色
              event: 触发讨论的事件
              debate_topic: 本次讨论的议题
              content: 与 event有关的 rag上下文
              user_id: 用户id
              chat_id: 会话ID
              history_chat_abstracts: 其他历史对话摘要参考
            Returns:
              各个角色的观点
         """

        # 2.定义SystemMessage和HumanMessage
        system_message = SystemMessage(
            content="""
                你是一名专业的国际战略与危机推演专家。
                你将根据给定角色信息，围绕议题生成**符合身份的战术推演式发言**，而非普通辩论。

                角色信息包含固定维度：
                姓名、身份、人设、发言风格、禁止内容、立场。

                输出规则：
                1. 严格贴合角色身份、立场，不偏离人设与专业领域，坚守无固定阵营、公平客观的核心。
                2. 语气、措辞严格匹配发言风格，保持理性、战略化表达，突出顶尖专业素养。
                3. 绝对不出现禁止内容中的表述。
                4. 发言必须包含：
                   - 对事件背后**真实目的与意图**的判断
                   - 相关方**下一步可能的行动动向**，包括最近和长期
                   - 对局势影响的判断与本方立场
                5. 逻辑专业、立场鲜明，以战略推演为主，不做情绪化争吵，依托相关情报与事实展开。
                6. 只输出角色发言正文，不添加格式、不解释、不自我介绍。
                7. 如果有其他历史对话摘要，你需要参考
                """
        )
        human_message = HumanMessage(
            content=f"""
                当前辩论议题：{debate_topic}
                当前发生的事件：{event}
                与事件相关的背景上下文：{content}

                请根据以下角色信息，以该角色的身份，
                **基于背景事实进行战略推演发言**：

                姓名：{role.name}
                身份：{role.identity}
                人设：{role.persona}
                发言风格：{role.speaking_style}
                禁止内容：{role.forbidden}
                立场：{role.stance}
                【参考的其他历史对话摘要】
                {history_chat_abstracts}

                要求：
                1. 严格依据上下文内容分析，禁止主观臆断，贴合角色公平客观、依托事实推演的特质。
                2. 重点阐述：事件背后的真实意图、相关方下一步可能动向、局势影响。
                3. 发言风格与身份高度统一，体现顶尖战略判断与决策视角。
                4. 字数限制在150字以内。
                """
        )

        # 3. 定义RunnableConfig
        config: RunnableConfig = {
            "configurable": {
                "thread_id": user_id
            }
        }
        print(f"\n\n当前发言的角色是:{role.name}, 身份:{role.identity}")
        input_data = {
            "messages": [system_message, human_message],
            "user_id": user_id,
            "speak_name": role.name,
            "chat_id": chat_id,
        }
        # 4.调用LLM生成讨论的议题
        msg_id = None
        await publish_message(user_id, chat_id, ResponseCode.CODE_ROLE, role.name, "role speak")
        async for event in self.agt.astream(
                input=input_data,
                config=config,
                stream_mode="messages",
        ):
            for msg in event:
                if isinstance(msg, AIMessageChunk) and msg.content:
                    print(msg.content, flush=True, end="")
                    await publish_message(user_id, chat_id, ResponseCode.CODE_POSITION, msg.content, "role position")
                if isinstance(msg, AIMessage) and msg.id:
                    if msg_id is None:
                        msg_id = msg.id
        print(f"message_id：{msg_id}", flush=True, end="")
        await publish_message(user_id, chat_id, ResponseCode.CODE_POSITION_MESSAGE_ID, msg_id,
                              "role position message id")

    async def role_debate_agent(self,
                                speaker: Role,
                                target_role: Role,
                                history_chat: str,
                                event: str,
                                debate_topic: str,
                                event_content: str,
                                user_id: str,
                                chat_id: str,
                                history_chat_abstracts: str):
        """
         各个角色之间互相驳斥
            Args:
              speaker: 发言人角色
              target_role: 待回复角色
              history_chat: 发言人角色与待回复角色的历史对话
              event: 触发讨论的事件
              debate_topic: 本次讨论的议题
              event_content: 与 event有关的 rag上下文
              user_id: 用户id
              chat_id: 会话ID
            Returns:
              驳斥的内容
        """

        system_message = SystemMessage(
            content=f"""
        你正在进行一场高规格国际危机**战术推演**。
        你的发言必须**高度贴合自身身份、专业背景与职责范围**，不做超出角色逻辑的空谈。

        【你的固定身份】
        - 姓名：{speaker.name}
        - 身份：{speaker.identity}
        - 人设：{speaker.persona}
        - 发言风格：{speaker.speaking_style}
        - 核心立场：{speaker.stance}
        - 禁止内容：{speaker.forbidden}

        【发言逻辑：按身份自然侧重】
        请根据你的身份，**以自身专业领域为核心**客观推演，其他领域仅在关联时简要提及：
        1. 军事战略家：
           - 核心：军事部署、战术意图、威慑风险、战场态势、下一步军事行动推演
           - 经济仅在影响军事后勤与战力时简要提及
        2. 金融与能源经济学家：
           - 核心：经济冲击、能源市场、制裁效果、供应链波动、长期经济代价
           - 军事仅在影响经济稳定时简要提及
        3. 顶尖政治家与外交调解专家：
           - 核心：战略意图、局势走向、危机管控、长期稳定、各方合理诉求平衡
           - 可兼顾军事与经济，保持全局决策视角，不深入技术细节

        【推演规则】
        1. 基于事实与情报客观分析，不站队、不偏袒任何国家
        2. 对他人观点可**理性驳斥错误逻辑**，也可**维护合理判断**
        3. 聚焦局势本身，不进行情绪化对抗与攻击

        【战术推演必须包含】
        1. 对当前事件背后真实意图的判断
        2. 未来24–72小时局势推演（从你专业视角出发）
        3. 对地区与全球安全、稳定的整体影响
        4. 基于客观视角的研判与合理建议

        【风格要求】
        - 专业、冷静、客观、极具战略判断力
        - 是学术级战略推演，不是辩论吵架
        - 语言简洁有力，不重复他人句式
        - 严格格式：@对方姓名 内容
        【其他历史对话摘要参考】
        - 如果有其他历史对话摘要，你需要参考
        【输出格式】
        仅输出一句话：
        @对方姓名{target_role.name} 战术推演内容
        """
        )

        human_message = HumanMessage(
            content=f"""
               【本次推演信息】
               推演议题：{debate_topic}
               触发事件：{event}
               背景上下文（用于识别阵营与局势）：
               {event_content}
               【你是】
               {speaker}
               【你要回复的角色是】
               {target_role}
               【你们两个的历史对话】
               {history_chat}
               【参考的其他历史对话摘要】
               {history_chat_abstracts}
               请你以 **{speaker.name}** 的身份，从专业视角进行战术推演，严格贴合你的职责与风格：
               - 军方侧重军事态势与安全风险
               - 经济官员侧重经济影响与代价
               - 外交官侧重战略、阵营与外交空间
               """
        )

        # 3. 定义RunnableConfig
        config: RunnableConfig = {
            "configurable": {
                "thread_id": user_id
            }
        }
        logging.info(f"\n\n当前发言的角色是:{speaker.name}, 身份:{speaker.identity}")
        logging.info(f"待回复发言的角色是:{target_role.name}, 身份:{target_role.identity}")

        input_data = {
            "messages": [system_message, human_message],
            "user_id": user_id,
            "speak_name": speaker.name,
            "target_name": target_role.name,
            "chat_id": chat_id,
        }
        # 4.调用LLM生成回复
        generated_content = ""
        msg_id = None
        data = {
            "speak_name": speaker.name,
            "target_name": target_role.name
        }
        await publish_message(user_id, chat_id, ResponseCode.CODE_DEBATE, data, "role debate speaker")
        async for event in self.agt.astream(
                input=input_data,
                config=config,
                stream_mode="messages",
        ):
            for msg in event:
                if isinstance(msg, AIMessageChunk) and msg.content:
                    print(msg.content, flush=True, end="")
                    generated_content += msg.content
                    await publish_message(user_id, chat_id, ResponseCode.CODE_DEBATE, msg.content, "role debate")
                if isinstance(msg, AIMessage) and msg.id:
                    if msg_id is None:
                        msg_id = msg.id
        print(f"message_id：{msg_id}", flush=True, end="")
        await publish_message(user_id, chat_id, ResponseCode.CODE_DEBATE_MESSAGE_ID, msg_id,
                              "role debate message id")
        logging.info(f"回复内容:{generated_content}")
        return generated_content

    async def do_meeting(self, stage: str,
                         event: str,
                         debate_topic: str,
                         user_id: str,
                         chat_id: str,
                         turns: int = 6,
                         reply_chain_length: int = 4,
                         history_chat_ids: list[str] = [],
                         relation_event: list[str] = []) -> Any:
        redis_server = MeetingRedisServer()
        relation_event = relation_event or []

        def get_roles() -> list[Role]:
            # 从Redis获取角色列表
            redis_roles_data = redis_server.get_roles(user_id, chat_id)

            roles = []
            if redis_roles_data:
                # 将字典列表转换为Role对象列表
                roles = [
                    Role(
                        name=role_dict.get("name", ""),
                        identity=role_dict.get("identity", ""),
                        persona=role_dict.get("persona", ""),
                        speaking_style=role_dict.get("speaking_style", ""),
                        forbidden=role_dict.get("forbidden", ""),
                        stance=role_dict.get("stance", "")
                    )
                    for role_dict in redis_roles_data
                ]
            logging.info(f"从Redis加载了 {len(roles)} 个角色")
            return roles

        if stage == "role_choose":
            logging.info(f"当前事件:{event}")
            # 分析此次事件涉及的角色
            rag = MilitaryNewsRAG(MilvusStorage(), Neo4jStorage())
            event_context, event_context_docs = await rag.retrieve(event)
            relation_events = "\n\n---\n\n".join([e for e in relation_event])
            event_context = event_context + relation_events
            # 存储事件上下文到Redis
            redis_server.save_event_context(user_id, chat_id, event_context)
            role_agent = RoleAnalysisAgent()
            ans = await role_agent.analyze(event_context, event, user_id, chat_id)
            return {
                "code": ResponseCode.CODE_COUNTRY,
                "data": ans,
                "user_id": user_id,
                "chat_id": chat_id
            }
        elif stage == "topic_generation":
            event_context = redis_server.get_event_context(user_id, chat_id)
            topics = []
            if not event_context:
                rag = MilitaryNewsRAG(MilvusStorage(), Neo4jStorage())
                event_context, event_context_docs = await rag.retrieve(event)
                relation_events = "\n\n---\n\n".join([e for e in relation_event])
                event_context = event_context + relation_events
                # 存储事件上下文到Redis
                redis_server.save_event_context(user_id, chat_id, event_context)
            if not debate_topic:
                # 从Redis获取事件上下文
                # 生成选题
                topics = await self.debate_topic_generator_agent(event=event, content=event_context, user_id=user_id,
                                                                 chat_id=chat_id)
            else:
                topics.append(debate_topic)
            # 存储议题列表到Redis
            redis_server.save_topics(user_id, chat_id, topics)
            logging.info(f"本轮讨论最终议题:{topics}, user_id:{user_id}, chat_id:{chat_id}")
            return {
                "code": ResponseCode.CODE_TOPIC_GENERATION,
                "data": topics,
                "user_id": user_id,
                "chat_id": chat_id
            }
        elif stage == "position":
            history_chat_abstracts = ""
            if history_chat_ids:
                history_chat_abstracts = await self.generate_chat_abstracts(user_id=user_id,
                                                                      history_chat_ids=history_chat_ids, limit=100,
                                                                      cur_chat_id=chat_id)
            redis_server.save_chat_to_history(user_id, chat_id, debate_topic, event)
            # 从Redis获取事件上下文
            event_context = redis_server.get_event_context(user_id, chat_id)
            # 各个角色发言--阐述观点
            for role in get_roles():
                await self.role_position_agent(
                    role=role,
                    event=event,
                    debate_topic=debate_topic,
                    content=event_context,
                    user_id=user_id,
                    chat_id=chat_id,
                    history_chat_abstracts=history_chat_abstracts
                )
            return {
                "code": ResponseCode.CODE_POSITION,
                "data": "",
                "user_id": user_id,
                "chat_id": chat_id
            }
        elif stage == "debate":
            # 从Redis获取事件上下文
            event_context = redis_server.get_event_context(user_id, chat_id)
            history_chat_abstracts = redis_server.get_event_context(user_id, chat_id)
            # 辩论进入第三阶段, 分多轮、每轮N组谈话, 与其他人自由交谈, 包括驳斥和支持
            await self.role_debate(
                roles=get_roles(),
                event=event,
                debate_topic=debate_topic,
                event_content=event_context,
                user_id=user_id,
                chat_id=chat_id,
                turns=turns,
                reply_chain_length=reply_chain_length,
                history_chat_abstracts=history_chat_abstracts)

            # 辩论结束后进行总结
            await self.summary(
                user_id=user_id,
                chat_id=chat_id,
                event=event,
                debate_topic=debate_topic
            )

            return {
                "code": ResponseCode.CODE_DEBATE,
                "data": "",
                "user_id": user_id,
                "chat_id": chat_id
            }
        return None

    async def _process_speech(self, roles_dict, chat_cache, redis_server,
                              debate_topic, event, event_content, speech, user_id, chat_id,
                              current_speaker, current_target, history_chat_abstracts):
        """
        处理单个发言任务的公共方法

        Args:
            roles_dict: 角色字典
            chat_cache: 聊天缓存
            redis_server: Redis服务器实例
            debate_topic: 辩论主题
            event: 触发事件
            event_content: 事件内容
            speech: 发言任务
            user_id: 用户ID
            current_speaker: 当前发言人
            current_target: 当前目标

        Returns:
            tuple: (更新后的current_speaker, current_target)
        """
        speaker = speech["speaker"]
        target = speech["target"]

        # 检查发言人和回复人是否变化
        if speaker != current_speaker or target != current_target:
            # 清空缓存
            chat_cache.clear()
            current_speaker = speaker
            current_target = target

        # 生成固定顺序的缓存键
        cache_key = tuple(sorted([speaker, target]))

        # 检查缓存
        if cache_key not in chat_cache:
            # 从 Redis 获取历史对话
            chat_cache[cache_key] = redis_server.get_history_chat(user_id, speaker, target)

        # 获取历史对话
        history_chat = chat_cache[cache_key]

        # 执行发言并获取返回的消息
        generated_content = await self.role_debate_agent(
            speaker=roles_dict.get(speaker),
            target_role=roles_dict.get(target),
            history_chat=history_chat,
            event=event,
            debate_topic=debate_topic,
            event_content=event_content,
            user_id=user_id,
            chat_id=chat_id,
            history_chat_abstracts=history_chat_abstracts
        )
        result = {"speaker": speaker, "target": target, "content": generated_content}
        # 更新缓存
        if result and result.get("content"):
            if cache_key in chat_cache:
                # 将新生成的消息添加到缓存中
                if chat_cache[cache_key]:
                    chat_cache[cache_key] += f"\n{result['speaker']}：{result['content']}"
                else:
                    chat_cache[cache_key] = f"{result['speaker']}：{result['content']}"

        return current_speaker, current_target

    async def role_debate(self,
                          roles: list[Role],
                          event: str,
                          debate_topic: str,
                          event_content: str,
                          user_id: str,
                          chat_id: str,
                          turns: int,
                          reply_chain_length: int,
                          history_chat_abstracts: str):
        # 1.获取最近的历史消息会话
        redis_server = MeetingRedisServer()
        roles_dict = {r.name: r for r in roles}
        # 3.开始驳斥
        name_list = [role.name for role in roles]
        # 初始化调度器
        scheduler = DebateScheduler(role_names=name_list, turns=turns, reply_chain_length=reply_chain_length)
        chat_cache = {}
        current_speaker = None
        current_target = None
        # 执行turns次基础发言
        for _ in range(turns):
            speech = scheduler.process_one_speech()
            current_speaker, current_target = await self._process_speech(
                roles_dict, chat_cache, redis_server,
                debate_topic, event, event_content, speech, user_id, chat_id,
                current_speaker, current_target, history_chat_abstracts
            )
        # 处理所有待回复任务，直到队列为空
        while scheduler.task_queue:
            speech = scheduler.process_one_speech()
            current_speaker, current_target = await self._process_speech(
                roles_dict, chat_cache, redis_server,
                debate_topic, event, event_content, speech, user_id, chat_id,
                current_speaker, current_target, history_chat_abstracts
            )

    async def generate_chat_abstracts(self, user_id: str, history_chat_ids: list[str], cur_chat_id: str,
                                      limit: int = 100) -> str:
        """
        生成聊天摘要

        Args:
            user_id: 用户ID
            history_chat_ids: 聊天ID列表
            limit: 每个聊天获取的消息数量限制，默认100
            cur_chat_id: 当前聊天的会话ID

        Returns:
            拼接后的摘要字符串，格式为："历史对话1：摘要内容...\n\n历史对话2：摘要内容..."
        """
        redis_server = MeetingRedisServer()
        all_abstracts = []

        for i, chat_id in enumerate(history_chat_ids, 1):
            try:
                # 获取历史消息
                messages = redis_server.get_recent_messages(user_id, chat_id, limit)
                if not messages:
                    all_abstracts.append(f"历史对话{i}：该对话没有历史消息")
                    continue

                # 构建对话文本
                dialog_text = ""
                for msg in messages:
                    speaker = msg.get("speak_name", "未知")
                    content = msg.get("content", "")
                    # 清理内容，移除特殊标记
                    if content:
                        dialog_text += f"{speaker}：{content}\n"

                if not dialog_text.strip():
                    all_abstracts.append(f"历史对话{i}：对话内容为空")
                    continue

                # 调用LLM生成摘要
                system_message = SystemMessage(
                    content="""你是一名专业的对话摘要生成专家。
                    请根据提供的对话历史，生成一段简洁、准确的摘要。
                    要求：
                    1. 摘要长度控制在300字以内
                    2. 突出重点观点和关键信息
                    3. 保持客观中立，不添加个人评价
                    4. 使用中文生成摘要"""
                )

                human_message = HumanMessage(
                    content=f"""以下是用户 {user_id} 在聊天 {chat_id} 中的对话历史：

{dialog_text}

请生成一段200字以内的摘要，概括对话的主要内容和关键观点。"""
                )

                input_data = {
                    "user_id": user_id,
                    "chat_id": chat_id,
                    "dialog_text": dialog_text,
                    "messages":[system_message, human_message]
                }

                # 调用LLM生成摘要
                config = {
                    "configurable": {
                        "thread_id": user_id
                    }
                }

                abstract = ""
                async for event in self.agt.astream(
                        input=input_data,
                        config=config,
                        stream_mode="messages",
                ):
                    for msg in event:
                        if isinstance(msg, AIMessageChunk) and msg.content:
                            abstract += msg.content.strip()

                # 如果摘要过长，进行截断
                if len(abstract) > 300:
                    abstract = abstract[:297] + "..."

                all_abstracts.append(f"历史对话{i}：{abstract}")

            except Exception as e:
                all_abstracts.append(f"历史对话{i}：生成摘要时出错 - {str(e)}")

        # 拼接所有摘要
        ans = "\n\n".join(all_abstracts)
        redis_server.save_chat_abstract(user_id, cur_chat_id, ans)
        return ans

    async def summary(self, user_id: str, chat_id: str, event: str, debate_topic: str) -> str:
        """
        总结辩论阶段各个角色的陈述以及对未来的推演

        Args:
            user_id: 用户ID
            chat_id: 会话ID
            event: 触发事件
            debate_topic: 辩论议题

        Returns:
            总结文本
        """
        redis_server = MeetingRedisServer()

        # 获取角色列表
        roles_data = redis_server.get_roles(user_id, chat_id)
        roles = []
        if roles_data:
            roles = [
                Role(
                    name=role_dict.get("name", ""),
                    identity=role_dict.get("identity", ""),
                    persona=role_dict.get("persona", ""),
                    speaking_style=role_dict.get("speaking_style", ""),
                    forbidden=role_dict.get("forbidden", ""),
                    stance=role_dict.get("stance", "")
                )
                for role_dict in roles_data
            ]

        # 获取最近的对话消息
        messages = redis_server.get_recent_messages(user_id, chat_id, limit=100)

        # 过滤出角色发言的消息（排除system_message和human_message）
        role_messages = []
        for msg in messages:
            speak_name = msg.get("speak_name", "")
            if speak_name not in ["system_message", "human_message"]:
                role_messages.append(msg)

        if not role_messages:
            return "暂无角色发言记录。"

        # 按时间戳排序
        role_messages.sort(key=lambda x: x.get("timestamp", 0))

        # 构建对话文本
        dialog_text = ""
        for msg in role_messages:
            speaker = msg.get("speak_name", "未知")
            target = msg.get("target_name", "")
            content = msg.get("content", "")
            if target:
                dialog_text += f"{speaker} -> {target}: {content}\n"
            else:
                dialog_text += f"{speaker}: {content}\n"

        # 获取角色信息文本
        roles_text = ""
        for role in roles:
            roles_text += f"姓名: {role.name}, 身份: {role.identity}, 立场: {role.stance}\n"

        # 调用LLM生成总结
        system_message = SystemMessage(
            content="""你是一名专业的国际战略与危机推演总结专家。
            请根据提供的辩论对话历史，生成一份全面的总结报告。

            要求：
            1. 总结每个角色的主要陈述和观点
            2. 提炼每个角色对未来局势的推演和预测
            3. 分析不同角色之间的观点差异和共识
            4. 评估当前局势的可能发展方向
            5. 语言专业、客观、简洁，突出战略洞察
            6. 总结长度控制在500字以内
            7. 使用中文生成总结

            输出格式：
            # 辩论总结报告

            ## 一、各角色主要观点
            - [角色1姓名] ([身份]): 主要观点...
            - [角色2姓名] ([身份]): 主要观点...

            ## 二、未来局势推演
            - [角色1姓名] 的推演: ...
            - [角色2姓名] 的推演: ...

            ## 三、观点差异与共识
            - 主要分歧: ...
            - 达成共识: ...

            ## 四、局势发展方向评估
            ..."""
        )

        human_message = HumanMessage(
            content=f"""以下是关于事件「{event}」的辩论对话记录，辩论议题为「{debate_topic}」。

            参与角色信息：
            {roles_text}

            辩论对话记录：
            {dialog_text}

            请根据以上信息，生成一份全面的推演辩论总结报告。"""
        )

        input_data = {
            "messages": [system_message, human_message],
            "user_id": user_id,
            "speak_name": "summary_agent",
            "chat_id": chat_id,
        }

        config = {
            "configurable": {
                "thread_id": user_id
            }
        }

        summary_text = ""
        async for event_stream in self.agt.astream(
                input=input_data,
                config=config,
                stream_mode="messages",
        ):
            for msg in event_stream:
                if isinstance(msg, AIMessageChunk) and msg.content:
                    summary_text += msg.content
                    await publish_message(user_id, chat_id, ResponseCode.CODE_SUMMARY, msg.content, "debate summary")
        logging.info(f"总结:{summary_text}")
        return summary_text


async def run_console():
    event = "美军一架F-15E战斗机3日在伊朗上空被击落"
    # event = "F-15E被击落事件"
    # debate_topic = "1.美军战机在伊朗领空被击落，伊朗的军事反击行为是否属于正当防卫？"
    debate_topic = ""
    turns = 4
    reply_chain_length = 2
    chat_id = "adasbh889900xxxx"
    from role_skills_manager import roles

    # 先保存角色到Redis
    redis_server = MeetingRedisServer()
    # 将Role对象列表转换为字典列表
    roles_dict_list = [
        {
            "name": role.name,
            "identity": role.identity,
            "persona": role.persona,
            "speaking_style": role.speaking_style,
            "forbidden": role.forbidden,
            "stance": role.stance
        }
        for role in roles
    ]
    redis_server.save_roles("admin", chat_id, roles_dict_list)
    print(f"[DEBUG] 已将 {len(roles_dict_list)} 个角色保存到Redis")

    # 创建MultiMeetingAgent实例
    agent = MultiMeetingAgent()

    # 选择角色阶段
    # await agent.do_meeting(
    #     stage="role_choose",
    #     event=event,
    #     debate_topic=debate_topic,
    #     user_id="admin",
    #     chat_id=chat_id,
    #     turns=turns,
    #     reply_chain_length=reply_chain_length,
    #     history_chat_ids=[],
    #     relation_event=[]
    # )
    # 生成选题阶段
    await agent.do_meeting(
        stage="topic_generation",
        event=event,
        debate_topic=debate_topic,
        user_id="admin",
        chat_id=chat_id,
        turns=turns,
        reply_chain_length=reply_chain_length,
        history_chat_ids=[],
        relation_event=[]
    )
    # 各个角色发言--阐述观点
    await agent.do_meeting(
        stage="position",
        event=event,
        debate_topic=debate_topic,
        user_id="admin",
        chat_id=chat_id,
        turns=turns,
        reply_chain_length=reply_chain_length,
        history_chat_ids=[],
        relation_event=[]
    )
    # 辩论进入第三阶段, 分多轮、每轮N组谈话, 与其他人自由交谈, 包括驳斥和支持
    await agent.do_meeting(
        stage="debate",
        event=event,
        debate_topic=debate_topic,
        user_id="admin",
        chat_id=chat_id,
        turns=turns,
        reply_chain_length=reply_chain_length,
        history_chat_ids=["456", "789"],
        relation_event=[]
    )


if __name__ == "__main__":
    # agent = MultiMeetingAgent()
    # asyncio.run(agent.debate_topic_generator_agent(
    #     event="伊朗发动“真实承诺-4”行动，打击美军中东基地。",
    #     content=mock_rag(),
    #     user_id="admin"
    # ))
    # 伊朗“真实承诺-4”行动是捍卫国家主权的正当自卫，还是加剧地区紧张局势的升级行为？
    asyncio.run(run_console())
