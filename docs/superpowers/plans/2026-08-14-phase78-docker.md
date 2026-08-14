# Phase 7/8 Docker 部署 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** docker-compose 一键部署(前端 nginx / 后端 JRE21 / PostgreSQL+pgvector)+ 密钥环境变量化 + 中英文档。

**Architecture:** 三容器 compose;后端多阶段构建(skipTests);前端 nginx SPA 托管 + /api 反代;application.yml 恢复跟踪为纯环境变量版,本机密钥迁移 .env。

**Tech Stack:** Docker + docker-compose;Maven 3.9 + Temurin 21;Node 20 + nginx alpine;pgvector/pgvector:pg16。

**规格:** `docs/superpowers/specs/2026-08-14-phase78-docker-design.md`

## Global Constraints

- 不新增代码依赖;部署文件全部 UTF-8;文档中文
- `.env` 已 gitignore(勿提交真实密钥);`application.yml` 干净版**不得含任何真实密钥**
- 验证命令:`docker compose config`(compose v2;v1 为 `docker-compose config`)、后端 `cd backend && DB_PASSWORD=root mvn test`、前端 `cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build`
- commit 英文 subject,逐任务提交

---

### Task 1: 密钥迁移 + 恢复跟踪干净版 application.yml

**Files:**
- Modify: `backend/src/main/resources/application.yml`(覆盖为干净版,恢复跟踪)
- Create: `.env`(本地,gitignored,存放真实密钥——**只本地写,不提交**)

**Interfaces:**
- Produces: 干净版 yml 环境变量约定(Task 4 compose 注入、Task 5 文档环境变量表消费)

- [ ] **Step 1: 把本机 yml 的真实密钥写入本地 .env**

先读 `backend/src/main/resources/application.yml` 中 `siliconflow-api-key` 与 `deepseek-api-key` 默认值(当前是真实 key),写入根目录 `.env`(gitignored):

```bash
cat > .env << 'EOF'
# 本机开发与 docker compose 共用(勿提交)
DB_PASSWORD=root
SILICONFLOW_API_KEY=<本机 yml 里的真实值>
DEEPSEEK_API_KEY=<本机 yml 里的真实值>
ADMIN_PASSWORD=admin123
JWT_SECRET=neuralbase-dev-secret-key-please-change-me-32bytes!
EOF
```

(<...> 处用实际读到的值替换;执行前先 `grep api-key backend/src/main/resources/application.yml` 确认)

- [ ] **Step 2: 覆盖 yml 为干净版(环境变量占位,无真实密钥)**

```yaml
spring:
  application:
    name: neuralbase-backend
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:neuralbase}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:}
  flyway:
    enabled: true
    locations: classpath:db/migration
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 60MB
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    banner: false
    db-config:
      id-type: auto
neuralbase:
  auth:
    jwt-secret: ${JWT_SECRET:neuralbase-dev-secret-key-please-change-me-32bytes!}
    token-expire-hours: 24
    default-admin-password: ${ADMIN_PASSWORD:admin123}
  storage:
    upload-dir: ${UPLOAD_DIR:uploads}
  news:
    cron: ${NEWS_CRON:0 0 8 * * *}
  ai:
    siliconflow-base-url: https://api.siliconflow.cn
    siliconflow-api-key: ${SILICONFLOW_API_KEY:}
    deepseek-base-url: https://api.deepseek.com
    deepseek-api-key: ${DEEPSEEK_API_KEY:}
    deepseek-model: ${DEEPSEEK_MODEL:deepseek-v4-flash}
server:
  port: ${SERVER_PORT:8080}
```

- [ ] **Step 3: 本地验证(env 加载后测试与启动)**

Run: `cd backend && set -a && source ../.env && set +a && DB_PASSWORD=root mvn test -q`
Expected: 107/107 绿

Run: `set -a && source ../.env && set +a && mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`(短暂起停,确认 env 注入链路 OK,`/api/health` 200 后停)

- [ ] **Step 4: 提交(不含 .env)**

```bash
git add backend/src/main/resources/application.yml
git commit -m "chore: restore tracked application.yml as env-placeholder only (secrets moved to local .env)"
```

---

### Task 2: backend Dockerfile + .dockerignore

**Files:**
- Create: `backend/Dockerfile`
- Create: `backend/.dockerignore`

