import { Mail, MessageSquare, Send } from 'lucide-vue-next'
import type { Component } from 'vue'

export interface ChannelTypeMeta {
  value: string
  label: string
  short: string
  gradient: string
  color: string
  icon: Component
  /** 配置 JSON 占位符示例 */
  configPlaceholder: string
  /** 简短的配置说明 */
  configHint: string
}

export const CHANNEL_TYPES: ChannelTypeMeta[] = [
  {
    value: 'WECOM',
    label: '企业微信',
    short: '企微',
    gradient: 'linear-gradient(135deg, rgba(16,185,129,0.25), rgba(16,185,129,0.05))',
    color: '#10B981',
    icon: Send,
    configPlaceholder: `{
  "webhook": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx",
  "mentionedMobileList": "13800000001,13800000002"
}`,
    configHint: '群机器人 webhook + 可选的 @ 手机号列表'
  },
  {
    value: 'EMAIL',
    label: '邮件',
    short: '邮件',
    gradient: 'linear-gradient(135deg, rgba(59,130,246,0.25), rgba(59,130,246,0.05))',
    color: '#3B82F6',
    icon: Mail,
    configPlaceholder: `{
  "host": "smtp.exmail.qq.com",
  "port": 465,
  "ssl": true,
  "username": "alert@example.com",
  "password": "********",
  "from": "alert@example.com",
  "fromName": "AIOps Alert",
  "defaultReceivers": "ops@example.com"
}`,
    configHint: 'SMTP 主机、账号密码、默认收件人列表'
  },
  {
    value: 'SMS',
    label: '短信',
    short: '短信',
    gradient: 'linear-gradient(135deg, rgba(245,158,11,0.25), rgba(245,158,11,0.05))',
    color: '#F59E0B',
    icon: MessageSquare,
    configPlaceholder: `{
  "provider": "aliyun",
  "accessKey": "xxx",
  "secretKey": "xxx",
  "sign": "AIOps",
  "template": "SMS_xxx",
  "dryRun": true,
  "defaultReceivers": "13800000001"
}`,
    configHint: '服务商账号 / 模板编码 / 演示用 dryRun 模式'
  }
]

export function getChannelTypeMeta(type?: string): ChannelTypeMeta {
  return CHANNEL_TYPES.find((t) => t.value === type) || CHANNEL_TYPES[0]
}
