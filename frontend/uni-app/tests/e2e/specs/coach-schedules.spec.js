const { assert, SkipError } = require('../lib/assert')
const { createAdminFixture } = require('../lib/admin')
const {
  openCoach, schedCards, activeTabText, clickTab, waitSchedCards, requestsMatching, waitUntil, nextFrames
} = require('../lib/coach-app')

/**
 * 约教练页排课浏览端到端用例。
 *
 * 排课数据全部通过隔离后端的真实管理端接口准备（新建课程、教练、排课并上架）；
 * 「追加失败」与「快速切换乱序」两类时序场景用响应拦截构造。
 */

/** 拦截构造用的排课卡片响应（只用于验证时序，不冒充真实数据流程） */
function fakeCardRow(options)
{
  const date = options.date
  return {
    id: String(options.id),
    courseName: options.courseName,
    courseCoverUrl: null,
    courseType: options.courseType,
    courseTypeName: options.courseTypeName,
    difficulty: 2,
    scheduleDate: date,
    startTime: `${date} 09:00:00`,
    endTime: `${date} 10:00:00`,
    coachName: options.coachName || '时序用例教练',
    storeId: '1856739201475235901',
    storeName: '时序用例门店',
    classroomName: '时序用例教室',
    maxPersons: 20,
    bookedPersons: 0,
    remainingPlaces: 20,
    status: 2,
    statusText: '可约',
    bookable: true
  }
}

