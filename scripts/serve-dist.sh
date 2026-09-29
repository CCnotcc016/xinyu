#!/usr/bin/env bash
# 心语 (Xinyu) · AI 情绪日记与匿名树洞
# Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
# 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
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
#
# 端口各自持有独立的配置与 pid 文件（nginx-<PORT>.conf / nginx-<PORT>.pid），
# 所以 8088 和 80 可以同时跑，互不影响。
# 个别系统上 <1024 的端口会提示 permission denied，那就给命令加 sudo（停止同理）。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${PORT:-8088}"
# 80 是 http 默认端口，URL 里不显示，避免出现 http://<IP>:80 这种多余写法
PORT_LABEL=":$PORT"
[ "$PORT" = "80" ] && PORT_LABEL=""
BACKEND_PORT="${BACKEND_PORT:-8080}"

NGX_HOME="$HOME/.xinyu-nginx"
CONF="$NGX_HOME/nginx-${PORT}.conf"
PIDFILE="$NGX_HOME/nginx-${PORT}.pid"
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
  if [ "$PORT" = "8088" ]; then
    echo "${sudo_hint}bash scripts/serve-dist.sh stop"
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
  echo "ℹ️  Nginx 已在运行（PID $(cat "$PIDFILE")，http://$(ipconfig getifaddr en0 2>/dev/null || echo 127.0.0.1)${PORT_LABEL}）"
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
pid               __NGX_HOME__/nginx-__PORT__.pid;
error_log         __NGX_HOME__/error-__PORT__.log warn;

events {
    worker_connections  512;
}

http {
    include       __MIME__;
    default_type  application/octet-stream;
    access_log    __NGX_HOME__/access-__PORT__.log;

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
        listen       __PORT__;
        server_name  _;
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
    -e "s|__PORT__|$PORT|g" \
    -e "s|__BACKEND_PORT__|$BACKEND_PORT|g" \
    "$CONF" > "$CONF.tmp" && mv "$CONF.tmp" "$CONF"

# ---- start ---------------------------------------------------------------
"$NGINX_BIN" -p "$NGX_HOME" -c "$CONF" -t
"$NGINX_BIN" -p "$NGX_HOME" -c "$CONF"

LAN_IP="$(ipconfig getifaddr en0 2>/dev/null || echo 127.0.0.1)"
echo ""
echo "✅ 已启动（PID $(cat "$PIDFILE")）"
echo "   本机：http://127.0.0.1${PORT_LABEL}"
echo "   手机：http://${LAN_IP}${PORT_LABEL}   ← 手机与电脑连同一个 Wi-Fi"
echo "   日志：$NGX_HOME/error-${PORT}.log"
echo "   停止：$(stop_hint)"
echo ""
echo "⚠️  前端改动后需重新构建：bash scripts/serve-dist.sh（或先 npm run build 再 --no-build）"
