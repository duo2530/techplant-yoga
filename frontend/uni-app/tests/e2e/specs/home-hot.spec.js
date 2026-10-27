const { assert, SkipError } = require('../lib/assert')
const { createAdminFixture } = require('../lib/admin')
const { requestsMatching, waitUntil } = require('../lib/coach-app')

/**
 * 首页「热门课程」栏目端到端用例。
 *
 * 排课通过真实管理端接口准备，预约量通过种子会员的真实预约接口制造（一次预约占用多个人数，
 * 用较少的次数卡次数拉开排名）；页面展示与公开接口 GET /api/schedules/hot 对照。
 * 用例结束先取消预约（返还次数）再删除排课；当前门店已有热门课程、会员没有足够次数的团课次卡时跳过。
 */

const HOT_API = '/api/schedules/hot'

/** 用例排课占用的人数合计：5 + 4 + 3 + 2 + 2 + 1 之外的备用量 */
const REQUIRED_TIMES = 12

/** 栏目终态：有卡片，或空态／失败文案（「加载中」不算终态） */
const HOME_HOT_READY = () => {
  const section = document.querySelector('.home-hot')
  if (!section) {
    return false
  }
  if (section.querySelector('.hot-card') || section.querySelector('.home-hot__empty')) {
    return true
  }
  const stateText = section.querySelector('.home-hot__state-text')
  return !!stateText && stateText.innerText.trim().indexOf('加载中') < 0
}

/** 当前门店的本地存储键：与 api/store.js 保持一致 */
const CURRENT_STORE_ID_KEY = 'ys_current_store_id'

/** 打开首页并等热门课程栏目进入终态；传 storeId 时先把它设为当前门店 */
async function openHome(ctx, options = {}) {
  for (const route of options.routes || []) {
    await ctx.page.route(route.match, route.handler)
  }
  if (options.storeId) {
    await ctx.page.addInitScript((args) => localStorage.setItem(args.key, args.id),
      { key: CURRENT_STORE_ID_KEY, id: String(options.storeId) })
  }
  await ctx.page.goto(ctx.h5Base + '/#/pages/home/index', { waitUntil: 'load' })
  await ctx.page.waitForSelector('uni-page-body', { timeout: 20000 })
  await ctx.page.waitForFunction(HOME_HOT_READY, null, { timeout: 20000 })
}

/** 页面上的热门课程卡片 */
function hotCards(ctx) {
  return ctx.page.evaluate(() =>
    Array.from(document.querySelectorAll('.hot-card:not(.hot-card--blank)')).map((card) => {
      const pick = (selector) => {
        const node = card.querySelector(selector)
        return node ? node.innerText.trim() : ''
      }
      const activeColor = 'rgb(71, 153, 239)'
      const stars = Array.from(card.querySelectorAll('.hot-card__star'))
      return {
        name: pick('.hot-card__name'),
        tag: pick('.hot-card__tag'),
        texts: Array.from(card.querySelectorAll('.hot-card__text')).map((node) => node.innerText.trim()),
        starTotal: stars.length,
        starActive: stars.filter((star) => {
          const nodes = [star].concat(Array.from(star.querySelectorAll('*')))
          return nodes.some((node) => getComputedStyle(node).color === activeColor)
        }).length
      }
    }))
}

async function fetchHot(ctx, storeId) {
  const response = await fetch(ctx.env.apiBase + HOT_API + '?storeId=' + encodeURIComponent(storeId))
  return (await response.json()).data || []
}

/** 会员当前可用于团课预约的次数卡：已开卡、正常、有指定门店且剩余次数足够 */
async function pickGroupCard(ctx) {
  const cards = await ctx.fetchCards()
  return (cards.rows || []).find((card) => card.status === 2 && card.cardType === 1 && card.courseScope === 1
    && card.storeId && Number(card.remainingTimes) >= REQUIRED_TIMES) || null
}

/**
 * 准备热门课程数据：在会员次数卡适用的门店下排 6 节课，并按 persons 预约。
 * 期望排名：S5(4 人) > S2(3 人) > S4(2 人，开课早) > S3(2 人) > S1(1 人，被截掉)，S6 无人预约不入选。
 */
