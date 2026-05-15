/**
 * 将 /api/plugins/detail 返回的数据拼成完整 .vue 文本，供 compilePastedSfc 使用。
 *
 * 仅使用 **template + script + styles** 片段拼装（暂不使用服务端 `raw` 整文件形态）。
 * - template：`<template>` 内层 HTML（可含根节点 div）
 *
 * styles 可为：
 * - `string[]`：每项一段 CSS，默认不带 scoped
 * - `{ content: string, scoped?: boolean }[]`
 */

/**
 * 片段内 `<template v-if/v-else>` 须成对闭合；漏写 `</template>` 会触发 Vue 编译告警。
 * @param {string} inner
 */
function balanceInnerTemplateTags(inner) {
  const s = String(inner || "");
  const opens = (s.match(/<template\b/gi) || []).length;
  const closes = (s.match(/<\/template>/gi) || []).length;
  const missing = opens - closes;
  if (missing > 0 && missing <= 5) {
    return `${s}\n${"</template>\n".repeat(missing)}`;
  }
  return s;
}

/**
 * @param {unknown} entry
 * @returns {{ content: string, scoped: boolean }}
 */
function normalizeStyleEntry(entry) {
  if (entry == null) return { content: "", scoped: false };
  if (typeof entry === "string") {
    return { content: entry, scoped: false };
  }
  if (typeof entry === "object") {
    const o = entry;
    const content = o.content != null ? String(o.content) : "";
    const scoped = !!o.scoped;
    return { content, scoped };
  }
  return { content: String(entry), scoped: false };
}

/**
 * @param {Record<string, unknown>} d
 */
export function buildVueSourceFromPluginDetail(detail) {
  const d = detail && typeof detail === "object" ? detail : {};

  const rawTpl = d.template != null ? String(d.template) : "";
  const tpl = balanceInnerTemplateTags(rawTpl);
  let out = `<template>\n${tpl}\n</template>\n`;

  const scriptRaw = d.script != null ? String(d.script).trim() : "";
  if (scriptRaw) {
    out += `<script>\n${scriptRaw}\n</script>\n`;
  }

  const styles = Array.isArray(d.styles) ? d.styles : [];
  for (let i = 0; i < styles.length; i += 1) {
    const { content, scoped } = normalizeStyleEntry(styles[i]);
    if (!String(content).trim()) continue;
    const scopedAttr = scoped ? " scoped" : "";
    out += `<style${scopedAttr}>\n${content}\n</style>\n`;
  }

  return out;
}
