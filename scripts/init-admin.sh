#!/usr/bin/env bash
# 心语 (Xinyu) · AI 情绪日记与匿名树洞
# Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
# 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
#
# 创建 / 重置管理员账号。
# 本仓库不预置任何账号和默认密码，部署后用它来创建自己的管理员。
#
# 用法：
#   bash scripts/init-admin.sh                      # 交互式输入用户名和密码
#   ADMIN_USER=admin ADMIN_PASS='你的密码' bash scripts/init-admin.sh
#   ADMIN_USER=admin ADMIN_PASS='你的密码' ADMIN_PHONE=13800000000 bash scripts/init-admin.sh
#
# 可选环境变量：
#   MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD / MYSQL_DB   数据库连接（默认读 .env，再退回 127.0.0.1:3306 root/root xinyu）
#   MYSQL_BIN                                                          指定 mysql 客户端完整路径
#   API                                                                后端地址，用于创建后自动验证登录（默认 http://127.0.0.1:8080/api）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 复用 .env 里的数据库配置（存在才读）
if [ -f "$ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

DB_HOST="${MYSQL_HOST:-127.0.0.1}"
DB_PORT="${MYSQL_PORT:-3306}"
DB_USER="${MYSQL_USER:-root}"
DB_PASS="${MYSQL_PASSWORD:-root}"
DB_NAME="${MYSQL_DB:-xinyu}"
API="${API:-http://127.0.0.1:8080/api}"

# ---------- 生成 BCrypt 哈希 ----------
gen_hash() {
  local pass="$1" hash=""
  if command -v htpasswd >/dev/null 2>&1; then
    hash="$(htpasswd -bnBC 10 "" "$pass" | tr -d ':\n')"
  elif python3 -c 'import bcrypt' >/dev/null 2>&1; then
    hash="$(python3 -c 'import bcrypt,sys; print(bcrypt.hashpw(sys.argv[1].encode(), bcrypt.gensalt(10, prefix=b"2a")).decode())' "$pass")"
  else
    echo "❌ 需要 htpasswd（macOS 自带；Ubuntu: apt install apache2-utils）或 python3 的 bcrypt 模块来生成 BCrypt 哈希。" >&2
    return 1
  fi
  # htpasswd 产出 $2y$，Spring Security 认 $2a$/$2b$/$2y$，统一成 $2a$ 更稳妥
  printf '%s' "${hash/\$2y\$/\$2a\$}"
}

# ---------- 执行 SQL（自动选择本机客户端 / Docker 容器）----------
# 附加参数（如 -N -B）会透传给 mysql 客户端
run_sql() {
  if [ -n "${MYSQL_BIN:-}" ]; then
    "$MYSQL_BIN" "$@" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME"
  elif command -v mysql >/dev/null 2>&1; then
    mysql "$@" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME"
  elif command -v docker >/dev/null 2>&1 && docker compose -f "$ROOT/docker-compose.yml" ps mysql >/dev/null 2>&1; then
    docker compose -f "$ROOT/docker-compose.yml" exec -T mysql mysql "$@" -uroot -p"$DB_PASS" "$DB_NAME"
  else
    echo "❌ 没找到可用的 mysql 客户端，也没有在运行的 docker compose mysql 服务。" >&2
    echo "   · 本机安装：brew install mysql-client / apt install mysql-client" >&2
    echo "   · 或指定路径：MYSQL_BIN=/usr/local/mysql/bin/mysql bash scripts/init-admin.sh" >&2
    return 1
  fi
}

# ---------- 前置检查：表必须先建好 ----------
# dev profile 第一次启动后端时会自动执行 schema.sql 建表，所以没启动过后端就来这儿会扑空
if ! printf 'CREATE DATABASE IF NOT EXISTS `%s` DEFAULT CHARSET utf8mb4;\n' "$DB_NAME" | run_sql >/dev/null 2>&1; then
  echo "❌ 连不上数据库 $DB_HOST:$DB_PORT（user=$DB_USER, db=$DB_NAME）。" >&2
  echo "   本机开发请确认 MySQL 已启动、.env 里的 MYSQL_* 配置正确；Docker 部署请先 docker compose up -d mysql。" >&2
  exit 1
fi

USER_TABLE_COUNT="$(printf "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '%s' AND table_name = 'user';\n" "$DB_NAME" | run_sql -N -B 2>/dev/null | tail -1 | tr -d '[:space:]' || true)"
if [ "$USER_TABLE_COUNT" != "1" ]; then
  echo "❌ 数据库 $DB_NAME 里还没有 user 表（还没初始化）。" >&2
  echo "   先启动一次后端让建表脚本跑完，再执行本脚本：" >&2
  echo "     · 本机：bash scripts/run-dev.sh" >&2
  echo "     · Docker：docker compose up -d mysql backend" >&2
  exit 1
fi

# ---------- 收集账号信息 ----------
ADMIN_USER="${ADMIN_USER:-}"
if [ -z "$ADMIN_USER" ]; then
  if [ -t 0 ]; then
    read -r -p "管理员用户名（默认 admin）：" ADMIN_USER || true
  fi
  ADMIN_USER="${ADMIN_USER:-admin}"
fi
if ! printf '%s' "$ADMIN_USER" | grep -Eq '^[A-Za-z0-9_]{3,20}$'; then
  echo "❌ 用户名只能用字母、数字、下划线，长度 3-20。" >&2
  exit 1
fi

ADMIN_PASS="${ADMIN_PASS:-}"
if [ -z "$ADMIN_PASS" ]; then
  if [ ! -t 0 ]; then
    echo "❌ 非交互环境请用环境变量传入密码，例如：ADMIN_PASS='你的密码' bash scripts/init-admin.sh" >&2
    exit 1
  fi
  read -r -s -p "管理员密码（至少 6 位）：" ADMIN_PASS; echo
  read -r -s -p "再输一遍确认：" PASS2; echo
  if [ "$ADMIN_PASS" != "$PASS2" ]; then
    echo "❌ 两次输入的密码不一致。" >&2
    exit 1
  fi
fi
if [ "${#ADMIN_PASS}" -lt 6 ]; then
  echo "❌ 密码至少 6 位。" >&2
  exit 1
fi

ADMIN_PHONE="${ADMIN_PHONE:-}"
if [ -z "$ADMIN_PHONE" ] && [ -t 0 ]; then
  read -r -p "管理员手机号（可留空，仅用于接收危机预警通知）：" ADMIN_PHONE || true
fi
if [ -n "$ADMIN_PHONE" ] && ! printf '%s' "$ADMIN_PHONE" | grep -Eq '^1[3-9][0-9]{9}$'; then
  echo "❌ 手机号格式不正确（需为 11 位大陆手机号，或留空）。" >&2
  exit 1
fi

NICKNAME="${ADMIN_NICKNAME:-系统管理员}"
HASH="$(gen_hash "$ADMIN_PASS")"
PHONE_SQL="NULL"
[ -n "$ADMIN_PHONE" ] && PHONE_SQL="'$ADMIN_PHONE'"

echo "🔧 正在写入管理员账号：$ADMIN_USER"
printf 'CREATE DATABASE IF NOT EXISTS `%s` DEFAULT CHARSET utf8mb4;\n' "$DB_NAME" | run_sql >/dev/null

printf '%s\n' "
INSERT INTO \`user\` (\`username\`, \`password_hash\`, \`nickname\`, \`phone\`, \`role\`, \`status\`)
VALUES ('$ADMIN_USER', '$HASH', '$NICKNAME', $PHONE_SQL, 'ADMIN', 1)
ON DUPLICATE KEY UPDATE
  \`password_hash\` = VALUES(\`password_hash\`),
  \`nickname\`      = VALUES(\`nickname\`),
  \`phone\`         = VALUES(\`phone\`),
  \`role\`          = 'ADMIN',
  \`status\`        = 1;
SELECT \`id\`, \`username\`, \`nickname\`, \`role\`, \`status\` FROM \`user\` WHERE \`username\` = '$ADMIN_USER';
" | run_sql

echo "✅ 管理员已就绪：${ADMIN_USER}（role=ADMIN）"

# ---------- 后端在跑的话，顺手验证一次登录 ----------
if curl -s -m 3 "$API/auth/login" >/dev/null 2>&1; then
  RESP="$(curl -s -m 5 -X POST "$API/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$(printf '%s' "$ADMIN_PASS" | sed 's/["\\]/\\&/g')\"}")"
  if printf '%s' "$RESP" | grep -q '"token"'; then
    echo "✅ 登录接口验证通过，现在可以用该账号登录管理后台。"
  else
    echo "⚠️  写入成功，但登录接口未返回 token，请检查：$RESP"
  fi
else
  echo "ℹ️  后端未在 $API 运行，跳过登录验证。启动后端后用该账号登录即可。"
fi
