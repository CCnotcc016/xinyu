#!/usr/bin/env bash
# 本机「静态部署」启动脚本（免 sudo）：
#   1) 构建前端 → frontend/dist
#   2) 用本机 Nginx 把 dist 直接提供出去，并把 /api、/uploads 反向代理到 8080 后端
# 效果：手机访问 http://<本机 IP>:8088 即可，不再依赖 npm run dev（Vite）。
#
# 用法：
#   bash scripts/serve-dist.sh              # 构建 + 启动（默认 8088 端口）
#   bash scripts/serve-dist.sh --no-build   # 跳过构建，直接用现有 dist
#   bash scripts/serve-dist.sh stop         # 停止
#   PORT=9000 bash scripts/serve-dist.sh    # 换端口
#   PORT=80 bash scripts/serve-dist.sh      # 用 80 端口，手机直接访问 http://<IP>
#   SSL=1 PORT=8443 bash scripts/serve-dist.sh --no-build
#                                           # 用 https 提供（自签证书，自动生成）
#
# 为什么要 HTTPS：手机浏览器（华为、小米等自带浏览器尤其明显）只在安全上下文里
# 开放 navigator.geolocation，http://192.168.x.x 会被直接拒绝，定位功能用不了。
# 自签证书首次打开会提示「不安全」，选择继续访问即可；http 与 https 两个实例可以同时跑。
#
# 端口各自持有独立的配置与 pid 文件（nginx-<PORT>[-ssl].conf / .pid），
# 所以 8088 和 80、8443 可以同时跑，互不影响。
# 个别系统上 <1024 的端口会提示 permission denied，那就给命令加 sudo（停止同理）。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${PORT:-8088}"
SSL="${SSL:-0}"
# ssl 与普通实例的配置/pid/日志分开命名，避免同端口下互相覆盖
PORT_KEY="$PORT"
[ "$SSL" = "1" ] && PORT_KEY="${PORT}-ssl"
# 80 是 http 默认端口、443 是 https 默认端口，URL 里不显示，避免出现 :80 这种多余写法
DEFAULT_PORT=80
[ "$SSL" = "1" ] && DEFAULT_PORT=443
PORT_LABEL=":$PORT"
[ "$PORT" = "$DEFAULT_PORT" ] && PORT_LABEL=""
SCHEME="http"
[ "$SSL" = "1" ] && SCHEME="https"
BACKEND_PORT="${BACKEND_PORT:-8080}"

# 免 sudo 工具链（Node 等），不存在时用系统自带的
if [ -f "$HOME/.xinyu-toolchain/env.sh" ]; then
  # shellcheck disable=SC1091
  source "$HOME/.xinyu-toolchain/env.sh"
fi

NGX_HOME="$HOME/.xinyu-nginx"
CONF="$NGX_HOME/nginx-${PORT_KEY}.conf"
PIDFILE="$NGX_HOME/nginx-${PORT_KEY}.pid"
DIST="$ROOT/frontend/dist"
NGINX_BIN="$(command -v nginx || true)"
# sudo 下 PATH 会被重置，这里再兜一层常见安装位置
for f in /usr/local/bin/nginx /opt/homebrew/bin/nginx /usr/sbin/nginx; do
  [ -n "$NGINX_BIN" ] && break
  [ -x "$f" ] && NGINX_BIN="$f"
done

if [ -z "$NGINX_BIN" ]; then
  echo "❌ 未找到 nginx，请先安装：brew install nginx"
  exit 1
fi

mkdir -p "$NGX_HOME"

# 给用户看的「怎么停」提示：pid 文件按端口区分，停止时要带上端口；
# 如果实例是以 root 启动的（<1024 端口在部分系统上要 sudo），还得加 sudo
stop_hint() {
  local owner="" sudo_hint=""
  if [ -f "$PIDFILE" ]; then
    owner="$(ps -o user= -p "$(cat "$PIDFILE")" 2>/dev/null | tr -d ' ')"
    [ "$owner" = "root" ] && sudo_hint="sudo "
  fi
  if [ "$PORT" = "8088" ] && [ "$SSL" != "1" ]; then
    echo "${sudo_hint}bash scripts/serve-dist.sh stop"
  elif [ "$SSL" = "1" ]; then
    echo "${sudo_hint}SSL=1 PORT=${PORT} bash scripts/serve-dist.sh stop"
  else
    echo "${sudo_hint}PORT=${PORT} bash scripts/serve-dist.sh stop"
  fi
}

