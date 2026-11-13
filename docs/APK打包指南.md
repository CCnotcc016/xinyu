# 把「心语」打包成 Android APK

> 目标：把现有的 Vue3 + Vant4 H5 装成手机桌面上一个真正的 App 图标。
> 本文只讲写代码之外的那部分，不需要重写前端，也不需要换成 uni-app / 原生。

> ✅ **本文已实测跑通（路线 B 内嵌资源 + release 签名）**
> 本机未装 Android Studio，用**手动解压 SDK 34** 的方式完成了打包：
>
> - `app-release.apk` → 3.5 MB，`apksigner verify` 显示 v1/v2 签名均有效
> - 成品已复制到项目根目录：`心语-v3.3.3.3072.apk`
> - 包名 `com.xinyu.app`，versionName `3.3.3.3072`，minSdk 22 / targetSdk 34
> - 前端已内嵌（`assets/public/`），另有原生外壳：自适应图标、启动图、状态栏、返回键
>
> **当前仓库里就是路线 B**：`capacitor.config.json` 里没有 `server.url`，
> 打 App 用 `npm run build:app` + `npx cap sync android`，详见第 3 节。

---

## 0. 先选路线：三条路的取舍

| 方案 | 原理 | 代码改动 | 离线可用 | 适合场景 |
|---|---|---|---|---|
| **A. Capacitor 远程加载** | APK 里只有一层 WebView，打开时加载 `http://电脑IP:8088` | **0 行** | ❌ 必须联网，电脑关机就白屏 | 十分钟验证「能不能装上」 |
| **B. Capacitor 内嵌资源**（**当前采用**） | 把 `frontend/dist` 打进 APK，接口指向服务器 | 已改好（1 个 `backend.js` + 8 处图片 + 1 处 SSE） | ✅ 界面秒开，且断网也能写日记（本地队列，联网自动补传） | 真正当 App 用、答辩演示 |
| C. PWA / TWA | 浏览器套壳捷径，`display: standalone` | 0 行 | ❌ | iOS 加桌面（但 iOS 装不了 APK） |

项目已经带了 [manifest.webmanifest](../../frontend/public/manifest.webmanifest) 和 sw.js，`添加到主屏幕` 可以直接用（**不用打包**，见文末「附：不打包的最快方案」）。

**A 和 B 的区别只有一句：**前端页面从服务器下载（A），还是从 APK 里读（B）。
本仓库已经按 B 做完，A 只留着当「怀疑是打包问题时」的对照实验（第 2 节）。

---

## 1. 环境准备

| 需要 | 版本 | 本机现状 |
|---|---|---|
| Node.js | ≥ 18（Capacitor 6），≥ 20 更稳 | ✅ `~/.xinyu-toolchain/node` v20.20.2 |
| JDK | **17**（Capacitor 6 要求 17；Capacitor 7 要求 21） | ✅ `~/.xinyu-toolchain/jdk` 17.0.20 |
| Android Studio | 任意近两年版本 | ➖ **不需要**，本机没装也打包成功了 |
| Android SDK | Platform **API 34 或 35** + Build-Tools | ✅ 已手动装好 `platforms/android-34` + `build-tools/34.0.0` |

> **版本别乱配**：本项目后端就是 JDK 17，所以 **Capacitor 锁 6.x**。
> 装 Capacitor 7 会连带要求 JDK 21、compileSdk 35，多一堆麻烦。

### 装 Android SDK（一次性，不用 Studio）

Studio 只是为了装 SDK，直接下 zip 解压更快（本机就是这么干的，`dl.google.com` 实测可访问）：

1. 从 <https://dl.google.com/android/repository/repository2-3.xml> 里查文件名：
   - Platform 34：`platform-34-ext7_r03.zip`
   - Build-Tools 34（macOS）：`build-tools_r34-macosx.zip`
2. 下载后按下面的**目录名规则**解压 —— 这一步错了 Gradle 会报「SDK 未安装」：

