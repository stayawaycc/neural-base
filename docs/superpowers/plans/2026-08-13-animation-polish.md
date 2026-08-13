# 动画打磨 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐动效规范:带 key 的页面切换过渡(淡入+上滑)、三个列表页骨架屏、按钮按压与上传涟漪微交互,全部尊重 prefers-reduced-motion。

**Architecture:** 纯 CSS + Naive UI NSkeleton,零新依赖。页面切换用 `key=route.fullPath` 强制区分组件实例;骨架屏以各页本地 `loading` ref 控制首载;微交互用全局 `.btn-press`/`.upload-ripple`/`.fade-in` 类。

**Tech Stack:** Vue 3.5 + Naive UI 2 + Tailwind;vitest + happy-dom;CDP 浏览器验收。

**规格:** `docs/superpowers/specs/2026-08-13-animation-polish-design.md`

## Global Constraints

- 不新增依赖;CSS 动画全部尊重 `prefers-reduced-motion: reduce`(禁用/弱化)
- 测试命令:`cd frontend && npx vitest run`、`npx vue-tsc --noEmit`、`npm run build`;CDP:proxy `http://localhost:3456`,前端 `http://localhost:5173`
- 纯 CSS 行为无单测,验证方式 = 现有 58 测试回归 + typecheck/build + CDP 断言;每任务明确列出验证命令
- 不改 naive-ui 按钮(自带动效),`.btn-press` 只加自绘元素
- commit 英文 subject;逐任务提交

---

### Task 1: 页面切换 transition 重做(实验驱动定稿)

**Files:**
- Modify: `frontend/src/App.vue`(transition 接线)

**Interfaces:**
- Produces: SPA 路由切换动画;旧页完全卸载(此前空白 bug 的回归约束)

- [ ] **Step 1: 接线(实验版本 A:key + out-in)**

`App.vue` 中(当前无 transition 的 router-view)改为:

```html
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" :key="route.fullPath" />
          </transition>
        </router-view>
```

- [ ] **Step 2: CDP 验证版本 A**

Run(CDP eval,新 tab 打开 `http://localhost:5173/kb`):

```bash
curl -s -X POST --data-raw 'http://localhost:5173/kb' http://localhost:3456/new
```

等 3 秒后 eval 点击 `a[href="/chat"]`,再等 3 秒后 eval:

```js
(() => {
  const main = document.querySelector('main')
  return JSON.stringify({
    url: location.pathname,
    mainLen: main.innerHTML.length,
    hasChat: main.innerText.includes('会') || main.querySelectorAll('aside button').length > 0,
    strayViews: main.children.length, // 应为 1(仅当前视图)
  })
})()
```

Expected: `url=/chat`, `mainLen>1000`, `strayViews===1`

若 `mainLen===7`(空白)或 `strayViews>1`(旧视图残留)→ **版本 B**(Step 3);否则跳 Step 5

- [ ] **Step 3: 退化方案(版本 B:key + 无 out-in)**

`App.vue` 改为:

```html
        <router-view v-slot="{ Component }">
          <transition name="page">
            <component :is="Component" :key="route.fullPath" />
          </transition>
        </router-view>
```

- [ ] **Step 4: CDP 验证版本 B**

重跑 Step 2 的验证序列(先整页刷新 `/kb` 再点击导航)。
Expected: `mainLen>1000`, `strayViews===1`(旧视图在过渡结束后卸载)
若仍失败 → 去掉 transition 保留 `:key`(版本 C),并记录实验结论到 commit 信息

- [ ] **Step 5: 回归与提交**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build`
Expected: 58 测试 PASS,typecheck 无输出,build 成功

```bash
git add frontend/src/App.vue
git commit -m "feat: page transition with keyed router-view (mode verified via cdp)"
```

---

### Task 2: 微交互与渐显全局类

**Files:**
- Modify: `frontend/src/styles/global.scss`(追加类与 reduced-motion 扩展)

**Interfaces:**
- Produces: `.btn-press`(按压 scale 0.97)、`.upload-ripple`(悬停放大+边框脉冲)、`.fade-in`(200ms 渐显)— Task 3/4/5 消费

- [ ] **Step 1: 追加 CSS**

`global.scss` 文件末尾(reduced-motion 块之前)插入:

```scss
/* 按钮按压反馈(自绘元素专用,naive-ui 按钮自带动效不叠加) */
.btn-press {
  transition: transform 0.1s ease;
}
.btn-press:active {
  transform: scale(0.97);
}

/* 上传拖拽区涟漪:悬停轻微放大 + 边框呼吸 */
.upload-ripple {
  transition:
    transform 0.2s ease,
    border-color 0.2s ease;
}
.upload-ripple:hover {
  transform: scale(1.01);
  animation: nb-border-pulse 1.2s ease-in-out infinite;
}
@keyframes nb-border-pulse {
  0%,
  100% {
    border-color: var(--nb-accent-cyan);
  }
  50% {
    border-color: var(--nb-border);
  }
}

