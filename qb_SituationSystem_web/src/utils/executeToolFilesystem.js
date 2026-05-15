/**
 * 在浏览器端执行 AI 委托的文件操作工具（File System Access API）
 * 逻辑与 public/frontend demo 对齐；路径严格限制在用户所选文件夹内。
 */

const DANGEROUS_PATH = /^(\.\.(\/|\\|$))|^([a-zA-Z]:[\/\\])|^[\/\\]/;

function validatePath(filePath) {
  if (!filePath || typeof filePath !== "string") {
    return { error: "❌ 安全限制：路径为空或无效" };
  }
  let normalized = filePath.replace(/\\/g, "/");
  if (normalized.startsWith("/") || DANGEROUS_PATH.test(normalized)) {
    return {
      error: `❌ 安全限制：禁止使用绝对路径 "${filePath}"，所有文件操作必须在用户选择的项目文件夹内`
    };
  }
  const segments = normalized.split("/");
  const safe = [];
  for (const seg of segments) {
    if (seg === "" || seg === "." || seg === "..") {
      return {
        error: `❌ 安全限制：路径中禁止包含 "."、".." 或空段 "${filePath}"，所有操作限制在项目文件夹内`
      };
    }
    safe.push(seg);
  }
  return { path: safe.join("/") };
}

async function resolvePath(handle, filePath, createDirs = false) {
  const parts = filePath.replace(/\\/g, "/").split("/");
  let current = handle;
  for (let i = 0; i < parts.length - 1; i += 1) {
    try {
      current = await current.getDirectoryHandle(parts[i]);
    } catch {
      if (createDirs) {
        current = await current.getDirectoryHandle(parts[i], { create: true });
      } else {
        return null;
      }
    }
  }
  const last = parts[parts.length - 1];
  return { parent: current, name: last };
}

async function resolveFile(handle, filePath) {
  const resolved = await resolvePath(handle, filePath);
  if (!resolved) return null;
  try {
    return await resolved.parent.getFileHandle(resolved.name);
  } catch {
    return null;
  }
}

async function readFile(handle, filePath) {
  const check = validatePath(filePath);
  if (check.error) return check.error;
  const fileHandle = await resolveFile(handle, check.path);
  if (!fileHandle) return `❌ 文件不存在: ${filePath}`;
  const file = await fileHandle.getFile();
  const content = await file.text();
  const lines = content.split("\n");
  return lines.map((l, i) => `${String(i + 1).padStart(4)}\t${l}`).join("\n");
}

async function writeFile(handle, filePath, content) {
  const check = validatePath(filePath);
  if (check.error) return check.error;
  const resolved = await resolvePath(handle, check.path, true);
  if (!resolved) return `❌ 无法创建: ${filePath}`;
  const fileHandle = await resolved.parent.getFileHandle(resolved.name, { create: true });
  const writable = await fileHandle.createWritable();
  await writable.write(content);
  await writable.close();
  return `✅ 已写入: ${filePath} (${content.split("\n").length} 行)`;
}

async function editFile(handle, filePath, oldString, newString) {
  const check = validatePath(filePath);
  if (check.error) return check.error;
  const fileHandle = await resolveFile(handle, check.path);
  if (!fileHandle) return `❌ 文件不存在: ${filePath}`;
  const file = await fileHandle.getFile();
  const content = await file.text();
  const count = content.split(oldString).length - 1;
  if (count === 0) return "❌ 未找到匹配文本，old_string 在文件中不存在";
  if (count > 1) return `❌ old_string 匹配了 ${count} 处，不唯一。请提供更长的上下文使其唯一。`;
  const newContent = content.replace(oldString, newString);
  const writable = await fileHandle.createWritable();
  await writable.write(newContent);
  await writable.close();
  return `✅ 已编辑: ${filePath}`;
}

