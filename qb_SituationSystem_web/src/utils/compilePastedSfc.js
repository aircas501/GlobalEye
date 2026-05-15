/**
 * 将 Vue2 单文件（template + script + style）编译为可 Vue.extend 的组件。
 * 供 AiIntelLiveMountPanel、PersonalizedPluginMountPanel / 生成对话框预览链路使用。
 * 约束：不支持 import 外部模块；script 须为可执行的 export default（勿粘贴不可信代码）。
 */

function stripImportLines(code) {
  return String(code || "").replace(/^\s*import\s+[\s\S]*?;\s*$/gm, "");
}

function extractBlock(source, tagName) {
  const re = new RegExp(`<${tagName}[^>]*>([\\s\\S]*?)<\\/${tagName}>`, "i");
  const m = String(source || "").match(re);
  return m ? m[1].trim() : "";
}

/** @param {string} s @param {number} idx */
function isIndexInsideHtmlComment(s, idx) {
  let pos = 0;
  while (pos < s.length) {
    const a = s.indexOf("<!--", pos);
    if (a === -1 || a > idx) return false;
    const b = s.indexOf("-->", a + 4);
    if (b === -1) return idx >= a;
    if (idx >= a && idx < b + 3) return true;
    pos = b + 3;
  }
  return false;
}

/**
 * 下一个 `<template…>` 开标签（不在 HTML 注释内）。
 * @param {string} s @param {number} from
 * @returns {{ index: number, len: number } | null}
 */
function findNextTemplateOpen(s, from) {
  const re = /<template\b[^>]*>/gi;
  re.lastIndex = Math.max(0, from);
  let m;
  while ((m = re.exec(s)) !== null) {
    if (!isIndexInsideHtmlComment(s, m.index)) {
      return { index: m.index, len: m[0].length };
    }
  }
  return null;
}

/**
 * 下一个 `</template>`（不在 HTML 注释内），避免注释里示例代码误结束 SFC 模板提取。
 * @param {string} s @param {number} from
 * @returns {{ index: number, len: number } | null}
 */
function findNextTemplateClose(s, from) {
  const re = /<\/template>/gi;
  re.lastIndex = Math.max(0, from);
  let m;
  while ((m = re.exec(s)) !== null) {
    if (!isIndexInsideHtmlComment(s, m.index)) {
      return { index: m.index, len: m[0].length };
    }
  }
  return null;
}

/**
 * 提取 SFC **最外层** `<template>` 的内层源码。
 * 根下可嵌套 `<template v-if / v-else-if / v-else>`；若用「第一个 `</template>`」截断（非贪婪正则），
 * 会把整段裁在子块闭合处，导致外层 `<template v-else>` 在编译字符串里未闭合 → Vue warn: no matching end tag。
 * 匹配 `</template>` / `<template` 时跳过 `<!-- … -->`，避免注释内字样提前截断（表现为根 div 未闭合等）。
 */
function extractRootTemplateInner(source) {
  const s = String(source || "");
  const openRe = /<template\b[^>]*>/i;
  const om = openRe.exec(s);
  if (!om) return "";

  const innerStart = om.index + om[0].length;
  let pos = innerStart;
  let depth = 1;

  while (depth > 0 && pos <= s.length) {
    const openHit = findNextTemplateOpen(s, pos);
    const closeHit = findNextTemplateClose(s, pos);

    const relOpen = openHit ? openHit.index : Infinity;
    const relClose = closeHit ? closeHit.index : Infinity;

    if (closeHit == null) return "";

    if (openHit && openHit.index < relClose) {
      depth += 1;
      pos = openHit.index + openHit.len;
    } else {
      depth -= 1;
      if (depth === 0) {
        return s.slice(innerStart, relClose).trim();
      }
      pos = relClose + closeHit.len;
    }
  }
  return "";
}

function extractAllStyles(source) {
  const s = String(source || "");
  const out = [];
  const re = /<style[^>]*>([\s\S]*?)<\/style>/gi;
  let m = re.exec(s);
  while (m) {
    out.push(m[1].trim());
    m = re.exec(s);
  }
  return out;
}

function evalExportDefault(scriptCode, Vue) {
  const cleaned = stripImportLines(scriptCode).trim();
  if (!cleaned) return {};
  if (!/\bexport\s+default\b/.test(cleaned)) {
    throw new Error("<script> 中须包含 export default { ... }（Demo 不支持纯注释或其它格式）");
  }

  const body = cleaned.replace(/export\s+default\s+/, "return ");
  try {
    const factory = new Function("Vue", `"use strict"; ${body}`);
    const exported = factory(Vue);
    if (!exported || (typeof exported !== "object" && typeof exported !== "function")) {
      throw new Error("export default 须为组件选项对象");
    }
    return exported;
  } catch (e) {
    const msg = e && e.message ? e.message : String(e);
    throw new Error(`脚本执行失败：${msg}`);
  }
}

/**
 * @param {import('vue').default} Vue
 * @param {string} source 完整 .vue 文本
 * @returns {{ Component: import('vue').Component, styles: string[] }}
 */
export function compilePastedSfc(Vue, source) {
  const template = extractRootTemplateInner(source);
  if (!template) {
    throw new Error("缺少 <template>，请粘贴完整 .vue 单文件");
  }

  const scriptRaw = extractBlock(source, "script");
  const styles = extractAllStyles(source);

  const rawOpts = scriptRaw ? evalExportDefault(scriptRaw, Vue) : {};
  const opts =
    typeof rawOpts === "function"
      ? { extends: rawOpts, template }
      : { ...(rawOpts && typeof rawOpts === "object" ? rawOpts : {}), template };

  return { Component: Vue.extend(opts), styles };
}
