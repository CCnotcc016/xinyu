# 心语 · AI 情绪日记与匿名树洞

> 一个 Java + H5 的全栈项目：写情绪日记，AI 给你共情回复、情绪评分与趋势分析；也可以匿名发到「树洞」，获得点赞、评论与陪伴感。

核心不是做心理诊断，而是：**记录情绪 + 被理解 + 看见趋势**。

当前版本：**v3.0.0.3072** · 变更见 [🔔 版本号与更新说明](#-版本号与更新说明)

---

## ✨ 项目定位

| 角色 | 能力 |
| --- | --- |
| 普通用户 | 写日记、AI 共情回复、情绪趋势 + 情绪日历、AI 情绪周报、**心理自评（PHQ-9 / GAD-7 / PHQ-4，仅供参考，不是诊断）**、发树洞（贴主可置顶评论区留言）、评论点赞、举报、联系管理员/客服、加好友与私聊、设置（头像/昵称/手机号/密码） |
| 管理员 | 用户管理、日记管理、树洞管理、评论管理、举报处理、危机预警跟进（可填处理备注）、客服工作台（可看到全部会话） |
| 客服（STAFF） | 只在「客服工作台」里回复用户咨询，看得到「待接手」和自己已接手的会话；**不能写日记、发树洞、加好友、私聊** |

**还没做**：AI 心理诊断（自评量表只做到「筛查 + 提示就医」，不做诊断也不给用药建议）、复杂推荐、群聊/语音通话。

**危机兜底**：内容命中自残 / 自杀关键词时，前端弹关怀提示、后端给管理员推预警（含注册手机号）；
心理自评里 PHQ-9 第 9 题不为 0 时也会直接生成一条预警。
不做诊断、不给极端建议，只做「被看见 + 联系得上」。

**隐私的边界画在哪**：日记正文只给自己看（唯一的例外是命中危机关键词），
私聊只给两个人看且不进 AI、不生成危机工单。三条线在《隐私政策》里逐条写清楚了。

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
│           ├── db/              # schema.sql + data.sql + upgrade-v2*.sql + seed-demo.sql
│           ├── forbidden-names.txt   # 昵称/用户名违禁词
│           └── sensitive-words.txt
├── frontend/                    # Vue3 + Vant H5 前端
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── package.json
│   ├── vite.config.js
│   ├── public/
│   │   ├── changelog.json       # 更新说明（运行时读取，改完刷新即生效）
│   │   └── sw.js                # Service Worker（外壳缓存，/changelog.json 不缓存）
│   └── src/
│       ├── api/                 # 接口封装
│       ├── components/          # 公共组件（底部导航、更新说明弹窗、关怀弹窗、天气卡片）
│       ├── composables/         # 组合式函数（更新说明状态、深浅色主题、天气与定位）
│       ├── config/              # 更新说明加载器（changelog.js）+ 三份协议（legal.js）
│       ├── router/              # 路由
│       ├── stores/              # Pinia 状态
│       ├── utils/               # Axios 封装
│       └── views/               # 页面（Settings 设置页、含 admin/）
├── nginx/nginx.conf             # 宿主机部署示例
├── scripts/                     # 本地启停 + 接口冒烟测试脚本（测试跑完自动清理账号）
│   ├── run-dev.sh               #   启动后端（自动 source 工具链 + 加载 .env）
│   ├── serve-dist.sh            #   构建前端并用 Nginx 静态托管（免 sudo，默认 8088，SSL=1 可出 HTTPS）
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

开发环境后端启动时会自动执行 `db/schema.sql`、`db/data.sql`、演示数据 `db/seed-demo.sql`，
以及各版本的增量脚本 `db/upgrade-v*.sql`（需先手动创建空的 `xinyu` 库，或让 `schema.sql` 里的 `CREATE DATABASE` 自动创建）。

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
| `JWT_SECRET` | 生产必填 | `openssl rand -hex 32` 生成；缺失时后端启动失败 |
| `JWT_EXPIRATION` | 否 | token 有效期（毫秒），默认 `86400000`（24 小时） |
| `MYSQL_ROOT_PASSWORD` | Docker 部署必填 | MySQL root 密码（compose 不再有默认值） |
| `MYSQL_APP_PASSWORD` | Docker 部署必填 | 后端使用的应用账号密码（对应 `MYSQL_USER`，默认 `xinyu`） |
| `REDIS_PASSWORD` | Docker 部署必填 | Redis 密码（未设置时 Redis 启动即失败） |
| `ADMIN_INIT_USERNAME` / `ADMIN_INIT_PASSWORD` | 首次部署必填一次 | 库里没有任何可用管理员时，用它创建首个管理员；创建后必须立即改密 |
| `HTTP_PORT` / `HTTPS_PORT` | 否 | Nginx 对外端口，默认 80 / 443 |
| `TLS_DIR` | 启用 HTTPS 时必填 | 证书目录（含 `fullchain.pem`、`privkey.pem`） |

> 换机器或轮换密钥时只改 `.env`，代码一行都不用动。

### 3. 启动后端

```bash
bash scripts/run-dev.sh
```

脚本会自动 source `~/.xinyu-toolchain/env.sh` 并加载 `.env`，然后启动后端。
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

#### 手机定位必须走 HTTPS（v2.3 天气功能）

浏览器的地理定位接口 `navigator.geolocation` **只在安全上下文（secure context）里可用**：
`https://` 或 `http://localhost` / `http://127.0.0.1`。用手机访问 `http://192.168.x.x` 时，
`window.isSecureContext === false`，Chrome / 华为浏览器 / 小米浏览器等会直接拒绝定位
（报错码是 `PERMISSION_DENIED`，看起来像用户点了「拒绝」，其实是被浏览器拦掉了）。

所以想在手机上用「自动定位天气」，就用脚本自带的 HTTPS 模式：

```bash
SSL=1 PORT=8443 bash scripts/serve-dist.sh --no-build   # HTTPS 起在 8443
SSL=1 PORT=8443 bash scripts/serve-dist.sh stop          # 停止
```

- 脚本会自动生成自签证书到 `~/.xinyu-nginx/cert/xinyu-<端口>.crt|.key`（含 `localhost`、
  `127.0.0.1`、`xinyu.local` 与本机局域网 IP 的 SAN），无需手动申请证书。
- HTTPS 实例的配置与 pid 按 `<端口>-ssl` 区分（`nginx-8443-ssl.conf` / `nginx-8443-ssl.pid`），
  不会和同端口的 HTTP 实例打架。
- 手机访问 `https://<电脑局域网 IP>:8443`，第一次会提示证书不受信任，
  点「高级」→「继续访问」即可（要彻底没有提示，可把 `~/.xinyu-nginx/cert/xinyu-8443.crt`
  装到手机并设为信任）。

HTTP 下定位不可用也不会卡住页面：天气卡会明确提示「当前不是 HTTPS，手机浏览器不允许定位」，
并提供**手动选择城市**（列表 + 关键词搜索 + 按手机时区猜好的默认城市），天气功能照常可用。
另外 `navigator.permissions` 预检、低精度→高精度两级定位、超时兜底、`watchPosition` 兜底都已接上，
失败时分别提示「定位权限被拒绝了 / 暂时定不到位 / 定位超时了」，不会出现一直转圈的骨架屏。

### 5. 登录

**仓库里不预置任何账号、也不写任何默认密码。** 第一次部署时用下面的方式创建管理员：

```bash
# 方式一：交给后端引导（推荐）
# 库里没有任何启用的管理员时，后端会用这两个变量创建首个管理员
ADMIN_INIT_USERNAME=admin \
ADMIN_INIT_PASSWORD='你的强密码（至少 8 位，含字母和数字）' \
bash scripts/run-dev.sh
```

```bash
# 方式二：脚本直接写库（密码只在本机终端输入，不进仓库）
MYSQL_PASSWORD='数据库密码' bash scripts/init-admin.sh
```

创建出来的管理员 `must_change_password = 1`：**首次登录只能改密**，
在改密完成前后端会用 `1028` 拦下除「改密 / 我的信息 / 登录」以外的所有接口。

普通用户在前端「注册」页面注册后登录即可（v2.0 起注册需手机号 + 短信验证码）。

> 本机开发（`dev` profile）会额外加载 `backend/src/main/resources/db/seed-demo.sql`，
> 里面是演示用的普通用户 / 客服账号，方便只看界面效果。
> 它**只在 `dev` profile 生效**，生产（`prod`）不会加载；仍然建议上线前删除该文件或清空其中的账号。

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
可用 `API=... ADMIN_USER=... ADMIN_PASS=...` 覆盖。

脚本会自己收尾：注册过的测试账号记录在 `CLEANUP_IDS` 里，通过 `trap cleanup EXIT`
在结束时（正常跑完、断言失败、甚至中途 `Ctrl+C` / `kill`）调用管理员接口把它们删掉，
不会在数据库里留下垃圾数据。注意这一点依赖管理员账号可用，所以 `ADMIN_USER` /
`ADMIN_PASS` 必须是未禁用的管理员；管理员登不上时会打印一行跳过提示。

---

## ✅ 本机免 sudo 运行验证

本项目已在 macOS 12 (Monterey, x86_64) 上完整编译并跑通，全程未使用 `sudo`，
所有工具安装在用户目录下。

### 目录结构

```
~/.xinyu-toolchain/
├── env.sh        # source 之后即可使用下面的命令
├── jdk/          # Temurin JDK 17
├── maven/        # Maven 3.9
├── node/         # Node 20（含 npm）
├── mysql/        # MySQL 8.0.28 免安装版
├── mysql-data/   # 数据目录
├── mysql-log/    # 错误日志
└── dl/           # 下载的安装包缓存（约 800MB，确认装好后可删：rm -rf ~/.xinyu-toolchain/dl）
```

### 启动步骤

```bash
source ~/.xinyu-toolchain/env.sh

# 1. 启动 MySQL（本机 3306 已被占用，这里用 3307）
~/.xinyu-toolchain/mysql/bin/mysqld \
  --port=3307 \
  --socket=~/.xinyu-toolchain/mysql.sock \
  --datadir=~/.xinyu-toolchain/mysql-data &

# 2. 启动后端（.env 里已覆盖本机 MySQL 3307 端口和 AI Key）
cd backend && mvn -DskipTests package
cd .. && bash scripts/run-dev.sh

# 3. 启动前端（注意：Vite 5 绑定的是 ::1）
cd frontend && npm install && npm run dev
# 浏览器访问 http://localhost:5173
```

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
| 界面实测（v2.2.3）：写日记页输入内容 → 离开页面 → 再进入，草稿自动恢复并提示「已恢复上次未保存的草稿」 | ✅ 正常 |
| 界面实测（v2.2.3）：日记保存成功后草稿被清空，再次进入不再提示 | ✅ 正常 |
| 界面实测（v2.2.3）：情绪趋势「日」默认「9月30日 - 10月2日（含今天）」，点「← 前一天」翻到「9月29日 - 10月1日」，`后一天` 回到今天后置灰 | ✅ 正常 |
| 接口实测（v2.2.3）：`GET /api/diaries/stats/trend?range=day&end=2026-09-30` 返回 09-28 / 09-29 / 09-30 三个点；不带 `end` 返回最近 3 日 | ✅ 正常 |
| 接口实测（v2.2.3）：新建含危机词的日记，创建响应里 `aiStatus=CRISIS`、`aiReply` 直接带上关怀文案（无需再点生成） | ✅ 正常 |
| 界面实测（v2.2.3）：后台查看 jk1 的历史危机日记，详情顶部提示条可见、「AI 回复」显示关怀文案（后台已不再弹关怀弹窗） | ✅ 正常 |
| 界面实测（v2.2.3）：后台查看一篇普通日记（无危机词）不弹关怀弹窗、不显示提示条及兜底文案 | ✅ 正常 |
| 界面实测（v2.2.3）：PC 端树洞评论框内按回车直接发送，`Command/Windows + 回车` 在光标处换行（多行内容一起发送） | ✅ 正常 |
| 接口实测（v2.2.3.2048）：新建 810 字含危机词的日记 → 创建成功，`crisis_alert` 新增一条 `PENDING` 记录（摘要 500 字，修复前会因 501 字触发 `Data too long` 整条丢失） | ✅ 正常 |
| 日志实测（v2.2.3.2048）：上述长日记创建过程中后端日志没有 `危机预警记录失败`（`CrisisAlertService` 的吞异常分支未被触发） | ✅ 正常 |
| 数据实测（v2.2.3.2048）：脚本补齐历史漏报预警，154（admin）/155（jk1）各有一条 `PENDING`，`pending-count` 返回 2 | ✅ 正常 |
| 界面实测（v2.2.3.2048）：管理员首页顶部横幅出现「有 2 条危机预警待跟进 · 枕雪闻兰 · 命中「活不下去」· 私密日记」+ 可拨打的注册手机号 | ✅ 正常 |
| 界面实测（v2.2.3.2048）：后台「日记」查看危机日记（155）只有顶部提示条 + `AI 状态 CRISIS` + 关怀文案，不再弹关怀弹窗 | ✅ 正常 |
| 界面实测（v2.2.3.2048）：后台「日记」查看普通日记（「好困啊，晚安。」）无提示条、无弹窗，详情正常 | ✅ 正常 |
| 界面实测（v2.2.3.2048）：更新说明弹窗提示 `v2.2.3.2048`，第 2 条标红加粗，其余为普通样式 | ✅ 正常 |
| 界面实测（v2.2.3.2048）：后台导航「预警」红点显示 2 | ✅ 正常 |
| 接口实测（v2.3）：注册不带 `agreementAccepted` → 业务码 `400 请先阅读并同意《用户协议》与《隐私政策》`；传 `false` 同样被拒；传 `true` 才注册成功，`agreementVersion=1.0` | ✅ 正常 |
| 接口实测（v2.3）：`POST /api/user/agreement` 传 `version=0.9` → 业务码 `400 请求参数错误`，必须与 `Legal.LEGAL_VERSION` 一致 | ✅ 正常 |
| 接口实测（v2.3）：`PUT /api/user/location-consent` 传 `true` / `false` 均返回最新 `locationConsent`，并写入 `location_consent_at` | ✅ 正常 |
| 数据库实测（v2.3）：`upgrade-v2.3.sql` 幂等执行后 `user` 表多出 `agreement_version` / `agreement_accepted_at` / `location_consent` / `location_consent_at` 四列，老数据不受影响 | ✅ 正常 |
| 界面实测（v2.3）：把测试账号的 `agreement_version` 置空后进入首页，弹出不可关闭的「我们更新了政策」；`/login` 不弹 | ✅ 正常 |
| 界面实测（v2.3）：弹窗正文第 2 条渲染为红色 `rgb(238,10,36)`；375px 宽时按钮先是「请先读完上面的内容」并禁用，滚到底后变成「我已阅读并同意」 | ✅ 正常 |
| 界面实测（v2.3）：点「我已阅读并同意」→ 服务端写入 `agreementVersion=1.0` + 时间戳，弹窗关闭，刷新不再出现 | ✅ 正常 |
| 界面实测（v2.3）：首页天气卡 → 授权说明 → 「手动选择城市」→ 搜索「杭州」→ 选定后显示 `☁️ 19°C 阴 杭州 19°~24° 体感 20°` + 夜间陪伴文案 | ✅ 正常 |
| 数据实测（v2.3）：授权后 `localStorage` 只有 `xinyu:weather:coords` / `:snapshot`，后端接口请求里不含任何经纬度 | ✅ 正常 |
| 界面实测（v2.3）：写日记页进入后天气已自动填为「阴天」；打开草稿时不会因为自动天气而误报「已恢复上次未保存的草稿」 | ✅ 正常 |
| 界面实测（v2.3）：`/legal/terms` `/legal/privacy` `/legal/disclaimer` 三页均可直接从 URL 打开（nginx `try_files` 回落），隐私政策九个小节完整 | ✅ 正常 |
| 界面实测（v2.3）：「我的 - 隐私与授权」开关打开 / 关闭 + 二次确认，关闭后本地坐标与天气缓存被清空 | ✅ 正常 |
| 界面实测（v2.3）：`138＊＊＊＊8000` 正常显示（全角星号，不再被 `**` 加粗语法吃掉） | ✅ 正常 |
| 界面实测（v2.3）：手机端（375px）首页间距收紧后「最近日记」上移约 70px，设置页总高度 967 → 880px | ✅ 正常 |
| 界面实测（v2.3）：1280px 宽屏回归——`.page` 内边距、问候卡、天气卡、格子高度、表格内边距与 v2.2.3 完全一致，PC 布局未受影响 | ✅ 正常 |
| 界面实测（v2.3）：`/diaries` `/treehole` `/trend` `/profile` `/diaries/write` 在 375px 下均无横向溢出（`scrollWidth === clientWidth`） | ✅ 正常 |
| 界面实测（v2.3）：`http://<局域网IP>`（非安全上下文）点「使用我的位置」→ 提示「当前不是 HTTPS，手机浏览器不允许定位」，天气卡退回手动选城市，**不再卡在骨架屏** | ✅ 正常 |
| 界面实测（v2.3）：注入定位桩分别返回错误码 1 / 2 / 3 → 弹窗依次为「定位权限被拒绝了」「暂时定不到位」「定位超时了」，均带「再试一次 / 手动选择城市」 | ✅ 正常 |
| 界面实测（v2.3）：定位桩返回杭州坐标（30.27, 120.16）→ 写入 `xinyu:weather:coords` 并显示 `🌧️ 22°C 下雨 杭州市 19°~23° 体感 24°` + 雨天陪伴文案 | ✅ 正常 |
| 实测（v2.3.1）：`SSL=1 PORT=8443 bash scripts/serve-dist.sh` 起 HTTPS 实例，`https://127.0.0.1:8443/` 与 `/api/auth/captcha` 均 200，且与 80 端口实例、后端 8080 互不影响 | ✅ 正常 |
| 实测（v2.3.1）：`scripts/smoke-test.sh` 75 项全部通过（v2.3 起注册必须带 `agreementAccepted`，脚本已同步补上，否则会在 Bean Validation 处统一返回 400） | ✅ 75/75 |
| 实测（v2.3.1）：后端 `mvn -DskipTests package` 产出 `xinyu-backend-2.3.1.jar`，启动日志为 `Starting XinyuApplication v2.3.1` | ✅ 正常 |
| 界面实测（v2.3.2）：底部导航高亮跟着路由走 —— 点「日记」后 URL 为 `/diaries`、标题「我的日记」、`日记` 项带 active、胶囊 `translateX(100%)`；`/treehole` 为 200%，`/trend` 为 100% | ✅ 正常 |
| 视觉实测（v2.3.2）：顶栏 `.van-nav-bar` 为 `background: transparent` / `border: 0` / `box-shadow: none` / `::after` 隐藏；`::before` 左右各 `-187.5px`（375px 宽）铺满整屏，`mask-image: linear-gradient(#000 65%, transparent)` 生效 | ✅ 正常 |
| 视觉实测（v2.3.2）：深色模式下逐行亮度最大跳变由 **120.9**（旧：内侧白色高光边）降到 **1.5**（新：平滑渐变）；浅色模式 0.3 → 1.0，人眼不可辨 | ✅ 正常 |
| 界面实测（v2.3.2）：首页 / 我的日记 / 匿名树洞 / 情绪趋势 / 设置五页顶栏样式一致，`npm run build` 产出 `assets/index-DEDfjBpy.js` 且 nginx 返回的 `index.html` 已引用新产物 | ✅ 正常 |
| 接口实测（v2.4）：`PUT /api/treehole/comments/{id}/pin?pinned=true` 由贴主置顶自己帖子下的评论返回 200（可以置顶别人的留言），换非贴主调用返回 `3005 只有贴主可以置顶评论`；再置顶第二条时第一条自动取消，取消置顶不影响其它评论 | ✅ 正常 |
| 数据实测（v2.4）：评论列表排序为 `pinned_at DESC, created_at ASC`，置顶评论稳定在评论区第一条；`post_comment.pinned_at` 置空后回到按时间排序 | ✅ 正常 |
| 界面实测（v2.4）：评论区置顶留言带「置顶」标签并排在最上面，只有贴主看得到「置顶 / 取消置顶」按钮（`CommentVO.canPin`） | ✅ 正常 |
| 接口实测（v2.4）：`GET /api/admin/reports?status=PENDING` 只返回待处理；`GET /api/admin/reports/pending-count` 与列表条数一致 | ✅ 正常 |
| 界面实测（v2.4）：管理员首页顶部蓝色通知条「有 N 条举报待处理」+ 举报原因，点击跳转 `/admin/reports`，「举报管理」页签带红点角标与待处理 / 已处理 / 已忽略 / 全部筛选 | ✅ 正常 |
| 接口实测（v2.4）：`POST /api/support/messages` 发消息 200；`GET /api/support/unread-count` 在有客服回复时返回未读数与最新回复人 | ✅ 正常 |
| 界面实测（v2.4）：用户首页通知条「客服回复了你 N 条消息」→ 进入 `/support` 聊天页，气泡按角色显示「客服 · 小语」/「我」，回车发送（手机上换行）| ✅ 正常 |
| 界面实测（v2.4）：客服账号 `/staff` 只看到「待接手」+ 自己接手的会话，点「我接手」后会话归属自己；另一个客服账号看不到该会话（直接访问 URL 提示「无权限访问」，写消息被拒后输入框收起并提示已由其他客服接手）| ✅ 正常 |
| 界面实测（v2.4）：管理员可看到全部会话，回复用户不会改变会话归属（客服仍能继续跟）| ✅ 正常 |
| 接口实测（v2.4）：客服消息不限敏感词、不写 `crisis_alert`（用户向客服求助不会被「内容违规」挡住）| ✅ 正常 |
| 界面实测（v2.4）：「我的」页新增「联系管理员/客服」「客服工作台」入口（工作台仅 ADMIN / STAFF 可见），版本号与「更新说明」一致 | ✅ 正常 |
| 界面实测（v2.4）：「更新说明」弹窗支持翻看历史版本（共 N 条历史记录，按时间倒序），当前版本无对应文案时直接进入历史列表 | ✅ 正常 |
| 实测（v2.4）：`mvn -DskipTests package` 产出 `xinyu-backend-2.4.0.jar`，启动日志为 `Starting XinyuApplication v2.4.0`；`upgrade-v2.4.sql` / `seed-demo.sql` 重复执行无副作用 | ✅ 正常 |
| 接口实测（v2.4.1）：`STAFF` 调 `POST /api/diaries`、`POST /api/treehole/posts`、`POST /api/treehole/posts/{id}/like`、`DELETE .../like`、`POST .../comments`、`PUT /api/treehole/comments/{id}/pin`、`POST /api/reports`、`POST /api/support/messages` 全部返回 `403 无权限访问`；`GET /api/treehole/posts`、`GET /api/staff/conversations`、`GET /api/support/thread` 正常返回 200 | ✅ 正常 |
| 接口实测（v2.4.1）：普通用户（USER）仍可 `POST /api/reports` 与 `POST /api/support/messages`（均 200），未被上面的 `STAFF` 守卫误伤；随后用管理员账号删除该临时账号，其会话 / 消息 / 举报一并清空，库里回到 2 个会话 / 8 条消息 | ✅ 正常 |
| 接口实测（v2.4.1）：接手的客服 `PUT /api/staff/conversations/{id}/close` 返回 200（`status=CLOSED`、`closedByName` 有值、用户侧多一条 `SYSTEM` 系统提示）；非接手的客服、非接手的客服结单均返回 `403 无权限访问`；已结单再结单 / 已结单时客服发消息均返回 `2003` | ✅ 正常 |
| 接口实测（v2.4.1）：结单后用户再发一条消息 → 会话回到 `OPEN`、`assigneeId` 与 `closedByName` 清空、`session_start_id` = 新消息 id - 1；客服侧会话详情只剩新一轮的 1 条消息（结单提示与上一轮记录都看不到），用户侧 5 条历史一条不少 | ✅ 正常 |
| 接口实测（v2.4.1）：管理员删除账号（`DELETE /api/admin/users/{id}`）后该账号的 `support_conversation`、`support_message` 一并删除，客服工作台不再出现找不到用户的会话 | ✅ 正常 |
| 界面实测（v2.4.1）：客服账号登录直接落到 `/staff`，底部导航只剩「工作台 / 我的」，「我的」页没有「我的日记 / 情绪趋势 / 联系管理员客服」；手输 `/`、`/diaries`、`/treehole`、`/diary/new` 全部被路由守卫重定向回 `/staff`，且不弹更新说明 / 绑定手机号弹窗 | ✅ 正常 |
| 界面实测（v2.4.1）：`/staff/{id}` 顶栏是「结单」按钮，结单后变成「已结单」+ 提示「本次会话已结单（xxx）。用户再次咨询时会重新分配接手人…」，输入框替换为「会话已结单，等用户再次咨询后即可回复」；客服工作台列表显示「已结单」标签 | ✅ 正常 |
| 界面实测（v2.4.1）：用户侧 `/support` 顶部提示「本次咨询已结束。还有问题的话直接再发一条消息，会重新安排客服/管理员接手。」，结单提示渲染成居中的系统一行，历史消息完整 | ✅ 正常 |
| 实测（v2.4.1）：`mvn -DskipTests package` 产出 `xinyu-backend-2.4.1.jar`，启动日志为 `Starting XinyuApplication v2.4.1`；`upgrade-v2.4.1.sql` / `seed-demo.sql` 重复执行无副作用（同一份 seed 在空库上跑 3 次，行数不变） | ✅ 正常 |

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
5. **危机干预不依赖 AI**：日记含「自杀 / 自残 / 想死」等关键词时，后端在**保存日记时**就把
   危机干预文案（心理援助热线 12356、紧急 120/110）写入 `ai_reply`，`ai_status` 置为 `CRISIS`，
   无需配置 AI Key、也无需手动点「生成 AI 回复」就能验证；详情页与后台详情都会读到这段文案
   （老数据里 `ai_reply` 为空时，读取时会兜底补上同样内容）。
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
14. **手机定位只在安全上下文里可用**：`navigator.geolocation` 在
    `http://192.168.x.x` 下会被 Chrome / 华为 / 小米等浏览器直接拒绝，报错码还是
    `PERMISSION_DENIED`（1），很容易误判成「用户自己点了拒绝」。判断依据是先看
    `window.isSecureContext`，false 就直接给出「当前不是 HTTPS」的提示，不去调定位接口。
    手机上要用真实定位，请用 `SSL=1 PORT=8443 bash scripts/serve-dist.sh` 的 HTTPS 模式
    （自签证书，首次访问点「继续访问」）。拿不到定位时天气卡会引导手动选城市，
    按手机时区预填默认城市，功能不受影响。
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
- [ ] `MYSQL_ROOT_PASSWORD` / `MYSQL_APP_PASSWORD` 不是 `root`、不是示例值
- [ ] `REDIS_PASSWORD` 已设置（Redis 不设密码 + 端口暴露等于把会话数据送人）
- [ ] 管理员账号是新建的，并且已经改掉首次登录的初始密码
- [ ] 已用 `docker-compose.tls.yml` 启用 HTTPS：`/api` 里带 JWT，明文 HTTP 等于把 token 送给同网段任何人
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
- `POST /api/auth/register` 注册，**必须填手机号 + 短信验证码**，且必须同意协议
  （`username` / `password` / `phone` / `smsCode` / `agreementAccepted: true`，`nickname` 可选；
  `agreementAccepted` 不是 `true` 会被参数校验直接挡住，前端不勾选也提交不了）
- `POST /api/auth/login` 登录（返回 JWT）
- `POST /api/auth/logout` 退出登录（服务端 `token_version` +1，**当前 token 立刻作废**，其它设备也一起下线）
- `GET /api/auth/ping` 存活探测（不需要登录，不查库、不查 Redis；前端用它判断「后端有没有起来」）

### 个人中心（「我的 → 设置」）

- `GET /api/user/me` 当前用户信息
- `PUT /api/user/me` 修改昵称（`{ "nickname": "..." }`，1-20 个字符）
- `PUT /api/user/password` 修改密码（`{ "oldPassword": "...", "newPassword": "..." }`）
- `PUT /api/user/phone` 绑定 / 更换手机号
  （`{ "phone": "...", "smsCode": "...", "password": "..." }`，首次绑定可省 `password`，换号必须带）
- `POST /api/user/avatar` 上传头像（`multipart/form-data`，字段名 `file`，jpg/png/webp/gif，≤ 2MB）
- `POST /api/user/agreement` 记录协议同意（`{ "version": "1.0" }`，版本号不是当前版本会被拒）
- `PUT /api/user/location-consent` 开启 / 撤回定位授权（`{ "consent": true }`）
- `GET /api/user/{id}/profile` 别人的公开主页（昵称、头像、注册时间、发帖数、收到的赞；
  **只有看自己时才返回 `diaryCount` 与 `streak`**，并附 `relation`：`SELF` / `FRIEND` /
  `REQUESTING`（我已申请）/ `INCOMING`（对方申请了我）/ `STRANGER`，
  前端据此决定显示「加好友」「同意」还是「发消息」）
- `GET /api/user/{id}/posts?page=&size=` 某个用户**非匿名**的树洞帖子
- `GET /api/user/data-export` **导出我的全部数据**（JSON：日记、树洞、评论、点赞、好友、通知等；
  日记一律不含别人写的内容，导出的文件只在这次响应里生成，服务端不留副本）
- `DELETE /api/user/account` **自助注销**（`{ "password": "..." }`，密码错误返回 `1002`；
  `ADMIN` 角色不允许自助注销；删除后不可恢复，前后端都会二次确认）

> `GET /api/user/{id}/profile` 与 `GET /api/user/{id}/posts` 只返回公开信息：
> 用户名、手机号、日记正文一律不出现在响应里；匿名帖不会出现在「他的主页」，
> `TreeholePostVO.userId` 也是 `null`，所以点不进去。
> 这两个接口对 `STAFF` 账号关闭（AI 生成的 `UserProfileVO` 里没有任何需要客服看的东西）。

> `GET /api/user/me` 会额外返回 `agreementAccepted`（是否同意过当前版本协议）与
> `locationConsent`（是否授权定位与天气），前端靠这两个字段决定要不要弹政策更新弹窗、
> 要不要在首页显示天气卡片。协议版本号以 `com.xinyu.common.Legal.CURRENT_VERSION` 为准。

> `username` 是登录凭据，不支持修改；显示名（昵称）、密码、手机号、头像都在「设置」里改。
> `PUT /api/user/me` 只更新 `nickname` 一列，`updated_at` 交给 MySQL 的
> `ON UPDATE CURRENT_TIMESTAMP` 自动维护。
> 昵称和用户名都会过一遍违禁词校验（详见下方「安全与隐私」一节）。

### 日记

- `POST /api/diaries` 创建日记
- `GET /api/diaries?page=&size=&start=&end=` 分页列表
- `GET /api/diaries/{id}` 详情
- `PUT /api/diaries/{id}` 更新
- `DELETE /api/diaries/{id}` 删除（**进回收站**：写 `deleted_at`，不真删）
- `GET /api/diaries/trash` 回收站列表（7 天内删除的日记，响应里带 `deletedAt`）
- `POST /api/diaries/{id}/restore` 从回收站恢复（情绪标签一起回来）
- `DELETE /api/diaries/trash/{id}` 彻底删除（连 `diary_tag` 一起清掉）
- `GET /api/diaries/{id}/ai-reply/stream` **SSE 流式 AI 回复**
- `GET /api/diaries/stats/trend?range=week` 情绪趋势（day/week/month；可选 `end=YYYY-MM-DD` 指定窗口最后一天，不带则看最近）
- `GET /api/diaries/stats/distribution` 情绪分布
- `GET /api/diaries/stats/insights` **情绪洞察**（天气 / 场景 / 标签 / 星期与情绪分的关系）
- `GET /api/diaries/stats/recap?period=month|year&anchor=YYYY-MM-DD` **月度 / 年度回顾**
- `GET /api/diaries/stats/calendar?days=91` **情绪日历**（每天的平均情绪分与日记数，用于热力图与连续记录天数）
- `GET /api/diaries/stats/weekly-report?weekStart=YYYY-MM-DD&refresh=false` **AI 情绪周报**
  （`weekStart` 不填默认本周一；结果按「用户 + 周」缓存，`refresh=true` 强制重新生成）

> **情绪日历与连续天数**：`CalendarVO` 返回 `days`（每天一条 `date` / `avgScore` / `count`）、
> `streak`（含今天往前连续有日记的天数）与 `maxStreak`。前端把它画成热力图，
> 一眼能看出「低的那几天是不是挤在一起」——趋势折线看不出这一点。
> 只统计自己的日记，不存在任何跨用户聚合。

> **AI 情绪周报**：只把**聚合后的统计**交给大模型（本周平均分、日记数、出现最多的标签、
> 高频标签名），不发送任何一条日记原文；同一用户同一周只调一次模型，结果落在
> `emotion_weekly_report`，离线或模型不可用时降级为 `MOCK` 模板文案（`source` 字段标明来源）。

### 树洞

- `GET /api/treehole/posts` 帖子列表
- `POST /api/treehole/posts` 发帖
- `GET /api/treehole/posts/{id}` 帖子详情
- `POST /api/treehole/posts/{id}/like` 点赞
- `DELETE /api/treehole/posts/{id}/like` 取消点赞
- `GET /api/treehole/posts/{id}/comments` 评论列表（回复会带上 `parentId` 与 `replyToNickname`）
- `POST /api/treehole/posts/{id}/comments` 发评论 / 回复（可选 `parentId`：填了就是回复某条评论，
  被回复的评论必须存在、未删除且属于同一个帖子，否则返回 `3002 评论不存在`）
- `PUT /api/treehole/comments/{id}/pin?pinned=true` 置顶 / 取消置顶**评论区里的某条留言**
  （只有贴主能动自己帖子下的评论，否则 `3005 只有贴主可以置顶评论`）
- `POST /api/reports` 举报（`STAFF` 账号不可用，返回 `403`，见「联系管理员/客服」一节的权限说明）

> **评论置顶**：`post_comment.pinned_at` 为 `NULL` 表示未置顶，否则记录置顶时间。
> 权限看的是**帖子作者**而不是评论作者 —— 贴主可以置顶任何人（包括自己）在他帖子下的留言。
> 一条帖子同一时间**只有一条置顶评论**（置顶新的会先清掉旧的），
> 评论列表排序是 `pinned_at DESC, created_at ASC`（MySQL 里 `NULL` 排最后），
> 所以置顶那条永远在评论区最上面；`CommentVO.pinned` 给前端渲染「置顶」标签，
> `CommentVO.canPin` 只在当前登录用户是贴主时为 `true`，前端据此显示「置顶 / 取消置顶」按钮。

> **评论回复**：只做两层 —— 回复同样排在评论列表里，靠 `replyToNickname` 在前端渲染成
> 「回复 @昵称：内容」。被回复的评论可能不在当前分页里，所以后端会额外批量查一次父评论
> （`parents()`），不会因为翻页而丢掉「回复谁」这个信息。

> **关怀弹窗**：发帖 / 评论命中自残、自杀等关键词时，接口照常成功，
> 但响应里会带 `crisisSupport: true`，前端立刻弹出一张磨砂玻璃的「我们关心你」弹窗
> （心理援助热线 12356、紧急情况 120/110），同时后端写入一条 `crisis_alert`
> 推给管理员。日记同理（`GET /api/diaries/{id}` 也会返回 `crisisSupport`，
> 所以重新打开一篇旧日记仍会看到关怀提示）。

### 好友与私聊（对客服账号关闭）

树洞里能看到昵称的地方都可以点进主页加好友，成为好友之后才聊得起来：

- `GET /api/friends` 我的好友（含最后一条消息与未读数，用来渲染聊天列表）
- `GET /api/friends/requests?type=INCOMING|OUTGOING|ALL` 好友申请
- `GET /api/friends/requests/pending-count` 待我同意的申请数（「我的」页角标）
- `POST /api/friends/requests` 发起申请（`{ "toUserId": 3, "message": "..." }`，附言可选）
- `PUT /api/friends/requests/{id}` 同意 / 拒绝（`{ "action": "ACCEPT" | "REJECT" }`）
- `DELETE /api/friends/{friendId}` 删除好友（**双向解除**，只删自己的那一行做不到）
- `GET /api/chat/conversations` 聊天列表（按最后一条消息倒序）
- `GET /api/chat/unread-count` 底部导航「聊天」的角标
- `GET /api/chat/{peerId}/messages?beforeId=&size=30` 聊天记录（`beforeId` 传上一页最小 id，往前翻页）
- `POST /api/chat/{peerId}/messages` 发消息（`{ "content": "..." }`，≤ 1000 字）
- `PUT /api/chat/{peerId}/read` 把对方发来的消息标记为已读

> **为什么没有会话表**：两个人的会话可以直接用 `(from_user_id, to_user_id)` 配对还原，
> 会话列表取每对好友的最新一条消息（`IF(from_user_id = me, to_user_id, from_user_id) AS peer_id`
> 再取 `MAX(id)`）。少一张表、少一次一致性维护，代价是查询稍复杂一点 ——
> 课程设计里这个取舍比「多一张 conversation 表」好讲。

> **好友才发得出消息，但记录一直能看**：`send` 会校验好友关系（否则 `3011 你们还不是好友`），
> 删除好友后双方**仍然能翻看历史消息**，只是输入框被禁用 —— 这样删好友不会变成
> 「连自己说过的话都不给我看了」。

> **私聊的隐私边界**：私聊内容不进 AI 流程、不生成危机预警工单（只在自己发出去时
> 通过响应里的 `crisis: true` 弹一次关怀提示给你本人）。例外是对方举报骚扰，
> 管理员在处理举报时才会查看相关记录 —— 这一条已写进《用户协议》与《隐私政策》(v1.1)。

> **未读与轮询**：没有长连接，聊天列表 20 秒、聊天窗口 4 秒轮询一次
> （`stores/chat.js`，登录后启动、退出登录停止）。列表与窗口的未读数是两套接口：
> 列表用 `FriendVO.unread`，角标用 `GET /api/chat/unread-count`。

### 客服（用户 ⇄ 管理员/客服）

一对一会话，一个用户只有一条会话记录，避免对话被拆散：

- `GET /api/support/thread` 我的会话（含全部消息，打开即清零未读）
- `POST /api/support/messages` 给管理员/客服发消息（`{ "content": "..." }`，≤ 2000 字）
- `GET /api/support/unread-count` 未读回复数（首页通知条用，返回最新一条的发送者与时间）

客服 / 管理员侧（需 ADMIN 或 STAFF 角色，见 `common/ErrorCode` 与 `SecurityConfig`）：

- `GET /api/staff/conversations?page=&size=` 会话列表（客服只看「待接手」+ 自己接手的）
- `GET /api/staff/conversations/{id}` 会话详情（别人的会话返回 `403`）
- `POST /api/staff/conversations/{id}/messages` 回复用户（首次回复即接手该会话）
- `PUT /api/staff/conversations/{id}/close` **结单**（v2.4.1 新增）
- `GET /api/staff/unread-count` 待回复的会话数（首页通知条用）

> **归属规则**：`assignee_id` 为空表示还没有人接手，所有客服都能看到并抢单；
> 一旦有客服回复过就固定归属，其他客服看不到（直接访问返回 403）；
> 管理员可以看到全部会话，但管理员回复**不会**改变归属，避免把会话从客服手里抢走。
> 消息里落库了 `sender_role`，所以用户侧气泡能显示成「客服 · 小语」。
> 客服看用户的手机号同样走打码（`UserVO.maskPhone`），只有 `@username` 是 7 位以上纯数字时才打码。
> 客服消息**不做敏感词过滤、也不记危机预警**——用户向客服求助时不该被「内容违规」挡住。

> **结单（v2.4.1）**：会话处理完后由**接手的客服**或**管理员**点「结单」，
> 状态转成 `CLOSED`，并给用户写一条 `SYSTEM` 消息「本次咨询已结束。如果还有问题，
> 再发一条消息就可以重新接入客服。」。已结单的会话：
> 客服侧发不出消息、也不能重复结单（都返回 `2003`）；
> 用户再发一条消息就自动开启新一轮 —— `status` 回到 `OPEN`、`assignee_id` 清空、
> `closed_at` / `closed_by_id` 清空，并把 `session_start_id` 记成「新消息 id - 1」。
> 客服侧读会话时只返回 `id > session_start_id` 的消息，所以**新一轮的接手人看不到结单之前的记录**，
> 用户侧不过滤、历史一条不少。结单信息给用户看的是「客服 · 小语 已结束本次会话」这类文案
> （`closedByName`），系统提示不计入未读数。

> **`STAFF` 只能回复用户（v2.4.1）**：发日记（`POST / PUT / DELETE /api/diaries/**`）、
> 发树洞帖子、点赞、评论、置顶评论（`/api/treehole/**` 的写接口）、举报（`POST /api/reports`）、
> 以用户身份开客服会话（`POST /api/support/messages`）都加了 `@PreAuthorize("!hasRole('STAFF')")`，
> 越权调用统一返回 `403 无权限访问`；读取类接口不受影响。
> 前端对应做了：登录后直接进「客服工作台」、底部导航只剩「工作台 / 我的」、
> 路由守卫把 `/`、`/diaries`、`/treehole`、`/diary/new` 这类页面重定向回 `/staff`、
> 「我的」页不再显示「我的日记 / 情绪趋势 / 联系管理员客服」、并且客服账号不弹首页弹窗。

### 心理自评（PHQ-9 / GAD-7 / PHQ-4）

- `GET /api/assessments/scales` 三份量表的完整定义：题目、选项、分段阈值、每档建议、免责声明。
  题库只在后端（`AssessmentScaleRegistry`），改题改阈值不用改前端、也不会出现「两端阈值不一样」
- `POST /api/assessments` 提交作答（`{ "scaleKey": "phq9", "answers": [0,1,2,...] }`）。
  **分数由后端按答案重算**，前端传什么分数都不作数；一次请求返回得分、档位、分维度、
  逐题明细、固定建议和一段 AI 解读。AI 不可用或当天额度用完时仍然返回完整结果，
  只是 `aiSource = "MOCK"`、解读换成本地拼的文本
- `GET /api/assessments?limit=10` 我的自评历史（精简版，不带逐题明细）
- `GET /api/assessments/{id}` 单条详情（只能看自己的，别人的返回 `3014 自评记录不存在`）
- `POST /api/assessments/{id}/interpretation` 重新生成 AI 解读（上次模型不可用时用）
- `DELETE /api/assessments/{id}` 删除一条记录

> 所有自评页面（入口 / 答题 / 结果）都固定显示
> 「仅供参考，如有不适请前往线下医院就医」，答题页提交按钮下方也再写一次。
> `POST /api/assessments` 带 `@PreAuthorize("!hasRole('STAFF')")`，客服账号只能看，不能作答。

### 后台（需 ADMIN 角色）

- `GET /api/admin/users` 用户列表
- `PUT /api/admin/users/{id}/status` 启用/禁用用户
- `DELETE /api/admin/users/{id}` 删除用户（级联清理该用户的日记、标签关联、情绪统计、帖子及其评论/点赞、自己发的评论/点赞、相关举报、与客服的会话及其消息；如果被删账号是客服/管理员，其接手或结单过的会话会把 `assignee_id` / `closed_by_id` 置空，避免客服工作台出现找不到人的会话）
- `GET /api/admin/diaries?page=&size=&userId=` 日记管理（`userId` 可选：传了就只看该账号的日记，
  返回 `AdminDiaryVO`，带作者昵称/用户名与 `crisisSupport` 标记；**正文与 AI 回复恒为 `null`**，
  `contentHidden: true` 提示前端「要点开才能看」）
- `POST /api/admin/diaries/{id}/reveal` 查看日记原文（`{ "reason": "≥5 个字的理由" }`），
  返回带正文的 `AdminDiaryVO`，同时写一条 `admin_audit_log`；**审计写失败直接拒绝查看**
- `DELETE /api/admin/diaries/{id}` 删除日记（清空对应危机预警摘录并留审计）
- `GET /api/admin/posts` / `PUT /api/admin/posts/{id}/status` 帖子管理
- `DELETE /api/admin/posts/{id}` 删除帖子（连同该帖评论、点赞一起清理；指向它的举报与危机预警保留，举报页会显示「(已删除)」，预警页仍可联系当事人）
- `GET /api/admin/comments` / `PUT /api/admin/comments/{id}/status` 评论管理
- `GET /api/admin/reports?status=&page=&size=` 举报列表（`status` 可选：`PENDING` / `HANDLED` / `DISMISSED`，
  不传就是全部；返回 `ReportVO`，带举报人昵称、被举报内容预览 `targetContent` 与处理说明 `remark`）
  / `PUT /api/admin/reports/{id}?action=HANDLED|DISMISSED&remark=` 举报处理（`action` 取 `PENDING` / `HANDLED` / `DISMISSED`，
  `remark` 会随回执通知一起发给举报人）
- `GET /api/admin/reports/pending-count` 待处理举报数（首页通知条 +「举报管理」页签红点）
- `GET /api/admin/crisis-alerts?status=&page=&size=` 危机预警列表
- `GET /api/admin/crisis-alerts/pending-count` 待跟进预警数（后台导航红点）
- `PUT /api/admin/crisis-alerts/{id}` 标记处理（`{ "status": "HANDLED", "remark": "..." }`）
- `PUT /api/admin/crisis-alerts/{id}/follow-up` 回访记录（`{ "note": "..." }`，已处理的预警也能补记）
- `POST /api/admin/users/{id}/unlock` 解除登录锁定（清空 `failed_attempts` / `locked_until`），
  用户列表里锁定中的账号会显示「已锁定」标签 + 解锁按钮
- `GET /api/admin/sensitive-words?page=&size=&keyword=` 敏感词列表 / `POST /api/admin/sensitive-words?word=`
  新增（**查询参数**，不是 JSON）/ `DELETE /api/admin/sensitive-words/{id}` 删除；增删后立即重载词库

> **危机预警与手机号**：`CrisisAlertVO.phone` 返回**打码手机号**（`139****0004`），
> 与「我的」页面一致；需要联系当事人时走站内沟通或由当事人自己留下联系方式。
> 预警里的 `content` 只是**命中关键词前后几十个字**的摘录，不是日记全文 ——
> 要看全文必须走 `/api/admin/diaries/{id}/reveal` 并留痕。

> **管理员自我保护**：`PUT /users/{id}/status`（禁用）与 `DELETE /users/{id}`
> 会拒绝操作当前登录账号，分别返回 `1005 不能禁用当前登录的账号`、
> `1006 不能删除当前登录的账号`；同时始终保留至少一个可用管理员（`1007`）。
> 因此「把自己禁用掉导致后台进不去」这种情况不会再发生。

---

## 🗄️ 数据库核心表

`user`、`diary`、`tag`、`diary_tag`、`treehole_post`、`post_comment`、`post_like`、`report`、`emotion_daily_stat`、`crisis_alert`、`support_conversation`、`support_message`、`notification`、`sensitive_word`、`assessment`（详见 `backend/src/main/resources/db/schema.sql` 与各自的 `upgrade-vX.Y.sql`）。

v3.2 起 `diary` 多了 `deleted_at`（回收站，`NULL` 表示未删除）、`report` 多了 `remark`（处理说明），
`crisis_alert` 多了 `follow_up_at` / `follow_up_note` / `follow_up_by`（回访记录）。
老库升级执行 `backend/src/main/resources/db/upgrade-v3.2.sql`（幂等，dev 启动时自动跑）。

v3.3 起 `user` 多了 `failed_attempts`（连续密码错误次数，默认 0）与 `locked_until`
（锁定到期时间，`NULL` 表示未锁定），老库执行 `upgrade-v3.3.sql`。

v3.5 新增 `assessment`（心理自评结果）：

- `user_id`、`scale_key`（`phq9` / `gad7` / `phq4`）、`scale_name`
- `answers`（`"1,2,0,..."`，下标即题号，保留原作答以便复算）
- `total_score`、`max_score`、`severity`（`NONE` / `MILD` / `MODERATE` / `MODERATELY_SEVERE` / `SEVERE`）
- `risk_flag`（PHQ-9 第 9 题是否阳性）、`ai_interpretation`、`ai_source`（`AI` / `MOCK`）、`created_at`
- 索引 `idx_user_time`（`user_id, created_at`）、`idx_user_scale`
- **题库不落库**：题目、选项、阈值、建议都是代码里的常量，表里只存「谁在什么时候得了多少分」

老库执行 `upgrade-v3.5.sql`。生产库不会自动建表，`spring.sql.init` 只在 `dev` 上开着。

v2.0 新增：

- `user.phone`（`varchar(20)`，唯一索引 `uk_phone`）—— 注册必填，既是账号找回凭据，也是危机干预时的联系方式
- `crisis_alert` —— 危机预警：`user_id`、`target_type`（DIARY / POST / COMMENT）、`target_id`、
  `keyword`、`content`（内容快照）、`status`（PENDING / HANDLED）、`remark`、`handled_at`、`created_at`

v2.2 新增：

- `post_comment.parent_id`（`bigint`，`NULL` 表示直接评论帖子）+ 索引 `idx_parent` —— 树洞评论回复

v2.3 新增（今天的天气 + 协议留痕，都是加列，不动老数据）：

- `user.agreement_version`（`varchar(20)`）—— 同意过的协议版本，`NULL` 表示没同意过
- `user.agreement_accepted_at`（`datetime`）—— 同意时间，用户可在「设置 → 隐私与授权」里查看
- `user.location_consent`（`tinyint`，默认 `0`）—— 是否授权定位与天气
- `user.location_consent_at`（`datetime`）—— 授权 / 撤回时间

v2.4 新增（评论区置顶 + 客服会话）：

- `post_comment.pinned_at`（`datetime`，`NULL` 表示未置顶）+ 索引 `idx_comment_pinned (post_id, pinned_at)`
- `support_conversation` —— 客服会话：`id`、`user_id`（唯一索引 `uk_user`，一个用户只有一条）、
  `assignee_id`（接手的客服，`NULL` 表示待接手）、`last_message`、`last_message_at`、
  `user_unread`、`staff_unread`、`created_at`、`updated_at`
- `support_message` —— 客服消息：`id`、`conversation_id`、`sender_id`、`sender_role`（USER / STAFF / ADMIN）、
  `content`、`created_at`
- `user.role` 的取值多了 `STAFF`（客服）

v3.0 新增（好友 + 私聊 + AI 周报，全是新表，不动任何老数据）：

- `friend` —— 好友关系：`user_id`、`friend_id`、`created_at`，
  唯一索引 `uk_user_friend (user_id, friend_id)`。**成为好友时插两行（A→B、B→A）**，
  这样「我的好友」永远是 `WHERE user_id = me`，不用到处判断方向
- `friend_request` —— 好友申请：`from_user_id`、`to_user_id`、`message`（附言，≤ 100 字）、
  `status`（PENDING / ACCEPTED / REJECTED）、`handled_at`、`created_at`。
  **故意不加 `(from, to, status)` 唯一索引** —— 被拒之后要允许重新申请，
  「同时只有一条 PENDING」由业务层保证（`friend_request` 表更像一份记录，而不是状态机）
- `chat_message` —— 私聊消息：`from_user_id`、`to_user_id`、`content`（≤ 1000 字）、
  `read_at`（`NULL` = 未读）、`created_at`，索引 `idx_pair (from_user_id, to_user_id, id)` 专供
  「按会话翻页」，`idx_to_unread (to_user_id, read_at)` 专供未读计数
- `emotion_weekly_report` —— AI 周报缓存：`user_id`、`week_start`（周一）、`diary_count`、
  `avg_score`、`dominant_label`、`keywords`、`content`、`source`（AI / MOCK）、`created_at`、`updated_at`，
  唯一索引 `uk_user_week (user_id, week_start)` —— 同一个人同一周只调用一次大模型

v3.0 还改了法律文本：《用户协议》新增「好友与私聊」一节、《隐私政策》新增
「好友与私聊信息」一节，**协议版本从 `1.0` 升到 `1.1`**
（`frontend/src/config/legal.js` 与 `com.xinyu.common.Legal.CURRENT_VERSION` 必须同步），
所以老用户下次登录会重新弹一次「政策更新」确认 —— 这是设计如此：私聊数据的处理方式变了。

老库升级执行一次（幂等，可重复跑）：

```bash
mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v2.sql
# v2.2 新增了 post_comment.parent_id，生产库（sql.init.mode: never）需要再补这一条
mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v2.2.sql
# v2.3 新增了 user 表上的 4 个协议 / 授权字段
mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v2.3.sql
# v2.4 新增了 post_comment.pinned_at 与 support_conversation / support_message 两张表
mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v2.4.sql
# v2.4.1 给 support_conversation 补了 status / closed_at / closed_by_id / session_start_id（结单用）
mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v2.4.1.sql
# v3.0 新增friend / friend_request / chat_message / emotion_weekly_report 四张表（CREATE TABLE IF NOT EXISTS）
mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v3.0.sql
# v3.3 给 user 补了 failed_attempts / locked_until（登录失败锁定用，默认 0 / NULL）
mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v3.3.sql
# v3.4 给 diary 补了 client_uid 与唯一键 uk_user_client_uid（离线补传幂等用）
mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v3.4.sql
# v3.6 安全加固：user.must_change_password / user.token_version、
#      admin_audit_log（含 target_user_id，老库里没有这一列会自动补）、历史危机预警摘录收敛成短文本
mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v3.6.sql
```


> 已注册但没手机号的老用户不会被踢下线，登录后前端会弹「绑定手机号」提示
> （`admin` 除外），可以「稍后再说」，但换号/找回需要先绑定。

> `upgrade-*.sql` 都用 `information_schema` 做过判断，重复执行是空操作；开发环境由
> Spring 的 `spring.sql.init` 自动执行（`application-dev.yml` 里的 `schema-locations`），
> **生产环境 `sql.init.mode: never`，必须手动跑一遍上面的命令**。

---

## 🧠 心理自评怎么做的（答辩可讲的设计取舍）

三份量表：**PHQ-9**（抑郁，9 题 / 0–27）、**GAD-7**（焦虑，7 题 / 0–21）、
**PHQ-4**（抑郁 + 焦虑快速筛查，4 题 / 0–12，带抑郁 / 焦虑两个子维度）。
每题四个选项：完全不会 0 / 好几天 1 / 一半以上时间 2 / 几乎每天 3，统一对应「最近两周」。

1. **题库和阈值只有一份**：全在 `backend/.../service/AssessmentScaleRegistry.java`，
   前端通过 `GET /api/assessments/scales` 拿定义来渲染。改阈值、改文案只动一处，
   不会出现「后端说中度、前端显示轻度」。**分数永远由后端按答案重算**，前端传分数没用。
2. **只存结果，不存题目副本**：`assessment` 表存 `scale_key` + `answers` + 得分 + 档位。
   题库升级后老记录仍能按新阈值重新解释，因为答案还在。
3. **AI 只看分数，不看日记正文**：生成解读的入参是「量表名 + 总分 + 档位 + 各维度分 +
   非零题号 + 最近 14 天的日记条数/均分/主导标签」，**不含任何日记文字**。
   提示词里明确禁止诊断、禁止提药名、建议一律落到「去线下医院面诊」。
4. **AI 挂了也有结果**：模型报错或当天额度用完时返回 `aiSource = "MOCK"` +
   本地拼的解读文本，结果页给一个「重新生成解读」按钮，功能不会因此不可用。
5. **危机不看 AI 心情**：PHQ-9 第 9 题（「有不如死掉或伤害自己的念头」）只要不为 0，
   后端直接 `risk_flag = true`，结果页顶部弹危机卡片（12356 / 120 / 110），
   同时写一条 `crisis_alert`（`target_type = "ASSESSMENT"`）给管理员跟进。
   这一条不经过 AI、不经过关键词库 —— 宁可多提示一次，也不能漏。
6. **免责声明是硬约束**：三份自评页面固定显示
   「本量表为国际通用的自评筛查工具，结果仅供参考，不构成医学诊断，也不能替代面诊。
   如有不适请前往线下医院就医。」这段文字由后端随量表下发（`disclaimer` 字段），
   前端另有 `MedicalDisclaimer` 组件常驻，页面改不动也删不掉。
7. **政策一起升到 v1.3**：《用户协议》新增「心理自评量表」一节、《隐私政策》新增同名一节
   （写清只存分数、AI 只收统计数据）、《免责声明》新增「自评量表不是诊断」一节。
   老用户登录后会重新确认一次协议，不点同意进不去。

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

**危机干预**：检测到「自杀 / 自残 / 轻生」等关键词时，后端不依赖 AI，保存日记时就把关怀文案写进 `ai_reply`，并把 `ai_status` 置为 `CRISIS`（跳过 AI 调用，省 token 也避免模型把话题带偏）；即使后来点了「生成 AI 回复」，走的也是同一段文案。同时写入一条 `crisis_alert`，管理员在「关怀预警」页面能看到发帖人注册时填的手机号并标记已跟进。预警里只存 500 字以内的正文摘要（`content` 列宽就是 `varchar(500)`，截断时为省略号留了一位，超长会让整条预警写不进去），不做全文存档。

---

## 🛡️ 安全与隐私

- JWT 无状态认证，密码 BCrypt 加密
- 树洞默认匿名展示，不暴露作者身份
- 敏感词检测（`sensitive-words.txt`）
- **昵称 / 用户名违禁词**（`forbidden-names.txt`）：国家领导人相关称呼，
  以及涉黄涉赌涉毒词汇，
  命中返回 `1014 该昵称包含违规词，请换一个`
- **图形验证码**：自绘 4 位 PNG（`CaptchaService`，字符集去掉了易混的 `0O1Il`），
  答案只存在服务端内存，一次性消费；答错不消费（可以重试），答对即失效
- **短信验证码**：`SmsCodeService`，60 秒重发冷却、单条一次性、每日发送上限；
  **必须先通过图形验证码**才会真的发短信，挡住脚本刷短信
- Redis 限流（AI 日配额、发帖频率），Redis 不可用时自动放行
- **登录防爆破（v3.3）**：失败次数**落 MySQL**（`user.failed_attempts` / `user.locked_until`），
  不依赖 Redis —— 因为本机演示环境常常没起 Redis，而「锁定」这种保护一旦降级就等于没有
  - 普通账号：连续 5 次密码错误锁 15 分钟；`ADMIN` / `STAFF`：3 次锁 30 分钟
  - 锁定期内**即使密码正确也拒绝**，否则在窗口里试中就能继续用
  - 按 IP 再加一层滑动窗口（10 分钟 20 次失败即 `429 登录尝试过于频繁`），挡「一个密码试一堆账号」的撞库；
    只存内存，重启即清空
  - 用户名不存在时只记 IP 次数，不报「用户不存在」，避免被当成查号工具
  - 管理员在「后台 → 用户管理」能一键解锁（`POST /api/admin/users/{id}/unlock`），不用干等锁定时间
  - 计数**不进事务**：登录失败要抛异常，`@Transactional` 会把「失败次数 +1」一起回滚，锁就永远涨不上去
- **密码强度（v3.3，`PasswordPolicy`）**：8-32 位、可打印 ASCII（不含空格）、必须同时含字母和数字、
  拒绝常见弱口令（`12345678` / `password` / `admin123` / `xinyu1234` …）、不能是同一字符重复、
  不能包含用户名（≥3 字时判断）；命中返回 `1027` 并说明具体原因
  - 只对新密码生效（注册 / 改密 / 建号），**老账号不影响登录**，否则演示账号直接进不去
  - 前端 `frontend/src/utils/password.js` 是同一套规则的镜像，只做预校验，最终以后端为准
- 举报 → 管理员审核 → 下架／删除／封禁
- **管理后台看不到日记正文（v3.6）**：列表只返回日期、情绪分、情绪标签、隐私级别；
  要看全文必须 `POST /api/admin/diaries/{id}/reveal` 并**填写理由（≥5 字）**，
  每次查看都会写进 `admin_audit_log`（谁、何时、哪一条、为什么），审计写失败就不给看
- **JWT 可吊销（v3.6）**：`user.token_version` 进 token 的 `tv` 声明，
  改密码 / 换手机号 / 退出登录时 +1，旧 token 立刻失效（`POST /api/auth/logout`）
- **令牌不再到处撒（v3.6）**：`?token=` 只对 `/stream` 结尾的 SSE 接口生效，
  其它接口一律只认 `Authorization` 头（URL 会进 access log、历史记录、Referer）
- **首个管理员由环境变量引导（v3.6）**：仓库里不再有默认管理员，`ADMIN_INIT_PASSWORD`
  必须满足密码强度，且账号带 `must_change_password=1`，改密前除「我的信息 / 改密码 / 同意协议」外全部 403（`1028`）
- **危机关键词收紧（v3.6）**：只有明确的自伤意图 / 计划 / 告别才算高危（`不想活` `想去死` …），
  「太累」「没意思」这类低危词只做温和回应、不生成预警；带否定的句子（`我没有不想活`）不误报。
  预警摘录只保留**命中词前后共几十字**，历史长摘录由 `upgrade-v3.6.sql` 收敛
- **容器不再裸奔（v3.6）**：`docker-compose.yml` 里 MySQL / Redis / 8080 都不再映射到宿主机，
  Redis 强制 `--requirepass`，MySQL 密码走 `${...:?}`（不填直接启动失败）；
  需要 HTTPS 时用 `docker-compose.tls.yml` + `nginx/nginx-tls.conf`
- 密钥只从环境变量注入，仓库内无任何真实 Key（见 [🤖 AI 配置](#-ai-配置)）
- 曾经出现在代码或聊天记录里的 Key，上线前必须吊销重建
- **协议留痕**：注册强制勾选同意，老用户升级后必须读完弹窗才能继续用，
  同意版本 + 时间存在 `user.agreement_version` / `user.agreement_accepted_at`
- **定位需单独授权**：默认关闭，坐标只留在浏览器、不上传服务器，随时可撤回并清除缓存

### 手机号的处理（隐私权衡，答辩可讲）

| 场景 | 返回 | 说明 |
| --- | --- | --- |
| 「我的」页面 / `GET /api/user/me` | `138****8000` | 一律打码 |
| 后台用户列表 | 打码 | 管理员日常也不看全号 |
| 后台危机预警 `/api/admin/crisis-alerts` | 打码 | 预警列表也不给全号，需要联系当事人时由管理员在站内沟通 |

> 早期版本曾在危机预警里返回明文手机号，v3.6 起统一改成打码：
> 「能看到工单」不等于「有权拿到电话号码」，能少给一个人就少给一个人。

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

## 📴 离线可用（断网也能写日记）

目标很朴素：**地铁里没信号、家里断网，也不该把刚冒出来的情绪弄丢**。

### 用户看到的行为

| 场景 | 表现 |
| --- | --- |
| 后端没启动时打开登录页 | 约 3 秒内出现「连不上服务器」，按钮变成「离线登录」，点一下账号即可进入（不再熬 30 秒「请求超时」） |
| 断网时打开「写日记」 | 页面正常打开，顶部出现「离线中 · 仍可继续写日记」 |
| 断网时保存 | 提示「已离线保存，联网后自动同步」，跳进详情页，标题旁标「待同步」 |
| 断网时看日记列表 / 详情 | 有缓存就照常显示（不弹任何错误提示），没缓存才说明「这台设备还没缓存到」 |
| 网络恢复 | 自动补传，待同步数量清零，详情页从 `local_xxx` 跳到服务器分配的正式 id |
| 断网时的 AI 回复 | 按钮置灰——模型在服务端，没网确实生不成，这一点不假装能用 |

### 三个关键设计

1. **本地队列 + 幂等补传**
   保存失败（不是业务报错，而是 `error.offline`）时，日记进 IndexedDB 的 `outbox`，
   本地 id 形如 `local_<随机串>`。每条都带一个 `clientUid`，补传时原样带给后端：
   `diary` 表上的唯一键 `uk_user_client_uid (user_id, client_uid)` 保证
   **重试多少次都只会落一条**（`DiaryService.create()` 先按 `clientUid` 查、撞唯一键再回查）。
   这也顺手解决了「请求超时但其实已经写成功了」这种最容易写出重复数据的场景。

2. **读接口的旧数据兜底**
   `GET` 类接口（`/diaries`、`/treehole`、`/user`、`/notifications` …）成功一次就顺手存进
   IndexedDB（最多 400 条，超出按旧换新）。断网时如果请求失败且本地有副本，
   直接返回副本 —— 不弹提示，因为**内容照常看得见**，再报一句「网络异常」只是添乱。
   `/auth`、`/admin` 不进缓存：前者带一次性验证码，后者管理员在电脑上处理，存到手机没有好处。
   第一页没有完全对应的副本时，会退一步拿同一接口最近的另一份（首页要 `size=5`、日记页要
   `page=1&size=10`，键不同但内容能顶上）；翻页不复用，避免同一批内容重复出现。

3. **后端没启动也能进去（离线登录）**
   请求层先探一次 `GET /api/auth/ping`（`AuthController#ping`，纯存活探测，不碰数据库和 Redis）。
   探不通就开一个 15 秒的**静默窗口**：窗口内的请求直接失败，交给缓存和待同步队列兜底，
   所以首页、日记列表基本是瞬间出内容，而不是每个请求各熬 30 秒。
   经过 nginx 时后端没起是 `502/503/504` 而不是网络错误，这一组状态码也按「连不上」处理。

   登录页据此切换成离线模式，密码用 `frontend/src/offline/credential.js` 在**本机**校验：
   登录成功时把 `salt + PBKDF2-SHA256(120k 轮)` 的校验值（不是密码）存进 `localStorage`
   的 `xinyu_offline_cred`，最多留 5 个账号；离线登录时用同样的盐重算一次比对。
   密码本身**不落地**，退出登录不会清掉（否则断网时用过的账号就再也登不进去），
   注销账号时会 `forgetAccount()` 一并删除。
   非安全上下文（例如 `http://192.168.x.x` 这种手机局域网地址，没有 `crypto.subtle`）会退化成
   FNV 加盐哈希，只为「本机便利校验」，不能当真实凭证用。

4. **离线页面预下载**
   H5 是按路由分包懒加载的，**没访问过的页面没有对应 JS，断网后再点就是白屏**。
   登录后空闲时 `prefetchOfflinePages()` 会提前把写日记 / 列表 / 详情 / 首页四个 chunk 拉下来；
   万一还是没下到，`router.onError` 会提示「当前离线，这个页面还没下载过」而不是干等。
   打包成 APK 后静态资源在本地，不存在这个问题。

### 存储位置与隐私

- 独立数据库 `xinyu-offline`，三个 store：`outbox`（待同步）、`cache`（接口副本）、`meta`
- 只存在**这台设备**的浏览器里，不上传；退出登录会清掉接口缓存（避免下一个人登录看到上一个人的日记），
  **但保留待同步队列** —— 不然断网时手一滑退出登录，刚写的东西就没了
- 「我的 → 设置 → 离线与同步」可以看待同步数量、手动补传、一键清空本地缓存；
  注销账号会连队列一起清

### 已知取舍（答辩可以主动讲）

- **删除不排队**：删一条已经同步到服务器的日记需要联网。删「待同步」的本地条目直接本地抹掉。
  理由：一半删成功一半没删的状态最难跟用户解释。
- **危机关键词在前端也有一份镜像**（`frontend/src/offline/crisis.js`）。
  离线存下的日记拿不到 AI 回复，但至少能在本地识别出危机信号并显示求助提示。
  这份镜像和后端 `CrisisKeywordService` 必须同步维护 —— 这是刻意接受的重复。
- **离线写的日记没有情绪分 / 标签**，这些要等补传后由 AI 补上（`ai_status` 变 `PENDING → DONE`）。

### 生产环境升级

`v3.4` 给 `diary` 加了 `client_uid` 列和唯一键，老库执行一次（幂等）：

```bash
mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v3.4.sql
mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v3.5.sql   # 心理自评 assessment 表
```

---

## 🔔 版本号与更新说明

前端在启动时会弹出一次「更新说明」弹窗，只有**版本号变化**时才会再弹，
用浏览器 `localStorage`（key：`xinyu:notice:version`）记住用户已读的版本号。

更新说明的数据放在 **`frontend/public/changelog.json`**，页面运行时用 `fetch` 读
（`src/config/changelog.js` 只负责加载、校验、缓存），所以**改文字不用重新构建、也不用重新部署**：

```bash
vim frontend/public/changelog.json   # 改完刷新页面（手机下拉刷新）就能看到
```

文件结构：

```json
{
  "version": "3.3.1.3072",
  "changelog": [
    {
      "version": "3.3.1.3072",
      "date": "2026-10-20,1425CST（UTC+8）",
      "items": [
        { "text": "想加粗高亮的重点条目", "highlight": true },
        "普通条目直接写字符串"
      ]
    }
  ]
}
```

发版时：

1. 改 `frontend/public/changelog.json`：递增 `version`，在 `changelog` 数组**最前面**插入同版本号的记录。
   这一步是热更新，改完刷新就生效。
2. `frontend/package.json` 与 `backend/pom.xml` 的 `version` 改成同一个前三段号 —— 这两处只影响构建产物
   命名和启动日志，页面显示的版本号以 JSON 里的 `version` 为准（顺序不能乱，`changelog` 数组最新一条在最前）。

`version` 与某条记录的 `version` 完全一致时，弹窗才会显示那一条（`currentNotice()`）；
对不上就当作这一版没写说明，弹窗不弹，也不会把用户标记成已读。

用户可以在「我的 → 更新说明」里随时手动重新打开弹窗，也可以往上翻看历史更新记录。

**「为什么没弹更新说明？」排查顺序：**

1. `localStorage['xinyu:notice:version']` 已经等于当前版本号 —— 弹过一次并点过「我知道了」就不会再弹。
   `localStorage` 按「协议 + 域名 + 端口」隔离，所以 `http://192.168.1.12`、`http://192.168.1.12:8088`、
   `http://127.0.0.1:5173` 各自记各自的已读版本。
2. `changelog.json` 里没有与 `version` 完全相同版本号的记录 —— `currentNotice()` 返回 `null`，弹窗直接跳过。
3. `changelog.json` 读不到（404 / 断网）—— 会退回上次成功读取的本地副本（`localStorage['xinyu:changelog:cache']`），
   并在控制台打一条 warning；连副本都没有时版本号显示 `—`，弹窗不出现。

**不同部署方式改 JSON 的效果：**

| 跑法 | 改 `changelog.json` 后 |
| --- | --- |
| `npm run dev`（Vite） | 刷新即生效（`public/` 按原样直出） |
| `bash scripts/serve-dist.sh` | 刷新即生效（nginx 里有 `location = /changelog.json` 直指 `frontend/public/`，不用重新构建） |
| Docker 镜像 | `dist` 里的那份是构建时复制过去的，要重新构建镜像；临时改线上机器可 `docker cp changelog.json <容器>:/usr/share/nginx/html/changelog.json` |

Service Worker 特意把 `/changelog.json` 排除在缓存之外（`frontend/public/sw.js`），
否则「改完刷新」会拿到缓存里的旧文案。

当前版本：**v3.3.1.3072**。

### v3.3.1 更新内容（与弹窗 `changelog.json` 保持一致）

- **更新说明改为运行时读取**：版本号和文案从 `frontend/public/changelog.json` 加载，
  以后改说明只改这个 JSON，刷新页面就生效，不用重新 `npm run build`、不用重新部署
- 弹窗、「设置」页、「我的」页显示的版本号统一以 JSON 里的 `version` 为准
- 读不到 JSON 时退回上次成功读取的本地副本，历史更新照样能翻

### v3.3.0 更新内容（与弹窗 `changelog.json` 保持一致）

- **登录防爆破**：连续输错密码临时锁定账号（普通 5 次 / 15 分钟，管理员 3 次 / 30 分钟），
  锁定期内密码正确也进不去；同 IP 10 分钟内失败 20 次直接 `429`
- **密码强度**：注册 / 改密要求 ≥8 位、字母+数字、非弱口令、不含用户名（`1027`，附具体原因）
- **后台一键解锁**：用户列表会标「已锁定」，`POST /api/admin/users/{id}/unlock` 立即放行
- 锁定计数存 MySQL 而非 Redis，Redis 挂掉也不会失去保护（见 [🛡️ 安全与隐私](#-安全与隐私)）

### v3.2.0 更新内容（与弹窗 `changelog.json` 保持一致）

1. **日记改为逻辑删除 + 回收站** —— 删除日记不再直接 `DELETE`，而是写 `deleted_at`；
   在「设置 → 回收站」里 7 天内可以恢复，也能彻底删除，超过 7 天访问回收站时自动清理。
   实现上给 `Diary.deletedAt` 加了 `@TableLogic(value = "null", delval = "now()")`，
   列表、趋势、分布、回顾等自动生成的查询都会补上 `deleted_at IS NULL`，不用逐个改统计 SQL。
   升级脚本：`backend/src/main/resources/db/upgrade-v3.2.sql`（dev 启动时自动执行）。
2. **数据导出** —— `GET /api/user/data-export` 返回这个账号的日记、树洞、评论、点赞、好友、通知等，
   前端存成一份 JSON 直接下载，答辩时可以讲「数据可携带权」。
3. **自助注销** —— `DELETE /api/user/account` 必须带登录密码二次确认，删除后不可恢复；
   管理员账号不允许自助注销（避免绕过「不能删掉最后一个管理员」的校验）。
4. **写日记提醒** —— `composables/useReminder.js` 用 `localStorage` 存提醒时间，
   页面开着时每 60 秒判断一次，一天最多提醒一次（纯前端，不是推送）。
5. **月度 / 年度回顾** —— `GET /api/diaries/stats/recap?period=month|year&anchor=`，
   给出篇数、活跃天数、平均情绪分与环比、高频标签 / 场景 / 天气、最长连续天数、最好 / 最难的一天和分段柱状数据。
6. **举报回执与处理说明** —— `report.remark` 存处理说明，
   处理结果通过站内通知（`type=REPORT`）发给举报人；「我的举报」页同时展示状态与说明。
7. **敏感词后台可维护** —— `GET/POST/DELETE /api/admin/sensitive-words`，
   增删后立即 `reload()` 生效；空库时仍会从 `sensitive-words.txt` 自动导入。
8. **PWA** —— `public/manifest.webmanifest` + `public/sw.js`：
   只缓存静态外壳，`/api` 与 `/uploads` 一律不缓存（隐私），导航请求 network-first。
9. **协议升到 v1.2** —— 《隐私政策》第七节改成「你可以自己导出、自己注销」，
   本次会重新弹一次确认。

### v3.1.0 更新内容（与弹窗 `changelog.json` 保持一致）

1. **树洞配图** —— 发帖最多 9 张图，列表显示缩略图，详情可看大图（`treehole_post.images`）。
2. **消息通知** —— 被赞、被评论、好友申请、私聊、客服回复、举报回执统一进「消息通知」，
   「我的」上带未读角标。
3. **情绪洞察** —— 在情绪趋势页看天气 / 场景 / 标签 / 星期与情绪分的关系，样本少于两篇不给结论。
4. **客服账号权限收紧** —— 不能加好友、不能私聊。

### v3.0.0 更新内容（与弹窗 `changelog.json` 保持一致）

1. **新增「好友 + 私聊」**（本次最大的改动）
   - 树洞里点昵称就能进对方主页：非匿名帖子、非匿名评论的昵称都可点，匿名帖不会暴露任何人；
   - 主页上可以直接「加好友」，对方在「我的 → 好友申请」里同意 / 拒绝，未处理的申请有角标；
   - 底部导航新增第 5 个 tab「聊天」，未读消息在 tab 上显示角标，聊天窗口 4 秒刷新一次；
   - 只有好友之间能发消息（`3011 你们还不是好友`），删除好友是双向的，但**双方都还能翻看旧记录**；
   - 客服账号没有这个入口：加好友、私聊、别人的主页，对 `STAFF` 一律关闭。
2. **新增情绪日历热力图** —— 把每天的情绪分画成一格一格的日历，并显示「连续记录 N 天」，
   比折线更容易看出「低的那几天是不是挤在一起」。
3. **新增 AI 情绪周报** —— 汇总上一周的平均情绪分、最多出现的标签，并给一段针对这一周的建议。
   只把聚合统计发给大模型，**不发送任何一条日记原文**；同一周只生成一次，模型不可用时降级为模板文案。
4. **危机预警跟进可填处理备注** —— 管理员处理后能写一句「已联系本人 / 误报」，
   列表里也显示处理人是谁，后面回查时不用只看到「已处理」三个字。
5. **协议版本升到 v1.1** —— 《用户协议》新增「好友与私聊」、《隐私政策》新增「好友与私聊信息」，
   说明私聊内容怎么收集、怎么用（不进 AI、不生成危机工单，只有被举报时管理员才查看）。
   本次会重新弹一次政策确认，点过才能继续使用。
6. **优化了客服账号的权限** —— `STAFF` 只能处理用户咨询：加好友、好友申请、私聊、
   查看他人主页的新接口一律返回 `403 无权限访问`（路由白名单也没有新增 `/chat`、`/friends/*`、`/u/:id`）。
7. **修复了一些过往的 bug 以及本次更新造成的一些错误** —— 例如后台「预警」列表只要有一条
   未处理记录就整页 500（`Map.of()` 取 `null` 处理人抛 NPE）、私聊发送成功后返回的
   `createdAt` 是 `null`（MySQL 的 `DEFAULT CURRENT_TIMESTAMP` 不会回填实体）。

### v2.4.2 更新内容（与弹窗 `changelog.json` 保持一致）

1. 优化了天气卡在手机上的显示 —— 以前「未授权」「定位失败 / 接口报错」这几种状态是单行小字，
   手机上只有 58~61px 高，看起来像一条分割线，用户不知道能点、也不知道能手选城市。
   现在统一成一张「标题 + 一句能看懂的解释 + 真按钮」的卡（实测 162.5px），并且：
   - `http` 打开时**不**把「使用我的位置」放在主位（点了必定被浏览器拦），
     主按钮直接是「手动选择城市（不用定位）」，另给一行 `https://<当前域名>:8443` 的入口；
   - 已授权但没坐标时，点卡片直接开城市选择，不再多弹一次「定位失败」；
   - 手机端字号不再被媒体查询压到 11.5px。

### v2.4.1 更新内容（与弹窗 `changelog.json` 保持一致）

1. 对客服：新增结单功能 —— 客服 / 管理员处理完一条会话后点右上角「结单」，
   该会话标记为「已结单」并给用户留一条系统提示；用户下次再发消息会自动开启新一轮，
   重新分配给客服 / 管理员，**接手人看不到结单之前的记录**（用户自己仍能看到全部历史）。
2. 优化了客服账号的权限 —— `STAFF` 只能处理用户咨询：发日记、发树洞帖子、点赞、评论、
   置顶评论全部返回 `403 无权限访问`；登录后直接进「客服工作台」，
   底部导航只剩「工作台 / 我的」，「我的」页也不再出现「我的日记 / 情绪趋势 / 联系管理员客服」，
   并且客服账号不再弹任何首页弹窗（更新说明、绑定手机号都不弹）。
3. 处理了一些错误 —— 管理员删除账号时会连同该账号与客服的会话、消息一起清理
   （以前会留下一条找不到用户的「幽灵会话」一直挂在客服工作台）；
   被删除的客服/管理员不再占着会话归属，相关会话回到「待接手」、结单人字段置空。

> 弹窗里没展开、只写进文档的改动：
>
> - **`STAFF` 权限矩阵**：`/api/diaries` 的 `POST / PUT / DELETE`、`/api/treehole` 的发帖 / 点赞 /
>   评论 / 置顶评论都加了 `@PreAuthorize("!hasRole('STAFF')")`，越权调用在 `GlobalExceptionHandler`
>   里统一转成 `403 无权限访问`；读取类接口（树洞列表、日记列表）保持可用，避免前端直接白屏。
> - **结单的判定**：只有**接手该会话的客服**或**管理员**能结单（`canClose`），
>   会话必须处于 `OPEN`；已结单的会话再调结单接口返回 `2003 该会话已结单，用户再次咨询后会重新分配`，
>   结单后客服侧也发不出消息（同样 `2003`），想继续跟只能等用户先说话。
> - **每一轮的消息隔离**：用户发新消息触发重开时，后端把 `session_start_id` 记成
>   「新消息 id - 1」，客服侧只返回 `id > session_start_id` 的消息，
>   所以结单提示与上一轮记录都不会出现在新接手人的界面里；用户侧不过滤，历史一条不少。
> - **每版演示数据都会往前推一格**：`seed-demo.sql` 末尾按版本追加了一段 demo01~demo05 的
>   日记 / 树洞帖子 / 评论 / 危机预警（本次新增 10 篇日记、5 条帖子、3 条待跟进预警），
>   全部用「内容不存在才插入」的方式判断，重复执行不会产生重复数据。

### v2.4.0 更新内容（与弹窗 `changelog.json` 保持一致）

1. 更新了联系管理员/客服入口（弹窗里标红）—— 「我的」页新增入口，用户可以直接给管理员/客服留言；
   管理员/客服在「客服工作台」看到会话列表并回复，首次回复即接手该会话
   （其他客服看不到、也进不去），管理员首页与用户首页都有未读提醒通知条。
2. 新增树洞内评论区评论置顶功能 —— 贴主可以把任何人（包括自己）在自己帖子下的留言置顶，
   置顶那条排在评论区最上面并带「置顶」标签，再点一下「取消置顶」即可；
   一条帖子同一时间只有一条置顶评论，非贴主看不到这个按钮。
3. 用户可查看过往的更新记录 —— 更新说明弹窗底部新增「查看历史更新」，
   进去后按时间倒序列出 `CHANGELOG` 里的全部版本；当当前版本还没写文案时，
   弹窗会直接打开历史列表（顶栏显示「共 N 条历史记录」），不会再是一片空白。
4. 修复了一些过往的 bug 以及本次更新造成的一些错误（弹窗里以注释形式保留原文）：
   客服会话标题重复显示成「客服 · 客服 · 小语」、取消置顶会把同一帖子下其它评论的置顶一起清掉、
   置顶评论后评论区把同一批留言渲染了两遍、客服会话被取消接待后详情页空白且没有任何提示。
5. （我的酒快喝完了 TAT，在更新期间，网页如有报错请谅解，更新时间已经尽可能的到深夜了。
   但是不管什么时间更新都有人用，对此造成的不变请各位谅解。）

> 弹窗里没展开、只写进文档的改动：
> 管理员首页新增举报通知条 —— 和危机预警同一个位置、同一套样式，显示「有 N 条举报待处理」
> 与被举报原因，点一下直接进「举报管理」；举报页签带红点角标，列表支持待处理 / 已处理 /
> 已忽略 / 全部四种筛选。`GET /api/admin/reports?status=` 与 `GET /api/admin/reports/pending-count`
> 的条数已实测一致。

### v2.3.2 更新内容（与弹窗 `changelog.json` 保持一致）

1. 修复部分 UI 的样式显示问题 —— 顶部「心语」标题栏原来是一块「贴上去的玻璃板」：
   1px 描边、Vant 自带的下发丝线、大投影和内侧 1px 高光边，而且只和内容列一样宽
   （375px 屏里只有 347px），深色模式下那条内侧高光渲染成一条很亮的白线，
   看起来就像把标题栏和页面切开。现在顶栏本身透明，磨砂层挪到 `::before` 上，
   左右各 `-50vw` 铺满整屏、往上盖住页面内边距，并用 `mask-image` 让染色和模糊一起往下淡出
   （`mask` 只能加在伪元素上，直接加在顶栏会把标题文字一起淡掉）。
2. 修复了部分已知 bug。
3. 修复了底部状态栏与主页面显示不一致的问题 —— `TabBar.vue` 判断当前该高亮哪个 tab 时
   把 `/diaries` 写成了 `/dai`，而 `'/diaries'.startsWith('/dai')` 是 `false`，
   所以从没命中过，永远退回高亮「首页」。点「日记」时内容切到了 `/diaries`，
   底部高亮却还停在首页。

> 具体做法（不在弹窗里展开，只写在文档里）：
> 顶栏的前后对比用逐行像素亮度量过，深色模式下旧样式的最大行间跳变是 **120.9**（那条白边），
> 新样式降到 **1.5**，即整段是平滑渐变；浅色模式两版都不明显（0.3 / 1.0）。
> 底部导航胶囊位移逐页实测：`/` → 0、`/diaries` → 100%、`/treehole` → 200%、
> `/trend` → 100%（趋势页归到「日记」tab）。12 处 `<van-nav-bar>` 用法全部统一，无冲突。
> 这两处都只改前端，后端没有接口/表结构变化；`backend/pom.xml` 的 `<version>` 仍按惯例同步到 `2.3.2`，
> 但当前正在跑的后端进程仍是 `xinyu-backend-2.3.1.jar`（`Starting XinyuApplication v2.3.1`），
> 重启一次后端即可对上，功能上没有任何差别。

### v2.3.1 更新内容（与弹窗 `changelog.json` 保持一致）

1. 修复了手机浏览器无法自动读取位置的问题。
2. 定位失败时会说清是哪种情况：当前不是 HTTPS / 权限被拒绝 / 暂时定不到位 / 定位超时了，并给出对应做法
3. 天气卡在拿不到位置时不再卡在加载骨架屏，可以「重新定位」或「换个城市」，城市选择页支持搜索，并按手机时区预填一个默认城市
4. 定位增加多级兜底

> 具体做法（不在弹窗里展开，只写在文档里）：手机要拿到真实定位必须走 HTTPS，见上文的
> [手机定位必须走 HTTPS](#手机定位必须走-httpsv23-天气功能)；定位链路是
> `navigator.permissions` 预检 → 低精度 → 高精度重试 → `watchPosition` 兜底；
> `scripts/serve-dist.sh` 已支持 `SSL=1` 自动生成自签证书，与 HTTP 实例可同时运行。

### v2.3 更新内容（与弹窗 `changelog.json` 保持一致）

1. 首页新增「今日情绪天气」：按用户授权的位置显示实时天气与一句陪伴，写日记时自动填好天气
   （位置需单独同意，坐标只留在浏览器里、不上传服务器，原理见
   [🌤️ 今日情绪天气](#-今日情绪天气需用户单独同意)）
2. 新增用户协议、隐私政策、免责声明三份文本，注册时必须先勾选同意
3. 老用户升级后弹出一次「政策更新」，关键条款标红，读完上面的内容才能继续使用
4. 「我的 - 隐私与授权」新增定位与天气开关、三份协议入口，以及用户的同意记录（版本号 + 时间）
5. 手机端整体收紧间距与字号：首页不再三张卡堆满一屏，设置页与列表页一屏也能多放一行；
   PC / 平板布局不变（新的密度规则统一放在 `@media (max-width: 767px)` 里）
6. 后端新增 `POST /api/user/agreement`、`PUT /api/user/location-consent`，
   `user` 表新增 4 个协议 / 授权字段（`upgrade-v2.3.sql`）

### v2.2.3 更新内容（与弹窗 `changelog.json` 保持一致）

1. 修复了一些已知 bug
2. 修复了日记处退出就没有草稿的严重 bug：写日记时会自动存草稿，误退出再回来能接着写
3. 修复了偶现的危机提示无法显示的问题：后台长日记滚动后关怀提示会被滚出屏幕；命中危机词的日记现在会在保存时直接写入关怀回复，不用手动生成 AI 回复也能看到
4. 情绪趋势页的「日趋势」现在会显示最近 3 日的变化，还能一天一天往回翻看更早的趋势（后端 `GET /api/diaries/stats/trend?range=day&end=YYYY-MM-DD`）
5. PC 端树洞评论按回车即可发送，需要换行请按住 Command / Windows 键再按回车
6. 修复了长日记（正文超过 500 字）漏报危机预警的严重 bug：摘要上限是 500 字，
   而截断后还会再拼一个省略号，写库时变成 501 字，触发 `Data too long` 整条预警静默失败
   （预警写入是「失败也不能影响用户发日记」的旁路逻辑，异常被吞掉，所以只有长日记会偶发漏报）。
   现在摘要按「含省略号不超过 500 字」截断，`CrisisAlertService` 里也补了注释说明
7. 新增：更新说明弹窗支持把某一条**标红**（`{ text: '…', highlight: true }`），本次标红的是第 2 条
8. 顺带：管理员在后台查看日记时只保留详情顶部的危机提示条，不再弹出用户端的关怀弹窗
   （这条在弹窗里被 `//` 注释掉了，用户端不展示）
9. 已用脚本给历史上「命中危机词但没有任何预警记录」的日记补了预警，管理员首页的预警通知恢复正常

### v2.2.2 更新内容（与弹窗 `changelog.json` 保持一致）

1. 修复了报错弹窗（Toast / Notify）在浅色模式下的文字颜色问题
2. 修复了部分错误
3. 顺带补上：80 端口场景下 nginx 访问地址不再显示多余的 `:80`

### v2.2.1 更新内容（与弹窗 `changelog.json` 保持一致）

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

### v2.0 更新内容（与弹窗 `changelog.json` 保持一致）

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

## 🌤️ 今日情绪天气（需用户单独同意）

首页问候语下面那张卡片：按用户所在地显示实时天气，配一句和情绪有关的话。
写日记时也会顺手把「天气」填好（用户随时可以改）。

数据源全部是免费、免 Key、可在浏览器直接调的接口：

| 用途 | 接口 | 说明 |
| --- | --- | --- |
| 实时 + 当日天气 | `api.open-meteo.com/v1/forecast` | 取 `temperature_2m` / `weather_code` / `is_day` 与当日最高最低 |
| 城市搜索（手选城市） | `geocoding-api.open-meteo.com/v1/search` | 中文名直接搜，返回候选让用户挑 |
| 反查城市名（用定位时） | `api.bigdatacloud.net/data/reverse-geocode-client` | Open-Meteo 没有逆地理编码；这个接口**必须跟随重定向**（curl 要加 `-L`，浏览器自动跟），失败就只显示「当前位置」 |

### 隐私设计（答辩可讲的三点）

1. **先同意，后定位**。默认不请求定位；用户在首页卡片或「设置 → 隐私与授权」里
   明确同意后才调用定位（统一走 `src/utils/geo.js`：网页用 `navigator.geolocation`，
   APK 用原生 `@capacitor/geolocation`）。同意动作会写进
   `user.location_consent` + `user.location_consent_at`，留下不可抵赖的时间戳。
2. **坐标不上传心语服务器**。经纬度只存在浏览器 `localStorage`
   （`xinyu:weather:coords`），由前端直接向天气服务查询；后端从头到尾没见过它。
   关闭授权时连同天气缓存（`xinyu:weather:snapshot`）一起删除。
3. **不想给定位也能用**。可以改成手动搜索城市，功能完全一样，这条路不碰任何权限。
4. **装成 APK 时是真的系统权限**。网页版点同意走的是浏览器授权；APK 里走
   `@capacitor/geolocation`，弹出的是 Android 系统对话框（`ACCESS_FINE_LOCATION` /
   `ACCESS_COARSE_LOCATION` 只在用户点「使用我的位置」时才申请，不是开机就问）。
   拒绝后不再重复弹窗，直接给文案 + 手动选城市；用户可以随时去
   「设置 → 应用 → 心语 → 权限 → 位置信息」改回来。
   > 侧载安装时系统提示「恶意应用 / 有风险」是自签名证书 + 非商店渠道的启发式提醒，
   > 不是包里有恶意代码，处理方法见 [docs/APK打包指南.md](docs/APK打包指南.md) 6.2。

另外还有两个小设计：

- 天气缓存 30 分钟（`useWeather.js` 里的 `SNAPSHOT_TTL`），来回切页面不会把免费接口打爆；
- 每天第一次打开首页时，用一条 Vant `showNotify` 把天气「推」给用户
  （`xinyu:weather:notified` 记录当天已推过），只推一次，不骚扰。

### 卡片有哪几种样子（v2.4.2 起）

`WeatherCard.vue` 里用 `promo` 计算属性把「未授权 / 没坐标 / 接口报错」三种情况合并成同一张卡，
避免出现「一行小字不知道能不能点」的形态（实测 390×844：http 未授权 162.5px、https 未授权 104px、没坐标 104px、正常天气卡 94.9px）：

| 情况 | 卡片长什么样 | 主按钮 |
| --- | --- | --- |
| 没授权 · `https` / `localhost` | 今日情绪天气 | 使用我的位置 |
| 没授权 · `http` 打开 | 今日情绪天气（说明「http 下浏览器禁止定位」） | 手动选择城市（不用定位），另附 https 入口 |
| 已授权 · 没坐标 | 选个城市看天气 | 手动选择城市 |
| 已授权 · 接口报错 | 暂时拿不到天气（显示真实错误） | 重试 |

`isSecurePage()`（`useWeather.js`）判定当前页面能不能定位：只有 `window.isSecureContext`
或 `localhost` / `127.0.0.1` / `::1` 才算安全上下文，所以用局域网 IP 走 `http` 时
`requestLocation()` 会在调接口之前就以 `reason: 'insecure'` 短路返回 —— 这是浏览器策略，
不是 bug，因此这类页面的正确做法是**引导用户手选城市**，而不是给一个点了必然失败的定位按钮。

`https://<当前域名>:8443` 这条入口指向 `~/.xinyu-nginx/nginx-8443-ssl.conf`（自签证书
`CN=xinyu.local`），手机访问时需要手动点过证书警告；能不能在过警告后拿到定位权限
跟浏览器版本有关，手选城市始终是更稳的那条路。

`composables/useWeather.js` 是模块级共享状态，首页卡片和写日记页共用同一份数据，
所以从首页走进写日记页不会重复请求。

---

## 📜 用户协议、隐私政策与免责声明

v2.3 新增三份法律文本，前端集中在 `frontend/src/config/legal.js` 一处维护：

| 文档 | 路由 | 内容要点 |
| --- | --- | --- |
| 用户协议 | `/legal/terms` | 账号规则、内容规范、树洞匿名不等于免责 |
| 隐私政策 | `/legal/privacy` | 收集什么、日记可见范围、AI 会拿到正文、定位与天气、危机干预、用户权利 |
| 免责声明 | `/legal/disclaimer` | 不是医疗或心理诊断，危机时请拨 120 / 110 或心理援助热线 12356 |

三份文本共用一个组件 `views/Legal.vue`，靠 `props.docKey` 区分；内容里的
`**两个星号**` 会渲染成高亮红色的关键条款。联系人 / 邮箱在
`LEGAL_CONTACT`（当前：ccZhang / 2947208937@qq.com）。

> 写正文时注意：半角 `**` 是加粗语法，正文里需要出现星号请用全角 `＊＊`
> （隐私政策里的打码手机号就踩过这个坑）。

### 同意是怎么留痕的

- **新用户**：注册页必须勾选同意，`POST /api/auth/register` 不带
  `agreementAccepted: true` 会被参数校验直接拒绝，同意版本与时间写进 `user` 表。
- **老用户**：登录后如果 `GET /api/user/me` 返回 `agreementAccepted: false`，
  `components/AgreementGate.vue` 会弹一个**不可关闭**的「我们更新了政策」弹窗
  （`close-on-click-overlay` 关掉、没有关闭按钮），要点里的关键条款标红，
  **内容必须滚到底**「我已阅读并同意」按钮才会亮起
  （内容撑不满屏幕时自动放行，不会逼用户去滑一个滑不动的框）。
  只有「不同意，先退出登录」这一个出口。
- **可核查**：同意版本与时间在「设置 → 隐私与授权 → 同意记录」里对用户可见。

协议版本号两边必须一致：后端 `com.xinyu.common.Legal.CURRENT_VERSION`
↔ 前端 `config/legal.js` 的 `LEGAL_VERSION`。改文案就要一起改版本号，
否则老用户不会弹出重新确认。

### 危机关键词与「后台只看得到命中的那一条」

隐私政策里明确写了：**日记正文默认只有自己可见，管理员日常操作看不到**；
唯一的例外是内容命中自残、自杀等危机关键词时，该条日记会进入管理后台的
「危机预警」列表（`crisis_alert` 表），供管理员判断是否需要人工联系。
这不是内容审查——目的是在用户最需要帮助的时候让一个人知道。

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

### 首页的三条通知条（v2.4）

三个横幅组件结构、关闭逻辑都一样（都用 `sessionStorage` 记「已关掉的最新 ID」），
都是「量 > 0 且没被关过」才出现，区别只在给谁看、点了去哪：

| 组件 | 给谁看 | 数据来源 | 点击去哪 |
| --- | --- | --- | --- |
| `CrisisAlertBanner.vue` | 管理员 | `/api/admin/crisis-alerts` (+ `pending-count`) | `/admin/crisis` |
| `ReportAlertBanner.vue` | 管理员 | `/api/admin/reports?status=PENDING&page=1&size=1` (+ `pending-count`) | `/admin/reports` |
| `SupportAlertBanner.vue` | 用户 + 客服 / 管理员 | `/api/support/unread-count`；客服 / 管理员另走 `/api/staff/unread-count` | `/support`；客服 / 管理员去 `/staff` |

- 举报通知条显示「有 N 条举报待处理」+ 被举报原因，用最新的举报 ID 做关闭标记，
  这样「关掉之后又来一条新举报」会重新弹出。
- 客服通知条显示「客服回复了你 N 条消息」（管理员/客服侧是「有 N 条用户咨询待回复」），
  用最新消息 ID 做关闭标记，避免误关掉后续的新回复。
- 三个横幅自己都不弹错误提示（`catch` 里只置空状态），但 `utils/request.js` 的全局拦截器
  会给 404 / 500 统一弹一次 toast —— 所以**只更新前端、没换后端**时，首页会看到
  「网络异常，请稍后再试」。升级请把 `xinyu-backend-2.4.1.jar` 一起重启，
  别只替换 `dist`。

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
# 编辑 .env：JWT_SECRET、MYSQL_ROOT_PASSWORD、MYSQL_APP_PASSWORD、REDIS_PASSWORD 全部必填
docker compose up -d --build
```

**端口暴露（v3.1 起收紧）**

| 服务 | 是否映射到宿主机 | 说明 |
| --- | --- | --- |
| Nginx（frontend） | ✅ `${HTTP_PORT:-80}` | 唯一对外入口 |
| backend | ❌ 仅 compose 内网 | 走 Nginx `/api` 反代 |
| MySQL | ❌ 仅 compose 内网 | 不再映射 3306，别再让整个局域网连库 |
| Redis | ❌ 仅 compose 内网 | 已用 `--requirepass` 强制密码 |

需要用客户端连库时临时开一条隧道即可，不要把 3306 长期 publish 出去：

```bash
docker compose exec mysql mysql -uxinyu -p xinyu
```

**启用 HTTPS（强烈建议）**

```bash
export TLS_DIR=/etc/letsencrypt/live/your.domain   # 目录里要有 fullchain.pem 与 privkey.pem
docker compose -f docker-compose.yml -f docker-compose.tls.yml up -d --build
```

覆盖文件会把 Nginx 换成带 TLS 的配置：80 自动 301 到 443，并下发 HSTS、
`X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy` 等安全响应头。

启动后：

- 前端：`http://localhost`（Nginx 已加 `/uploads/` 反代，头像能正常显示）
- 后端接口：`http://localhost/api`（后端 8080 只在 compose 内网可达）
- MySQL：仅容器内可达（首次启动自动执行 `db/schema.sql`；`data.sql` 只剩标签等基础数据，
  不再带任何账号。老库升级按顺序补 `upgrade-v3.6.sql` 等脚本，见 `backend/src/main/resources/db/`）

上传的头像落在 `xinyu-uploads` 数据卷（容器内 `/app/uploads`），
`docker compose down` 再 `up` 不会丢图；要彻底清空才需要 `docker compose down -v`。

> 已有的老库（不是全新初始化）升级：在容器里或宿主机执行一次
> `mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v2.sql`，
> 之后的版本按顺序补 `upgrade-v2.2.sql` / `upgrade-v2.3.sql` / `upgrade-v2.4.sql` / `upgrade-v2.4.1.sql`。

---

## 📱 打包成 Android APK

**已经打好一个可以直接装的包**：项目根目录 `心语-v3.4.0.3072.apk`（3.9 MB，`versionCode 3072` / `versionName 3.4.0.3072`，已签名，`apksigner` 校验通过）。

> ⚠️ `versionCode` / `versionName` 写在 `frontend/android/app/build.gradle`（第 18–19 行），
> 是**编译进 APK 清单**的，改文件名不等于改版本号；而且 `changelog.json` 会被打进
> `assets/public/`，所以**发新版本必须重新打包**（网页端才是改了刷新就生效）。

这一版是**内嵌前端 + 原生外壳**（Capacitor 6）：

| 能力 | 说明 |
|---|---|
| 页面 | `frontend/dist` 打进 APK（`assets/public/`），打开即用，**没有白屏等下载** |
| 图标 / 启动图 | 首版图标：青绿底 + 白色圆角卡片 + 蓝色「心」字（自适应/圆形/旧版三套齐全）+ 白底启动页，由 `frontend/scripts/gen-android-assets.py` 从 `frontend/scripts/assets/legacy/` 的母版生成 |
| 状态栏 | 跟随 App 内深浅色主题 |
| 返回键 | 二级页返回上一级，Tab 页退到后台（同微信） |
| 定位权限 | 原生权限 + 系统弹窗（`@capacitor/geolocation`），只在点「使用我的位置」时申请 |
| 离线 | 断网也能写日记，联网后自动同步（见「📴 离线可用」） |
| 数据 | 联网读写仍走电脑上的后端，同一 Wi-Fi 下访问 `http://电脑IP:8080` |

> 所以：**界面能离线看，日记能离线写**；AI 回复、树洞、趋势统计这些要联网。

装之前确认（电脑和手机同一个 Wi-Fi）：

```bash
curl http://127.0.0.1:8080/api/health     # 后端要活着
curl -I http://192.168.1.14:8088          # 网页版静态站（可选，App 不依赖它）
```

**连不上服务器时不用重新打包**：App 里打开
「我的 → 设置 → 服务器地址」，填电脑当前的局域网地址（例如 `192.168.1.14:8080`），
点「测试并保存」即可切换；填错或想回退时点「恢复默认地址」。
地址存在本机（`localStorage['xinyu_api_base']`），优先级高于打包时写死的 `VITE_API_BASE`。

> 侧载安装时系统可能提示「恶意应用 / 有风险」——自签名证书 + 非商店渠道的启发式提醒，
> 点「更多信息 → 仍要安装」即可，原因和处理办法见
> [docs/APK打包指南.md](docs/APK打包指南.md) 6.2。

想自己重新打：

```bash
source ~/.xinyu-toolchain/env.sh
cd frontend
npm run build:app                          # 读 .env.capacitor，注入后端绝对地址
npx cap sync android                       # dist → assets/public
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd android && ./gradlew --no-daemon assembleRelease
```

- 前端改完 → `build:app` + `cap sync` + 编译（3~5 分钟）
- **打完包记得再跑一次 `npm run build`**（`build:app` 会覆盖 `dist`，别把带绝对地址的产物留给 nginx）
- 换了 Wi-Fi / 电脑 IP 变了 → 手机 App 里改「设置 → 服务器地址」就行（不用重装）；
  想让新装的包默认就指向新地址，再改 `frontend/.env.capacitor` 的 `VITE_API_BASE` 重打
- 只改后端 Java → 重启后端即可，**APK 不用动**

完整流程、签名、镜像、踩坑清单见 [docs/APK打包指南.md](docs/APK打包指南.md)。

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
- **今日情绪天气**：按用户授权的位置显示实时天气 + 情绪化文案，写日记时自动填好天气
- **三份法律文本 + 强制重新同意**：用户协议 / 隐私政策 / 免责声明，老用户升级后必须读完才能用
- 显示模式自选（浅色 / 深色 / 跟随系统），图表也会跟着变色
- 树洞评论可回复（两层结构 + 「回复 @昵称」引用）
- 底部导航毛玻璃 + 滑动指示器动画，弹窗磨砂玻璃质感
- Java 后端完整，H5 移动端适配
- 自带回归脚本：`scripts/smoke-test.sh`（75 项）、`scripts/admin-sse-test.sh`（40 项）
