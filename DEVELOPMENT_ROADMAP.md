# 智响后端 — 渐进式开发路线图

> 从零到一，每写完一个模块就自测，确保每一步都可运行、可验证。
> 本项目是对源项目照着手敲学习，代码逻辑与源项目保持一致。

---

## 项目说明

本仓库是一个 **手敲学习项目**，目的不是从零原创，而是逐行对照源项目代码，通过手打理解每一行代码的设计意图。

| 内容 | 说明 |
|------|------|
| **学习方式** | 对照源项目源码，逐行手敲，理解每个模块的设计与实现 |
| **代码原则** | 与源项目保持逻辑一致，不擅自改动接口设计、业务流程和核心实现 |
| **差异范围** | 仅允许自定义配置文件（如数据库连接、密钥等本地环境差异） |
| **学习目标** | 精通 Spring Boot 后端开发的模块划分、架构设计、常见业务实现 |

### 源项目

```
GitHub: https://github.com/G-Pegasus/zhiguang_be.git
```

请先将源项目 clone/fork 到本地作为参考，实现每个模块时打开源项目对应位置的代码，**手打抄录** 一遍并理解每一行的作用。

---
## 项目现状

| 项目 | 说明 |
|------|------|
| 框架 | Spring Boot 3.0.2, Java 17 |
| 构建工具 | Maven |
| GroupId | `com.czltnb` |
| 模块目录 | 已建好，每个模块的包结构已就位 |
| 已有代码 | 仅 `ZhiXiangBackendApplication.java` 启动类 + 空测试类 |
| 配置文件 | 无（需从模块1开始补） |
| 数据库 | 尚未引入 |
| ORM | 预留了 `mapper/` 目录，推荐 MyBatis-Plus |
| 缓存 | 预留了 `cache/` 目录 |

---

## 推荐开发顺序

模块之间有依赖关系，请严格按此顺序推进：

```
Phase 1: 基础设施
  ├── 准备工作（集成通用依赖、配置）
  └── 模块① 用户模块（基础用户实体 + MyBatis + 数据库表）

Phase 2: 认证与文件
  ├── 模块② 认证登录模块（注册、登录、JWT Token）
  └── 模块③ 对象存储模块（文件上传、下载）

Phase 3: 用户扩展
  └── 模块④ 用户资料模块（个人资料、头像等）

Phase 4: 互动
  ├── 模块⑤ 计数模块（点赞数、关注数等）
  └── 模块⑥ 用户关系模块（关注、粉丝、好友）

Phase 5: 高级功能
  ├── 模块⑦ AI 模块（LLM 对话、RAG）
  └── 模块⑧ 搜索模块（全文检索）
```

---

## 准备工作（基础设施）

在开始任何模块前，先搭建好项目基础能力。

### 步骤

1. **添加核心 pom 依赖**（一次性完成）
   - 以源项目 `pom.xml` 为准，逐条添加所需依赖
   - 典型依赖：Spring Boot Web Starter、Validation Starter、MySQL Driver、MyBatis-Plus Boot Starter、Lombok、JWT 相关、springdoc-openapi 等

2. **创建 application.yml**
   - 以源项目的配置文件为准，保留全部配置项
   - 仅替换其中敏感信息（数据库密码、密钥等）为本机环境值

3. **搭建 common 基础模块**
   - `common/web` → 统一返回体 `R<T>`、全局异常处理 `GlobalExceptionHandler`
   - `common/exception` → 业务异常 `BusinessException`、错误码枚举 `ErrorCode`
   - `common/util` → 常用工具类

### 自测方法

```bash
# 1. 编译通过
mvn clean compile -DskipTests

# 2. 运行启动类，控制台出现 Spring Boot 启动日志
mvn spring-boot:run

# 3. 写一个简单测试 controller（建在 common/web 下），访问 http://localhost:8080/api/ping 返回 pong
#    然后删掉这个测试 controller

# 4. 运行单元测试确认 contextLoads 依然通过
mvn test
```

---

## 模块① 用户模块

**依赖**: 准备工作已完成
**包路径**: `com.czltnb.zhi_xiang_backend.user`

**学习方式**: 打开源项目 `user/` 目录，逐文件手敲

### 实现步骤

1. **实体类** — `user/domain/User.java`
   - 照搬源项目的字段定义、注解、表名映射

2. **Mapper 接口** — `user/mapper/UserMapper.java`
   - 继承 `BaseMapper<User>`
   - 在 `resources/mapper/` 下创建 `UserMapper.xml`

3. **Service 层** — `user/service/UserService.java` + `impl/UserServiceImpl.java`
   - 与源项目的接口定义和实现逻辑完全一致

