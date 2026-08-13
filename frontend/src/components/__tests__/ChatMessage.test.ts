import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ChatMessage from '@/components/ChatMessage.vue'

const sources = [{ docId: 2, title: '参考文档', content: '片段内容', score: 0.9 }]

describe('ChatMessage', () => {
  // 内嵌 MarkdownRenderer 依赖 theme store
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('用户消息右对齐纯文本', () => {
    const wrapper = mount(ChatMessage, {
      props: { role: 'USER', content: '你好' },
    })
    expect(wrapper.text()).toContain('你好')
    expect(wrapper.classes()).toContain('justify-end')
  })

  it('助手消息展示引用来源与流式光标', () => {
    const wrapper = mount(ChatMessage, {
      props: { role: 'ASSISTANT', content: '回答', sources, streaming: true },
    })
    expect(wrapper.text()).toContain('参考来源')
    expect(wrapper.text()).toContain('参考文档')
    expect(wrapper.find('.nb-cursor').exists()).toBe(true)
  })

  it('非流式不显示光标', () => {
    const wrapper = mount(ChatMessage, {
      props: { role: 'ASSISTANT', content: '回答' },
    })
    expect(wrapper.find('.nb-cursor').exists()).toBe(false)
  })
})
