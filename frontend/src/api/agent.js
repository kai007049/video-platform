import axios from 'axios'
import { useUserStore } from '../stores/user'

const agentRequest = axios.create({
  baseURL: '/agent-api',
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' }
})

agentRequest.interceptors.request.use(config => {
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = `Bearer ${store.token}`
  }
  return config
})

export const createAskTask = (payload) => agentRequest.post('/tasks/ask', payload)
export const createUploadAssistTask = (payload) => agentRequest.post('/tasks/upload-assist', payload)
export const createMessageDraftTask = (payload) => agentRequest.post('/tasks/message-draft', payload)
export const getAgentTask = (taskId) => agentRequest.get(`/tasks/${taskId}`)

export async function pollAgentTask(taskId, options = {}) {
  const { maxAttempts = 15, intervalMs = 800 } = options
  for (let i = 0; i < maxAttempts; i++) {
    const { data } = await getAgentTask(taskId)
    if (data?.status === 'success' || data?.status === 'failed' || data?.status === 'not_found') {
      return data
    }
    await new Promise(resolve => setTimeout(resolve, intervalMs))
  }
  throw new Error('AI 任务执行超时，请稍后再试')
}
