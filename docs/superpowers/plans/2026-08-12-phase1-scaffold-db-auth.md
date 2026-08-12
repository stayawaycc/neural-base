# NeuralBase Phase 1 实施计划（脚手架 + 数据库 + 认证）

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务执行本计划。步骤用复选框（`- [ ]`）跟踪。

**Goal:** 搭建 NeuralBase 后端骨架：Spring Boot 应用能连接本机 PostgreSQL、用 Flyway 建出完整单 PG 表结构、实现 JWT 登录认证 + 知识库 CRUD，全部由集成测试覆盖（测试跑在本机 `neuralbase_test` 库上）。

**Architecture:** 单体 Spring Boot 3.5 应用（`D:\neural-base\backend`），MyBatis-Plus 做持久化，Spring Security + JJWT 做认证，Flyway 管 schema。单 PostgreSQL 库（业务表 + pgvector 同库）。无 Docker（本机原生 PG 18.4 + Redis）。

**Tech Stack:** JDK 21 · Spring Boot 3.5.16 · MyBatis-Plus 3.5.17 · PostgreSQL 18.4 + pgvector 0.8.5 · Flyway · JJWT 0.12.7

## Global Constraints

- 项目根目录：`D:\neural-base`，git init；后端在 `backend/`（目录命名与设计文档的 `neural-base-backend` 略异，更简洁）
- **单 PostgreSQL**：开发库 `neuralbase`、测试库 `neuralbase_test`；**不用 MySQL、不用 Docker**
- pgvector 必须启用；`vector_store.embedding` 为 `VECTOR(4096)`。**已裁决（2026-08-12）**：pgvector 的 HNSW/IVFFlat 索引上限 2000 维，4096 维无法建近似索引 → 采用精确扫描（个人量级足够快）；Phase 2 的 Spring AI `PgVectorStore` 必须用 `initializeSchema(false)` + Flyway 管理表结构，避开自动建索引导致的 2000 维报错
- JDK 21；Boot 3.5.16；包根 `com.neuralbase`
- 认证：单系统用户，启动时种子化 `admin`（密码取 `neuralbase.auth.default-admin-password`，默认 `admin123`）；JWT 24h 过期
- 统一响应体 `Result<T>{code,message,data}`；未认证返回 401 JSON
- 测试：`@SpringBootTest` + MockMvc + `@ActiveProfiles("test")`，跑在 `neuralbase_test`（Flyway 自动迁移）；`application-test.yml` 位于 `src/test/resources/`（不入生产 JAR）
- **已裁决 FK 级联（2026-08-12）**：`document.kb_id` → `ON DELETE CASCADE`（删库删文档）；`chat_session.kb_id` → `ON DELETE SET NULL`（保留会话）；`daily_news.source_id` → `ON DELETE SET NULL`（保留日报）。注意：文档的向量 chunk 需 Phase 2 显式清理
- **已裁决缺省资源语义（2026-08-12）**：`GET/PUT/DELETE` 不存在的 id 返回 HTTP 200 + `code:0, data:null`（前端需特判）；Phase 2 接入 UI 前可再议
- Spring AI（1.1.8 BOM + deepseek/pgvector starter）**本阶段不加入 pom**，Phase 2 再加（避免未配置 api-key 导致上下文启动失败）

## 前置条件（执行者必须向用户获取）

- **本地 PostgreSQL 超级用户密码**：本机 PG 18.4 运行于 `localhost:5432`，postgres 账号密码只有用户知道。建库步骤需要它。若用户不愿给密码，请用户自行执行第 1 步建库命令后，再提供应用连接账号密码。

---

### Task 1: 项目骨架 + 建库

**Files:**
- Create: `D:\neural-base\.gitignore`
- Create: `D:\neural-base\backend\pom.xml`
- Create: `D:\neural-base\backend\src\main\java\com\neuralbase\NeuralBaseApplication.java`
- Create: `D:\neural-base\backend\src\main\java\com\neuralbase\common\Result.java`
- Create: `D:\neural-base\backend\src\main\java\com\neuralbase\controller\HealthController.java`
- Create: `D:\neural-base\backend\src\main\resources\application.yml`
- Create: `D:\neural-base\backend\src\test\resources\application-test.yml`
- Test: `D:\neural-base\backend\src\test\java\com\neuralbase\NeuralBaseApplicationTests.java`

