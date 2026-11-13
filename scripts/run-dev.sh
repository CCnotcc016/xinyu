#!/usr/bin/env bash
# 本机开发启动脚本：自动加载项目根目录的 .env（密钥、端口覆盖），再启动后端。
# 用法：bash scripts/run-dev.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 免 sudo 工具链（JDK17 / Maven / Node / MySQL），不存在时用系统自带的
if [ -f "$HOME/.xinyu-toolchain/env.sh" ]; then
  # shellcheck disable=SC1091
  source "$HOME/.xinyu-toolchain/env.sh"
fi

if [ -f "$ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
else
  echo "⚠️  未找到 .env，AI 将降级为离线 Mock 模式。"
  echo "    先执行：cp .env.example .env 然后填入 AI_API_KEY"
fi

# 工具链自带的本地 MySQL（默认 3307）没在跑就顺手拉起来。
# 数据库没启动时登录会一直卡到超时，前端只会提示「网络异常」，很难看出真实原因。
MYSQL_PORT="$(printf '%s' "${SPRING_DATASOURCE_URL:-}" | sed -n 's#.*127\.0\.0\.1:\([0-9][0-9]*\).*#\1#p')"
MYSQL_PORT="${MYSQL_PORT:-3307}"
MYSQLD="$HOME/.xinyu-toolchain/mysql/bin/mysqld"
if [ -x "$MYSQLD" ] && ! nc -z 127.0.0.1 "$MYSQL_PORT" >/dev/null 2>&1; then
  echo "🐬 本地 MySQL 端口 ${MYSQL_PORT} 未运行，正在启动..."
  nohup "$MYSQLD" \
    --port="$MYSQL_PORT" \
    --socket="$HOME/.xinyu-toolchain/mysql.sock" \
    --datadir="$HOME/.xinyu-toolchain/mysql-data" \
    >>"$HOME/.xinyu-toolchain/mysql-log/mysqld$MYSQL_PORT.log" 2>&1 &
  for _ in $(seq 1 30); do
    nc -z 127.0.0.1 "$MYSQL_PORT" >/dev/null 2>&1 && break
    sleep 1
  done
  nc -z 127.0.0.1 "$MYSQL_PORT" >/dev/null 2>&1 \
    && echo "✅ MySQL 已就绪" \
    || echo "⚠️  MySQL 启动失败，请查看 ~/.xinyu-toolchain/mysql-log/mysqld$MYSQL_PORT.log"
fi

JAR="$(ls -t "$ROOT"/backend/target/xinyu-backend-*.jar 2>/dev/null | head -1 || true)"
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
  echo "🔨 未找到 jar，先执行打包..."
  (cd "$ROOT/backend" && mvn -q -DskipTests package)
  JAR="$(ls -t "$ROOT"/backend/target/xinyu-backend-*.jar | head -1)"
fi

# 本机开发固定 dev：application.yml 的默认 profile 已经改成 prod（生产必须显式注入密钥），
# 这里不显式指定的话，本地会按生产配置启动并因为缺 MYSQL_PASSWORD / JWT_SECRET 直接失败。
: "${SPRING_PROFILES_ACTIVE:=dev}"
export SPRING_PROFILES_ACTIVE

echo "🚀 启动后端（profile=${SPRING_PROFILES_ACTIVE}）"
cd "$ROOT/backend"
exec java -jar "$JAR"
