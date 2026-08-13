// happy-dom 与 DOMPurify 的兼容性补丁:
// DOMPurify 出于防 clobber 安全缓存 Node.prototype.nodeName 的 getter 并直接调用,
// 而 happy-dom 的 Node 基类 getter 恒返回 ''(真实逻辑挂在 Element.prototype 上),
// 导致所有元素标签名解析为空串,全部被 DOMPurify 当作未允许标签移除。
// 这里让基类 getter 委托到 tagName/#text,使 DOMPurify 在测试环境下正常工作。
const originalNodeName = Object.getOwnPropertyDescriptor(Node.prototype, 'nodeName')
if (originalNodeName?.get) {
  Object.defineProperty(Node.prototype, 'nodeName', {
    configurable: true,
    get(this: Node) {
      const tag = (this as unknown as { tagName?: unknown }).tagName
      if (typeof tag === 'string' && tag) return tag
      if (this.nodeType === 3) return '#text'
      return originalNodeName.get!.call(this)
    },
  })
}