async function glob(handle, pattern) {
  const regex = globToRegex(pattern);
  const results = [];

  async function walk(dirHandle, prefix) {
    for await (const [name, h] of dirHandle.entries()) {
      const path = prefix ? `${prefix}/${name}` : name;
      if (h.kind === "directory") {
        await walk(h, path);
      } else if (regex.test(path) || regex.test(name)) {
        results.push(path);
      }
    }
  }

  await walk(handle, "");
  if (!results.length) return `未找到匹配 '${pattern}' 的文件`;
  return (
    results
      .slice(0, 100)
      .map((f) => `  📄 ${f}`)
      .join("\n") + (results.length > 100 ? `\n  ... 还有 ${results.length - 100} 个结果` : "")
  );
}

function globToRegex(pattern) {
  const re = pattern
    .replace(/\./g, "\\.")
    .replace(/\*\*/g, "<<DOUBLESTAR>>")
    .replace(/\*/g, "[^/]*")
    .replace(/<<DOUBLESTAR>>/g, ".*")
    .replace(/\?/g, ".");
  return new RegExp(`^${re}$`);
}

async function grep(handle, pattern, searchPath) {
  const searchRoot = searchPath
    ? (() => {
        const check = validatePath(searchPath);
        return check.error ? "" : check.path;
      })()
    : "";
  if (searchPath && !searchRoot) return `❌ 安全限制：搜索路径 "${searchPath}" 无效`;

  let regex;
  try {
    regex = new RegExp(pattern);
  } catch (e) {
    return `❌ 无效正则: ${e && e.message ? e.message : String(e)}`;
  }
  const results = [];

  async function walkAndSearch(dirHandle, prefix) {
    for await (const [name, h] of dirHandle.entries()) {
      const path = prefix ? `${prefix}/${name}` : name;
      if (h.kind === "directory") {
        if (name === "node_modules" || name === "__pycache__" || name === ".git" || name === "target") {
          continue;
        }
        await walkAndSearch(h, path);
      } else {
        const textExts = [
          ".py",
          ".js",
          ".ts",
          ".jsx",
          ".tsx",
          ".java",
          ".go",
          ".rs",
          ".c",
          ".cpp",
          ".h",
          ".html",
          ".css",
          ".sql",
          ".sh",
          ".yaml",
          ".yml",
          ".json",
          ".md",
          ".txt",
          ".xml",
          ".toml",
          ".ini",
          ".cfg",
          ".vue"
        ];
        const ext = `.${name.split(".").pop()}`;
        if (!textExts.includes(ext)) continue;
        if (searchRoot && !path.startsWith(searchRoot.replace(/\\/g, "/"))) continue;
        try {
          const file = await h.getFile();
          const content = await file.text();
          const lines = content.split("\n");
          for (let i = 0; i < lines.length; i += 1) {
            if (regex.test(lines[i])) {
              results.push(`${path}:${i + 1}: ${lines[i].trim().slice(0, 120)}`);
              if (results.length >= 50) return;
            }
          }
        } catch {
          /* skip */
        }
      }
    }
  }

  const startDir = searchRoot ? await resolvePath(handle, searchRoot) : null;
  const dirToSearch = startDir ? await startDir.parent.getDirectoryHandle(startDir.name) : handle;

  await walkAndSearch(dirToSearch, searchRoot || "");
  if (!results.length) return `未找到匹配 '${pattern}' 的内容`;
  return results.join("\n");
}

/**
 * @param {FileSystemDirectoryHandle | null} handle
 * @param {string} tool
 * @param {Record<string, unknown>} args
 * @returns {Promise<string>}
 */
export async function executeTool(handle, tool, args) {
  if (!handle) return "❌ 未选择项目文件夹";

  try {
    switch (tool) {
      case "read_file":
        return await readFile(handle, args.file_path);
      case "write_file":
        return await writeFile(handle, args.file_path, args.content);
      case "edit_file":
        return await editFile(handle, args.file_path, args.old_string, args.new_string);
      case "glob":
        return await glob(handle, args.pattern);
      case "grep":
        return await grep(handle, args.pattern, args.path || "");
      default:
        return `❌ 未知工具: ${tool}`;
    }
  } catch (e) {
    return `❌ 工具执行异常: ${e && e.message ? e.message : String(e)}`;
  }
}