**Interfaces:**
- Consumes: 无
- Produces: `Result<T>` 记录（code/message/data），后续所有控制器使用

- [ ] **Step 1: 初始化仓库与目录**

```bash
cd /d
mkdir -p /d/neural-base
cd /d/neural-base
git init
mkdir -p backend/src/main/java/com/neuralbase/{common,controller,config,security,auth}
mkdir -p backend/src/main/java/com/neuralbase/{entity,mapper,service,service/impl}
mkdir -p backend/src/main/resources/db/migration
mkdir -p backend/src/test/java/com/neuralbase
```

- [ ] **Step 2: 写 `.gitignore`**

`D:\neural-base\.gitignore`：
```
target/
node_modules/
dist/
.idea/
*.iml
.vscode/
.DS_Store
.env
*.log
backend/uploads/
```

- [ ] **Step 3: 写 `pom.xml`**（内容见下，完整复制）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>
    <groupId>com.neuralbase</groupId>
    <artifactId>neuralbase-backend</artifactId>
    <version>0.1.0</version>
    <name>neuralbase-backend</name>
    <properties>
        <java.version>21</java.version>
        <mybatis-plus.version>3.5.17</mybatis-plus.version>
        <jjwt.version>0.12.7</jjwt.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: 写主类与响应体、健康检查**

`NeuralBaseApplication.java`：
```java
package com.neuralbase;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.neuralbase.mapper")
public class NeuralBaseApplication {
    public static void main(String[] args) {
        SpringApplication.run(NeuralBaseApplication.class, args);
    }
}
```

`common/Result.java`：
```java
package com.neuralbase.common;

public record Result<T>(int code, String message, T data) {
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
```

`controller/HealthController.java`：
```java
package com.neuralbase.controller;

import com.neuralbase.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    @GetMapping
    public Result<String> health() {
        return Result.ok("ok");
    }
}
```

- [ ] **Step 5: 写配置文件**

`application.yml`：
```yaml
spring:
  application:
    name: neuralbase-backend
  datasource:
    url: jdbc:postgresql://localhost:5432/neuralbase
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:}
  flyway:
    enabled: true
    locations: classpath:db/migration
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
server:
  port: 8080
```

`application-test.yml`（`src/test/resources/`）：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/neuralbase_test
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:}
  flyway:
    enabled: true
    locations: classpath:db/migration
```

- [ ] **Step 6: 创建开发库与测试库**（需要 postgres 密码，向用户索取；或请用户执行）

```bash
export PGPASSWORD='<用户提供的 postgres 密码>'
"/d/postgresql/18/bin/psql.exe" -U postgres -h localhost -p 5432 -c "CREATE DATABASE neuralbase;"
"/d/postgresql/18/bin/psql.exe" -U postgres -h localhost -p 5432 -c "CREATE DATABASE neuralbase_test;"
```

验证：`"/d/postgresql/18/bin/psql.exe" -U postgres -h localhost -l` 应列出 `neuralbase`、`neuralbase_test`。

> 若用户不愿提供超级用户密码：请用户执行上面两条 CREATE DATABASE，然后提供 `neuralbase` / `neuralbase_test` 的应用级账号密码，填入 `${DB_PASSWORD}`。

- [ ] **Step 7: 写上下文加载测试**（先红后绿）

`NeuralBaseApplicationTests.java`：
```java
package com.neuralbase;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NeuralBaseApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 8: 跑测试验证**

```bash
cd /d/neural-base/backend
export DB_PASSWORD='<用户提供的连接密码>'
mvn test
```

**预期：先失败（数据库尚未建好/连不上）→ 建库后 PASS。** `contextLoads` 通过 = Boot 上下文 + 数据源连接 + Flyway 迁移（此时无迁移文件，空跑）全部正常。

- [ ] **Step 9: 提交**

```bash
cd /d/neural-base
git add -A
git commit -m "chore: scaffold Spring Boot 3.5 backend skeleton"
```

---

### Task 2: Flyway 全量表结构 + pgvector

**Files:**
- Create: `D:\neural-base\backend\src\main\resources\db\migration\V1__init_schema.sql`
- Test: `D:\neural-base\backend\src\test\java\com\neuralbase\schema\SchemaMigrationTest.java`

