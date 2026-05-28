import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useHttpHealth } from '@/composables/useHttpHealth'
import { API_BASE_URL } from './base'

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

const httpHealth = useHttpHealth()

const instance: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000
})

// AI 路径默认拉长到 3 分钟（推理类模型生成耗时较长）
const LONG_PATHS = [/^\/ai\//, /^\/llm-configs\/.+\/test$/]

instance.interceptors.request.use((config) => {
  const url = config.url || ''
  if (config.timeout === instance.defaults.timeout && LONG_PATHS.some((re) => re.test(url))) {
    config.timeout = 180000
  }
  return config
})

instance.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult<unknown>
    if (body && typeof body.code === 'number' && body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return response
  },
  (error) => {
    const status = error?.response?.status
    if (typeof status === 'number' && status >= 500) {
      httpHealth.reportFailure()
    }
    let msg = error?.response?.data?.message || error?.message || '网络异常'
    if (error?.code === 'ECONNABORTED' || /timeout/i.test(msg)) {
      msg = 'AI 调用超时，模型可能在思考较长内容，可稍后再试或调小请求'
    }
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

async function unwrap<T>(promise: Promise<{ data: ApiResult<T> }>): Promise<T> {
  const r = await promise
  return r.data.data
}

export const http = {
  get<T>(url: string, config?: AxiosRequestConfig) {
    return unwrap<T>(instance.get(url, config))
  },
  post<T>(url: string, body?: unknown, config?: AxiosRequestConfig) {
    return unwrap<T>(instance.post(url, body, config))
  },
  put<T>(url: string, body?: unknown, config?: AxiosRequestConfig) {
    return unwrap<T>(instance.put(url, body, config))
  },
  delete<T>(url: string, config?: AxiosRequestConfig) {
    return unwrap<T>(instance.delete(url, config))
  }
}
