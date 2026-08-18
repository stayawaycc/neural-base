import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ChatMessage from '@/components/ChatMessage.vue'

const sources = [{ docId: 2, title: '参考文档', content: '片段内容', score: 0.9 }]

describe('ChatMessage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('用户消息右对齐纯文本', () => {
    const wrapper = mount(ChatMessage, {
      props: { role: 'USER', content: '你好' },
    })
    expect(wrapper.text()).toContain('你好')
    expect(wrapper.classes()).toContain('msg-row--user')
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

  it('助手非流式消息渲染 👍👎,点击高亮项 emit null 取消', async () => {
    const wrapper = mount(ChatMessage, {
      props: { role: 'ASSISTANT', content: '回答', feedback: 'LIKE' },
    })
    const likeBtn = wrapper.find('.msg-action-btn')
    expect(likeBtn.exists()).toBe(true)
    expect(likeBtn.attributes('class')).toContain('msg-action-btn--active')
    await likeBtn.trigger('click')
    expect(wrapper.emitted('feedback')?.[0]).toEqual([null])
    const dislikeBtn = wrapper.findAll('.msg-action-btn')[1]
    await dislikeBtn.trigger('click')
    expect(wrapper.emitted('feedback')?.[1]).toEqual(['DISLIKE'])
  })

  it('流式与用户消息不显示反馈按钮', () => {
    const streaming = mount(ChatMessage, {
      props: { role: 'ASSISTANT', content: '', streaming: true },
    })
    expect(streaming.find('.msg-action-btn').exists()).toBe(false)
    const user = mount(ChatMessage, { props: { role: 'USER', content: '你好' } })
    expect(user.find('.msg-action-btn').exists()).toBe(false)
  })
})
