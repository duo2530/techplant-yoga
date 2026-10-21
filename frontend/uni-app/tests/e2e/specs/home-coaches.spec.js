const { assert, AssertionError } = require('../lib/assert')
const { createAdminFixture } = require('../lib/admin')
const { openHome, homeCoaches, requestsMatching, waitUntil } = require('../lib/coach-app')

/**
 * 首页「金牌教练」栏目端到端用例。
 *
 * 教练数据通过隔离后端的真实管理端接口准备（新建教练 + 设为金牌 + 取消金牌），
 * 首页展示结果与公开接口 GET /api/coaches/featured 的期望值对照；
 * 网络失败与空列表用响应拦截构造。
 */

module.exports = {
  name: '首页金牌教练',
  cases: [
    {
      name: '全量展示：超过三位金牌教练一次全部展示，普通教练不出现',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const created = []
          for (let i = 0; i < 4; i++)
          {
            created.push(await fixture.createCoach({
              name: `E2E金牌${fixture.runSuffix}-${i}`,
              intro: `金牌简介 ${i}`
            }))
          }
          const normal = await fixture.createCoach({
            name: `E2E普通${fixture.runSuffix}`,
            intro: '普通教练简介',
            featured: false
          })

          const remote = await fixture.publicFeatured()
          assert.ok(remote.length > 3, '隔离环境金牌教练应超过三位')
          for (const coach of created)
          {
            assert.ok(remote.some((item) => String(item.id) === coach.id), `公开接口应返回 ${coach.name}`)
          }
          assert.ok(!remote.some((item) => String(item.id) === normal.id), '普通教练不应出现在金牌列表')

          await openHome(ctx)
          const cards = await homeCoaches(ctx)
          assert.equal(cards.length, remote.length, '首页应一次展示全部金牌教练，不做截断')
          assert.ok(cards.length > 3, '金牌卡片数量应大于三')
          assert.equal(cards.map((item) => item.name).join(','), remote.map((item) => item.name).join(','),
            '首页卡片顺序应与公开接口一致')

          for (const coach of created)
          {
            assert.ok(cards.some((item) => item.name === coach.name), `首页应展示金牌教练 ${coach.name}`)
          }
          assert.ok(!cards.some((item) => item.name === normal.name), '普通教练不得出现在金牌栏目')

          const first = cards.filter((item) => item.name === created[0].name)[0]
          assert.equal(first.intro, '金牌简介 0', '卡片应展示教练简介')
          assert.ok(!(await ctx.bodyText()).includes('更多教练'), '金牌栏目不设「更多教练」入口')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '资料不完整：头像加载失败用中性占位，简介为空显示「暂无介绍」',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const tag = fixture.runSuffix
          const avatarA = `${ctx.h5Base}/static/images/home/coach1-avatar.jpg?e2e=${tag}-ok`
          const avatarB = `${ctx.h5Base}/static/images/home/coach2-avatar.jpg?e2e=${tag}-fail`
          const coachA = await fixture.createCoach({
            name: `E2E有头像${tag}`,
            avatarUrl: avatarA,
            intro: '有头像的教练'
          })
          const coachB = await fixture.createCoach({
            name: `E2E坏头像${tag}`,
            avatarUrl: avatarB,
            intro: ''
          })

          // 只拦截第二张头像，先保证另一张真实头像能加载，证明拦截场景确实来自图片失败
          await openHome(ctx, {
            routes: [{
              match: (url) => url.pathname.endsWith('/coach2-avatar.jpg') && url.searchParams.get('e2e') === `${tag}-fail`,
              handler: (route) => route.abort('failed')
            }]
          })
          await ctx.page.waitForFunction((name) =>
          {
            const cards = Array.from(document.querySelectorAll('.home-coach'))
            const card = cards.filter((item) =>
            {
              const node = item.querySelector('.home-coach__name')
              return !!node && node.innerText.includes(name)
            })[0]
            return !!card && !!card.querySelector('.home-coach__avatar--empty')
          }, coachB.name, { timeout: 20000 })

          const cards = await homeCoaches(ctx)
          const cardA = cards.filter((item) => item.name === coachA.name)[0]
          const cardB = cards.filter((item) => item.name === coachB.name)[0]
          assert.ok(cardA, '应展示有头像的金牌教练卡片')
          assert.ok(cardA.hasAvatar, '未拦截的头像应正常加载，证明失败占位来自图片加载失败')
          assert.ok(!cardA.avatarEmpty, '有头像的卡片不使用占位')
          assert.ok(cardB, '应展示头像加载失败的教练卡片')
          assert.ok(cardB.avatarEmpty, '头像加载失败应使用中性占位')
          assert.equal(cardB.intro, '暂无介绍', '简介为空时显示「暂无介绍」')
          assert.ok(await ctx.page.locator('.home-coach').count() >= 2, '卡片仍可展示与点击')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '后台取消金牌后重新进入首页，刷新列表不再展示该教练',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const coach = await fixture.createCoach({
            name: `E2E取消${fixture.runSuffix}`,
            intro: '取消金牌用例'
          })

          await openHome(ctx)
          await ctx.page.waitForFunction((name) =>
            Array.from(document.querySelectorAll('.home-coach__name'))
              .some((node) => node.innerText.includes(name)), coach.name, { timeout: 20000 })
          const featuredRequestsBefore = requestsMatching(ctx, '/api/coaches/featured').length
          assert.ok(featuredRequestsBefore >= 1, '进入首页应请求金牌列表')

          await fixture.setCoachLevel(coach.id, 1)

          // 离开首页再返回，触发 onShow 重新拉取金牌列表
          await ctx.page.goto(ctx.h5Base + '/#/pages/messages/index', { waitUntil: 'load' })
          await ctx.page.waitForTimeout(800)
          await ctx.page.goBack()
          // 先确认刷新请求确实发出并返回，再断言列表移除该教练
          await waitUntil(() => requestsMatching(ctx, '/api/coaches/featured').length > featuredRequestsBefore,
            '返回首页后未重新请求金牌列表')
          await ctx.page.waitForFunction((name) =>
          {
            if (!document.querySelector('.home-coaches'))
            {
              return false
            }
            return !Array.from(document.querySelectorAll('.home-coach__name'))
              .some((node) => node.innerText.includes(name))
          }, coach.name, { timeout: 20000 })

          const remote = await fixture.publicFeatured()
          assert.ok(!remote.some((item) => String(item.id) === coach.id), '公开接口不应再返回已取消金牌的教练')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '网络失败：栏目显示加载失败，重试后展示列表且门店卡不受影响',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const coach = await fixture.createCoach({
            name: `E2E重试${fixture.runSuffix}`,
            intro: '网络重试用例'
          })
          const stores = await fixture.publicStores()
          let failedOnce = false

          await openHome(ctx, {
            routes: [{
              match: (url) => url.pathname === '/api/coaches/featured',
              handler: async (route) =>
              {
                if (!failedOnce)
                {
                  failedOnce = true
                  await route.abort('failed')
                  return
                }
                await route.continue()
              }
            }]
          })

          await ctx.page.waitForFunction(() => document.body.innerText.includes('金牌教练加载失败'),
            null, { timeout: 20000 })
          assert.ok(await ctx.page.locator('.home-coaches__retry').count() === 1, '失败状态应提供重试入口')

          await ctx.page.locator('.home-coaches__retry').click()
          await ctx.page.waitForFunction((name) =>
            Array.from(document.querySelectorAll('.home-coach__name'))
              .some((node) => node.innerText.includes(name)), coach.name, { timeout: 20000 })

          if (stores.length)
          {
            assert.equal(await ctx.page.locator('.store').count(), 1, '金牌列表失败/重试不影响门店卡')
            assert.ok(await ctx.page.locator('.store__name').innerText(), '门店卡内容应仍可见')
          }
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '空列表：接口成功返回空数组时显示「暂无金牌教练」，不插入模拟人物',
      async run(ctx)
      {
        await openHome(ctx, {
          routes: [{
            match: (url) => url.pathname === '/api/coaches/featured',
            handler: (route) => route.fulfill({
              status: 200,
              contentType: 'application/json',
              body: JSON.stringify({ code: 200, msg: '查询成功', data: [] })
            })
          }]
        })

        await ctx.page.waitForFunction(() => document.body.innerText.includes('暂无金牌教练'),
          null, { timeout: 20000 })
        assert.equal(await ctx.page.locator('.home-coach').count(), 0, '空列表时不渲染任何教练卡片')
      }
    },
    {
      name: '换店：门店卡更新后金牌列表仍按平台范围展示',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const coach = await fixture.createCoach({
            name: `E2E换店${fixture.runSuffix}`,
            intro: '换店用例'
          })

          await openHome(ctx)
          await ctx.page.waitForFunction((name) =>
            Array.from(document.querySelectorAll('.home-coach__name'))
              .some((node) => node.innerText.includes(name)), coach.name, { timeout: 20000 })

          const before = await homeCoaches(ctx)
          const storeName = (await ctx.page.locator('.store__name').innerText()).trim()

          await ctx.page.locator('.store__change').click()
          await ctx.page.waitForSelector('.picker__panel', { timeout: 20000 })
          try
          {
            await ctx.page.waitForFunction(() => document.querySelectorAll('.pitem').length >= 2,
              null, { timeout: 20000 })
          }
          catch (error)
          {
            // 前置不满足属于环境问题，直接失败并说明，不降级成跳过
            throw new AssertionError('前置不满足：隔离环境门店列表少于两个，无法验证换店后金牌列表')
          }
          const pickerNames = (await ctx.page.locator('.pitem__name').allInnerTexts()).map((text) => text.trim())
          const target = pickerNames.filter((name) => name && name !== storeName)[0]
          if (!target)
          {
            throw new AssertionError('前置不满足：隔离环境没有第二个可选门店')
          }

          await ctx.page.locator('.pitem', { hasText: target }).first().click()
          await ctx.page.waitForFunction((name) =>
          {
            const node = document.querySelector('.store__name')
            return !!node && node.innerText.trim() === name
          }, target, { timeout: 20000 })

          const after = await homeCoaches(ctx)
          assert.ok(after.length > 0, '换店后金牌列表不应为空')
          const remote = await fixture.publicFeatured()
          assert.equal(after.map((item) => item.name).join(','), remote.map((item) => item.name).join(','),
            '换店后金牌列表应与平台范围的公开接口一致，不随门店过滤')
          assert.ok(requestsMatching(ctx, '/api/coaches/featured').length >= 1,
            '首页确实请求过金牌列表接口')
          assert.equal(before.length, after.length, '换店前后金牌列表数量不变')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '金牌卡片点击：真实导航进入所选教练的约教练页',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const coachA = await fixture.createCoach({
            name: `E2E点击A${fixture.runSuffix}`,
            intro: '点击 A 的简介'
          })
          const coachB = await fixture.createCoach({
            name: `E2E点击B${fixture.runSuffix}`,
            intro: '点击 B 的简介'
          })

          await openHome(ctx)
          await ctx.page.waitForFunction((name) =>
            Array.from(document.querySelectorAll('.home-coach__name'))
              .some((node) => node.innerText.includes(name)), coachB.name, { timeout: 20000 })

          await ctx.page.locator('.home-coach', { hasText: coachB.name }).first().click()
          await ctx.page.waitForFunction((id) =>
          {
            const hash = location.hash
            return hash.includes('pages/coaches/index') && hash.includes('coachId=' + id)
          }, coachB.id, { timeout: 20000 })
          await ctx.page.waitForFunction((name) =>
          {
            const node = document.querySelector('.profile__name')
            return !!node && node.innerText.trim() === name
          }, coachB.name, { timeout: 20000 })

          const profileName = (await ctx.page.locator('.profile__name').innerText()).trim()
          assert.equal(profileName, coachB.name, '进入的是被点击教练的资料页')
          assert.ok((await ctx.page.locator('body').innerText()).includes('点击 B 的简介'), '展示对应教练简介')
          assert.ok(!(await ctx.page.locator('body').innerText()).includes('点击 A 的简介'),
            '不展示其他教练资料')
          assert.ok(!(await ctx.page.locator('body').innerText()).includes(coachA.name),
            '不展示未被点击的教练姓名')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '首页无门店：门店区空态时金牌列表仍展示，且可进入教练资料页',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const coach = await fixture.createCoach({
            name: `E2E无门店${fixture.runSuffix}`,
            intro: '首页无门店时的教练'
          })
          await openHome(ctx, {
            routes: [{
              match: (url) => url.pathname === '/api/stores',
              handler: (route) => route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ code: 200, msg: '查询成功', rows: [], total: 0 })
              })
            }]
          })

          await ctx.page.waitForFunction(() => document.body.innerText.includes('暂无可选门店'),
            null, { timeout: 20000 })
          assert.equal(await ctx.page.locator('.store').count(), 0, '无门店时不渲染门店卡')
          await ctx.page.waitForFunction((name) =>
            Array.from(document.querySelectorAll('.home-coach__name'))
              .some((node) => node.innerText.includes(name)), coach.name, { timeout: 20000 })

          await ctx.page.locator('.home-coach', { hasText: coach.name }).first().click()
          await ctx.page.waitForFunction((id) =>
            location.hash.includes('coachId=' + id), coach.id, { timeout: 20000 })
          await ctx.page.waitForFunction((name) =>
          {
            const node = document.querySelector('.profile__name')
            return !!node && node.innerText.trim() === name
          }, coach.name, { timeout: 20000 })
          await ctx.page.waitForFunction(() => document.body.innerText.includes('请先在首页选择门店'),
            null, { timeout: 20000 })
          assert.equal(requestsMatching(ctx, '/schedules').length, 0, '无门店时不请求排课')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    }
  ]
}
