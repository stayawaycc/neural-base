import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { useThemeStore } from '@/stores/theme'

// vitest 默认不处理 CSS,?inline 导入返回空串;用带标记的假内容驱动切换断言
vi.mock('highlight.js/styles/github.css?inline', () => ({
  default: '.hljs{color:black}/* Theme: Git */',
}))
vi.mock('highlight.js/styles/github-dark.css?inline', () => ({
  default: '.hljs{color:white}/* Theme: GitHub Dark */',
}))

function hljsStyleContent(): string {
  const styles = Array.from(document.querySelectorAll('style'))
  const el = styles.find((s) => (s.textContent ?? '').includes('.hljs'))
  return el?.textContent ?? ''
}

describe('MarkdownRenderer', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.querySelectorAll('style').forEach((s) => {
      if ((s.textContent ?? '').includes('.hljs')) s.remove()
    })
  })

  it('渲染加粗与转义原始 HTML(消毒)', () => {
    const wrapper = mount(MarkdownRenderer, {
      props: { content: '**加粗** <script>alert(1)</script>' },
    })
    expect(wrapper.html()).toContain('<strong>加粗</strong>')
    expect(wrapper.html()).not.toContain('<script>')
    // html:false 第一道消毒:原始 <script> 被转义为文本,不可执行(文本内容仍保留)
    expect(wrapper.html()).toContain('&lt;script&gt;')
  })

  it('代码块渲染并注入复制按钮,点击复制调用 clipboard', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    const wrapper = mount(MarkdownRenderer, {
      props: { content: '```js\nconst a = 1\n```' },
    })
    await wrapper.vm.$nextTick()
    const btn = wrapper.find('.code-copy-btn')
    expect(btn.exists()).toBe(true)
    await btn.trigger('click')
    // hljs 输出保留围栏末尾换行,复制内容为 'const a = 1\n'
    expect(writeText).toHaveBeenCalledWith(expect.stringContaining('const a = 1'))
  })

  it('anchor-headings 模式给标题注入与 buildOutline 一致的 id,默认模式不注入', () => {
    const anchored = mount(MarkdownRenderer, {
      props: { content: '# 甲\n\n## 乙\n\n## 乙', anchorHeadings: true },
    })
    const anchoredHtml = anchored.html()
    expect(anchoredHtml).toContain('id="甲"')
    expect(anchoredHtml).toContain('id="乙"')
    expect(anchoredHtml).toContain('id="乙-2"')

    const plain = mount(MarkdownRenderer, {
      props: { content: '# 甲' },
    })
    expect(plain.html()).not.toContain('id="甲"')
  })

  it('高亮样式随主题多轮切换(亮→暗→亮)且仅注入一套', async () => {
    mount(MarkdownRenderer, { props: { content: '```js\nconst a = 1\n```' } })
    const themeStore = useThemeStore()
    const lightMarker = 'color:black'
    const darkMarker = 'color:white'

    expect(hljsStyleContent()).toContain(lightMarker)
    expect(hljsStyleContent()).not.toContain(darkMarker)

    themeStore.toggleTheme() // → dark
    await vi.waitFor(() => expect(hljsStyleContent()).toContain(darkMarker))
    expect(hljsStyleContent()).not.toContain(lightMarker)

    themeStore.toggleTheme() // → light(此前动态 import 缓存导致此处滞留 dark)
    await vi.waitFor(() => expect(hljsStyleContent()).toContain(lightMarker))
    expect(hljsStyleContent()).not.toContain(darkMarker)
  })

  it('含 h4+ 的文档锚点不错位(h4 跳过,后续标题拿到自己的 id)', () => {
    const anchored = mount(MarkdownRenderer, {
      props: { content: '# 甲\n\n#### 丁\n\n## 乙', anchorHeadings: true },
    })
    const html = anchored.html()
    expect(html).toContain('id="甲"')
    expect(html).toContain('id="乙"')
    expect(html).not.toContain('id="丁"')
    // h4 不能消费「乙」的 id:「乙」的 id 必须恰好出现一次,且没有别的元素带它
    expect(html.match(/id="乙"/g)).toHaveLength(1)
  })
})
