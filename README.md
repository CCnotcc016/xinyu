# 心语 · AI 情绪日记与匿名树洞

> 一个 Java + H5 的全栈项目：写情绪日记，AI 给你共情回复、情绪评分与趋势分析；也可以匿名发到「树洞」，获得点赞、评论与陪伴感。

核心不是做心理诊断，而是：**记录情绪 + 被理解 + 看见趋势**。

当前版本：**v2.2.2.2048** · 变更见 [🔔 版本号与更新说明](#-版本号与更新说明)

> **📜 著作权声明**：本项目由 **CCnotcc016** 独立开发完成，**版权所有 © CCnotcc016**。
> **仅供交流学习使用**，任何形式的**商业售卖、付费转售、二次销售**均需事先征询作者本人并获得书面许可。
> 联系方式：`CCnotcc016`（2947208937@qq.com）。完整条款见 [LICENSE](./LICENSE)。

---

## ✨ 项目定位

| 角色 | 能力 |
| --- | --- |
| 普通用户 | 写日记、AI 共情回复、情绪趋势、发树洞、评论点赞、举报、设置（头像/昵称/手机号/密码） |
| 管理员 | 用户管理、日记管理、树洞管理、评论管理、举报处理、危机预警跟进 |

**先不做**：即时聊天、好友系统、AI 心理诊断、复杂推荐。

**危机兜底**：内容命中自残 / 自杀关键词时，前端弹关怀提示、后端给管理员推预警（含注册手机号），
不做诊断、不给极端建议，只做「被看见 + 联系得上」。

---

## 🧱 技术选型

- 后端：JDK 17 · Spring Boot 3.x · Spring Security + JWT · MyBatis-Plus · MySQL 8 · Redis · SSE（AI 流式回复）· WebClient（调用大模型）
- 前端：Vue3 + Vite · Vant4 · Pinia · Axios · ECharts · Vue Router
- AI：DeepSeek / 通义 / OpenAI 兼容接口（Key 只在后端）
- 部署：Nginx + Docker Compose

---

## 📁 目录结构

```
心语/
├── backend/                     # Spring Boot 后端
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/xinyu/
│       │   ├── XinyuApplication.java
│       │   ├── ai/              # AI 流式分析服务
│       │   ├── common/          # 统一返回/异常/分页
│       │   ├── config/          # 安全、Redis、MyBatis-Plus、属性配置
│       │   ├── controller/      # 用户/日记/树洞/举报接口
│       │   │   └── admin/       # 后台管理接口
│       │   ├── dto/             # 请求/响应对象
│       │   ├── entity/          # 数据库实体
│       │   ├── mapper/          # MyBatis-Plus Mapper
│       │   ├── security/        # JWT 认证
│       │   └── service/         # 业务逻辑
│       └── resources/
│           ├── application*.yml # 配置（dev/prod）
│           ├── db/              # schema.sql + data.sql + upgrade-v2.sql + upgrade-v2.2.sql
│           ├── forbidden-names.txt   # 昵称/用户名违禁词
│           └── sensitive-words.txt
├── frontend/                    # Vue3 + Vant H5 前端
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── api/                 # 接口封装
│       ├── components/          # 公共组件（底部导航、更新说明弹窗、关怀弹窗）
│       ├── composables/         # 组合式函数（更新说明状态、深浅色主题）
│       ├── config/              # 版本号 + 更新说明内容（changelog.js）
│       ├── router/              # 路由
│       ├── stores/              # Pinia 状态
│       ├── utils/               # Axios 封装
│       └── views/               # 页面（Settings 设置页、含 admin/）
├── nginx/nginx.conf             # 宿主机部署示例
├── scripts/                     # 本地启停 + 接口冒烟测试脚本（测试跑完自动清理账号）
│   ├── run-dev.sh               #   启动后端（自动 source 工具链 + 加载 .env）
│   ├── serve-dist.sh            #   构建前端并用 Nginx 静态托管（免 sudo，默认 8088）
│   ├── smoke-test.sh            #   用户端 75 项断言
│   └── admin-sse-test.sh        #   后台 + SSE 40 项断言
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## 🚀 快速开始（本地开发）

### 环境要求

- JDK 17、Maven 3.8+
- MySQL 8、Redis
- Node.js 18+

### 1. 初始化数据库

开发环境后端启动时会自动执行 `db/schema.sql`、`db/upgrade-*.sql`（幂等增量脚本）和 `db/data.sql`（只含常用标签，不含任何账号）。
库名由连接串决定（`application-dev.yml` 里默认 `xinyu`，可用环境变量覆盖），建表脚本本身不写死库名，库不存在时 `createDatabaseIfNotExist=true` 会自动创建。

```sql
-- 手动创建（可选）
CREATE DATABASE xinyu DEFAULT CHARACTER SET utf8mb4;
```

### 2. 配置密钥（.env）

**仓库里不含任何真实 Key**：`application.yml` 里只写 `${AI_API_KEY:}`，真实值统一放在项目根目录的 `.env`（已被 `.gitignore` 忽略）：

```bash
cp .env.example .env
# 然后编辑 .env，至少填入 AI_API_KEY
```

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `AI_API_KEY` | 否 | 留空时 AI 自动降级为离线 Mock 陪伴回复，功能仍可演示 |
| `AI_BASE_URL` | 否 | 默认 `https://api.deepseek.com` |
| `AI_MODEL` | 否 | 默认 `deepseek-chat` |
| `JWT_SECRET` | 生产必填 | `openssl rand -hex 32` 生成 |
| `MYSQL_PASSWORD` | Docker 部署用 | MySQL root 密码 |

> 换机器或轮换密钥时只改 `.env`，代码一行都不用动。

### 3. 启动后端

```bash
bash scripts/run-dev.sh
```

脚本会自动加载项目根目录的 `.env`（若存在）再启动后端。
也可以手动启动（`cd backend && mvn spring-boot:run`），但那样不会读取 `.env`。

后端默认运行在 `http://localhost:8080`。启动日志会明确告诉你当前用的是真实 AI 还是 Mock：

```
AI 已就绪：provider=deepseek model=deepseek-flash（API Key 已加载，长度 35）
未配置 AI_API_KEY，AI 情绪分析将使用离线 Mock 陪伴回复（不会调用外部大模型）
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，已配置代理将 `/api` 转发到后端。

> **手机预览**：`vite.config.js` 里已开 `server.host: true`，手机与电脑连同一个 Wi-Fi，
> 访问 `http://<电脑局域网 IP>:5173` 即可（查 IP：`ipconfig getifaddr en0`）。
> 注意 Vite 5 默认只绑 IPv6，所以电脑上用 `localhost` 而不是 `127.0.0.1`。

#### 另一种跑法：构建 + Nginx 静态托管（更接近上线）

`npm run dev` 是开发服务器（浏览器实时编译 `.vue`，改完即刷新），只在开发时用；
要看接近线上的效果，就用构建产物 + Nginx：

```bash
bash scripts/serve-dist.sh            # 构建前端 + Nginx 起在 8088，并反代 /api、/uploads
bash scripts/serve-dist.sh --no-build # 跳过构建，直接用现有 dist
bash scripts/serve-dist.sh stop       # 停止
PORT=9000 bash scripts/serve-dist.sh  # 换端口
PORT=80 bash scripts/serve-dist.sh --no-build  # 80 端口（手机直接访问 http://<IP>）
```

> 每个端口各有一份配置与 pid 文件（`~/.xinyu-nginx/nginx-<端口>.conf` / `nginx-<端口>.pid`），
> 所以 8088 和 80 可以同时跑、互不干扰；停止时带上同一个 `PORT` 即可。

| | `npm run dev`（开发） | `scripts/serve-dist.sh`（静态托管） |
| --- | --- | --- |
| 前端代码 | 浏览器实时编译 | 先 `npm run build` 成 `dist/` 静态文件 |
| 谁在发文件 | Vite（Node 进程） | Nginx |
| 地址 | `http://<IP>:5173` | `http://<IP>:8088` |
| 关掉 npm 进程后 | 网页打不开 | 照常访问 |
| 改代码后 | 自动热更新 | 需重新构建 |
| gzip / 缓存头 | 无 | 有（`/assets/` 30 天 immutable，`index.html` no-cache） |

脚本生成的配置与日志都在 `~/.xinyu-nginx/`，并且**按端口分开存放**（`nginx-<端口>.conf`、`nginx-<端口>.pid`），
所以 8088 和 80 可以同时跑、互不干扰；停止时带上同一个 `PORT` 即可。
默认用 8088 而不是标准的 80，是因为小于 1024 的端口在部分系统上需要特权（本机实测免 `sudo` 可以绑定，
若终端提示 `permission denied`，就在启动和停止命令前加 `sudo`）。
用 80 端口：`PORT=80 bash scripts/serve-dist.sh`，停止：`PORT=80 bash scripts/serve-dist.sh stop`。
后端 `8080` 只是 API 端口，不提供网页，用浏览器直接打开只会看到 `{"code":401,"message":"未登录或登录已过期"}`。

### 5. 创建管理员并登录

**仓库里不含任何默认账号，也没有任何默认密码**，请自己创建第一个管理员：

```bash
bash scripts/init-admin.sh                       # 交互式：输入用户名 + 密码（不回显）
ADMIN_USER=admin ADMIN_PASS='你的强密码' bash scripts/init-admin.sh        # 或非交互式
ADMIN_USER=admin ADMIN_PASS='你的强密码' ADMIN_PHONE=13800000000 bash scripts/init-admin.sh
```

脚本会用 BCrypt 生成密码哈希并写入数据库（账号已存在则更新密码和角色），默认连
`127.0.0.1:3306/xinyu`，可用 `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USER` /
`MYSQL_PASSWORD` / `MYSQL_DB` 覆盖；没装 mysql 客户端时会自动改用
`docker compose exec mysql`，也可以用 `MYSQL_BIN=/path/to/mysql` 指定路径。
注意它需要 `user` 表已经存在，请先启动一次后端（dev profile 会自动建表）再执行。

- 管理员：用上面创建出来的账号登录
- 普通用户：在前端「注册」页面注册后登录

> v2.0 起注册必须填手机号并完成短信验证码，发验证码前要先过图形人机验证。
> 本机没有接短信服务商，开发环境（`dev` profile）会把图形验证码答案和短信验证码
> 一起放在接口响应里（`xinyu.captcha.echo-code` / `xinyu.sms.echo-code`），
> 所以本地演示时验证码直接看响应或后端日志的「【模拟短信】」即可。
> **上线前务必把这两个开关关掉。**

> Redis 只用于限流（`RateLimitService`），未启动时会自动降级放行，本地开发可以不装。

### 6. 跑一遍接口自检（可选）

```bash
bash scripts/smoke-test.sh        # 用户端 75 项：注册(含手机号校验)/登录 / 日记 / 树洞 / 点赞 / 评论(含回复) / 举报 / 设置
bash scripts/admin-sse-test.sh    # 40 项：后台管理(含按账号查日记、删除帖子) + SSE 流式回复 + 危机预警
```

两个脚本都依赖 `curl` 和 `python3`，默认访问 `http://127.0.0.1:8080/api`，
可用 `API=...` 覆盖；管理员密码必须自己传入（仓库里不存默认口令）：

```bash
API=http://127.0.0.1:8080/api ADMIN_USER=admin ADMIN_PASS='你的密码' bash scripts/smoke-test.sh
```

脚本会自己收尾：注册过的测试账号记录在 `CLEANUP_IDS` 里，通过 `trap cleanup EXIT`
在结束时（正常跑完、断言失败、甚至中途 `Ctrl+C` / `kill`）调用管理员接口把它们删掉，
不会在数据库里留下垃圾数据。注意这一点依赖管理员账号可用，所以 `ADMIN_USER` /
`ADMIN_PASS` 必须是未禁用的管理员；管理员登不上时会打印一行跳过提示。

---

## ✅ 验证记录

如下为开发过程中的实际验证结果（macOS 12 / x86_64，全程未使用 `sudo`，工具链装在用户目录下）：

### 验证结果

| 项目 | 结果 |
| --- | --- |
| 后端编译打包（JDK 17 + Maven） | ✅ BUILD SUCCESS |
| 后端启动 + 自动建表（10 张表，含 `crisis_alert`） | ✅ 正常 |
| 用户端接口用例（75 条） | ✅ 75 / 75 |
| 后台 + SSE 用例（40 条） | ✅ 40 / 40 |
| 前端 `npm run build` | ✅ 构建成功 |
| Nginx 静态托管（`scripts/serve-dist.sh`，8088）：SPA 回落 / gzip 压缩（390 KB → 145 KB）/ `index.html` no-cache | ✅ 正常 |
| Nginx 反代：`/api` 登录与鉴权接口、SSE 流式回复、`/uploads` 头像 | ✅ 正常 |
| 浏览器实测：登录 / 写日记 / 日记详情 / AI SSE 流式回复 | ✅ 正常 |
| 浏览器实测：趋势图（日 / 周 / 月）、情绪分布饼图 | ✅ 正常渲染 |
| 浏览器实测：树洞发帖 / 点赞 / 评论 / 举报 | ✅ 正常 |
| 浏览器实测：后台 6 个管理页面 + 举报处理 | ✅ 正常 |
| 浏览器实测：注册页（图形验证码 → 短信验证码 → 手机号必填） | ✅ 正常 |
| 浏览器实测：设置页（头像 / 昵称 / 手机号 / 密码 / 深色模式） | ✅ 正常 |
| 浏览器实测：树洞写「想自残」→ 关怀弹窗，后台预警页出现记录并可标记跟进 | ✅ 正常 |
| 缺陷修复复验：日记 / 用户更新后 `updated_at` 正常刷新（`updated_at > created_at`） | ✅ 正常 |
| 缺陷修复复验：打包产物含 `multipart` 上限，3 MB 图片返回友好提示（非 413） | ✅ 正常 |
| 界面实测：关怀弹窗改为液态玻璃（3 个流动光斑 + 旋转高光 + 30px 背景模糊） | ✅ 正常（深 / 浅色均验证） |
| 界面实测：admin 登录后首页顶部出现危机预警横幅，可关闭、可跳转预警页 | ✅ 正常 |
| 界面实测：横幅关闭状态在同标签页刷新后保持（`sessionStorage` 记住预警 ID） | ✅ 正常 |
| 界面实测：普通用户首页不出现预警横幅，`/api/admin/crisis-alerts` 返回 403 | ✅ 正常 |
| 界面实测：PC 适配——560 / 880 / 1040px 三档外壳，移动端卡片列表 + PC 栅格 | ✅ 正常 |
| 界面实测：PC 上底部抽屉居中且不横跨整屏（1440px：宽 1040，左右各留 200） | ✅ 正常 |
| 界面实测：Nginx（8088）产物包含液态玻璃与 PC 适配样式，深链接回落正常 | ✅ 正常 |
| 数据库升级：`upgrade-v2.2.sql` 幂等执行，`post_comment.parent_id` + `idx_parent` 就位（重复执行无副作用） | ✅ 正常 |
| 界面实测：树洞评论「回复」按钮 → 输入框上方回复条 → 发送后渲染「回复 @昵称：」（移动端 + PC 1440px 均验证） | ✅ 正常 |
| 界面实测：设置页「显示模式」自选浅色 / 深色 / 跟随系统，当前项标注「当前」，刷新后保持 | ✅ 正常 |
| 界面实测：「跟随系统」模式下切换系统配色，`<html>` 主题类与 `color-scheme` 同步变化 | ✅ 正常 |
| 界面实测：日记详情页「删除这篇日记」二次确认 → 提示「已删除」并回到列表 | ✅ 正常 |
| 界面实测：重新打开一篇历史危机日记，关怀弹窗同样出现 | ✅ 正常 |
| 界面实测：后台「日记」先选账号（支持搜索、可切回「全部用户」）再「查看」，详情弹窗标出危机关键词 | ✅ 正常 |
| 缺陷修复复验：新增的评论会立刻显示时间（`createdAt` 由后端赋值，不再出现空白时间） | ✅ 正常 |
| 界面实测：后台「帖子」的「删除」（移动端 + nginx 产物）——取消不改动、确认后行消失并提示「已删除」 | ✅ 正常 |
| 数据实测：管理员删除帖子后，`treehole_post` / `post_comment` / `post_like` 对应行数均为 0，其余帖子不受影响 | ✅ 正常 |
| 界面实测：浅色模式下错误提示 Toast 文字跟随主题（`rgb(50,50,51)`，不再白底白字），深色模式为 `rgb(245,245,245)` | ✅ 正常 |
| 界面实测：Nginx 80 端口（`PORT=80 bash scripts/serve-dist.sh`）与 8088 并存，SPA 深链接回落、`/api` 反代均正常 | ✅ 正常 |

### 本机验证时的几个坑（已在代码中处理）

1. **`createDatabaseIfNotExist=true` 是必需的**：数据源会先连接 `xinyu` 库，
   之后才执行 `schema.sql`，没有该参数在全新机器上会启动失败。已在
   `application-dev.yml` / `application-prod.yml` 中默认加上。
2. **Vite 5 只监听 IPv6**，请用 `http://localhost:5173` 而不是 `http://127.0.0.1:5173`。
3. **SSE 与 Spring Security 6**：SSE 异步完成后的 `ASYNC` 分发不在 `SecurityContext`
   内，若不放开会抛 `AccessDeniedException` 并打印「响应已提交」错误日志。
   已在 `SecurityConfig` 中放行 `DispatcherType.ASYNC` / `DispatcherType.ERROR`。
4. **AI 未配置时的离线陪伴（Mock 模式）**：`AI_API_KEY` 为占位符或未设置时，后端会自动
   进入「离线 Mock 陪伴」模式——依然以 SSE 流式返回一段温暖的共情回复，并给出情绪分
   （1-10）、情绪标签和小建议，`ai_status` 置为 `DONE`，保证未申请大模型 Key 时演示、
   答辩、课程验收都能完整跑通。配置了真实 `AI_API_KEY` 后会自动走真实大模型。
5. **危机干预不依赖 AI**：日记含「自杀 / 自残 / 想死」等关键词时，后端直接短路返回
   危机干预文案（心理援助热线 12356、紧急 120/110），`ai_status` 置为 `CRISIS`，
   无需配置 AI Key 也能验证。
6. **树洞的危机表达不被敏感词拦截**：树洞发帖只拦截违规/广告/辱骂类敏感词；情绪与
   危机类表达（如「活不下去了」）不会被拦截，发布成功后会返回 `crisisSupport=true`，
   前端弹出陪伴与求助引导，避免用户在低谷时被「内容违规」二次伤害。
7. **本机没有短信服务商**：开发环境用 `xinyu.sms.echo-code=true` 把验证码回显在响应里，
   并打一条「【模拟短信】」日志。注册流程因此可以在本机完整走通，也方便自动化脚本断言。
8. **头像上传目录**：默认 `./uploads`（相对后端工作目录），可用 `XINYU_UPLOAD_DIR` 覆盖；
   Docker 里挂成数据卷，Nginx 反代 `/uploads/`，开发时由 Vite 代理转发。
9. **Nginx 默认只放行 1 MB 请求体**：头像最大 2 MB，不放宽的话会直接返回 413（还没到后端）。
   三份配置都已加 `client_max_body_size 5m`：`nginx/nginx.conf`、`frontend/nginx.conf`
   和 `scripts/serve-dist.sh` 生成的本地配置。
10. **上传上限要写在基础配置里**：`spring.servlet.multipart` 只写在 `application-dev.yml`
    时，`prod` 会退回 Spring 默认的 1 MB，大于 1 MB 的头像在生产环境直接被拒。现统一放在
    `application.yml`（`max-file-size: 2MB` / `max-request-size: 4MB`），dev / prod 共用；
    超出时由 `GlobalExceptionHandler` 转成友好提示（如「图片不能超过 2MB」）。
11. **MyBatis-Plus 会把旧的 `updated_at` 写回 UPDATE 语句**：实体不设置 `createdAt` /
    `updatedAt`，靠数据库默认值；但 `updateById` 会把「查出来的旧值」拼进
    `SET updated_at = ?`，从而冲掉数据库的 `ON UPDATE CURRENT_TIMESTAMP`，表现为
    「改了内容，时间戳不动」。`Diary` / `User` 的 `updatedAt` 已加
    `@TableField(value = "updated_at", updateStrategy = FieldStrategy.NEVER)`，
    把该列从 UPDATE 语句里排除，交给 MySQL 维护。
    （该策略只在字段值真的变化时才刷新时间戳，空更新不会动，写自测用例时要注意这一点。）
12. **Vant 遮罩没法在 `<style scoped>` 里改**：`van-overlay` 用的是 Vant 自己的
    `useScopeId()`，作用域 ID 跟调用方不一样，所以 `.crisis-overlay` 只能写成
    `main.css` 里的全局样式。好在 `main.js` 是在 `vant/lib/index.css` **之后**引入
    `main.css` 的，同优先级（都是 0,1,0）时后引入者胜。
13. **弹窗里不要再套一层 `backdrop-filter`**：Safari 对嵌套的 `backdrop-filter`
    支持不稳定，外层常常拿不到模糊，所以关怀弹窗本身设 `backdrop-filter: none`，
    模糊交给内层的 `.xinyu-liquid__glass` 去做。
14. **`van-popup` 的 `overflow-y: auto` 会裁掉子元素阴影**，所以液态弹窗的阴影写在
    `van-popup` 自己身上；另外 `.van-popup--round` 半径是 0,2,0，想覆盖需要写成
    `.crisis-popup.crisis-popup`（0,3,0）。
15. **宽屏上居中底部抽屉，必须显式给 `right: 0`**：Vant 的 `.van-popup--bottom`
    只设了 `left: 0` 和 `width: 100%`，`right` 留在 `auto`。这种「过约束」情况下
    浏览器会把 `auto` 外边距算成 0，`margin: auto` 完全不起作用，弹窗照样贴左边
    （实测 1440px 视口下 `left: 0`、宽 1040）。补上 `right: 0` 之后宽度方程才有
    剩余空间分给两侧 `auto` 外边距，抽屉才会居中；手机端因为媒体查询不生效而不受影响。
16. **`grid` + 子元素自带的 `margin` 不会塌陷**：`.post` 原本有 `margin: 8px 0`，
    一放进 PC 栅格就会和 `gap` 叠加成双倍间距。用组件内的
    `.xinyu-cards .post { margin: 0 }`（0,3,0）盖掉它——全局的 `.xinyu-cards > *`
    只有 0,1,0，打不过带 `[data-v]` 的 scoped 选择器（0,2,0）。
17. **绝对定位的液态光斑要让父级定尺寸**：`.xinyu-liquid__glass` 上原本写了
    `height: 100%`，但它父级高度是 `auto`，百分比高度会解析成 `auto`，结果盒子被压扁。
    去掉之后由玻璃层自己撑开外层，绝对定位的光斑再跟着铺满即可。
18. **靠数据库默认值填充的时间字段，insert 之后实体里是 `null`**：`post_comment.created_at`
    由 MySQL 的 `DEFAULT CURRENT_TIMESTAMP` 维护，`insert` 只回填自增主键，
    所以返回给前端的 `CommentVO.createdAt` 是空的；评论列表会重新拉取所以看不出来，
    但发完评论后前端是直接 `push` 返回值的，于是新评论的时间一片空白。
    `TreeholeService.addComment` 里显式 `setCreatedAt(LocalDateTime.now())`
    （库、JVM、Jackson 都是 `Asia/Shanghai`，不会串时区）。
    凡是要把 insert 结果直接渲染到页面的地方，都要注意这个坑。

---

## 🤖 AI 配置

后端通过 WebClient 调用 OpenAI 兼容接口，默认指向 DeepSeek。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_API_KEY` | 空 | **无内置默认值**；未配置时自动进入离线 Mock 陪伴模式 |
| `AI_BASE_URL` | `https://api.deepseek.com` | 接口根地址 |
| `AI_MODEL` | `deepseek-chat` | 模型名 |
| `JWT_SECRET` | 开发环境占位值 | 生产环境必填，缺失则启动失败 |

> API Key 只存在于后端的环境变量（`.env`）中：既不写进 `application.yml`，
> 也不会出现在前端代码里。前端只请求自己的 `/api`，拿不到 Key。

### 密钥该放哪里

| 做法 | 是否推荐 |
| --- | --- |
| 写死在 `application.yml` | ❌ 随代码泄露，且会一起被打进 jar / Docker 镜像 |
| 放在 `.env`（已 gitignore），启动时注入环境变量 | ✅ 本项目采用 |
| 由 CI 或服务器注入环境变量 / 密钥管理服务 | ✅ 上线推荐 |
| 写进前端代码 | ❌ 前端源码用户可见，等于公开 |

### 轮换步骤（Key 进过 git 或发给过别人时必做）

1. 去大模型控制台**吊销旧 Key** 并新建一个 —— 只改文件不吊销是没有用的，旧 Key 已经泄露
2. 更新 `.env` 里的 `AI_API_KEY`
3. 重启后端，看到启动日志 `AI 已就绪` 即生效

### 上线前密钥检查清单

- [ ] `AI_API_KEY` 只存在于服务器环境变量 / `.env`，仓库里搜不到
- [ ] `JWT_SECRET` 用 `openssl rand -hex 32` 生成，不是示例值
- [ ] `MYSQL_PASSWORD` 不是 `root`
- [ ] `.env` 在 `.gitignore` 中（本项目默认已包含）
- [ ] 确认 `git log -S "sk-"` 搜不到历史 Key；提交过就必须吊销重建

> 生产 profile 会强制校验密钥，配错时进程**启动即失败**并给出中文原因，不会带病上线：
>
> | 情况 | 启动日志 |
> | --- | --- |
> | 没设 `JWT_SECRET` | `未配置 jwt 密钥：请设置环境变量 JWT_SECRET（生成方式：openssl rand -hex 32）…` |
> | `JWT_SECRET` 少于 32 字节 | `jwt 密钥过短：HS256 要求至少 32 字节，当前 N 字节…` |
> | 没设 `AI_API_KEY` | 不报错，降级为离线 Mock：`未配置 AI_API_KEY，AI 情绪分析将使用离线 Mock 陪伴回复` |

---

## 📡 核心接口

### 认证

- `GET /api/auth/captcha` 获取图形验证码（返回 `captchaId` + PNG 的 data URL）
- `POST /api/auth/sms-code` 发送短信验证码，**必须携带图形验证码**
  （`{ "phone": "...", "captchaId": "...", "captchaCode": "..." }`）
- `POST /api/auth/register` 注册，**必须填手机号 + 短信验证码**
  （`username` / `password` / `phone` / `smsCode`，`nickname` 可选）
- `POST /api/auth/login` 登录（返回 JWT）

### 个人中心（「我的 → 设置」）

- `GET /api/user/me` 当前用户信息
- `PUT /api/user/me` 修改昵称（`{ "nickname": "..." }`，1-20 个字符）
- `PUT /api/user/password` 修改密码（`{ "oldPassword": "...", "newPassword": "..." }`）
- `PUT /api/user/phone` 绑定 / 更换手机号
  （`{ "phone": "...", "smsCode": "...", "password": "..." }`，首次绑定可省 `password`，换号必须带）
- `POST /api/user/avatar` 上传头像（`multipart/form-data`，字段名 `file`，jpg/png/webp/gif，≤ 2MB）

> `username` 是登录凭据，不支持修改；显示名（昵称）、密码、手机号、头像都在「设置」里改。
> `PUT /api/user/me` 只更新 `nickname` 一列，`updated_at` 交给 MySQL 的
> `ON UPDATE CURRENT_TIMESTAMP` 自动维护。
> 昵称和用户名都会过一遍违禁词校验（详见下方「安全与隐私」一节）。

### 日记

- `POST /api/diaries` 创建日记
- `GET /api/diaries?page=&size=&start=&end=` 分页列表
- `GET /api/diaries/{id}` 详情
- `PUT /api/diaries/{id}` 更新
- `DELETE /api/diaries/{id}` 删除
- `GET /api/diaries/{id}/ai-reply/stream` **SSE 流式 AI 回复**
- `GET /api/diaries/stats/trend?range=week` 情绪趋势（day/week/month）
- `GET /api/diaries/stats/distribution` 情绪分布

### 树洞

- `GET /api/treehole/posts` 帖子列表
- `POST /api/treehole/posts` 发帖
- `GET /api/treehole/posts/{id}` 帖子详情
- `POST /api/treehole/posts/{id}/like` 点赞
- `DELETE /api/treehole/posts/{id}/like` 取消点赞
- `GET /api/treehole/posts/{id}/comments` 评论列表（回复会带上 `parentId` 与 `replyToNickname`）
- `POST /api/treehole/posts/{id}/comments` 发评论 / 回复（可选 `parentId`：填了就是回复某条评论，
  被回复的评论必须存在、未删除且属于同一个帖子，否则返回 `3002 评论不存在`）
- `POST /api/reports` 举报

> **评论回复**：只做两层 —— 回复同样排在评论列表里，靠 `replyToNickname` 在前端渲染成
> 「回复 @昵称：内容」。被回复的评论可能不在当前分页里，所以后端会额外批量查一次父评论
> （`parents()`），不会因为翻页而丢掉「回复谁」这个信息。

> **关怀弹窗**：发帖 / 评论命中自残、自杀等关键词时，接口照常成功，
> 但响应里会带 `crisisSupport: true`，前端立刻弹出一张磨砂玻璃的「我们关心你」弹窗
> （心理援助热线 12356、紧急情况 120/110），同时后端写入一条 `crisis_alert`
> 推给管理员。日记同理（`GET /api/diaries/{id}` 也会返回 `crisisSupport`，
> 所以重新打开一篇旧日记仍会看到关怀提示）。

### 后台（需 ADMIN 角色）

- `GET /api/admin/users` 用户列表
- `PUT /api/admin/users/{id}/status` 启用/禁用用户
- `DELETE /api/admin/users/{id}` 删除用户（级联清理该用户的日记、标签关联、情绪统计、帖子及其评论/点赞、自己发的评论/点赞、相关举报）
- `GET /api/admin/diaries?page=&size=&userId=` 日记管理（`userId` 可选：传了就只看该账号的日记，
  返回 `AdminDiaryVO`，带作者昵称/用户名与 `crisisSupport` 标记）/ `DELETE /api/admin/diaries/{id}` 删除日记
- `GET /api/admin/posts` / `PUT /api/admin/posts/{id}/status` 帖子管理
- `DELETE /api/admin/posts/{id}` 删除帖子（连同该帖评论、点赞一起清理；指向它的举报与危机预警保留，举报页会显示「(已删除)」，预警页仍可联系当事人）
- `GET /api/admin/comments` / `PUT /api/admin/comments/{id}/status` 评论管理
- `GET /api/admin/reports` / `PUT /api/admin/reports/{id}` 举报处理
- `GET /api/admin/crisis-alerts?status=&page=&size=` 危机预警列表
- `GET /api/admin/crisis-alerts/pending-count` 待跟进预警数（后台导航红点）
- `PUT /api/admin/crisis-alerts/{id}` 标记处理（`{ "status": "HANDLED", "remark": "..." }`）

> **危机预警与手机号**：`CrisisAlertVO.phone` 返回的是**明文手机号**，
> 这是刻意的例外 —— 管理员需要能在紧急情况下联系到人，所以该接口只对
> `ROLE_ADMIN` 开放，后台页面也明确标注「仅用于危机干预」。其他所有接口一律打码。

> **管理员自我保护**：`PUT /users/{id}/status`（禁用）与 `DELETE /users/{id}`
> 会拒绝操作当前登录账号，分别返回 `1005 不能禁用当前登录的账号`、
> `1006 不能删除当前登录的账号`；同时始终保留至少一个可用管理员（`1007`）。
> 因此「把自己禁用掉导致后台进不去」这种情况不会再发生。

---

## 🗄️ 数据库核心表

`user`、`diary`、`tag`、`diary_tag`、`treehole_post`、`post_comment`、`post_like`、`report`、`emotion_daily_stat`、`crisis_alert`（详见 `backend/src/main/resources/db/schema.sql`）。

v2.0 新增：

- `user.phone`（`varchar(20)`，唯一索引 `uk_phone`）—— 注册必填，既是账号找回凭据，也是危机干预时的联系方式
- `crisis_alert` —— 危机预警：`user_id`、`target_type`（DIARY / POST / COMMENT）、`target_id`、
  `keyword`、`content`（内容快照）、`status`（PENDING / HANDLED）、`remark`、`handled_at`、`created_at`

v2.2 新增：

- `post_comment.parent_id`（`bigint`，`NULL` 表示直接评论帖子）+ 索引 `idx_parent` —— 树洞评论回复

老库升级执行一次（幂等，可重复跑）：

```bash
mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v2.sql
# v2.2 新增了 post_comment.parent_id，生产库（sql.init.mode: never）需要再补这一条
mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v2.2.sql
```

> 已注册但没手机号的老用户不会被踢下线，登录后前端会弹「绑定手机号」提示
> （`admin` 除外），可以「稍后再说」，但换号/找回需要先绑定。

> `upgrade-*.sql` 都用 `information_schema` 做过判断，重复执行是空操作；开发环境由
> Spring 的 `spring.sql.init` 自动执行（`application-dev.yml` 里的 `schema-locations`），
> **生产环境 `sql.init.mode: never`，必须手动跑一遍上面的命令**。

---

## 💬 AI 提示词方向

系统角色：**温暖、克制的情绪陪伴者**，不做医学诊断、不给极端建议。根据日记输出：

```json
{
  "emotion_score": 7,
  "emotion_label": "开心",
  "suggestion": "今天也请记得早点休息"
}
```

随后输出共情回复正文（2-4 句）。

**危机干预**：检测到「自杀 / 自残 / 轻生」等关键词时，后端不依赖 AI，直接返回危机干预提示（联系信任的人、心理援助热线 12356，紧急拨打 120/110），并把 `ai_status` 置为 `CRISIS`（跳过 AI 调用，省 token 也避免模型把话题带偏）。同时写入一条 `crisis_alert`，管理员在「关怀预警」页面能看到发帖人注册时填的手机号并标记已跟进。

---

## 🛡️ 安全与隐私

- JWT 无状态认证，密码 BCrypt 加密
- 树洞默认匿名展示，不暴露作者身份
- 敏感词检测（`sensitive-words.txt`）
- **昵称 / 用户名违禁词**（`forbidden-names.txt`）：国家领导人相关称呼，
  以及涉黄涉赌涉毒词汇（`逼`、`屌`、`小穴`、`肉棒`、`阴茎`、`阴道`、`奶子`、`乳房` 等），
  命中返回 `1014 该昵称包含违规词，请换一个`
- **图形验证码**：自绘 4 位 PNG（`CaptchaService`，字符集去掉了易混的 `0O1Il`），
  答案只存在服务端内存，一次性消费；答错不消费（可以重试），答对即失效
- **短信验证码**：`SmsCodeService`，60 秒重发冷却、单条一次性、每日发送上限；
  **必须先通过图形验证码**才会真的发短信，挡住脚本刷短信
- Redis 限流（AI 日配额、发帖频率），Redis 不可用时自动放行
- 举报 → 管理员审核 → 下架／删除／封禁
- 密钥只从环境变量注入，仓库内无任何真实 Key（见 [🤖 AI 配置](#-ai-配置)）
- 曾经出现在代码或聊天记录里的 Key，上线前必须吊销重建

### 手机号的处理（隐私权衡，答辩可讲）

| 场景 | 返回 | 说明 |
| --- | --- | --- |
| 「我的」页面 / `GET /api/user/me` | `138****8000` | 一律打码 |
| 后台用户列表 | 打码 | 管理员日常也不看全号 |
| 后台危机预警 `/api/admin/crisis-alerts` | **明文** | 唯一的例外，仅供管理员在紧急情况下联系当事人 |

开发期两个 `echo-code` 开关默认只在 `dev` profile 打开（`application-dev.yml`），
生产环境必须保持 `false`，否则验证码会跟着响应一起返回：

```yaml
xinyu:
  captcha:
    echo-code: false   # 把图形验证码答案一起返回（仅本地 / 自动化测试）
  sms:
    echo-code: false   # 把短信验证码一起返回（仅本地 / 自动化测试）
```

本机没接短信服务商时，验证码只写日志（`SmsCodeService.deliver()` 里的「【模拟短信】」）。
上线换成阿里云 / 腾讯云短信 SDK 时只改这个方法，限流、过期、一次性消费的逻辑都不用动。

---

## 🔔 版本号与更新说明

前端在启动时会弹出一次「更新说明」弹窗，只有**版本号变化**时才会再弹，
用浏览器 `localStorage`（key：`xinyu:notice:version`）记住用户已读的版本号。

发版时改两处即可，务必保持一致：

1. `frontend/src/config/changelog.js`
   - 递增 `APP_VERSION`
   - 在 `CHANGELOG` 数组**最前面**插入一条同版本号的记录（`version` / `date` / `items`）
2. `frontend/package.json` 的 `version` 改成同一个号

`APP_VERSION` 与 `CHANGELOG` 中的版本号对不上时弹窗不会出现（`currentNotice()` 返回 `null`），
所以两边一定要同步修改。

用户可以在「我的 → 更新说明」里随时手动重新打开弹窗。

后端版本号（`backend/pom.xml` 的 `<version>`）也跟着递增，这样启动日志里
`Starting XinyuApplication v2.2.2` 就能直接看出跑的是哪一版。

**「为什么没弹更新说明？」排查顺序：**

1. 浏览器里跑的仍是旧构建 —— `dist` 没重新构建 / nginx 没重启，页面的 `APP_VERSION` 还是旧号；
   改完 `changelog.js` 一定要 `bash scripts/serve-dist.sh`（或 `npm run build` + `--no-build`）。
2. `localStorage['xinyu:notice:version']` 已经等于当前 `APP_VERSION` —— 弹过一次并点过「我知道了」就不会再弹。
   `localStorage` 按「协议 + 域名 + 端口」隔离，所以 `http://<你的局域网IP>` 与
   `http://<你的局域网IP>:8088`、`http://127.0.0.1:5173` 各自记各自的已读版本。
3. `CHANGELOG` 里没有与 `APP_VERSION` 完全相同版本号的记录 —— `currentNotice()` 返回 `null`，弹窗直接跳过。

当前版本：**v2.2.2.2048**。

### v2.2.2 更新内容（与弹窗 `changelog.js` 保持一致）

1. 修复了报错弹窗（Toast / Notify）在浅色模式下的文字颜色问题
2. 修复了部分错误
3. 顺带补上：80 端口场景下 nginx 访问地址不再显示多余的 `:80`

### v2.2.1 更新内容（与弹窗 `changelog.js` 保持一致）

1. 修复了部分错误
2. 顺带补上：后台「帖子」新增「删除」，除了下架还能直接删掉（评论、点赞一并清理）

### v2.2 更新内容

1. 树洞评论支持「回复」了，点评论右边的回复按钮就能接话，回复对象会显示在内容前面
2. 显示模式改为自己选：浅色模式 / 深色模式 / 跟随系统，选择会记住，不再只能听系统的
3. 日记详情页新增「删除这篇日记」，不用再回列表左滑了
4. 后台「日记」可以按账号查看了：先选用户（支持搜索），再点「查看」看完整内容与 AI 回复
5. 日记里再次翻到带有自残、自杀字眼的内容时，同样会弹出关怀提示，不会因为当时点掉了就再也看不到
6. 后台查看这些日记时也会标出危机关键词，方便和「预警」页对着处理
7. 修复了一些错误，并对部分功能进行了优化

### v2.1 更新内容

1. 关怀弹窗换成液态玻璃质感（三层流动光斑 + 旋转高光）
2. 管理员登录后首页顶部出现危机预警横幅，有求必应地提醒有人需要被看见
3. PC 适配：560 / 880 / 1040px 三档外壳，手机上还是熟悉的那一套

### v2.0 更新内容（与弹窗 `changelog.js` 保持一致）

1. 安全组件全面升级：注册需手机号 + 短信验证码，发验证码前要通过图形人机验证，接口加限流
2. 手机号、密码、昵称、头像统一收进「设置」，支持修改密码、更换绑定手机号
3. 昵称审核：禁止使用国家领导人姓名及涉黄、涉赌、涉毒等违规昵称
4. 树洞与日记中出现自残、自杀等字眼时，会先弹出关怀提示，并同步提醒管理员跟进
5. 后台新增「预警」页：管理员可看到求助内容与用户注册时填写的手机号，及时干预
6. 修复：树洞屏蔽词误伤正常发言、日记天气 / 场景选择器不可用、AI 陪伴偶发无响应
7. 性能优化：列表首屏加载更快，趋势图切换更顺滑，长列表滚动掉帧明显减少
8. 界面更新：底部导航换成毛玻璃 + 滑动指示器并带切换动画，弹窗统一磨砂玻璃质感
9. 注册页文字配色由紫色改为青绿色，深色模式下更护眼
10. （喝了 4 瓶小酒。）

---

## 🎨 显示模式

v2.2 起由用户自己选，设置页「显示模式」提供三档：

| 选项 | 行为 |
| --- | --- |
| 跟随系统 | 听 `prefers-color-scheme` 的，手机 / macOS 到点变暗就跟着变（默认） |
| 浅色模式 | 一直用浅色 |
| 深色模式 | 一直用深色 |

选择存在 `localStorage`（key：`xinyu:theme`，取值 `auto` / `light` / `dark`），
刷新、重开浏览器都记得住；老版本没这个 key，或值被改坏了，一律退回「跟随系统」。

实现方式：

- `composables/useTheme.js` 是唯一的真相来源：`mode`（用户选的）+ `systemDark`
  （`matchMedia` 监听）= `theme`，`setMode()` 负责落盘；
- `App.vue` 用 `<van-config-provider :theme="theme">` 包住整个应用，
  Vant 会往 `<html>` 上加 `van-theme-dark` / `van-theme-light`，Vant 组件的深色变量都挂在这个类下面；
- 同时设置 `document.documentElement.style.colorScheme`，让滚动条、原生输入框、下拉框等浏览器控件一起变；
- `assets/main.css` 里的 `html/body/#app` 不写死颜色，改用 `var(--van-background)` /
  `var(--van-background-2)` / `var(--van-text-color)`；液态玻璃的底色走 `--xinyu-glass-bg`
  （浅色 `rgba(255,255,255,.66)` / 深色 `rgba(26,28,32,.62)`），所以弹窗、输入条在两种模式下都不会糊；
- `views/Trend.vue` 的两张 ECharts 图会在主题变化时用同一份数据重画一遍，
  否则深色背景下坐标轴和饼图文字会看不清。

---

## 🧊 界面细节：液态玻璃与 PC 适配

### 液态玻璃关怀弹窗

树洞 / 日记命中危机关键词时弹出的 `components/CrisisDialog.vue` 是液态玻璃质感，
结构由可复用的 `components/LiquidGlass.vue` 提供（本身没有样式，只负责 DOM 层次）：

```text
.xinyu-liquid            外壳：圆角 26px、overflow: hidden、isolation: isolate
├─ .xinyu-liquid__stage  绝对定位的舞台（aria-hidden）
│  ├─ .xinyu-liquid__blob--a / --b / --c   青 / 蓝 / 琥珀三团色斑，blur(24px) 后缓慢漂移
│  └─ .xinyu-liquid__sheen                 旋转的 conic-gradient 高光
└─ .xinyu-liquid__glass  毛玻璃层：backdrop-filter: blur(30px) saturate(190%) + 内侧亮边
   └─ <slot />           真实内容
```

- 色彩与不透明度走 CSS 变量（`--xinyu-liquid-tint` / `--xinyu-liquid-rim` /
  `--xinyu-liquid-edge` / `--xinyu-liquid-blur` / `--xinyu-liquid-blob-opacity`），
  深色模式在 `html.van-theme-dark` 下换一套值，不用改组件。
- 尊重 `prefers-reduced-motion: reduce`：关闭光斑漂移与高光旋转，只保留静态玻璃质感。
- `van-popup` 本身设 `backdrop-filter: none`，模糊全部交给内层玻璃（见上文第 13 条坑）。
- 危机文案、按钮（我知道了 / 拨打 12356）与后端返回的 `CRISIS_REPLY` 完全没动，
  这次只换了外观。

### 管理员危机预警横幅

`components/CrisisAlertBanner.vue` 只在首页（`views/Home.vue`）顶部出现，条件有三个：
当前用户是 `ADMIN`、待跟进数量 > 0、并且这条预警没有被本标签页关闭过。

- 数据来自 `GET /api/admin/crisis-alerts/pending-count` 与
  `GET /api/admin/crisis-alerts?status=PENDING&page=1&size=1`（按 `createdAt DESC`，
  取到的第一条就是最新的），两个请求并发发出。
- 横幅展示：待跟进条数、触发用户昵称、命中的关键词、来源（树洞帖子 / 日记）、时间，
  以及**用户注册时填写的手机号**（可点击拨打，未绑定则显示「未绑定」）。
- 关闭按钮把当前预警 ID 写进 `sessionStorage`（key：`xinyu_crisis_banner_dismissed_id`）。
  用 ID 而不是条数当标记，这样「关掉之后又来一条同样数量的新预警」仍会重新弹出。
- 点横幅主体进入 `/admin/crisis`。请求失败不弹额外提示（`request.js` 已经统一 toast 过），
  并且普通用户拿不到数据（后端返回 403），横幅自然不会出现。

### PC 适配

布局宽度统一由 CSS 变量 `--xinyu-shell` 控制，三档断点：

| 断点 | `--xinyu-shell` | 说明 |
| --- | --- | --- |
| 默认（手机） | `560px` | 单列，底部导航毛玻璃 |
| `≥ 1024px` | `880px` | 内容居中，页面左右内边距 24px |
| `≥ 1440px` | `1040px` | 大屏再放宽一档 |

- `#app`、底部导航内层、树洞详情输入栏都用 `var(--xinyu-shell)`，
  改一个变量即可整体换宽。
- `≥ 1024px` 时 `.xinyu-cards` 从纵向列表切成栅格
  （`repeat(auto-fill, minmax(300px, 1fr))`），首页日记、日记列表、树洞列表共用这个类。
  **这层栅格与卡片描边只在 1024px 以上的媒体查询里生效**，手机端外观完全不变。
- 悬浮按钮（`.fab`）用 `right: max(20px, calc((100vw - var(--xinyu-shell)) / 2 + 20px))`
  对齐到内容区右边缘，宽屏下不会飘到屏幕角落。
- `≥ 768px` 时 `van-popup--bottom` / `--top` 限宽并居中，抽屉不再横跨 1440px 的整条底边
  （居中必须补 `right: 0`，原因见上文第 15 条坑）。

### v2.2 新增的几处界面

- **树洞评论回复**（`views/TreeholeDetail.vue`）：每条评论右侧一个「回复」，
  点了以后输入框上方出现一条回复栏（`回复 @昵称` + 取消），发送时带 `parentId`；
  回复仍然纵向排在同一条评论流里，靠前置的 `.reply-to`（`回复 @昵称：`）说明对象。
  输入栏改成 `flex-direction: column`，背景用 `--xinyu-glass-bg` + `backdrop-filter: blur(18px)`，
  和底部导航是一套玻璃语言；窄屏不会挤出横向滚动条。
- **设置页「显示模式」**（`views/Settings.vue`）：点击弹出 `van-action-sheet`，
  三个选项来自 `THEME_OPTIONS`，当前项右侧标注「当前」——
  Vant 的 action sheet 不支持选中态样式，所以用 `subname` 自己标。
- **后台「日记」按账号查看**（`views/admin/AdminDiaries.vue`）：「查看账号」那一行点开是
  一个底部抽屉：搜索框（300ms 防抖）+ 分页用户列表 + 首行「全部用户」；
  选中后列表标题变成 `昵称（@username）`，每行「查看」弹出详情抽屉
  （命中危机关键词时顶部一条 `van-notice-bar`，提示去「预警」页跟进）。
- **日记详情页**（`views/DiaryDetail.vue`）：底部多了「删除这篇日记」（二次确认后
  提示「已删除」并回到列表）；进页面时会检查后端返回的 `crisisSupport`，
  是历史危机日记也一样弹关怀弹窗。

---

## 🐳 Docker Compose 部署

```bash
cp .env.example .env
# 编辑 .env，填入 AI_API_KEY、JWT_SECRET 等
docker compose up -d --build
```

启动后：

- 前端：`http://localhost`（Nginx 已加 `/uploads/` 反代，头像能正常显示）
- 后端：`http://localhost:8080/api`
- MySQL：`localhost:3306`（首次启动自动执行 `db/schema.sql` + `db/data.sql`，含 v2.0 的 `user.phone` 与 `crisis_alert`；v2.2 的 `post_comment.parent_id` 见 `db/upgrade-v2.2.sql`，生产库要手动跑一次）

上传的头像落在 `xinyu-uploads` 数据卷（容器内 `/app/uploads`），
`docker compose down` 再 `up` 不会丢图；要彻底清空才需要 `docker compose down -v`。

> 已有的老库（不是全新初始化）升级：在容器里或宿主机执行一次
> `mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v2.sql`。

---

## 🗓️ 开发顺序（已按此实现）

1. 第 1 周：Spring Boot 骨架、登录注册、日记 CRUD、Vue H5 登录和日记页
2. 第 2 周：AI 接入、SSE 流式回复、情绪分析、ECharts 趋势图
3. 第 3 周：树洞发帖、评论、点赞、举报、后台管理
4. 第 4 周：敏感词、限流、隐私保护、Docker 部署、答辩演示

---

## 🌟 项目亮点

- AI **流式**共情回复，体验好
- 情绪趋势可视化（折线图 + 饼图）
- 匿名树洞 + 隐私保护
- 敏感词 + 举报 + 后台审核
- 危机关键词干预：前端关怀弹窗 + 后端预警推送 + 管理员跟进闭环
- 注册手机号 + 图形验证码 + 短信验证码，短信通道可换真实服务商
- 设置页统一管理头像 / 昵称 / 手机号 / 密码，昵称违禁词审核
- 管理员自我保护（不能禁用/删除自己，始终保留一个可用管理员）
- 版本号驱动的「更新说明」弹窗，每次发版只提示一次
- 显示模式自选（浅色 / 深色 / 跟随系统），图表也会跟着变色
- 树洞评论可回复（两层结构 + 「回复 @昵称」引用）
- 底部导航毛玻璃 + 滑动指示器动画，弹窗磨砂玻璃质感
- Java 后端完整，H5 移动端适配
- 自带回归脚本：`scripts/smoke-test.sh`（75 项）、`scripts/admin-sse-test.sh`（40 项）

---

## 📜 著作权与使用声明

- **著作权人**：**CCnotcc016** ｜ **版权所有 © 2026 CCnotcc016，保留所有权利**
- **联系方式**：`CCnotcc016`（2947208937@qq.com）
- **使用范围**：本项目**仅供交流学习**（课程设计、毕业设计、技术学习、个人研究）。
- **禁止事项**：未经作者事先书面许可，**不得用于任何商业售卖、付费转售、二次销售、打包出售或纳入付费课程/付费源码库**。
- **商用 / 售卖**：请先联系作者**征询本人**并获得授权，未经许可的售卖行为视为侵权。
- **署名保留**：分发、引用、二次开发时请保留本声明、`LICENSE` 文件及源码文件头的版权水印，不得删除或篡改。
- 本项目涉及的情绪分析与「危机关键词」提示均为**陪伴与提醒**用途，**不构成任何医学诊断或治疗建议**。

> 完整条款见 [LICENSE](./LICENSE)。

