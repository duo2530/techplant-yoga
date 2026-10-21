const { assert } = require('../lib/assert')
const { createAdminFixture } = require('../lib/admin')
const {
  openCoach, requestsMatching, profileText, gallerySources, openGalleryPreview, previewState, waitPreviewCurrent
} = require('../lib/coach-app')

/**
 * 约教练页（单教练资料）端到端用例。
 *
 * 正常流程连隔离后端：教练资料、相册、无门店提示与 404 都以真实接口结果为准；
 * 网络失败与「无门店」用响应拦截构造，覆盖失败重试与资料仍可见的分支。
 */

module.exports = {
  name: '约教练页（教练资料）',
  cases: [
    {
      name: '不同 coachId 打开对应教练的真实资料与相册',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const avatarA = ctx.h5Base + '/static/images/home/coach1-avatar.jpg'
          const galleryA = [
            ctx.h5Base + '/static/images/home/coach1-avatar.jpg',
            ctx.h5Base + '/static/images/home/coach2-avatar.jpg'
          ]
          const coachA = await fixture.createCoach({
            name: `E2E资料A${fixture.runSuffix}`,
            avatarUrl: avatarA,
            intro: '资料 A 的简介',
            gallery: galleryA
          })
          const coachB = await fixture.createCoach({
            name: `E2E资料B${fixture.runSuffix}`,
            avatarUrl: ctx.h5Base + '/static/images/home/coach2-avatar.jpg',
            intro: '资料 B 的简介'
          })

          await openCoach(ctx, coachA.id)
          await ctx.page.waitForFunction((name) =>
          {
            const node = document.querySelector('.profile__name')
            return !!node && node.innerText.trim() === name
          }, coachA.name, { timeout: 20000 })
          const profileA = await profileText(ctx)
          assert.equal(profileA.name, coachA.name, '应按 coachId 展示对应教练姓名')
          assert.equal(profileA.intro, '资料 A 的简介', '资料页应展示教练简介')
          assert.equal(profileA.galleryCount, 2, '资料页应展示相册图片且保持接口顺序')

          const sources = await gallerySources(ctx)
          assert.equal(sources.length, galleryA.length, '相册缩略图数量应与接口一致')
          assert.equal(sources[0], galleryA[0], '第一张相册与接口顺序一致')
          assert.equal(sources[1], galleryA[1], '第二张相册与接口顺序一致')

          await openGalleryPreview(ctx, 1)
          await waitPreviewCurrent(ctx, 1)
          const preview = await previewState(ctx)
          assert.ok(preview, '点击相册图片应打开图片预览')
          assert.equal(preview.current, 1, '预览当前图应是点击的第二张相册图')
          assert.contains(preview.images[1], 'coach2-avatar', '预览图与点击的相册图一致')
          assert.equal(await ctx.page.locator('.profile__avatar--empty').count(), 0, '有头像时不显示中性占位')

          await openCoach(ctx, coachB.id)
          // 同一路由只改变 query 时浏览器不会重建页面，reload 模拟按新 coachId 重新打开链接
          await ctx.page.reload({ waitUntil: 'load' })
          await ctx.page.waitForSelector('uni-page-body', { timeout: 20000 })
          await ctx.page.waitForFunction((name) =>
          {
            const node = document.querySelector('.profile__name')
            return !!node && node.innerText.trim() === name
          }, coachB.name, { timeout: 20000 })
          const profileB = await profileText(ctx)
          assert.equal(profileB.name, coachB.name, '切换 coachId 后应展示另一位教练的资料')
          assert.equal(profileB.intro, '资料 B 的简介', '另一位教练的简介应与其 ID 对应')
          assert.equal(profileB.galleryCount, 0, '该教练没有相册')
          assert.ok((await ctx.page.locator('body').innerText()).includes('暂无教练相册'),
            '相册为空时显示「暂无教练相册」')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '空资料：中性头像、简介与相册空文案，正常门店下显示「暂无排课」',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const coach = await fixture.createCoach({
            name: `E2E空资料${fixture.runSuffix}`,
            intro: '',
            avatarUrl: null,
            gallery: [],
            featured: false
          })
          const stores = await fixture.publicStores()

          await openCoach(ctx, coach.id)
          await ctx.page.waitForFunction((name) =>
          {
            const node = document.querySelector('.profile__name')
            return !!node && node.innerText.trim() === name
          }, coach.name, { timeout: 20000 })

          assert.equal(await ctx.page.locator('.profile__avatar--empty').count(), 1, '无头像时使用中性占位')
          const body = await ctx.page.locator('body').innerText()
          assert.ok(body.includes('暂无教练简介'), '简介为空时显示「暂无教练简介」')
          assert.ok(body.includes('暂无教练相册'), '相册为空时显示「暂无教练相册」')

          if (stores.length)
          {
            await ctx.page.waitForFunction(() => document.body.innerText.includes('当前门店 · 未来14天'),
              null, { timeout: 20000 })
            await ctx.page.waitForFunction(() => document.body.innerText.includes('暂无排课'),
              null, { timeout: 20000 })
          }
          else
          {
            assert.ok(body.includes('请先在首页选择门店'), '没有门店时提示先选择门店')
          }
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '缺失或非法 coachId：每个场景真实重新打开后显示参数错误，且不新增教练请求',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const coach = await fixture.createCoach({
            name: `E2E参数校验${fixture.runSuffix}`,
            intro: '参数校验前的合法教练',
            featured: false
          })

          // 先打开合法教练页面：后续非法场景必须在「已有页面实例」上真正重新加载
          await openCoach(ctx, coach.id)
          await ctx.page.waitForFunction((name) =>
          {
            const node = document.querySelector('.profile__name')
            return !!node && node.innerText.trim() === name
          }, coach.name, { timeout: 20000 })
          const baseline = requestsMatching(ctx, '/api/coaches/').length
          assert.ok(baseline >= 1, '合法教练页面应请求教练详情')

          const scenarios = [
            { label: '缺失 coachId', suffix: '' },
            { label: '非数字 coachId', suffix: '?coachId=abc' },
            { label: '零值 coachId', suffix: '?coachId=0' },
            { label: '20 位 coachId', suffix: '?coachId=9999999999999999999' },
            { label: '超出 Long 的 19 位 coachId', suffix: '?coachId=9223372036854775808' }
          ]

          for (const scenario of scenarios)
          {
            await ctx.page.goto(ctx.h5Base + '/#/pages/coaches/index' + scenario.suffix, { waitUntil: 'load' })
            // 同一路由只改 hash query 不会重建页面，reload 保证按新参数重新进入页面
            await ctx.page.reload({ waitUntil: 'load' })
            await ctx.page.waitForSelector('uni-page-body', { timeout: 20000 })
            await ctx.page.waitForFunction(() => document.body.innerText.includes('参数错误'),
              null, { timeout: 20000 })
            // 留出潜在请求的调度时间，再断言请求数没有增长
            await ctx.page.waitForTimeout(300)

            assert.equal(requestsMatching(ctx, '/api/coaches/').length, baseline,
              `${scenario.label} 不应新增教练详情或排课请求`)
            const backText = (await ctx.page.locator('.coach-state__btn').innerText()).trim()
            assert.contains(backText, '返回首页', `${scenario.label} 应提供返回入口`)
            const body = await ctx.page.locator('body').innerText()
            assert.ok(!body.includes(coach.name), `${scenario.label} 不应展示其他教练资料`)
          }
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '教练已删除：提示「教练不存在或已被删除」并提供返回入口',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const coach = await fixture.createCoach({
            name: `E2E删除${fixture.runSuffix}`,
            intro: '删除用例',
            featured: false
          })
          await fixture.deleteCoach(coach.id)
          fixture.created.coaches = fixture.created.coaches.filter((id) => id !== coach.id)

          await openCoach(ctx, coach.id)
          await ctx.page.waitForFunction(() => document.body.innerText.includes('教练不存在或已被删除'),
            null, { timeout: 20000 })

          const backText = (await ctx.page.locator('.coach-state__btn').innerText()).trim()
          assert.ok(backText.includes('返回首页'), '404 状态应提供返回入口')
          const body = await ctx.page.locator('body').innerText()
          assert.ok(!body.includes('暂无教练相册'), '404 时不展示其他教练资料')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '详情请求失败：显示加载失败与重试，不误报教练不存在',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const coach = await fixture.createCoach({
            name: `E2E详情失败${fixture.runSuffix}`,
            intro: '详情失败用例'
          })
          let failedOnce = false

          await openCoach(ctx, coach.id, {
            routes: [{
              match: (url) => /^\/api\/coaches\/\d+$/.test(url.pathname),
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

          await ctx.page.waitForFunction(() => document.body.innerText.includes('加载失败'),
            null, { timeout: 20000 })
          const failedBody = await ctx.page.locator('body').innerText()
          assert.ok(!failedBody.includes('教练不存在或已被删除'), '网络失败不能误报教练不存在')

          await ctx.page.locator('.coach-state__btn').click()
          await ctx.page.waitForFunction((name) =>
          {
            const node = document.querySelector('.profile__name')
            return !!node && node.innerText.trim() === name
          }, coach.name, { timeout: 20000 })
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '无有效门店：教练资料与相册仍可见，提示「请先在首页选择门店」且不请求排课',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const coach = await fixture.createCoach({
            name: `E2E无门店${fixture.runSuffix}`,
            avatarUrl: ctx.h5Base + '/static/images/home/coach1-avatar.jpg',
            intro: '无门店时的教练简介',
            gallery: [ctx.h5Base + '/static/images/home/coach1-avatar.jpg']
          })

          await openCoach(ctx, coach.id, {
            routes: [{
              match: (url) => url.pathname === '/api/stores',
              handler: (route) => route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ code: 200, msg: '查询成功', rows: [], total: 0 })
              })
            }]
          })

          await ctx.page.waitForFunction((name) =>
          {
            const node = document.querySelector('.profile__name')
            return !!node && node.innerText.trim() === name
          }, coach.name, { timeout: 20000 })
          await ctx.page.waitForFunction(() => document.body.innerText.includes('请先在首页选择门店'),
            null, { timeout: 20000 })

          const profile = await profileText(ctx)
          assert.equal(profile.intro, '无门店时的教练简介', '无门店时教练简介仍可见')
          assert.equal(profile.galleryCount, 1, '无门店时教练相册仍可见')
          assert.equal(requestsMatching(ctx, '/schedules').length, 0, '无有效门店时不应请求排课')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '取消金牌后已打开的教练资料仍可访问',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const coach = await fixture.createCoach({
            name: `E2E取消金牌${fixture.runSuffix}`,
            intro: '取消金牌后资料仍可访问'
          })

          await openCoach(ctx, coach.id)
          await ctx.page.waitForFunction((name) =>
          {
            const node = document.querySelector('.profile__name')
            return !!node && node.innerText.trim() === name
          }, coach.name, { timeout: 20000 })

          await fixture.setCoachLevel(coach.id, 1)
          await ctx.page.reload({ waitUntil: 'load' })
          await ctx.page.waitForSelector('uni-page-body', { timeout: 20000 })
          await ctx.page.waitForFunction((name) =>
          {
            const node = document.querySelector('.profile__name')
            return !!node && node.innerText.trim() === name
          }, coach.name, { timeout: 20000 })

          const body = await ctx.page.locator('body').innerText()
          assert.ok(!body.includes('教练不存在或已被删除'), '取消金牌不应使教练资料链接失效')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '详情接口返回 400：显示参数错误而不是可重试失败',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const coach = await fixture.createCoach({
            name: `E2E详情400${fixture.runSuffix}`,
            intro: '详情 400 用例',
            featured: false
          })
          await openCoach(ctx, coach.id, {
            routes: [{
              match: (url) => /^\/api\/coaches\/\d+$/.test(url.pathname),
              handler: (route) => route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ code: 400, msg: '参数[coachId]格式非法' })
              })
            }]
          })

          await ctx.page.waitForFunction(() => document.body.innerText.includes('参数错误'),
            null, { timeout: 20000 })
          const body = await ctx.page.locator('body').innerText()
          assert.ok(!body.includes('加载失败'), '400 不能落到网络失败状态')
          assert.ok(!body.includes('教练不存在或已被删除'), '400 不能误报教练不存在')
          assert.contains((await ctx.page.locator('.coach-state__btn').innerText()).trim(), '返回首页',
            '参数错误提供返回入口')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    },
    {
      name: '门店信息加载失败：显示门店失败与重试，不误报「请先在首页选择门店」，恢复后再拉排课',
      async run(ctx)
      {
        const fixture = await createAdminFixture()
        try
        {
          const coach = await fixture.createCoach({
            name: `E2E门店失败${fixture.runSuffix}`,
            intro: '门店加载失败用例',
            featured: false
          })
          let storeRequests = 0

          await openCoach(ctx, coach.id, {
            routes: [{
              match: (url) => url.pathname === '/api/stores',
              handler: async (route) =>
              {
                storeRequests++
                if (storeRequests === 1)
                {
                  await route.abort('failed')
                  return
                }
                await route.continue()
              }
            }]
          })

          await ctx.page.waitForFunction((name) =>
          {
            const node = document.querySelector('.profile__name')
            return !!node && node.innerText.trim() === name
          }, coach.name, { timeout: 20000 })
          await ctx.page.waitForFunction(() => document.body.innerText.includes('门店信息加载失败'),
            null, { timeout: 20000 })
          assert.ok(!(await ctx.page.locator('body').innerText()).includes('请先在首页选择门店'),
            '门店接口失败不能误报为未选择门店')
          assert.equal(requestsMatching(ctx, '/schedules').length, 0, '门店未就绪时不请求排课')

          await ctx.page.locator('.window__retry').click()
          await ctx.page.waitForFunction(() => document.body.innerText.includes('当前门店 · 未来14天'),
            null, { timeout: 20000 })
          await ctx.page.waitForFunction(() => document.body.innerText.includes('暂无排课'),
            null, { timeout: 20000 })
          assert.ok(requestsMatching(ctx, '/schedules').length >= 1, '门店恢复后应请求排课')
        }
        finally
        {
          await fixture.cleanup()
        }
      }
    }
  ]
}
