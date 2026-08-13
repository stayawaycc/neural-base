import { describe, expect, it } from 'vitest'
import { buildOutline, computeActiveId, slugify } from '@/utils/outline'

describe('slugify', () => {
  it('转小写、去特殊字符、空格转横线', () => {
    expect(slugify('Hello World 世界!')).toBe('hello-world-世界')
  })

  it('空串兜底 section', () => {
    expect(slugify('')).toBe('section')
  })
})

describe('buildOutline', () => {
  it('按序提取 h1~h3 层级与文本', () => {
    const items = buildOutline('# 总览\n\n## 环境\n\n### 依赖\n\n正文')
    expect(items).toEqual([
      { text: '总览', level: 1, id: '总览' },
      { text: '环境', level: 2, id: '环境' },
      { text: '依赖', level: 3, id: '依赖' },
    ])
  })

  it('重复标题 id 加 -2 后缀', () => {
    const items = buildOutline('## 安装\n\n## 安装')
    expect(items.map((i) => i.id)).toEqual(['安装', '安装-2'])
  })

  it('代码块内的 # 不算标题,空内容返回空数组', () => {
    expect(buildOutline('```\n# 不是标题\n```')).toEqual([])
    expect(buildOutline('')).toEqual([])
  })

  it('标题含链接时取链接文本', () => {
    const items = buildOutline('## [链接标题](https://example.com)')
    expect(items[0]).toMatchObject({ text: '链接标题', id: '链接标题' })
  })
})

describe('computeActiveId', () => {
  it('取 top 不超过基准线的最后一个标题', () => {
    const headings = [
      { id: 'a', top: 50 },
      { id: 'b', top: 100 },
      { id: 'c', top: 200 },
    ]
    expect(computeActiveId(headings, 120)).toBe('b')
    expect(computeActiveId(headings, 30)).toBeNull()
    expect(computeActiveId(headings, 500)).toBe('c')
  })
})
