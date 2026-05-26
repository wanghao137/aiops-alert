import { http } from './http'

/**
 * 演示用：让某对象某指标立即进入异常状态，约 30-60 秒后规则引擎会触发告警。
 */
export function forceStory(objectId: number, metricCode: string) {
  return http.post<string>('/simulator/force-story', null, {
    params: { objectId, metricCode }
  })
}
