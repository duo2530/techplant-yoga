/**
 * 页面跳转守卫
 *
 * ⚠️ 一期用户端**免登录**（BR-全局-002 / BR-全局-005）：
 * 用户端只有 3 个接口（`GET /api/stores`、`GET /api/schedules`、`GET /api/schedules/{scheduleId}`），
 * 全部标注 `@Anonymous`、匿名只读、不认身份，也就**没有任何登录态可校验**。
 *
 * 因此这里**不做任何登录拦截**：
 *   - 旧原型里「换店要先登录」「进详情要先登录」之类的拦截已按一期范围删除；
 *   - 之前用 `MOCK_MODE` 临时放行的写法也一并去掉，免登录是**一期口径**而不是临时开关。
 *
 * 保留拦截器挂载点只是为了将来恢复登录时集中改这一个文件（页面里不要各自实现登录判断）。
 * 按 AGENTS.md §8：这里只做初始化与守卫，不写业务逻辑。
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