**Interfaces:**
- Produces: 镜像 `neuralbase-backend`(Task 4 引用)

- [ ] **Step 1: 写文件**

`backend/Dockerfile`:

```dockerfile
# 构建阶段:打包(镜像内无数据库,集成测试跳过;测试在本机/CI 跑)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# 运行阶段
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN mkdir -p /app/uploads
VOLUME /app/uploads
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

`backend/.dockerignore`:

```
target/
uploads/
.idea/
*.iml
```

- [ ] **Step 2: 验证**

Run: `docker --version && docker build --check . 2>/dev/null || docker build -q -t neuralbase-backend:test backend/ 2>&1 | tail -5`
Expected: 本机有 Docker 则构建成功;无 Docker 则记录「需 Docker 环境验收」,继续后续任务(文件语法人工复核)

- [ ] **Step 3: 提交**

```bash
git add backend/Dockerfile backend/.dockerignore
git commit -m "feat: backend dockerfile (maven build + jre21 runtime)"
```

---

### Task 3: frontend Dockerfile + nginx.conf + .dockerignore

**Files:**
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`
- Create: `frontend/.dockerignore`

**Interfaces:**
- Produces: 镜像 `neuralbase-frontend`(Task 4 引用);nginx 反代依赖 backend 服务名与 8080 端口

- [ ] **Step 1: 写文件**

`frontend/Dockerfile`:

```dockerfile
FROM node:20-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
```

`frontend/nginx.conf`:

```nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    # 50MB 上传限制与后端 multipart 对齐
    client_max_body_size 60m;

    # SPA history 路由回退
    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_buffering off;          # SSE 流式聊天勿缓冲
        proxy_read_timeout 300s;      # 流式生成与爬取任务耗时
    }
}
```

`frontend/.dockerignore`:

```
node_modules/
dist/
.idea/
```

- [ ] **Step 2: 验证**

Run: `docker build -q -t neuralbase-frontend:test frontend/ 2>&1 | tail -5`
Expected: 有 Docker 则构建成功;无 Docker 则记录待验收

- [ ] **Step 3: 提交**

```bash
git add frontend/Dockerfile frontend/nginx.conf frontend/.dockerignore
git commit -m "feat: frontend dockerfile with nginx spa hosting and api proxy"
```

---

### Task 4: docker-compose.yml + .env.example

**Files:**
- Create: `docker-compose.yml`
- Create: `.env.example`

**Interfaces:**
- Consumes: Task 2/3 镜像;Task 1 环境变量约定
- Produces: 一键部署入口(Task 5 文档、Task 6 验收消费)

- [ ] **Step 1: 写文件**

`docker-compose.yml`:

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: neuralbase-postgres
    environment:
      POSTGRES_DB: ${DB_NAME:-neuralbase}
      POSTGRES_USER: ${DB_USERNAME:-postgres}
      POSTGRES_PASSWORD: ${DB_PASSWORD:?请在 .env 设置 DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME:-postgres} -d ${DB_NAME:-neuralbase}"]
      interval: 5s
      timeout: 3s
      retries: 12

  backend:
    build: ./backend
    container_name: neuralbase-backend
    environment:
      DB_HOST: postgres
      DB_PORT: "5432"
      DB_NAME: ${DB_NAME:-neuralbase}
      DB_USERNAME: ${DB_USERNAME:-postgres}
      DB_PASSWORD: ${DB_PASSWORD:?请在 .env 设置 DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET:?请在 .env 设置 JWT_SECRET}
      ADMIN_PASSWORD: ${ADMIN_PASSWORD:-admin123}
      SILICONFLOW_API_KEY: ${SILICONFLOW_API_KEY:-}
      DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY:-}
      DEEPSEEK_MODEL: ${DEEPSEEK_MODEL:-deepseek-v4-flash}
      NEWS_CRON: ${NEWS_CRON:-0 0 8 * * *}
      UPLOAD_DIR: /app/uploads
    volumes:
      - uploads:/app/uploads
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy

  frontend:
    build: ./frontend
    container_name: neuralbase-frontend
    ports:
      - "80:80"
    depends_on:
      - backend

volumes:
  pgdata:
  uploads:
```

`.env.example`:

```bash
# ===== 必填 =====
# PostgreSQL 密码(自动建库账号)
DB_PASSWORD=change-me
# JWT 签名密钥(≥32 字节随机串;生产务必更换)
JWT_SECRET=change-me-please-generate-a-random-32-bytes-string!

# ===== AI 服务(不填则 AI 对话/摘要/embedding 不可用) =====
DEEPSEEK_API_KEY=sk-xxxxxxxx
SILICONFLOW_API_KEY=sk-xxxxxxxx

# ===== 可选(有默认值) =====
DB_NAME=neuralbase
DB_USERNAME=postgres
ADMIN_PASSWORD=admin123
DEEPSEEK_MODEL=deepseek-v4-flash
NEWS_CRON=0 0 8 * * *
```

- [ ] **Step 2: 验证**

Run: `docker compose config --quiet && echo OK`
Expected: `OK`(compose v1 用 `docker-compose config -q`;本机无 docker 则记录待验收)

Run(交叉核对变量引用):`grep -o '\${[A-Z_]*' docker-compose.yml | sort -u` 与 `.env.example` 变量名集合一致

- [ ] **Step 3: 提交**

```bash
git add docker-compose.yml .env.example
git commit -m "feat: docker-compose one-click deployment (postgres + backend + frontend)"
```

---

### Task 5: README + 部署文档

**Files:**
- Create: `README.md`
- Create: `docs/部署文档.md`

**Interfaces:** 无(纯文档)

- [ ] **Step 1: 写 README.md**

```markdown
# NeuralBase — 个人 AI 知识中枢

基于 Spring Boot 3 + Spring AI(DeepSeek / SiliconFlow Qwen3-Embedding)+ PostgreSQL(pgvector)+ Vue 3 的个人知识管理平台。

## 功能

| 模块 | 能力 |
|------|------|
| 📚 知识库 | 多格式上传(PDF/Word/MD/TXT)、自动切片 Embedding、pgvector 语义搜索、文档阅读器(目录/滚动高亮/PDF 预览) |
| 💬 AI 对话 | DeepSeek 流式对话,普通 / 知识库(RAG)双模式,会话管理、点赞点踩 |
| 📖 八股文库 | URL 导入 → 爬取 → AI 结构化问答对,标签筛选、收藏进知识库 |
| 📰 AI 日报 | GitHub Trending + Hacker News 定时/手动爬取,DeepSeek 单篇摘要分类,时间线 + 一键收藏 + 已读 |

## 架构

```
Vue3 + Vite (nginx 托管, /api 反代)
        │
Spring Boot 3 (REST + SSE + @Scheduled)
        │
PostgreSQL 16 + pgvector (业务表 + 向量同库)
```

## 快速开始(Docker,推荐)

```bash
git clone <repo-url> && cd neural-base
cp .env.example .env   # 填入 DB_PASSWORD、JWT_SECRET 与两个 AI key
docker compose up -d --build
# 打开 http://localhost:8080 访问前端(默认账号 admin / ADMIN_PASSWORD)
```

## 本地开发

```bash
# 后端(需本机 PostgreSQL,库 neuralbase / neuralbase_test)
set -a && source .env && set +a
cd backend && mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081

# 前端(vite 代理 → 8081)
cd frontend && npm install && npm run dev
```

## 测试

- 后端:`cd backend && DB_PASSWORD=<测试库密码> mvn test`
- 前端:`cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build`

## 部署

详见 [docs/部署文档.md](docs/部署文档.md)。

## License

MIT
```

- [ ] **Step 2: 写 docs/部署文档.md**

```markdown
# NeuralBase 部署文档

> 适用:任意装有 Docker(Compose v2)的主机。数据库迁移由 Flyway 启动时自动执行。

## 1. 环境变量

| 变量 | 必填 | 默认 | 说明 |
|------|------|------|------|
| DB_PASSWORD | ✅ | — | PostgreSQL 密码(自动建库) |
| JWT_SECRET | ✅ | — | JWT 签名密钥,≥32 字节随机串(如 `openssl rand -hex 32` 生成) |
| DEEPSEEK_API_KEY | 建议 | 空 | 对话/摘要/八股结构化;不填则相关功能报错 |
| SILICONFLOW_API_KEY | 建议 | 空 | 文档 Embedding;不填则上传文档无法入库 |
| DB_NAME / DB_USERNAME | — | neuralbase / postgres | 数据库名与账号 |
| ADMIN_PASSWORD | — | admin123 | 初始管理员密码(首次启动创建) |
| DEEPSEEK_MODEL | — | deepseek-v4-flash | 对话模型 |
| NEWS_CRON | — | `0 0 8 * * *` | 日报定时爬取 cron |

