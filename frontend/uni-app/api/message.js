import { mockResult, isEmpty } from '@/mock/config'
import { messages, notices, MESSAGE_TYPES } from '@/mock/message'

/** 消息类型 Tab */
export function getMessageTypes() {
  return mockResult(MESSAGE_TYPES)
}

/**
 * 消息列表（mock）
 *
 * 原型「消息」页整页是空态（插画 + 「暂无数据」），默认由 MOCK.empty.messages 控制。
 *
 * @param {Object} params { type }
 */
export function listMessages(params = {}) {
  if (isEmpty('messages')) {
    return mockResult([])
  }
  let list = messages.slice()
  if (params.type) {
    list = list.filter((item) => item.type === Number(params.type))
  }
  return mockResult(list)
}

/** 消息详情 */
export function getMessageDetail(messageId) {
  return mockResult(messages.find((item) => String(item.id) === String(messageId)) || null)
}

/** 公告列表（首页公告栏用；空态由 MOCK.empty.homeNotices 控制） */
export function listNotices() {
  return mockResult(isEmpty('homeNotices') ? [] : notices)
}