### 自测方法

```bash
# 1. 准备好建表 SQL（从源项目复制 DLL 语句）
# 2. 编写 UserMapperTest 单元测试
#    - 测试插入一条记录
#    - 测试根据 ID 查询
#    - 测试根据手机号查询
# 3. 运行测试
mvn test -Dtest=UserMapperTest

# 4. 确认 Service 层测试通过
mvn test -Dtest=UserServiceTest
```

**预期结果**: 数据库能正常读写 user 表，所有单元测试绿色通过。

---

## 模块② 认证登录模块

**依赖**: 模块①（用户模块）
**包路径**: `com.czltnb.zhi_xiang_backend.auth`

**学习方式**: 对照源项目 `auth/` 目录，逐文件手敲。重点关注 JWT 的 Token 生成/校验逻辑、Spring Security 过滤器链配置。

### 实现步骤

1. **添加 JWT 依赖**（`jjwt-api`, `jjwt-impl`, `jjwt-jackson`）

2. **JWT 工具类** — `auth/util/JwtUtil.java`
   - 与源项目一致：生成 access_token、refresh_token、解析校验

3. **Token 配置** — `auth/config/JwtConfig.java`
   - 密钥和数据源配置与源项目保持一致（本地仅改密钥文件路径）

4. **认证过滤器** — `auth/token/JwtAuthenticationFilter.java`
   - 照搬源项目的过滤器实现逻辑

5. **Spring Security 配置** — `auth/config/SecurityConfig.java`
   - 白名单路径和过滤器注册顺序与源项目一致

6. **DTO、Service、Controller**
   - 全部与源项目的接口签名和返回结构保持一致

### 自测方法

```bash
# 1. 启动项目
mvn spring-boot:run

# 2. 注册（终端或 Postman）
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800138000","password":"Test1234","nickname":"测试用户"}'
# 预期: 返回 200 + 用户信息 + token

# 3. 重复注册同一手机号
# 预期: 返回 400 业务异常（用户已存在）

# 4. 登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800138000","password":"Test1234"}'
# 预期: 返回 access_token 和 refresh_token

# 5. 密码错误
# 预期: 返回 401 未授权

# 6. 使用 access_token 访问受保护的测试接口
# 预期: 正常返回用户信息

# 7. 不传 token / 传过期 token
# 预期: 返回 401

# 8. 刷新 token
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<上一步得到的refresh_token>"}'
# 预期: 返回新的 access_token

# 9. 单测
mvn test -Dtest=AuthServiceTest
```

---

## 模块③ 对象存储模块

**依赖**: 模块②（需登录才能上传/下载私有文件）
**包路径**: `com.czltnb.zhi_xiang_backend.storage`

**学习方式**: 对照源项目 `storage/` 目录，逐文件手敲。重点学习策略模式在文件存储上的应用。

### 实现步骤

1. **配置** — `storage/config/StorageConfig.java`
   - 照搬源项目的存储策略配置

2. **存储策略接口** — `storage/api/FileStorageStrategy.java`
   - 照搬源项目接口定义

3. **存储实现** — 照搬源项目的本地实现和云存储实现

4. **Controller** — `storage/api/FileController.java`
   - 接口路径和逻辑与源项目一致

### 自测方法

```bash
# 1. 启动项目
mvn spring-boot:run

# 2. 上传文件（需带 token）
curl -X POST http://localhost:8080/api/storage/upload \
  -H "Authorization: Bearer <access_token>" \
  -F "file=@/path/to/a/test-image.png"
# 预期: 返回 200 + fileKey + url

# 3. 访问返回的 url
# 预期: 浏览器中显示图片

# 4. 删除文件
curl -X DELETE http://localhost:8080/api/storage/<fileKey> \
  -H "Authorization: Bearer <access_token>"
# 预期: 返回 204

# 5. 再次访问 url
# 预期: 404 或 410

# 6. 不上传文件直接请求
# 预期: 400 参数校验错误

# 7. 单元测试
mvn test -Dtest=FileStorageStrategyTest
```

---

## 模块④ 用户资料模块

**依赖**: 模块②（鉴权） + 模块③（上传头像）
**包路径**: `com.czltnb.zhi_xiang_backend.profile`

**学习方式**: 对照源项目 `profile/` 目录，逐文件手敲。

### 实现步骤

1. **实体层** — 照搬源项目的资料表结构和实体类

2. **Controller** — `profile/api/ProfileController.java`
   - 接口路径、方法签名、返回结构与源项目一致

3. **DTO** — 照搬源项目的请求体和响应体

### 自测方法

