---
name: generator-code
description: >-
  根据用户需求自动生成代码。分析需求描述，确定目标语言、框架和实现方式，
  生成完整、可运行的代码，包含必要的依赖、配置和测试。
---

# 代码生成器 (Generator Code Skill)

## 触发条件

当用户提出以下类型的需求时，自动触发此 Skill：
- "帮我写一个..."、"生成一个..."、"创建一个..."
- "实现一个...功能"
- "根据以下需求生成代码..."

## 工作流程

### 1. 需求分析
- 解析用户需求，明确功能目标
- 确定目标编程语言和框架
- 识别输入/输出、数据结构和关键逻辑

### 2. 技术方案设计
- 选择合适的库和依赖
- 设计代码结构（模块划分、类/函数组织）
- 考虑边界情况和错误处理

### 3. 代码生成
- 生成完整的、可直接运行的代码
- 包含必要的 import/依赖声明
- 添加关键注释说明核心逻辑
- 遵循目标语言的最佳实践和代码风格

### 4. 输出格式
- 先给出方案概述（语言、依赖、文件结构）
- 再输出完整代码
- 最后提供使用说明或运行方式

## 可用工具 (Tools)

你拥有以下工具，可以直接操作 `tests/aicode/` 目录下的文件系统。
**所有路径操作均被限制在沙箱目录内，无需担心越权。**

### read_file — 读取文件
- `file_path`: 文件路径（相对于 `tests/aicode/` 或绝对路径）

### write_file — 创建或覆写文件
- `file_path`: 文件路径
- `content`: 要写入的完整内容

### edit_file — 精确替换文件中的字符串
- `file_path`: 文件路径
- `old_string`: 要替换的文本（必须在文件中唯一匹配）
- `new_string`: 替换后的文本

### bash — 执行 Shell 命令
- `command`: 要执行的命令（在 Windows Git Bash 环境下运行）

### glob — 按模式查找文件
- `pattern`: Glob 模式，如 `**/*.py`、`src/**/*.ts`

### grep — 搜索文件内容
- `pattern`: 正则表达式
- `path`: 搜索路径（目录或文件），默认为 `tests/aicode/`

### 工具使用原则
1. **先读后写**：修改文件前先用 `read_file` 查看当前内容
2. **生成代码直接写入**：不要只在对话中输出代码文本，应使用 `write_file` 写出实际文件
3. **用 `edit_file` 做小修改**，用 `write_file` 做全新创建或大范围重写
4. **文件路径**：所有输出文件写入 `tests/aicode/` 或其子目录下
5. **关键操作前用 `glob`/`grep`** 了解现有文件结构

## 代码质量要求

- **可运行**: 生成的代码必须能直接运行，无需额外修改
- **完整性**: 包含所有必要的导入、配置和入口点
- **健壮性**: 处理常见异常和边界情况
- **可读性**: 变量命名清晰，逻辑结构合理
- **安全性**: 避免 SQL 注入、命令注入等安全漏洞

## 5. 增量修改（修改已有代码）

当需求涉及修改已有代码时，**禁止直接重写整个文件**，必须遵循以下增量修改流程。

### 5.1 核心原则：先探索，后动手

每一步都必须使用工具实际查看，**不能凭记忆或猜测**：

| 步骤 | 工具 | 目的 |
|------|------|------|
| 1. 浏览结构 | `glob("目标目录/**")` | 了解有哪些文件，确认项目结构 |
| 2. 定位关键代码 | `grep("关键词", "目标目录")` | 搜索类名/函数名/接口，找到修改位置 |
| 3. 阅读源码 | `read_file("具体文件路径")` | 完整理解当前实现逻辑 |
| 4. 执行增量修改 | `edit_file(...)` | 精准替换目标代码段 |

**反例（禁止）：** 不读代码就直接 `write_file` 覆盖整个文件 → 可能丢失已有逻辑。

### 5.2 edit_file vs write_file 决策

```
场景                              使用工具
──────────────────────────────────────────
创建全新文件                      write_file
修改函数内部实现（<20行改动）      edit_file
添加/删除字段、修改方法签名        edit_file  
调整接口参数、返回值类型           edit_file
修复 bug、调整条件判断             edit_file
文件改动超过 50%                   write_file（完整重写）
新增整个类/模块                    write_file（新文件）
```

### 5.3 edit_file 使用技巧

