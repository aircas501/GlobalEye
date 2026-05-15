"""
@Time: 2026/5/7 14:16
@Author: ZTL
@File: threat_information.py
"""
import json
import os
import sys
import redis
from openai import OpenAI
from dotenv import load_dotenv

# Windows控制台UTF-8编码支持
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# 加载环境变量
load_dotenv(os.path.join(os.path.dirname(__file__), "..", ".env"))

# 初始化大模型客户端
client = OpenAI(
    base_url=os.getenv("LLM_BASE_URL", "https://api.deepseek.com"),
    api_key=os.getenv("LLM_API_KEY"),
)
MODEL = os.getenv("LLM_MODEL", "deepseek-chat")

# 七大类别标签
CATEGORY_ORDER = ["动能", "非动能", "电子战", "网络", "在轨机动", "实战使用", "透明度"]


def read_file(file_path: str) -> str:
    """读取文件内容"""
    with open(file_path, "r", encoding="utf-8") as f:
        return f.read()


def parse_with_llm(text: str) -> list[dict]:
    """
    调用大模型解析文本，识别每个国家及其7类分类内容。
    大模型只做结构化提取，不修改原文内容。
    返回: [{"country": "中国", "categories": {"动能": "...", ...}}, ...]
    """
    categories_label = "、".join(CATEGORY_ORDER)

    response = client.chat.completions.create(
        model=MODEL,
        messages=[
            {
                "role": "system",
                "content": (
                    "你是一个精准的文本解析器。你的任务是从输入文本中识别每个国家/实体及其分类内容，"
                    "输出为JSON格式。\n\n"
                    "## 输入格式说明\n"
                    "文本由多个国家/实体块组成。每个块的结构为：\n"
                    "- 第一行：国家/实体名称（如：中国、美国、俄罗斯、伊朗等）\n"
                    f"- 后续行：以以下七个类别名之一开头的行：{categories_label}\n"
                    "- 不同国家/实体之间以空行分隔\n\n"
                    "## 任务要求\n"
                    "1. 识别所有国家/实体名称\n"
                    "2. 将每行分类内容归属到对应类别下\n"
                    "3. **严禁修改任何一行原文内容**——类别下的文本必须与输入完全一致，一字不改\n"
                    "4. 如果某国家缺少某个类别，该类别值设为空字符串\"\"\n\n"
                    "## 输出格式\n"
                    "严格输出以下JSON结构（不要输出任何其他文字）：\n"
                    '[\n'
                    '  {\n'
                    '    "country": "国家名",\n'
                    '    "categories": {\n'
                    '      "动能": "原文内容（一字不改）",\n'
                    '      "非动能": "原文内容（一字不改）",\n'
                    '      "电子战": "原文内容（一字不改）",\n'
                    '      "网络": "原文内容（一字不改）",\n'
                    '      "在轨机动": "原文内容（一字不改）",\n'
                    '      "实战使用": "原文内容（一字不改）",\n'
                    '      "透明度": "原文内容（一字不改）"\n'
                    '    }\n'
                    '  },\n'
                    '  ...\n'
                    ']\n'
                ),
            },
            {"role": "user", "content": text},
        ],
        temperature=0.0,  # 最低温度，确保精确还原
        max_tokens=16000,
    )

    # 解析大模型返回的JSON
    raw_output = response.choices[0].message.content.strip()
    # 去除可能的markdown代码块标记
    if raw_output.startswith("```"):
        raw_output = raw_output.split("\n", 1)[-1]
        if raw_output.endswith("```"):
            raw_output = raw_output[:-3]
        raw_output = raw_output.strip()
    if raw_output.startswith("json"):
        raw_output = raw_output[4:].strip()

    blocks = json.loads(raw_output)
    return blocks


def print_blocks(blocks: list[dict]) -> None:
    """将解析后的国家分类数据输出到控制台"""
    print("=" * 80)
    print(f"太空威胁评估 — 各国反太空能力分类（共 {len(blocks)} 个国家/实体）")
    print("=" * 80)

    for i, block in enumerate(blocks):
        country = block["country"]
        categories = block["categories"]

        if i > 0:
            print()

        print(country)
        for cat in CATEGORY_ORDER:
            content = categories.get(cat, "").strip()
            if content:
                # 去除LLM可能已带的类别前缀，统一由我们添加
                for prefix in (f"{cat}：", f"{cat}:", f"{cat} :"):
                    if content.startswith(prefix):
                        content = content[len(prefix):].strip()
                        break
                print(f"{cat}：{content}")
            else:
                print(f"{cat}：无")


def save_to_redis(blocks: list[dict]) -> None:
    """
    将分类数据存入Redis。
    数据结构：
    - threat:info:{国家名} → Hash，字段：动能/非动能/电子战/网络/在轨机动/实战使用/透明度
    - threat:info:countries → Set，存储所有国家名
    """
    r = redis.Redis(
        host=os.getenv("MEET_REDIS_HOST", "127.0.0.1"),
        port=int(os.getenv("MEET_REDIS_PORT", "6379")),
        password=os.getenv("MEET_REDIS_PASSWORD", ""),
        db=int(os.getenv("MEET_REDIS_DB", "0")),
        decode_responses=True,
    )

    pipe = r.pipeline()
    countries = []

    for block in blocks:
        country = block["country"]
        categories = block["categories"]
        key = f"threat:info:{country}"

        # 先清除旧数据
        pipe.delete(key)

        # 将各分类内容存入Hash
        mapping = {}
        for cat in CATEGORY_ORDER:
            content = categories.get(cat, "").strip()
            if content:
                # 去除LLM可能已带的类别前缀
                for prefix in (f"{cat}：", f"{cat}:", f"{cat} :"):
                    if content.startswith(prefix):
                        content = content[len(prefix):].strip()
                        break
                mapping[cat] = content
        if mapping:
            pipe.hset(key, mapping=mapping)

        countries.append(country)

    # 存储国家名集合
    pipe.delete("threat:info:countries")
    if countries:
        pipe.sadd("threat:info:countries", *countries)

    pipe.execute()
    print(f"已存入Redis: {len(countries)} 个国家/实体 → Hash: threat:info:{{国家名}}, Set: threat:info:countries")


def main():
    file_path = "C:/b.txt"

    print("读取文件 C:/b.txt ...")
    text = read_file(file_path)
    print(f"读取完成，共 {len(text)} 字符\n")

    print("调用大模型解析文本...")
    blocks = parse_with_llm(text)
    print(f"大模型识别到 {len(blocks)} 个国家/实体:")
    for b in blocks:
        cats = b.get("categories", {})
        cat_names = [c for c in CATEGORY_ORDER if cats.get(c)]
        print(f"  - {b['country']} ({len(cat_names)} 类)")
    print()

    print("存入Redis...")
    save_to_redis(blocks)
    print()

    print_blocks(blocks)


if __name__ == "__main__":
    main()
