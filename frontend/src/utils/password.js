// 与后端 PasswordPolicy 保持一致的密码规则，仅做前端预校验，最终以后端为准
export const PASSWORD_MIN = 8
export const PASSWORD_MAX = 32

const WEAK = new Set([
  '12345678', '123456789', '1234567890', '87654321', '11111111',
  'password', 'password1', 'password123', 'passw0rd', 'qwerty123',
  'abc12345', 'a1234567', 'abcd1234', '1qaz2wsx', '1q2w3e4r',
  'iloveyou', 'woaini1314', '5201314520', 'admin123', 'administrator',
  'xinyu123', 'xinyu1234', 'test1234', 'changeme'
])

/** 校验密码强度；通过返回空字符串，不通过返回具体原因（文案与后端逐条一致） */
export function checkPassword(password, username = '') {
  const pwd = String(password ?? '')
  if (!pwd.trim()) return '请输入密码'
  if (pwd.length < PASSWORD_MIN) return `密码至少 ${PASSWORD_MIN} 位`
  if (pwd.length > PASSWORD_MAX) return `密码不能超过 ${PASSWORD_MAX} 位`
  if (!/^[\x21-\x7e]+$/.test(pwd)) return '密码只能使用英文字母、数字和常见符号'
  if (!/[A-Za-z]/.test(pwd) || !/\d/.test(pwd)) return '密码需要同时包含字母和数字'
  if (WEAK.has(pwd.toLowerCase())) return '这个密码太常见了，换一个'
  const name = String(username ?? '').trim().toLowerCase()
  if (name.length >= 3 && pwd.toLowerCase().includes(name)) return '密码里不能包含用户名'
  if (/^(.)\1+$/.test(pwd)) return '密码不能是同一个字符重复'
  return ''
}
