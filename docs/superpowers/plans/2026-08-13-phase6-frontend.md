# NeuralBase Phase 6 前端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 前端全模块落地——登录（cookie 认证）+ Dashboard + 知识库 + AI 对话（SSE 流式）+ 八股文库 + 日报占位，深色玻璃拟态设计系统，移动端底部 Tab；配套后端改动：cookie 认证改造与 `DELETE /api/document`。

**Architecture:** 新建 `frontend/`（Vue 3.5 + Vite 7 + TS + Pinia + Naive UI + Tailwind 3.4）。设计系统先行（SCSS token + GlassCard + Naive UI themeOverrides），API 层统一解 `Result` 壳（axios 拦截器），SSE 走 fetch + 手写解析器（`utils/sse.ts`，纯函数可测）。认证用 HttpOnly cookie（后端 login Set-Cookie + 新增 logout 端点 + filter cookie 兜底），前端不存 token。两个迭代：① cookie 认证 → 脚手架 → 设计系统 → 登录 → Chat；② DELETE document → 知识库 → 八股文 → Dashboard → 日报占位。

**Tech Stack:** Vue 3.5 + Vite 7 + TypeScript 5.9 + Pinia 3 + Vue Router 4 + Naive UI 2 + Tailwind 3.4 + markdown-it 14 + highlight.js 11 + DOMPurify 3 + @iconify/vue 5 + axios 1.13 + Vitest 3 + @vue/test-utils 2.4 + happy-dom。

## Global Constraints

- **基准 spec**：`docs/superpowers/specs/2026-08-13-frontend-design.md`（含全部 12 项已裁决决策与 API 契约，第六节为准）
- **前端路径**：`D:\neural-base\frontend`；**后端路径**：`D:\neural-base\backend`。命令在 Git Bash 下用正斜杠绝对路径
- **后端分层模式**：entity → `BaseMapper`（接口）→ `@Service` 构造注入 → `@RestController`，统一返回 `Result<T>(code, message, data)`；异常 `IllegalArgumentException` → `GlobalExceptionHandler` → 400；**不改动既有行为**（`ChatService`/`DocumentService` 现有逻辑除新增 `delete()` 外不动）
- **后端测试**：`@SpringBootTest` + `@ActiveProfiles("test")`，mock 用测试源码 `com.neuralbase.config` 下的普通 `@Configuration` + `@Primary`（自动扫描纳入），`@BeforeEach` 清表；集成测试依赖共享 test 库（`neuralbase_test`）已有 kb id=1 种子（`TestKbFixture`）
- **前端代码风格**：Composition API `<script setup lang="ts">`；Pinia setup store；路径别名 `@/` → `src/`；中文 UI 文案与注释
- **前端响应契约**：所有接口返回 `Result<T>` 壳，`code===0` 成功；`request.ts` 拦截器负责解壳（成功返回 `data`，失败抛 `Error(message)`）与 401 跳登录；**各 api 模块与页面拿到的已是解壳后的 data，不要再判断 code**
- **认证**：HttpOnly cookie 名 `nb_token`（`HttpOnly; SameSite=Lax; Path=/`），前端不存 token；`POST /api/auth/logout` 清 cookie；filter 解析顺序 header 优先、cookie 兜底；login 响应 body 仍含 token（冒烟 curl 兼容，前端忽略）
- **SSE**：`POST /api/chat/stream`（fetch + ReadableStream 解析，**非 EventSource**），事件序 `meta→sources?→delta*→done`，出错 `error`；中止用 AbortController
- **Vite dev proxy**：`/api` → `http://localhost:8081`（本机 8080 被占用，后端按惯例 `--server.port=8081`）
- **后端本地运行**：`export DB_PASSWORD='Huangwei990758'`（PROGRESS.md 2026-08-12 实测值），测试库密码在 `application-test.yml`
- **提交粒度**：每个任务结束一个 commit，`git add` 显式文件（勿 `-A`），中文 commit message
- **验证标准**：前端 `npm run typecheck` + `npm run build` + `npx vitest run` 全绿；后端 `mvn test` 全绿；冒烟按任务内清单
- **依赖锁定**：`package.json` 按 Task 2 给出的版本号；不新增计划外依赖
- **frontend-design skill**（CLAUDE.md 5.5 要求）：Task 3 动笔前，先调用 `frontend-design` skill 获取设计指导，按其建议落地玻璃拟态设计系统；后续页面视觉细节同样受其指导
- **迭代边界**：迭代 1（Task 1–9）完成时「登录 + 双模式对话」真实冒烟通过；迭代 2（Task 10–14）完成时全量冒烟通过

---

# 迭代一

### Task 1: 后端 cookie 认证改造（login Set-Cookie + logout + filter cookie 兜底）

**Files:**
- Modify: `backend/src/main/java/com/neuralbase/controller/AuthController.java`
- Modify: `backend/src/main/java/com/neuralbase/security/JwtAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/neuralbase/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/neuralbase/auth/AuthControllerTest.java`（新增 3 个测试）
- Create: `backend/src/test/java/com/neuralbase/auth/CookieAuthTest.java`

**Interfaces:**
- Produces: `POST /api/auth/login` → 响应头新增 `Set-Cookie: nb_token=<jwt>; Path=/; HttpOnly; SameSite=Lax; Max-Age=86400`，body 不变（`Result<String>` 含 token）
- Produces: `POST /api/auth/logout` → `Result.ok(null)`，`Set-Cookie: nb_token=; Max-Age=0`（清 cookie），permitAll
- Produces: filter 认证顺序：`Authorization: Bearer` 优先 → 无 header 时读 `nb_token` cookie

- [ ] **Step 1: 写失败测试（AuthControllerTest 追加 3 个测试方法）**

`AuthControllerTest` 末尾追加（imports 补 `jakarta.servlet.http.Cookie`、`org.springframework.http.HttpHeaders`、`static ...requestBuilders.*` 已有 post/get）：

```java
@Test
void loginSetsHttpOnlyCookie() throws Exception {
    MvcResult r = mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
        .andExpect(status().isOk())
        .andReturn();
    String setCookie = r.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie)
        .contains("nb_token=")
        .contains("HttpOnly")
        .contains("SameSite=Lax")
        .contains("Path=/");
}

@Test
void logoutClearsCookie() throws Exception {
    mvc.perform(post("/api/auth/logout"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(result -> assertThat(
            result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0"));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /d/neural-base/backend && mvn test -Dtest=AuthControllerTest -q`
Expected: FAIL——login 无 Set-Cookie 头;logout 404(端点不存在)

- [ ] **Step 3: 实现 AuthController 改动**

`AuthController.java` 全文替换为:

```java
package com.neuralbase.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neuralbase.auth.LoginRequest;
import com.neuralbase.common.Result;
import com.neuralbase.entity.User;
import com.neuralbase.mapper.UserMapper;
import com.neuralbase.security.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String COOKIE_NAME = "nb_token";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final long tokenExpireHours;

    public AuthController(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                          @Value("${neuralbase.auth.token-expire-hours}") long tokenExpireHours) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.tokenExpireHours = tokenExpireHours;
    }

    @PostMapping("/login")
    public ResponseEntity<Result<String>> login(@Valid @RequestBody LoginRequest req,
                                                HttpServletResponse response) {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, req.username()));
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(401, "用户名或密码错误"));
        }
        String token = jwtUtil.generateToken(user.getUsername());
        response.addHeader(HttpHeaders.SET_COOKIE, authCookie(token).toString());
        return ResponseEntity.ok(Result.ok(token));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, authCookie("").maxAge(Duration.ZERO).toString());
        return Result.ok(null);
    }

    @GetMapping("/me")
    public Result<User> me(Authentication authentication) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, authentication.getName()));
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        user.setPasswordHash(null);
        return Result.ok(user);
    }

    private ResponseCookie authCookie(String token) {
        return ResponseCookie.from(COOKIE_NAME, token)
            .httpOnly(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofHours(tokenExpireHours))
            .build();
    }
}
```

- [ ] **Step 4: 写失败测试（新建 CookieAuthTest.java）**

```java
package com.neuralbase.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CookieAuthTest {

    @Autowired
    private MockMvc mvc;

    private String loginAndExtractCookie() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk())
            .andReturn();
        String setCookie = r.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("nb_token=");
        return setCookie.substring(setCookie.indexOf("nb_token=") + "nb_token=".length(),
            setCookie.indexOf(";"));
    }

    @Test
    void meWithCookieOnlyReturnsUser() throws Exception {
        String token = loginAndExtractCookie();
        mvc.perform(get("/api/auth/me").cookie(new Cookie("nb_token", token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    void protectedEndpointWithInvalidCookieReturns401() throws Exception {
        mvc.perform(get("/api/kb").cookie(new Cookie("nb_token", "invalid-token")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void headerStillTakesPrecedence() throws Exception {
        String token = loginAndExtractCookie();
        mvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .cookie(new Cookie("nb_token", "stale-but-ignored")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("admin"));
    }
}
```

- [ ] **Step 5: 运行测试确认失败**

Run: `cd /d/neural-base/backend && mvn test -Dtest=CookieAuthTest -q`
Expected: FAIL——cookie 不被 filter 识别(401)

- [ ] **Step 6: 实现 filter cookie 兜底 + SecurityConfig permitAll**

`JwtAuthenticationFilter.java` 的 `doFilterInternal` 改为(其余不动,imports 补 `jakarta.servlet.http.Cookie`):

```java
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                String username = jwtUtil.parseUsername(token);
                User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getUsername, username));
                if (user != null) {
                    var auth = new UsernamePasswordAuthenticationToken(
                        username, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("无效 JWT: {}", ex.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    /** header 优先,cookie 兜底 */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("nb_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
```

`SecurityConfig.java` 的 `requestMatchers` 改为:

```java
                .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/health").permitAll()
```

- [ ] **Step 7: 运行全部相关测试确认通过**

Run: `cd /d/neural-base/backend && mvn test -Dtest=AuthControllerTest,CookieAuthTest -q`
Expected: PASS(两个类全部测试绿)

- [ ] **Step 8: 回归全量测试**

Run: `cd /d/neural-base/backend && mvn test -q`
Expected: 66+ 全绿(既有 66 个 + 新增 6 个)

- [ ] **Step 9: 提交**

```bash
cd /d/neural-base && git add backend/src/main/java/com/neuralbase/controller/AuthController.java backend/src/main/java/com/neuralbase/security/JwtAuthenticationFilter.java backend/src/main/java/com/neuralbase/config/SecurityConfig.java backend/src/test/java/com/neuralbase/auth/AuthControllerTest.java backend/src/test/java/com/neuralbase/auth/CookieAuthTest.java
git commit -m "feat: cookie auth (login set-cookie, logout endpoint, filter fallback)"
```

### Task 2: 前端脚手架（Vite + Vue + TS + Tailwind + Pinia + Router + Vitest）

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tailwind.config.js`
- Create: `frontend/postcss.config.js`
- Create: `frontend/index.html`
- Create: `frontend/src/env.d.ts`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`（Task 5 会替换为完整布局，此处为最小壳）
- Create: `frontend/src/router/index.ts`（Task 5 补守卫，此处仅路由表 + 一个占位视图）
- Create: `frontend/src/views/PlaceholderView.vue`（迭代 2 前其他页面的占位）

**Interfaces:**
- Produces: 构建链路（`npm run dev/build/typecheck/test`）、路径别名 `@/`、Vite proxy `/api → localhost:8081`、Vitest（happy-dom）
- Produces: 占位视图组件 `PlaceholderView.vue`（接收 prop `title`，被 Task 5 的存根路由复用，迭代 2 逐个替换为真实页面）

- [ ] **Step 1: 创建 package.json**

```json
{
  "name": "neuralbase-frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc --noEmit && vite build",
    "preview": "vite preview",
    "typecheck": "vue-tsc --noEmit",
    "test": "vitest run"
  },
  "dependencies": {
    "@iconify/vue": "^5.0.0",
    "axios": "^1.13.2",
    "dompurify": "^3.2.6",
    "highlight.js": "^11.11.1",
    "markdown-it": "^14.1.0",
    "naive-ui": "^2.43.1",
    "pinia": "^3.0.3",
    "vue": "^3.5.13",
    "vue-router": "^4.5.0"
  },
  "devDependencies": {
    "@types/markdown-it": "^14.1.2",
    "@types/node": "^24.0.0",
    "@vitejs/plugin-vue": "^6.0.1",
    "@vue/test-utils": "^2.4.6",
    "autoprefixer": "^10.4.21",
    "happy-dom": "^18.0.1",
    "postcss": "^8.5.6",
    "sass": "^1.93.2",
    "tailwindcss": "^3.4.17",
    "typescript": "~5.9.2",
    "vite": "^7.1.0",
    "vitest": "^3.2.4",
    "vue-tsc": "^3.0.1"
  }
}
```

- [ ] **Step 2: 创建 vite.config.ts**

```ts
/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// dev proxy: /api → 本机后端 8081(PROGRESS.md:8080 被占用)
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8081', changeOrigin: true },
    },
  },
  test: {
    environment: 'happy-dom',
    globals: true,
  },
})
```

- [ ] **Step 3: 创建 tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "strict": true,
    "noEmit": true,
    "jsx": "preserve",
    "isolatedModules": true,
    "esModuleInterop": true,
    "resolveJsonModule": true,
    "useDefineForClassFields": true,
    "skipLibCheck": true,
    "baseUrl": ".",
    "paths": { "@/*": ["src/*"] },
    "types": ["vite/client", "vitest/globals"]
  },
  "include": ["src/**/*.ts", "src/**/*.d.ts", "src/**/*.vue", "vite.config.ts"],
  "exclude": ["node_modules", "dist"]
}
```

- [ ] **Step 4: 创建 tailwind.config.js + postcss.config.js + index.html + env.d.ts**

`frontend/tailwind.config.js`:

```js
/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  theme: { extend: {} },
  plugins: [],
}
```

`frontend/postcss.config.js`:

```js
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
}
```

`frontend/index.html`:

```html
<!doctype html>
<html lang="zh-CN" class="dark">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>NeuralBase</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

`frontend/src/env.d.ts`:

```ts
/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}
```

- [ ] **Step 5: 创建 main.ts + 最小 App.vue + 占位路由与视图**

`frontend/src/main.ts`:

```ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
```

`frontend/src/App.vue`(最小壳,Task 5 替换):

```vue
<template>
  <router-view />
</template>
```

`frontend/src/views/PlaceholderView.vue`:

```vue
<script setup lang="ts">
defineProps<{ title: string }>()
</script>

<template>
  <div class="glass-card">
    <p class="text-sm text-gray-400">{{ title }}——开发中,迭代 2 落地。</p>
  </div>
</template>
```

`frontend/src/router/index.ts`(Task 5 补守卫):

```ts
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
    { path: '/kb', name: 'kb', component: () => import('@/views/KnowledgeBaseView.vue') },
    { path: '/chat', name: 'chat', component: () => import('@/views/ChatView.vue') },
    { path: '/baguwen', name: 'baguwen', component: () => import('@/views/BaguwenView.vue') },
    { path: '/baguwen/:id', name: 'baguwen-detail', component: () => import('@/views/BaguwenDetailView.vue') },
    { path: '/news', name: 'news', component: () => import('@/views/DailyNewsView.vue') },
  ],
})

export default router
```

> 注意:路由引用的 `LoginView` 等视图 Task 5 才创建。为保证本任务 typecheck/build 通过,先创建 6 个最小占位文件(与路由名一一对应),Task 5 起逐个替换为真实实现:

`frontend/src/views/LoginView.vue`、`DashboardView.vue`、`KnowledgeBaseView.vue`、`ChatView.vue`、`BaguwenView.vue`、`BaguwenDetailView.vue`、`DailyNewsView.vue` 各创建为:

```vue
<script setup lang="ts">
import PlaceholderView from './PlaceholderView.vue'
</script>

<template>
  <PlaceholderView title="登录" />
</template>
```

（title 分别为:登录 / 首页 Dashboard / 知识库 / AI 对话 / 八股文库 / 八股文详情 / AI 日报）

- [ ] **Step 6: 安装依赖并验证构建**

Run:
```bash
cd /d/neural-base/frontend && npm install
npm run typecheck && npm run build
```
Expected: install 成功;typecheck 无输出;build 产物在 `dist/`(占位页面可编译)

- [ ] **Step 7: 启动 dev server 确认占位页可访问**

Run: `cd /d/neural-base/frontend && npm run dev`
Expected: 控制台输出 `Local: http://localhost:5173/`,浏览器访问显示「登录——开发中」占位。确认后 Ctrl+C 停止。

- [ ] **Step 8: 提交**

```bash
cd /d/neural-base && git add frontend/package.json frontend/package-lock.json frontend/vite.config.ts frontend/tsconfig.json frontend/tailwind.config.js frontend/postcss.config.js frontend/index.html frontend/src/env.d.ts frontend/src/main.ts frontend/src/App.vue frontend/src/router/index.ts frontend/src/views/
git commit -m "feat: frontend scaffold (vite+vue+ts+tailwind+pinia+router)"
```

---

### Task 3: 设计系统（SCSS token + Naive UI 主题 + GlassCard + 全局样式）

**Files:**
- Create: `frontend/src/styles/variables.scss`
- Create: `frontend/src/styles/global.scss`
- Create: `frontend/src/styles/theme.ts`
- Create: `frontend/src/utils/feedback.ts`
- Create: `frontend/src/components/GlassCard.vue`
- Test: `frontend/src/components/__tests__/GlassCard.test.ts`

**Interfaces:**
- Produces: SCSS token(`$bg-base`、`$card-bg`、`$border-subtle`、`$text-primary/secondary/faint`、`$accent-kb/chat/baguwen/news`)
- Produces: CSS 变量 `--nb-card-bg`、`--nb-border`(global.scss `:root`)、打字光标 `.nb-cursor`、页面切换过渡 `.page-*`
- Produces: `themeOverrides`(Naive UI 深色玻璃主题,`styles/theme.ts` 默认导出)
- Produces: `feedback.ts` 导出 `message`/`dialog`(createDiscreteApi,全局可用,无需 Provider 包裹)
- Produces: `GlassCard.vue` props:`accent?: 'cyan' | 'violet' | 'amber' | 'emerald'`、`hoverable?: boolean`、`padded?: boolean`(默认 true),默认插槽

- [ ] **Step 1: 写失败测试(GlassCard.test.ts)**

```ts
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
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /d/neural-base/frontend && npx vitest run src/components/__tests__/GlassCard.test.ts`
Expected: FAIL——找不到 `@/components/GlassCard.vue`

- [ ] **Step 3: 创建 variables.scss + global.scss**

`frontend/src/styles/variables.scss`:

```scss
// 深色玻璃拟态设计 token(设计文档 5.1)
$bg-base: #0a0a0f;
$card-bg: rgba(255, 255, 255, 0.02);
$card-hover-bg: rgba(255, 255, 255, 0.05);
$border-subtle: rgba(255, 255, 255, 0.05);
$text-primary: #f3f4f6;
$text-secondary: #9ca3af;
$text-faint: #4b5563;
$accent-kb: #22d3ee;
$accent-chat: #a78bfa;
$accent-baguwen: #fbbf24;
$accent-news: #34d399;
```

`frontend/src/styles/global.scss`:

```scss
@use './variables' as *;

@tailwind base;
@tailwind components;
@tailwind utilities;

:root {
  --nb-card-bg: #{$card-bg};
  --nb-border: #{$border-subtle};
}

html,
body,
#app {
  height: 100%;
}

body {
  background: $bg-base;
  color: $text-primary;
  font-family: 'Inter', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

/* 滚动条 */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}
::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.12);
  border-radius: 4px;
}
::-webkit-scrollbar-track {
  background: transparent;
}

::selection {
  background: rgba(34, 211, 238, 0.3);
}

/* AI 打字光标(设计文档: cursor blink 1s) */
@keyframes nb-blink {
  0%,
  49% {
    opacity: 1;
  }
  50%,
  100% {
    opacity: 0;
  }
}
.nb-cursor {
  display: inline-block;
  width: 8px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: $accent-chat;
  animation: nb-blink 1s infinite;
}

/* 页面切换(设计文档: 淡入 + 上滑 300ms) */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(10px);
}
.page-leave-to {
  opacity: 0;
}
```

- [ ] **Step 4: 创建 theme.ts + feedback.ts**

`frontend/src/styles/theme.ts`:

```ts
import type { GlobalThemeOverrides } from 'naive-ui'

// 深色玻璃拟态主题覆盖(设计文档 5.3)
export const themeOverrides: GlobalThemeOverrides = {
  common: {
    bodyColor: '#0a0a0f',
    cardColor: 'rgba(255, 255, 255, 0.02)',
    modalColor: '#15151f',
    popoverColor: '#15151f',
    borderColor: 'rgba(255, 255, 255, 0.08)',
    dividerColor: 'rgba(255, 255, 255, 0.06)',
    borderRadius: '8px',
    textColorBase: '#f3f4f6',
    textColor1: '#f3f4f6',
    textColor2: '#9ca3af',
    textColor3: '#4b5563',
    primaryColor: '#22d3ee',
    primaryColorHover: '#67e8f9',
    primaryColorPressed: '#0e7490',
    primaryColorSuppl: '#67e8f9',
    infoColor: '#a78bfa',
    infoColorHover: '#c4b5fd',
    infoColorPressed: '#7c3aed',
    infoColorSuppl: '#c4b5fd',
    successColor: '#34d399',
    successColorHover: '#6ee7b7',
    successColorPressed: '#059669',
    successColorSuppl: '#6ee7b7',
    warningColor: '#fbbf24',
    warningColorHover: '#fcd34d',
    warningColorPressed: '#d97706',
    warningColorSuppl: '#fcd34d',
    errorColor: '#f87171',
    errorColorHover: '#fca5a5',
    errorColorPressed: '#dc2626',
    errorColorSuppl: '#fca5a5',
    inputColor: 'rgba(255, 255, 255, 0.03)',
  },
}
```

`frontend/src/utils/feedback.ts`:

```ts
import { createDiscreteApi } from 'naive-ui'

// 全局 message/dialog(spec 5.3「全局化提示」的落地:discrete api 不依赖组件上下文)
export const { message, dialog } = createDiscreteApi(['message', 'dialog'])
```

- [ ] **Step 5: 创建 GlassCard.vue**

```vue
<script setup lang="ts">
withDefaults(
  defineProps<{
    accent?: 'cyan' | 'violet' | 'amber' | 'emerald'
    hoverable?: boolean
    padded?: boolean
  }>(),
  { accent: undefined, hoverable: false, padded: true },
)
</script>

<template>
  <div
    class="glass-card"
    :class="[
      accent ? `glass-card--${accent}` : '',
      hoverable ? 'glass-card--hover' : '',
      padded ? 'glass-card--padded' : '',
    ]"
  >
    <slot />
  </div>
</template>

<style scoped>
.glass-card {
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
  border-radius: 12px;
  backdrop-filter: blur(12px);
  transition:
    transform 0.3s ease,
    border-color 0.3s ease,
    box-shadow 0.3s ease;
}
.glass-card--padded {
  padding: 16px;
}
.glass-card--hover:hover {
  transform: translateY(-2px);
  border-color: var(--nb-accent);
  box-shadow: 0 8px 32px -8px var(--nb-accent);
}
.glass-card--cyan {
  --nb-accent: #22d3ee;
}
.glass-card--violet {
  --nb-accent: #a78bfa;
}
.glass-card--amber {
  --nb-accent: #fbbf24;
}
.glass-card--emerald {
  --nb-accent: #34d399;
}
</style>
```

- [ ] **Step 6: 把 global.scss 接入 main.ts**

`frontend/src/main.ts` 首行 import 区追加:

```ts
import '@/styles/global.scss'
```

- [ ] **Step 7: 运行测试与构建确认通过**

Run: `cd /d/neural-base/frontend && npx vitest run && npm run typecheck && npm run build`
Expected: 测试 PASS;typecheck 无输出;build 成功

- [ ] **Step 8: 提交**

```bash
cd /d/neural-base && git add frontend/src/styles/ frontend/src/utils/feedback.ts frontend/src/components/GlassCard.vue frontend/src/components/__tests__/GlassCard.test.ts frontend/src/main.ts
git commit -m "feat: design system tokens, naive-ui glass theme, glass card"
```

### Task 4: API 层(types 镜像 + request 拦截器 + 六个 api 模块)

**Files:**
- Create: `frontend/src/types/index.ts`
- Create: `frontend/src/api/request.ts`
- Create: `frontend/src/api/auth.ts`
- Create: `frontend/src/api/kb.ts`
- Create: `frontend/src/api/document.ts`
- Create: `frontend/src/api/conversation.ts`
- Create: `frontend/src/api/baguwen.ts`

**Interfaces:**
- Produces: `types/index.ts` 全部 DTO 接口(spec 第六节镜像):`User`、`Kb`、`DocumentItem`、`DocumentStatus`、`SearchResult`、`ChatRequest`、`RagSource`、`ConversationView`、`MessageView`、`BaguwenArticleView`、`Page<T>`、`QaPairView`、`BaguwenDetail`、`ImportTask`、`TagCount`、`FavoriteResult`、`Result<T>`
- Produces: `request.ts` 默认导出 `http`:`http.get<T>(url, params?)`、`http.post<T>(url, data?, config?)`、`http.put<T>(url, data?)`、`http.del<T>(url)`——全部返回解壳后的 `Promise<T>`
- Produces: api 模块函数(见各文件),签名供后续任务消费

- [ ] **Step 1: 创建 types/index.ts**

```ts
// 后端 DTO 的 TS 镜像(spec 第六节 API 契约)
export interface Result<T> {
  code: number
  message: string
  data: T
}

export interface User {
  id: number
  username: string
  role: string
  createdAt: string
}

export interface Kb {
  id: number
  name: string
  description?: string
  color?: string
  createdAt: string
}

export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'

export interface DocumentItem {
  id: number
  kbId?: number
  title: string
  fileType: string
  status: DocumentStatus
  chunkCount?: number
  error?: string
  createdAt: string
}

export interface SearchResult {
  docId: number
  title: string | null
  content: string
  score: number
}

export interface ChatRequest {
  conversationId?: number
  kbId?: number
  question: string
}

export interface RagSource {
  docId: number
  title: string
  content: string
  score: number
}

export interface ConversationView {
  id: number
  title: string
  kbId?: number
  createdAt: string
  updatedAt: string
  lastMessageContent?: string
}

export interface MessageView {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  sources?: RagSource[]
  createdAt: string
}

export interface Page<T> {
  records: T[]
  total: number
  size: number
  current: number
}

export interface BaguwenArticleView {
  id: number
  title: string
  url: string
  tags: string[]
  createdAt: string
}

export interface QaPairView {
  id: number
  question: string
  answer: string
  difficulty: string
}

export interface BaguwenDetail {
  id: number
  title: string
  url: string
  content: string
  tags: string[]
  createdAt: string
  qaPairs: QaPairView[]
}

export type ImportTaskStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED'

export interface ImportTask {
  id: number
  url: string
  status: ImportTaskStatus
  errorMsg?: string
  articleId?: number
  attempt: number
  createdAt: string
  updatedAt: string
}

export interface TagCount {
  tag: string
  count: number
}

export interface FavoriteResult {
  embedded: number
}
```

- [ ] **Step 2: 创建 api/request.ts**

```ts
import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import router from '@/router'
import type { Result } from '@/types'

// 统一拦截器:解 Result 壳 + 401 跳登录(spec 第八节)
const client = axios.create({ baseURL: '/api', timeout: 30_000 })

client.interceptors.response.use(
  (resp) => {
    const body = resp.data as Result<unknown>
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body.data as never
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body as never
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message
    if (status === 401 && router.currentRoute.value.name !== 'login') {
      void router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    }
    return Promise.reject(new Error(message || error.message || '网络错误'))
  },
)

// 各 api 模块统一入口:返回值为解壳后的 data
export const http = {
  get: <T>(url: string, params?: Record<string, unknown>) =>
    client.get(url, { params }) as Promise<T>,
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    client.post(url, data, config) as Promise<T>,
  put: <T>(url: string, data?: unknown) => client.put(url, data) as Promise<T>,
  del: <T>(url: string) => client.delete(url) as Promise<T>,
}

export default http
```

- [ ] **Step 3: 创建六个 api 模块**

`frontend/src/api/auth.ts`:

```ts
import http from './request'
import type { User } from '@/types'

// login 成功后 token 由后端 Set-Cookie(HttpOnly)携带,body token 前端忽略
export const login = (username: string, password: string) =>
  http.post<string>('/auth/login', { username, password })

export const logout = () => http.post<null>('/auth/logout')

export const me = () => http.get<User>('/auth/me')
```

`frontend/src/api/kb.ts`:

```ts
import http from './request'
import type { Kb } from '@/types'

export const listKbs = () => http.get<Kb[]>('/kb')
export const createKb = (payload: { name: string; description?: string; color?: string }) =>
  http.post<Kb>('/kb', payload)
export const updateKb = (id: number, payload: { name: string; description?: string; color?: string }) =>
  http.put<Kb>(`/kb/${id}`, payload)
export const deleteKb = (id: number) => http.del<null>(`/kb/${id}`)
```

`frontend/src/api/document.ts`:

```ts
import http from './request'
import type { DocumentItem, SearchResult } from '@/types'

export const uploadDocument = (
  file: File,
  kbId?: number,
  onProgress?: (pct: number) => void,
) => {
  const form = new FormData()
  form.append('file', file)
  if (kbId != null) form.append('kbId', String(kbId))
  return http.post<DocumentItem>('/document', form, {
    onUploadProgress: (e) => {
      if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100))
    },
  })
}

export const searchDocuments = (query: string, kbId?: number, topK = 5) =>
  http.post<SearchResult[]>('/document/search', { query, kbId, topK })

export const listDocuments = (kbId?: number) =>
  http.get<DocumentItem[]>('/document', kbId != null ? { kbId } : undefined)

export const deleteDocument = (id: number) => http.del<null>(`/document/${id}`)
```

`frontend/src/api/conversation.ts`(导出名与 Task 7 chat store 消费侧一致):

```ts
import http from './request'
import type { ConversationView, MessageView } from '@/types'

export const create = (title?: string, kbId?: number) =>
  http.post<ConversationView>('/conversation', { title, kbId })

export const list = () => http.get<ConversationView[]>('/conversation')

export const messages = (id: number) => http.get<MessageView[]>(`/conversation/${id}/messages`)

export const remove = (id: number) => http.del<null>(`/conversation/${id}`)
```

`frontend/src/api/baguwen.ts`:

```ts
import http from './request'
import type {
  BaguwenArticleView,
  BaguwenDetail,
  FavoriteResult,
  ImportTask,
  ImportTaskStatus,
  Page,
  TagCount,
} from '@/types'

export const importUrls = (urls: string[]) => http.post<ImportTask[]>('/baguwen/import', { urls })

export const listTasks = (status?: ImportTaskStatus) =>
  http.get<ImportTask[]>('/baguwen/tasks', status ? { status } : undefined)

export const retryTask = (id: number) => http.post<ImportTask>(`/baguwen/tasks/${id}/retry`)

export const listArticles = (params: { tag?: string; keyword?: string; page: number; size: number }) =>
  http.get<Page<BaguwenArticleView>>('/baguwen', {
    tag: params.tag,
    keyword: params.keyword,
    page: params.page,
    size: params.size,
  })

export const articleDetail = (id: number) => http.get<BaguwenDetail>(`/baguwen/${id}`)

export const listTags = () => http.get<TagCount[]>('/baguwen/tags')

export const deleteArticle = (id: number) => http.del<null>(`/baguwen/${id}`)

export const favoriteArticle = (id: number, kbId: number) =>
  http.post<FavoriteResult>(`/baguwen/${id}/favorite`, { kbId })
```

- [ ] **Step 4: 验证类型与构建**

Run: `cd /d/neural-base/frontend && npm run typecheck && npm run build`
Expected: typecheck 无输出;build 成功(types 与 api 模块尚无消费者,编译通过即验证)

- [ ] **Step 5: 提交**

```bash
cd /d/neural-base && git add frontend/src/types/ frontend/src/api/
git commit -m "feat: api layer (types, result-unwrap interceptor, six api modules)"
```

---

### Task 5: 布局 + 用户 store + 路由守卫 + 登录页

**Files:**
- Create: `frontend/src/stores/user.ts`
- Modify: `frontend/src/router/index.ts`(加守卫)
- Modify: `frontend/src/App.vue`(完整布局:n-config-provider + header/tab bar + 过渡)
- Create: `frontend/src/components/AppHeader.vue`
- Create: `frontend/src/components/BottomTabBar.vue`
- Create: `frontend/src/views/LoginView.vue`(替换占位)
- Test: `frontend/src/stores/__tests__/user.test.ts`

**Interfaces:**
- Consumes: `authApi.login/logout/me`(Task 4)、`themeOverrides`(Task 3)、`feedback.message`(Task 3)
- Produces: `useUserStore`:`user: Ref<User|null>`、`initialized: Ref<boolean>`、`login(username,password)`、`fetchMe()`、`logout()`
- Produces: 全局布局:非登录页固定顶部 header(h-16 backdrop-blur)+ 移动端底部 Tab;路由守卫:未登录 → `/login?redirect=…`,已登录访问 /login → 首页

- [ ] **Step 1: 写失败测试(stores/__tests__/user.test.ts)**

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores/user'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  me: vi.fn(),
}))

import { login, logout, me } from '@/api/auth'

describe('user store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('登录成功写入 user 且 initialized 为 true', async () => {
    vi.mocked(me).mockResolvedValue({ id: 1, username: 'admin', role: 'ADMIN', createdAt: '' })
    vi.mocked(login).mockResolvedValue('token')
    const store = useUserStore()
    await store.login('admin', 'admin123')
    expect(store.user?.username).toBe('admin')
    expect(store.initialized).toBe(true)
  })

  it('fetchMe 失败时 user 为 null 不抛错', async () => {
    vi.mocked(me).mockRejectedValue(new Error('未认证'))
    const store = useUserStore()
    await store.fetchMe()
    expect(store.user).toBeNull()
    expect(store.initialized).toBe(true)
  })

  it('登出清空 user', async () => {
    vi.mocked(me).mockResolvedValue({ id: 1, username: 'admin', role: 'ADMIN', createdAt: '' })
    vi.mocked(logout).mockResolvedValue(null)
    const store = useUserStore()
    await store.fetchMe()
    expect(store.user).not.toBeNull()
    await store.logout()
    expect(store.user).toBeNull()
    expect(logout).toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /d/neural-base/frontend && npx vitest run src/stores/__tests__/user.test.ts`
Expected: FAIL——找不到 `@/stores/user`

- [ ] **Step 3: 创建 stores/user.ts**

```ts
import { ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import type { User } from '@/types'

// 登录态由 HttpOnly cookie 承载,前端不存 token(spec 二、认证方案)
export const useUserStore = defineStore('user', () => {
  const user = ref<User | null>(null)
  const initialized = ref(false)

  async function login(username: string, password: string) {
    await authApi.login(username, password) // 后端 Set-Cookie
    await fetchMe()
  }

  async function fetchMe() {
    try {
      user.value = await authApi.me()
    } catch {
      user.value = null
    } finally {
      initialized.value = true
    }
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // 即使后端失败也清空本地登录态
    }
    user.value = null
  }

  return { user, initialized, login, fetchMe, logout }
})
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /d/neural-base/frontend && npx vitest run src/stores/__tests__/user.test.ts`
Expected: PASS(3 个用例)

- [ ] **Step 5: 路由加守卫**

`frontend/src/router/index.ts` 全文替换为:

```ts
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
    { path: '/kb', name: 'kb', component: () => import('@/views/KnowledgeBaseView.vue') },
    { path: '/chat', name: 'chat', component: () => import('@/views/ChatView.vue') },
    { path: '/baguwen', name: 'baguwen', component: () => import('@/views/BaguwenView.vue') },
    { path: '/baguwen/:id', name: 'baguwen-detail', component: () => import('@/views/BaguwenDetailView.vue') },
    { path: '/news', name: 'news', component: () => import('@/views/DailyNewsView.vue') },
  ],
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  if (!userStore.initialized) await userStore.fetchMe()
  if (to.name !== 'login' && !userStore.user) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && userStore.user) return { name: 'dashboard' }
})

export default router
```

- [ ] **Step 6: 替换 App.vue 为完整布局**

`frontend/src/App.vue` 全文替换为:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { darkTheme, NConfigProvider } from 'naive-ui'
import { themeOverrides } from '@/styles/theme'
import AppHeader from '@/components/AppHeader.vue'
import BottomTabBar from '@/components/BottomTabBar.vue'

const route = useRoute()
const isLogin = computed(() => route.name === 'login')
</script>

<template>
  <n-config-provider :theme="darkTheme" :theme-overrides="themeOverrides">
    <template v-if="!isLogin">
      <AppHeader />
      <main class="mx-auto max-w-7xl px-4 pb-20 pt-24 md:pb-10">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
      <BottomTabBar />
    </template>
    <router-view v-else />
  </n-config-provider>
</template>
```

- [ ] **Step 7: 创建 AppHeader.vue**

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { NDropdown } from 'naive-ui'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()

const navs = [
  { name: 'dashboard', label: '首页', path: '/' },
  { name: 'kb', label: '知识库', path: '/kb' },
  { name: 'chat', label: 'AI 对话', path: '/chat' },
  { name: 'baguwen', label: '八股文', path: '/baguwen' },
  { name: 'news', label: 'AI 日报', path: '/news' },
]

const initial = computed(() => (userStore.user?.username ?? '?').charAt(0).toUpperCase())

const userOptions = [{ label: '退出登录', key: 'logout' }]

async function onUserSelect(key: string) {
  if (key === 'logout') {
    await userStore.logout()
    void router.push('/login')
  }
}
</script>

<template>
  <header
    class="fixed inset-x-0 top-0 z-40 h-16 border-b backdrop-blur-xl"
    style="background: rgba(10, 10, 15, 0.72); border-color: rgba(255, 255, 255, 0.05)"
  >
    <div class="mx-auto flex h-full max-w-7xl items-center gap-2 px-4">
      <RouterLink to="/" class="flex shrink-0 items-center gap-2">
        <span class="h-2.5 w-2.5 rounded-full bg-gradient-to-r from-cyan-400 to-purple-400" />
        <span class="text-sm font-semibold tracking-wide text-gray-100">NeuralBase</span>
      </RouterLink>
      <nav class="ml-6 hidden items-center gap-1 md:flex">
        <RouterLink
          v-for="n in navs"
          :key="n.name"
          :to="n.path"
          class="nav-link rounded-lg px-3 py-1.5 text-sm transition-colors"
        >
          {{ n.label }}
        </RouterLink>
      </nav>
      <div class="ml-auto">
        <n-dropdown :options="userOptions" trigger="click" @select="onUserSelect">
          <button
            class="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-gray-300 transition-colors hover:bg-white/5"
          >
            <span
              class="flex h-6 w-6 items-center justify-center rounded-full bg-gradient-to-br from-cyan-400 to-purple-400 text-xs font-semibold text-black"
            >
              {{ initial }}
            </span>
            <span class="hidden sm:inline">{{ userStore.user?.username }}</span>
          </button>
        </n-dropdown>
      </div>
    </div>
  </header>
</template>

<style scoped>
.nav-link {
  color: #9ca3af;
}
.nav-link:hover {
  color: #e5e7eb;
}
.nav-link.router-link-active {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.05);
}
</style>
```

- [ ] **Step 8: 创建 BottomTabBar.vue**

```vue
<script setup lang="ts">
import { Icon } from '@iconify/vue'

const tabs = [
  { name: 'dashboard', label: '首页', icon: 'mdi:view-dashboard-outline', path: '/' },
  { name: 'kb', label: '知识库', icon: 'mdi:bookshelf', path: '/kb' },
  { name: 'chat', label: '对话', icon: 'mdi:message-text-outline', path: '/chat' },
  { name: 'baguwen', label: '八股文', icon: 'mdi:file-document-multiple-outline', path: '/baguwen' },
  { name: 'news', label: '日报', icon: 'mdi:newspaper-variant-outline', path: '/news' },
]
</script>

<template>
  <nav
    class="fixed inset-x-0 bottom-0 z-40 border-t backdrop-blur-xl md:hidden"
    style="background: rgba(10, 10, 15, 0.85); border-color: rgba(255, 255, 255, 0.05)"
  >
    <div class="grid h-16 grid-cols-5">
      <RouterLink
        v-for="t in tabs"
        :key="t.name"
        :to="t.path"
        class="tab-link flex flex-col items-center justify-center gap-0.5 text-[11px]"
      >
        <Icon :icon="t.icon" class="h-5 w-5" />
        {{ t.label }}
      </RouterLink>
    </div>
  </nav>
</template>

<style scoped>
.tab-link {
  color: #6b7280;
}
.tab-link.router-link-active {
  color: #22d3ee;
}
</style>
```

- [ ] **Step 9: 替换 LoginView.vue 占位为真实登录页**

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NForm, NFormItem, NInput } from 'naive-ui'
import { useUserStore } from '@/stores/user'
import { message } from '@/utils/feedback'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

const username = ref('')
const password = ref('')
const loading = ref(false)

async function onSubmit() {
  if (!username.value || !password.value) {
    message.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login(username.value, password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.push(redirect)
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4">
    <div class="glass-card w-full max-w-sm !p-8">
      <div class="mb-8 flex flex-col items-center gap-3">
        <span class="h-3 w-3 rounded-full bg-gradient-to-r from-cyan-400 to-purple-400" />
        <h1 class="text-xl font-semibold tracking-wide text-gray-100">NeuralBase</h1>
        <p class="text-xs text-gray-500">个人 AI 知识中枢</p>
      </div>
      <n-form label-placement="top" @keyup.enter="onSubmit">
        <n-form-item label="用户名">
          <n-input v-model:value="username" placeholder="请输入用户名" />
        </n-form-item>
        <n-form-item label="密码">
          <n-input v-model:value="password" type="password" placeholder="请输入密码" show-password-on="click" />
        </n-form-item>
        <n-button type="primary" block :loading="loading" @click="onSubmit">登 录</n-button>
      </n-form>
    </div>
  </div>
</template>
```

- [ ] **Step 10: 验证测试与构建**

Run: `cd /d/neural-base/frontend && npx vitest run && npm run typecheck && npm run build`
Expected: 全部通过;build 成功

- [ ] **Step 11: 提交**

```bash
cd /d/neural-base && git add frontend/src/stores/ frontend/src/router/index.ts frontend/src/App.vue frontend/src/components/AppHeader.vue frontend/src/components/BottomTabBar.vue frontend/src/views/LoginView.vue
git commit -m "feat: layout, user store, route guard, login page"
```

### Task 6: SSE 解析器 utils/sse.ts

**Files:**
- Create: `frontend/src/utils/sse.ts`
- Test: `frontend/src/utils/__tests__/sse.test.ts`

**Interfaces:**
- Produces: `SseEvent { event: string; data: string }`
- Produces: `class SseParser`:`push(chunk: string): SseEvent[]`、`flush(): SseEvent[]`(增量解析,容错:跨块断行、注释行 `:`、无空行结尾)
- Produces: `streamChat(payload: ChatRequest, handlers: StreamHandlers, signal?: AbortSignal): Promise<void>`;`StreamHandlers { onMeta?(meta), onSources?(sources), onDelta?(text), onDone?(), onError?(message) }`——meta/sources 的 data 已 JSON.parse;非 2xx 抛 `Error(message)`(优先取响应体 Result.message)

- [ ] **Step 1: 写失败测试(utils/__tests__/sse.test.ts)**

```ts
import { describe, expect, it, vi } from 'vitest'
import { SseParser, streamChat } from '@/utils/sse'
import type { RagSource } from '@/types'

describe('SseParser', () => {
  it('解析完整事件序列 meta/sources/delta/done', () => {
    const parser = new SseParser()
    const raw = [
      'event: meta\ndata: {"conversationId":1}\n\n',
      'event: sources\ndata: [{"docId":2,"title":"t","content":"c","score":0.9}]\n\n',
      'event: delta\ndata: 你好\n\n',
      'event: done\ndata: \n\n',
    ].join('')
    const events = parser.push(raw)
    expect(events.map((e) => e.event)).toEqual(['meta', 'sources', 'delta', 'done'])
    expect(events[2].data).toBe('你好')
    expect(JSON.parse(events[0].data)).toEqual({ conversationId: 1 })
  })

  it('跨块断行与一行多事件拼接正确', () => {
    const parser = new SseParser()
    const first = parser.push('event: delta\ndata: hel')
    expect(first).toEqual([])
    const second = parser.push('lo\n\nevent: done\ndata: \n\n')
    expect(second.map((e) => e.event)).toEqual(['delta', 'done'])
    expect(second[0].data).toBe('hello')
  })

  it('忽略注释行,flush 处理无空行结尾', () => {
    const parser = new SseParser()
    expect(parser.push(': keep-alive\n')).toEqual([])
    expect(parser.push('data: tail')).toEqual([])
    expect(parser.flush()).toEqual([{ event: 'message', data: 'tail' }])
  })
})

describe('streamChat', () => {
  function mockFetchWithChunks(chunks: string[]) {
    const encoder = new TextEncoder()
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        for (const c of chunks) controller.enqueue(encoder.encode(c))
        controller.close()
      },
    })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(stream, { status: 200 })))
  }

  it('按事件调用对应 handler,delta 按序累积', async () => {
    mockFetchWithChunks(['event: meta\ndata: {"conversationId":7}\n\n', 'event: delta\ndata: 你', 'event: delta\ndata: 好\n\n'])
    const deltas: string[] = []
    let meta: unknown = null
    await streamChat(
      { question: 'hi' },
      {
        onMeta: (m) => (meta = m),
        onDelta: (t) => deltas.push(t),
      },
    )
    expect(meta).toEqual({ conversationId: 7 })
    expect(deltas.join('')).toBe('你好')
    expect(fetch).toHaveBeenCalledWith(
      '/api/chat/stream',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ question: 'hi' }) }),
    )
  })

  it('error 事件走 onError', async () => {
    mockFetchWithChunks(['event: error\ndata: stream error\n\n'])
    const onError = vi.fn()
    await streamChat({ question: 'hi' }, { onError })
    expect(onError).toHaveBeenCalledWith('生成出错,请重试')
  })

  it('非 2xx 抛出 Result.message', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 401, message: '未认证', data: null }), { status: 401 }),
      ),
    )
    await expect(streamChat({ question: 'hi' }, {})).rejects.toThrow('未认证')
  })

  it('sources 事件透传解析后的数组', async () => {
    mockFetchWithChunks([
      'event: sources\ndata: [{"docId":2,"title":"t","content":"c","score":0.9}]\n\n',
    ])
    const onSources = vi.fn()
    await streamChat({ question: 'hi' }, { onSources })
    expect(onSources).toHaveBeenCalledWith([
      { docId: 2, title: 't', content: 'c', score: 0.9 } as RagSource,
    ])
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /d/neural-base/frontend && npx vitest run src/utils/__tests__/sse.test.ts`
Expected: FAIL——找不到 `@/utils/sse`

