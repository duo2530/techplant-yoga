import { mockResult, isEmpty } from '@/mock/config'
import { trialApplications, TRIAL_STATUS, TRIAL_FORM_OPTIONS } from '@/mock/trial'

/** 体验课申请状态 Tab（全部 / 申请待审核 / 审核通过 / 审核拒绝） */
export function getTrialStatusTabs() {
  return mockResult(TRIAL_STATUS)
}

export function trialStatusLabel(status) {
  const hit = TRIAL_STATUS.find((item) => item.value === Number(status))
  return hit ? hit.label : ''
}

/**
 * 我的体验课申请列表（mock）
 *
 * 原型「我的体验课」页是空态（「暂无数据」#AAAAAA），默认由 MOCK.empty.trials 控制。
 *
 * @param {Object} params { status }
 */
export function listTrialApplications(params = {}) {
  if (isEmpty('trials')) {
    return mockResult([])
  }
  let list = trialApplications.slice()
  if (params.status) {
    list = list.filter((item) => item.status === Number(params.status))
  }
  return mockResult(list.map((item) => Object.assign({}, item, { statusLabel: trialStatusLabel(item.status) })))
}

/** 申请体验表单的可选项 */
export function getTrialFormOptions() {
  return mockResult(TRIAL_FORM_OPTIONS)
}

/**
 * 提交体验课申请（mock）：只返回成功，不落库
 *
 * 必填口径照原型截图：只有「选择门店」「手机号码」带必填星，`姓名` / `留言` 没有，
 * 所以这里只强校验手机号（原先还强校验 contactName，会把留空姓名的正常提交拦掉）。
 */
export function submitTrialApply(form) {
  if (!form || !form.phone) {
    return mockResult({ success: false, message: '请填写手机号' })
  }
  return mockResult({ success: true, message: '申请已提交，请等待门店审核', applyId: 2003 })
}
