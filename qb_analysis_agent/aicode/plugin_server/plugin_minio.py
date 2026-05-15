"""
插件系统 Minio 操作

文件结构:
    Bucket: front-plugins
    Key:    {username}/{date}/{file_name}
    例:     front-plugins/zhangsan/20250401/data-table.vue
"""

import io
import os
import re
from dataclasses import dataclass

from dotenv import load_dotenv
from minio import Minio
from minio.error import S3Error

load_dotenv()

MINIO_BUCKET = "front-plugins"


@dataclass
class PluginFile:
    file_id: str             # 文件名去后缀 (data-table)
    file_name: str            # 文件名 (data-table.vue)
    date: str                 # 日期目录 (20250401)
    component_name: str       # Vue 组件 name 属性
    size: int                 # 字节数


def _get_client() -> Minio:
    return Minio(
       endpoint=os.getenv("MINIO_ENDPOINT", "47.99.70.126:9000"),
        access_key=os.getenv("MINIO_ACCESS_KEY", "minioadmin"),
        secret_key=os.getenv("MINIO_SECRET_KEY", "minioadmin"), 
        secure=False,
    )


def _ensure_bucket():
    client = _get_client()
    if not client.bucket_exists(MINIO_BUCKET):
        client.make_bucket(MINIO_BUCKET)


def _safe_name(username: str) -> str:
    return re.sub(r'[^\w\-]', '_', username) if username else "default"


def _object_key(username: str, date: str, file_name: str) -> str:
    return f"{_safe_name(username)}/{date}/{file_name}"


# ── 对外 API ────────────────────────────────────────────────


def list_plugins(username: str) -> list[PluginFile]:
    """列出用户所有 .vue 插件（按 date 倒序），同名只保留最新日期"""
    client = _get_client()
    prefix = f"{_safe_name(username)}/"

    by_name: dict[str, tuple[str, str, int]] = {}  # file_name → (date, obj_name, size)

    try:
        objects = list(client.list_objects(MINIO_BUCKET, prefix=prefix, recursive=True))
    except S3Error:
        return []

    for obj in objects:
        # object_name: zhangsan/20250401/data-table.vue
        rest = obj.object_name[len(prefix):]  # 20250401/data-table.vue
        if not rest.endswith(".vue"):
            continue
        parts = rest.split("/", 1)
        if len(parts) != 2:
            continue
        date, file_name = parts
        if file_name not in by_name or date > by_name[file_name][0]:
            by_name[file_name] = (date, obj.object_name, obj.size)

    result = []
    for file_name, (date, obj_name, size) in by_name.items():
        file_id = file_name[:-4]  # 去 .vue
        try:
            resp = client.get_object(MINIO_BUCKET, obj_name)
            source = resp.read().decode("utf-8")
            resp.close()
        except S3Error:
            continue
        component_name = _parse_component_name(source) or file_id
        result.append(PluginFile(
            file_id=file_id,
            file_name=file_name,
            date=date,
            component_name=component_name,
            size=size,
        ))

    result.sort(key=lambda p: p.file_id)
    return result


def get_plugin_source(username: str, file_id: str) -> str | None:
    """获取插件 .vue 源文件，自动取最新日期"""
    client = _get_client()
    prefix = f"{_safe_name(username)}/"
    best_key = None
    best_date = ""

    try:
        for obj in client.list_objects(MINIO_BUCKET, prefix=prefix, recursive=True):
            rest = obj.object_name[len(prefix):]
            parts = rest.split("/", 1)
            if len(parts) != 2:
                continue
            date, file_name = parts
            if file_name == f"{file_id}.vue" and date > best_date:
                best_date = date
                best_key = obj.object_name
    except S3Error:
        return None

    if not best_key:
        return None

    try:
        resp = client.get_object(MINIO_BUCKET, best_key)
        data = resp.read().decode("utf-8")
        resp.close()
        return data
    except S3Error:
        return None


def resolve_plugin(username: str, file_id: str) -> dict | None:
    """获取源文件 + 元信息，返回 { source, date, file_id, file_name } 或 None"""
    client = _get_client()
    prefix = f"{_safe_name(username)}/"
    best = None  # (date, obj_name)

    try:
        for obj in client.list_objects(MINIO_BUCKET, prefix=prefix, recursive=True):
            rest = obj.object_name[len(prefix):]
            parts = rest.split("/", 1)
            if len(parts) != 2:
                continue
            date, file_name = parts
            if file_name == f"{file_id}.vue" and (best is None or date > best[0]):
                best = (date, obj.object_name, file_name)
    except S3Error:
        return None

    if not best:
        return None

    date, obj_name, file_name = best
    client2 = _get_client()
    try:
        resp = client2.get_object(MINIO_BUCKET, obj_name)
        source = resp.read().decode("utf-8")
        resp.close()
    except S3Error:
        return None

    return {
        "source": source,
        "date": date,
        "file_id": file_id,
        "file_name": file_name,
    }


def put_plugin(username: str, date: str, file_name: str, source: str | bytes):
    """上传插件到 Minio"""
    _ensure_bucket()
    client = _get_client()
    key = _object_key(username, date, file_name)
    if isinstance(source, str):
        source = source.encode("utf-8")
    client.put_object(MINIO_BUCKET, key, io.BytesIO(source), len(source))


def delete_plugin(username: str, date: str, file_name: str):
    """删除插件"""
    client = _get_client()
    client.remove_object(MINIO_BUCKET, _object_key(username, date, file_name))


def _parse_component_name(source: str) -> str | None:
    m = re.search(r"name:\s*['\"]([^'\"]+)['\"]", source)
    return m.group(1) if m else None
