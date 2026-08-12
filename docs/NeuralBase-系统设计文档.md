# NeuralBase — 个人 AI 知识中枢系统

> 基于 JDK 21 + Spring AI + DeepSeek + SiliconFlow(Qwen3-Embedding-8B) + pgvector + Vue 3 的智能知识管理平台

---

## 📌 项目概述

NeuralBase 是一个面向技术人的个人 AI 知识库系统，集 **知识库管理（RAG）**、**AI 智能对话**、**八股文自动爬取整理**、**AI 技术日报自动汇总** 四大核心能力于一体。系统采用深色玻璃拟态（Glassmorphism）设计风格，打造沉浸式的高级阅读与交互体验。

### 核心能力

| 模块 | 功能描述 |
|------|---------|
| 📚 知识库 | 支持多格式文档上传，自动切片 Embedding，基于向量检索的 RAG 问答 |
| 💬 AI 对话 | 对接 DeepSeek，支持知识库模式与普通模式，流式输出 |
| 📖 八股文库 | 输入 URL 自动爬取、AI 结构化提取问答对，生成面试题库 |
| 📰 AI 日报 | 每日定时爬取 AI 前沿资讯，AI 自动分类汇总生成技术日报 |

---

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                      前端层 (Vue 3 + Vite)                   │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐           │
│  │ 首页Dashboard │ │ 知识库 │ │ AI聊天 │ │ 八股文 │ │ AI日报 │ │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘           │
└────────────────────────┬────────────────────────────────────┘
                         │ HTTPS / WebSocket
┌────────────────────────▼────────────────────────────────────┐
│                   网关层 (Nginx / Spring Gateway)            │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│              应用层 (Spring Boot 3.x + JDK 21)               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ Spring AI   │  │ 爬虫引擎    │  │ 定时任务调度        │ │
│  │ + DeepSeek  │  │ (Playwright)│  │ (Quartz/Scheduler)  │ │
│  │ RAG 模块    │  │ 八股文解析  │  │ AI 日报生成         │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└────────────────────────┬────────────────────────────────────┘
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│   MySQL     │  │ PostgreSQL  │  │    Redis    │
│ 结构化数据   │  │ + pgvector  │  │   缓存/会话  │
│ 用户/文档元数据│ │ 向量存储    │  │  限流/队列   │
└─────────────┘  └─────────────┘  └─────────────┘
```

---

## 🔧 后端技术栈

| 层级 | 技术选型 | 版本 | 说明 |
|------|---------|------|------|
| **JDK** | OpenJDK | 21 | Virtual Threads 处理高并发爬虫与 AI 流式响应 |
| **框架** | Spring Boot | 3.2+ | 原生支持 JDK 21，自动配置 |
| **AI 框架** | Spring AI | 1.0+ | 统一抽象层，支持多模型 Provider |
| **对话模型** | DeepSeek | V4-Flash / V4-Pro | 通用对话与深度推理 |
| **Embedding 模型** | Qwen3-Embedding-8B (via SiliconFlow) | - | MTEB 多语言排行榜第一，4096 维，32K 上下文 |
| **关系数据库** | MySQL | 8.0 | 用户、文档元数据、八股文结构化存储 |
| **向量数据库** | PostgreSQL + pgvector | 16 | 文档切片后的向量存储，支持相似度检索 |
| **ORM** | MyBatis-Plus | 3.5+ | 复杂查询与分页 |
| **爬虫引擎** | Playwright + Jsoup | - | Playwright 处理动态渲染，Jsoup 解析静态 HTML |
| **文档处理** | Apache Tika + PDFBox | - | 提取 PDF/Word/Markdown 文本内容 |
| **缓存** | Redis | 7.x | 会话、热点数据、爬虫去重布隆过滤器 |
| **消息队列** | Redis Stream | 7.x | 异步处理文档 Embedding、爬虫任务 |
| **任务调度** | Quartz | 2.3+ | 定时爬取 AI 日报源 |
| **安全** | Spring Security + JWT | - | 登录认证与权限控制 |
| **API 文档** | SpringDoc OpenAPI | 2.x | 自动生成 Swagger 文档 |

### RAG 核心流程

```
用户上传文档
    ↓
