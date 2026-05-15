"""
@Time: 2026/3/31 10:42
@Author: LxpStu
@File: role.py
"""
class Role:
    """
    角色属性定义
    Attributes:
        # country: 国别
        name: 姓名
        identity: 身份
        persona: 人设
        speaking_style: 发言风格
        forbidden: 禁止
        stance: 立场
    """

    def __init__(self, name, identity, persona, speaking_style, forbidden, stance):
        # self.country = country
        self.name = name
        self.identity = identity
        self.persona = persona
        self.speaking_style = speaking_style
        self.forbidden = forbidden
        self.stance = stance