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