```bash
# 1. 注册用户 A 和 用户 B
# 2. 用户 A 更新资料
curl -X PUT http://localhost:8080/api/profile/me \
  -H "Authorization: Bearer <token_A>" \
  -H "Content-Type: application/json" \
  -d '{"bio":"你好世界","gender":"male","location":"北京"}'
# 预期: 返回更新后的完整资料

# 3. 用户 A 获取自己的资料
curl http://localhost:8080/api/profile/me \
  -H "Authorization: Bearer <token_A>"
# 预期: 返回所有字段

# 4. 用户 A 上传头像
curl -X POST http://localhost:8080/api/profile/avatar \
  -H "Authorization: Bearer <token_A>" \
  -F "file=@avatar.jpg"
# 预期: 返回头像 url，user 表的 avatar_url 已更新

# 5. 用户 B 查看用户 A 的资料
curl http://localhost:8080/api/profile/<user_A_id> \
  -H "Authorization: Bearer <token_B>"
# 预期: 返回公开资料

# 6. 未登录查看资料
# 预期: 401

# 7. 单元测试
mvn test -Dtest=ProfileServiceTest
```

---

## 模块⑤ 计数模块

**依赖**: 模块②（鉴权）
**包路径**: `com.czltnb.zhi_xiang_backend.counter`

**学习方式**: 对照源项目 `counter/` 目录，逐文件手敲。重点理解计数器的原子性保证和事件驱动设计。

### 实现步骤

1. **计数字段设计** — 照搬源项目的计数类型枚举和键值规范

2. **Service** — 与源项目保持一致：`increment`、`decrement`、`get`、`mGet`

3. **事件监听** — 照搬源项目的异步事件处理

4. **Controller** — 接口路径和逻辑与源项目一致

### 自测方法

```bash
# 1. 确保 Redis 已启动（如源项目使用 Redis）
# 2. 获取计数
curl http://localhost:8080/api/counter/like/post_123 \
  -H "Authorization: Bearer <token>"
# 预期: 返回 0

# 3. 增加计数
curl -X POST http://localhost:8080/api/counter/like/post_123/incr \
  -H "Authorization: Bearer <token>"
# 预期: 返回 1

# 4. 再次获取
# 预期: 返回 1

# 5. 减少计数
curl -X POST http://localhost:8080/api/counter/like/post_123/decr \
  -H "Authorization: Bearer <token>"
# 预期: 返回 0

# 6. 减少到负数
# 预期: 返回 0（不允许负值）

# 7. 单测
mvn test -Dtest=CounterServiceTest
```

---

## 模块⑥ 用户关系模块

**依赖**: 模块①（用户模块） + 模块⑤（计数模块，用于更新粉丝/关注数）
**包路径**: `com.czltnb.zhi_xiang_backend.relation`

**学习方式**: 对照源项目 `relation/` 目录，逐文件手敲。重点理解好友关系的数据建模和关注事件的发件箱模式。

### 实现步骤

1. **数据库表** — 照搬源项目的关系表 DDL

2. **Mapper** — `relation/mapper/RelationMapper.java`

3. **Service** — 与源项目方法签名和业务逻辑一致

4. **Controller** — 接口路径和逻辑与源项目一致

### 自测方法

```bash
# 1. 用户 A 关注用户 B
curl -X POST http://localhost:8080/api/relation/follow/<user_B_id> \
  -H "Authorization: Bearer <token_A>"
# 预期: 返回 200 + 关系状态

# 2. 检查计数器联动
curl http://localhost:8080/api/counter/follower/<user_B_id>
# 预期: 返回 1

# 3. 重复关注
# 预期: 返回 200（幂等）

# 4. 用户 B 查看粉丝列表
curl http://localhost:8080/api/relation/followers/<user_B_id> \
  -H "Authorization: Bearer <token_B>"
# 预期: 列表包含用户 A

# 5. 用户 A 取消关注
curl -X DELETE http://localhost:8080/api/relation/follow/<user_B_id> \
  -H "Authorization: Bearer <token_A>"
# 预期: 返回 200

# 6. 再次查看粉丝数
# 预期: 返回 0

# 7. 单测
mvn test -Dtest=RelationServiceTest
```

---

## 模块⑦ AI 模块

**依赖**: 模块②（鉴权）
**包路径**: `com.czltnb.zhi_xiang_backend.llm`

**学习方式**: 对照源项目 `llm/` 目录，逐文件手敲。重点理解 LLM 客户端抽象、流式 SSE 响应的实现、RAG 的设计思路。

### 实现步骤

1. **LLM 配置** — 照搬源项目的配置类

2. **LLM 客户端抽象** — 照搬源项目的接口和实现