## 2. 部署步骤

1. `cp .env.example .env` 并填写必填项
2. `docker compose up -d --build`
3. 浏览器打开 `http://<主机>:80`,用 `admin / ADMIN_PASSWORD` 登录
4. 后端 API 直连 `http://<主机>:8080`(默认未暴露公网用途,可自行加反代/HTTPS)

## 3. 数据持久化与备份

- 数据卷:`pgdata`(数据库)、`uploads`(上传原文件),`docker compose down` 不删除
- 备份:`docker compose exec postgres pg_dump -U postgres neuralbase > backup.sql`
- 恢复:`docker compose exec -T postgres psql -U postgres neuralbase < backup.sql`
- 上传文件:备份 `docker volume` 对应目录或 `docker cp`

## 4. 升级

- 拉新代码后 `docker compose up -d --build`;数据库结构变更由 Flyway 迁移自动应用(V1~V6)
- 版本升级前建议先备份(见上)

## 5. 常见问题

- **80/8080 端口被占**:改 compose 里 `ports` 映射(如 `"8081:80"`)
- **上传失败**:nginx 已放行 60MB,与后端 50MB 限制对齐;更大文件改 compose/nginx 两端
- **八股文 JS 渲染站点抓取失败**:后端镜像内未安装 Playwright 浏览器;静态站点(Jsoup)不受影响,JS 站点需自行 `docker compose exec backend` 内不可行 → 本地导入后再部署,或后续挂载浏览器缓存
- **AI 功能报 401**:检查 .env 中两个 API key 后 `docker compose up -d` 重启 backend
- **密钥轮换**:改 .env → `docker compose up -d --force-recreate backend`

## 6. 本地开发(非 Docker)

见 README「本地开发」;后端需本机 PostgreSQL(neuralbase / neuralbase_test 两库),密钥经 `set -a; source .env; set +a` 注入。
```

- [ ] **Step 3: 提交**

```bash
git add README.md docs/部署文档.md
git commit -m "docs: readme and deployment guide"
```

---

### Task 6: 总验证 + PROGRESS 收尾

- [ ] **Step 1: 回归与校验**

Run: `docker compose config --quiet && echo COMPOSE-OK`(无 Docker 则跳过并记录)
Run: `cd backend && set -a && source ../.env && set +a && DB_PASSWORD=root mvn test -q`(预期 107 绿)
Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build`(预期 67 绿)

- [ ] **Step 2: Docker 真机验收(本机有 Docker 时)**

```bash
docker compose up -d --build
# 等待健康后:
curl http://localhost:8080/            # 前端首页 200
curl http://localhost:8080/api/health  # {"code":0,...}
curl -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}'  # 登录 200
docker compose logs backend | grep -i flyway   # 迁移成功
# 完成后: docker compose down(保留卷)或 down -v(清数据)
```

无 Docker 环境:记录「由用户在 Docker 环境按 docs/部署文档.md 验收」,作为明确的未验证项

- [ ] **Step 3: PROGRESS 更新并提交**

Phase 7/8 → ✅;下一步建议清空(全部 Roadmap 完成);测试数不变:

```bash
git add docs/PROGRESS.md
git commit -m "docs: record phase7/8 docker deployment completion"
```

---

## Self-Review 记录

- **规格覆盖**:§3.1 → Task 1;§3.2 → Task 2/3/4;§3.3 → Task 5;§四 验证 → Task 6
- **占位符**:无 TBD;Task 1 的 `<真实值>` 是执行时读取动作(已注明先 grep 确认),非文档占位
- **类型一致**:环境变量名 DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD/JWT_SECRET/ADMIN_PASSWORD/SILICONFLOW_API_KEY/DEEPSEEK_API_KEY/DEEPSEEK_MODEL/NEWS_CRON/UPLOAD_DIR/SERVER_PORT 在 yml/compose/.env.example/文档间一致