**Interfaces:**
- Consumes: Task 1 的数据库连接
- Produces: 全部表（`users/kb/document/chat_session/chat_message/baguwen/daily_news/crawl_task/crawler_source/vector_store`），`vector` 扩展，`vector_store.embedding` 为 `vector(4096)`

- [ ] **Step 1: 写失败测试**（先断言表存在，此时无迁移 → 失败）

`SchemaMigrationTest.java`：
```java
package com.neuralbase.schema;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SchemaMigrationTest {

    @Autowired
    private DataSource dataSource;

    private Set<String> tableNames() throws Exception {
        Set<String> names = new HashSet<>();
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT tablename FROM pg_tables WHERE schemaname = 'public'")) {
            while (rs.next()) names.add(rs.getString(1));
        }
        return names;
    }

    @Test
    void allBusinessTablesExist() throws Exception {
        Set<String> expected = Set.of("users", "kb", "document", "chat_session",
            "chat_message", "baguwen", "daily_news", "crawl_task", "crawler_source", "vector_store");
        Set<String> actual = tableNames();
        assertTrue(actual.containsAll(expected), "缺少表: " + expected.stream().filter(e -> !actual.contains(e)).toList());
    }

    @Test
    void pgvectorExtensionEnabled() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM pg_extension WHERE extname = 'vector'")) {
            rs.next();
            assertTrue(rs.getInt(1) == 1, "pgvector 扩展未启用");
        }
    }

    @Test
    void vectorStoreEmbeddingIs4096Dims() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT format_type(atttypid, atttypmod) FROM pg_attribute " +
                 "WHERE attrelid = 'vector_store'::regclass AND attname = 'embedding'")) {
            rs.next();
            assertTrue(rs.getString(1).equals("vector(4096)"), "embedding 列类型应为 vector(4096)，实际: " + rs.getString(1));
        }
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd /d/neural-base/backend
export DB_PASSWORD='<密码>'
mvn test -Dtest=SchemaMigrationTest
```

**预期：FAIL**（表不存在）。

