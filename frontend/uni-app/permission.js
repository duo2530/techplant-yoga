/** 页面导航拦截器，当前允许所有页面跳转。 */
const list = ['navigateTo', 'redirectTo', 'reLaunch', 'switchTab']

list.forEach((item) => {
  uni.addInterceptor(item, {
    invoke() {
      return true
    },
    fail(err) {
      console.log(err)
    }
  })
})
