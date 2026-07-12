import type { AuthTokens, UserProfile } from './types'

const KEYS = {
  accessToken: 'smart-logistics-access-token',
  refreshToken: 'smart-logistics-refresh-token',
  expiresAt: 'smart-logistics-token-expires-at',
  user: 'smart-logistics-api-user',
} as const

export interface AuthSession {
  accessToken: string
  refreshToken: string
  expiresAt: number
  user?: UserProfile
}

function emitSessionChanged() {
  window.dispatchEvent(new CustomEvent('smart-logistics:session-changed'))
}

export const tokenManager = {
  getAccessToken(): string | null {
    return localStorage.getItem(KEYS.accessToken) || sessionStorage.getItem(KEYS.accessToken)
  },

  getRefreshToken(): string | null {
    return localStorage.getItem(KEYS.refreshToken) || sessionStorage.getItem(KEYS.refreshToken)
  },

  getUser(): UserProfile | null {
    try {
      return JSON.parse(localStorage.getItem(KEYS.user) || sessionStorage.getItem(KEYS.user) || 'null')
    } catch {
      return null
    }
  },

  getSession(): AuthSession | null {
    const accessToken = this.getAccessToken()
    const refreshToken = this.getRefreshToken()
    const expiresAt = Number(localStorage.getItem(KEYS.expiresAt) || sessionStorage.getItem(KEYS.expiresAt) || 0)
    if (!accessToken || !refreshToken) return null
    return { accessToken, refreshToken, expiresAt, user: this.getUser() || undefined }
  },

  save(tokens: AuthTokens, user?: UserProfile) {
    const expiresAt = Date.now() + Math.max(0, tokens.expiresIn) * 1_000
    localStorage.setItem(KEYS.accessToken, tokens.accessToken)
    localStorage.setItem(KEYS.refreshToken, tokens.refreshToken)
    localStorage.setItem(KEYS.expiresAt, String(expiresAt))
    if (user) localStorage.setItem(KEYS.user, JSON.stringify(user))
    Object.values(KEYS).forEach((key) => sessionStorage.removeItem(key))
    emitSessionChanged()
  },

  updateTokens(tokens: AuthTokens) {
    this.save(tokens, this.getUser() || undefined)
  },

  updateUser(user: UserProfile) {
    if (localStorage.getItem(KEYS.accessToken)) {
      localStorage.setItem(KEYS.user, JSON.stringify(user))
    } else if (sessionStorage.getItem(KEYS.accessToken)) {
      sessionStorage.setItem(KEYS.user, JSON.stringify(user))
    }
    emitSessionChanged()
  },

  isExpiring(leewaySeconds = 30): boolean {
    const expiresAt = Number(localStorage.getItem(KEYS.expiresAt) || sessionStorage.getItem(KEYS.expiresAt) || 0)
    return Boolean(expiresAt && expiresAt <= Date.now() + leewaySeconds * 1_000)
  },

  clear() {
    Object.values(KEYS).forEach((key) => {
      localStorage.removeItem(key)
      sessionStorage.removeItem(key)
    })
    emitSessionChanged()
  },
}