Apache Tika 提取文本
    ↓
文本切片（Chunk，按语义/段落）
    ↓
SiliconFlow Embedding API (Qwen3-Embedding-8B) → 生成 1024/1536 维向量
    ↓
存入 PostgreSQL pgvector
    ↓
用户提问
    ↓
向量相似度检索 Top-K
    ↓
构建 Prompt（上下文注入）
    ↓
调用 DeepSeek Chat API
    ↓
SSE 流式返回答案
```

---

## 🎨 前端技术栈

| 技术 | 选型 | 版本 | 用途 |
|------|------|------|------|
| **框架** | Vue | 3.4+ | 响应式、Composition API |
| **构建工具** | Vite | 5.x | 极速冷启动、HMR |
| **语言** | TypeScript | 5.x | 类型安全 |
| **状态管理** | Pinia | 2.x | 全局状态管理 |
| **路由** | Vue Router | 4.x | 前端路由 |
| **UI 组件库** | Naive UI | 2.x | 现代风格，暗黑主题精致 |
| **样式框架** | Tailwind CSS | 3.4+ | 原子化 CSS |
| **预处理器** | SCSS | - | 自定义主题变量与嵌套样式 |
| **图标** | Iconify | - | 按需加载，10万+ 图标 |
| **富文本编辑** | TipTap | 2.x | 文档编辑与渲染 |
| **Markdown** | Markdown-it + highlight.js | - | 代码高亮与渲染 |
| **图表** | ECharts | 5.x | 数据可视化 |
| **动画** | GSAP | 3.x | 页面切换、微交互动效 |
| **HTTP 客户端** | Axios | 1.6+ | API 请求封装 |
| **流式通信** | EventSource | - | AI 流式聊天接收（SSE） |

---


## 📦 四大功能模块

### 1️⃣ 知识库（RAG）

| 功能点 | 说明 |
|--------|------|
| **多格式上传** | 支持 PDF、Word（doc/docx）、Markdown、TXT，拖拽上传 + 进度条 |
| **异步解析** | 后台队列处理：Tika 提取 → 语义切片 → 批量 Embedding → 入库 |
| **语义搜索** | 全局搜索框输入自然语言，pgvector 向量相似度检索 |
| **文档阅读器** | 左侧文件树导航，右侧渲染阅读，支持大纲跳转 |
| **RAG 问答** | 选中知识库模式后，AI 基于检索到的文档片段回答问题 |

**切片策略：**
- 按段落分割（\n\n）
- 重叠窗口（overlap 200 tokens）保证上下文连贯
- 单 chunk 控制在 512-1024 tokens

---

### 2️⃣ AI 对话

| 功能点 | 说明 |
|--------|------|
| **双模式切换** | 普通模式（纯 DeepSeek）/ 知识库模式（RAG 增强） |
| **模型切换** | 支持 DeepSeek-V4-Flash（快速通用）与 DeepSeek-V4-Pro（深度推理）切换 |
| **流式输出** | SSE 实时推送，打字机效果，支持停止生成 |
| **Markdown 渲染** | 代码块语法高亮、表格、LaTeX 公式 |
| **代码操作** | 一键复制、插入到编辑器 |
| **会话管理** | 左侧历史会话列表，支持重命名、删除、收藏 |
| **反馈机制** | 点赞/点踩，用于后续模型优化 |

**Prompt 工程（知识库模式）：**
```
你是 NeuralBase 的 AI 助手。请基于以下检索到的文档片段回答用户问题。
如果文档中没有相关信息，请明确告知用户。

--- 检索到的文档片段 ---
{context}

