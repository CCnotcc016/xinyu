#!/usr/bin/env bash
# 心语 (Xinyu) · AI 情绪日记与匿名树洞
# Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
# 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
# 本机开发启动脚本：自动加载项目根目录的 .env（密钥、端口覆盖），再启动后端。
# 用法：bash scripts/run-dev.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ -f "$ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
else
  echo "⚠️  未找到 .env，AI 将降级为离线 Mock 模式。"
  echo "    先执行：cp .env.example .env 然后填入 AI_API_KEY"
fi

JAR="$(ls -t "$ROOT"/backend/target/xinyu-backend-*.jar 2>/dev/null | head -1 || true)"
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
  echo "🔨 未找到 jar，先执行打包..."
  (cd "$ROOT/backend" && mvn -q -DskipTests package)
  JAR="$(ls -t "$ROOT"/backend/target/xinyu-backend-*.jar | head -1)"
fi

echo "🚀 启动后端（profile=${SPRING_PROFILES_ACTIVE:-dev}）"
cd "$ROOT/backend"
exec java -jar "$JAR"
