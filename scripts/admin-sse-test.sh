#!/usr/bin/env bash
# 心语 (Xinyu) · AI 情绪日记与匿名树洞
# Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
# 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
# 心语 · 后台管理 + SSE 流式接口测试
#
# 用法：
#   ADMIN_USER=admin ADMIN_PASS=你的密码 bash scripts/admin-sse-test.sh
#
# 依赖：curl、python3。
# 脚本会注册随机用户、发帖、举报并执行管理操作。
# 结束时统一删除本次创建的测试账号及其全部关联数据，不留垃圾数据。
# 管理员密码请通过 ADMIN_PASS 环境变量传入，仓库里不提供默认口令。
set -u
API=${API:-http://127.0.0.1:8080/api}
ADMIN_USER=${ADMIN_USER:-admin}
if [ -z "${ADMIN_PASS:-}" ]; then
  echo "❌ 请先用 ADMIN_PASS 环境变量传入管理员密码，例如：ADMIN_PASS=你的密码 bash scripts/admin-sse-test.sh" >&2
  exit 1
fi
pass=0; fail=0
chk() {
  if [ "$2" = "$3" ]; then echo "  ✅ $1 (=$3)"; pass=$((pass+1));
  else echo "  ❌ $1 expected=$2 actual=$3"; fail=$((fail+1)); fi
}
code() { python3 -c "import json,sys;print(json.load(sys.stdin).get('code'))"; }
val()  { python3 -c "import json,sys;d=json.load(sys.stdin).get('data');print(d$1 if d is not None else 'NONE')"; }

# ---------- 测试数据清理 ----------
CLEANUP_IDS=()
cleanup() {
  if [ ${#CLEANUP_IDS[@]} -eq 0 ]; then
    echo "== 清理：本次未创建测试账号，无需清理 =="
    return
  fi
  echo "== 清理：删除本次创建的 ${#CLEANUP_IDS[@]} 个测试账号 =="
  local at id rc
  at=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
    -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" | val "['token']")
  if [ "$at" = "NONE" ] || [ -z "$at" ]; then
    echo "  ⚠️  管理员登录失败，跳过清理。残留账号 id: ${CLEANUP_IDS[*]}"
    return
  fi
  for id in "${CLEANUP_IDS[@]}"; do
    rc=$(curl -s -X DELETE "$API/admin/users/$id" -H "Authorization: Bearer $at" | code)
    if [ "$rc" = "0" ]; then echo "  🧹 已删除用户 #$id"
    else echo "  ⚠️  删除用户 #$id 失败 (code=$rc)"; fi
  done
}
trap cleanup EXIT

# 随机生成一个格式合法的手机号，避免与既有账号撞号
gen_phone() { printf '13%09d' $(( (RANDOM % 90000 + 10000) * 10000 + RANDOM % 10000 )); }

# 过图形验证码后取一条短信验证码（依赖 dev 配置回显验证码）
sms_code_for() { # phone -> 打印 6 位短信验证码，失败打印 NONE/SMS_FAIL_<code>
  local phone=$1 cap cid answer res rc
  cap=$(curl -s "$API/auth/captcha")
  cid=$(echo "$cap" | val "['captchaId']")
  answer=$(echo "$cap" | val "['code']")
  if [ "$cid" = "NONE" ] || [ "$answer" = "NONE" ]; then echo "NONE"; return; fi
  res=$(curl -s -X POST $API/auth/sms-code -H 'Content-Type: application/json' \
    -d "{\"phone\":\"$phone\",\"captchaId\":\"$cid\",\"captchaCode\":\"$answer\"}")
  rc=$(echo "$res" | code)
  if [ "$rc" != "0" ]; then echo "SMS_FAIL_$rc"; return; fi
  echo "$res" | val "['code']"
}

echo "== 0. 畸形 JSON 请求体应返回 400 =="
U="adm$$$RANDOM"
PHONE=$(gen_phone); SMS=$(sms_code_for "$PHONE")
if [ "$SMS" = "NONE" ] || [ "${SMS#SMS_FAIL_}" != "$SMS" ]; then
  echo "  ❌ 无法获取短信验证码（$SMS）：请确认后端以 dev 配置启动（需回显验证码）"
  exit 1
fi
curl -s -X POST $API/auth/register -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"password\":\"pass123456\",\"phone\":\"$PHONE\",\"smsCode\":\"$SMS\"}" >/dev/null
TOKEN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' -d "{\"username\":\"$U\",\"password\":\"pass123456\"}" | val "['token']")
MYUID=$(curl -s $API/user/me -H "Authorization: Bearer $TOKEN" | val "['id']")
[ "$MYUID" != "NONE" ] && CLEANUP_IDS+=("$MYUID")
AUTH="Authorization: Bearer $TOKEN"
BAD_BODY='"targetType":"POST"'
chk "malformed json -> 400" 400 "$(curl -s -X POST "$API/reports" -H "$AUTH" -H 'Content-Type: application/json' -d "$BAD_BODY" | code)"

echo "== 1. 管理员登录 =="
ADM=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}")
chk "admin login code" 0 "$(echo "$ADM" | code)"
chk "admin role" ADMIN "$(echo "$ADM" | val "['user']['role']")"
AT=$(echo "$ADM" | val "['token']")
AA="Authorization: Bearer $AT"

echo "== 2. 后台列表接口 =="
chk "admin users" 0 "$(curl -s "$API/admin/users?page=1&size=5" -H "$AA" | code)"
chk "admin diaries" 0 "$(curl -s "$API/admin/diaries?page=1&size=5" -H "$AA" | code)"
chk "admin posts" 0 "$(curl -s "$API/admin/posts?page=1&size=5" -H "$AA" | code)"
chk "admin comments" 0 "$(curl -s "$API/admin/comments?page=1&size=5" -H "$AA" | code)"
chk "admin reports" 0 "$(curl -s "$API/admin/reports?page=1&size=5" -H "$AA" | code)"
chk "admin users keyword" 0 "$(curl -s "$API/admin/users?page=1&size=5&keyword=$U" -H "$AA" | code)"

echo "== 3. 用户建帖 + 举报 =="
R=$(curl -s -X POST $API/treehole/posts -H "$AUTH" -H 'Content-Type: application/json' -d '{"content":"需要管理员处理的内容","isAnonymous":true}')
PID=$(echo "$R" | val "['id']")
RJSON='{"targetType":"POST","targetId":'"$PID"',"reason":"违规内容"}'
chk "create report" 0 "$(curl -s -X POST "$API/reports" -H "$AUTH" -H 'Content-Type: application/json' -d "$RJSON" | code)"
RID=$(curl -s "$API/admin/reports?page=1&size=50" -H "$AA" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
rec=[r for r in (d['records'] if isinstance(d,dict) and 'records' in d else d) if r.get('targetId')==int('$PID')]
print(rec[0]['id'] if rec else 'NONE')")
echo "  (report id = $RID, post id = $PID)"

echo "== 4. 管理员处理举报 -> 帖子下架 =="
chk "handle report HANDLED" 0 "$(curl -s -X PUT "$API/admin/reports/$RID?action=HANDLED" -H "$AA" | code)"
chk "downed post hidden from public detail" 3001 "$(curl -s "$API/treehole/posts/$PID" -H "$AA" | code)"
chk "post status now 0 (admin list)" 0 "$(curl -s "$API/admin/posts?page=1&size=50&status=0" -H "$AA" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
rec=d['records'] if isinstance(d,dict) and 'records' in d else d
print(0 if any(r['id']==int('$PID') for r in rec) else 1)")"
chk "bad action -> 400" 400 "$(curl -s -X PUT "$API/admin/reports/$RID?action=WRONG" -H "$AA" | code)"

echo "== 5. 管理员帖子/评论/用户状态 =="
R2=$(curl -s -X POST $API/treehole/posts -H "$AUTH" -H 'Content-Type: application/json' -d '{"content":"第二个帖子用于后台操作","isAnonymous":false}')
P2=$(echo "$R2" | val "['id']")
C2=$(curl -s -X POST "$API/treehole/posts/$P2/comments" -H "$AUTH" -H 'Content-Type: application/json' -d '{"content":"给管理员用的评论"}')
CID=$(echo "$C2" | val "['id']")
chk "admin post status -> 0" 0 "$(curl -s -X PUT "$API/admin/posts/$P2/status?status=0" -H "$AA" | code)"
chk "admin comment status -> 0" 0 "$(curl -s -X PUT "$API/admin/comments/$CID/status?status=0" -H "$AA" | code)"
chk "admin user disable -> 0" 0 "$(curl -s -X PUT "$API/admin/users/$MYUID/status?status=0" -H "$AA" | code)"
chk "disabled user token rejected" 401 "$(curl -s "$API/diaries?page=1&size=5" -H "$AUTH" | code)"
chk "admin user restore -> 0" 0 "$(curl -s -X PUT "$API/admin/users/$MYUID/status?status=1" -H "$AA" | code)"
chk "user works again" 0 "$(curl -s "$API/diaries?page=1&size=5" -H "$AUTH" | code)"
chk "bad path id -> 400" 400 "$(curl -s -X PUT "$API/admin/users/NONE/status?status=0" -H "$AA" | code)"

echo "== 5b. 管理员删除帖子（连同评论、点赞一起清理） =="
chk "admin post delete -> 0" 0 "$(curl -s -X DELETE "$API/admin/posts/$P2" -H "$AA" | code)"
chk "deleted post detail -> 3001" 3001 "$(curl -s "$API/treehole/posts/$P2" -H "$AA" | code)"
chk "deleted post comments -> 3001" 3001 "$(curl -s "$API/treehole/posts/$P2/comments" -H "$AA" | code)"
chk "comment row gone (admin list)" 0 "$(curl -s "$API/admin/comments?page=1&size=50" -H "$AA" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
rec=d['records'] if isinstance(d,dict) and 'records' in d else d
print(0 if not any(r['id']==int('$CID') for r in rec) else 1)")"
chk "post gone from admin list" 0 "$(curl -s "$API/admin/posts?page=1&size=50" -H "$AA" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
rec=d['records'] if isinstance(d,dict) and 'records' in d else d
print(0 if not any(r['id']==int('$P2') for r in rec) else 1)")"
chk "delete missing post -> 3001" 3001 "$(curl -s -X DELETE "$API/admin/posts/$P2" -H "$AA" | code)"
chk "delete post without token -> 401" 401 "$(curl -s -X DELETE "$API/admin/posts/$PID" | code)"

echo "== 6. SSE 危机干预短路 =="
R3=$(curl -s -X POST $API/diaries -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"content":"最近压力很大，有时候会想死，觉得撑不下去了。","weather":"阴","scene":"深夜","privacy":1}')
DID=$(echo "$R3" | val "['id']")
STREAM=$(curl -sN --max-time 30 "$API/diaries/$DID/ai-reply/stream?token=$TOKEN")
echo "  --- SSE raw ---"
echo "$STREAM" | sed 's/^/  | /'
echo "$STREAM" | grep -q "event:crisis" && { echo "  ✅ SSE has crisis event"; pass=$((pass+1)); } || { echo "  ❌ SSE missing crisis event"; fail=$((fail+1)); }
chk "aiStatus=CRISIS" CRISIS "$(curl -s "$API/diaries/$DID" -H "$AUTH" | val "['aiStatus']")"

echo "== 6b. 后台按账号查看日记（v2.2） =="
UID_=$(curl -s "$API/admin/users?page=1&size=5&keyword=$U" -H "$AA" | val "['records'][0]['id']")
R=$(curl -s "$API/admin/diaries?page=1&size=5&userId=$UID_" -H "$AA")
chk "admin diaries by user" 0 "$(echo "$R" | code)"
chk "filter only this user" True \
  "$(echo "$R" | python3 -c "import json,sys;d=json.load(sys.stdin)['data']['records'];print(bool(d) and all(r['userId']==int('$UID_') for r in d))")"
chk "row carries owner" True \
  "$(echo "$R" | python3 -c "import json,sys;d=json.load(sys.stdin)['data']['records'];print(bool(d) and all(r.get('username') and r.get('nickname') for r in d))")"
chk "row carries crisis flag" True \
  "$(echo "$R" | python3 -c "import json,sys;d=json.load(sys.stdin)['data']['records'];print(any(r.get('crisisSupport') for r in d))")"

echo "== 7. 管理员危机预警 =="
chk "pending-count code" 0 "$(curl -s "$API/admin/crisis-alerts/pending-count" -H "$AA" | code)"
chk "crisis list code" 0 "$(curl -s "$API/admin/crisis-alerts?page=1&size=50&status=PENDING" -H "$AA" | code)"
CAID=$(curl -s "$API/admin/crisis-alerts?page=1&size=50&status=PENDING" -H "$AA" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
rec=d['records'] if isinstance(d,dict) and 'records' in d else d
hit=[r for r in rec if r.get('targetId')==int('$DID')]
print(hit[0]['id'] if hit else 'NONE')")
chk "diary crisis alert exists" 1 "$([ "$CAID" != "NONE" ] && echo 1 || echo 0)"
chk "alert shows registered phone" "$PHONE" "$(curl -s "$API/admin/crisis-alerts?page=1&size=50&status=PENDING" -H "$AA" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
rec=d['records'] if isinstance(d,dict) and 'records' in d else d
hit=[r for r in rec if r.get('targetId')==int('$DID')]
print(hit[0].get('phone') if hit else 'NONE')")"
if [ "$CAID" != "NONE" ]; then
  chk "handle alert code" 0 "$(curl -s -X PUT "$API/admin/crisis-alerts/$CAID" -H "$AA" -H 'Content-Type: application/json' -d '{"status":"HANDLED"}' | code)"
  chk "bad handle status -> 400" 400 "$(curl -s -X PUT "$API/admin/crisis-alerts/$CAID" -H "$AA" -H 'Content-Type: application/json' -d '{"status":"WRONG"}' | code)"
fi

echo
echo "==== 后台/SSE 用例: pass=$pass fail=$fail ===="