- [ ] **Step 3: 实现 utils/sse.ts**

```ts
import type { ChatRequest, RagSource } from '@/types'

export interface SseEvent {
  event: string
  data: string
}

/** SSE 文本流增量解析器(spec 6.1 事件协议;POST + Bearer/cookie 故不用 EventSource) */
export class SseParser {
  private buffer = ''
  private eventName = 'message'
  private dataLines: string[] = []

  push(chunk: string): SseEvent[] {
    const events: SseEvent[] = []
    this.buffer += chunk
    let idx: number
    while ((idx = this.buffer.indexOf('\n')) >= 0) {
      const line = this.buffer.slice(0, idx).replace(/\r$/, '')
      this.buffer = this.buffer.slice(idx + 1)
      if (line === '') {
        if (this.dataLines.length > 0) {
          events.push({ event: this.eventName, data: this.dataLines.join('\n') })
          this.dataLines = []
          this.eventName = 'message'
        }
      } else if (line.startsWith(':')) {
        // keep-alive 注释行,忽略
      } else if (line.startsWith('event:')) {
        this.eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        this.dataLines.push(line.slice(5).trimStart())
      }
    }
    return events
  }

  /** 流结束时把残存 data 行(无空行结尾)作为最后一个事件 */
  flush(): SseEvent[] {
    const tail = this.push('\n\n')
    this.buffer = ''
    this.dataLines = []
    this.eventName = 'message'
    return tail
  }
}

export interface StreamHandlers {
  onMeta?: (meta: { conversationId: number }) => void
  onSources?: (sources: RagSource[]) => void
  onDelta?: (text: string) => void
  onDone?: () => void
  onError?: (message: string) => void
}

function dispatch(ev: SseEvent, h: StreamHandlers) {
  switch (ev.event) {
    case 'meta':
      try {
        h.onMeta?.(JSON.parse(ev.data))
      } catch {
        /* 坏数据忽略 */
      }
      break
    case 'sources':
      try {
        h.onSources?.(JSON.parse(ev.data))
      } catch {
        /* 坏数据忽略 */
      }
      break
    case 'delta':
      h.onDelta?.(ev.data)
      break
    case 'done':
      h.onDone?.()
      break
    case 'error':
      h.onError?.('生成出错,请重试')
      break
  }
}

/** POST /api/chat/stream(fetch + ReadableStream;同源自动携带 cookie) */
export async function streamChat(
  payload: ChatRequest,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const resp = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal,
  })
  if (!resp.ok) {
    let message = `请求失败(${resp.status})`
    try {
      const body = await resp.json()
      message = body?.message || message
    } catch {
      /* 非 JSON 响应体,保留默认 message */
    }
    throw new Error(message)
  }
  if (!resp.body) throw new Error('响应无内容')

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  const parser = new SseParser()
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    for (const ev of parser.push(decoder.decode(value, { stream: true }))) dispatch(ev, handlers)
  }
  for (const ev of parser.flush()) dispatch(ev, handlers)
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /d/neural-base/frontend && npx vitest run src/utils/__tests__/sse.test.ts`
Expected: PASS(7 个用例)

- [ ] **Step 5: 全量验证与提交**

Run: `cd /d/neural-base/frontend && npx vitest run && npm run typecheck`
Expected: 全部通过

```bash
cd /d/neural-base && git add frontend/src/utils/sse.ts frontend/src/utils/__tests__/sse.test.ts
git commit -m "feat: sse stream parser and chat stream client"
```

---

### Task 7: chat store(会话/消息/流式状态机)

**Files:**
- Create: `frontend/src/stores/chat.ts`
- Test: `frontend/src/stores/__tests__/chat.test.ts`

**Interfaces:**
- Consumes: `streamChat`(Task 6)、`conversationApi.list/messages/remove`(Task 4)
- Produces: `useChatStore`:`conversations: Ref<ConversationView[]>`、`currentId: Ref<number|null>`、`messages: Ref<ChatUiMessage[]>`、`streaming: Ref<boolean>`、`streamError: Ref<string|null>`;动作 `loadConversations()`、`openConversation(id)`、`newConversation()`、`send(question, kbId?)`、`stopStream()`、`removeConversation(id)`;`ChatUiMessage { role: 'USER'|'ASSISTANT'; content: string; sources?: RagSource[]; streaming?: boolean }`
- 语义:send 时先本地追加 USER 消息与空 ASSISTANT 消息(streaming=true),`meta` 事件回填 currentId 并刷新会话列表,`delta` 追加到末尾助手消息,结束(成功/中止/出错)统一 streaming=false;**AbortError 不算错误**,已生成内容保留

- [ ] **Step 1: 写失败测试(stores/__tests__/chat.test.ts)**

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useChatStore } from '@/stores/chat'

vi.mock('@/api/conversation', () => ({
  list: vi.fn(),
  messages: vi.fn(),
  remove: vi.fn(),
}))

import { list, messages, remove } from '@/api/conversation'

const streamChatMock = vi.hoisted(() => vi.fn())
vi.mock('@/utils/sse', () => ({ streamChat: streamChatMock }))

describe('chat store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('send 流式追加:meta 回填会话、delta 累积、结束 streaming=false', async () => {
    streamChatMock.mockImplementation(async (_payload, handlers) => {
      handlers.onMeta?.({ conversationId: 9 })
      handlers.onDelta?.('你')
      handlers.onDelta?.('好')
      handlers.onDone?.()
    })
    vi.mocked(list).mockResolvedValue([])
    const store = useChatStore()

    await store.send('问题', undefined)

    expect(store.currentId).toBe(9)
    expect(store.messages.map((m) => m.role)).toEqual(['USER', 'ASSISTANT'])
    expect(store.messages[1].content).toBe('你好')
    expect(store.streaming).toBe(false)
    expect(store.messages[1].streaming).toBe(false)
    expect(store.messages[0].content).toBe('问题')
  })

  it('中止不视为错误且保留已生成内容', async () => {
    streamChatMock.mockImplementation((_payload, _handlers, signal) =>
      new Promise((_resolve, reject) => {
        signal?.addEventListener('abort', () =>
          reject(Object.assign(new Error('aborted'), { name: 'AbortError' })),
        )
      }),
    )
    const store = useChatStore()
    const sendPromise = store.send('hi')
    store.messages[1].content = '已生成的部分'
    store.stopStream()
    await sendPromise

    expect(store.streaming).toBe(false)
    expect(store.streamError).toBeNull()
    expect(store.messages[1].content).toBe('已生成的部分')
  })

  it('流出错写入 streamError,onError 事件同样处理', async () => {
    streamChatMock.mockImplementation(async (_payload, handlers) => {
      handlers.onDelta?.('部分')
      handlers.onError?.('生成出错,请重试')
    })
    const store = useChatStore()
    await store.send('hi')
    expect(store.streamError).toBe('生成出错,请重试')
    expect(store.messages[1].content).toBe('部分')
  })

  it('加载历史消息与删除会话', async () => {
    vi.mocked(list).mockResolvedValue([
      { id: 1, title: '旧会话', createdAt: '', updatedAt: '' },
    ])
    vi.mocked(messages).mockResolvedValue([
      { id: 1, role: 'USER', content: '你好', createdAt: '' },
      { id: 2, role: 'ASSISTANT', content: '你好呀', createdAt: '' },
    ])
    vi.mocked(remove).mockResolvedValue(null)
    const store = useChatStore()

    await store.loadConversations()
    expect(store.conversations).toHaveLength(1)

    await store.openConversation(1)
    expect(store.messages).toHaveLength(2)
    expect(store.currentId).toBe(1)

    await store.removeConversation(1)
    expect(remove).toHaveBeenCalledWith(1)
    expect(store.currentId).toBeNull()
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /d/neural-base/frontend && npx vitest run src/stores/__tests__/chat.test.ts`
Expected: FAIL——找不到 `@/stores/chat`

- [ ] **Step 3: 实现 stores/chat.ts**

```ts
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { streamChat } from '@/utils/sse'
import * as conversationApi from '@/api/conversation'
import type { ConversationView, RagSource } from '@/types'

export interface ChatUiMessage {
  role: 'USER' | 'ASSISTANT'
  content: string
  sources?: RagSource[]
  streaming?: boolean
}

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<ConversationView[]>([])
  const currentId = ref<number | null>(null)
  const messages = ref<ChatUiMessage[]>([])
  const streaming = ref(false)
  const streamError = ref<string | null>(null)

  let abortController: AbortController | null = null

  async function loadConversations() {
    conversations.value = await conversationApi.list()
  }

  async function openConversation(id: number) {
    currentId.value = id
    const history = await conversationApi.messages(id)
    messages.value = history.map((m) => ({
      role: m.role,
      content: m.content,
      sources: m.sources,
    }))
    streamError.value = null
  }

  function newConversation() {
    stopStream()
    currentId.value = null
    messages.value = []
    streamError.value = null
  }

  async function send(question: string, kbId?: number) {
    if (streaming.value || !question.trim()) return
    streamError.value = null
    messages.value.push({ role: 'USER', content: question })
    messages.value.push({ role: 'ASSISTANT', content: '', streaming: true })
    streaming.value = true
    abortController = new AbortController()
    const assistant = lastAssistant()
    try {
      await streamChat(
        { conversationId: currentId.value ?? undefined, kbId, question },
        {
          onMeta: (meta) => {
            currentId.value = meta.conversationId
            void loadConversations()
          },
          onSources: (sources) => {
            assistant.sources = sources
          },
          onDelta: (text) => {
            assistant.content += text
          },
          onError: (msg) => {
            streamError.value = msg
          },
        },
        abortController.signal,
      )
    } catch (err) {
      if ((err as Error).name !== 'AbortError') {
        streamError.value = (err as Error).message
      }
    } finally {
      assistant.streaming = false
      streaming.value = false
      abortController = null
    }
  }

  function stopStream() {
    abortController?.abort()
  }

  async function removeConversation(id: number) {
    await conversationApi.remove(id)
    if (currentId.value === id) newConversation()
    await loadConversations()
  }

  function lastAssistant(): ChatUiMessage {
    const last = messages.value[messages.value.length - 1]
    if (last.role !== 'ASSISTANT') throw new Error('最后一条消息不是助手消息')
    return last
  }

  return {
    conversations,
    currentId,
    messages,
    streaming,
    streamError,
    loadConversations,
    openConversation,
    newConversation,
    send,
    stopStream,
    removeConversation,
  }
})
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /d/neural-base/frontend && npx vitest run src/stores/__tests__/chat.test.ts`
Expected: PASS(4 个用例)

- [ ] **Step 5: 全量验证与提交**

Run: `cd /d/neural-base/frontend && npx vitest run && npm run typecheck`
Expected: 全部通过

```bash
cd /d/neural-base && git add frontend/src/stores/chat.ts frontend/src/stores/__tests__/chat.test.ts
git commit -m "feat: chat store with streaming state machine"
```

### Task 8: Chat 页面(MarkdownRenderer + ChatMessage + ChatView)

**Files:**
- Create: `frontend/src/components/MarkdownRenderer.vue`
- Create: `frontend/src/components/ChatMessage.vue`
- Create: `frontend/src/views/ChatView.vue`(替换占位)
- Test: `frontend/src/components/__tests__/MarkdownRenderer.test.ts`
- Test: `frontend/src/components/__tests__/ChatMessage.test.ts`

**Interfaces:**
- Consumes: `useChatStore`(Task 7)、`kbApi.listKbs`(Task 4)、`GlassCard`(Task 3)、`feedback.message`(Task 3)
- Produces: `MarkdownRenderer.vue` prop `content: string`(markdown-it `html:false` + linkify + highlight.js 自动语言 + DOMPurify 消毒 + 代码块注入复制按钮)
- Produces: `ChatMessage.vue` props `{ role: 'USER'|'ASSISTANT'; content: string; sources?: RagSource[]; streaming?: boolean }`
- Produces: `ChatView.vue` 会话列表(桌面侧栏/移动端抽屉)+ 消息流 + 输入区(发送/停止)+ 模式切换(普通/知识库下拉选 KB)

- [ ] **Step 1: 写失败测试(MarkdownRenderer.test.ts)**

```ts
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
    expect(wrapper.html()).not.toContain('alert(1)')
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
    expect(writeText).toHaveBeenCalledWith('const a = 1')
  })
})
```

- [ ] **Step 2: 写失败测试(ChatMessage.test.ts)**

```ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ChatMessage from '@/components/ChatMessage.vue'

const sources = [{ docId: 2, title: '参考文档', content: '片段内容', score: 0.9 }]

