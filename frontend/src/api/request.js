import axios from 'axios'
import { useUserStore } from '../stores/user'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

request.interceptors.request.use(config => {
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = `Bearer ${store.token}`
  }
  // FormData 上传时由浏览器自动设置 Content-Type（含 boundary），否则 multipart 会失败
  if (config.data instanceof FormData) {
    delete config.headers['Content-Type']
  }
  return config
})

request.interceptors.response.use(
  res => {
    const { code, data, message } = res.data
    if (code === 200) return data
    if (code === 401 || code === 403) {
      const store = useUserStore()
      store.logout()
    }
    return Promise.reject(new Error(message || '请求失败'))
  },
  err => {
    const msg = err.response?.data?.message || err.message || '网络错误'
    if (err.response?.status === 401 || err.response?.status === 403) {
      const store = useUserStore()
      store.logout()
    }
    return Promise.reject(new Error(msg))
  }
)

export default request
