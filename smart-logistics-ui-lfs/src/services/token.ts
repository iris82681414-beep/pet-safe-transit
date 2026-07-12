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
    return sessionStorage.getItem(KEYS.accessToken)
  },

  getRefreshToken(): string | null {
    return sessionStorage.getItem(KEYS.refreshToken)
  },

  getUser(): UserProfile | null {
    try {
      return JSON.parse(sessionStorage.getItem(KEYS.user) || 'null')
    } catch {
      return null
    }
  },

  getSession(): AuthSession | null {
    const accessToken = this.getAccessToken()
    const refreshToken = this.getRefreshToken()
    const expiresAt = Number(sessionStorage.getItem(KEYS.expiresAt) || 0)
    if (!accessToken || !refreshToken) return null
    return { accessToken, refreshToken, expiresAt, user: this.getUser() || undefined }
  },

  save(tokens: AuthTokens, user?: UserProfile) {
    const expiresAt = Date.now() + Math.max(0, tokens.expiresIn) * 1_000
    sessionStorage.setItem(KEYS.accessToken, tokens.accessToken)
    sessionStorage.setItem(KEYS.refreshToken, tokens.refreshToken)
    sessionStorage.setItem(KEYS.expiresAt, String(expiresAt))
    if (user) sessionStorage.setItem(KEYS.user, JSON.stringify(user))
    emitSessionChanged()
  },

  updateTokens(tokens: AuthTokens) {
    this.save(tokens, this.getUser() || undefined)
  },

  isExpiring(leewaySeconds = 30): boolean {
    const expiresAt = Number(sessionStorage.getItem(KEYS.expiresAt) || 0)
    return Boolean(expiresAt && expiresAt <= Date.now() + leewaySeconds * 1_000)
  },

  clear() {
    Object.values(KEYS).forEach((key) => sessionStorage.removeItem(key))
    emitSessionChanged()
  },
}
