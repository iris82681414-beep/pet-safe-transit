import { request } from '@/utils/request'

export async function sendVoiceCommand(audioBlob: Blob, context: Record<string, any> = {}) {
  const formData = new FormData()
  formData.append('audio', audioBlob, 'voice.wav')
  formData.append('sourcePage', context.sourcePage || '')
  formData.append('selectedEntityId', context.selectedEntityId || '')
  formData.append('selectedEntityType', context.selectedEntityType || '')
  return request.upload<Record<string, any>>('/voice/command', formData)
}
