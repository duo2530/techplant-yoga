const { assert, SkipError } = require('../lib/assert')
const { jsonRoute } = require('../lib/runner')

/**
 * 预约流程（课程详情 → 立即预约 → 预约成功 → 我的预约）端到端用例。
 *
 * 全程连真实后端：可约场次、可用会员卡与预约结果都以同一批接口为期望值来源；
 * 每次运行前先清理该场次遗留的未取消预约，避免上一轮数据让本轮预约冲突。
 * 「筛选切换」与「签到按钮」两类时序用例用响应拦截构造数据。
 */

const RESERVATION_API = '**/api/reservations**'

/** 用种子会员名下「E2E 团课次卡」的适用门店，保证有可用卡 */
const STORE_ID = process.env.E2E_STORE_ID || '1856739201475235901'

/** 团课 */
const COURSE_TYPE_GROUP = 1

function dateAfterDays(days)
{
  const date = new Date(Date.now() + days * 86400000)
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** 相对当前时间的 yyyy-MM-dd HH:mm:ss（与接口时间格式一致） */
function localDateTime(offsetMs)
{
  const date = new Date(Date.now() + offsetMs)
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/** 构造一条 ReservationPublicItemVO，用于拦截响应 */
function reservationRow(options)
{
  const startTime = localDateTime(options.startOffsetMs)
  const endTime = localDateTime(options.startOffsetMs + 60 * 60000)
  return {
    id: String(options.id),
    scheduleId: String(options.scheduleId),
    courseId: '1856739201475235801',
    courseName: options.courseName,
    courseCoverUrl: null,
    courseType: COURSE_TYPE_GROUP,
    courseTypeName: '团课',
    coachName: '林教练',
    storeAddress: '漕溪北路 88 号',
    scheduleDate: startTime.slice(0, 10),
    startTime,
    endTime,
    status: options.status,
    statusText: options.statusText
  }
}

/** 今天起 3 天内的第一个可约团课场次 */
async function pickBookableSchedule(ctx)
{
  for (const days of [1, 2, 3])
  {
    const page = await ctx.fetchSchedules({ storeId: STORE_ID, courseType: COURSE_TYPE_GROUP, date: dateAfterDays(days) })
    const bookable = (page.rows || []).filter((row) => row.bookable)
    if (bookable.length)
    {
      return bookable[0]
    }
  }
  return null
}

/** 清理该场次遗留的未取消预约（同一会员不能重复预约同一场次） */
async function cancelActiveReservations(ctx, scheduleId)
{
  const page = await ctx.fetchReservations({ pageNum: 1, pageSize: 100, status: 1 })
  const active = (page.rows || []).filter((row) => String(row.scheduleId) === String(scheduleId))
  for (const row of active)
  {
    await ctx.cancelReservation(row.id)
  }
}

module.exports = {
  name: '预约（booking-confirm → booking-success → my-bookings）',
  cases: [
    {
      name: '完整流程：详情页「立即预约」→ 预约页提交 → 预约成功页 →「查看预约」进我的预约',
      async run(ctx)
      {
        const schedule = await pickBookableSchedule(ctx)
        if (!schedule)
        {
          throw new SkipError('种子门店今天起 3 天内没有可约的团课场次')
        }
        await cancelActiveReservations(ctx, schedule.id)
        const before = await ctx.fetchReservations({ pageNum: 1, pageSize: 100 })

        await ctx.openScheduleDetail(schedule.id)
        assert.contains((await ctx.page.locator('.bar__btn').innerText()).trim(), '立即预约', '课程详情底部按钮文案')

        await ctx.page.locator('.bar__btn').click()
        await ctx.page.waitForFunction(() => !!document.querySelector('.bar__btn, .nofound'), null, { timeout: 20000 })
        assert.contains(ctx.page.url(), 'booking-confirm', '点击后应进入立即预约页')
        assert.equal(await ctx.count('.ccard'), 1, '立即预约页应展示课程卡')
        assert.equal(await ctx.count('.store'), 1, '立即预约页应展示门店卡')
        assert.contains(await ctx.page.locator('.ccard__name').innerText(), schedule.courseName, '立即预约页课程名')

        const usableCards = await ctx.count('.mcard--on')
        if (!usableCards)
        {
          throw new SkipError('当前会员没有可用于该场次的会员卡（立即预约页没有可选中卡片）')
        }

        await ctx.page.locator('.bar__btn').click()
        await ctx.waitBookingSuccess()
        assert.contains(await ctx.page.locator('.ok__title').innerText(), '预约成功', '预约成功页标题')
        const buttons = (await ctx.page.locator('.ok__btn-text').allInnerTexts()).join(',')
        assert.contains(buttons, '查看预约', '成功页第一个按钮')
        assert.contains(buttons, '返回首页', '成功页第二个按钮')

        const after = await ctx.fetchReservations({ pageNum: 1, pageSize: 100 })
        const created = (after.rows || []).find(
          (row) => String(row.scheduleId) === String(schedule.id) && row.status === 1)
        assert.ok(created, '预约成功后后端应有一条该场次的已预约记录')
        assert.equal(after.total, before.total + 1, '预约成功后我的预约总数应加一')

        await ctx.page.locator('.ok__btn--primary').click()
        await ctx.page.waitForSelector('.ys-booking', { timeout: 20000 })
        assert.contains(ctx.page.url(), 'my-bookings', '「查看预约」应进入我的预约页')
        const firstCard = (await ctx.page.locator('.ys-booking').first().innerText()).replace(/\n+/g, ' | ')
        assert.contains(firstCard, schedule.courseName, '我的预约首条应是刚预约的课程')
        assert.contains(firstCard, '已预约', '我的预约首条状态')
      }
    },
    {
      name: '预约成功页：无 query 时给结果提示，两个出口分别是我的预约与首页',
      async run(ctx)
      {
        await ctx.openBookingSuccess()
        assert.contains(await ctx.page.locator('.ok__title').innerText(), '预约成功', '预约成功页标题')
        assert.contains(await ctx.page.locator('.ok__subtitle').innerText(), '已加入我的预约', '无 query 时的副标题')

        await ctx.page.locator('.ok__btn--ghost').click()
        // switchTab 到首个 tab 时 H5 的 hash 会回到 `#/`，因此同时接受首页内容出现
        await ctx.page.waitForFunction(() =>
        {
          const hash = location.hash
          return (hash === '#/' || hash.includes('pages/home/index')) && !!document.querySelector('.home')
        }, null, { timeout: 20000 })
        assert.contains(ctx.page.url(), '#/', '「返回首页」应回到首页')
      }
    },
    {
      name: '我的预约：「取消预约」确认后状态变为已取消，按钮消失',
      async run(ctx)
      {
        const active = await ctx.fetchReservations({ pageNum: 1, pageSize: 100, status: 1 })
        const target = (active.rows || [])[0]
        if (!target)
        {
          throw new SkipError('当前会员没有已预约记录（先跑完整流程用例）')
        }

        await ctx.openMyBookings()
        const card = ctx.page.locator('.ys-booking').first()
        await card.waitFor({ timeout: 20000 })
        assert.contains(await card.innerText(), '已预约', '首条预约状态')

        await card.locator('.ys-booking__cancel').click()
        await ctx.page.waitForSelector('.uni-modal__btn_primary', { timeout: 10000 })
        await ctx.page.locator('.uni-modal__btn_primary').click()

        await ctx.page.waitForFunction(() =>
        {
          const first = document.querySelector('.ys-booking')
          return !!first && first.innerText.includes('已取消')
        }, null, { timeout: 20000 })
        assert.equal(await ctx.page.locator('.ys-booking').first().locator('.ys-booking__cancel').count(), 0,
          '已取消的预约不再显示取消按钮')

        const cancelled = await ctx.fetchReservations({ pageNum: 1, pageSize: 100, status: 2 })
        assert.ok(cancelled.rows.some((row) => String(row.id) === String(target.id)),
          '取消后该预约应出现在已取消筛选里')
      }
    },
    {
      name: '筛选切换：加载中切到「已预约」后，过期的旧筛选响应不得覆盖列表',
      async run(ctx)
      {
        const staleRow = reservationRow({
          id: 9001,
          scheduleId: 9101,
          courseName: '旧筛选的已取消课程',
          status: 2,
          statusText: '已取消',
          startOffsetMs: 25 * 60000
        })
        const freshRow = reservationRow({
          id: 9002,
          scheduleId: 9102,
          courseName: '新筛选的已预约课程',
          status: 1,
          statusText: '已预约',
          startOffsetMs: 90 * 60000
        })

        await ctx.openMyBookings({
          waitReady: false,
          routes: [{
            match: RESERVATION_API,
            handler: async (route) =>
            {
              if (route.request().url().includes('status=1'))
              {
                await route.fulfill({
                  status: 200,
                  contentType: 'application/json',
                  body: JSON.stringify({ total: 1, rows: [freshRow], code: 200, msg: '查询成功' })
                })
                return
              }
              // 首屏（全部）响应故意延迟，制造「加载中切换筛选」
              await new Promise((resolve) => setTimeout(resolve, 2500))
              await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ total: 1, rows: [staleRow], code: 200, msg: '查询成功' })
              })
            }
          }]
        })

        await ctx.page.locator('.status__item', { hasText: '已预约' }).click()
        await ctx.page.waitForFunction(() => document.body.innerText.includes('新筛选的已预约课程'),
          null, { timeout: 20000 })
        assert.contains(await ctx.page.locator('.status__text--on').innerText(), '已预约', '选中的是「已预约」Tab')

        // 等过首屏延迟，确认过期响应没有把旧筛选结果写回列表
        await ctx.page.waitForTimeout(3000)
        const text = (await ctx.page.locator('body').innerText()).replace(/\n+/g, ' | ')
        assert.contains(text, '新筛选的已预约课程', '列表应保持新筛选结果')
        assert.ok(!text.includes('旧筛选的已取消课程'), '过期的旧筛选响应不得写回列表')
      }
    },
    {
      name: '签到按钮：窗口前置灰，进入签到窗口后自动可点',
      async run(ctx)
      {
        // 上课时间 = 当前时间 + 30 分钟 + 8 秒：签到窗口约 8 秒后开放
        const row = reservationRow({
          id: 9003,
          scheduleId: 9103,
          courseName: '即将开课的团课',
          status: 1,
          statusText: '已预约',
          startOffsetMs: 30 * 60000 + 8000
        })

        await ctx.openMyBookings({
          routes: [{ match: RESERVATION_API, handler: jsonRoute({ total: 1, rows: [row], code: 200, msg: '查询成功' }) }]
        })

        const checkin = ctx.page.locator('.ys-booking__checkin')
        await checkin.waitFor({ timeout: 20000 })
        assert.ok(await checkin.evaluate((el) => el.classList.contains('ys-booking__checkin--off')),
          '签到窗口前按钮应置灰')

        await ctx.page.waitForFunction(() =>
        {
          const button = document.querySelector('.ys-booking__checkin')
          return !!button && !button.classList.contains('ys-booking__checkin--off')
        }, null, { timeout: 40000 })
      }
    }
  ]
}