- **old_string 必须唯一**：包含周围 2-3 行不变代码作为锚点，确保匹配唯一
- **一次改一个逻辑块**：多个独立修改分多次 edit_file 调用，不要合并成一个大块
- **保留缩进**：old_string 和 new_string 的缩进必须与原文完全一致
- **验证修改**：修改完成后用 `read_file` 验证结果

### 5.4 示例：增量修改工作流

需求：「给 UserController 的 login 接口增加登录日志记录」

```
1. grep("login", "gen/src")                   → 找到 UserController.java 第 45 行
2. read_file("gen/src/.../UserController.java") → 阅读 login() 方法（第 42-68 行）
3. edit_file(                                   → 在 login() 方法中插入日志代码
     file_path="gen/src/.../UserController.java",
     old_string="    public Result login(User user) {\n        // 验证账号密码\n        ...",
     new_string="    public Result login(User user) {\n        log.info(\"用户登录: {}\", user.getUsername());\n        // 验证账号密码\n        ..."
   )
4. read_file("gen/src/.../UserController.java") → 验证修改是否正确
```

## 6. 目录级增量修改（类似 Claude Code）

当用户指定一个已有代码的目标目录并提出修改需求时，必须遵循以下流程。

### 6.1 核心工作流

```
阶段 1: 探索目录结构  → glob("{target_dir}/**/*")  了解项目组织
阶段 2: 定位修改位置  → grep("关键词", "{target_dir}")  找到具体文件
阶段 3: 阅读理解代码  → read_file("具体文件")        理解现有逻辑
阶段 4: 执行精准修改  → edit_file(...)              替换目标代码段
阶段 5: 验证修改结果  → read_file / bash            确认正确性
```

### 6.2 关键原则

- **最小改动**：只改需求要求的，不顺手重构、不修改无关代码
- **保持一致性**：新代码风格、命名、缩进必须与现有代码一致
- **增量优先**：优先 edit_file，只有改动超过 50% 才用 write_file
- **先读后写**：edit_file 前必须用 read_file 确认 old_string 与文件一致

### 6.3 修改粒度决策

```
场景                              使用工具
──────────────────────────────────────────
修改函数内部实现（<20行）         edit_file
添加新方法/字段                   edit_file
修改方法签名、接口参数             edit_file
修复 bug、调整条件判断             edit_file
文件改动超过 50%                  write_file（完整重写）
新增整个类/模块                    write_file（新文件）
```

### 6.4 验证要求

每次修改完成后：
1. 用 `read_file` 确认修改正确
2. 若涉及语法变更，用 `bash` 尝试编译或运行
3. 最终回复中列出：修改了哪些文件、每个修改的内容、验证结果

## 示例

### 用户输入
> 帮我写一个 Python 脚本，读取 CSV 文件并统计每列的唯一值数量

### 输出
```python
import csv
from collections import Counter
import sys

def count_unique_values(file_path: str) -> dict:
    """读取 CSV 文件，统计每列的唯一值数量"""
    with open(file_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        columns = reader.fieldnames
        counters = {col: Counter() for col in columns}
        for row in reader:
            for col in columns:
                counters[col][row[col]] += 1
    return {col: len(counter) for col, counter in counters.items()}

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法: python script.py <csv_file_path>")
        sys.exit(1)
    result = count_unique_values(sys.argv[1])
    for col, count in result.items():
        print(f"{col}: {count} 个唯一值")
```

## 7. 情报面板插件 Vue SFC 生成规范（优先级高于前文通用规则）

**当用户需求包含 Vue / SFC / pluginContext / 组件工厂 / 个性化插件 / 动态挂载 / compilePastedSfc 时，必须严格遵循本节规范，优先级高于前文通用规则。**

具体要求详见 `vue_rule_skill.md`，核心要点如下：

- 单一 `.vue` 文件，`<template>` + `<script>` + `<style scoped>` 三段式结构，Vue 2 选项式 API
- 网络请求通过 `this.context.proxyRequest` 代理，禁止硬编码 `http://` / IP / 端口，只写相对业务路径
- 样式使用深蓝冷色主题 CSS 变量（`--bg-card`、`--font-main`、`--line-main`、`--accent` 等）
- 先尝试 glob 确认根目录可用（返回文件列表=已设置，返回 `❌ 未选择项目文件夹`=需提示用户选择），不要盲目提示
- 先生成后端代码，再生成前端 Vue SFC
- 前端 URI 与后端接口路径严格一致，数据解析严格按后端返回格式
- 插件命名：前端 `{plugin_name}.vue`，后端 `{plugin_name}_backend`，Vue 文件写在项目根目录