--- 用户问题 ---
{question}
```

---

### 3️⃣ 八股文爬取整理

| 功能点 | 说明 |
|--------|------|
| **URL 输入** | 用户粘贴目标文章链接 |
| **智能爬取** | Playwright 渲染页面 → 提取正文（去除广告、导航、评论区） |
| **AI 结构化** | 调用 DeepSeek 提取「问题-答案」对，生成结构化 JSON |
| **分类标签** | 自动识别技术领域（Java/Spring/DB/算法/架构等） |
| **阅读模式** | 卡片式展示，支持标签筛选、全文搜索、收藏到知识库 |
| **批量导入** | 支持导入多个链接，队列异步处理 |

**AI 结构化 Prompt：**
```
请将以下技术文章整理为面试题格式，提取其中的问题和答案。
要求：
1. 每个问题简洁明了
2. 答案要点清晰，分条列出
3. 标注难度等级（简单/中等/困难）
4. 输出 JSON 数组格式

文章内容：
{article_content}
```

---

### 4️⃣ AI 技术日报

| 功能点 | 说明 |
|--------|------|
| **定时爬取** | 每日凌晨 Quartz 触发，自动爬取多个技术源 |
| **信息源** | GitHub Trending、Hacker News、Paper with Code、机器之心、InfoQ 等 |
| **AI 汇总** | DeepSeek 读取多篇文章 → 按类别（大模型/框架/工具/论文）生成摘要 |
| **时间线展示** | 卡片时间线布局，分类彩色标签 |
| **一键收藏** | 感兴趣的文章一键存入知识库，自动 Embedding |
| **已读标记** | 阅读后自动标记，支持批量已读 |

**爬取源配置：**
```yaml
crawler:
  sources:
    - name: "GitHub Trending"
      url: "https://github.com/trending"
      type: "html"
      selector: "article.Box-row"
    - name: "机器之心"
      url: "https://www.jiqizhixin.com/"
      type: "rss"
      category: "INDUSTRY"
    - name: "Paper with Code"
      url: "https://paperswithcode.com/"
      type: "html"
      category: "PAPER"
```

---

## 🎨 UI/UX 设计规范

### 设计关键词

> **深色玻璃拟态** · **霓虹渐变** · **微交互动效** · **模块化卡片**

### 配色方案

| 用途 | 色值 | 说明 |
|------|------|------|
| 背景主色 | `#0a0a0f` | 深邃黑，降低视觉疲劳 |
| 卡片底色 | `rgba(255,255,255,0.02)` | 半透明玻璃质感 |
| 卡片悬浮 | `rgba(255,255,255,0.05)` | 轻微提亮 |
| 边框 | `rgba(255,255,255,0.05)` | 极细发光边 |
| 主强调色 | `#22d3ee` (Cyan-400) | 科技青，知识库模块 |
| 次强调色 | `#a78bfa` (Purple-400) | 智能紫，AI 聊天模块 |
| 成功/日报 | `#34d399` (Emerald-400) | AI 日报模块 |
| 警告/八股文 | `#fbbf24` (Amber-400) | 八股文模块 |
| 文字主色 | `#f3f4f6` (Gray-100) | 主标题 |
| 文字次色 | `#9ca3af` (Gray-400) | 描述文字 |
| 文字弱化 | `#4b5563` (Gray-600) | 时间、元信息 |

### 页面布局

```
┌────────────────────────────────────────┐
│  固定顶部导航 (Glassmorphism + Blur)   │  h-16, backdrop-blur-xl
├──────────┬─────────────────────────────┤
│          │                             │
│  侧边栏   │      主内容区 (自适应)        │
│ (w-64,   │      - 模块卡片网格           │
│  可折叠)  │      - 聊天对话区             │
│          │      - 文档阅读器             │
│          │                             │
└──────────┴─────────────────────────────┘
```

### 交互动效规范

