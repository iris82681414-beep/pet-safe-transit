import { executeAgentAction } from '@/utils/actionExecutor'
import { sendVoiceCommand } from '@/utils/voiceCommand'
import { resolveVoiceFallbackAction } from '@/utils/voiceFallback'

export interface VoiceAgentContext {
  sourcePage?: string
  selectedEntityId?: string
  selectedEntityType?: string
  [key: string]: unknown
}

export interface VoiceAgentResult extends Record<string, any> {
  recognizedText: string
  reply: string
  action: Record<string, any> | null
  result?: unknown
}

export interface VoiceAgentOptions {
  onExecuting?: () => void
}

/**
 * 所有语音入口共用的完整 Agent 链路：录音上传 -> 后端 ASR -> LLM Agent
 * -> 本地动作兜底 -> 权限确认与真实业务动作执行。
 */
export async function runVoiceAgent(
  audio: Blob,
  context: VoiceAgentContext = {},
  options: VoiceAgentOptions = {},
): Promise<VoiceAgentResult> {
  const payload = await sendVoiceCommand(audio, context)
  const recognizedText = String(payload.recognizedText || payload.text || '').trim()
  const serverAction = payload.action && payload.action.type !== 'NOOP' ? payload.action : null
  const action = serverAction || resolveVoiceFallbackAction(payload)
  const reply = String(action?.reply || payload.reply || payload.message || '').trim()

  if (payload.intent === 'ERROR') throw new Error(reply || '语音识别失败')

  if (action) options.onExecuting?.()
  const result = action
    ? await executeAgentAction({
        ...action,
        recognizedText,
        reply,
        logId: payload.logId,
        needConfirm: payload.needConfirm,
      })
    : undefined

  return { ...payload, recognizedText, reply, action, result }
}
