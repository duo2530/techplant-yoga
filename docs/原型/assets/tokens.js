/* 设计 token —— 色值来自截图真实像素采样，不是估计值 */
tailwind.config = {
  theme: {
    extend: {
      colors: {
        brand: { DEFAULT: '#609CE9', light: '#87C7F6', soft: '#EAF4F6', pale: '#E6F0FA', deep: '#4A8BD8' },
        page: '#F7F9FC',
        ink: { 1: '#333333', 2: '#666666', 3: '#999999', 4: '#BBBBBB' },
        line: '#EDEFF2',
        wechat: '#07C160'
      },
      fontFamily: {
        sans: ['-apple-system', 'BlinkMacSystemFont', 'PingFang SC', 'Helvetica Neue', 'Microsoft YaHei', 'sans-serif']
      }
    }
  }
}

/* 截图模式：headless 渲染校验用。加 ?shot=1 后手机壳贴左上角，保证 1:1 无偏移。 */
if (location.search.indexOf('shot') > -1) {
  document.documentElement.classList.add('shot')
}