async function prepareHot(ctx, fixture) {
  const card = await pickGroupCard(ctx)
  if (!card) {
    throw new SkipError('会员没有剩余次数足够的团课次卡，无法制造预约量')
  }
  const storeId = String(card.storeId)
  const stores = await fixture.publicStores()
  const store = stores.find((item) => String(item.id) === storeId)
  assert.ok(store, '次卡适用门店应存在')
  const existing = await fetchHot(ctx, storeId)
  if (existing.length) {
    throw new SkipError('次卡适用门店已有热门课程，无法构造确定的排名')
  }

  const classroom = await fixture.ensureClassroom(storeId)
  const group = await fixture.createCourse(1, { name: `E2E热门团课${fixture.runSuffix}`, difficulty: 2 })
  const boutique = await fixture.createCourse(2, { name: `E2E热门精品${fixture.runSuffix}`, difficulty: 4 })
  const coach = await fixture.createCoach({ name: `E2E热门教练${fixture.runSuffix}`, featured: false })
  const base = { coachId: coach.id, storeId, classroomId: classroom.id, maxPersons: 20 }

  const plan = [
    { key: 's1', course: group, dayOffset: 3, hour: 10, minute: 0, persons: 1 },
    { key: 's2', course: group, dayOffset: 4, hour: 9, minute: 0, persons: 3 },
    { key: 's3', course: group, dayOffset: 5, hour: 11, minute: 0, persons: 2 },
    { key: 's4', course: group, dayOffset: 5, hour: 9, minute: 0, persons: 2 },
    { key: 's5', course: boutique, dayOffset: 6, hour: 9, minute: 0, persons: 4 },
    { key: 's6', course: group, dayOffset: 6, hour: 13, minute: 0, persons: 0 }
  ]
  const schedules = {}
  for (const item of plan) {
    const id = await fixture.createSchedule(Object.assign({ courseId: item.course.id, dayOffset: item.dayOffset,
      hour: item.hour, minute: item.minute }, base))
    schedules[item.key] = id
    if (item.persons > 0) {
      await ctx.createReservation({ scheduleId: id, memberCardId: card.id, persons: item.persons })
    }
  }
  return { store, group, boutique, coach, classroom, schedules, scheduleIds: Object.values(schedules) }
}

/** 先取消本次制造的预约（返还次数），再删除夹具数据；任一步失败都向上抛出 */
async function cleanup(ctx, fixture, scheduleIds) {
  const failures = []
  try {
    if (scheduleIds && scheduleIds.length) {
      const page = await ctx.fetchReservations({ pageNum: 1, pageSize: 100, status: 1 })
      for (const row of (page.rows || []).filter((item) => scheduleIds.includes(String(item.scheduleId)))) {
        try {
          await ctx.cancelReservation(row.id)
        } catch (error) {
          failures.push(`取消预约 ${row.id}：${error.message}`)
        }
      }
    }
  } catch (error) {
    failures.push(`查询待取消预约：${error.message}`)
  }
  try {
    await fixture.cleanup()
  } catch (error) {
    failures.push(error.message)
  }
  if (failures.length) {
    throw new Error('热门课程测试数据清理失败：\n  ' + failures.join('\n  '))
  }
}

