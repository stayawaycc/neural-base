import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GlassCard from '@/components/GlassCard.vue'

describe('GlassCard', () => {
  it('渲染插槽内容并默认带 padded 与 glass-card 类', () => {
    const wrapper = mount(GlassCard, { slots: { default: '<p>内容</p>' } })
    expect(wrapper.classes()).toContain('glass-card')
    expect(wrapper.classes()).toContain('glass-card--padded')
    expect(wrapper.text()).toContain('内容')
  })

  it('accent 与 hoverable 生效', () => {
    const wrapper = mount(GlassCard, { props: { accent: 'violet', hoverable: true } })
    expect(wrapper.classes()).toContain('glass-card--violet')
    expect(wrapper.classes()).toContain('glass-card--hover')
  })
})