```bash
mkdir -p "$HOME/Library/Android/sdk/platforms" "$HOME/Library/Android/sdk/build-tools"
# platform zip 解开就叫 android-34，名字对得上
unzip platform-34-ext7_r03.zip     -d "$HOME/Library/Android/sdk/platforms/"
# build-tools zip 解开是 android-14/，必须改名成版本号目录
unzip build-tools_r34-macosx.zip   -d /tmp/bt && mv /tmp/bt/android-14 "$HOME/Library/Android/sdk/build-tools/34.0.0"
```

3. 环境变量（`~/.zshrc`）：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$HOME/.xinyu-toolchain/jdk/Contents/Home/bin:$PATH"
```

> `frontend/android/local.properties` 里已经有 `sdk.dir`，所以只是命令行打包的话，不设 `ANDROID_HOME` 也能编过；
> 但 `./gradlew` 那次 shell 里最好还是 `export` 一下，避免 Capacitor 插件找不到 `aapt2`。

---

## 2. 路线 A：远程加载（对照实验用）

> ⚠️ 仓库当前**不是**这个配置，第 3 节才是。这一节留着，是为了在你怀疑「是不是内嵌搞坏了」时，
> 用一个零改动的壳快速判断问题出在打包还是在页面本身。

### 2.1 装 Capacitor

```bash
cd frontend
npm i -D @capacitor/cli@6
npm i @capacitor/core@6 @capacitor/android@6
```

### 2.2 初始化配置（做实验时临时加回去）

`frontend/capacitor.config.json`：

```json
{
  "appId": "com.xinyu.app",
  "appName": "心语",
  "webDir": "dist",
  "server": {
    "url": "http://192.168.1.14:8088",
    "cleartext": true
  },
  "android": {
    "allowMixedContent": true
  }
}
```

要点：

- `server.url` 填**手机能访问到的地址**，也就是平时手机浏览器打开的那个（本机是 `http://192.168.1.14:8088`）。填 `localhost` 会白屏。
- 这个地址会被**写进 APK 内的 `assets/capacitor.config.json`**，换 Wi-Fi / IP 变了必须改配置**重新打包**。
- `cleartext: true` 允许 http 明文，不加的话 Android 9+ 直接拒连，表现就是「网络异常」。它会让 manifest 带上 `android:usesCleartextTraffic="true"`。
- `appId` 一旦确定就别改（相当于 iOS Bundle ID），`com.xinyu.app` 用着就行。
- **实验做完务必删掉 `server.url`**，否则又会退化成「电脑关机就白屏」。

### 2.3 生成 Android 工程

```bash
npx cap add android
```

会多出 `frontend/android/` 目录（Capacitor 的壳，Gradle 工程，可以直接用 Studio 打开）。
**本仓库里这个目录已经提交了**，别人拉下来不用再执行 `cap add`。

生成时如果 `cap add` 里那次 Gradle sync 报下载失败（本项目第一次就报过），**不影响工程生成**，直接看下一步手动编。

### 2.4 打 debug 包

```bash
source ~/.xinyu-toolchain/env.sh          # JDK 17 + Node
cd frontend/android
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew --no-daemon assembleDebug
```

产物：`frontend/android/app/build/outputs/apk/debug/app-debug.apk`（实测 4.4 MB）

- 首次编译要下 Gradle 8.2.1 + AGP + AndroidX 依赖，**本机实测 12 分 36 秒**；有缓存后 1 分钟内。
- Gradle 本体走腾讯镜像（`gradle-wrapper.properties` 已改），否则 `services.gradle.org` 会卡住，见第 5 节。
- 校验产物：

```bash
~/Library/Android/sdk/build-tools/34.0.0/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk | head -3
# package: name='com.xinyu.app' versionCode='3072' versionName='3.3.3.3072'
# application-label:'心语'
```

### 2.5 装到手机

```bash
adb devices                                  # 确认手机已连上（需开 USB 调试）
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

没有数据线：把 `app-debug.apk` 用微信/QQ/隔空投递传到手机，点安装，允许「安装未知应用」即可。

> 前提：手机和电脑在同一个 Wi-Fi，且 `http://IP:8088` 在**手机浏览器**里能正常登录。
> 浏览器都打不开，APK 一定也用不了——先解决网络，再折腾打包。

**装之前的自检顺序**（本项目真实踩过的）：

