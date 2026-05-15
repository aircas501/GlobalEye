"""
多角色战略推演发言调度系统
@Time: 2026/4/2
@Author: ZTL
@File: debate_scheduler.py
"""
import random
from typing import List, Dict, Optional


class DebateScheduler:
    """
    多角色战略推演发言调度系统
    用于控制多人国际危机/战略战术推演的发言顺序、回复对象和加权随机选择
    """
    
    def __init__(self, role_names: List[str], turns: int, reply_chain_length: int):
        """
        初始化调度器
        
        Args:
            role_names: 角色列表
            turns: 本轮基础发言次数
            reply_chain_length: 回复链长度, 设置为3会产生3条对话消息
        """
        # 参数验证
        if not role_names:
            raise ValueError("角色列表不能为空")
        if not all(isinstance(role, str) for role in role_names):
            raise ValueError("角色列表中的元素必须都是字符串")
        if len(role_names) != len(set(role_names)):
            raise ValueError("角色列表中存在重复的角色名称")
        if turns < 0:
            raise ValueError("每轮基础发言次数不能为负数")
        if reply_chain_length < 0:
            raise ValueError("回复链长度不能为负数")
        
        self.roles = role_names
        self.turns = turns
        self.reply_chain_length = reply_chain_length - 1
        self.speak_count = {r: 0 for r in role_names}  # 发言计数字典
        self.task_queue = []  # 待回复任务队列

    def _weighted_random_choice(self, candidates: List[str]) -> Optional[str]:
        """
        加权随机选择
        发言越少，被选中概率越高

        Args:
            candidates: 候选角色列表

        Returns:
            选中的角色名称，如果候选列表为空则返回 None
        """
        if not candidates:
            return None
        # 计算每个角色的权重
        weights = [1 / (self.speak_count[role] + 1) for role in candidates]
        # 使用 weighted random choice
        return random.choices(candidates, weights=weights, k=1)[0]

    def _get_task_for_speaker(self, speaker: str) -> Optional[Dict]:
        """
        获取指定发言人的待回复任务

        Args:
            speaker: 发言人角色名称

        Returns:
            任务字典，如果没有则返回 None
        """
        for i, task in enumerate(self.task_queue):
            if task['target'] == speaker:
                return self.task_queue.pop(i)
        return None

    def _select_speaker(self) -> str:
        """
        选择发言人
        - 有待回复任务 → 从有任务的角色中加权随机选
        - 无任务 → 从全部角色中加权随机选

        Returns:
            选中的发言人角色名称
        """
        # 获取所有有待回复任务的角色
        task_roles = list({task['target'] for task in self.task_queue})

        if task_roles:
            # 从有任务的角色中加权随机选
            speaker = self._weighted_random_choice(task_roles)
        else:
            # 从全部角色中加权随机选
            speaker = self._weighted_random_choice(self.roles)

        # 确保返回有效的发言人
        if speaker is None:
            # 如果没有有效发言人，返回第一个角色
            return self.roles[0]
        return speaker

    def _select_target(self, speaker: str) -> str:
        """
        确定回复对象

        Args:
            speaker: 发言人角色名称

        Returns:
            回复对象角色名称
        """
        # 从排除自己的所有角色中加权随机选对象
        other_roles = [role for role in self.roles if role != speaker]
        target = self._weighted_random_choice(other_roles)

        # 确保返回有效的目标
        if target is None:
            # 如果没有有效目标，返回第一个角色（排除自己）
            for role in self.roles:
                if role != speaker:
                    return role
            # 如果只有自己一个角色，返回自己
            return speaker
        return target

    def process_one_speech(self) -> Dict:
        """
        处理一次发言

        Returns:
            发言记录字典，包含 speaker, target, content
        """
        # 选择发言人
        speaker = self._select_speaker()

        # 检查发言人是否有待回复任务
        task = self._get_task_for_speaker(speaker)

        if task:
            # 必须回复任务指定的target
            target = task['speaker']
            # 减少剩余回复步骤
            task['remaining_steps'] -= 1
            # 如果还有剩余步骤，将任务放回队列
            if task['remaining_steps'] > 0:
                # 需要交换发言人和回复人
                task['speaker'] = task['target']
                task['target'] = target
                self.task_queue.append(task)
        else:
            # 无任务，随机选择回复对象
            target = self._select_target(speaker)
            # 新建任务，加入队列
            new_task = {
                'speaker': speaker,
                'target': target,
                'remaining_steps': self.reply_chain_length
            }
            self.task_queue.append(new_task)

        # 发言计数+1
        self.speak_count[speaker] += 1

        # 返回发言记录
        return {
            'speaker': speaker,
            'target': target,
            'content': ""
        }

    def run_one_round(self) -> List[Dict]:
        """
        运行一轮完整流程
        1. 执行 N 次基础发言
        2. N 用完后，继续处理所有待回复任务，直到队列为空
        3. 本轮结束，保留发言计数到下一轮

        Returns:
            本轮所有发言记录列表
        """
        speech_records = []

        # 执行 N 次基础发言
        for _ in range(self.turns):
            speech = self.process_one_speech()
            speech_records.append(speech)
        
        # 处理所有待回复任务，直到队列为空
        while self.task_queue:
            speech = self.process_one_speech()
            speech_records.append(speech)
        
        return speech_records


# 测试示例
if __name__ == "__main__":
    # 初始化调度器
    roles = ["特朗普", "拜登", "耶伦", "哈梅内伊"]
    N = 5000  # 每轮基础发言次数
    reply_chain_length = 5  # 回复链长度
    
    scheduler = DebateScheduler(roles, N, reply_chain_length)
    
    # 运行一轮
    print("=== 第一轮 ===")
    round1_records = scheduler.run_one_round()
    # for i, record in enumerate(round1_records, 1):
    #     print(f"发言 {i}: {record['content']}")
    
    # 打印发言计数
    print("\n发言计数:")
    for role, count in scheduler.speak_count.items():
        print(f"{role}: {count} 次")
