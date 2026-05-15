"""
角色 Skill 管理路由：角色列表 / 下载示例 / 上传 / 删除
"""
import json
import os

from fastapi import APIRouter, HTTPException, UploadFile, File
from fastapi.responses import Response

from agent.multiple_meeting.role_skills_manager import (
    roles,
    add_role_from_file,
    remove_role,
    get_roles_dir,
    generate_md_content,
)

router = APIRouter()


@router.post("/api/personas")
async def get_user_persona():
    """获取所有角色人物信息"""
    return {"code": "20000", "msg": "成功", "data": roles}


@router.get("/api/roles/skills")
async def list_role_skills():
    """获取所有角色 Skill 列表"""
    skill_list = [
        {"name": r.name, "identity": r.identity}
        for r in roles
    ]
    return {"code": "20000", "msg": "成功", "data": skill_list}


@router.get("/api/roles/sample")
async def download_sample_skill():
    """下载示例角色 Skill Markdown 文件"""
    sample_data = {
        "name": "示例角色",
        "identity": "请填写角色身份（如：美国总统、外交部长等）",
        "persona": "请填写角色人设，描述角色的性格特点、立场倾向等",
        "speaking_style": "请填写角色发言风格，描述语气、措辞特点等",
        "forbidden": "请填写角色禁止内容，描述哪些话题或行为不允许",
        "stance": "请填写角色核心立场，描述角色的主要观点和主张",
    }
    md_content = generate_md_content(sample_data)
    return Response(
        content=md_content,
        media_type="text/markdown",
        headers={
            "Content-Disposition": 'attachment; filename="示例角色Skill.md"'
        }
    )


@router.post("/api/roles/upload")
async def upload_role_skill(file: UploadFile = File(...)):
    """上传角色 Skill Markdown 文件，保存到 skills/roles/ 目录并更新缓存"""
    if not file.filename.endswith(".md") and not file.filename.endswith(".MD"):
        raise HTTPException(status_code=400, detail="仅支持 .md 文件")

    try:
        raw = await file.read()
        md_content = raw.decode("utf-8")

        roles_dir = get_roles_dir()
        os.makedirs(roles_dir, exist_ok=True)

        # 从 MD 中解析角色名
        import re
        name_match = re.search(r"^#\s+(.+)$", md_content, re.MULTILINE)
        if not name_match:
            raise HTTPException(status_code=400, detail="MD 文件中找不到角色姓名（# 标题）")
        role_name = name_match.group(1).strip()
        md_filename = f"{role_name}Skill.md"
        filepath = os.path.join(roles_dir, md_filename)

        with open(filepath, "w", encoding="utf-8") as f:
            f.write(md_content)

        role = add_role_from_file(filepath)

        return {
            "code": "20000",
            "msg": "角色上传成功",
            "data": {"name": role.name, "identity": role.identity}
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"上传失败: {str(e)}")


@router.delete("/api/roles/{role_name}")
async def delete_role_skill(role_name: str):
    """删除指定角色的 Skill 文件和缓存"""
    try:
        success = remove_role(role_name)
        if success:
            return {"code": "20000", "msg": f"角色 '{role_name}' 已删除", "data": ""}
        else:
            return {"code": "404", "msg": f"角色 '{role_name}' 不存在", "data": ""}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"删除失败: {str(e)}")