1. 后端在跑：`curl http://127.0.0.1:8080/api/health`（`scripts/run-dev.sh`）
2. 静态站在跑：`curl -I http://192.168.1.14:8088`（`scripts/serve-dist.sh`）
3. 手机浏览器打开 `http://192.168.1.14:8088`，能出登录页
4. 再装 APK

已签名的成品直接拷到项目根目录了：`心语-v3.3.4.3072.apk`（4.05 MB）。一装完就打不开页面、或者提示「请求超时」，
先在 App 里走「我的 → 设置 → 服务器地址」，把地址改成电脑**当前**的局域网 IP（`192.168.1.14:8080`）点「测试并保存」，
不需要重新打包。

---

## 3. 路线 B：内嵌前端 + 原生外壳（当前采用）

App 里的页面来自 APK 自己（`android/app/src/main/assets/public/`），所以：

- 打开就跟本地应用一样，**没有白屏等下载**，电脑关机也能看到界面和骨架屏；
- 但**数据和登录态仍然在后端**，电脑上的 Spring Boot 不开，就只能看到「网络异常」。

> 一句话：**能离线看界面，也能离线写日记**——断网时写的日记会先进本机队列（IndexedDB），
> 联网后自动补传，服务端靠 `client_uid` 唯一键保证不会重复。
> 但**看别人的树洞、生成 AI 回复仍然需要网络**，这些数据没法本机凭空造出来。
> 细节见 [README 的「离线可用」一节](../README.md#-离线可用断网也能写日记)。

### 3.1 Capacitor 配置

`frontend/capacitor.config.json`（**仓库里已经是这样**）：

```json
{
  "appId": "com.xinyu.app",
  "appName": "心语",
  "webDir": "dist",
  "server": { "cleartext": true },
  "android": {
    "allowMixedContent": true,
    "backgroundColor": "#06101a"
  },
  "plugins": {
    "SplashScreen": {
      "launchAutoHide": false,
      "launchShowDuration": 800,
      "backgroundColor": "#06101a",
      "androidScaleType": "CENTER_CROP",
      "showSpinner": false,
      "splashFullScreen": true
    }
  }
}
```

- **没有 `server.url`** 就是内嵌模式；有它就是路线 A。
- `cleartext: true` 让 manifest 带上 `android:usesCleartextTraffic="true"`（本仓库的 manifest 里也已手写了一份，双保险）。
- `launchAutoHide: false`：启动图不自动消失，由 `src/native.js` 在首个路由就绪后手动 `SplashScreen.hide()`，避免「启动图没了、页面还没画」的中间白帧。
- `androidScheme` 保持默认的 `https`，所以页面 origin 是 `https://localhost`。跨域请求 http 后端靠 `allowMixedContent`。

### 3.2 前端怎么知道后端在哪

页面 origin 变成 `https://localhost` 之后，所有相对路径（`/api`、`/uploads`）都会打到 WebView 自己身上 → 请求 404、图片全裂。
解决办法是**把后端地址做成一个运行时可改的出口**：构建期注入默认值，装完之后在 App 里也能改：

| 文件 | 作用 |
|---|---|
| `frontend/src/utils/backend.js` | `getApiBase()` / `setApiBase()` / `resetApiBase()` / `defaultApiBase()`、`registerClient()`、`mediaUrl(path)`、`probeBackend()` |
| `frontend/.env.capacitor` | `VITE_API_BASE=http://192.168.1.14:8080/api` —— 只决定**默认值** |
| `frontend/src/views/Settings.vue` | 「服务器地址」：输入 `192.168.1.14:8080` → 测试并保存（自动补 `http://` 与 `/api`） |
| `frontend/src/utils/request.js` | `registerClient(axios.create(...))`，`setApiBase()` 会批量改 `defaults.baseURL` |
| `frontend/src/views/DiaryDetail.vue` | SSE 用 `` `${getApiBase()}/diaries/${id}/ai-reply/stream?token=...` `` |
| `Profile / Settings / UserProfile / ChatList / ChatRoom / FriendRequests / Treehole / TreeholeDetail` | 头像、树洞图、大图预览统一走 `mediaUrl(...)`（每次调用现算 origin） |

运行时覆盖值存在 `localStorage['xinyu_api_base']`，优先级高于构建期常量；`registerClient()` 让所有
axios 实例（主请求 + 同步用的静默实例）都能被一次切换。**换 Wi-Fi 后不必重新打包**，用户自己就能改；
`isApp` 用「默认地址是不是绝对 URL」判断，所以网页部署（同源 `/api`）不会显示这一栏。

为什么直接指 **8080（后端本身）** 而不是 8088（nginx）：App 里没有 nginx，`/api` 必须打到真正的后端；
而 `/uploads` 也是 Spring Boot 自己提供的静态目录，同源，所以 `mediaUrl()` 只要补一个 origin 就够了。

`vite build --mode capacitor` 会自动读 `.env.capacitor`，这就是 `npm run build:app` 做的事。
网页部署（`npm run build`）读不到这个变量，默认地址退回 `/api`，行为和以前完全一样。

### 3.3 原生外壳

`frontend/src/native.js`，由 `src/main.js` 在 `app.mount()` 之后调用，浏览器里第一行就 `return`：

- **状态栏**：`setOverlaysWebView(false)` + 背景色跟随深浅主题（`#06101a` / `#eaf2f5`），图标颜色用 `Style.Dark/Light`。
- **返回键**：Tab 页（home / diaries / treehole / profile）按下退到后台（跟微信一致），二级页 `router.back()`。
- **启动图**：`router.isReady()` 之后再 `hide()`。
- **Service Worker**：`main.js` 里判断 `Capacitor.isNativePlatform()`，原生下不注册 —— 否则升级 APK 后 WebView 还在吐旧缓存。

### 3.4 图标和启动图

不用 `@capacitor/assets`（它要装 sharp，几十 MB），项目自带一个纯标准库脚本（只用 `sips` 解码/缩放 PNG，不需要 Pillow）：

```bash
cd frontend
python3 scripts/gen-android-assets.py            # 默认 --style legacy
```

当前图标是**首版样式**：青绿底上的白色圆角卡片 + 蓝色（`#5DB8FD`）「心」字。首版的成品 PNG 全部留存在 `scripts/assets/legacy/`（`ic_launcher_{48..192}.png`、`ic_launcher_round_192.png`、`ic_launcher_foreground_{108..432}.png`、`splash_{port,land}_*.png`，以及 `legacy/web/` 里的 PWA 图标），`apply_legacy()` 直接按密度拷进去；首版 APK 里有两张尺寸错的残次图（mdpi 圆形 162²、port-hdpi 启动图 96²），脚本会用 192 圆形母版和 1280×1920 竖屏母版重新缩放，不照抄坏图。

> 还保留了一套「青绿渐变 + 白色心」的方案，跑 `python3 scripts/gen-android-assets.py --style gradient` 可切回去。渐变字形来自预渲染蒙版 `scripts/assets/xin-glyph.png`（1024²，白字形黑底，苹方 SC Semibold，描边 9px）；要改字形跑 `python3 scripts/make-glyph.py`（这个脚本依赖 Pillow，需要临时 venv，见脚本头部说明）。

产物：

| 产物 | 规格 |
|---|---|
| `mipmap-{m,h,x,xx,xxx}dpi/ic_launcher.png` | 48 / 72 / 96 / 144 / 192，来自首版母版 |
| `mipmap-*/ic_launcher_round.png` | 同上，圆形裁切（统一由 192 圆形母版缩放） |
| `mipmap-*/ic_launcher_foreground.png` | 108 / 162 / 216 / 324 / 432，透明底，卡片落在 66% 安全区 |
| `drawable-port-*/splash.png` | 320×480 → 1280×1920，白底 + 居中 logo（由 1280×1920 母版缩放） |
| `drawable-land-*/splash.png` | 横屏一份 |
| `drawable/splash.png` | 480×320 默认密度兜底 |
| `public/icons/{icon-512,icon-192,apple-touch-icon,icon-maskable-512}.png` | H5 / PWA 图标，与首版逐字节一致 |

配套的资源文件也必须对得上，否则图标会「白底白字」看不见：

- `mipmap-anydpi-v26/ic_launcher{,_round}.xml` → `@color/ic_launcher_background` + `@mipmap/ic_launcher_foreground`（首版前景自带白色卡片，底色用白色正好融合）
- `values/ic_launcher_background.xml` → `#FFFFFF` 纯色兜底
- 切到 `--style gradient` 时需要另外把渐变 `<shape>` 放回 `drawable/ic_launcher_background.xml`，并把上面两个 XML 的底色改成 `@drawable/ic_launcher_background`
- `values/colors.xml` → `xinyu_boot = #06101A`，用于 `values/styles.xml`：
  - `AppTheme.NoActionBar` 的 `android:windowBackground`（WebView 首帧前的底色）
  - `AppTheme.NoActionBarLaunch` 的 `windowSplashScreenBackground`（Android 12+ 系统启动图）
  - 同时 `windowSplashScreenAnimatedIcon="@mipmap/ic_launcher"`、`postSplashScreenTheme="@style/AppTheme.NoActionBar"`

### 3.5 完整打包流程

```bash
source ~/.xinyu-toolchain/env.sh            # JDK 17 + Node 20
cd frontend
npm run build:app                            # 读 .env.capacitor，产出 dist/
npx cap sync android                         # dist → android/app/src/main/assets/public，顺带注册 3 个插件
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd android && ./gradlew --no-daemon assembleRelease
```

产物 `frontend/android/app/build/outputs/apk/release/app-release.apk`，签名与校验见第 4 节。

自检：

```bash
# 1. dist 里确实是绝对地址
grep -o "http://192.168.1.14:8080/api" ../dist/assets/index-*.js | head -1

# 2. 资源真的进了 APK
unzip -l app/build/outputs/apk/release/app-release.apk | grep -c "assets/public/"

# 3. APK 里没有 server.url（有就是退回了路线 A）
unzip -p app/build/outputs/apk/release/app-release.apk assets/capacitor.config.json | grep -c '"url"'
```

### 3.6 日常更新要重打什么

| 改了什么 | 需要做的事 |
|---|---|
| 只改了页面 / 样式 / 文案 | `npm run build:app` → `npx cap sync android` → `./gradlew assembleRelease`（3~5 分钟） |
| 后端 IP 变了（换 Wi-Fi） | 改 `frontend/.env.capacitor` 再走上面全流程 |
| 改了 `capacitor.config.json` / 原生代码 / 图标 | 同上，并且**必须重装 APK**（`npx cap sync` 只更新 assets，不改已装应用的清单） |
| 只改了后端 Java | 重启后端即可，**APK 不用动** |

> 网页版（`npm run build` + `scripts/serve-dist.sh`）和 APK 是两份独立产物，
> 平时调试用网页版最快，发版前再打一次 APK。

后端 CORS 已经放开（`backend/src/main/java/com/xinyu/config/SecurityConfig.java` 里 `allowedOriginPatterns("*")`），`https://localhost` 的跨域请求不会被拦。

---

## 4. 正式包：签名 + release

debug 包也能装，但每次升级要卸载重装。给别人装建议签一个 release 包。

### 4.1 生成 keystore（只做一次，丢了就再也升级不了同一个包名）

```bash
cd frontend/android
keytool -genkeypair -v \
  -keystore xinyu-release.keystore \
  -alias xinyu -keyalg RSA -keysize 2048 -validity 36500 \
  -storepass xinyu2026 -keypass xinyu2026 \
  -dname "CN=XinYu, OU=XinYu Course, O=XinYu, L=Beijing, ST=Beijing, C=CN"
```

> **这个文件要备份，不能提交到仓库**（`.gitignore` 里已加 `frontend/android/*.keystore`、`frontend/android/key.properties`）。
> 丢了 → 同一个包名无法覆盖安装升级，只能卸载重装、包名加后缀。

### 4.2 配置签名

`frontend/android/key.properties`（**不要提交**）：

```properties
storeFile=xinyu-release.keystore
storePassword=xinyu2026
keyAlias=xinyu
keyPassword=xinyu2026
```

`android/app/build.gradle`（Capacitor 6 是 Groovy 写法；如果你装的是 Capacitor 7，对应文件是 `build.gradle.kts`，要换成 Kotlin DSL）。
**本项目已经改好，内容如下**：

```groovy
// android { } 之前
def keystorePropertiesFile = rootProject.file("key.properties")
def keystoreProperties = new Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
}

android {
    signingConfigs {
        release {
            if (keystorePropertiesFile.exists()) {
                // 注意用 rootProject.file：storeFile 是相对于 app 模块解析的，直接写文件名会找不到
                storeFile rootProject.file(keystoreProperties['storeFile'])
                storePassword keystoreProperties['storePassword']
                keyAlias keystoreProperties['keyAlias']
                keyPassword keystoreProperties['keyPassword']
            }
        }
    }
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
            if (keystorePropertiesFile.exists()) {
                signingConfig signingConfigs.release
            }
        }
    }
}
```

> 外面套 `if (exists())` 是为了**别人拉代码没有 keystore 时也能编出 unsigned 包**，不会直接报错。

### 4.3 打包

```bash
cd frontend/android
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew --no-daemon assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk（实测 3.4 MB，比 debug 小 1 MB）
```

要上架（Google Play / 国内商店）就出 AAB：

```bash
./gradlew bundleRelease
# 产物：app/build/outputs/bundle/release/app-release.aab
```

### 4.3.1 校验签名（强烈建议，30 秒）

```bash
BT=$HOME/Library/Android/sdk/build-tools/34.0.0
$BT/apksigner verify --print-certs -v app/build/outputs/apk/release/app-release.apk
# Verifies
# Verified using v1 scheme (JAR signing): true
# Verified using v2 scheme (APK Signature Scheme v2): true
# Signer #1 certificate DN: CN=XinYu, OU=XinYu Course, ...
```

`Verifies` + v1/v2 都是 `true` 就说明手机能正常装。若出现 `ERROR: Failed to read PNG signature` 之类，通常是包没下全，重跑 `assembleRelease`。

### 4.4 版本号

`android/app/build.gradle` 里（本项目当前填的是前端的版本号）：

```groovy
defaultConfig {
    versionCode 3072              // 每次发布 +1，整数
    versionName "3.3.3.3072"      // 给用户看的
}
```

---

## 5. 国内网络：Gradle 依赖走镜像

`dl.google.com` 连不上时，`./gradlew` 会卡在 `Could not resolve com.android.tools.build:gradle`。
改 `frontend/android/settings.gradle`（或 `settings.gradle.kts`），把镜像放最前：

```groovy
pluginManagement {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
        maven { url 'https://maven.aliyun.com/repository/public' }
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/public' }
        google()
        mavenCentral()
    }
}
```

Gradle 本体下载慢或直接超时，改 `android/gradle/wrapper/gradle-wrapper.properties`（**本项目已改好**）：

```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.2.1-all.zip
```

- 版本号要和 `gradle-wrapper.properties` 原来的 `8.2.1` 一致，别随手升（Capacitor 6 / AGP 8.2.1 配的是这个）。
- 改 URL 后缓存目录的 hash 会变，等于**重新下一次（解压后约 439 MB）**，第一次会慢，之后就命中缓存了。
- 本机实测：`services.gradle.org` 能连但拉包极慢，`mirrors.cloud.tencent.com` 走 206 分段下载正常；`dl.google.com`（AGP、AndroidX）**现在可以直连，所以 Maven 的阿里云镜像本项目没加也能编过**。真挂了再按上面的 `pluginManagement` 加镜像。

---

## 6. 本项目要额外注意的 6 个坑

| 坑 | 表现 | 处理 |
|---|---|---|
| **明文 HTTP** | 登录/接口提示「网络异常」，但手机浏览器能开 | manifest 里 `android:usesCleartextTraffic="true"` + `server.cleartext: true`（都已配好） |
| **接口相对路径** | 白屏 / 所有请求 404 / 一直转圈 | `API_BASE` 指向 8080 绝对地址（3.2） |
| **`/uploads` 图片** | 头像全是默认图、树洞配图裂开 | 所有 `<img>` 走 `mediaUrl()`（3.2） |
| **SSE 流式回复** | AI 回复不出字或立刻报错 | `EventSource` 也走 `API_BASE`（3.2 已改） |
| **定位** | 「今日情绪天气」拿不到定位 | 已接 `@capacitor/geolocation`（原生权限 + 系统弹窗），见 6.1；实在不行还有「手动选择城市」 |
| **物理返回键** | 一按就退出 App | `src/native.js` 里统一监听 `backButton`（3.3），不要在页面里各写一份 |

`src/native.js` 里返回键的实际写法：

```js
import { App as CapacitorApp } from '@capacitor/app'

const TAB_ROUTES = new Set(['home', 'diaries', 'treehole', 'profile'])

// Tab 页退到后台（和微信一致），二级页正常返回
CapacitorApp.addListener('backButton', ({ canGoBack }) => {
  if (TAB_ROUTES.has(router.currentRoute.value.name)) {
    CapacitorApp.minimizeApp()
  } else if (canGoBack) {
    router.back()
  } else {
    CapacitorApp.minimizeApp()
  }
})
```

> `minimizeApp()` 是 Capacitor 6 新加的（等价于按 Home 键）；老版本要自己 `exitApp()`，那样体验很差。

图标和启动图的做法见 3.4（脚本生成，不用 `@capacitor/assets`）。

### 6.1 定位权限（怎么像真 App 一样弹窗）

纯 WebView 里 `navigator.geolocation` 是拿不到系统权限的（要自己实现
`WebChromeClient.onGeolocationPermissionsShowPrompt`）。现在改用 Capacitor 插件，
由原生层去问系统，弹出来就是标准的「心语想要访问您的位置信息」对话框。

三处配合：

```xml
<!-- android/app/src/main/AndroidManifest.xml -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- 不强制要求硬件，平板/模拟器也能装 -->
<uses-feature android:name="android.hardware.location" android:required="false" />
<uses-feature android:name="android.hardware.location.gps" android:required="false" />
```

```js
// frontend/src/utils/geo.js（原生分支）
const { location } = await Geolocation.checkPermissions()
if (location !== 'granted') await Geolocation.requestPermissions({ permissions: ['location'] })
await Geolocation.getCurrentPosition({ enableHighAccuracy: false, timeout: 10000 })
```

流程是「先同意，后定位」：用户在天气卡片点「使用我的位置」才请求，默认一次都不问。
拒绝过之后不会反复骚扰（`permissionState()` 返回 `denied` 时直接给文案 + 手动选城市）。
用户想改回来：**设置 → 应用 → 心语 → 权限 → 位置信息**。

> 打新包必须重新 `npx cap sync android`，否则 `android/capacitor.settings.gradle`
> 里没有 `:capacitor-geolocation`，运行时报 `Geolocation plugin is not implemented on android`。

### 6.2 装的时候提示「恶意应用 / 有风险」，正常吗

正常。这是手机厂商和 Play Protect 的**启发式判断**，不是因为包里有恶意代码：

| 触发点 | 说明 |
|---|---|
| 自签名证书 | 证书是自己生成的（`CN=XinYu, OU=XinYu Course`），不是应用商店的证书链，系统无法追溯开发者 |
| 不是从商店安装 | 走 `adb`/文件管理器侧载，没有商店背书 |
| `usesCleartextTraffic="true"` | 允许明文 HTTP，风险评分会被抬高 |
| WebView 外壳 | 一个内嵌网页的壳子，特征和某些灰色 App 相似 |

处理办法：

1. 安装界面点「更多信息 / 仍要安装」继续；
2. 小米/OPPO/vivo/华为：关掉「应用安装扫描 / 纯净模式」或临时允许本次安装；
3. 想彻底不弹——上架应用市场，或者至少换成真域名 + HTTPS（见第 8 节），
   把 `usesCleartextTraffic` / `server.cleartext` 删掉。


---

## 7. 报错对照表

| 现象 | 大概率原因 | 怎么查 |
|---|---|---|
| `SDK location not found` | 没设 `ANDROID_HOME`，或 `android/local.properties` 缺 `sdk.dir` | 加 `export ANDROID_HOME=...`，或写 `sdk.dir=/Users/你/Library/Android/sdk` |
| `unsupported class file major version` | 用了 JDK 21 跑 Capacitor 6 的 Gradle | `export JAVA_HOME=~/.xinyu-toolchain/jdk/Contents/Home` |
| `compileSdk 35 not found` | SDK 里没装 platform 35 | Studio SDK Manager 装 `Android 15 (API 35)` |
| 白屏、点了没反应 | 用了 `npm run build` 而不是 `build:app`，产物里没有绝对地址 | 改回 `npm run build:app` |
| 装完打开是「无法连接」 | 手机和电脑不在同一网络 / IP 变了 | 手机浏览器先验证 `http://IP:8080/api/health` |
| 改了前端没生效 | 只 `build:app` 没 `npx cap sync` | 每次都 `npx cap sync android` 之后再编译 |
| 改了 `capacitor.config.json` 没生效 | 配置文件只在 `cap sync/copy` 时读 | `npx cap sync android` |
| 界面还是旧版 | Service Worker 缓存了 dist | 卸载重装，或把 `frontend/public/sw.js` 的缓存版本号 +1 |
| 安装时报「解析包时出现问题」 | APK 没下完 / minSdk 高于手机系统 | 重下；`minSdkVersion` 默认 22/23，别往上调 |

---

## 8. 想让同学、老师也能装

APK 里写死的地址必须是「谁的手机都能访问到」的地址（现在写在 `frontend/.env.capacitor`），有两条路：

1. **同一个 Wi-Fi**：用电脑的局域网 IP（`ipconfig getifaddr en0`）。
   路由器最好给电脑绑个固定 IP，否则换个网络、重启路由器，地址就变了，APK 也得重打。
2. **公网 HTTPS**（推荐给外人装）：用内网穿透拿一个临时域名，例如：

```bash
# cloudflared（免费、不用账号），直接穿透后端 8080
cloudflared tunnel --url http://127.0.0.1:8080
# 会给你 https://xxx-yyy-zzz.trycloudflare.com
```

把 `.env.capacitor` 改成：

```bash
VITE_API_BASE=https://xxx-yyy-zzz.trycloudflare.com/api
```

然后重新 `npm run build:app` + `cap sync` + 编译。
顺便解决三件事：明文 HTTP 限制、定位 API、Service Worker 只能跑在 https 的问题
（内嵌页面本身已经是 `https://localhost`，所以 sw 其实用不上，这里主要是接口和定位）。

> 换 https 之后 `server.cleartext`、`android:usesCleartextTraffic` 都可以删掉，留着也无害（只是允许，不是强制）。

---

## 9. 验收清单（答辩前照着过一遍）

- [ ] 桌面上图标是青绿底 + 白色卡片 + 蓝色「心」字，名字是「心语」，**不是默认的安卓机器人**
- [ ] 点开先看到白底启动图，再进登录页，中间**没有白闪**
- [ ] 冷启动 3 秒内出登录页（开场动画能跳过）
- [ ] 注册 → 登录 → 写一篇日记 → AI 回复能一个字一个字出
- [ ] 情绪趋势页图表能画出来（ECharts 在 WebView 里正常）
- [ ] 树洞发帖带图、点赞、评论、举报都通
- [ ] 头像、树洞配图正常显示（**这条最能验证 `mediaUrl()` 有没有漏**）
- [ ] 杀进程重开还是登录态（token 在 localStorage，WebView 重启会保留）
- [ ] 物理返回键：二级页返回上一级，Tab 页按一下退到后台
- [ ] 状态栏颜色跟着深/浅色主题变，不是白条
- [ ] 第一次点「使用我的位置」会弹系统定位授权（**没有就说明 manifest 权限或 `cap sync` 漏了**）
- [ ] 断网时写日记能存本地，出现「离线中 · N 篇待同步」，联网后自动同步
- [ ] 安装时若提示「恶意应用 / 有风险」，点「仍要安装」可正常装完（原因见 6.2）
- [ ] 关掉电脑后端再打开 App：界面能出来，接口报网络错误（说明是内嵌的，不是远程加载）

---

## 附：不打包的最快方案

如果只是想让它在手机上「像个 App」：

1. 手机浏览器打开 `http://IP:8088`
2. Android Chrome：菜单 →「添加到主屏幕」；iOS Safari：「共享 → 添加到主屏幕」
3. 全屏、无地址栏（`manifest.webmanifest` 里已经是 `display: standalone`）

零成本、零构建，缺点是没有原生能力、入口和普通网页一样。
**答辩演示够用，真要当项目交付还是建议走 Capacitor。**
