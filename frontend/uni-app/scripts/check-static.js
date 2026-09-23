/**
 * uni-app（HBuilderX 工程）静态自检 —— 不装 npm 包，复用 HBuilderX 自带编译器依赖。
 *
 * 用法（PowerShell）：
 *   $env:NODE_PATH='D:\Develop\HBuilderX\plugins\uniapp-cli-vite\node_modules;D:\Develop\HBuilderX\plugins\compile-dart-sass\node_modules'
 *   node frontend\uni-app\scripts\check-static.js <uni-app 工程绝对路径>
 *
 * 检查项清单见 frontend/uni-app/AGENTS.md 第 9 节第 3 步。
 */
const fs = require('fs')
const path = require('path')

const ROOT = process.argv[2]
if (!ROOT) throw new Error('usage: node frontend/uni-app/scripts/check-static.js <uni-app project dir>')

const { parse, compileTemplate, compileScript } = require('@vue/compiler-sfc')
const sass = require('sass')

const errors = []
const warns = []
const E = (f, m) => errors.push(`${f}: ${m}`)
const W = (f, m) => warns.push(`${f}: ${m}`)

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name)
    const st = fs.statSync(p)
    if (st.isDirectory()) {
      if (name === 'node_modules' || name === 'unpackage' || name === 'dist') continue
      walk(p, out)
    } else out.push(p)
  }
  return out
}

const rel = (p) => path.relative(ROOT, p).replace(/\\/g, '/')

// ---------- 1. 收集 .vue ----------
const vueFiles = [
  ...walk(path.join(ROOT, 'pages')),
  ...walk(path.join(ROOT, 'components'))
].filter((f) => f.endsWith('.vue'))

const UNI_SCSS = fs.readFileSync(path.join(ROOT, 'uni.scss'), 'utf8')

const JS_GLOBALS = new Set([
  'true', 'false', 'null', 'undefined', 'new', 'typeof', 'in', 'instanceof', 'void', 'delete',
  'return', 'if', 'else', 'this', 'Math', 'String', 'Number', 'Boolean', 'Array', 'Object', 'JSON',
  'Date', 'parseInt', 'parseFloat', 'isNaN', 'isFinite', 'uni', 'wx', 'NaN', 'Infinity',
  'encodeURIComponent', 'decodeURIComponent', 'console', 'item', 'index'
])

const userComponents = vueFiles.map((f) => path.basename(f, '.vue'))

