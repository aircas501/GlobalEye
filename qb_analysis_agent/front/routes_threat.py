"""
威胁情报分类数据查询路由
"""
import os
import redis
from dotenv import load_dotenv
from fastapi import APIRouter, HTTPException, Query

load_dotenv()

router = APIRouter()

CATEGORY_ORDER = ["动能", "非动能", "电子战", "网络", "在轨机动", "实战使用", "透明度"]


def _fmt(text: str | None) -> str | None:
    """将中文分号替换为<br>，使前端可换行显示"""
    if text is None:
        return None
    return text.replace("；", "；<br>")


def _get_redis():
    return redis.Redis(
        host=os.getenv("MEET_REDIS_HOST", "127.0.0.1"),
        port=int(os.getenv("MEET_REDIS_PORT", "6379")),
        password=os.getenv("MEET_REDIS_PASSWORD", ""),
        db=int(os.getenv("MEET_REDIS_DB", "0")),
        decode_responses=True,
    )


@router.get("/api/threat/countries")
async def list_countries():
    """获取所有已分类的国家/实体列表"""
    r = _get_redis()
    countries = sorted(r.smembers("threat:info:countries"))
    return {"code": "20000", "msg": "成功", "data": countries}


@router.get("/api/threat/categories")
async def list_categories():
    """获取七大类别列表"""
    return {"code": "20000", "msg": "成功", "data": CATEGORY_ORDER}


@router.get("/api/threat/info")
async def get_threat_info(
    country: str = Query(..., description="国家/实体名称，如：中国、俄罗斯"),
    category: str = Query(..., description="类别名称，如：动能、电子战"),
):
    """根据国家和类别查询威胁情报分类数据"""
    if category not in CATEGORY_ORDER:
        raise HTTPException(
            status_code=400,
            detail=f"无效的类别：{category}，有效类别：{', '.join(CATEGORY_ORDER)}",
        )

    r = _get_redis()
    key = f"threat:info:{country}"
    content = r.hget(key, category)

    if content is None:
        # 检查国家是否存在
        if not r.exists(key):
            countries = sorted(r.smembers("threat:info:countries"))
            raise HTTPException(
                status_code=404,
                detail=f"未找到国家/实体：{country}，已录入：{', '.join(countries)}",
            )
        # 国家存在但该类别无数据
        return {"code": "20000", "msg": "成功", "data": None}

    return {"code": "20000", "msg": "成功", "data": _fmt(content)}


@router.get("/api/threat/info/all")
async def get_threat_country_all(
country: str = Query(..., description="国家/实体名称，如：中国、俄罗斯"),
):
    """获取某个国家的全部7类数据"""
    r = _get_redis()
    key = f"threat:info:{country}"

    if not r.exists(key):
        countries = sorted(r.smembers("threat:info:countries"))
        raise HTTPException(
            status_code=404,
            detail=f"未找到国家/实体：{country}，已录入：{', '.join(countries)}",
        )

    all_data = r.hgetall(key)
    # 按 CATEGORY_ORDER 顺序排列，并对每个值做换行格式化
    ordered = {cat: _fmt(all_data.get(cat, "") or "") for cat in CATEGORY_ORDER}
    return {"code": "20000", "msg": "成功", "data": ordered}