| 场景 | 动效 | 参数 |
|------|------|------|
| 卡片悬浮 | 边框渐变发光 + 上移 | `border-color` 过渡 300ms，`translateY(-2px)`，`box-shadow` 扩散 |
| 页面切换 | 淡入 + 轻微上滑 | Vue `<Transition>`，`opacity` 0→1，`translateY(10px→0)`，300ms |
| 数据加载 | 骨架屏 → 内容渐显 | Skeleton 脉冲动画，内容 `opacity` 渐显 200ms |
| AI 打字 | 逐字出现 + 光标闪烁 | 每字 30ms，`cursor: blink` 1s 周期 |
| 文件上传 | 拖拽区域涟漪动画 | `scale` 脉冲，`border-color` 高亮 |
| 按钮点击 | 缩放反馈 | `scale(0.97)`，100ms |

### 响应式断点

| 断点 | 布局 |
|------|------|
| `≥1280px` (桌面端) | 侧边栏常驻，四列卡片网格 |
| `≥768px` (平板端) | 侧边栏可折叠，两列网格 |
| `<768px` (移动端) | 底部 Tab 导航，单列堆叠 |

---

## 📁 项目目录结构

```
neural-base/
├── 📁 neural-base-backend/          # 后端服务
│   ├── src/main/java/com/neuralbase/
│   │   ├── 📁 config/               # 配置类（CORS、Security、AI Client）
│   │   ├── 📁 controller/           # REST API 控制器
│   │   │   ├── DocumentController.java
│   │   │   ├── ChatController.java
│   │   │   ├── BaguwenController.java
│   │   │   └── DailyNewsController.java
│   │   ├── 📁 service/              # 业务逻辑层
│   │   │   ├── DocumentService.java
│   │   │   ├── RAGService.java
│   │   │   ├── ChatService.java
│   │   │   ├── CrawlerService.java
│   │   │   └── DailyNewsService.java
│   │   ├── 📁 repository/           # 数据访问层（MyBatis-Plus Mapper）
│   │   ├── 📁 domain/               # 实体、DTO、枚举
│   │   ├── 📁 ai/                   # Spring AI 配置与 RAG 核心逻辑
│   │   │   ├── EmbeddingConfig.java
│   │   │   ├── VectorStoreConfig.java
│   │   │   └── RAGPromptTemplate.java
│   │   ├── 📁 crawler/              # 爬虫引擎
│   │   │   ├── PlaywrightCrawler.java
│   │   │   ├── ContentExtractor.java
│   │   │   └── BaguwenParser.java
│   │   ├── 📁 scheduler/            # 定时任务
│   │   │   └── DailyNewsCrawlerJob.java
│   │   ├── 📁 infra/                # 基础设施（Redis、消息队列）
│   │   └── 📁 util/                 # 工具类
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   └── db/migration/            # Flyway 数据库迁移脚本
│   └── pom.xml
│
├── 📁 neural-base-frontend/         # 前端应用
│   ├── src/
│   │   ├── 📁 views/                # 页面级组件
│   │   │   ├── Dashboard.vue        # 首页仪表盘
│   │   │   ├── KnowledgeBase.vue    # 知识库
│   │   │   ├── Chat.vue             # AI 对话
│   │   │   ├── Baguwen.vue          # 八股文库
│   │   │   └── DailyNews.vue        # AI 日报
│   │   ├── 📁 components/           # 公共组件
│   │   │   ├── AppSidebar.vue
│   │   │   ├── AppHeader.vue
│   │   │   ├── GlassCard.vue        # 玻璃卡片
│   │   │   ├── MarkdownRenderer.vue
│   │   │   ├── ChatMessage.vue
│   │   │   └── FileUploader.vue
│   │   ├── 📁 stores/               # Pinia 状态管理
│   │   │   ├── user.ts
│   │   │   ├── chat.ts
│   │   │   └── document.ts
│   │   ├── 📁 api/                  # Axios 封装与 API 定义
│   │   │   ├── request.ts           # Axios 实例配置
│   │   │   ├── document.ts
│   │   │   ├── chat.ts
│   │   │   └── crawler.ts
│   │   ├── 📁 composables/          # Vue 组合式函数
│   │   │   ├── useSSE.ts            # SSE 流式处理
│   │   │   └── useDarkMode.ts       # 暗黑模式
│   │   ├── 📁 assets/               # 静态资源
│   │   │   ├── styles/
│   │   │   │   ├── variables.scss   # SCSS 变量
│   │   │   │   └── global.scss      # 全局样式
│   │   │   └── images/
│   │   ├── 📁 router/               # Vue Router 配置
│   │   │   └── index.ts
│   │   ├── App.vue
│   │   └── main.ts
│   ├── index.html
│   ├── vite.config.ts
│   ├── tailwind.config.js
│   ├── tsconfig.json
│   └── package.json
│
├── 📁 docker/                       # Docker 配置
│   ├── Dockerfile.backend
│   ├── Dockerfile.frontend
│   └── nginx.conf
│
├── docker-compose.yml               # 一键部署
├── README.md
└── .env.example                     # 环境变量模板
```