describe('ChatMessage', () => {
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
```

- [ ] **Step 3: 运行测试确认失败**

Run: `cd /d/neural-base/frontend && npx vitest run src/components/__tests__/MarkdownRenderer.test.ts src/components/__tests__/ChatMessage.test.ts`
Expected: FAIL——找不到组件

- [ ] **Step 4: 实现 MarkdownRenderer.vue**

```vue
<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import 'highlight.js/styles/github-dark.css'
import { message } from '@/utils/feedback'

const props = defineProps<{ content: string }>()

const md = new MarkdownIt({
  html: false, // 原始 HTML 一律转义(spec 二:Markdown 消毒第一道)
  linkify: true,
  highlight(code: string, lang: string): string {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${hljs.highlight(code, { language: lang }).value}</code></pre>`
      } catch {
        /* 高亮失败走默认转义 */
      }
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(code)}</code></pre>`
  },
})

const container = ref<HTMLElement>()

function renderHtml(): string {
  // 第二道消毒:渲染产物再过 DOMPurify(spec 二)
  return DOMPurify.sanitize(md.render(props.content), { USE_PROFILES: { html: true } })
}

function injectCopyButtons() {
  container.value?.querySelectorAll('pre').forEach((pre) => {
    if (pre.querySelector('.code-copy-btn')) return
    pre.classList.add('code-block')
    const btn = document.createElement('button')
    btn.className = 'code-copy-btn'
    btn.type = 'button'
    btn.textContent = '复制'
    pre.appendChild(btn)
  })
}

function onClick(e: MouseEvent) {
  const btn = (e.target as HTMLElement).closest('.code-copy-btn')
  if (!btn) return
  const code = btn.closest('.code-block')?.querySelector('code')?.textContent ?? ''
  void navigator.clipboard?.writeText(code)
  message.success('已复制')
}

onMounted(injectCopyButtons)
watch(() => props.content, () => void nextTick(injectCopyButtons))
</script>

<template>
  <div ref="container" class="md-body" v-html="renderHtml()" @click="onClick" />
</template>

<style scoped>
.md-body :deep(p) {
  margin: 0.5em 0;
}
.md-body :deep(pre.code-block) {
  position: relative;
  margin: 0.75em 0;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  overflow: hidden;
}
.md-body :deep(.code-copy-btn) {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 2px 10px;
  font-size: 12px;
  color: #9ca3af;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  cursor: pointer;
  transition: color 0.2s ease;
}
.md-body :deep(.code-copy-btn:hover) {
  color: #f3f4f6;
}
.md-body :deep(code) {
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
}
.md-body :deep(a) {
  color: #22d3ee;
}
.md-body :deep(table) {
  border-collapse: collapse;
  margin: 0.75em 0;
}
.md-body :deep(th),
.md-body :deep(td) {
  border: 1px solid rgba(255, 255, 255, 0.08);
  padding: 6px 12px;
}
</style>
```

- [ ] **Step 5: 实现 ChatMessage.vue**

```vue
<script setup lang="ts">
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import type { RagSource } from '@/types'

defineProps<{
  role: 'USER' | 'ASSISTANT'
  content: string
  sources?: RagSource[]
  streaming?: boolean
}>()
</script>

<template>
  <div class="flex" :class="role === 'USER' ? 'justify-end' : 'justify-start'">
    <div class="max-w-[88%] md:max-w-[75%]">
      <div
        v-if="role === 'USER'"
        class="whitespace-pre-wrap rounded-2xl rounded-br-sm border border-white/10 bg-gradient-to-br from-cyan-500/15 to-purple-500/15 px-4 py-2.5 text-[15px] leading-relaxed text-gray-100"
      >
        {{ content }}
      </div>
      <div v-else class="space-y-3">
        <div v-if="sources?.length" class="glass-card space-y-2 !p-3">
          <div class="text-xs text-gray-500">📚 参考来源</div>
          <div
            v-for="(s, i) in sources"
            :key="i"
            class="rounded-lg border border-white/5 bg-white/[0.02] px-3 py-2"
          >
            <div class="text-xs text-gray-300">{{ s.title }}</div>
            <div class="mt-1 line-clamp-2 text-xs text-gray-500">{{ s.content }}</div>
          </div>
        </div>
        <div class="text-[15px] leading-relaxed text-gray-100">
          <MarkdownRenderer :content="content" />
          <span v-if="streaming" class="nb-cursor" />
        </div>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 6: 运行组件测试确认通过**

Run: `cd /d/neural-base/frontend && npx vitest run src/components/__tests__/MarkdownRenderer.test.ts src/components/__tests__/ChatMessage.test.ts`
Expected: PASS(5 个用例)

- [ ] **Step 7: 替换 ChatView.vue 占位为真实聊天页**

```vue
<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { NButton, NDrawer, NDrawerContent, NInput, NPopconfirm, NSelect, NTag } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useChatStore } from '@/stores/chat'
import { listKbs } from '@/api/kb'
import ChatMessage from '@/components/ChatMessage.vue'
import type { Kb } from '@/types'

const chatStore = useChatStore()
const kbs = ref<Kb[]>([])
const mode = ref<'normal' | 'rag'>('normal')
const kbId = ref<number | null>(null)
const input = ref('')
const drawerOpen = ref(false)
const scrollRef = ref<HTMLElement>()

const modeOptions = [
  { label: '普通模式', value: 'normal' },
  { label: '知识库模式', value: 'rag' },
]

const activeKbName = computed(
  () => kbs.value.find((k) => k.id === kbId.value)?.name ?? '未选择',
)

onMounted(async () => {
  await chatStore.loadConversations()
  try {
    kbs.value = await listKbs()
  } catch {
    kbs.value = []
  }
})

watch(
  () => [chatStore.messages.length, chatStore.messages.at(-1)?.content],
  () => void nextTick(scrollToBottom),
)

function scrollToBottom() {
  scrollRef.value?.scrollTo({ top: scrollRef.value.scrollHeight })
}

async function onSend() {
  const question = input.value.trim()
  if (!question || chatStore.streaming) return
  input.value = ''
  await chatStore.send(question, mode.value === 'rag' && kbId.value ? kbId.value : undefined)
  scrollToBottom()
}

async function onNewConversation() {
  chatStore.newConversation()
  drawerOpen.value = false
}

async function onOpenConversation(id: number) {
  await chatStore.openConversation(id)
  drawerOpen.value = false
  scrollToBottom()
}
</script>

<template>
  <div class="flex h-[calc(100vh-9.5rem)] gap-4">
    <!-- 桌面端会话侧栏 -->
    <aside class="hidden w-64 shrink-0 flex-col md:flex">
      <div class="mb-3 flex items-center justify-between">
        <span class="text-sm text-gray-400">会话</span>
        <n-button size="small" quaternary @click="onNewConversation">
          <template #icon><Icon icon="mdi:plus" class="h-4 w-4" /></template>
          新建
        </n-button>
      </div>
      <div class="flex-1 space-y-1.5 overflow-y-auto pr-1">
        <button
          v-for="c in chatStore.conversations"
          :key="c.id"
          class="glass-card w-full !p-3 text-left transition-colors"
          :class="c.id === chatStore.currentId ? '!border-cyan-400/40' : 'hover:!border-white/10'"
          @click="onOpenConversation(c.id)"
        >
          <div class="flex items-center justify-between gap-2">
            <span class="truncate text-sm text-gray-200">{{ c.title }}</span>
            <n-popconfirm @positive-click="chatStore.removeConversation(c.id)">
              <template #trigger>
                <span class="text-gray-600 hover:text-red-400">
                  <Icon icon="mdi:trash-can-outline" class="h-4 w-4" />
                </span>
              </template>
              删除该会话?
            </n-popconfirm>
          </div>
          <div v-if="c.lastMessageContent" class="mt-1 truncate text-xs text-gray-500">
            {{ c.lastMessageContent }}
          </div>
        </button>
        <div v-if="!chatStore.conversations.length" class="py-8 text-center text-xs text-gray-600">
          暂无会话,直接提问即可创建
        </div>
      </div>
    </aside>

    <!-- 移动端会话抽屉 -->
    <n-drawer v-model:show="drawerOpen" placement="left" :width="280">
      <n-drawer-content title="会话">
        <div class="space-y-1.5">
          <button
            v-for="c in chatStore.conversations"
            :key="c.id"
            class="glass-card w-full !p-3 text-left"
            :class="c.id === chatStore.currentId ? '!border-cyan-400/40' : ''"
            @click="onOpenConversation(c.id)"
          >
            <span class="truncate text-sm text-gray-200">{{ c.title }}</span>
          </button>
          <div v-if="!chatStore.conversations.length" class="py-8 text-center text-xs text-gray-600">
            暂无会话
          </div>
        </div>
      </n-drawer-content>
    </n-drawer>

    <!-- 消息区 -->
    <div class="flex min-w-0 flex-1 flex-col">
      <div class="mb-3 flex items-center gap-2">
        <n-button class="md:hidden" quaternary size="small" @click="drawerOpen = true">
          <template #icon><Icon icon="mdi:menu" class="h-4 w-4" /></template>
          会话
        </n-button>
        <n-select
          v-model:value="mode"
          :options="modeOptions"
          size="small"
          class="w-32"
        />
        <template v-if="mode === 'rag'">
          <n-select
            v-model:value="kbId"
            size="small"
            class="w-40"
            placeholder="选择知识库"
            :options="kbs.map((k) => ({ label: k.name, value: k.id }))"
          />
          <n-tag v-if="kbId" size="small" :bordered="false" type="info">{{ activeKbName }}</n-tag>
        </template>
      </div>

      <div ref="scrollRef" class="flex-1 space-y-4 overflow-y-auto pb-4 pr-1">
        <div v-if="!chatStore.messages.length" class="flex h-full flex-col items-center justify-center gap-3">
          <span class="h-3 w-3 rounded-full bg-gradient-to-r from-cyan-400 to-purple-400" />
          <p class="text-sm text-gray-500">开始提问吧,支持普通模式与知识库模式</p>
        </div>
        <ChatMessage
          v-for="(m, i) in chatStore.messages"
          :key="i"
          :role="m.role"
          :content="m.content"
          :sources="m.sources"
          :streaming="m.streaming"
        />
        <div v-if="chatStore.streamError" class="rounded-lg border border-red-400/30 bg-red-500/10 px-3 py-2 text-xs text-red-300">
          ⚠️ {{ chatStore.streamError }}(已生成内容保留)
        </div>
      </div>

      <div class="mt-3 flex items-end gap-2">
        <n-input
          v-model:value="input"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 6 }"
          placeholder="输入问题,Enter 发送,Shift+Enter 换行"
          :disabled="chatStore.streaming"
          @keydown.enter.exact.prevent="onSend"
        />
        <n-button v-if="chatStore.streaming" type="warning" @click="chatStore.stopStream()">
          停止
        </n-button>
        <n-button v-else type="primary" :disabled="!input.trim()" @click="onSend">
          <template #icon><Icon icon="mdi:send" class="h-4 w-4" /></template>
          发送
        </n-button>
      </div>
    </div>
  </div>
</template>
```

> 注意:`messages.at(-1)` 与 `v-for` 序号 key 在追加流式内容时会导致 MarkdownRenderer 整段重渲染,单用户规模可接受;若流式卡顿,后续可改为按 delta 节流——不在本轮范围。

- [ ] **Step 8: 全量验证与提交**

Run: `cd /d/neural-base/frontend && npx vitest run && npm run typecheck && npm run build`
Expected: 全部通过

```bash
cd /d/neural-base && git add frontend/src/components/MarkdownRenderer.vue frontend/src/components/ChatMessage.vue frontend/src/views/ChatView.vue frontend/src/components/__tests__/MarkdownRenderer.test.ts frontend/src/components/__tests__/ChatMessage.test.ts
git commit -m "feat: chat page with markdown renderer, streaming messages, conversation list"
```

---

### Task 9: 迭代 1 真实冒烟(登录 + 双模式对话)

**Files:** 无(纯验证;发现 bug 时修复并在本任务提交)

**Interfaces:**
- Consumes: Task 1–8 全部产物

- [ ] **Step 1: 启动后端(8081)**

Run:
```bash
cd /d/neural-base/backend && export DB_PASSWORD='Huangwei990758' && mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```
Expected: 启动成功,`/api/health` 返回 ok(另开终端:`curl http://localhost:8081/api/health`)

- [ ] **Step 2: 启动前端并冒烟**

Run: `cd /d/neural-base/frontend && npm run dev`

浏览器 `http://localhost:5173` 逐项验证:
1. 未登录访问 `/` → 自动跳 `/login`
2. 登录 `admin / admin123` → 跳转首页占位;DevTools Application 面板可见 `nb_token` cookie(HttpOnly ✓)
3. 顶部导航切到「AI 对话」→ 空状态引导可见
4. 普通模式发送「用一句话介绍你自己」→ 打字机流式输出,结束后光标消失,会话列表出现新会话(标题=问题前 20 字)
5. 「停止」按钮:发送长问题后立刻点停止 → 输出中断,已生成内容保留,无错误横幅
6. 知识库模式:选择任一已有 KB(本地 PG 有 Phase 2/3 冒烟遗留数据)→ 问「我上传的文档讲了什么」→ 出现「📚 参考来源」卡片 + 引用回答
7. 切换回普通模式再发一条 → 无来源卡片
8. 刷新页面 → 仍登录态(守卫过);删除一个会话 → 列表消失
9. 退出登录 → 跳 `/login`,DevTools 中 cookie 已清除
10. 开发者工具切到手机视口(<768px)→ 顶部导航消失、底部 Tab 出现,聊天页「会话」按钮呼出抽屉

- [ ] **Step 3: 修复冒烟发现的问题并回归**

Run: `cd /d/neural-base/frontend && npx vitest run && npm run typecheck && npm run build`
Expected: 全绿;冒烟问题修复后重新走一遍 Step 2 清单

- [ ] **Step 4: 提交(如无改动则跳过)**

```bash
cd /d/neural-base && git add -u frontend backend && git status
# 仅提交本任务改动到的文件,message 如:
# fix: <冒烟问题简述>
```

**迭代 1 完成标准**:Step 2 全部 10 项通过 + 前端测试/构建绿 + 后端 `mvn test` 绿。

---

# 迭代二

### Task 10: 后端 DELETE /api/document/{id}(向量+文件+元数据全删)

**Files:**
- Modify: `backend/src/main/java/com/neuralbase/service/DocumentService.java`(加 `delete()` 方法与 slf4j Logger)
- Modify: `backend/src/main/java/com/neuralbase/controller/DocumentController.java`(加 `@DeleteMapping("/{id}")`)
- Create: `backend/src/test/java/com/neuralbase/document/DocumentDeleteTest.java`

**Interfaces:**
- Produces: `DELETE /api/document/{id}` → `Result.ok(null)`;不存在 → `IllegalArgumentException("文档不存在: "+id)` → 400。删除语义(spec Q9-A):向量行(`DELETE FROM vector_store WHERE metadata->>'docId'=?`,复用 `process()` 同款 SQL)→ 磁盘文件 `Files.deleteIfExists`(失败仅 log.warn 不阻断)→ `documentMapper.deleteById`

- [ ] **Step 1: 写失败测试(DocumentDeleteTest.java)**

```java
package com.neuralbase.document;

import com.neuralbase.config.MockEmbeddingConfig;
import com.neuralbase.entity.Document;
import com.neuralbase.mapper.DocumentMapper;
import com.neuralbase.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(MockEmbeddingConfig.class)
class DocumentDeleteTest {

    @Autowired
    private DocumentService documentService;
    @Autowired
    private DocumentMapper documentMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deleteRemovesVectorsFileAndRow() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "delete-me.md", "text/markdown",
            ("删除测试内容。".repeat(200)).getBytes());
        Document doc = documentService.upload(file, 1L);
        documentService.process(doc.getId());

        Document before = documentMapper.selectById(doc.getId());
        assertThat(before.getStatus()).isEqualTo("READY");
        assertThat(before.getChunkCount()).isGreaterThan(0);

        documentService.delete(doc.getId());

        assertThat(documentMapper.selectById(doc.getId())).isNull();
        Integer vectors = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vector_store WHERE metadata->>'docId' = ?",
            Integer.class, String.valueOf(doc.getId()));
        assertThat(vectors).isZero();
        assertThat(Files.exists(Path.of(before.getFilePath()))).isFalse();
    }

    @Test
    void deleteMissingDocumentThrows() {
        assertThatThrownBy(() -> documentService.delete(999_999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("文档不存在");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /d/neural-base/backend && mvn test -Dtest=DocumentDeleteTest -q`
Expected: FAIL——`DocumentService.delete` 不存在(编译错误)

- [ ] **Step 3: 实现 DocumentService.delete()**

`DocumentService.java` 追加(imports 补 `org.slf4j.Logger`、`org.slf4j.LoggerFactory`;类首行加 `private static final Logger log = LoggerFactory.getLogger(DocumentService.class);`,放在现有字段前):

```java
    public void delete(Long documentId) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }
        jdbcTemplate.update(
            "DELETE FROM vector_store WHERE metadata->>'docId' = ?",
            String.valueOf(documentId));
        if (doc.getFilePath() != null && !doc.getFilePath().isBlank()) {
            try {
                Files.deleteIfExists(Path.of(doc.getFilePath()));
            } catch (IOException e) {
                log.warn("删除文档文件失败 {}: {}", doc.getFilePath(), e.getMessage());
            }
        }
        documentMapper.deleteById(documentId);
    }
```

- [ ] **Step 4: 实现 Controller 端点**

`DocumentController.java` 追加(imports 补 `org.springframework.web.bind.annotation.DeleteMapping`、`org.springframework.web.bind.annotation.PathVariable`):

```java
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.ok(null);
    }
```

- [ ] **Step 5: 运行测试确认通过 + 全量回归**

Run: `cd /d/neural-base/backend && mvn test -q`
Expected: PASS(含新测试,总数 74:既有 66 + Task 1 新增 6 + 本任务 2)

- [ ] **Step 6: 提交**

```bash
cd /d/neural-base && git add backend/src/main/java/com/neuralbase/service/DocumentService.java backend/src/main/java/com/neuralbase/controller/DocumentController.java backend/src/test/java/com/neuralbase/document/DocumentDeleteTest.java
git commit -m "feat: delete document endpoint (vectors + file + row)"
```

---

### Task 11: 知识库页面(document store + KB CRUD/色板 + 上传/搜索/轮询/删除)

**Files:**
- Create: `frontend/src/stores/document.ts`
- Create: `frontend/src/components/FileUploader.vue`
- Create: `frontend/src/views/KnowledgeBaseView.vue`(替换占位)
- Test: `frontend/src/stores/__tests__/document.test.ts`

**Interfaces:**
- Consumes: `kbApi.*`、`documentApi.*`(Task 4)、`GlassCard`、`feedback.message/dialog`(Task 3)
- Produces: `useDocumentStore`:`kbs`、`currentKbId`、`documents`、`searchResults: Ref<SearchResult[]|null>`、`loadKbs()`、`loadDocuments()`、`selectKb(id|null)`、`upload(file, onProgress?)`、`remove(id)`、`search(query)`、`createKb(payload)`、`removeKb(id)`、轮询(2s,直至无 PENDING/PROCESSING 自动停,组件卸载时 `stopPolling()`)
- Produces: `FileUploader.vue`(n-upload 拖拽,accept `.pdf,.doc,.docx,.md,.txt,.markdown`,进度回调,成功提示「上传成功,后台解析中」)
- KB 色板 8 色(spec Q12-A):`#22d3ee` 青、`#a78bfa` 紫、`#fbbf24` 琥珀、`#34d399` 绿、`#60a5fa` 蓝、`#f472b6` 粉、`#fb923c` 橙、`#6b7280` 灰

- [ ] **Step 1: 写失败测试(stores/__tests__/document.test.ts)**

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useDocumentStore } from '@/stores/document'

vi.mock('@/api/kb', () => ({
  listKbs: vi.fn(),
  createKb: vi.fn(),
  deleteKb: vi.fn(),
}))

vi.mock('@/api/document', () => ({
  listDocuments: vi.fn(),
  uploadDocument: vi.fn(),
  searchDocuments: vi.fn(),
  deleteDocument: vi.fn(),
}))

import { listKbs, createKb, deleteKb } from '@/api/kb'
import { listDocuments, uploadDocument, searchDocuments, deleteDocument } from '@/api/document'

const kb = { id: 1, name: '我的知识库', createdAt: '' }
const readyDoc = {
  id: 11,
  title: 'a.md',
  fileType: 'md',
  status: 'READY' as const,
  createdAt: '',
}

describe('document store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  it('加载 KB 与文档,selectKb 切换并清空搜索结果', async () => {
    vi.mocked(listKbs).mockResolvedValue([kb])
    vi.mocked(listDocuments).mockResolvedValue([readyDoc])
    const store = useDocumentStore()

    await store.loadKbs()
    await store.loadDocuments()
    expect(store.kbs).toHaveLength(1)
    expect(store.documents).toHaveLength(1)

    store.searchResults = [{ docId: 11, title: 'a.md', content: 'x', score: 0.9 }]
    store.selectKb(1)
    expect(store.currentKbId).toBe(1)
    expect(store.searchResults).toBeNull()
  })

  it('上传后立即加载并启动轮询,全部就绪后自动停止', async () => {
    vi.mocked(listKbs).mockResolvedValue([kb])
    vi.mocked(uploadDocument).mockResolvedValue(readyDoc)
    vi.mocked(listDocuments)
      .mockResolvedValueOnce([{ ...readyDoc, status: 'PROCESSING' as const }])
      .mockResolvedValue([readyDoc])
    const store = useDocumentStore()

    await store.upload(new File(['x'], 'a.md'))
    expect(uploadDocument).toHaveBeenCalled()
    expect(listDocuments).toHaveBeenCalledTimes(1) // 上传成功后刷新一次(PROCESSING)

    await vi.advanceTimersByTimeAsync(2000)
    expect(listDocuments).toHaveBeenCalledTimes(2) // 轮询一次发现 READY,自动停止
    expect(store.documents.every((d) => d.status === 'READY')).toBe(true)
    store.stopPolling()
  })

  it('删除文档后刷新列表', async () => {
    vi.mocked(deleteDocument).mockResolvedValue(null)
    vi.mocked(listDocuments).mockResolvedValue([])
    const store = useDocumentStore()
    await store.remove(11)
    expect(deleteDocument).toHaveBeenCalledWith(11)
    expect(store.documents).toHaveLength(0)
  })

  it('搜索写入 searchResults,建库与删库调 API', async () => {
    vi.mocked(searchDocuments).mockResolvedValue([{ docId: 11, title: 'a.md', content: 'x', score: 0.9 }])
    vi.mocked(createKb).mockResolvedValue(kb)
    vi.mocked(deleteKb).mockResolvedValue(null)
    const store = useDocumentStore()

    await store.search('问题')
    expect(store.searchResults).toHaveLength(1)

    await store.createKb({ name: '新库' })
    expect(createKb).toHaveBeenCalledWith({ name: '新库' })
    await store.removeKb(1)
    expect(deleteKb).toHaveBeenCalledWith(1)
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /d/neural-base/frontend && npx vitest run src/stores/__tests__/document.test.ts`
Expected: FAIL——找不到 `@/stores/document`

- [ ] **Step 3: 实现 stores/document.ts**

```ts
import { ref } from 'vue'
import { defineStore } from 'pinia'
import * as kbApi from '@/api/kb'
import * as documentApi from '@/api/document'
import type { DocumentItem, Kb, SearchResult } from '@/types'

export const useDocumentStore = defineStore('document', () => {
  const kbs = ref<Kb[]>([])
  const currentKbId = ref<number | null>(null)
  const documents = ref<DocumentItem[]>([])
  const searchResults = ref<SearchResult[] | null>(null)

  let pollTimer: number | null = null

  async function loadKbs() {
    kbs.value = await kbApi.listKbs()
  }

  async function loadDocuments() {
    documents.value = await documentApi.listDocuments(currentKbId.value ?? undefined)
  }

  function selectKb(id: number | null) {
    currentKbId.value = id
    searchResults.value = null
    void loadDocuments()
  }

  async function upload(file: File, onProgress?: (pct: number) => void) {
    await documentApi.uploadDocument(file, currentKbId.value ?? undefined, onProgress)
    await loadDocuments()
    startPolling()
  }

  async function remove(id: number) {
    await documentApi.deleteDocument(id)
    await loadDocuments()
  }

  async function search(query: string) {
    searchResults.value = await documentApi.searchDocuments(
      query,
      currentKbId.value ?? undefined,
    )
  }

  async function createKb(payload: { name: string; description?: string; color?: string }) {
    await kbApi.createKb(payload)
    await loadKbs()
  }

  async function removeKb(id: number) {
    await kbApi.deleteKb(id)
    if (currentKbId.value === id) selectKb(null)
    await loadKbs()
  }

  function startPolling() {
    stopPolling()
    pollTimer = window.setInterval(() => {
      void (async () => {
        await loadDocuments()
        const pending = documents.value.some(
          (d) => d.status === 'PENDING' || d.status === 'PROCESSING',
        )
        if (!pending) stopPolling()
      })()
    }, 2000)
  }

  function stopPolling() {
    if (pollTimer != null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  return {
    kbs,
    currentKbId,
    documents,
    searchResults,
    loadKbs,
    loadDocuments,
    selectKb,
    upload,
    remove,
    search,
    createKb,
    removeKb,
    startPolling,
    stopPolling,
  }
})
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /d/neural-base/frontend && npx vitest run src/stores/__tests__/document.test.ts`
Expected: PASS(4 个用例)

- [ ] **Step 5: 创建 FileUploader.vue**

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { NIcon, NUpload, NUploadDragger } from 'naive-ui'
import type { UploadCustomRequestOptions } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useDocumentStore } from '@/stores/document'
import { message } from '@/utils/feedback'

const docStore = useDocumentStore()
const uploading = ref(false)

async function onUpload({ file, onFinish, onError, onProgress }: UploadCustomRequestOptions) {
  uploading.value = true
  try {
    await docStore.upload(file.file as File, (pct) => onProgress({ percent: pct }))
    onFinish()
    message.success('上传成功,后台解析中')
  } catch (e) {
    onError()
    message.error((e as Error).message)
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <n-upload
    :custom-request="onUpload"
    :show-file-list="false"
    accept=".pdf,.doc,.docx,.md,.txt,.markdown"
    :disabled="uploading"
  >
    <n-upload-dragger class="glass-card !border-dashed">
      <div class="flex flex-col items-center gap-2 py-2">
        <n-icon size="28" :color="uploading ? '#9ca3af' : '#22d3ee'">
          <Icon :icon="uploading ? 'mdi:loading' : 'mdi:cloud-upload-outline'" />
        </n-icon>
        <span class="text-sm text-gray-300">
          {{ uploading ? '上传中…' : '点击或拖拽文件到此处上传' }}
        </span>
        <span class="text-xs text-gray-600">支持 PDF / Word / Markdown / TXT</span>
      </div>
    </n-upload-dragger>
  </n-upload>
</template>
```

- [ ] **Step 6: 替换 KnowledgeBaseView.vue 占位为真实页面**

```vue
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { NButton, NInput, NModal, NPopconfirm, NTag } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useDocumentStore } from '@/stores/document'
import { dialog, message } from '@/utils/feedback'
import FileUploader from '@/components/FileUploader.vue'
import type { DocumentStatus } from '@/types'

const docStore = useDocumentStore()

const kbModal = ref(false)
const kbForm = reactive({ name: '', description: '', color: '#22d3ee' })
const kbPalette = ['#22d3ee', '#a78bfa', '#fbbf24', '#34d399', '#60a5fa', '#f472b6', '#fb923c', '#6b7280']

const query = ref('')
const searching = ref(false)

const statusMeta: Record<DocumentStatus, { type: 'default' | 'info' | 'success' | 'error'; label: string }> = {
  PENDING: { type: 'default', label: '排队中' },
  PROCESSING: { type: 'info', label: '解析中' },
  READY: { type: 'success', label: '就绪' },
  FAILED: { type: 'error', label: '失败' },
}

const hasProcessing = computed(() =>
  docStore.documents.some((d) => d.status === 'PENDING' || d.status === 'PROCESSING'),
)

onMounted(async () => {
  await docStore.loadKbs()
  await docStore.loadDocuments()
  if (hasProcessing.value) docStore.startPolling()
})

onBeforeUnmount(() => docStore.stopPolling())

async function onCreateKb() {
  if (!kbForm.name.trim()) {
    message.warning('请输入知识库名称')
    return
  }
  await docStore.createKb({ ...kbForm })
  kbModal.value = false
  kbForm.name = ''
  kbForm.description = ''
  message.success('创建成功')
}

async function onSearch() {
  if (!query.value.trim()) return
  searching.value = true
  try {
    await docStore.search(query.value)
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    searching.value = false
  }
}

function confirmDeleteKb(id: number, name: string) {
  dialog.warning({
    title: '删除知识库',
    content: `确定删除「${name}」?文档列表不会自动删除。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: () => void docStore.removeKb(id),
  })
}
</script>

<template>
  <div class="flex h-[calc(100vh-9.5rem)] gap-4">
    <!-- 知识库列表 -->
    <aside class="hidden w-64 shrink-0 flex-col md:flex">
      <div class="mb-3 flex items-center justify-between">
        <span class="text-sm text-gray-400">知识库</span>
        <n-button size="small" quaternary @click="kbModal = true">
          <template #icon><Icon icon="mdi:plus" class="h-4 w-4" /></template>
          新建
        </n-button>
      </div>
      <div class="flex-1 space-y-1.5 overflow-y-auto pr-1">
        <button
          class="glass-card w-full !p-3 text-left"
          :class="docStore.currentKbId === null ? '!border-cyan-400/40' : ''"
          @click="docStore.selectKb(null)"
        >
          <span class="text-sm text-gray-200">全部文档</span>
        </button>
        <button
          v-for="k in docStore.kbs"
          :key="k.id"
          class="glass-card w-full !p-3 text-left"
          :class="docStore.currentKbId === k.id ? '!border-cyan-400/40' : ''"
          @click="docStore.selectKb(k.id)"
        >
          <div class="flex items-center gap-2">
            <span v-if="k.color" class="h-2.5 w-2.5 rounded-full" :style="{ background: k.color }" />
            <span class="truncate text-sm text-gray-200">{{ k.name }}</span>
          </div>
        </button>
        <div v-if="!docStore.kbs.length" class="py-8 text-center text-xs text-gray-600">
          暂无知识库,点击「新建」创建
        </div>
      </div>
    </aside>

    <!-- 主区 -->
    <div class="flex min-w-0 flex-1 flex-col gap-3 overflow-y-auto">
      <FileUploader />

      <div class="flex gap-2">
        <n-input v-model:value="query" placeholder="语义搜索:输入自然语言问题…" @keyup.enter="onSearch" />
        <n-button type="primary" :loading="searching" @click="onSearch">搜索</n-button>
      </div>

      <div v-if="docStore.searchResults" class="space-y-2">
        <div class="text-xs text-gray-500">
          搜索结果 {{ docStore.searchResults.length }} 条
          <n-button text size="tiny" @click="docStore.searchResults = null">清除</n-button>
        </div>
        <div
          v-for="r in docStore.searchResults"
          :key="`${r.docId}-${r.score}`"
          class="glass-card !p-3"
        >
          <div class="flex items-center justify-between">
            <span class="text-sm text-gray-200">{{ r.title ?? `文档#${r.docId}` }}</span>
            <span class="text-xs text-gray-600">相似度 {{ r.score.toFixed(3) }}</span>
          </div>
          <p class="mt-1 line-clamp-3 text-xs leading-relaxed text-gray-400">{{ r.content }}</p>
        </div>
      </div>

      <template v-else>
        <div v-if="hasProcessing" class="text-xs text-gray-500">
          有文档处理中,每 2 秒自动刷新…
        </div>
        <div class="space-y-2">
          <div v-for="d in docStore.documents" :key="d.id" class="glass-card !p-3">
            <div class="flex items-center justify-between gap-2">
              <span class="truncate text-sm text-gray-200">{{ d.title }}</span>
              <div class="flex shrink-0 items-center gap-2">
                <n-tag size="small" :type="statusMeta[d.status].type" :bordered="false">
                  {{ statusMeta[d.status].label }}<template v-if="d.chunkCount"> · {{ d.chunkCount }} 块</template>
                </n-tag>
                <n-popconfirm @positive-click="docStore.remove(d.id)">
                  <template #trigger>
                    <span class="cursor-pointer text-gray-600 hover:text-red-400">
                      <Icon icon="mdi:trash-can-outline" class="h-4 w-4" />
                    </span>
                  </template>
                  删除该文档?
                </n-popconfirm>
              </div>
            </div>
            <div v-if="d.status === 'FAILED' && d.error" class="mt-1 text-xs text-red-400">
              {{ d.error }}
            </div>
          </div>
          <div v-if="!docStore.documents.length" class="py-12 text-center text-sm text-gray-600">
            还没有文档,上传一个开始构建知识库
          </div>
        </div>
      </template>
    </div>
  </div>

  <!-- 新建知识库弹窗 -->
  <n-modal v-model:show="kbModal" preset="dialog" title="新建知识库" positive-text="创建" negative-text="取消" @positive-click="onCreateKb">
    <div class="space-y-4 py-2">
      <n-input v-model:value="kbForm.name" placeholder="名称" />
      <n-input v-model:value="kbForm.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="描述(可选)" />
      <div>
        <div class="mb-2 text-xs text-gray-500">颜色</div>
        <div class="flex gap-2">
          <button
            v-for="c in kbPalette"
            :key="c"
            class="h-6 w-6 rounded-full border-2 transition-transform"
            :style="{ background: c, borderColor: kbForm.color === c ? '#fff' : 'transparent' }"
            @click="kbForm.color = c"
          />
        </div>
      </div>
    </div>
  </n-modal>
</template>
```

> 注:KB 列表移动端入口后续由 Dashboard 模块卡进入(顶部导航在移动端收进底部 Tab),本页移动端隐藏侧栏、主区全宽即可(已满足 spec Q11-A)。

- [ ] **Step 7: 全量验证与提交**

Run: `cd /d/neural-base/frontend && npx vitest run && npm run typecheck && npm run build`
Expected: 全部通过

```bash
cd /d/neural-base && git add frontend/src/stores/document.ts frontend/src/stores/__tests__/document.test.ts frontend/src/components/FileUploader.vue frontend/src/views/KnowledgeBaseView.vue
git commit -m "feat: knowledge base page (kb crud+palette, upload, search, status polling, delete)"
```

---

### Task 12: 八股文库页面(baguwen store + 导入/任务/列表/详情/收藏)

**Files:**
- Create: `frontend/src/stores/baguwen.ts`
- Create: `frontend/src/views/BaguwenView.vue`(替换占位)
- Create: `frontend/src/views/BaguwenDetailView.vue`(替换占位)
- Test: `frontend/src/stores/__tests__/baguwen.test.ts`

**Interfaces:**
- Consumes: `baguwenApi.*`、`kbApi.listKbs`(Task 4)、`GlassCard`、`feedback.message/dialog`(Task 3)
- Produces: `useBaguwenStore`:`articles`、`total`、`tags`、`tasks`、`filter: { tag; keyword; page; size }`、`loadTags()`、`loadArticles()`、`importUrls(urls: string[])`、`refreshTasks()`、`retry(id)`、`removeArticle(id)`、`favorite(id, kbId)`;任务轮询 2s(有 PENDING/PROCESSING 时),全部落定后刷新文章与标签并停
- 详情页路由 `/baguwen/:id`,问答对卡片难度徽标(简单=success/中等=warning/困难=error)

- [ ] **Step 1: 写失败测试(stores/__tests__/baguwen.test.ts)**

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useBaguwenStore } from '@/stores/baguwen'

vi.mock('@/api/baguwen', () => ({
  importUrls: vi.fn(),
  listTasks: vi.fn(),
  retryTask: vi.fn(),
  listArticles: vi.fn(),
  listTags: vi.fn(),
  deleteArticle: vi.fn(),
  favoriteArticle: vi.fn(),
}))

import {
  importUrls,
  listTasks,
  retryTask,
  listArticles,
  listTags,
  deleteArticle,
  favoriteArticle,
} from '@/api/baguwen'

const article = { id: 1, title: 'JVM 面试题', url: 'https://x', tags: ['Java'], createdAt: '' }
const task = {
  id: 1,
  url: 'https://x',
  status: 'SUCCESS' as const,
  attempt: 0,
  createdAt: '',
  updatedAt: '',
}

describe('baguwen store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  it('导入后刷新任务并轮询,全部落定后刷新文章与标签', async () => {
    vi.mocked(importUrls).mockResolvedValue([task])
    vi.mocked(listTasks)
      .mockResolvedValueOnce([{ ...task, status: 'PROCESSING' as const }])
      .mockResolvedValue([task])
    vi.mocked(listArticles).mockResolvedValue({ records: [article], total: 1, size: 20, current: 1 })
    vi.mocked(listTags).mockResolvedValue([{ tag: 'Java', count: 1 }])
    const store = useBaguwenStore()

    await store.importUrls(['https://x'])
    expect(store.tasks).toHaveLength(1)
    expect(store.tasks[0].status).toBe('PROCESSING')

    await vi.advanceTimersByTimeAsync(2000)
    expect(store.articles).toHaveLength(1)
    expect(store.tags).toHaveLength(1)
    store.stopPolling()
  })

  it('重试调 API 并重启轮询,删除文章后刷新列表与标签', async () => {
    vi.mocked(retryTask).mockResolvedValue(task)
    vi.mocked(listTasks).mockResolvedValue([task])
    vi.mocked(listArticles).mockResolvedValue({ records: [], total: 0, size: 20, current: 1 })
    vi.mocked(listTags).mockResolvedValue([])
    vi.mocked(deleteArticle).mockResolvedValue(null)
    const store = useBaguwenStore()

    await store.retry(1)
    expect(retryTask).toHaveBeenCalledWith(1)

    await store.removeArticle(1)
    expect(deleteArticle).toHaveBeenCalledWith(1)
  })

  it('收藏转发 kbId 并返回 embedded 数', async () => {
    vi.mocked(favoriteArticle).mockResolvedValue({ embedded: 10 })
    const store = useBaguwenStore()
    const result = await store.favorite(1, 2)
    expect(favoriteArticle).toHaveBeenCalledWith(1, 2)
    expect(result.embedded).toBe(10)
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /d/neural-base/frontend && npx vitest run src/stores/__tests__/baguwen.test.ts`
Expected: FAIL——找不到 `@/stores/baguwen`

- [ ] **Step 3: 实现 stores/baguwen.ts**

```ts
import { reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import * as baguwenApi from '@/api/baguwen'
import type { BaguwenArticleView, ImportTask, TagCount } from '@/types'

export const useBaguwenStore = defineStore('baguwen', () => {
  const articles = ref<BaguwenArticleView[]>([])
  const total = ref(0)
  const tags = ref<TagCount[]>([])
  const tasks = ref<ImportTask[]>([])
  const filter = reactive({ tag: '', keyword: '', page: 1, size: 20 })

  let pollTimer: number | null = null

  async function loadTags() {
    tags.value = await baguwenApi.listTags()
  }

  async function loadArticles() {
    const page = await baguwenApi.listArticles({
      tag: filter.tag || undefined,
      keyword: filter.keyword || undefined,
      page: filter.page,
      size: filter.size,
    })
    articles.value = page.records
    total.value = page.total
  }

  async function importUrls(urls: string[]) {
    await baguwenApi.importUrls(urls)
    await refreshTasks()
    startPolling()
  }

  async function refreshTasks() {
    tasks.value = await baguwenApi.listTasks()
  }

  async function retry(id: number) {
    await baguwenApi.retryTask(id)
    await refreshTasks()
    startPolling()
  }

  async function removeArticle(id: number) {
    await baguwenApi.deleteArticle(id)
    await loadArticles()
    await loadTags()
  }

  async function favorite(id: number, kbId: number) {
    return baguwenApi.favoriteArticle(id, kbId)
  }

  function startPolling() {
    stopPolling()
    pollTimer = window.setInterval(() => {
      void (async () => {
        await refreshTasks()
        const pending = tasks.value.some(
          (t) => t.status === 'PENDING' || t.status === 'PROCESSING',
        )
        if (!pending) {
          stopPolling()
          await loadArticles()
          await loadTags()
        }
      })()
    }, 2000)
  }

  function stopPolling() {
    if (pollTimer != null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  return {
    articles,
    total,
    tags,
    tasks,
    filter,
    loadTags,
    loadArticles,
    importUrls,
    refreshTasks,
    retry,
    removeArticle,
    favorite,
    startPolling,
    stopPolling,
  }
})
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /d/neural-base/frontend && npx vitest run src/stores/__tests__/baguwen.test.ts`
Expected: PASS(3 个用例)

- [ ] **Step 5: 替换 BaguwenView.vue 占位为真实页面**

```vue
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NInput, NModal, NPopconfirm, NPagination, NSelect, NTag } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useBaguwenStore } from '@/stores/baguwen'
import { listKbs } from '@/api/kb'
import { message } from '@/utils/feedback'
import type { ImportTaskStatus, Kb } from '@/types'

const baguwenStore = useBaguwenStore()
const router = useRouter()

const urlText = ref('')
const importing = ref(false)
const searchKeyword = ref('')

const favoriteModal = ref(false)
const favoriteArticleId = ref<number | null>(null)
const favoriteKbId = ref<number | null>(null)
const kbs = ref<Kb[]>([])
const favoriting = ref(false)

const taskStatusMeta: Record<ImportTaskStatus, { type: 'default' | 'info' | 'success' | 'error'; label: string }> = {
  PENDING: { type: 'default', label: '排队中' },
  PROCESSING: { type: 'info', label: '爬取中' },
  SUCCESS: { type: 'success', label: '成功' },
  FAILED: { type: 'error', label: '失败' },
}

const pendingTaskCount = computed(
  () => baguwenStore.tasks.filter((t) => t.status === 'PENDING' || t.status === 'PROCESSING').length,
)

onMounted(async () => {
  await baguwenStore.loadArticles()
  await baguwenStore.loadTags()
  await baguwenStore.refreshTasks()
  if (pendingTaskCount.value) baguwenStore.startPolling()
})

onBeforeUnmount(() => baguwenStore.stopPolling())

async function onImport() {
  const urls = urlText.value
    .split('\n')
    .map((u) => u.trim())
    .filter((u) => u && /^https?:\/\//.test(u))
  if (!urls.length) {
    message.warning('请输入至少一个 http(s) 链接,每行一个')
    return
  }
  importing.value = true
  try {
    await baguwenStore.importUrls(urls)
    urlText.value = ''
    message.success(`已提交 ${urls.length} 个导入任务`)
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    importing.value = false
  }
}

function onSearch() {
  baguwenStore.filter.keyword = searchKeyword.value.trim()
  baguwenStore.filter.page = 1
  void baguwenStore.loadArticles()
}

function onTagFilter(tag: string) {
  baguwenStore.filter.tag = baguwenStore.filter.tag === tag ? '' : tag
  baguwenStore.filter.page = 1
  void baguwenStore.loadArticles()
}

function onPageChange(page: number) {
  baguwenStore.filter.page = page
  void baguwenStore.loadArticles()
}

async function openFavorite(id: number) {
  favoriteArticleId.value = id
  favoriteKbId.value = null
  favoriteModal.value = true
  if (!kbs.value.length) {
    try {
      kbs.value = await listKbs()
    } catch {
      kbs.value = []
    }
  }
}

async function onFavoriteConfirm() {
  if (!favoriteArticleId.value || !favoriteKbId.value) {
    message.warning('请选择知识库')
    return
  }
  favoriting.value = true
  try {
    const result = await baguwenStore.favorite(favoriteArticleId.value, favoriteKbId.value)
    favoriteModal.value = false
    message.success(`已收藏,写入 ${result.embedded} 个向量片段`)
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    favoriting.value = false
  }
}
</script>

<template>
  <div class="space-y-4">
    <!-- 导入区 -->
    <div class="glass-card space-y-2">
      <div class="text-sm text-gray-300">导入文章(每行一个 URL,后台异步爬取 + AI 结构化)</div>
      <n-input
        v-model:value="urlText"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 5 }"
        placeholder="https://example.com/article&#10;https://example.com/another"
      />
      <div class="flex justify-end">
        <n-button type="primary" :loading="importing" @click="onImport">
          <template #icon><Icon icon="mdi:download-box-outline" class="h-4 w-4" /></template>
          开始导入
        </n-button>
      </div>
    </div>

    <!-- 任务进度 -->
    <div v-if="baguwenStore.tasks.length" class="glass-card space-y-1.5">
      <div class="text-xs text-gray-500">
        导入任务(处理中 {{ pendingTaskCount }} 个,每 2 秒刷新)
      </div>
      <div v-for="t in baguwenStore.tasks" :key="t.id" class="flex items-center justify-between gap-2 py-1">
        <span class="min-w-0 flex-1 truncate text-xs text-gray-400">{{ t.url }}</span>
        <div class="flex shrink-0 items-center gap-2">
          <n-tag size="small" :type="taskStatusMeta[t.status].type" :bordered="false">
            {{ taskStatusMeta[t.status].label }}
          </n-tag>
          <n-button v-if="t.status === 'FAILED'" size="tiny" quaternary @click="baguwenStore.retry(t.id)">
            重试
          </n-button>
        </div>
      </div>
    </div>

    <!-- 筛选 + 列表 -->
    <div class="flex flex-wrap items-center gap-2">
      <n-input
        v-model:value="searchKeyword"
        placeholder="搜索标题/正文/问答…"
        class="max-w-xs"
        clearable
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <n-button quaternary @click="onSearch">搜索</n-button>
      <div class="flex flex-wrap gap-1.5">
        <n-tag
          v-for="t in baguwenStore.tags"
          :key="t.tag"
          size="small"
          :bordered="false"
          :type="baguwenStore.filter.tag === t.tag ? 'warning' : 'default'"
          class="cursor-pointer"
          @click="onTagFilter(t.tag)"
        >
          {{ t.tag }} {{ t.count }}
        </n-tag>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
      <div
        v-for="a in baguwenStore.articles"
        :key="a.id"
        class="glass-card glass-card--hover flex cursor-pointer flex-col gap-2"
        @click="router.push({ name: 'baguwen-detail', params: { id: a.id } })"
      >
        <div class="flex items-start justify-between gap-2">
          <span class="line-clamp-2 text-sm font-medium text-gray-200">{{ a.title }}</span>
          <div class="flex shrink-0 gap-1" @click.stop>
            <span
              class="cursor-pointer rounded p-1 text-gray-500 transition-colors hover:bg-white/5 hover:text-cyan-400"
              title="收藏到知识库"
              @click="openFavorite(a.id)"
            >
              <Icon icon="mdi:bookmark-plus-outline" class="h-4 w-4" />
            </span>
            <n-popconfirm @positive-click="baguwenStore.removeArticle(a.id)">
              <template #trigger>
                <span class="cursor-pointer rounded p-1 text-gray-500 transition-colors hover:bg-white/5 hover:text-red-400">
                  <Icon icon="mdi:trash-can-outline" class="h-4 w-4" />
                </span>
              </template>
              删除该文章及其问答对?
            </n-popconfirm>
          </div>
        </div>
        <div class="flex flex-wrap gap-1">
          <n-tag v-for="t in a.tags" :key="t" size="tiny" :bordered="false" type="warning">
            {{ t }}
          </n-tag>
        </div>
        <div class="mt-auto text-xs text-gray-600">{{ new Date(a.createdAt).toLocaleDateString() }}</div>
      </div>
    </div>

    <div v-if="!baguwenStore.articles.length" class="py-12 text-center text-sm text-gray-600">
      暂无文章,在上方输入 URL 开始导入
    </div>

    <div v-if="baguwenStore.total > baguwenStore.filter.size" class="flex justify-center">
      <n-pagination
        :page="baguwenStore.filter.page"
        :page-size="baguwenStore.filter.size"
        :item-count="baguwenStore.total"
        @update:page="onPageChange"
      />
    </div>
  </div>

  <!-- 收藏弹窗 -->
  <n-modal
    v-model:show="favoriteModal"
    preset="dialog"
    title="收藏到知识库"
    positive-text="收藏"
    negative-text="取消"
    :positive-button-props="{ loading: favoriting }"
    @positive-click="onFavoriteConfirm"
  >
    <div class="py-2">
      <n-select
        v-model:value="favoriteKbId"
        placeholder="选择知识库"
        :options="kbs.map((k) => ({ label: k.name, value: k.id }))"
      />
    </div>
  </n-modal>
</template>
```

- [ ] **Step 6: 替换 BaguwenDetailView.vue 占位为详情页**

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NModal, NSelect, NTag } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { articleDetail, favoriteArticle } from '@/api/baguwen'
import { listKbs } from '@/api/kb'
import { message } from '@/utils/feedback'
import type { BaguwenDetail, Kb } from '@/types'