- [ ] **Step 3: 写迁移脚本 `V1__init_schema.sql`**（完整复制）

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE kb (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    color       VARCHAR(20),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE document (
    id         BIGSERIAL PRIMARY KEY,
    kb_id      BIGINT REFERENCES kb(id),
    title      VARCHAR(255)  NOT NULL,
    file_type  VARCHAR(20),
    file_path  VARCHAR(500),
    source_url VARCHAR(1000),
    status     VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    chunk_count INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE chat_session (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(255) NOT NULL,
    mode           VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    kb_id          BIGINT REFERENCES kb(id),
    model          VARCHAR(50)  NOT NULL DEFAULT 'deepseek-v4-flash',
    first_question TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE chat_message (
    id         BIGSERIAL PRIMARY KEY,
    session_id BIGINT      NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    role       VARCHAR(10) NOT NULL,
    content    TEXT        NOT NULL,
    feedback   VARCHAR(10),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE baguwen (
    id         BIGSERIAL PRIMARY KEY,
    source_url VARCHAR(1000),
    title      VARCHAR(255),
    category   VARCHAR(50),
    tags       TEXT[],
    qa_json    JSONB,
    status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE crawler_source (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100)  NOT NULL,
    url        VARCHAR(1000) NOT NULL,
    type       VARCHAR(10)   NOT NULL,
    selector   VARCHAR(500),
    category   VARCHAR(50),
    enabled    BOOLEAN       NOT NULL DEFAULT TRUE,
    freq_min   INT           NOT NULL DEFAULT 1440,
    last_crawl TIMESTAMPTZ
);

CREATE TABLE daily_news (
    id         BIGSERIAL PRIMARY KEY,
    source_id  BIGINT REFERENCES crawler_source(id),
    url        VARCHAR(1000),
    title      VARCHAR(255) NOT NULL,
    category   VARCHAR(50),
    summary    TEXT,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE crawl_task (
    id         BIGSERIAL PRIMARY KEY,
    type       VARCHAR(20)  NOT NULL,
    target_url VARCHAR(1000),
    status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    progress   INT          NOT NULL DEFAULT 0,
    error      TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Spring AI PgVectorStore 兼容表（4096 维）
CREATE TABLE vector_store (
    id        UUID PRIMARY KEY,
    content   TEXT,
    metadata  JSONB,
    embedding VECTOR(4096)
);

-- 已知不可行（pgvector HNSW/IVFFlat 索引上限 2000 维，本列为 vector(4096)）：
-- 用注释保留，等维度 ≤2000 或 pgvector 支持更高维后再启用。
-- CREATE INDEX idx_vector_store_embedding
--     ON vector_store USING hnsw (embedding vector_cosine_ops);
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn test -Dtest=SchemaMigrationTest
```

**预期：PASS**（3 个用例）。

- [ ] **Step 5: 提交**

```bash
cd /d/neural-base
git add -A
git commit -m "feat: flyway initial schema with pgvector"
```

---

### Task 3: JWT 认证 + 种子用户

**Files:**
- Create: `backend/src/main/java/com/neuralbase/entity/User.java`
- Create: `backend/src/main/java/com/neuralbase/mapper/UserMapper.java`
- Create: `backend/src/main/java/com/neuralbase/security/JwtUtil.java`
- Create: `backend/src/main/java/com/neuralbase/security/JwtAuthenticationFilter.java`
- Create: `backend/src/main/java/com/neuralbase/config/SecurityConfig.java`
- Create: `backend/src/main/java/com/neuralbase/auth/LoginRequest.java`
- Create: `backend/src/main/java/com/neuralbase/controller/AuthController.java`
- Create: `backend/src/main/java/com/neuralbase/config/DataInitializer.java`
- Test: `backend/src/test/java/com/neuralbase/auth/AuthControllerTest.java`

**Interfaces:**
- Consumes: Task 2 的 `users` 表
- Produces: `POST /api/auth/login`（→ `Result<String>` token）、`GET /api/auth/me`（→ `Result<User>`，密码置空）、`User`/`UserMapper`（Task 4 复用）、`SecurityConfig`（后续控制器全走 JWT）

- [ ] **Step 1: 写失败测试**（此时无 /api/auth → 401/404）

`AuthControllerTest.java`：
```java
package com.neuralbase.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    private String login(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
        String body = r.getResponse().getContentAsString();
        return body.replaceAll(".*\"data\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void loginWithCorrectPasswordReturnsToken() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data").isNotEmpty());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong-pass\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithTokenReturnsUser() throws Exception {
        String token = login("admin", "admin123");
        assertThat(token).isNotBlank();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("admin"))
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/kb"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn test -Dtest=AuthControllerTest
```

**预期：FAIL**。

- [ ] **Step 3: 实现实体与 Mapper**

`entity/User.java`：
```java
package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String role;
    private OffsetDateTime createdAt;
}
```

`mapper/UserMapper.java`：
```java
package com.neuralbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neuralbase.entity.User;

public interface UserMapper extends BaseMapper<User> {
}
```

- [ ] **Step 4: 实现 JWT 工具与过滤器**

`security/JwtUtil.java`：
```java
package com.neuralbase.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${neuralbase.auth.jwt-secret}") String secret,
                   @Value("${neuralbase.auth.token-expire-hours}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000L;
    }

    public String generateToken(String username) {
        Date now = new Date();
        return Jwts.builder()
            .subject(username)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expireMillis))
            .signWith(key)
            .compact();
    }

    public String parseUsername(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).getPayload();
        return claims.getSubject();
    }
}
```

`security/JwtAuthenticationFilter.java`：
```java
package com.neuralbase.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neuralbase.entity.User;
import com.neuralbase.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String username = jwtUtil.parseUsername(header.substring(7));
                User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getUsername, username));
                if (user != null) {
                    var auth = new UsernamePasswordAuthenticationToken(
                        username, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                // token 无效 → 保持未认证
            }
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 5: 实现 SecurityConfig 与登录端点**

`config/SecurityConfig.java`：
```java
package com.neuralbase.config;

import com.neuralbase.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/login", "/api/health").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint()))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未认证\",\"data\":null}");
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

`auth/LoginRequest.java`：
```java
package com.neuralbase.auth;

public record LoginRequest(String username, String password) {
}
```

`controller/AuthController.java`：
```java
package com.neuralbase.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neuralbase.auth.LoginRequest;
import com.neuralbase.common.Result;
import com.neuralbase.entity.User;
import com.neuralbase.mapper.UserMapper;
import com.neuralbase.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<Result<String>> login(@RequestBody LoginRequest req) {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, req.username()));
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(401, "用户名或密码错误"));
        }
        return ResponseEntity.ok(Result.ok(jwtUtil.generateToken(user.getUsername())));
    }

    @GetMapping("/me")
    public Result<User> me(Authentication authentication) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, authentication.getName()));
        user.setPasswordHash(null);
        return Result.ok(user);
    }
}
```

`config/DataInitializer.java`：
```java
package com.neuralbase.config;

