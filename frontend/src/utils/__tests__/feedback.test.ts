import { describe, expect, it } from 'vitest'
import { applyFeedbackTheme, message } from '@/utils/feedback'

describe('feedback theme', () => {
  it('applyFeedbackTheme 切换后 message 实例更换且仍可用', async () => {
    const before = message
    applyFeedbackTheme(true)
    const dark = message
    expect(dark).not.toBe(before)
    applyFeedbackTheme(false)
    expect(message).not.toBe(dark)
    // 切换后调用不抛错(happy-dom 下 naive-ui message 正常渲染)
    message.success('ok')
    expect(document.body.textContent).toContain('ok')
  })
})
