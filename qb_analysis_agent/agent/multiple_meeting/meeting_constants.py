"""
@Time: 2026/4/3 16:23
@Author: ZTL
@File: meeting_constants.py.py
"""
# meeting_constants.py
class ResponseCode:
    """接口返回码常量类"""
    CODE_COUNTRY = "21000"  # 国家码
    CODE_ROLE = "21001"  # 角色码
    CODE_TOPIC_GENERATION = "21002"  # 议题码
    CODE_POSITION = "21003"  # 角色发言
    CODE_POSITION_MESSAGE_ID = "21004"  # 角色发言最后的完整消息对应的 MessageId
    CODE_DEBATE = "21005"  # 角色回复
    CODE_DEBATE_MESSAGE_ID = "21006"  # 角色回复最后的完整消息对应的 MessageId
    CODE_DELETE_MESSAGE = "21007"  # 删除消息
    CODE_SAVE_ROLES = "21008"  # 保存本轮讨论的角色响应码
    CODE_GET_CHAT_HISTORY = "21009"  # 查询历史消息列表
    CODE_DEL_CHAT_HISTORY = "21010"  # 删除历史消息列表
    CODE_SUMMARY = "21011"  # 辩论总结