import com.neuralbase.entity.User;
import com.neuralbase.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public DataInitializer(UserMapper userMapper, PasswordEncoder passwordEncoder,
                           @Value("${neuralbase.auth.default-admin-password}") String adminPassword) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userMapper.selectCount(null) == 0) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setRole("ADMIN");
            userMapper.insert(admin);
        }
    }
}
```

- [ ] **Step 6: 跑测试确认通过**

```bash
mvn test -Dtest=AuthControllerTest
```

**预期：PASS**（4 个用例）。

- [ ] **Step 7: 提交**

```bash
cd /d/neural-base
git add -A
git commit -m "feat: jwt auth with seeded admin user"
```

---

### Task 4: 知识库 CRUD

**Files:**
- Create: `backend/src/main/java/com/neuralbase/entity/Kb.java`
- Create: `backend/src/main/java/com/neuralbase/mapper/KbMapper.java`
- Create: `backend/src/main/java/com/neuralbase/service/KbService.java`
- Create: `backend/src/main/java/com/neuralbase/service/impl/KbServiceImpl.java`
- Create: `backend/src/main/java/com/neuralbase/controller/KbController.java`
- Test: `backend/src/test/java/com/neuralbase/kb/KbControllerTest.java`

**Interfaces:**
- Consumes: Task 3 的登录/JWT、`users` 表；Task 2 的 `kb` 表
- Produces: `GET/POST/PUT/DELETE /api/kb`（后续 Phase 2 的 document/上传复用该控制器风格）

- [ ] **Step 1: 写失败测试**

`KbControllerTest.java`：
```java
package com.neuralbase.kb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class KbControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk()).andReturn();
        JsonNode node = objectMapper.readTree(r.getResponse().getContentAsString());
        return node.get("data").asText();
    }

    @Test
    void kbCrudRoundtrip() throws Exception {
        // create
        MvcResult create = mvc.perform(post("/api/kb")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Java 面试\",\"description\":\"八股文合集\",\"color\":\"#fbbf24\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").isNumber())
            .andExpect(jsonPath("$.data.name").value("Java 面试"))
            .andReturn();
        long id = objectMapper.readTree(create.getResponse().getContentAsString())
            .get("data").get("id").asLong();

        // list contains created
        mvc.perform(get("/api/kb").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.name=='Java 面试')]").exists());

        // update
        mvc.perform(put("/api/kb/{id}", id)
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Java 面试 v2\",\"description\":\"更新\",\"color\":\"#22d3ee\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Java 面试 v2"));

        // delete, then get returns empty
        mvc.perform(delete("/api/kb/{id}", id)
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk());
        mvc.perform(get("/api/kb/{id}", id)
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").doesNotExist());
    }
}
```

> 说明：测试用单方法内完整 roundtrip（create→list→update→delete），避免 JUnit PER_METHOD 生命周期下实例字段跨方法丢失的问题。

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn test -Dtest=KbControllerTest
```

**预期：FAIL**。

- [ ] **Step 3: 实现实体、Mapper、Service、Controller**

`entity/Kb.java`：
```java
package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("kb")
public class Kb {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String color;
    private OffsetDateTime createdAt;
}
```

`mapper/KbMapper.java`：
```java
package com.neuralbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neuralbase.entity.Kb;

public interface KbMapper extends BaseMapper<Kb> {
}
```

`service/KbService.java`：
```java
package com.neuralbase.service;

import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.neuralbase.entity.Kb;

public interface KbService extends IRepository<Kb> {
}
```

> **计划修正（2026-08-12，实测）**：MyBatis-Plus 3.5.9+ 已移除 `extension.service.IService/ServiceImpl`，替换为 `extension.repository.IRepository/AbstractRepository`。3.5.17 下 `IService` 不存在，必须用 `IRepository`。