/* 内容渐显(骨架屏替换后) */
.fade-in {
  animation: nb-fade-in 0.2s ease;
}
@keyframes nb-fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
```

reduced-motion 块内追加:

```scss
  .btn-press {
    transition: none;
  }
  .btn-press:active {
    transform: none;
  }
  .upload-ripple:hover {
    transform: none;
    animation: none;
  }
  .fade-in {
    animation: none;
  }
```

- [ ] **Step 2: 验证与提交**

Run: `cd frontend && npx vue-tsc --noEmit && npm run build && grep -l "prefers-reduced-motion" dist/assets/*.css | head -1`
Expected: typecheck 无输出;build 成功;dist CSS 含 reduced-motion 块

```bash
git add frontend/src/styles/global.scss
git commit -m "feat: btn-press, upload-ripple and fade-in motion classes"
```

---

### Task 3: Dashboard 骨架屏

**Files:**
- Modify: `frontend/src/views/DashboardView.vue`

**Interfaces:**
- Consumes: `.fade-in`(Task 2)

- [ ] **Step 1: 加 loading 状态与骨架**

`DashboardView.vue` script 改为:

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NSkeleton } from 'naive-ui'
import * as conversationApi from '@/api/conversation'
import { listDocuments } from '@/api/document'
import ModuleCard from '@/components/ModuleCard.vue'
import type { ConversationView, DocumentItem } from '@/types'

const conversations = ref<ConversationView[]>([])
const documents = ref<DocumentItem[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    conversations.value = (await conversationApi.list()).slice(0, 5)
  } catch {
    conversations.value = []
  }
  try {
    documents.value = (await listDocuments()).slice(0, 5)
  } catch {
    documents.value = []
  }
  loading.value = false
})
</script>
```

两个列表卡片的 template 改为:

```html
      <div class="glass-card">
        <div class="mb-3 flex items-center justify-between">
          <span class="text-sm text-gray-700 dark:text-gray-300">最近会话</span>
          <RouterLink to="/chat" class="text-xs text-cyan-600 hover:underline dark:text-cyan-400">全部 →</RouterLink>
        </div>
        <div v-if="loading" class="space-y-3">
          <n-skeleton text :repeat="3" />
        </div>
        <div v-else class="fade-in space-y-1.5">
          <!-- 原会话列表 v-for 不变 -->
        </div>
      </div>
```

(「最近文档」卡片同构:loading 分支 `n-skeleton` + else `fade-in` 列表)

- [ ] **Step 2: 验证与提交**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build`
Expected: 58 测试 PASS,typecheck 无输出,build 成功

```bash
git add frontend/src/views/DashboardView.vue
git commit -m "feat: skeleton loading for dashboard recent lists"
```

---

### Task 4: KB + 八股文骨架屏

**Files:**
- Modify: `frontend/src/views/KnowledgeBaseView.vue`
- Modify: `frontend/src/views/BaguwenView.vue`

**Interfaces:**
- Consumes: `.fade-in`(Task 2)

- [ ] **Step 1: KB 页**

`KnowledgeBaseView.vue`:import 加 `NSkeleton`;script 加 `const listLoading = ref(true)`;onMounted 改为:

```ts
onMounted(async () => {
  await docStore.loadKbs()
  await docStore.loadDocuments()
  listLoading.value = false
  if (hasProcessing.value) docStore.startPolling()
})
```

文档列表区(原 `<div class="space-y-2">` 内 v-for 前)加分支:

```html
        <div v-if="listLoading" class="space-y-2">
          <div v-for="i in 3" :key="i" class="glass-card flex items-center gap-3 !p-3">
            <n-skeleton text style="width: 60%" />
            <n-skeleton text style="width: 20%" />
          </div>
        </div>
        <div v-else class="fade-in space-y-2">
          <!-- 原 v-for="d in docStore.documents" 列表不变 -->
        </div>
```

(注意:原 `v-for` 外层 div 的 `space-y-2` 类并入新 else 分支)

- [ ] **Step 2: 八股文页**

`BaguwenView.vue`:import 加 `NSkeleton`;script 加 `const listLoading = ref(true)`;onMounted 改为:

```ts
onMounted(async () => {
  await baguwenStore.loadArticles()
  await baguwenStore.loadTags()
  await baguwenStore.refreshTasks()
  listLoading.value = false
  if (pendingTaskCount.value) baguwenStore.startPolling()
})
```

文章网格区加分支:

```html
    <div v-if="listLoading" class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
      <div v-for="i in 3" :key="i" class="glass-card space-y-3 !p-4">
        <n-skeleton text :repeat="2" />
        <n-skeleton text style="width: 40%" />
      </div>
    </div>
    <div v-else class="fade-in grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
      <!-- 原 v-for="a in baguwenStore.articles" 卡片不变 -->
    </div>
```

(原网格 div 的 class 并入 else 分支;空态与分页在网格之后保持不变)

- [ ] **Step 3: 验证与提交**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build`
Expected: 58 测试 PASS(reader 测试含 KB/八股页面不受影响),typecheck 无输出,build 成功

```bash
git add frontend/src/views/KnowledgeBaseView.vue frontend/src/views/BaguwenView.vue
git commit -m "feat: skeleton loading for kb document list and baguwen cards"
```

---

### Task 5: .btn-press / .upload-ripple 应用

**Files:**
- Modify: `frontend/src/components/FileUploader.vue`(涟漪)
- Modify: `frontend/src/components/AppHeader.vue`、`frontend/src/components/ModuleCard.vue`、`frontend/src/views/DocumentReaderView.vue`、`frontend/src/views/KnowledgeBaseView.vue`、`frontend/src/views/ChatView.vue`、`frontend/src/views/BaguwenView.vue`(btn-press)

**Interfaces:**
- Consumes: `.btn-press`/`.upload-ripple`(Task 2)

- [ ] **Step 1: 应用类(逐文件精确替换)**

`FileUploader.vue`:

```html
    <n-upload-dragger class="glass-card upload-ripple !border-dashed">
```

`AppHeader.vue` 主题按钮 class 追加 `btn-press`:

```html
          class="theme-toggle btn-press mr-2 flex h-9 w-9 items-center justify-center rounded-lg text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-white/5 dark:hover:text-gray-200"
```

`ModuleCard.vue`:

```html
    <GlassCard :accent="accent" hoverable class="btn-press h-full">
```

`DocumentReaderView.vue`:

```html
          class="doc-item btn-press glass-card w-full !p-3 text-left"
```

与目录按钮:

```html
          class="outline-trigger btn-press absolute right-4 top-4 z-10 flex h-9 items-center gap-1.5 rounded-full border border-gray-200 bg-white/90 px-3 text-xs text-gray-600 backdrop-blur hover:text-cyan-600 dark:border-white/10 dark:bg-[#15151f]/90 dark:text-gray-300 dark:hover:text-cyan-300"
```

抽屉大纲项:

```html
            class="outline-item btn-press block w-full rounded px-2 py-1.5 text-left text-sm transition-colors"
```

`KnowledgeBaseView.vue`(2 处 KB 按钮):

```html
          class="glass-card btn-press w-full !p-3 text-left"
```

`ChatView.vue`(2 处会话按钮):

```html
          class="glass-card btn-press w-full !p-3 text-left transition-colors"
```

`BaguwenView.vue` 文章卡片:

```html
        class="glass-card glass-card--hover btn-press flex cursor-pointer flex-col gap-2"
```

- [ ] **Step 2: 验证与提交**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build`
Expected: 58 测试 PASS,typecheck 无输出,build 成功

```bash
git add frontend/src/components/FileUploader.vue frontend/src/components/AppHeader.vue frontend/src/components/ModuleCard.vue frontend/src/views/DocumentReaderView.vue frontend/src/views/KnowledgeBaseView.vue frontend/src/views/ChatView.vue frontend/src/views/BaguwenView.vue
git commit -m "feat: apply btn-press and upload-ripple motion classes"
```

---

### Task 6: 全量回归 + CDP 验收

**Files:** 无(验收失败才改)

- [ ] **Step 1: 前端全量**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build`
Expected: 58/58 + typecheck 无输出 + build 成功

- [ ] **Step 2: CDP 验收**(规格五.2)

1. **SPA 切换回归**(核心):新 tab 打开 `/kb` → 点击 `a[href="/chat"]` → 2s 后断言 `document.querySelector('main').children.length === 1` 且内容为 Chat(无空白/无旧页残留);再导航回 `/kb` 断言同样成立;连切 3 页循环断言
2. **骨架屏**:刷新 `/kb` 后立即(≤300ms)eval 断言 `.n-skeleton` 存在,2s 后断言消失且文档列表渲染
3. **动画类存在**:eval 断言 `.btn-press`、`.upload-ripple`、`.fade-in` 样式规则已在页面(检查 document.styleSheets 或直接读 build 产物 grep)
4. 亮暗两态各跑一遍步骤 1(主题切换按钮或 localStorage 预置)

- [ ] **Step 3: 验收问题修复(如有)后提交**

```bash
git add -A
git commit -m "fix: animation polish acceptance findings"
```

---

## Self-Review 记录

- **规格覆盖**:§3.1 → Task 1;§3.2 → Task 3/4;§3.3 → Task 2/5;§五.1/五.2 → Task 6;reduced-motion → Task 2
- **占位符**:无 TBD;Task 1 含实验分支(版本 A→B→C)与每步判定标准
- **类型一致**:`loading`/`listLoading` 命名各页一致;`.btn-press`/`.upload-ripple`/`.fade-in` 类名全局一致