# ---- stop ----------------------------------------------------------------
if [ "${1:-}" = "stop" ]; then
  if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
    kill "$(cat "$PIDFILE")"
    echo "🛑 已停止 Nginx（端口 ${PORT}）"
  else
    echo "ℹ️  Nginx 未在运行"
  fi
  exit 0
fi

if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
  echo "ℹ️  Nginx 已在运行（PID $(cat "$PIDFILE")，${SCHEME}://$(ipconfig getifaddr en0 2>/dev/null || echo 127.0.0.1)${PORT_LABEL}）"
  echo "    要先停止请执行：$(stop_hint)"
  exit 0
fi

# ---- build ---------------------------------------------------------------
if [ "${1:-}" != "--no-build" ]; then
  echo "🔨 构建前端（npm run build）..."
  (cd "$ROOT/frontend" && npm run build)
fi

if [ ! -f "$DIST/index.html" ]; then
  echo "❌ 未找到 $DIST/index.html，请先构建（去掉 --no-build 再跑一次）"
  exit 1
fi

# ---- 自签证书（SSL=1 时）--------------------------------------------------
# 用配置文件而不是 -addext：macOS 自带的 LibreSSL 不支持 -addext，
# 而配置文件写法在 LibreSSL 与 OpenSSL 1.1/3 上都能用。
SSL_ARGS=""
SSL_DIRECTIVES=""
if [ "$SSL" = "1" ]; then
  CERT_DIR="$NGX_HOME/cert"
  CRT="$CERT_DIR/xinyu-${PORT}.crt"
  KEY="$CERT_DIR/xinyu-${PORT}.key"
  mkdir -p "$CERT_DIR"
  if [ ! -f "$CRT" ] || [ ! -f "$KEY" ]; then
    LAN_IP_FOR_CERT="$(ipconfig getifaddr en0 2>/dev/null || true)"
    CERT_CFG="$CERT_DIR/xinyu-${PORT}.cnf"
    {
      echo '[req]'
      echo 'distinguished_name = dn'
      echo 'x509_extensions    = v3_req'
      echo 'prompt             = no'
      echo '[dn]'
      echo 'CN = xinyu.local'
      echo 'O  = Xinyu Local Dev'
      echo '[v3_req]'
      echo 'basicConstraints = CA:FALSE'
      echo 'keyUsage         = digitalSignature, keyEncipherment'
      echo 'extendedKeyUsage = serverAuth'
      echo 'subjectAltName   = @alt_names'
      echo '[alt_names]'
      echo 'DNS.1 = localhost'
      echo 'DNS.2 = xinyu.local'
      echo 'IP.1  = 127.0.0.1'
      if [ -n "$LAN_IP_FOR_CERT" ]; then
        echo "IP.2  = $LAN_IP_FOR_CERT"
      fi
    } > "$CERT_CFG"
    echo "🔐 生成自签证书（含本机 IP，有效期 825 天）..."
    openssl req -x509 -newkey rsa:2048 -sha256 -nodes -days 825 \
      -keyout "$KEY" -out "$CRT" -config "$CERT_CFG" >/dev/null 2>&1
    chmod 600 "$KEY"
  fi
  SSL_ARGS=" ssl"
  SSL_DIRECTIVES="        ssl_certificate     $CRT;
        ssl_certificate_key $KEY;
        ssl_protocols       TLSv1.2 TLSv1.3;
        ssl_session_cache   shared:SSL:1m;
        ssl_session_timeout 10m;"
fi

# ---- 生成 Nginx 配置 -----------------------------------------------------
MIME=""
for f in /usr/local/etc/nginx/mime.types /opt/homebrew/etc/nginx/mime.types /etc/nginx/mime.types; do
  [ -f "$f" ] && MIME="$f" && break
done
if [ -z "$MIME" ]; then
  echo "❌ 未找到 mime.types，请检查 Nginx 安装"
  exit 1
fi

cat > "$CONF" <<'TPL'
# 本机静态部署配置（由 scripts/serve-dist.sh 自动生成，改这个文件没用，请改脚本）
worker_processes  1;
pid               __NGX_HOME__/nginx-__PORT_KEY__.pid;
error_log         __NGX_HOME__/error-__PORT_KEY__.log warn;

events {
    worker_connections  512;
}