---

## 🐳 Docker 部署方案

### docker-compose.yml

```yaml
version: '3.8'

services:
  # 后端服务
  backend:
    build:
      context: ./neural-base-backend
      dockerfile: ../docker/Dockerfile.backend
    container_name: neuralbase-backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_AI_DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY}
      - SPRING_AI_SILICONFLOW_API_KEY=${SILICONFLOW_API_KEY}
      - SPRING_DATASOURCE_MYSQL_URL=jdbc:mysql://mysql:3306/neuralbase?useSSL=false&serverTimezone=Asia/Shanghai
      - SPRING_DATASOURCE_MYSQL_USERNAME=root
      - SPRING_DATASOURCE_MYSQL_PASSWORD=${MYSQL_ROOT_PASSWORD}
      - SPRING_DATASOURCE_PGSQL_URL=jdbc:postgresql://postgres:5432/neuralbase
      - SPRING_DATASOURCE_PGSQL_USERNAME=postgres
      - SPRING_DATASOURCE_PGSQL_PASSWORD=${POSTGRES_PASSWORD}
      - SPRING_REDIS_HOST=redis
      - SPRING_REDIS_PORT=6379
    depends_on:
      - mysql
      - postgres
      - redis
    networks:
      - neuralbase-network

  # 前端服务（Nginx）
  frontend:
    build:
      context: ./neural-base-frontend
      dockerfile: ../docker/Dockerfile.frontend
    container_name: neuralbase-frontend
    ports:
      - "80:80"
    depends_on:
      - backend
    networks:
      - neuralbase-network

  # MySQL
  mysql:
    image: mysql:8.0
    container_name: neuralbase-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: neuralbase
      MYSQL_CHARSET: utf8mb4
      MYSQL_COLLATION: utf8mb4_unicode_ci
    volumes:
      - mysql-data:/var/lib/mysql
    ports:
      - "3306:3306"
    networks:
      - neuralbase-network

  # PostgreSQL + pgvector
  postgres:
    image: ankane/pgvector:latest
    container_name: neuralbase-postgres
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: neuralbase
    volumes:
      - postgres-data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    networks:
      - neuralbase-network

  # Redis
  redis:
    image: redis:7-alpine
    container_name: neuralbase-redis
    volumes:
      - redis-data:/data
    ports:
      - "6379:6379"
    networks:
      - neuralbase-network

volumes:
  mysql-data:
  postgres-data:
  redis-data:

networks:
  neuralbase-network:
    driver: bridge
```

### .env.example

```bash
# DeepSeek API Key（官方平台 https://platform.deepseek.com/）
DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxxxxx

# SiliconFlow API Key（Embedding 服务 https://cloud.siliconflow.cn/）
SILICONFLOW_API_KEY=sk-xxxxxxxxxxxxxxxx

# MySQL
MYSQL_ROOT_PASSWORD=your_mysql_password

# PostgreSQL
POSTGRES_PASSWORD=your_postgres_password

# JWT Secret
JWT_SECRET=your_jwt_secret_key_here

# 文件上传路径
UPLOAD_PATH=/app/uploads
```

