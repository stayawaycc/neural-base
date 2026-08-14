# Phase 7/8 Docker 部署与文档设计规格

> 日期:2026-08-14　状态:已敲定(经 brainstorming 澄清)
> 所属:Phase 7(Docker 本地部署)+ Phase 8(部署文档)——Roadmap 最后两个阶段,合并为一个规格

---

## 一、已敲定决策

| # | 决策点 | 结论 |
|---|--------|------|
| 1 | 范围 | Docker 化(后端/前端/DB)+ docker-compose 一键启动 + .env.example + 部署文档;**不做压测/性能优化/K8s/CI/CD**(个人工具 YAGNI) |
| 2 | yml 密钥策略 | 仓库恢复「环境变量占位」干净版 application.yml(无任何密钥默认值);本机真实 key 迁移到本地 `.env`(已 gitignore) |
| 3 | 文档形态 | 根 `README.md`(简介/快速开始/架构)+ `docs/部署文档.md`(详细:环境变量表/部署步骤/备份/FAQ) |
| 4 | 数据库 | 单 PostgreSQL `pgvector/pgvector:pg16`(与现有单库架构一致;Flyway 自动迁移) |
| 5 | 前端托管 | nginx:SPA history 回退 + `/api` 反代 backend:8080 + `client_max_body_size 60m`(配合 50MB 上传) |
| 6 | 后端镜像 | 多阶段:`maven:3.9-eclipse-temurin-21` 打包(`-DskipTests`,镜像内无 DB 跑不了集成测试)→ `eclipse-temurin:21-jre` 运行;uploads 卷挂载 |

**不做(YAGNI)**:压测报告、Kubernetes、CI/CD 流水线、多实例部署、Redis。

## 二、文件清单

**新增**:
- `backend/Dockerfile`、`backend/.dockerignore`
- `frontend/Dockerfile`、`frontend/.dockerignore`、`frontend/nginx.conf`
- `docker-compose.yml`、`.env.example`
- `README.md`、`docs/部署文档.md`

**恢复跟踪**:`backend/src/main/resources/application.yml`(干净版,全部 `${ENV:...}`)

## 三、内容要点

### 3.1 application.yml 干净版

- `spring.datasource.url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:neuralbase}`
- `spring.datasource.username/password: ${DB_USERNAME:postgres} / ${DB_PASSWORD:}`
- `neuralbase.auth.jwt-secret: ${JWT_SECRET:neuralbase-dev-secret-key-please-change-me-32bytes!}`(默认值仅本地开发)
- `neuralbase.auth.default-admin-password: ${ADMIN_PASSWORD:admin123}`
- `neuralbase.ai.siliconflow-api-key / deepseek-api-key: ${...:}`(空默认,容器内由 env 注入)
- `neuralbase.news.cron: ${NEWS_CRON:0 0 8 * * *}`
- `server.port: ${SERVER_PORT:8080}`
- 迁移流程:读本机现有 yml 的 2 个真实 key → 写入本地 `.env`(gitignored)→ 覆盖 yml 为干净版 → 本地开发 `set -a; source .env; set +a`

### 3.2 docker-compose

```yaml
services:
  postgres: pgvector/pgvector:pg16, POSTGRES_DB/USER/PASSWORD=${DB_*}, volume pgdata:/var/lib/postgresql/data, healthcheck pg_isready
  backend: build ./backend, env: DB_HOST=postgres + 全量 ${...} 注入 + UPLOAD_DIR=/app/uploads, volume uploads:/app/uploads, depends_on postgres(healthy), ports 8080:8080
  frontend: build ./frontend, ports "80:80", depends_on backend
volumes: pgdata, uploads
```

### 3.3 文档

- README:项目简介、四大功能表、架构图(简化单库版)、Docker 快速开始(3 步)、本地开发、目录结构、测试命令、License
- 部署文档:环境变量全表(必填/可选/默认值)、部署步骤、数据持久化与备份(pg_dump/pg_restore)、Flyway 升级说明、常见问题(端口占用、上传大小、Playwright 浏览器缺失影响八股 JS 站点、密钥轮换)、任意主机部署(仅需 Docker)

## 四、验证标准

1. `docker compose config` 无语法错误;`.env.example` 与 compose 引用的变量一一对应
2. 本机 Docker 可用则:`docker compose up -d --build` → 健康检查 → 登录 → 上传文档 → 日报页 → 数据卷持久化验证;不可用则记录,由用户在 Docker 环境按文档验收
3. 后端 107/前端 67 测试不受影响(纯部署文件);恢复的 yml 用环境变量默认值在本地可启动(现有测试已验证同款配置)