`service/impl/KbServiceImpl.java`：
```java
package com.neuralbase.service.impl;

import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.extension.repository.AbstractRepository;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.neuralbase.entity.Kb;
import com.neuralbase.mapper.KbMapper;
import com.neuralbase.service.KbService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class KbServiceImpl extends AbstractRepository<KbMapper, Kb> implements KbService {

    @Autowired
    private KbMapper baseMapper;

    @Override
    public KbMapper getBaseMapper() {
        return baseMapper;
    }

    @Override
    public boolean saveBatch(Collection<Kb> entityList, int batchSize) {
        return executeBatch(entityList, batchSize, (sqlSession, entity) ->
            sqlSession.insert(SqlHelper.getSqlStatement(Kb.class, SqlMethod.INSERT_ONE), entity));
    }

    @Override
    public boolean updateBatchById(Collection<Kb> entityList, int batchSize) {
        return executeBatch(entityList, batchSize, (sqlSession, entity) ->
            sqlSession.update(SqlHelper.getSqlStatement(Kb.class, SqlMethod.UPDATE_BY_ID), entity));
    }

    @Override
    public boolean saveOrUpdateBatch(Collection<Kb> entityList, int batchSize) {
        return SqlHelper.saveOrUpdateBatch(getSqlSessionFactory(), Kb.class, log, entityList, batchSize,
            (sqlSession, entity) -> entity.getId() != null && getBaseMapper().selectById(entity.getId()) != null,
            (sqlSession, entity) -> {
                sqlSession.update(SqlHelper.getSqlStatement(Kb.class, SqlMethod.UPDATE_BY_ID), entity);
                return 0;
            });
    }
}
```

> 注意：`IRepository` 声明 `saveBatch`/`updateBatchById`/`saveOrUpdateBatch`/`getBaseMapper` 为抽象方法，`AbstractRepository` 提供其余（含 controller 用到的 `save/list/getById/updateById/removeById`），故子类只需 override 这 4 个。

`controller/KbController.java`：
```java
package com.neuralbase.controller;

import com.neuralbase.common.Result;
import com.neuralbase.entity.Kb;
import com.neuralbase.service.KbService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/kb")
public class KbController {

    private final KbService kbService;

    public KbController(KbService kbService) {
        this.kbService = kbService;
    }

    @GetMapping
    public Result<List<Kb>> list() {
        return Result.ok(kbService.list());
    }

    @GetMapping("/{id}")
    public Result<Kb> get(@PathVariable Long id) {
        return Result.ok(kbService.getById(id));
    }

    @PostMapping
    public Result<Kb> create(@RequestBody Kb kb) {
        kbService.save(kb);
        return Result.ok(kb);
    }

    @PutMapping("/{id}")
    public Result<Kb> update(@PathVariable Long id, @RequestBody Kb kb) {
        kb.setId(id);
        kbService.updateById(kb);
        return Result.ok(kbService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        kbService.removeById(id);
        return Result.ok(null);
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn test -Dtest=KbControllerTest
```

**预期：PASS**（4 个用例）。

- [ ] **Step 5: 全量回归 + 提交**

```bash
mvn test
```

**预期：全部 PASS**（ContextLoads + SchemaMigration + Auth + Kb）。

```bash
cd /d/neural-base
git add -A
git commit -m "feat: kb crud api"
```

---

## 完成标准（Phase 1 Done）

- `D:\neural-base` 为 git 仓库，`backend` 可 `mvn test` 全绿
- 本地 `neuralbase` 库已建全量表结构 + pgvector(4096)
- `POST /api/auth/login`、`GET /api/auth/me` 可用，其余 API 需 Bearer token
- 知识库 CRUD 可用
- 前端脚手架 + 后续模块（embedding/chat/爬虫/日报）为独立计划

## Self-Review

- **Spec coverage**：设计共识的认证（Q5-C）、单 PG（Q2-B）、pgvector 4096（Q9-B）、Boot 3.x+Spring AI 1.x 版本（Q10-A）均已覆盖；Spring AI starter 按约定留到 Phase 2。
- **Placeholder scan**：无 TBD/占位；唯一外部输入是 PostgreSQL 密码（前置条件已声明）。
- **Type consistency**：`Result<T>`、`User`、`Kb` 签名在 Task 3/4 间一致；`login()` 辅助方法在两测试中签名一致。
