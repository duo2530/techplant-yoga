// 应用全局配置
export default {
  /**
   * 后端地址
   *
   * 当前阶段（原型还原 + mock 数据）**页面不调用后端接口**：
   * 所有数据来自 `mock/` 目录，页面只依赖 `@/api/*` 这层假接口。
   * 将来接真实后端时，把 `@/api/*` 换成走 `@/utils/request` 的真实请求即可，
   * 那时这里的 baseUrl 就是接口地址（真机/发布要换成已备案的 https 域名）。
   */
  baseUrl: 'http://localhost:8080',

  // 应用信息
  appInfo: {
    // 应用名称（原型导航栏与协议页用的品牌名）
    name: '一水·瑜伽普拉提',
    // 应用版本
    version: '1.0.0',
    // 应用logo（原型导航栏左侧的书法「一」，从截图裁出）
    logo: '/static/images/brand/nav-logo.png',
    // 官方网站（暂无）
    site_url: '',
    // 政策协议（本地协议页）
    agreements: [
      {
        title: '隐私政策',
        url: '/pages/privacy/index'
      },
      {
        title: '用户服务协议',
        url: '/pages/terms/index'
      }
    ]
  }
}
