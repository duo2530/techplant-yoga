import { getToken } from '@/utils/auth'

/**
 * 页面跳转守卫
 *
 * 当前阶段是「原型还原 + mock 数据」：**没有真实登录态**（登录页也是 mock 的），
 * 因此这里不做登录拦截，任何页面都可以直接访问，方便在 HBuilderX / 微信开发者工具里
 * 逐页对照原型验收。
 *
 * 接入真实后端后要恢复登录拦截：把 `MOCK_MODE` 改成 false，
 * 并确认 token 由 `@/utils/auth` 写入（登录页到时改走真实接口）。
 */
const MOCK_MODE = true

// 登录页面
const loginPage = '/pages/auth/quick-login'

// 免登录白名单（登录、注册、协议、公共页）
const whiteList = [
  '/pages/auth/quick-login',
  '/pages/auth/phone-auth',
  '/pages/auth/forgot-password',
  '/pages/terms/index',
  '/pages/privacy/index',
  '/pages/common/webview/index',
  '/pages/common/textview/index'
]

// 检查地址白名单
function checkWhite(url) {
  const path = (url || '').split('?')[0]
  return whiteList.indexOf(path) !== -1
}

// 页面跳转验证拦截器
const list = ['navigateTo', 'redirectTo', 'reLaunch', 'switchTab']
list.forEach((item) => {
  uni.addInterceptor(item, {
    invoke(to) {
      // mock 阶段：不校验登录态
      if (MOCK_MODE) {
        return true
      }
      if (getToken()) {
        if (to.url === loginPage) {
          uni.reLaunch({ url: '/' })
        }
        return true
      }
      if (checkWhite(to.url)) {
        return true
      }
      uni.reLaunch({ url: loginPage })
      return false
    },
    fail(err) {
      console.log(err)
    }
  })
})
