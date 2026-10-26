const { assert, SkipError } = require('../lib/assert')
const { createAdminFixture } = require('../lib/admin')
const { requestsMatching, waitUntil } = require('../lib/coach-app')

/**
 * 首页「今日可约团课」栏目端到端用例。
 *
 * 排课数据通过隔离后端的真实管理端接口准备：开课时间按运行时刻动态取「当前时刻之后的当天时段」，
 * 页面展示与公开接口 GET /api/schedules/today-bookable 对照；失败与重试用响应拦截构造。
 * 当天剩余时间不足以排下用例所需场次（接近午夜）时跳过，不用固定时刻伪造。
 */

const TODAY_API = '/api/schedules/today-bookable'

/** 栏目终态：有卡片，或空态／失败文案（「加载中」不算终态） */
const HOME_TODAY_READY = () => {
  const section = document.querySelector('.home-today')
  if (!section) {
    return false
  }
  if (section.querySelector('.today-item') || section.querySelector('.home-today__empty')) {
    return true
  }
  const stateText = section.querySelector('.home-today__state-text')
  return !!stateText && stateText.innerText.trim().indexOf('加载中') < 0
}

async function openHome(ctx, routes = []) {
  for (const route of routes) {
    await ctx.page.route(route.match, route.handler)
  }
  await ctx.page.goto(ctx.h5Base + '/#/pages/home/index', { waitUntil: 'load' })
  await ctx.page.waitForSelector('uni-page-body', { timeout: 20000 })
  await ctx.page.waitForFunction(HOME_TODAY_READY, null, { timeout: 20000 })
}

/** 页面上的今日团课卡片 */
function todayCards(ctx) {
  return ctx.page.evaluate(() =>
    Array.from(document.querySelectorAll('.today-item')).map((card) => {
      const pick = (selector) => {
        const node = card.querySelector(selector)
        return node ? node.innerText.trim() : ''
      }
      return {
        clock: pick('.today-item__clock'),
        name: pick('.today-item__name'),
        cells: Array.from(card.querySelectorAll('.today-item__text')).map((node) => node.innerText.trim()),
        go: pick('.today-item__go-text')
      }
    }))
}

async function fetchToday(ctx, storeId) {
  const response = await fetch(ctx.env.apiBase + TODAY_API + '?storeId=' + encodeURIComponent(storeId))
  return (await response.json()).data || []
}

function pad2(value) {
  return value < 10 ? '0' + value : '' + value
}

/** 当前时刻之后 offsetMinutes 分钟的整点分钟；越过当天则返回 null */
function laterToday(offsetMinutes) {
  const target = new Date(Date.now() + offsetMinutes * 60000)
  const today = new Date()
  if (target.getDate() !== today.getDate()) {
    return null
  }
  return { hour: target.getHours(), minute: target.getMinutes() }
}

/** 按开课时刻在今天排一场课：时刻不在当天内时跳过用例 */
function requireToday(offsetMinutes, duration) {
  const at = laterToday(offsetMinutes)
  const end = laterToday(offsetMinutes + duration)
  if (!at || !end) {
    throw new SkipError('当天剩余时间不足以排下用例所需场次，跳过')
  }
  return at
}

async function prepare(fixture) {
  const store = await fixture.currentStore()
  assert.ok(store, '隔离环境应至少有一家门店')
  const classroom = await fixture.ensureClassroom(store.id)
  const course = await fixture.createCourse(1, { name: `E2E今日团课${fixture.runSuffix}` })
  const boutique = await fixture.createCourse(2, { name: `E2E今日精品${fixture.runSuffix}` })
  return { store, classroom, course, boutique }
}