3. **RAG 预留** — 照搬源项目的 RAG 接口设计

4. **Service 和 Controller** — 与源项目一致

### 自测方法

```bash
# 1. 配置好 LLM API Key（写入 application.yml，与源项目的配置项名一致）

# 2. 普通对话
curl -X POST http://localhost:8080/api/llm/chat \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"message":"你好，请用中文回答","sessionId":"test-001"}'
# 预期: 返回 AI 回复

# 3. 带上下文的多轮对话
curl -X POST http://localhost:8080/api/llm/chat \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"message":"我刚才说了什么？","sessionId":"test-001"}'
# 预期: AI 能记住上一步对话

# 4. 流式对话（SSE），确认 Server-Sent Events 正常推送

# 5. 未登录调用
# 预期: 401

# 6. 单测
mvn test -Dtest=LlmServiceTest
```

---

## 模块⑧ 搜索模块

**依赖**: 有可搜索的内容（用户、帖子等）
**包路径**: `com.czltnb.zhi_xiang_backend.search`

**学习方式**: 对照源项目 `search/` 目录，逐文件手敲。重点理解搜索引擎的索引同步发件箱模式。

### 实现步骤

1. **搜索引擎集成** — 与源项目选择的搜索引擎一致（ES / MySQL 全文索引）

2. **索引管理** — 照搬源项目的索引器实现

3. **发件箱模式** — 照搬源项目的事件同步机制

4. **Service 和 Controller** — 接口路径和逻辑与源项目一致

### 自测方法

```bash
# 1. 确认搜索引擎已就绪
# 2. 确保有几条用户数据、帖子数据可搜索

# 3. 搜索用户
curl "http://localhost:8080/api/search/users?q=测试&page=1&size=10" \
  -H "Authorization: Bearer <token>"
# 预期: 返回匹配的用户列表 + 分页信息

# 4. 搜索帖子
curl "http://localhost:8080/api/search/posts?q=关键词&page=1&size=10" \
  -H "Authorization: Bearer <token>"
# 预期: 返回匹配的帖子列表 + 分页信息

# 5. 搜索空关键词
# 预期: 返回空结果或最新列表

# 6. 搜索不存在的关键词
# 预期: 返回空列表

# 7. 单测
mvn test -Dtest=SearchServiceTest
```

---

## 附录 A：快速启动命令汇总

```bash
# 启动项目
mvn spring-boot:run

# 运行全部单元测试
mvn test

# 运行单个测试类
mvn test -Dtest=UserServiceTest

# 编译（跳过测试）
mvn clean compile -DskipTests

# 打包
mvn clean package -DskipTests
```

## 附录 B：推荐测试工具

| 工具 | 用途 |
|------|------|
| IntelliJ HTTP Client (`.http` 文件) | 项目内直接写、分享 API 请求 |
| Postman / Apifox | 可视化调试 REST API |
| `curl` | 快速命令行测试 |
| H2 内存数据库 | 单元测试时替代 MySQL，避免依赖外部数据库 |

## 附录 C：模块目录结构总览

```
src/main/java/com/czltnb/zhi_xiang_backend/
├── ZhiXiangBackendApplication.java          启动类
├── common/                                  公共模块
│   ├── web/                                 统一返回体、全局异常处理
│   ├── exception/                           业务异常、错误码
│   └── util/                                工具类
├── config/                                  全局配置（跨模块）
├── cache/                                   缓存
├── user/                                    模块① 用户模块
├── auth/                                    模块② 认证登录模块
├── storage/                                 模块③ 对象存储模块
├── profile/                                 模块④ 用户资料模块
├── counter/                                 模块⑤ 计数模块
├── relation/                                模块⑥ 用户关系模块
├── llm/                                     模块⑦ AI 模块
├── search/                                  模块⑧ 搜索模块
└── knowpost/                                [附加] 知识帖内容模块
```

---

## 附录 D：学习建议

- **先读后写**：每次写一个文件前，先把源项目对应文件完整读一遍，理解每段代码在做什么
- **逐行手打**：不要复制粘贴，手打才能帮你"看见"细节
- **写注释**：在你不太理解的地方加中文注释，后续复习时这就是你当时的思路锚点
- **提交粒度**：每完成一个子功能（例如"完成 UserMapper"）就 `git commit` 一次，commit message 写你学到了什么
- **回看对照**：写完一个模块后用 diff 工具对比你的代码和源项目之间的差异，差异就是你的理解盲区

> 每完成一个模块，花5分钟跑一遍"自测方法"中的全部测试案例，确保没有破坏之前已完成的功能。遇到报错时先修当前模块的问题再进下一个。
