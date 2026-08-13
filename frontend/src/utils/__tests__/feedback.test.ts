import { describe, expect, it, vi } from 'vitest'
import { applyFeedbackTheme, message } from '@/utils/feedback'

describe('feedback theme', () => {
  it('applyFeedbackTheme 切换后 message 实例更换且仍可用', async () => {
    const before = message
    applyFeedbackTheme(true)
    const dark = message
    expect(dark).not.toBe(before)
    applyFeedbackTheme(false)
    expect(message).not.toBe(dark)
    // 切换后调用不抛错(naive-ui message 异步挂载,等待其渲染)
    message.success('ok')
    await vi.waitFor(() => {
      expect(document.body.textContent).toContain('ok')
    })
  })

  it('多次切换不累积 discrete 容器(旧实例已 unmount)', () => {
    const containers = () => document.body.querySelectorAll('.n-discrete-container, [data-discrete]').length
    const before = containers()
    applyFeedbackTheme(true)
    applyFeedbackTheme(false)
    applyFeedbackTheme(true)
    expect(containers()).toBeLessThanOrEqual(before + 1)
  })
})