module.exports = {
  name: '首页今日可约团课',
  cases: [
    {
      name: '展示与排序：只展示今天未开始、有名额的团课，最多 2 条且按开课时间升序',
      async run(ctx) {
        const fixture = await createAdminFixture()
        try {
          const first = requireToday(20, 60)
          const second = requireToday(100, 60)
          const third = requireToday(180, 60)
          const { store, classroom, course, boutique } = await prepare(fixture)
          const coaches = []
          for (let i = 0; i < 4; i++) {
            coaches.push(await fixture.createCoach({ name: `E2E今日教练${i}-${fixture.runSuffix}`, featured: false }))
          }
          const base = { storeId: store.id, classroomId: classroom.id }
          // 故意乱序创建：页面必须按开课时间升序展示
          await fixture.createSchedule(Object.assign({ coachId: coaches[2].id, courseId: course.id, dayOffset: 0,
            maxPersons: 15 }, base, third))
          const firstId = await fixture.createSchedule(Object.assign({ coachId: coaches[0].id, courseId: course.id,
            dayOffset: 0, maxPersons: 15 }, base, first))
          const secondId = await fixture.createSchedule(Object.assign({ coachId: coaches[1].id, courseId: course.id,
            dayOffset: 0, maxPersons: 15 }, base, second))
          // 同一天的精品课、待上架团课与明天的团课都不应出现
          await fixture.createSchedule(Object.assign({ coachId: coaches[3].id, courseId: boutique.id, dayOffset: 0 },
            base, requireToday(40, 30), { duration: 30 }))
          await fixture.createSchedule(Object.assign({ coachId: coaches[3].id, courseId: course.id, dayOffset: 0,
            publish: false }, base, requireToday(240, 30), { duration: 30 }))
          await fixture.createSchedule(Object.assign({ coachId: coaches[3].id, courseId: course.id, dayOffset: 1,
            hour: 9, minute: 0 }, base))

          const remote = await fetchToday(ctx, store.id)
          assert.equal(remote.length, 2, '接口最多返回 2 条')
          assert.equal(remote.map((item) => item.id).join(','), [firstId, secondId].join(','),
            '接口应按开课时间升序取最早的 2 场团课')

          await openHome(ctx)
          const cards = await todayCards(ctx)
          assert.equal(cards.length, 2, '首页最多展示 2 条')
          const clock = (time) => pad2(time.hour) + ':' + pad2(time.minute)
          assert.equal(cards.map((item) => item.clock).join(','), [clock(first), clock(second)].join(','),
            '开课时间按升序展示且只显示时分')
          assert.equal(cards[0].name, course.name, '展示课程名')
          assert.equal(cards[0].cells[0], coaches[0].name, '展示教练')
          assert.equal(cards[0].cells[1], classroom.name, '展示教室')
          assert.equal(cards[0].cells[2], '60分钟', '时长由结束时间减开始时间得到')
          assert.equal(cards[0].cells[3], '0/15人', '展示已约人数/最大人数')
          assert.equal(cards[0].go, '去预约', '展示去预约按钮')
          assert.equal(await ctx.page.locator('.home-today__empty').count(), 0, '有场次时不显示空态')
        } finally {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '无可约场次：显示「今日暂无可约课程」',
      async run(ctx) {
        const fixture = await createAdminFixture()
        try {
          const store = await fixture.currentStore()
          assert.ok(store, '隔离环境应至少有一家门店')
          const remote = await fetchToday(ctx, store.id)
          if (remote.length) {
            throw new SkipError('隔离库当前门店已有今日可约团课，无法构造空态')
          }
          await openHome(ctx)
          assert.equal((await ctx.page.locator('.home-today__empty').innerText()).trim(), '今日暂无可约课程',
            '空态文案')
          assert.equal(await ctx.page.locator('.today-item').count(), 0, '空态不显示卡片')
        } finally {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '点击「去预约」进入该场次的课程详情',
      async run(ctx) {
        const fixture = await createAdminFixture()
        try {
          const at = requireToday(30, 60)
          const { store, classroom, course } = await prepare(fixture)
          const coach = await fixture.createCoach({ name: `E2E今日跳转${fixture.runSuffix}`, featured: false })
          const scheduleId = await fixture.createSchedule(Object.assign({ coachId: coach.id, courseId: course.id,
            storeId: store.id, classroomId: classroom.id, dayOffset: 0 }, at))

          await openHome(ctx)
          const remote = await fetchToday(ctx, store.id)
          assert.ok(remote.some((item) => item.id === scheduleId), '新建场次应出现在接口结果里')
          const index = remote.findIndex((item) => item.id === scheduleId)
          await ctx.page.locator('.today-item__go').nth(index).click()
          await ctx.page.waitForFunction((id) => location.hash.includes('pages/detail/index')
            && location.hash.includes('scheduleId=' + id), scheduleId, { timeout: 15000 })
        } finally {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '点击「查看全部」进入约课页',
      async run(ctx) {
        const fixture = await createAdminFixture()
        try {
          const at = requireToday(30, 60)
          const { store, classroom, course } = await prepare(fixture)
          const coach = await fixture.createCoach({ name: `E2E今日全部${fixture.runSuffix}`, featured: false })
          await fixture.createSchedule(Object.assign({ coachId: coach.id, courseId: course.id,
            storeId: store.id, classroomId: classroom.id, dayOffset: 0 }, at))

          await openHome(ctx)
          await ctx.page.locator('.home-today .ys-section-head__more').click()
          await ctx.page.waitForFunction(() => location.hash.includes('pages/booking/index'), null,
            { timeout: 15000 })
        } finally {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '网络失败：栏目显示加载失败，重试后展示场次且金牌教练不受影响',
      async run(ctx) {
        const fixture = await createAdminFixture()
        try {
          const at = requireToday(30, 60)
          const { store, classroom, course } = await prepare(fixture)
          const coach = await fixture.createCoach({ name: `E2E今日重试${fixture.runSuffix}`, featured: false })
          const scheduleId = await fixture.createSchedule(Object.assign({ coachId: coach.id, courseId: course.id,
            storeId: store.id, classroomId: classroom.id, dayOffset: 0 }, at))
          let failedOnce = false

          await openHome(ctx, [{
            match: (url) => url.pathname === TODAY_API,
            handler: async (route) => {
              if (!failedOnce) {
                failedOnce = true
                await route.abort('failed')
                return
              }
              await route.continue()
            }
          }])
          assert.ok(failedOnce, '首次请求应被拦截失败')
          assert.contains(await ctx.page.locator('.home-today__state-text').first().innerText(), '加载失败',
            '失败时显示加载失败')
          assert.equal(await ctx.page.locator('.today-item').count(), 0, '失败时不显示卡片')
          assert.ok(await ctx.page.locator('.home-coaches').count() === 1, '金牌教练栏目不受影响')

          await ctx.page.locator('.home-today__retry').click()
          await ctx.page.waitForSelector('.today-item', { timeout: 15000 })
          const remote = await fetchToday(ctx, store.id)
          assert.ok(remote.some((item) => item.id === scheduleId), '重试后接口结果包含新建场次')
          assert.ok(requestsMatching(ctx, TODAY_API).length >= 2, '重试应再次请求接口')
        } finally {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '换店后重新请求并按新门店展示',
      async run(ctx) {
        const fixture = await createAdminFixture()
        try {
          const at = requireToday(30, 60)
          const stores = await fixture.publicStores()
          if (stores.length < 2) {
            throw new SkipError('隔离库门店不足两家，无法验证换店')
          }
          const current = stores[0]
          const other = stores[1]
          const classroom = await fixture.ensureClassroom(other.id)
          const course = await fixture.createCourse(1, { name: `E2E今日换店${fixture.runSuffix}` })
          const coach = await fixture.createCoach({ name: `E2E今日换店教练${fixture.runSuffix}`, featured: false })
          const scheduleId = await fixture.createSchedule(Object.assign({ coachId: coach.id, courseId: course.id,
            storeId: other.id, classroomId: classroom.id, dayOffset: 0 }, at))

          await openHome(ctx)
          const before = requestsMatching(ctx, TODAY_API).length
          assert.ok(before >= 1, '进入首页应请求今日团课')
          assert.ok(requestsMatching(ctx, TODAY_API).every((url) => url.includes('storeId=' + current.id)),
            '默认按当前门店请求')

          await ctx.page.locator('.store__change').click()
          await ctx.page.locator('.pitem', { hasText: other.name }).click()
          await waitUntil(() => requestsMatching(ctx, TODAY_API).length > before, '换店后未重新请求今日团课')
          assert.ok(requestsMatching(ctx, TODAY_API).pop().includes('storeId=' + other.id), '换店后按新门店请求')
          await ctx.page.waitForFunction((name) => Array.from(document.querySelectorAll('.today-item__name'))
            .some((node) => node.innerText.includes(name)), course.name, { timeout: 15000 })
          assert.ok(scheduleId, '场次已创建')
        } finally {
          await fixture.cleanup()
        }
      }
    }
  ]
}
