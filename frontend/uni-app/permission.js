/**
 * 页面跳转守卫
 *
 * ⚠️ 一期用户端**免登录**（BR-全局-002 / BR-全局-005）：三个浏览接口
 * （`GET /api/stores`、`GET /api/schedules`、`GET /api/schedules/{scheduleId}`）
 * 全部标注 `@Anonymous`、匿名只读、不认身份。
 *
 * 二期已交付**会员登录**（`POST /api/auth/login` 签发会员 token，见用户端接口详细设计
 * §2.2.6），但**这里仍然不做登录拦截**：除登录/注册/忘记密码外的页面数据还是本地 mock，
 * 打开即拦会把逐页对照原型的验收流程整个堵死。登录态本身已经可用
 * （`@/utils/auth` 的 token，`@/utils/request` 统一处理 401）。
 *
 * 将来这些页面都接上真实后端时，在这个文件里加白名单与跳转即可——
 * 页面里不要各自实现登录判断。按 AGENTS.md §8：这里只做初始化与守卫，不写业务逻辑。
 */
const list = ['navigateTo', 'redirectTo', 'reLaunch', 'switchTab']

list.forEach((item) => {
  uni.addInterceptor(item, {
    invoke() {
      // 一期用户端免登录：一律放行
      return true
    },
    fail(err) {
      console.log(err)
    }
  })
})
