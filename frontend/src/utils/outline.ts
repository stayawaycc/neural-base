import MarkdownIt from 'markdown-it'

export interface OutlineItem {
  text: string
  level: number
  id: string
}

/** 标题文本 → 锚点 id:小写、去特殊字符、空格转横线(与 MarkdownRenderer 锚点注入共用同一算法)。 */
export function slugify(text: string): string {
  const base = text
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .replace(/\s+/g, '-')
  return base || 'section'
}

/** 标题 token 的纯文本(含链接/代码时取 children 的 text/code_inline,而非原始 markdown 源码)。 */
function headingText(inline?: { content?: string; children?: { type: string; content: string }[] | null }): string {
  if (!inline || !inline.children) return inline?.content ?? ''
  return inline.children
    .filter((c) => c.type === 'text' || c.type === 'code_inline')
    .map((c) => c.content)
    .join('')
}

/** 提取 markdown 的 h1~h3 标题生成大纲;重复标题 id 依次加 -2/-3 后缀(与锚点注入顺序一致)。 */
export function buildOutline(content: string): OutlineItem[] {
  const tokens = new MarkdownIt().parse(content, {})
  const used = new Map<string, number>()
  const items: OutlineItem[] = []
  for (let i = 0; i < tokens.length; i++) {
    const t = tokens[i]
    if (t.type !== 'heading_open') continue
    const level = Number(t.tag.slice(1))
    if (level < 1 || level > 3) continue
    const text = headingText(tokens[i + 1])
    const base = slugify(text)
    const count = (used.get(base) ?? 0) + 1
    used.set(base, count)
    items.push({ text, level, id: count === 1 ? base : `${base}-${count}` })
  }
  return items
}

/** 滚动高亮:取 top 不超过基准线(top ≤ lineTop)的最后一个标题 id。 */
export function computeActiveId(
  headings: { id: string; top: number }[],
  lineTop: number,
): string | null {
  let current: string | null = null
  for (const h of headings) {
    if (h.top <= lineTop) current = h.id
    else break
  }
  return current
}