http {
    include       __MIME__;
    default_type  application/octet-stream;
    access_log    __NGX_HOME__/access-__PORT_KEY__.log;

    sendfile           on;
    tcp_nopush         on;
    keepalive_timeout  65;
    # 头像上传最大 2MB，这里放宽到 5MB（Nginx 默认只有 1MB，会返回 413）
    client_max_body_size 5m;

    gzip              on;
    gzip_vary         on;
    gzip_min_length   1024;
    gzip_comp_level   5;
    gzip_types        text/plain text/css application/javascript application/json image/svg+xml;

    server {
        listen       __PORT____SSL_ARGS__;
        server_name  _;
__SSL_DIRECTIVES__
        root         __DIST__;
        index        index.html;

        # SPA：前端路由交给 index.html
        location / {
            try_files $uri $uri/ /index.html;
        }

        # 带 hash 的静态资源可长缓存
        location /assets/ {
            expires 30d;
            add_header Cache-Control "public, max-age=2592000, immutable";
        }

        location = /index.html {
            add_header Cache-Control "no-cache";
        }

        # 更新说明读源码目录里的 public/changelog.json（不是 dist 里那份拷贝）：
        # 改完文字刷新页面就能看到，不用重新 npm run build
        location = /changelog.json {
            alias  __PUBLIC__/changelog.json;
            add_header Cache-Control "no-store";
            types { }
            default_type application/json;
        }

        location /api/ {
            proxy_pass http://127.0.0.1:__BACKEND_PORT__;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

            # SSE 流式 AI 回复：必须关闭缓冲
            proxy_buffering off;
            proxy_cache off;
            proxy_read_timeout 3600s;
        }

        # 用户上传的头像（后端 /uploads/** 静态映射）；文件名带随机串，可长缓存
        location /uploads/ {
            proxy_pass http://127.0.0.1:__BACKEND_PORT__;
            proxy_set_header Host $host;
            expires 7d;
            add_header Cache-Control "public, max-age=604800";
        }
    }
}
TPL

sed -e "s|__NGX_HOME__|$NGX_HOME|g" \
    -e "s|__MIME__|$MIME|g" \
    -e "s|__DIST__|$DIST|g" \
    -e "s|__PUBLIC__|$ROOT/frontend/public|g" \
    -e "s|__PORT_KEY__|$PORT_KEY|g" \
    -e "s|__PORT__|$PORT|g" \
    -e "s|__SSL_ARGS__|$SSL_ARGS|g" \
    -e "s|__BACKEND_PORT__|$BACKEND_PORT|g" \
    "$CONF" > "$CONF.tmp" && mv "$CONF.tmp" "$CONF"

# 多行的 ssl 指令单独插：sed 的替换串里带不了换行，改用「读到占位行时把它删掉、
# 顺便把指令文件读进来」的写法，BSD sed 与 GNU sed 都支持
if [ -n "$SSL_DIRECTIVES" ]; then
  printf '%s\n' "$SSL_DIRECTIVES" > "$NGX_HOME/.ssl-directives.${PORT_KEY}.tmp"
  sed -e "/__SSL_DIRECTIVES__/{
      r $NGX_HOME/.ssl-directives.${PORT_KEY}.tmp
      d
    }" "$CONF" > "$CONF.tmp" && mv "$CONF.tmp" "$CONF"
  rm -f "$NGX_HOME/.ssl-directives.${PORT_KEY}.tmp"
else
  sed -e "/__SSL_DIRECTIVES__/d" "$CONF" > "$CONF.tmp" && mv "$CONF.tmp" "$CONF"
fi

# ---- start ---------------------------------------------------------------
"$NGINX_BIN" -p "$NGX_HOME" -c "$CONF" -t
"$NGINX_BIN" -p "$NGX_HOME" -c "$CONF"

LAN_IP="$(ipconfig getifaddr en0 2>/dev/null || echo 127.0.0.1)"
echo ""
echo "✅ 已启动（PID $(cat "$PIDFILE")）"
echo "   本机：${SCHEME}://127.0.0.1${PORT_LABEL}"
echo "   手机：${SCHEME}://${LAN_IP}${PORT_LABEL}   ← 手机与电脑连同一个 Wi-Fi"
echo "   日志：$NGX_HOME/error-${PORT_KEY}.log"
echo "   停止：$(stop_hint)"
echo ""
if [ "$SSL" = "1" ]; then
  echo "⚠️  自签证书：手机首次打开会提示「连接不安全」，选「继续访问」即可。"
  echo "    有了 HTTPS，手机浏览器才会开放定位（华为等自带浏览器在 http 下会直接拒绝）。"
  echo ""
fi
echo "⚠️  前端改动后需重新构建：bash scripts/serve-dist.sh（或先 npm run build 再 --no-build）"
echo "    例外：frontend/public/changelog.json（更新说明）改了刷新页面就生效，不用构建"