const route = useRoute()
const router = useRouter()

const detail = ref<BaguwenDetail | null>(null)
const loading = ref(true)

const favoriteModal = ref(false)
const favoriteKbId = ref<number | null>(null)
const kbs = ref<Kb[]>([])

const difficultyMeta: Record<string, { type: 'success' | 'warning' | 'error'; label: string }> = {
  简单: { type: 'success', label: '简单' },
  中等: { type: 'warning', label: '中等' },
  困难: { type: 'error', label: '困难' },
}

onMounted(async () => {
  try {
    detail.value = await articleDetail(Number(route.params.id))
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    loading.value = false
  }
})

async function openFavorite() {
  favoriteModal.value = true
  if (!kbs.value.length) {
    try {
      kbs.value = await listKbs()
    } catch {
      kbs.value = []
    }
  }
}

async function onFavoriteConfirm() {
  if (!detail.value || !favoriteKbId.value) {
    message.warning('请选择知识库')
    return
  }
  try {
    const result = await favoriteArticle(detail.value.id, favoriteKbId.value)
    favoriteModal.value = false
    message.success(`已收藏,写入 ${result.embedded} 个向量片段`)
  } catch (e) {
    message.error((e as Error).message)
  }
}
</script>

<template>
  <div v-if="loading" class="py-16 text-center text-sm text-gray-600">加载中…</div>
  <div v-else-if="detail" class="mx-auto max-w-3xl space-y-4">
    <div class="flex items-start justify-between gap-3">
      <h1 class="text-lg font-semibold text-gray-100">{{ detail.title }}</h1>
      <div class="flex shrink-0 items-center gap-2">
        <n-button quaternary size="small" @click="router.back()">
          <template #icon><Icon icon="mdi:arrow-left" class="h-4 w-4" /></template>
          返回
        </n-button>
        <n-button size="small" @click="openFavorite">
          <template #icon><Icon icon="mdi:bookmark-plus-outline" class="h-4 w-4" /></template>
          收藏
        </n-button>
      </div>
    </div>
    <div class="flex flex-wrap items-center gap-2">
      <n-tag v-for="t in detail.tags" :key="t" size="small" :bordered="false" type="warning">
        {{ t }}
      </n-tag>
      <a
        v-if="detail.url"
        :href="detail.url"
        target="_blank"
        rel="noopener"
        class="text-xs text-cyan-400 hover:underline"
      >
        原文链接 ↗
      </a>
    </div>
    <div class="space-y-3">
      <div v-for="(qa, i) in detail.qaPairs" :key="qa.id" class="glass-card !p-4">
        <div class="flex items-start gap-2">
          <span class="mt-0.5 shrink-0 text-sm text-gray-500">Q{{ i + 1 }}</span>
          <div class="flex-1">
            <div class="flex items-center justify-between gap-2">
              <span class="text-sm font-medium text-gray-100">{{ qa.question }}</span>
              <n-tag
                v-if="difficultyMeta[qa.difficulty]"
                size="tiny"
                :type="difficultyMeta[qa.difficulty].type"
                :bordered="false"
              >
                {{ difficultyMeta[qa.difficulty].label }}
              </n-tag>
            </div>
            <div class="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-gray-400">
              {{ qa.answer }}
            </div>
          </div>
        </div>
      </div>
      <div v-if="!detail.qaPairs.length" class="py-8 text-center text-sm text-gray-600">
        本文未提取到问答对
      </div>
    </div>
  </div>
  <div v-else class="py-16 text-center text-sm text-gray-600">文章不存在</div>

  <n-modal
    v-model:show="favoriteModal"
    preset="dialog"
    title="收藏到知识库"
    positive-text="收藏"
    negative-text="取消"
    @positive-click="onFavoriteConfirm"
  >
    <div class="py-2">
      <n-select
        v-model:value="favoriteKbId"
        placeholder="选择知识库"
        :options="kbs.map((k) => ({ label: k.name, value: k.id }))"
      />
    </div>
  </n-modal>
