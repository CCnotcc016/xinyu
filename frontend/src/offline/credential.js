/**
 * 本机账号凭证：为了「后端没启动时也能进 App」。
 *
 * 在线登录成功后记一份，之后连不上服务器时，用同样的用户名 + 密码就能离线进去，
 * 写下的日记进待同步队列，等后端起来再补传。
 *
 * 存什么、不存什么：
 *   · 不存密码本身，只存 盐 + PBKDF2 校验值（crypto.subtle 不可用时退化成弱一些的
 *     本机校验值，见 fallbackHash —— 那种环境本来也只能做「本地便利校验」）；
 *   · 存 user 和 token：token 桌面版本来就存在 localStorage 里，这里多留一份是为了
 *     退出登录之后仍然能离线进入；密码仍然是必须输入的。
 *   · 只记最近 5 个账号，都是这台设备上用过的，不会同步到任何地方。
 */

const KEY = 'xinyu_offline_cred'
const MAX_ACCOUNTS = 5
const PBKDF2_ITERATIONS = 120000

function readAll() {
  try {
    const map = JSON.parse(localStorage.getItem(KEY) || '{}')
    return map && typeof map === 'object' ? map : {}
  } catch {
    // 隐私模式或被手改坏了：当成没有记录，绝不因此挡住正常登录
    return {}
  }
}

function writeAll(map) {
  try {
    localStorage.setItem(KEY, JSON.stringify(map))
  } catch {
    // 写不进去就算了，下次登录再记
  }
}

export function normalizeUsername(username) {
  return String(username ?? '').trim().toLowerCase()
}

function randomSalt() {
  const bytes = new Uint8Array(16)
  if (globalThis.crypto?.getRandomValues) {
    crypto.getRandomValues(bytes)
  } else {
    for (let i = 0; i < bytes.length; i += 1) bytes[i] = Math.floor(Math.random() * 256)
  }
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

function toHex(bytes) {
  return Array.from(new Uint8Array(bytes), (b) => b.toString(16).padStart(2, '0')).join('')
}

/** 没有 crypto.subtle 的场合（http://局域网IP 打开时不算安全来源）用的退化校验值 */
function fallbackHash(secret, salt) {
  let h1 = 0x811c9dc5
  let h2 = 0x01000193
  const input = `${salt}|${secret}`
  // 多轮混合，只是让直接读 localStorage 的人不能一眼看出密码，不当作真正的加密
  for (let round = 0; round < 4000; round += 1) {
    for (let i = 0; i < input.length; i += 1) {
      const c = input.charCodeAt(i) ^ round
      h1 = Math.imul(h1 ^ c, 0x01000193) >>> 0
      h2 = Math.imul(h2 + c, 0x85ebca6b) >>> 0
    }
  }
  return `fallback$${(h1 >>> 0).toString(16).padStart(8, '0')}${(h2 >>> 0).toString(16).padStart(8, '0')}`
}

async function hashPassword(password, salt, username) {
  const secret = `${username}::${password}`
  if (globalThis.crypto?.subtle && globalThis.TextEncoder) {
    try {
      const encoder = new TextEncoder()
      const key = await crypto.subtle.importKey('raw', encoder.encode(secret), 'PBKDF2', false, ['deriveBits'])
      const bits = await crypto.subtle.deriveBits(
        { name: 'PBKDF2', salt: encoder.encode(salt), iterations: PBKDF2_ITERATIONS, hash: 'SHA-256' },
        key,
        256
      )
      return `pbkdf2$${PBKDF2_ITERATIONS}$${toHex(bits)}`
    } catch {
      // 走到这里说明 WebCrypto 存在但用不了，退回退化算法
    }
  }
  return fallbackHash(secret, salt)
}

/**
 * 记录用「账号名」做键，而不是用户当时输入的登录名 ——
 * 手机号也能登录，同一个人用两种方式进来不该变成两条记录。
 */
function accountKey(user, loginId) {
  return normalizeUsername(user?.username || loginId)
}

/** 登录成功后调用：记住这个账号，供以后离线进入 */
export async function rememberAccount(loginId, password, token, user) {
  const key = accountKey(user, loginId)
  if (!key || !password || !user?.id) return
  const map = readAll()
  const salt = randomSalt()
  map[key] = {
    salt,
    verifier: await hashPassword(password, salt, key),
    // 当时用的是用户名还是手机号，离线登录时两种输入都认
    loginId: normalizeUsername(loginId),
    user,
    token: token || '',
    at: Date.now()
  }
  // 只留最近用过的几个，避免这台设备上的 localStorage 无限长大
  const entries = Object.entries(map).sort((a, b) => (b[1]?.at || 0) - (a[1]?.at || 0))
  writeAll(Object.fromEntries(entries.slice(0, MAX_ACCOUNTS)))
}

/** 按用户输入（账号名或手机号）找记录，返回 [键, 记录] */
function findRecord(input) {
  const id = normalizeUsername(input)
  if (!id) return [null, null]
  const map = readAll()
  if (map[id]) return [id, map[id]]
  const hit = Object.entries(map).find(([, record]) => normalizeUsername(record?.loginId) === id)
  return hit || [null, null]
}

/**
 * 离线登录校验。
 * 命中返回 { user, token }，否则返回 null —— 调用方据此给「密码不对」或「本机没有记录」的提示。
 */
export async function verifyAccount(input, password) {
  const [key, record] = findRecord(input)
  if (!key || !record?.salt || !record?.verifier || !record?.user) return null
  const verifier = await hashPassword(password, record.salt, key)
  if (verifier !== record.verifier) return null
  return { user: record.user, token: record.token || '' }
}

/** 本机有哪些账号可以离线进入（按最近使用排序） */
export function listAccounts() {
  return Object.entries(readAll())
    .map(([username, record]) => ({
      username,
      nickname: record?.user?.nickname || username,
      at: record?.at || 0
    }))
    .sort((a, b) => b.at - a.at)
}

export function hasAccount(input) {
  if (!input) return listAccounts().length > 0
  return !!findRecord(input)[1]
}

/** 注销 / 不想留痕时清掉某个账号的离线凭证 */
export function forgetAccount(username) {
  const name = normalizeUsername(username)
  const map = readAll()
  if (!name || !map[name]) return
  delete map[name]
  writeAll(map)
}
