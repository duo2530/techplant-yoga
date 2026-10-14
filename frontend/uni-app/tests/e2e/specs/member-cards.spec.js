const { assert, SkipError } = require('../lib/assert')
const { jsonRoute } = require('../lib/runner')

/**
 * 我的会员卡（pages/member-cards）端到端用例。
 *
 * 正常流程连真实后端（期望值来自同一个列表接口）；空列表、查询失败、失败后重试这类异常分支用响应拦截构造；
 * 未登录与页面恢复走真实交互路径。
 */

const CARD_API = '**/api/member-cards**'

function byCardNo(metrics, cardNo)
{
  const hit = metrics.find((item) => item.cardNo === cardNo)
  if (!hit)
  {
    throw new Error(`页面上没有卡号 ${cardNo} 的卡片`)
  }
  return hit
}

module.exports = {
  name: '我的会员卡（pages/member-cards）',
  cases: [
    {
      name: '列表：卡号、卡名、状态与分类角标与接口逐条一致，最新发卡在前',
      async run(ctx) {
        await ctx.openMemberCards()
        const expected = await ctx.fetchCards()
        if (!expected.rows.length) {
          throw new SkipError('种子会员名下没有会员卡，无法校验列表内容')
        }
        const metrics = await ctx.cardMetrics()
        assert.equal(metrics.length, expected.rows.length, '卡片数量应等于接口返回条数')
        expected.rows.forEach((row, index) => {
          const card = metrics[index]
          assert.equal(card.cardNo, row.cardNo, `第 ${index + 1} 张卡号`)
          assert.equal(card.name, row.cardName, `第 ${index + 1} 张卡名`)
          assert.equal(card.status, row.statusText, `第 ${index + 1} 张状态文案`)
          assert.equal(card.category, row.cardCategoryText, `第 ${index + 1} 张分类角标`)
        })
      }
    },
    {
      name: '次数卡：进度条比例 = 剩余次数/初始次数，初始次数带赠送时括注（购买+赠送）',
      async run(ctx) {
        await ctx.openMemberCards()
        const expected = await ctx.fetchCards()
        const timesRows = expected.rows.filter((row) => row.cardType === 1)
        if (!timesRows.length) {
          throw new SkipError('种子会员名下没有次数卡')
        }
        const metrics = await ctx.cardMetrics()
        for (const row of timesRows) {
          const card = byCardNo(metrics, row.cardNo)
          assert.equal(card.statLabels[0], '当前剩余次数', `${row.cardNo} 剩余次数标签`)
          assert.equal(card.statValues[0], `${row.remainingTimes}次`, `${row.cardNo} 剩余次数取值`)
          const expectLabel = row.giftTimes > 0 ? '初始次数(包含赠送)' : '初始次数'
          assert.equal(card.statLabels[1], expectLabel, `${row.cardNo} 初始次数标签`)
          const expectValue = row.giftTimes > 0
            ? `${row.totalTimes}(${row.purchaseTimes}+${row.giftTimes})次`
            : `${row.totalTimes}次`
          assert.equal(card.statValues[1], expectValue, `${row.cardNo} 初始次数取值`)
          assert.ok(card.barWidth, `${row.cardNo} 次数卡应有进度条`)
          const ratio = row.remainingTimes / row.totalTimes
          assert.close(card.fillWidth / card.barWidth, ratio, 0.03, `${row.cardNo} 进度条填充比例`)
        }
      }
    },
    {
      name: '期限卡（年卡）：全部门店通用、只给剩余天数、不出进度条',
      async run(ctx) {
        await ctx.openMemberCards()
        const expected = await ctx.fetchCards()
        const annualRows = expected.rows.filter((row) => row.cardType === 2)
        if (!annualRows.length) {
          throw new SkipError('种子会员名下没有期限卡')
        }
        const metrics = await ctx.cardMetrics()
        for (const row of annualRows) {
          const card = byCardNo(metrics, row.cardNo)
          assert.equal(card.category, '期限卡', `${row.cardNo} 分类角标`)
          assert.equal(card.store, row.storeScopeText, `${row.cardNo} 适用门店文案`)
          assert.equal(card.statLabels.join(','), '剩余天数', `${row.cardNo} 只应展示剩余天数`)
          assert.equal(card.barWidth, null, `${row.cardNo} 期限卡不应有进度条`)
          const expectDays = row.remainingDays === null
            ? (row.validEndTime ? '已过期' : '暂无')
            : `${row.remainingDays}天`
          assert.equal(card.statValues[0], expectDays, `${row.cardNo} 剩余天数取值`)
          const expectValid = row.validEndTime
            ? `有效期至 ${String(row.validEndTime).slice(0, 10)}`
            : '开卡后开始计算有效期'
          assert.equal(card.valid, expectValid, `${row.cardNo} 有效期文案`)
        }
      }
    },
    {
      name: '成功空列表：展示「暂无会员卡」，不出现失败提示',
      async run(ctx) {
        await ctx.openMemberCards({
          routes: [{ match: CARD_API, handler: jsonRoute({ total: 0, rows: [], code: 200, msg: '查询成功' }) }]
        })
        assert.equal(await ctx.count('.ys-empty'), 1, '应展示无卡空态')
        assert.equal(await ctx.count('.cards__error'), 0, '不应出现失败提示')
        assert.contains(await ctx.bodyText(), '暂无会员卡', '空态文案')
      }
    },
    {
      name: '查询失败：展示失败提示 + 重试入口，不误报为「暂无会员卡」',
      async run(ctx) {
        await ctx.openMemberCards({
          routes: [{ match: CARD_API, handler: jsonRoute({ code: 500, msg: '系统异常，请稍后重试' }) }]
        })
        assert.equal(await ctx.count('.cards__error'), 1, '应展示失败态')
        assert.equal(await ctx.count('.ys-empty'), 0, '失败不能显示无卡空态')
        assert.equal(await ctx.count('.card'), 0, '失败时不应有卡片')
        const text = await ctx.bodyText()
        assert.contains(text, '会员卡加载失败', '失败提示语')
        assert.contains(text, '重新加载', '重试入口')
      }
    },
    {
      name: '失败后重试：点「重新加载」恢复真实列表',
      async run(ctx) {
        let failed = 0
        await ctx.openMemberCards({
          routes: [{
            match: CARD_API,
            handler: (route) => {
              if (failed === 0) {
                failed += 1
                return jsonRoute({ code: 500, msg: '系统异常，请稍后重试' })(route)
              }
              return route.continue()
            }
          }]
        })
        assert.equal(await ctx.count('.cards__error'), 1, '首次查询失败应展示失败态')

        await ctx.page.locator('.cards__retry').click()
        await ctx.page.waitForSelector('.card', { timeout: 20000 })

        const expected = await ctx.fetchCards()
        assert.equal(await ctx.count('.cards__error'), 0, '重试成功后失败态消失')
        assert.equal(await ctx.count('.card'), expected.rows.length, '重试后卡片数量应与接口一致')
      }
    },
    {
      name: '未登录：跳登录页；返回后是登录提示，不误报为「暂无会员卡」',
      async run(ctx) {
        await ctx.openMemberCards({ session: 'none' })
        assert.contains(ctx.page.url(), 'quick-login', '未登录应被送到登录页')

        await ctx.page.goBack()
        await ctx.page.waitForSelector('.cards__error', { timeout: 20000 })
        assert.equal(await ctx.count('.ys-empty'), 0, '未登录不能显示无卡空态')
        assert.equal(await ctx.count('.card'), 0, '未登录不应有卡片')
        const text = await ctx.bodyText()
        assert.contains(text, '登录后查看我的会员卡', '未登录提示语')
        assert.contains(text, '去登录', '去登录入口')
      }
    },
    {
      name: '页面恢复：首屏只拉一次，离开再返回重新拉取',
      async run(ctx) {
        await ctx.openMemberCards()
        const before = ctx.memberCardRequests().length
        assert.equal(before, 1, '首屏应只请求一次会员卡列表')

        await ctx.leaveAndReturn()
        const after = ctx.memberCardRequests().length
        assert.equal(after, before + 1, '页面恢复（onShow）应重新拉取一次')

        const expected = await ctx.fetchCards()
        assert.equal(await ctx.count('.card'), expected.rows.length, '恢复后列表仍与接口一致')
      }
    },
    {
      name: '底部文案：一次加载完全部数据时显示「已经到底了」',
      async run(ctx) {
        const expected = await ctx.fetchCards()
        if (!expected.rows.length) {
          throw new SkipError('种子会员名下没有会员卡')
        }
        await ctx.openMemberCards()
        const footer = await ctx.page.locator('.cards__foot-text').allInnerTexts()
        if (expected.total <= 10) {
          assert.contains(footer.join(','), '已经到底了', '全部加载完的收尾文案')
        } else {
          assert.equal(footer.length, 0, '还有下一页时不应显示收尾文案')
        }
      }
    }
  ]
}
