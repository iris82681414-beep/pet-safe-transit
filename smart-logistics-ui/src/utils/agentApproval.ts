export type AgentApprovalMode = 'request' | 'direct'

const storageKey = 'smart-logistics-agent-approval-mode'

export function getAgentApprovalMode(): AgentApprovalMode {
  return localStorage.getItem(storageKey) === 'direct' ? 'direct' : 'request'
}

export function setAgentApprovalMode(mode: AgentApprovalMode) {
  localStorage.setItem(storageKey, mode)
  window.dispatchEvent(new CustomEvent('smart-logistics:agent-approval-mode', { detail: { mode } }))
}

export function shouldRequestAgentApproval(action?: Record<string, any>) {
  const requested = action?.approvalMode || action?.permissionMode
  if (requested === 'direct' || requested === 'DIRECT') return false
  if (requested === 'request' || requested === 'REQUEST') return true
  return getAgentApprovalMode() === 'request'
}
