import { TOKEN_KEY } from '@/constants'

/**
 * 统一请求封装：后端返回 { code, message, data }，code === 0 视为成功。
 * 携带 JWT；401 时清空会话并通过回调通知上层弹出登录框。
 */

const API_BASE = import.meta.env.VITE_API_BASE ?? ''

export class ApiRequestError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
  }
}

interface ApiEnvelope<T> {
  code?: number
  message?: string
  data?: T
}

function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {})
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')

  const response = await fetch(`${API_BASE}${path}`, { ...options, headers })
  const payload = (await response.json().catch(() => null)) as ApiEnvelope<T> | null

  if (!response.ok || !payload || payload.code !== 0) {
    const message = payload?.message || `请求失败（HTTP ${response.status}）`
    if (response.status === 401) unauthorizedHandler?.()
    throw new ApiRequestError(message, response.status)
  }

  return payload.data as T
}