</template>
```

- [ ] **Step 7: 全量验证与提交**

Run: `cd /d/neural-base/frontend && npx vitest run && npm run typecheck && npm run build`
Expected: 全部通过

```bash
cd /d/neural-base && git add frontend/src/stores/baguwen.ts frontend/src/stores/__tests__/baguwen.test.ts frontend/src/views/BaguwenView.vue frontend/src/views/BaguwenDetailView.vue
git commit -m "feat: baguwen pages (import tasks, filter list, qa detail, favorite)"
```

---

### Task 13: Dashboard + 日报占位页

**Files:**
- Create: `frontend/src/components/ModuleCard.vue`
- Create: `frontend/src/views/DashboardView.vue`(替换占位)
- Create: `frontend/src/views/DailyNewsView.vue`(替换占位)

**Interfaces:**
- Consumes: `conversationApi.list`、`documentApi.listDocuments`(Task 4)、`GlassCard`
- Produces: `ModuleCard.vue` props `{ title: string; desc: string; icon: string; accent: 'cyan'|'violet'|'amber'|'emerald'; to: string }`

- [ ] **Step 1: 创建 ModuleCard.vue**

```vue
<script setup lang="ts">
import { Icon } from '@iconify/vue'

withDefaults(
  defineProps<{
    title: string
    desc: string
    icon: string
    accent: 'cyan' | 'violet' | 'amber' | 'emerald'
    to: string
  }>(),
  { accent: 'cyan' },
)
</script>

