<script setup>
  import config from './config'
  import { useConfigStore } from '@/store'
  import { onLaunch } from '@dcloudio/uni-app'

  onLaunch(() => {
    initApp()
  })

  // 初始化应用
  function initApp() {
    // 初始化应用配置（品牌名、logo、协议链接）
    initConfig()
    // 注意：当前阶段是「原型还原 + mock 数据」，没有真实登录态，
    // 因此这里不做登录检查（也不跳转登录页），首页即启动页。
    // 接入真实后端后，需要恢复基于 token 的登录检查（见 permission.js 里的 MOCK_MODE 说明）。
  }

  function initConfig() {
    useConfigStore().setConfig(config)
  }
</script>

<style lang="scss">
  @import '@/static/scss/index.scss'
</style>
