"""
@Time: 2026/3/31 10:55
@Author: ZTL
@File: role_skills_manager.py

角色 Skill 管理模块，从 skills/roles/ 目录动态加载 *Skill.md 角色文件并缓存到内存。
提供文件上传和删除的缓存更新接口，供 web_api.py 路由模块调用。

MD 文件格式：
    # {角色姓名}

    **身份**：{identity}

    **人设**：{persona}

    **发言风格**：{speaking_style}

    **禁止内容**：{forbidden}

    **核心立场**：{stance}
"""
import logging
import os
import re
from agent.multiple_meeting.entity.role import Role

# 角色文件存放目录（相对于项目根目录：skills/roles/）
_current_dir = os.path.dirname(os.path.abspath(__file__))
_project_root = os.path.dirname(os.path.dirname(_current_dir))
ROLES_DIR = os.path.join(_project_root, "skills", "roles")

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s: %(message)s"
)

# MD 字段 → Role 构造字段映射
_MD_FIELD_MAP = {
    "身份": "identity",
    "人设": "persona",
    "发言风格": "speaking_style",
    "禁止内容": "forbidden",
    "核心立场": "stance",
}

# 内存缓存，启动时从文件加载
roles = []


def _parse_md(filepath: str) -> Role:
    """解析 *Skill.md 文件，提取角色字段，返回 Role 对象

    MD 格式说明：
        - 第 1 行 `# {name}` 为角色姓名
        - 后续每行 `**{中文标签}**：{值}` 对应各字段
    """
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    data = {}

    # 提取标题行：`# 角色姓名`
    title_match = re.search(r"^#\s+(.+)$", content, re.MULTILINE)
    if title_match:
        data["name"] = title_match.group(1).strip()
    else:
        raise ValueError(f"无法从文件中解析角色姓名: {filepath}")

    # 提取各字段：`**身份**：值`
    for md_label, field_name in _MD_FIELD_MAP.items():
        # 匹配 **{label}**：{value}，支持中英文冒号
        pattern = r"\*\*" + re.escape(md_label) + r"\*\*[：:]\s*(.+?)(?=\n\*\*|\Z)"
        match = re.search(pattern, content, re.DOTALL)
        if match:
            data[field_name] = match.group(1).strip()
        else:
            raise ValueError(f"从 {filepath} 中找不到字段 '{md_label}'")

    return Role(
        name=data["name"],
        identity=data["identity"],
        persona=data["persona"],
        speaking_style=data["speaking_style"],
        forbidden=data["forbidden"],
        stance=data["stance"],
    )


def _get_md_filename(role_name: str) -> str:
    """根据角色名生成对应的 MD 文件名"""
    return f"{role_name}Skill.md"


def _get_md_filepath(role_name: str) -> str:
    """根据角色名生成对应的 MD 文件绝对路径"""
    return os.path.join(ROLES_DIR, _get_md_filename(role_name))


def load_roles_from_files():
    """从 skills/roles/ 目录加载所有 *Skill.md 角色文件到缓存"""
    global roles
    roles = []
    if not os.path.exists(ROLES_DIR):
        os.makedirs(ROLES_DIR, exist_ok=True)
        logging.info(f"[role_skills_manager] 创建角色目录: {ROLES_DIR}")
        return
    for filename in sorted(os.listdir(ROLES_DIR)):
        if not filename.endswith("Skill.md"):
            continue
        filepath = os.path.join(ROLES_DIR, filename)
        try:
            role = _parse_md(filepath)
            roles.append(role)
            logging.info(f"[role_skills_manager] 加载角色: {role.name}")
        except Exception as e:
            logging.error(f"[role_skills_manager] 加载角色文件 {filename} 失败: {e}")

    logging.info(f"[role_skills_manager] 共加载 {len(roles)} 个角色")


def add_role_from_file(filepath: str) -> Role:
    """从单个 MD 文件加载角色并加入缓存

    Args:
        filepath: *Skill.md 文件绝对路径

    Returns:
        Role: 解析后的角色对象
    """
    role = _parse_md(filepath)
    # 去重：同名角色只保留最新
    global roles
    roles = [r for r in roles if r.name != role.name]
    roles.append(role)
    logging.info(f"[role_skills_manager] 缓存新增/更新角色: {role.name}")
    return role


def remove_role(role_name: str) -> bool:
    """从缓存和文件系统中删除指定角色

    Args:
        role_name: 角色名称

    Returns:
        bool: 是否成功删除
    """
    global roles
    old_len = len(roles)
    roles = [r for r in roles if r.name != role_name]

    # 删除对应的 MD 文件
    filepath = _get_md_filepath(role_name)
    if os.path.exists(filepath):
        os.remove(filepath)
        logging.info(f"[role_skills_manager] 删除文件: {filepath}")

    deleted = len(roles) < old_len
    if deleted:
        logging.info(f"[role_skills_manager] 缓存移除角色: {role_name}")
    else:
        logging.error(f"[role_skills_manager] 角色不存在: {role_name}")
    return deleted


def get_roles_dir() -> str:
    """获取角色文件存放目录路径"""
    return ROLES_DIR


def generate_md_content(role_data: dict) -> str:
    """根据角色数据字典生成 *Skill.md 文件内容

    Args:
        role_data: 包含 name, identity, persona, speaking_style, forbidden, stance 的字典

    Returns:
        str: 格式化的 Markdown 内容
    """
    lines = [f"# {role_data['name']}", ""]
    for md_label in ["身份", "人设", "发言风格", "禁止内容", "核心立场"]:
        field_name = _MD_FIELD_MAP[md_label]
        lines.append(f"**{md_label}**：{role_data[field_name]}")
        lines.append("")
    return "\n".join(lines)


# 启动时自动加载
load_roles_from_files()
