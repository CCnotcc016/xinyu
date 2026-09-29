#!/usr/bin/env bash
# 心语 (Xinyu) · AI 情绪日记与匿名树洞
# Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
# 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
# 心语 · 用户端接口冒烟测试
#
# 用法：
#   bash scripts/smoke-test.sh
#   API=http://127.0.0.1:9090/api bash scripts/smoke-test.sh
#
# 依赖：curl、python3。
# 脚本会注册随机用户并写入测试数据，运行结束后统一删除这些账号
# （连带清理它们产生的日记、帖子、评论、点赞、举报、危机预警），不会留下垃圾数据。
# v2.0 起注册必须带手机号：脚本会自动过图形验证码、取短信验证码再注册，
# 因此需要在 dev 配置下运行（xinyu.captcha.echo-code / xinyu.sms.echo-code=true 回显验证码）。
# 清理需要管理员账号：请通过环境变量传入，仓库里不提供默认口令
#   ADMIN_USER=admin ADMIN_PASS=你的密码 bash scripts/smoke-test.sh
set -u
API=${API:-http://127.0.0.1:8080/api}
ADMIN_USER=${ADMIN_USER:-admin}
if [ -z "${ADMIN_PASS:-}" ]; then
  echo "❌ 请先用 ADMIN_PASS 环境变量传入管理员密码，例如：ADMIN_PASS=你的密码 bash scripts/smoke-test.sh" >&2
  exit 1
fi
pass=0; fail=0
chk() { # name expected actual
  if [ "$2" = "$3" ]; then echo "  ✅ $1 (=$3)"; pass=$((pass+1));
  else echo "  ❌ $1 expected=$2 actual=$3"; fail=$((fail+1)); fi
}
code() { python3 -c "import json,sys;print(json.load(sys.stdin).get('code'))" 2>/dev/null || echo "PARSE_ERR"; }
val()  { python3 -c "import json,sys;d=json.load(sys.stdin).get('data');print(d$1 if d is not None else 'NONE')" 2>/dev/null || echo "NONE"; }

# ---------- 测试数据清理 ----------
# 注册的账号 id 收集在这里，退出时（含中途失败、Ctrl-C）统一删除
CLEANUP_IDS=()
cleanup() {
  if [ ${#CLEANUP_IDS[@]} -eq 0 ]; then
    echo "== 清理：本次未创建测试账号，无需清理 =="
    return
  fi
  local ids
  # 同一账号可能在多处被记录，去重后再删，避免对同一个 id 重复 DELETE 报 1004
  ids=$(printf '%s\n' "${CLEANUP_IDS[@]}" | sort -u | tr '\n' ' ')
  echo "== 清理：删除本次创建的 $(echo $ids | wc -w | tr -d ' ') 个测试账号 =="
  local at
  at=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
    -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" | val "['token']")
  if [ "$at" = "NONE" ] || [ -z "$at" ]; then
    echo "  ⚠️  管理员登录失败，跳过清理。残留账号 id: $ids"
    echo "     可手动删除：DELETE $API/admin/users/<id>"
    return
  fi
  local id rc
  for id in $ids; do
    rc=$(curl -s -X DELETE "$API/admin/users/$id" -H "Authorization: Bearer $at" | code)
    if [ "$rc" = "0" ]; then echo "  🧹 已删除用户 #$id"
    else echo "  ⚠️  删除用户 #$id 失败 (code=$rc)"; fi
  done
}
trap cleanup EXIT

# 随机生成一个格式合法的手机号，避免与既有账号撞号
gen_phone() { printf '13%09d' $(( (RANDOM % 90000 + 10000) * 10000 + RANDOM % 10000 )); }

# 过图形验证码后取一条短信验证码
# 依赖开发环境 xinyu.captcha.echo-code / xinyu.sms.echo-code=true 回显答案；
# 生产环境关闭回显时脚本无法自动注册（属预期，改用真实短信验证）。
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

# 注册一个用户并把 id 记入待清理列表
reg_user() { # username -> 打印 id
  local name=$1 ph sc
  ph=$(gen_phone)
  sc=$(sms_code_for "$ph")
  if [ "$sc" = "NONE" ] || [ "${sc#SMS_FAIL_}" != "$sc" ]; then
    echo "  ⚠️  取短信验证码失败（$sc）：请确认后端运行在 dev 配置下"
    echo "NONE"; return
  fi
  curl -s -X POST $API/auth/register -H 'Content-Type: application/json' \
    -d "{\"username\":\"$name\",\"password\":\"pass123456\",\"phone\":\"$ph\",\"smsCode\":\"$sc\"}" >/dev/null
  local id
  id=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
    -d "{\"username\":\"$name\",\"password\":\"pass123456\"}" | val "['user']['id']")
  [ "$id" != "NONE" ] && CLEANUP_IDS+=("$id")
  echo "$id"
}

RND="$RANDOM$RANDOM"          # 每轮不重复的后缀，用户名/昵称测试都用它，避免撞上历史数据
U="user$RND"
PHONE=$(gen_phone)
SMS=$(sms_code_for "$PHONE")
echo "== 1. 人机验证 + 短信验证码 =="
if [ "$SMS" = "NONE" ] || [ "${SMS#SMS_FAIL_}" != "$SMS" ]; then
  echo "  ❌ 无法获取短信验证码（$SMS）：请确认后端以 dev 配置启动（需回显验证码）"
  echo ""; echo "==== 普通用户用例: pass=$pass fail=$fail ===="
  exit 1
fi
chk "sms code 6 digits" 6 "${#SMS}"

CAP=$(curl -s "$API/auth/captcha")
CID=$(echo "$CAP" | val "['captchaId']")
CANS=$(echo "$CAP" | val "['code']")
chk "captcha image is data-url" "data:image/png" "$(echo "$CAP" | val "['image']" | cut -c1-14)"
# 注意：`chk ... "$(curl ... -d "{\"a\":...}")"` 这种「嵌套 $( ) 里再写转义双引号」在 bash 3.2 下会被
# 重新拆词，body 必须先在顶层赋值再引用
BODY="{\"phone\":\"$(gen_phone)\",\"captchaId\":\"$CID\",\"captchaCode\":\"zzzz\"}"
R=$(curl -s -X POST $API/auth/sms-code -H 'Content-Type: application/json' -d "$BODY")
chk "wrong captcha answer blocked" 1016 "$(echo "$R" | code)"
# 同一张验证码答对一次后立即失效（答错不消费，见 CaptchaService）
BODY="{\"phone\":\"$(gen_phone)\",\"captchaId\":\"$CID\",\"captchaCode\":\"$CANS\"}"
R=$(curl -s -X POST $API/auth/sms-code -H 'Content-Type: application/json' -d "$BODY")
chk "captcha accepted once" 0 "$(echo "$R" | code)"
BODY="{\"phone\":\"$(gen_phone)\",\"captchaId\":\"$CID\",\"captchaCode\":\"$CANS\"}"
R=$(curl -s -X POST $API/auth/sms-code -H 'Content-Type: application/json' -d "$BODY")
chk "captcha is one-shot" 1015 "$(echo "$R" | code)"

echo "== 2. 注册（手机号 + 短信验证码） =="
R=$(curl -s -X POST $API/auth/register -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"password\":\"pass123456\",\"nickname\":\"测试昵称\",\"phone\":\"$PHONE\",\"smsCode\":\"$SMS\"}")
chk "register code" 0 "$(echo "$R" | code)"
chk "register returns masked phone" "****" "$(echo "$R" | val "['phone']" | cut -c4-7)"
chk "register phoneBound" True "$(echo "$R" | val "['phoneBound']")"
RID=$(echo "$R" | val "['id']")
UID_=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"password\":\"pass123456\"}" | val "['user']['id']")

echo "== 3. 注册校验 =="
chk "register without phone -> 400" 400 "$(curl -s -X POST $API/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"nophone'$$'","password":"pass123456"}' | code)"
BODY="{\"username\":\"badsms$RND\",\"password\":\"pass123456\",\"phone\":\"$(gen_phone)\",\"smsCode\":\"000000\"}"
R=$(curl -s -X POST $API/auth/register -H 'Content-Type: application/json' -d "$BODY")
chk "register with bad sms -> 1017" 1017 "$(echo "$R" | code)"
# 昵称审核在短信校验之后，所以这条要拿一条真实验证码
NP=$(gen_phone); NS=$(sms_code_for "$NP")
BODY="{\"username\":\"badname$RND\",\"password\":\"pass123456\",\"nickname\":\"乳房好大\",\"phone\":\"$NP\",\"smsCode\":\"$NS\"}"
R=$(curl -s -X POST $API/auth/register -H 'Content-Type: application/json' -d "$BODY")
chk "nickname blocklist -> 1014" 1014 "$(echo "$R" | code)"
chk "sms resend too frequent -> 1018" "SMS_FAIL_1018" "$(sms_code_for "$PHONE")"

echo "== 4. 重复注册应失败 =="
NP2=$(gen_phone); NS2=$(sms_code_for "$NP2")
R=$(curl -s -X POST $API/auth/register -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"password\":\"pass123456\",\"phone\":\"$NP2\",\"smsCode\":\"$NS2\"}")
chk "duplicate register code" 1001 "$(echo "$R" | code)"

echo "== 5. 参数校验 =="
R=$(curl -s -X POST $API/auth/register -H 'Content-Type: application/json' -d '{"username":"ab","password":"123"}')
chk "validation code" 400 "$(echo "$R" | code)"

echo "== 4. 登录 =="
R=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' -d "{\"username\":\"$U\",\"password\":\"pass123456\"}")
chk "login code" 0 "$(echo "$R" | code)"
TOKEN=$(echo "$R" | val "['token']")
U1=$(echo "$R" | val "['user']['id']")
[ "$UID_" != "NONE" ] && CLEANUP_IDS+=("$UID_")
echo "  token len=${#TOKEN}"
AUTH="Authorization: Bearer $TOKEN"

echo "== 5. 错误密码 =="
R=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' -d "{\"username\":\"$U\",\"password\":\"wrongpass1\"}")
chk "bad password code" 1002 "$(echo "$R" | code)"

echo "== 6. GET /user/me =="
R=$(curl -s $API/user/me -H "$AUTH")
chk "me code" 0 "$(echo "$R" | code)"
chk "me username" "$U" "$(echo "$R" | val "['username']")"
chk "me phone masked" "${PHONE:0:3}****${PHONE:7}" "$(echo "$R" | val "['phone']")"
chk "me phoneBound" True "$(echo "$R" | val "['phoneBound']")"

echo "== 7. 无 token 访问 =="
chk "me without token" 401 "$(curl -s -o /dev/null -w '%{http_code}' $API/user/me)"

echo "== 8. 写日记 =="
R=$(curl -s -X POST $API/diaries -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"content":"今天天气很好，和朋友一起吃了饭，心情不错。","emotionTags":["开心"],"weather":"晴天","scene":"社交","privacy":"PRIVATE"}')
chk "create diary code" 0 "$(echo "$R" | code)"
DID=$(echo "$R" | val "['id']")
echo "  diaryId=$DID"

echo "== 9. 日记列表分页 =="
R=$(curl -s "$API/diaries?page=1&size=10" -H "$AUTH")
chk "list diary code" 0 "$(echo "$R" | code)"
chk "list total>=1" "1" "$(python3 -c "import json,sys;print(1 if json.load(sys.stdin)['data']['total']>=1 else 0)" <<<"$R" 2>/dev/null)"
chk "emotionTags roundtrip" "['开心']" "$(echo "$R" | val "['records'][0]['emotionTags']")"

echo "== 10. 日记详情 =="
R=$(curl -s "$API/diaries/$DID" -H "$AUTH")
chk "diary detail code" 0 "$(echo "$R" | code)"
chk "diary aiStatus PENDING" "PENDING" "$(echo "$R" | val "['aiStatus']")"

echo "== 11. 修改日记 =="
R=$(curl -s -X PUT "$API/diaries/$DID" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"content":"改过了：今天有点累，但还不错。","emotionTags":["疲惫"],"weather":"阴天","scene":"工作","privacy":"PUBLIC"}')
chk "update diary code" 0 "$(echo "$R" | code)"
R=$(curl -s "$API/diaries/$DID" -H "$AUTH")
chk "update persisted weather" "阴天" "$(echo "$R" | val "['weather']")"
chk "update persisted tags" "['疲惫']" "$(echo "$R" | val "['emotionTags']")"

echo "== 12. 越权访问他人日记 =="
U2="other$$$RANDOM"
reg_user "$U2" >/dev/null
T2=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' -d "{\"username\":\"$U2\",\"password\":\"pass123456\"}" | val "['token']")
chk "other user diary detail -> 403" 403 "$(curl -s "$API/diaries/$DID" -H "Authorization: Bearer $T2" | code)"

echo "== 13. 趋势/分布统计 =="
chk "trend code" 0 "$(curl -s "$API/diaries/stats/trend?range=week" -H "$AUTH" | code)"
chk "distribution code" 0 "$(curl -s "$API/diaries/stats/distribution" -H "$AUTH" | code)"

echo "== 14. 树洞发帖 =="
R=$(curl -s -X POST $API/treehole/posts -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"content":"最近压力有点大，来这里说说话。","isAnonymous":true}')
chk "create post code" 0 "$(echo "$R" | code)"
PID=$(echo "$R" | val "['id']")
chk "anonymous nickname" "匿名" "$(echo "$R" | val "['nickname']")"

echo "== 15. 敏感词拦截 =="
chk "sensitive post blocked" 4001 "$(curl -s -X POST $API/treehole/posts -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"content":"我要卖毒品","isAnonymous":true}' | code)"

echo "== 16. 帖子列表/详情 =="
chk "post list code" 0 "$(curl -s "$API/treehole/posts?page=1&size=10" -H "$AUTH" | code)"
chk "post detail code" 0 "$(curl -s "$API/treehole/posts/$PID" -H "$AUTH" | code)"

echo "== 17. 点赞 / 取消 =="
R=$(curl -s -X POST "$API/treehole/posts/$PID/like" -H "$AUTH")
chk "like code" 0 "$(echo "$R" | code)"
chk "likeCount=1" 1 "$(echo "$R" | val "['likeCount']")"
R=$(curl -s -X POST "$API/treehole/posts/$PID/like" -H "$AUTH")
chk "duplicate like idempotent" 1 "$(echo "$R" | val "['likeCount']")"
R=$(curl -s -X DELETE "$API/treehole/posts/$PID/like" -H "$AUTH")
chk "unlike count=0" 0 "$(echo "$R" | val "['likeCount']")"

echo "== 18. 评论 =="
R=$(curl -s -X POST "$API/treehole/posts/$PID/comments" -H "$AUTH" -H 'Content-Type: application/json' -d '{"content":"抱抱你，会好起来的。"}')
chk "comment code" 0 "$(echo "$R" | code)"
CID=$(echo "$R" | val "['id']")
chk "comment parentId null" None "$(echo "$R" | val "['parentId']")"
R=$(curl -s "$API/treehole/posts/$PID" -H "$AUTH")
chk "commentCount=1" 1 "$(echo "$R" | val "['commentCount']")"
chk "comment list code" 0 "$(curl -s "$API/treehole/posts/$PID/comments?page=1&size=10" -H "$AUTH" | code)"

echo "== 18b. 评论回复 =="
R=$(curl -s -X POST "$API/treehole/posts/$PID/comments" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"content":"谢谢你，我也在慢慢好起来。","parentId":'"$CID"'}')
chk "reply code" 0 "$(echo "$R" | code)"
chk "reply parentId" "$CID" "$(echo "$R" | val "['parentId']")"
R=$(curl -s "$API/treehole/posts/$PID/comments?page=1&size=10" -H "$AUTH")
chk "reply list total" 2 "$(echo "$R" | val "['total']")"
# 第 2 条是回复，应该带上被回复人的昵称
chk "replyToNickname present" True \
  "$(echo "$R" | python3 -c "import json,sys;r=json.load(sys.stdin)['data']['records'][1];print(bool(r.get('replyToNickname')))")"
chk "reply comments count=2" 2 "$(curl -s "$API/treehole/posts/$PID" -H "$AUTH" | val "['commentCount']")"
# 回复另一篇帖子里的评论应当被拒绝（防跨帖串楼）
R=$(curl -s -X POST $API/treehole/posts -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"content":"串楼测试用的帖子。","isAnonymous":true}')
PID2=$(echo "$R" | val "['id']")
R=$(curl -s -X POST "$API/treehole/posts/$PID2/comments" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"content":"串楼测试","parentId":'"$CID"'}')
chk "cross-post reply rejected" 3002 "$(echo "$R" | code)"
chk "cross-post reply not counted" 0 "$(curl -s "$API/treehole/posts/$PID2" -H "$AUTH" | val "['commentCount']")"

echo "== 19. 举报 =="
REPORT_JSON='{"targetType":"POST","targetId":'"$PID"',"reason":"测试举报"}'
chk "report code" 0 "$(curl -s -X POST "$API/reports" -H "$AUTH" -H 'Content-Type: application/json' -d "$REPORT_JSON" | code)"

echo "== 20. 普通用户访问后台应 403 =="
chk "user -> admin 403" 403 "$(curl -s -o /dev/null -w '%{http_code}' $API/admin/users -H "$AUTH")"

echo "== 21. 危机关怀（日记） =="
R=$(curl -s -X POST $API/diaries -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"content":"最近真的很累，有时候会想自杀，觉得撑不下去了。","privacy":"PRIVATE"}')
chk "crisis diary created" 0 "$(echo "$R" | code)"
CDID=$(echo "$R" | val "['id']")
chk "crisisSupport true" True "$(echo "$R" | val "['crisisSupport']")"
chk "crisisSupport persisted" True "$(curl -s "$API/diaries/$CDID" -H "$AUTH" | val "['crisisSupport']")"

echo "== 22. 危机关怀（树洞）+ 管理员预警 =="
R=$(curl -s -X POST $API/treehole/posts -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"content":"测试：我又想自残了，谁来拉我一把。","isAnonymous":true}')
chk "crisis post code" 0 "$(echo "$R" | code)"
chk "crisisSupport true (post)" True "$(echo "$R" | val "['crisisSupport']")"
CPID=$(echo "$R" | val "['id']")

echo "== 23. 昵称 / 密码 / 手机号 / 头像 =="
chk "nickname blocklist on update -> 1014" 1014 "$(curl -s -X PUT $API/user/me -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"nickname":"习近平"}' | code)"
chk "nickname update code" 0 "$(curl -s -X PUT $API/user/me -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"nickname":"冒烟测试昵称"}' | code)"
chk "nickname persisted" "冒烟测试昵称" "$(curl -s $API/user/me -H "$AUTH" | val "['nickname']")"
chk "change password wrong old -> 1013" 1013 "$(curl -s -X PUT $API/user/password -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"oldPassword":"wrongold1","newPassword":"pass654321"}' | code)"
chk "change password code" 0 "$(curl -s -X PUT $API/user/password -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"oldPassword":"pass123456","newPassword":"pass654321"}' | code)"
BODY="{\"username\":\"$U\",\"password\":\"pass123456\"}"
R=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' -d "$BODY")
chk "old password rejected" 1002 "$(echo "$R" | code)"
BODY="{\"username\":\"$U\",\"password\":\"pass654321\"}"
R=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' -d "$BODY")
chk "new password works" 0 "$(echo "$R" | code)"
NEWPHONE=$(gen_phone); NEWSMS=$(sms_code_for "$NEWPHONE")
BODY="{\"phone\":\"$NEWPHONE\",\"smsCode\":\"$NEWSMS\",\"password\":\"pass654321\"}"
R=$(curl -s -X PUT $API/user/phone -H "$AUTH" -H 'Content-Type: application/json' -d "$BODY")
chk "change phone code" 0 "$(echo "$R" | code)"
chk "new phone masked" "${NEWPHONE:0:3}****${NEWPHONE:7}" "$(curl -s $API/user/me -H "$AUTH" | val "['phone']")"
echo "not an image" > /tmp/xinyu-smoke-notimage.txt
chk "avatar rejects text -> 1021" 1021 "$(curl -s -X POST $API/user/avatar -H "$AUTH" \
  -F "file=@/tmp/xinyu-smoke-notimage.txt" | code)"
rm -f /tmp/xinyu-smoke-notimage.txt

echo "== 24. 管理员危机预警列表（需管理员账号） =="
AT=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" | val "['token']")
if [ "$AT" = "NONE" ] || [ -z "$AT" ]; then
  echo "  ⚠️  管理员登录失败，跳过预警用例"
else
  AA2="Authorization: Bearer $AT"
  chk "pending-count code" 0 "$(curl -s "$API/admin/crisis-alerts/pending-count" -H "$AA2" | code)"
  chk "crisis list code" 0 "$(curl -s "$API/admin/crisis-alerts?page=1&size=50&status=PENDING" -H "$AA2" | code)"
  CAID=$(curl -s "$API/admin/crisis-alerts?page=1&size=50&status=PENDING" -H "$AA2" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
rec=d['records'] if isinstance(d,dict) and 'records' in d else d
hit=[r for r in rec if r.get('targetId') in (int('$CDID'), int('$CPID'))]
print(hit[0]['id'] if hit else 'NONE')")
  chk "alert carries contact phone" "$NEWPHONE" "$(curl -s "$API/admin/crisis-alerts?page=1&size=50&status=PENDING" -H "$AA2" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
rec=d['records'] if isinstance(d,dict) and 'records' in d else d
hit=[r for r in rec if r.get('targetId') in (int('$CDID'), int('$CPID'))]
print(hit[0].get('phone') if hit else 'NONE')")"
  if [ "$CAID" != "NONE" ]; then
    chk "mark handled code" 0 "$(curl -s -X PUT "$API/admin/crisis-alerts/$CAID" -H "$AA2" -H 'Content-Type: application/json' \
      -d '{"status":"HANDLED"}' | code)"
    chk "alert no longer pending" 0 "$(curl -s "$API/admin/crisis-alerts?page=1&size=50&status=PENDING" -H "$AA2" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
rec=d['records'] if isinstance(d,dict) and 'records' in d else d
print(1 if any(r['id']==int('$CAID') for r in rec) else 0)")"
  else
    echo "  ⚠️  未找到本次创建的预警记录，跳过后两步"
  fi
fi

echo ""
echo "==== 普通用户用例: pass=$pass fail=$fail ===="
echo "TOKEN_USER=$TOKEN"
