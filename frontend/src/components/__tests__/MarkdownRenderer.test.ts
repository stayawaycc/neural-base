import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

describe('MarkdownRenderer', () => {
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
})