<template>
  <RouterLink :to="to" class="block">
    <div class="glass-card glass-card--hover h-full" :class="`glass-card--${accent}`">
      <div class="mb-3 flex h-10 w-10 items-center justify-center rounded-xl border border-white/10 bg-white/[0.03]">
        <Icon :icon="icon" class="h-5 w-5" :style="{ color: `var(--nb-accent)` }" />
      </div>
      <div class="text-sm font-medium text-gray-100">{{ title }}</div>
      <div class="mt-1 text-xs leading-relaxed text-gray-500">{{ desc }}</div>
    </div>
  </RouterLink>
</template>
```

> 注:GlassCard 的 `--nb-accent` 变量定义在 GlassCard 自己的 scoped style 中,跨组件继承 CSS 变量在 DOM 层生效(scoped 样式不隔离变量继承),ModuleCard 内直接使用即可。

- [ ] **Step 2: 替换 DashboardView.vue 占位**

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import * as conversationApi from '@/api/conversation'
import { listDocuments } from '@/api/document'
import ModuleCard from '@/components/ModuleCard.vue'
import type { ConversationView, DocumentItem } from '@/types'

const conversations = ref<ConversationView[]>([])
const documents = ref<DocumentItem[]>([])

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
})
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-lg font-semibold text-gray-100">知识中枢</h1>
      <p class="mt-1 text-xs text-gray-500">上传文档构建知识库,与 AI 对话,自动整理八股文</p>
    </div>

    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <ModuleCard
        title="知识库"
        desc="多格式上传,向量检索,语义搜索"
        icon="mdi:bookshelf"
        accent="cyan"
        to="/kb"
      />
      <ModuleCard
        title="AI 对话"
        desc="流式输出,普通 / 知识库双模式"
        icon="mdi:message-text-outline"
        accent="violet"
        to="/chat"
      />
      <ModuleCard
        title="八股文库"
        desc="URL 导入,AI 结构化问答对"
        icon="mdi:file-document-multiple-outline"
        accent="amber"
        to="/baguwen"
      />
      <ModuleCard
        title="AI 日报"
        desc="定时爬取前沿资讯,自动汇总"
        icon="mdi:newspaper-variant-outline"
        accent="emerald"
        to="/news"
      />
    </div>

    <div class="grid grid-cols-1 gap-3 lg:grid-cols-2">
      <div class="glass-card">
        <div class="mb-3 flex items-center justify-between">
          <span class="text-sm text-gray-300">最近会话</span>
          <RouterLink to="/chat" class="text-xs text-cyan-400 hover:underline">全部 →</RouterLink>
        </div>
        <div class="space-y-1.5">
          <div v-for="c in conversations" :key="c.id" class="rounded-lg bg-white/[0.02] px-3 py-2">
            <div class="truncate text-sm text-gray-200">{{ c.title }}</div>
            <div v-if="c.lastMessageContent" class="mt-0.5 truncate text-xs text-gray-500">
              {{ c.lastMessageContent }}
            </div>
          </div>
          <div v-if="!conversations.length" class="py-6 text-center text-xs text-gray-600">
            暂无会话
          </div>
        </div>
      </div>

      <div class="glass-card">
        <div class="mb-3 flex items-center justify-between">
          <span class="text-sm text-gray-300">最近文档</span>
          <RouterLink to="/kb" class="text-xs text-cyan-400 hover:underline">全部 →</RouterLink>
        </div>
        <div class="space-y-1.5">
          <div v-for="d in documents" :key="d.id" class="flex items-center justify-between gap-2 rounded-lg bg-white/[0.02] px-3 py-2">
            <span class="truncate text-sm text-gray-200">{{ d.title }}</span>
            <span class="shrink-0 text-xs text-gray-600">{{ d.status }}</span>
          </div>
          <div v-if="!documents.length" class="py-6 text-center text-xs text-gray-600">
            暂无文档
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 3: 替换 DailyNewsView.vue 占位**

```vue
<script setup lang="ts">
import { Icon } from '@iconify/vue'
</script>

<template>
  <div class="glass-card flex min-h-[40vh] flex-col items-center justify-center gap-4 text-center">
    <div class="flex h-14 w-14 items-center justify-center rounded-2xl border border-emerald-400/20 bg-emerald-500/10">
      <Icon icon="mdi:newspaper-variant-outline" class="h-7 w-7 text-emerald-400" />
    </div>
    <div>
      <h1 class="text-base font-medium text-gray-100">AI 技术日报</h1>
      <p class="mt-2 max-w-sm text-xs leading-relaxed text-gray-500">
        每日定时爬取 GitHub Trending、Hacker News、机器之心等前沿资讯,由 DeepSeek 自动分类汇总。
      </p>
      <p class="mt-3 text-xs text-gray-600">后端开发中(Phase 5),就绪后此处将展示时间线与一键收藏。</p>
    </div>
  </div>
</template>
```

- [ ] **Step 4: 删除 PlaceholderView 并验证构建与提交**

Task 2 创建的占位视图已全部被真实页面替换,`PlaceholderView.vue` 不再被引用,显式删除:

Run: `cd /d/neural-base/frontend && npm run typecheck && npm run build && npx vitest run`
Expected: 全部通过(确认无组件引用 PlaceholderView,typecheck 会兜底)

```bash
cd /d/neural-base && git rm frontend/src/views/PlaceholderView.vue && git add frontend/src/components/ModuleCard.vue frontend/src/views/DashboardView.vue frontend/src/views/DailyNewsView.vue
git commit -m "feat: dashboard module cards, recent lists, daily news placeholder"
```

---

### Task 14: 全量验证 + 真实冒烟 + PROGRESS.md 更新

**Files:**
- Modify: `docs/PROGRESS.md`(前端状态、Roadmap Phase 6、模块核对表、技术架构表)

**Interfaces:** 无(收尾任务)

- [ ] **Step 1: 后端与前端全量测试**

Run:
```bash
cd /d/neural-base/backend && mvn test -q
cd /d/neural-base/frontend && npx vitest run && npm run typecheck && npm run build
```
Expected: 后端 74 全绿;前端 28 个测试全绿(GlassCard 2、user 3、sse 7、chat 4、MarkdownRenderer 2、ChatMessage 3、document 4、baguwen 3)、typecheck 无输出、build 成功

- [ ] **Step 2: 全链路真实冒烟(前后端同跑)**

后端:`cd /d/neural-base/backend && export DB_PASSWORD='Huangwei990758' && mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`
前端:`cd /d/neural-base/frontend && npm run dev`

浏览器逐项验证:
1. 登录 → Dashboard 显示四模块卡、最近会话/文档列表
2. 知识库:新建「前端冒烟」KB(选紫色)→ 上传一个 md 文件 → 状态 PENDING→PROCESSING→READY(轮询自动停止)→ 语义搜索命中片段 → 删除文档(向量+文件删除)
3. Chat:知识库模式选「前端冒烟」→ 提问命中引用来源;普通模式正常流式;停止生成
4. 八股文:导入 `https://www.ruanyifeng.com/blog/2026/08/weekly-issue-287.html`(阮一峰周刊,SSR 站点 Jsoup 直达,不依赖 Playwright)→ 任务 SUCCESS → 列表出现 → 详情问答对 + 难度徽标 → 收藏进「前端冒烟」KB → 去 Chat 知识库模式验证能检索到问答对
5. 移动视口(<768px):底部 Tab 可导航,聊天会话抽屉可用
6. 日报页显示占位;退出登录 → cookie 清除

- [ ] **Step 3: 修复冒烟问题并回归(如有)**

Run: `cd /d/neural-base/frontend && npx vitest run && npm run typecheck && npm run build && cd /d/neural-base/backend && mvn test -q`
Expected: 全绿后提交修复:`fix: <问题简述>`

- [ ] **Step 4: 更新 docs/PROGRESS.md**

改动如下(注意与 spec 一致,不夸大):

1. **Roadmap 表** Phase 6 行:`| **Phase 6** | UI 精细化打磨、动画、响应式 | ⏸ | 主要页面完成(登录/知识库/对话/八股文/日报占位);动画打磨与亮色模式未做 |`
2. **二、模块核对表**:
   - 知识库:多格式上传行说明改 `后端…✅;前端拖拽上传 ✅`(状态仍 ⏸,等文档阅读器);文档阅读器行保持 ❌
   - AI 对话:停止生成改 ✅(前端停止按钮 + AbortController);Markdown 渲染/代码操作改 ✅(渲染+代码复制;插入编辑器仍无,说明注明);会话管理行说明补 `前端列表/新建/删除 ✅`(重命名/收藏仍延后)
   - 八股文库:阅读模式/前端行改 ✅(列表/筛选/详情/收藏前端完成;原文渲染仍是纯文本问答对)
3. **三、技术架构表**:安全行说明补 `;cookie 认证(login Set-Cookie + logout 端点 + filter 兜底,header 兼容保留)`;新增一行 `| 前端 | 设计文档 Vue 3 + Vite 5 | ✅ Vue 3.5 + Vite 7 + Pinia 3 + Naive UI 2 + Tailwind 3.4 | TipTap/ECharts/GSAP 裁剪(见 spec) |`
4. **四、测试与冒烟**:自动化测试行补 `前端 Vitest 28(GlassCard 2、user 3、sse 7、chat 4、MarkdownRenderer 2、ChatMessage 3、document 4、baguwen 3)`;冒烟记录追加 `Phase 6(2026-08-13):登录 cookie 落盘 → 上传/轮询/搜索/删除文档 → RAG 引用流式 → 八股文导入收藏 → 移动端底部 Tab ✅`
5. **六、前端状态**:整段替换为完成说明,列出已完成页面与未做项(亮色模式、动画打磨、文档阅读器、日报真功能)

- [ ] **Step 5: 提交**

```bash
cd /d/neural-base && git add docs/PROGRESS.md && git commit -m "docs: phase6 frontend completion, progress update"
```

**迭代 2 / 全计划完成标准**:Step 1–2 全部通过 + PROGRESS.md 已更新。最后运行一次全量验证并汇报。

<!-- APPEND -->