function dateAfterDays(days)
{
  const date = new Date(Date.now() + days * 86400000)
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** 用当前首页门店准备「课程 + 教室 + 教练」三件套 */
async function prepareCoachAssets(fixture, courseTypes)
{
  const store = await fixture.currentStore()
  if (!store)
  {
    throw new SkipError('隔离环境没有门店，无法准备排课数据')
  }
  const classroom = await fixture.ensureClassroom(store.id)
  const courses = []
  for (const courseType of courseTypes)
  {
    courses.push(await fixture.createCourse(courseType))
  }
  const coach = await fixture.createCoach({
    name: `E2E排课${fixture.runSuffix}-${courseTypes.join('')}`,
    intro: '排课用例教练'
  })
  return { store, classroom, courses, coach }
}

module.exports = {
  name: '约教练页（排课浏览）',
  cases: [
    {
      name: '课种切换：默认团课，四类课种分别展示对应教练排课与卡片字段',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const assets = await prepareCoachAssets(fixture, [1, 2, 3, 4])
          const remote = []
          for (let i = 0; i < assets.courses.length; i++)
          {
            await fixture.createSchedule({
              coachId: assets.coach.id,
              storeId: assets.store.id,
              courseId: assets.courses[i].id,
              classroomId: assets.classroom.id,
              dayOffset: i + 1,
              hour: 9 + i,
              minute: 0,
              duration: 60
            })
            remote.push(await fixture.publicCoachSchedules(assets.coach.id, {
              storeId: assets.store.id,
              courseType: i + 1,
              pageNum: 1,
              pageSize: 10
            }))
          }

          await openCoach(ctx, assets.coach.id)
          await ctx.page.waitForFunction(() => document.body.innerText.includes('当前门店 · 未来14天'),
            null, { timeout: 20000 })
          await waitSchedCards(ctx, 1)

          const firstPage = remote[0]
          assert.equal(firstPage.total, 1, '团课应只有一场排课')
          const date = String(firstPage.rows[0].scheduleDate)

          let cards = await schedCards(ctx)
          assert.equal(cards.length, 1, '默认团课只展示团课排课')
          assert.equal(cards[0].name, assets.courses[0].name, '排课卡课程名来自真实排课')
          assert.equal(cards[0].type, '团课', '排课卡显示课种')
          assert.equal(cards[0].status, '可约', '排课卡显示后端状态文案')
          assert.ok(cards[0].coverEmpty, '课程没有封面时使用课程占位')
          assert.ok(cards[0].lines[0].includes('时间 '), '排课卡显示时间')
          assert.ok(cards[0].lines[0].includes(date), '排课卡显示日期')
          assert.ok(cards[0].lines[0].includes('09:00-10:00'), '排课卡显示起止时间')
          assert.ok(cards[0].lines[1].includes('难度'), '排课卡显示难度')
          assert.ok(cards[0].lines[1].includes(assets.classroom.name), '排课卡显示教室')
          assert.ok(cards[0].remaining.includes('剩余'), '排课卡显示剩余名额')
          assert.ok(cards[0].remaining.includes('20'), '剩余名额取真实值')

          const expected = [
            { label: '精品课', index: 1 },
            { label: '私教课', index: 2 },
            { label: '特色课', index: 3 }
          ]
          for (const item of expected)
          {
            await clickTab(ctx, item.label)
            await ctx.page.waitForFunction((name) =>
            {
              const node = document.querySelector('.sched-card__name')
              return !!node && node.innerText.trim() === name
            }, assets.courses[item.index].name, { timeout: 20000 })
            cards = await schedCards(ctx)
            assert.equal(cards.length, 1, `切换到${item.label}后只展示该课种排课`)
            assert.equal(cards[0].name, assets.courses[item.index].name, `${item.label}排课应与接口一致`)
            assert.equal(await activeTabText(ctx), item.label, `选中的课种 Tab 是${item.label}`)
            assert.ok(requestsMatching(ctx, 'courseType=' + (item.index + 1)).length >= 1,
              `切换课种应请求 courseType=${item.index + 1}`)
          }
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '加载更多：默认每页 10 条，显式按钮追加下一页并显示末尾状态',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const assets = await prepareCoachAssets(fixture, [1])
          for (let i = 0; i < 12; i++)
          {
            await fixture.createSchedule({
              coachId: assets.coach.id,
              storeId: assets.store.id,
              courseId: assets.courses[0].id,
              classroomId: assets.classroom.id,
              dayOffset: i + 1,
              hour: 9,
              minute: 0,
              duration: 60
            })
          }
          const remote = await fixture.publicCoachSchedules(assets.coach.id, {
            storeId: assets.store.id,
            courseType: 1,
            pageNum: 1,
            pageSize: 100
          })
          assert.equal(remote.total, 12, '真实数据应有 12 场排课')

          await openCoach(ctx, assets.coach.id)
          await waitSchedCards(ctx, 10)
          assert.equal(await ctx.page.locator('.schedules__more').count(), 1, '还有下一页时显示显式「加载更多」按钮')
          assert.ok((await ctx.page.locator('.schedules__more').innerText()).includes('加载更多'),
            '加载更多按钮文案')

          await ctx.page.locator('.schedules__more').click()
          await waitSchedCards(ctx, 12)
          await ctx.page.waitForFunction(() => document.body.innerText.includes('没有更多了'),
            null, { timeout: 20000 })

          const cards = await schedCards(ctx)
          assert.equal(cards.length, remote.total, '加载更多后展示全部真实排课')
          assert.ok(cards[0].lines[0].includes(String(remote.rows[0].scheduleDate)), '首条按开始时间升序')
          assert.ok(cards[11].lines[0].includes(String(remote.rows[11].scheduleDate)), '末条按开始时间升序')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '加载更多后切换课种：从新课种第一页重新加载，不混入旧课种',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const assets = await prepareCoachAssets(fixture, [1, 2])
          for (let i = 0; i < 12; i++)
          {
            await fixture.createSchedule({
              coachId: assets.coach.id,
              storeId: assets.store.id,
              courseId: assets.courses[0].id,
              classroomId: assets.classroom.id,
              dayOffset: i + 1,
              hour: 9,
              minute: 0,
              duration: 60
            })
          }
          await fixture.createSchedule({
            coachId: assets.coach.id,
            storeId: assets.store.id,
            courseId: assets.courses[1].id,
            classroomId: assets.classroom.id,
            dayOffset: 2,
            hour: 14,
            minute: 0,
            duration: 60
          })

          await openCoach(ctx, assets.coach.id)
          await waitSchedCards(ctx, 10)
          await ctx.page.locator('.schedules__more').click()
          await waitSchedCards(ctx, 12)

          await clickTab(ctx, '精品课')
          await ctx.page.waitForFunction((name) =>
          {
            const node = document.querySelector('.sched-card__name')
            return !!node && node.innerText.trim() === name
          }, assets.courses[1].name, { timeout: 20000 })

          const cards = await schedCards(ctx)
          assert.equal(cards.length, 1, '切换课种后只展示新课种第一页结果')
          assert.equal(cards[0].name, assets.courses[1].name, '新课种卡片来自对应课程')
          assert.ok(!cards.some((card) => card.name === assets.courses[0].name),
            '切课种后不得混入旧课种已加载的分页数据')
          assert.equal(await activeTabText(ctx), '精品课', '当前课种为精品课')
          assert.ok(requestsMatching(ctx, 'courseType=2').some((url) => url.includes('pageNum=1')),
            '切换课种应从第一页重新请求')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '切换课种请求挂起期间：旧课种卡片立即清空，不渲染也不可点（M1 回归）',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const assets = await prepareCoachAssets(fixture, [1, 2])
          await fixture.createSchedule({
            coachId: assets.coach.id,
            storeId: assets.store.id,
            courseId: assets.courses[0].id,
            classroomId: assets.classroom.id,
            dayOffset: 1,
            hour: 9,
            minute: 0,
            duration: 60
          })
          await fixture.createSchedule({
            coachId: assets.coach.id,
            storeId: assets.store.id,
            courseId: assets.courses[1].id,
            classroomId: assets.classroom.id,
            dayOffset: 2,
            hour: 14,
            minute: 0,
            duration: 60
          })
          const type2Page = await fixture.publicCoachSchedules(assets.coach.id, {
            storeId: assets.store.id,
            courseType: 2,
            pageNum: 1,
            pageSize: 10
          })

          let releaseNewType
          const newTypeGate = new Promise((resolve) => { releaseNewType = resolve })
          let newTypeRequestSeen = false

          await openCoach(ctx, assets.coach.id, {
            routes: [{
              match: (url) => /\/api\/coaches\/\d+\/schedules$/.test(url.pathname),
              handler: async (route) =>
              {
                const courseType = Number(new URL(route.request().url()).searchParams.get('courseType'))
                if (courseType === 2)
                {
                  newTypeRequestSeen = true
                  await newTypeGate
                  await route.fulfill({
                    status: 200,
                    contentType: 'application/json',
                    body: JSON.stringify({ code: 200, msg: '查询成功', rows: type2Page.rows, total: type2Page.total })
                  })
                  return
                }
                await route.continue()
              }
            }]
          })
          await waitSchedCards(ctx, 1)
          assert.equal((await schedCards(ctx))[0].name, assets.courses[0].name, '初始展示团课卡片')

          await clickTab(ctx, '精品课')
          await waitUntil(() => newTypeRequestSeen, '精品课请求未发出')

          assert.equal(await ctx.page.locator('.sched-card').count(), 0,
            '新课种响应到达前旧课种卡片必须清空，不能渲染或可点')
          const pendingBody = await ctx.page.locator('body').innerText()
          assert.ok(!pendingBody.includes(assets.courses[0].name), '旧课种课程名不得继续渲染')
          assert.ok(pendingBody.includes('加载中'), '清空后应显示加载中状态而不是空态')

          releaseNewType()
          await ctx.page.waitForFunction((name) =>
          {
            const node = document.querySelector('.sched-card__name')
            return !!node && node.innerText.trim() === name
          }, assets.courses[1].name, { timeout: 20000 })
          await waitSchedCards(ctx, 1)
          assert.equal((await schedCards(ctx))[0].name, assets.courses[1].name, '新响应到达后展示精品课卡片')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '排课接口 404/400：显示不可重试提示与返回/换店入口，不进入网络失败重试',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const assets = await prepareCoachAssets(fixture, [1])
          let currentCode = 404
          await openCoach(ctx, assets.coach.id, {
            routes: [{
              match: (url) => /\/api\/coaches\/\d+\/schedules$/.test(url.pathname),
              handler: (route) => route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                  code: currentCode,
                  msg: currentCode === 404 ? '门店不存在或已被删除' : '参数[storeId]格式非法'
                })
              })
            }]
          })

          await ctx.page.waitForFunction(() => document.body.innerText.includes('门店或教练已不存在'),
            null, { timeout: 20000 })
          await ctx.page.waitForTimeout(500)
          assert.equal(await ctx.page.locator('.schedules__retry').count(), 0,
            '数据不存在不应显示网络失败的重试入口')
          assert.equal(await ctx.page.locator('.schedules__actions').count(), 1,
            '不可重试场景应提供返回/换店入口')
          const actionText = (await ctx.page.locator('.schedules__actions').innerText()).replace(/\n+/g, ' | ')
          assert.contains(actionText, '返回', '提供返回入口')
          assert.contains(actionText, '去首页换店', '提供换店入口')
          const scheduleRequests = requestsMatching(ctx, '/schedules').length
          await ctx.page.waitForTimeout(500)
          assert.equal(requestsMatching(ctx, '/schedules').length, scheduleRequests,
            '404 场景不应自动重试排课请求')

          currentCode = 400
          await ctx.page.reload({ waitUntil: 'load' })
          await ctx.page.waitForSelector('uni-page-body', { timeout: 20000 })
          await ctx.page.waitForFunction(() => document.body.innerText.includes('排课查询参数有误'),
            null, { timeout: 20000 })
          assert.equal(await ctx.page.locator('.schedules__retry').count(), 0,
            '参数错误同样不应提供网络重试')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '追加失败：加载更多失败后可重试同一页，不丢失已加载内容',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const assets = await prepareCoachAssets(fixture, [1])
          for (let i = 0; i < 12; i++)
          {
            await fixture.createSchedule({
              coachId: assets.coach.id,
              storeId: assets.store.id,
              courseId: assets.courses[0].id,
              classroomId: assets.classroom.id,
              dayOffset: i + 1,
              hour: 9,
              minute: 0,
              duration: 60
            })
          }

          let failedOnce = false
          await openCoach(ctx, assets.coach.id, {
            routes: [{
              match: (url) => /\/api\/coaches\/\d+\/schedules$/.test(url.pathname),
              handler: async (route) =>
              {
                if (!failedOnce && urlHasPage(route, 2))
                {
                  failedOnce = true
                  await route.abort('failed')
                  return
                }
                await route.continue()
              }
            }]
          })

          await waitSchedCards(ctx, 10)
          await ctx.page.locator('.schedules__more').click()
          await ctx.page.waitForFunction(() => document.body.innerText.includes('加载更多失败'),
            null, { timeout: 20000 })
          assert.equal(await ctx.page.locator('.sched-card').count(), 10, '追加失败时已加载内容保留')
          assert.equal(await ctx.page.locator('.schedules__retry').count(), 1, '追加失败提供重试入口')

          await ctx.page.locator('.schedules__retry').click()
          await waitSchedCards(ctx, 12)
          assert.ok((await ctx.page.locator('body').innerText()).includes('没有更多了'), '重试成功后到达列表末尾')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '快速切换课种：旧课种晚返回的响应不覆盖当前列表',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const assets = await prepareCoachAssets(fixture, [1])
          const stale = fakeCardRow({
            id: 990001,
            courseName: '精品课旧响应',
            courseType: 2,
            courseTypeName: '精品课',
            date: dateAfterDays(2)
          })
          const fresh = fakeCardRow({
            id: 990002,
            courseName: '私教课新响应',
            courseType: 3,
            courseTypeName: '私教课',
            date: dateAfterDays(3)
          })

          let releaseStale
          const staleGate = new Promise((resolve) => { releaseStale = resolve })
          let staleRequestSeen = false
          let staleFulfilled
          const staleDone = new Promise((resolve) => { staleFulfilled = resolve })

          await openCoach(ctx, assets.coach.id, {
            routes: [{
              match: (url) => /\/api\/coaches\/\d+\/schedules$/.test(url.pathname),
              handler: async (route) =>
              {
                const courseType = Number(new URL(route.request().url()).searchParams.get('courseType'))
                if (courseType === 2)
                {
                  // 旧课种响应先挂起，由用例在断言后手动释放
                  staleRequestSeen = true
                  await staleGate
                  await route.fulfill({
                    status: 200,
                    contentType: 'application/json',
                    body: JSON.stringify({ code: 200, msg: '查询成功', rows: [stale], total: 1 })
                  })
                  staleFulfilled()
                  return
                }
                if (courseType === 3)
                {
                  await route.fulfill({
                    status: 200,
                    contentType: 'application/json',
                    body: JSON.stringify({ code: 200, msg: '查询成功', rows: [fresh], total: 1 })
                  })
                  return
                }
                await route.continue()
              }
            }]
          })
          await ctx.page.waitForFunction(() => document.body.innerText.includes('暂无排课'),
            null, { timeout: 20000 })

          await clickTab(ctx, '精品课')
          await waitUntil(() => staleRequestSeen, '精品课旧请求未发出')
          await clickTab(ctx, '私教课')
          await ctx.page.waitForFunction(() => document.body.innerText.includes('私教课新响应'),
            null, { timeout: 20000 })
          assert.equal(await activeTabText(ctx), '私教课', '当前课种保持为最后选择的私教课')
          assert.ok(!(await ctx.page.locator('body').innerText()).includes('精品课旧响应'),
            '旧课种响应未释放前不得混入当前列表')

          // 释放被挂起的旧课种响应，确认过期响应不会覆盖当前列表
          releaseStale()
          await staleDone
          await nextFrames(ctx)
          const body = await ctx.page.locator('body').innerText()
          assert.ok(body.includes('私教课新响应'), '列表保留当前课种结果')
          assert.ok(!body.includes('精品课旧响应'), '旧课种晚返回的响应不得覆盖当前列表')
          assert.ok(requestsMatching(ctx, 'courseType=2').length >= 1, '旧课种请求确实发出')
          assert.ok(requestsMatching(ctx, 'courseType=3').length >= 1, '新课种请求确实发出')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '返回刷新：从课程详情返回后重新拉当前课种第一页，课种 Tab 保持不变',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const assets = await prepareCoachAssets(fixture, [1, 2])
          await fixture.createSchedule({
            coachId: assets.coach.id,
            storeId: assets.store.id,
            courseId: assets.courses[0].id,
            classroomId: assets.classroom.id,
            dayOffset: 1,
            hour: 9,
            minute: 0,
            duration: 60
          })
          await fixture.createSchedule({
            coachId: assets.coach.id,
            storeId: assets.store.id,
            courseId: assets.courses[1].id,
            classroomId: assets.classroom.id,
            dayOffset: 2,
            hour: 14,
            minute: 0,
            duration: 60
          })

          await openCoach(ctx, assets.coach.id)
          await waitSchedCards(ctx, 1)
          const firstType1Requests = scheduleRequests(ctx, 1).length
          assert.equal(firstType1Requests, 1, '首次展示当前课种只请求一次第一页')
          await ctx.page.waitForTimeout(400)
          assert.equal(scheduleRequests(ctx, 1).length, firstType1Requests, '首屏展示后不重复请求排课')

          // 页面已打开后通过管理端新增一场团课，进入课程详情再返回应看到刷新后的列表
          await fixture.createSchedule({
            coachId: assets.coach.id,
            storeId: assets.store.id,
            courseId: assets.courses[0].id,
            classroomId: assets.classroom.id,
            dayOffset: 3,
            hour: 10,
            minute: 0,
            duration: 60
          })
          await ctx.page.locator('.sched-card').first().click()
          await ctx.page.waitForFunction(() => location.hash.includes('pages/detail/index'),
            null, { timeout: 20000 })
          await ctx.page.waitForSelector('.detail__body', { timeout: 20000 })
          await ctx.page.goBack()

          await waitSchedCards(ctx, 2)
          assert.ok(scheduleRequests(ctx, 1).length > firstType1Requests,
            '从详情返回后必须重新请求当前课种第一页')
          assert.equal(await activeTabText(ctx), '团课', '返回后课种 Tab 保持为团课')

          // 切到精品课后再进入详情返回：刷新当前课种且 Tab 仍保持精品课
          await clickTab(ctx, '精品课')
          await ctx.page.waitForFunction((name) =>
          {
            const node = document.querySelector('.sched-card__name')
            return !!node && node.innerText.trim() === name
          }, assets.courses[1].name, { timeout: 20000 })
          const firstType2Requests = scheduleRequests(ctx, 2).length

          await ctx.page.locator('.sched-card').first().click()
          await ctx.page.waitForFunction(() => location.hash.includes('pages/detail/index'),
            null, { timeout: 20000 })
          await ctx.page.waitForSelector('.detail__body', { timeout: 20000 })
          await ctx.page.goBack()

          await ctx.page.waitForFunction((name) =>
          {
            const node = document.querySelector('.sched-card__name')
            return !!node && node.innerText.trim() === name
          }, assets.courses[1].name, { timeout: 20000 })
          assert.ok(scheduleRequests(ctx, 2).length > firstType2Requests,
            '返回后必须重新请求当前课种第一页')
          assert.equal(await activeTabText(ctx), '精品课', '返回后课种 Tab 保持为精品课')
          const cards = await schedCards(ctx)
          assert.equal(cards.length, 1, '精品课列表刷新后仍是当前课种的排课')
          assert.equal(cards[0].name, assets.courses[1].name, '刷新后数据来自当前课种')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '排课卡进入课程详情：按 scheduleId 打开既有详情页，游客可浏览',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const assets = await prepareCoachAssets(fixture, [1])
          const scheduleId = await fixture.createSchedule({
            coachId: assets.coach.id,
            storeId: assets.store.id,
            courseId: assets.courses[0].id,
            classroomId: assets.classroom.id,
            dayOffset: 1,
            hour: 9,
            minute: 0,
            duration: 60
          })

          await openCoach(ctx, assets.coach.id)
          await waitSchedCards(ctx, 1)
          await ctx.page.locator('.sched-card').first().click()

          await ctx.page.waitForFunction((id) =>
          {
            const hash = location.hash
            return hash.includes('pages/detail/index') && hash.includes('scheduleId=' + id)
          }, scheduleId, { timeout: 20000 })
          await ctx.page.waitForSelector('.detail__body', { timeout: 20000 })
          assert.ok((await ctx.page.locator('.card__title').innerText()).includes(assets.courses[0].name),
            '课程详情展示对应排课的课程名')
          assert.equal(await ctx.page.locator('.bar__btn').count(), 1, '游客浏览详情仍显示预约操作条')
          assert.ok((await ctx.page.locator('.bar__btn').innerText()).includes('立即预约'),
            '未结束场次保留既有预约入口')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    }
  ]
}

function urlHasPage(route, pageNum)
{
  return new URL(route.request().url()).searchParams.get('pageNum') === String(pageNum)
}

/** 当前用例中按课种发起的教练排课第一页请求 */
function scheduleRequests(ctx, courseType)
{
  return requestsMatching(ctx, '/schedules').filter((url) => url.includes('courseType=' + courseType))
}