for (const file of vueFiles) {
  const src = fs.readFileSync(file, 'utf8')
  const r = rel(file)
  const { descriptor, errors: perr } = parse(src, { filename: file })
  perr.forEach((e) => E(r, `[parse] ${e.message}`))

  const tpl = descriptor.template && descriptor.template.content
  if (!tpl) { W(r, 'no <template>'); continue }

  // 1a. 模板语法
  const ct = compileTemplate({
    source: tpl,
    filename: file,
    id: r,
    compilerOptions: { bindingMetadata: {} }
  })
  ct.errors.forEach((e) => E(r, `[template] ${e.message || e}`))

  // 1b. 脚本语法
  let bindings = {}
  try {
    const cs = compileScript(descriptor, { id: r })
    bindings = cs.bindings || {}
  } catch (e) {
    E(r, `[script] ${e.message}`)
  }

  // 1c. text 节点里的 `>` / `<` / HTML 实体
  //     uni-app 编译时会把模板里的字面 `>` 转义成 `&gt;` 写进 WXML，
  //     而微信 <text> 默认不解码实体（会原样显示成「&gt;」），所以必须加 decode 属性（或改成绑定）。
  //     先去掉注释，否则注释里的示例代码会误报。
  const tplNoComment = tpl.replace(/<!--[\s\S]*?-->/g, '')
  for (const m of tplNoComment.matchAll(/<text\b([^>]*)>([\s\S]*?)<\/text>/g)) {
    const [, attrs, inner] = m
    if (/\bdecode\b/.test(attrs)) continue
    const literal = inner.replace(/\{\{[\s\S]*?\}\}/g, '') // 绑定里的内容不受转义影响
    if (/[<>]|&(gt|lt|amp|nbsp|quot|#\d+);/.test(literal)) {
      E(r, `text 节点里有会被转义/不解码的字符，需要加 decode 或改成绑定：${literal.trim().slice(0, 40)}`)
    }
  }
  const ents = tplNoComment.match(/&(gt|lt|amp|nbsp|quot|#\d+);/g)
  if (ents) E(r, `模板里有 HTML 实体：${[...new Set(ents)].join(' ')}（WXML 不解码，必须配 decode 或改成绑定）`)

  // 1d. 模板里引用但未声明的标识符（script setup 无自动导入）
  const declared = new Set(Object.keys(bindings))
  // v-for / v-slot 里声明的局部名
  for (const m of tpl.matchAll(/v-for\s*=\s*"([^"]*)"/g)) {
    const left = m[1].split(/\s+in\s+|\s+of\s+/)[0]
    left.replace(/[()]/g, ' ').split(/[\s,]+/).filter(Boolean).forEach((n) => declared.add(n.trim()))
  }
  for (const m of tpl.matchAll(/(?:v-slot|#\w+)\s*=\s*"([^"]*)"/g)) {
    m[1].replace(/[{}]/g, ' ').split(/[\s,]+/).filter(Boolean).forEach((n) => declared.add(n.trim()))
  }
  const exprs = []
  exprs.push(...[...tpl.matchAll(/\{\{([\s\S]*?)\}\}/g)].map((m) => m[1]))
  exprs.push(...[...tpl.matchAll(/\s(?::|v-bind:|@|v-on:|v-if|v-else-if|v-show|v-model|v-for|v-html|v-text)[\w:.-]*\s*=\s*"([^"]*)"/g)].map((m) => m[1]))
  const used = new Set()
  for (let e of exprs) {
    e = e.replace(/'(?:[^'\\]|\\.)*'/g, "''").replace(/"(?:[^"\\]|\\.)*"/g, '""').replace(/`(?:[^`\\]|\\.)*`/g, '``')
    e = e.replace(/\?\.\s*[A-Za-z_$][\w$]*/g, '') // 可选链属性访问
    e = e.replace(/\.[A-Za-z_$][\w$]*/g, '') // 属性访问只保留根标识符
    e = e.replace(/([{,]\s*)[A-Za-z_$][\w$]*\s*:/g, '$1') // 去掉对象字面量的 key
    for (const m of e.matchAll(/[A-Za-z_$][\w$]*/g)) used.add(m[0])
  }
  for (const n of used) {
    if (JS_GLOBALS.has(n) || declared.has(n)) continue
    if (n.startsWith('$')) continue
    if (/^[A-Z]/.test(n)) continue
    if (userComponents.includes(n)) continue
    E(r, `模板引用了未声明的标识符「${n}」（script setup 无自动导入，页面里会渲染成空）`)
  }

  // 1e. 样式：scss 变量 + WXSS 非法选择器
  for (const st of descriptor.styles) {
    if (!st.content.trim()) continue
    const pre = UNI_SCSS + '\n' + st.content
    try {
      // HBuilderX 自带的 dart-sass 1.43 只有 legacy API
      const css = sass.renderSync({ data: pre, outputStyle: 'expanded' }).css.toString()
      if (/\*/.test(css.replace(/\/\*[\s\S]*?\*\//g, ''))) {
        const bad = css.split('\n').filter((l) => /\*/.test(l) && !/^\s*\*/.test(l))
        E(r, `WXSS 不支持通配选择器 *：${bad.slice(0, 3).join(' | ')}`)
      }
    } catch (e) {
      E(r, `[scss] ${e.message.split('\n')[0]}`)
    }
  }

  // 1f. 页面里不该直连请求 / 直接读 mock
  if (r.startsWith('pages/')) {
    const noComment = src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')
    if (/uni\.request\s*\(/.test(noComment)) E(r, '页面里直接用了 uni.request')
    if (/@\/utils\/request/.test(noComment)) E(r, '页面里直接引了 @/utils/request')
    if (/from\s+['"]@\/mock/.test(noComment)) E(r, '页面里直接引了 @/mock（应只依赖 @/api）')
  }
}

// ---------- 2. 静态资源引用 ----------
const staticRoot = path.join(ROOT, 'static')
const scanDirs = ['pages', 'components', 'mock', 'api', 'store', 'utils']
const scanFiles = [path.join(ROOT, 'pages.json'), path.join(ROOT, 'config.js'), path.join(ROOT, 'App.vue')]
const assetRefs = new Map()
for (const d of scanDirs) {
  const full = path.join(ROOT, d)
  if (!fs.existsSync(full)) continue
  for (const f of walk(full)) {
    if (!/\.(vue|js|json)$/.test(f)) continue
    scanFiles.push(f)
  }
}
for (const f of scanFiles) {
  if (!fs.existsSync(f)) continue
  const src = fs.readFileSync(f, 'utf8')
  for (const m of src.matchAll(/['"`](\/static\/[^'"`\s)]+)['"`]/g)) {
    const p = m[1]
    if (!assetRefs.has(p)) assetRefs.set(p, [])
    assetRefs.get(p).push(rel(f))
  }
}
for (const [p, refs] of assetRefs) {
  if (!fs.existsSync(path.join(ROOT, p.slice(1)))) E(refs[0], `静态资源不存在：${p}`)
}

// ---------- 3. pages.json：页面 / tabBar / 图标 ----------
const pagesJson = JSON.parse(fs.readFileSync(path.join(ROOT, 'pages.json'), 'utf8'))
const registered = new Set()
for (const p of pagesJson.pages) {
  registered.add(p.path)
  const f = path.join(ROOT, p.path + '.vue')
  if (!fs.existsSync(f)) E('pages.json', `登记的页面缺文件：${p.path}.vue`)
}
const tabBarPaths = new Set((pagesJson.tabBar && pagesJson.tabBar.list || []).map((t) => t.pagePath))
for (const t of (pagesJson.tabBar && pagesJson.tabBar.list) || []) {
  for (const k of ['iconPath', 'selectedIconPath']) {
    if (t[k] && !fs.existsSync(path.join(ROOT, t[k]))) E('pages.json', `tabBar 图标缺失：${t[k]}`)
  }
}

// ---------- 4. 跳转目标 ----------
for (const f of vueFiles) {
  const r = rel(f)
  const src = fs.readFileSync(f, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '').replace(/<!--[\s\S]*?-->/g, '')
  for (const m of src.matchAll(/(navigateTo|redirectTo|reLaunch|switchTab)\s*\(\s*\{[^}]*url\s*:\s*['"`]([^'"`?]+)/g)) {
    const [, api, url] = m
    const target = url.replace(/^\//, '')
    if (!registered.has(target)) { E(r, `跳转目标未登记：${url}`); continue }
    if (tabBarPaths.has(target) && api !== 'switchTab') E(r, `${target} 是 tabBar 页，必须用 switchTab（当前 ${api}）`)
    if (!tabBarPaths.has(target) && api === 'switchTab') E(r, `${target} 不是 tabBar 页，不能用 switchTab`)
  }
}

// ---------- 5. uni-icons type 有效性 ----------
const iconFile = walk(path.join(ROOT, 'uni_modules', 'uni-icons')).find((f) => /uniicons_file_vue\.js$/.test(f))
if (iconFile) {
  const iconSrc = fs.readFileSync(iconFile, 'utf8')
  const valid = new Set([...iconSrc.matchAll(/["']font_class["']\s*:\s*["']([^"']+)["']/g)].map((m) => m[1]))
  for (const f of vueFiles) {
    const src = fs.readFileSync(f, 'utf8')
    for (const m of src.matchAll(/<uni-icons[^>]*\stype\s*=\s*["']([^"']+)["']/g)) {
      if (!valid.has(m[1])) E(rel(f), `uni-icons type 不存在：${m[1]}`)
    }
  }
} else W('uni_modules', '未找到 uni-icons 图标表，跳过校验')

// ---------- 6. mock 数据一致性 ----------
const mockDir = path.join(ROOT, 'mock')
if (fs.existsSync(mockDir)) {
  const load = (n) => {
    const f = path.join(mockDir, n + '.js')
    return fs.existsSync(f) ? fs.readFileSync(f, 'utf8') : ''
  }
  const ids = (src, key) => new Set([...src.matchAll(new RegExp(`\\b${key}\\s*:\\s*['"]?(\\d{15,})['"]?`, 'g'))].map((m) => m[1]))
  const courseSrc = load('course'), coachSrc = load('coach'), storeSrc = load('store'), bookingSrc = load('booking')
  const courseIds = ids(courseSrc, 'id'), coachIds = ids(coachSrc, 'id'), storeIds = ids(storeSrc, 'id')
  for (const [name, src, field, pool] of [
    ['course', courseSrc, 'coachId', coachIds],
    ['booking', bookingSrc, 'courseId', courseIds],
    ['booking', bookingSrc, 'coachId', coachIds],
    ['booking', bookingSrc, 'storeId', storeIds]
  ]) {
    for (const m of src.matchAll(new RegExp(`\\b${field}\\s*:\\s*['"]?(\\d{15,})['"]?`, 'g'))) {
      if (!pool.has(m[1])) E(`mock/${name}.js`, `${field} 解析不到实体：${m[1]}`)
    }
  }
  // 雪花 ID 必须是字符串字面量
  for (const f of walk(mockDir)) {
    const src = fs.readFileSync(f, 'utf8')
    for (const m of src.matchAll(/\bid\s*:\s*(\d{15,})\b/g)) {
      E(rel(f), `雪花 ID 必须写成字符串，否则超出 JS 安全整数会塌缩：${m[1]}`)
    }
  }
}

// ---------- 输出 ----------
console.log(`检查 ${vueFiles.length} 个 .vue / ${registered.size} 个登记页面 / ${assetRefs.size} 个静态资源引用`)
if (warns.length) { console.log('\n--- WARN ---'); warns.forEach((w) => console.log('  ' + w)) }
if (errors.length) {
  console.log('\n--- ERROR ---')
  errors.forEach((e) => console.log('  ' + e))
  console.log(`\n共 ${errors.length} 个错误`)
  process.exit(1)
}
console.log('\n全部通过')