module.exports = {
  name: '首页热门课程',
  cases: [
    {
      name: '展示与排序：按预约人数降序、同人数开课早的在前，最多 4 条且每页两张',
      async run(ctx) {
        const fixture = await createAdminFixture()
        let scheduleIds = []
        try {
          const data = await prepareHot(ctx, fixture)
          scheduleIds = data.scheduleIds.map(String)
          const { schedules } = data
          const expected = [schedules.s5, schedules.s2, schedules.s4, schedules.s3]

          const remote = await fetchHot(ctx, data.store.id)
          assert.equal(remote.map((item) => item.id).join(','), expected.join(','),
            '接口应按预约人数降序取前 4 条，同人数开课早的在前，无人预约的不入选')

          await openHome(ctx, { storeId: data.store.id })
          const cards = await hotCards(ctx)
          assert.equal(cards.length, 4, '首页展示接口返回的 4 条')
          assert.equal(cards[0].name, data.boutique.name, '第一名是预约人数最多的精品课')
          assert.equal(cards[0].tag, '精品课', '展示课种角标')
          assert.equal(cards[0].texts[0], data.coach.name, '展示教练')
          assert.equal(cards[0].texts[1], '60分钟', '时长由结束时间减开始时间得到')
          assert.equal(cards[0].starTotal, 5, '难度固定 5 颗星')
          assert.equal(cards[0].starActive, 4, '精品课难度 4 星')
          assert.equal(cards[1].name, data.group.name, '第二名展示团课')
          assert.equal(cards[1].tag, '团课', '团课角标')
          assert.equal(cards[1].starActive, 2, '团课难度 2 星')

          assert.equal(await ctx.page.locator('.home-hot__swiper uni-swiper-item').count(), 2, '4 张卡片分 2 页')
          assert.equal(await ctx.page.locator('.home-hot__dot').count(), 2, '指示点对应页数')
          assert.equal(await ctx.page.locator('.home-hot__dot--on').count(), 1, '当前页指示点高亮')
          assert.equal(await ctx.page.locator('.home-hot__empty').count(), 0, '有课程时不显示空态')
        } finally {
          await cleanup(ctx, fixture, scheduleIds)
        }
      }
    },
    {
      name: '无热门课程：显示「暂无热门课程」',
      async run(ctx) {
        const fixture = await createAdminFixture()
        try {
          const store = await fixture.currentStore()
          assert.ok(store, '隔离环境应至少有一家门店')
          const remote = await fetchHot(ctx, store.id)
          if (remote.length) {
            throw new SkipError('隔离库当前门店已有热门课程，无法构造空态')
          }
          await openHome(ctx)
          assert.contains((await ctx.page.locator('.home-hot__empty').innerText()).trim(), '暂无热门课程', '空态文案')
          assert.equal(await ctx.page.locator('.hot-card').count(), 0, '空态不显示卡片')
          assert.equal(await ctx.page.locator('.home-hot__dot').count(), 0, '空态不显示指示点')
        } finally {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '点击卡片进入该场次的课程详情，点击「更多课程」进入约课页',
      async run(ctx) {
        const fixture = await createAdminFixture()
        let scheduleIds = []
        try {
          const data = await prepareHot(ctx, fixture)
          scheduleIds = data.scheduleIds.map(String)

          await openHome(ctx, { storeId: data.store.id })
          await ctx.page.locator('.hot-card:not(.hot-card--blank)').first().click()
          await ctx.page.waitForFunction((id) => location.hash.includes('pages/detail/index')
            && location.hash.includes('scheduleId=' + id), data.schedules.s5, { timeout: 15000 })

          await openHome(ctx, { storeId: data.store.id })
          await ctx.page.locator('.home-hot .ys-section-head__more').click()
          await ctx.page.waitForFunction(() => location.hash.includes('pages/booking/index'), null,
            { timeout: 15000 })
        } finally {
          await cleanup(ctx, fixture, scheduleIds)
        }
      }
    },
    {
      name: '网络失败：栏目显示加载失败，重试后展示课程且今日团课、金牌教练不受影响',
      async run(ctx) {
        const fixture = await createAdminFixture()
        let scheduleIds = []
        try {
          const data = await prepareHot(ctx, fixture)
          scheduleIds = data.scheduleIds.map(String)
          let failedOnce = false

          await openHome(ctx, {
            storeId: data.store.id,
            routes: [{
              match: (url) => url.pathname === HOT_API,
              handler: async (route) => {
                if (!failedOnce) {
                  failedOnce = true
                  await route.abort('failed')
                  return
                }
                await route.continue()
              }
            }]
          })
          assert.ok(failedOnce, '首次请求应被拦截失败')
          assert.contains(await ctx.page.locator('.home-hot__state-text').first().innerText(), '加载失败',
            '失败时显示加载失败')
          assert.equal(await ctx.page.locator('.hot-card').count(), 0, '失败时不显示卡片')
          assert.equal(await ctx.page.locator('.home-today').count(), 1, '今日可约团课栏目不受影响')
          assert.equal(await ctx.page.locator('.home-coaches').count(), 1, '金牌教练栏目不受影响')

          await ctx.page.locator('.home-hot__retry').click()
          await ctx.page.waitForSelector('.hot-card', { timeout: 15000 })
          assert.equal((await hotCards(ctx)).length, 4, '重试后展示 4 张卡片')
          assert.ok(requestsMatching(ctx, HOT_API).length >= 2, '重试应再次请求接口')
        } finally {
          await cleanup(ctx, fixture, scheduleIds)
        }
      }
    },
    {
      name: '换店后重新请求并按新门店展示',
      async run(ctx) {
        const fixture = await createAdminFixture()
        try {
          const stores = await fixture.publicStores()
          if (stores.length < 2) {
            throw new SkipError('隔离库门店不足两家，无法验证换店')
          }
          const current = stores[0]
          const other = stores[1]

          await openHome(ctx)
          const before = requestsMatching(ctx, HOT_API).length
          assert.ok(before >= 1, '进入首页应请求热门课程')
          assert.ok(requestsMatching(ctx, HOT_API).every((url) => url.includes('storeId=' + current.id)),
            '默认按当前门店请求')

          await ctx.page.locator('.store__change').click()
          await ctx.page.locator('.pitem', { hasText: other.name }).click()
          await waitUntil(() => requestsMatching(ctx, HOT_API).length > before, '换店后未重新请求热门课程')
          assert.ok(requestsMatching(ctx, HOT_API).pop().includes('storeId=' + other.id), '换店后按新门店请求')
          await ctx.page.waitForFunction(HOME_HOT_READY, null, { timeout: 15000 })

          const remote = await fetchHot(ctx, other.id)
          assert.equal((await hotCards(ctx)).length, remote.length, '页面卡片数与新门店接口结果一致')
        } finally {
          await fixture.cleanup()
        }
      }
    }
  ]
}