### 启动命令

```bash
# 1. 克隆项目
git clone https://github.com/yourname/neural-base.git
cd neural-base

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 填入你的 DeepSeek 和 SiliconFlow API Key

# 3. 一键启动
docker-compose up -d

# 4. 查看日志
docker-compose logs -f backend
```

---

## 🔑 关键配置示例

### Spring AI 多 Provider 配置（DeepSeek + SiliconFlow）

```yaml
spring:
  ai:
    # DeepSeek 官方 API —— 用于对话
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-v4-flash    # 通用对话模型
          temperature: 0.7
          max-tokens: 4096

    # SiliconFlow —— 用于 Embedding（OpenAI 兼容接口）
    openai:
      api-key: ${SILICONFLOW_API_KEY}
      base-url: https://api.siliconflow.cn/v1
      embedding:
        options:
          model: Qwen/Qwen3-Embedding-8B
          dimensions: 1536            # 可选: 64,128,256,512,768,1024,1536,2048,2560,4096
```

> **说明**：SiliconFlow 的 Embedding API 完全兼容 OpenAI `/v1/embeddings` 接口，因此 Spring AI 中直接使用 `OpenAiEmbeddingModel` 即可，只需将 `base-url` 指向 `https://api.siliconflow.cn/v1`。

### pgvector VectorStore 配置

```java
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, 
                                    EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(1536)           // 与 SiliconFlow dimensions 参数一致
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .initializeSchema(true)
            .build();
    }
}
```

### DeepSeek Chat Client Bean

```java
@Configuration
public class AIConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultSystem("你是 NeuralBase 的 AI 助手，擅长技术问答和知识整理。")
            .build();
    }

    /**
     * SiliconFlow Embedding —— 通过 OpenAI 兼容接口
     */
    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.base-url}") String baseUrl) {
        var openAiApi = new OpenAiApi(baseUrl, apiKey);
        var embeddingOptions = OpenAiEmbeddingOptions.builder()
            .withModel("Qwen/Qwen3-Embedding-8B")
            .withDimensions(1536)
            .build();
        return new OpenAiEmbeddingModel(openAiApi, embeddingOptions);
    }
}
```

### Maven 依赖（pom.xml）

```xml
<dependencies>
    <!-- Spring Boot Starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring AI DeepSeek（对话） -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-deepseek-spring-boot-starter</artifactId>
    </dependency>

    <!-- Spring AI OpenAI（SiliconFlow Embedding 兼容层） -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    </dependency>

    <!-- Spring AI pgvector -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
    </dependency>

    <!-- 其他依赖省略... -->
</dependencies>
```

---

## 📋 开发 Roadmap

| 阶段 | 目标 | 预计周期 |
|------|------|---------|
| **Phase 1** | 项目脚手架搭建、数据库设计、基础 CRUD | 1 周 |
| **Phase 2** | 知识库上传 + SiliconFlow Embedding + pgvector 检索 | 1 周 |
| **Phase 3** | AI 聊天（SSE 流式 + 知识库 RAG 模式 + DeepSeek 推理） | 1 周 |
| **Phase 4** | 八股文爬取 + DeepSeek 结构化整理 | 1 周 |
| **Phase 5** | AI 日报定时爬取 + DeepSeek 汇总生成 | 3 天 |
| **Phase 6** | UI 精细化打磨、动画、响应式适配 | 3 天 |
| **Phase 7** | Docker 本地部署、性能优化、压测 | 3 天 |
| **Phase 8** | 生成Docker部署文档支持任何地方部署 | 3 天 |

---

## 📄 License

MIT License © 2026 NeuralBase

---

> 💡 **提示**：本文档为系统设计蓝图，实际开发中可根据需求调整技术选型与实现细节。建议先完成 Phase 1-2 的基础架构，再逐步迭代功能模块。
