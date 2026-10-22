const { assert } = require('../lib/assert')
const {
  apiAdminLogin,
  apiUpdateCoachLevelRaw,
  apiCreateRole,
  apiListRoles,
  apiDeleteRole,
  apiCreateUser,
  apiListUsers,
  apiDeleteUser
} = require('../lib/admin-api')

/**
 * 管理端金牌教练设置的最小端到端覆盖。
 *
 * 全部用例连接隔离的真实后端：登录、资料修改、等级读写都走真实接口；
 * 仅「保存失败不提示成功」用响应拦截构造失败分支（测试手册允许异常场景拦截响应）。
 * 每个用例自建前置状态（通过真实等级接口把测试教练置为指定等级），不依赖用例执行顺序。
 */

module.exports = {
  id: 'coach-level',
  name: '教练管理-金牌设置',
  cases: [
    {
      name: '管理员登录并进入教练管理',
      async run(helper)
      {
        const { context, page } = helper
        await helper.loginViaUi()

        const cookies = await context.cookies(helper.baseUrl)
        assert.ok(
          cookies.some((cookie) => cookie.name === 'Admin-Token' && cookie.value),
          '登录后未写入 Admin-Token Cookie'
        )
        assert.contains(await helper.bodyText(), '经营基础', '登录后侧边栏未出现「经营基础」菜单')

        const coachMenu = page.locator('.el-menu-item', { hasText: '教练管理' })
        if (!(await coachMenu.isVisible()))
        {
          await page.locator('.el-sub-menu__title', { hasText: '经营基础' }).click()
        }
        await coachMenu.waitFor({ state: 'visible', timeout: 10000 })
        await coachMenu.click()
        await page.waitForURL((url) => url.pathname === '/operation/coach', { timeout: 20000 })
        await page.getByPlaceholder('请输入教练姓名').waitFor({ state: 'visible', timeout: 20000 })

        await helper.searchCoach(helper.fixture.name)
        await helper.waitLevelDisplay(helper.fixture.name, '普通')
      }
    },
    {
      name: '设为金牌后列表与接口一致',
      async run(helper)
      {
        const name = helper.fixture.name
        await helper.setLevelViaApi(1)
        await helper.openCoachList({ searchName: name })
        await helper.waitLevelDisplay(name, '普通')

        await helper.clickLevelAction(name, '设为金牌')
        const confirmText = await helper.confirmDialog()
        assert.contains(confirmText, name, '二次确认未包含教练姓名')
        await helper.expectSuccessToast('设为金牌成功')
        await helper.waitLevelDisplay(name, '金牌')

        assert.equal(await helper.fetchLevel(), 2, '后端等级未持久化为金牌')
        const requests = helper.levelRequests()
        assert.equal(requests.length, 1, '等级接口调用次数不符')
        const levelUrl = new URL(requests[0].url)
        assert.equal(
          levelUrl.pathname,
          `/prod-api/admin/coaches/${helper.fixture.id}/level`,
          '等级接口路径不符'
        )
        assert.equal(levelUrl.searchParams.get('level'), '2', '等级接口参数应为 level=2')

        await helper.page.reload({ waitUntil: 'load' })
        await helper.searchCoach(name)
        await helper.waitLevelDisplay(name, '金牌')
      }
    },
    {
      name: '修改资料保持金牌等级',
      async run(helper)
      {
        const name = helper.fixture.name
        await helper.setLevelViaApi(2)
        await helper.openCoachList({ searchName: name })
        await helper.waitLevelDisplay(name, '金牌')

        const dialog = await helper.openEditDialog(name)
        const updatedIntro = `${helper.fixture.initialIntro}（E2E 已编辑）`
        await dialog.locator('textarea').fill(updatedIntro)
        await helper.submitDialog(dialog)
        await helper.expectSuccessToast('修改成功')

        const payload = helper.lastCoachUpdatePayload()
        assert.ok(
          !Object.prototype.hasOwnProperty.call(payload, 'level'),
          '资料表单不应提交 level 字段'
        )
        assert.equal(await helper.fetchLevel(), 2, '编辑资料后等级被改变')
        const detail = await helper.fetchDetail()
        assert.equal(detail.intro, updatedIntro, '简介未按提交内容更新')
        await helper.waitLevelDisplay(name, '金牌')
      }
    },
    {
      name: '取消金牌后恢复普通',
      async run(helper)
      {
        const name = helper.fixture.name
        await helper.setLevelViaApi(2)
        await helper.openCoachList({ searchName: name })
        await helper.waitLevelDisplay(name, '金牌')

        await helper.clickLevelAction(name, '取消金牌')
        const confirmText = await helper.confirmDialog()
        assert.contains(confirmText, '取消金牌', '二次确认未说明取消金牌')
        await helper.expectSuccessToast('取消金牌成功')
        await helper.waitLevelDisplay(name, '普通')

        assert.equal(await helper.fetchLevel(), 1, '后端等级未恢复为普通')
        const requests = helper.levelRequests()
        assert.equal(requests.length, 1, '等级接口调用次数不符')
        assert.equal(
          new URL(requests[0].url).searchParams.get('level'),
          '1',
          '等级接口参数应为 level=1'
        )
      }
    },
    {
      name: '等级保存失败不提示成功',
      async run(helper)
      {
        const name = helper.fixture.name
        await helper.setLevelViaApi(1)

        const invalid = await apiUpdateCoachLevelRaw(helper.session.token, helper.fixture.id, 3)
        assert.equal(invalid.code, 400, '非法等级应返回业务码 400')
        assert.equal(await helper.fetchLevel(), 1, '非法等级请求不应改变等级')

        await helper.page.route('**/prod-api/admin/coaches/*/level**', (route) => route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: 400, msg: '教练等级取值为 1 或 2' })
        }))

        await helper.openCoachList({ searchName: name })
        await helper.waitLevelDisplay(name, '普通')

        await helper.clickLevelAction(name, '设为金牌')
        await helper.confirmDialog()
        const errorText = await helper.expectErrorToast()
        assert.ok(errorText.length > 0, '保存失败应出现错误提示')
        await helper.assertNoSuccessToast('设为金牌成功')

        await helper.waitLevelDisplay(name, '普通')
        assert.equal(await helper.fetchLevel(), 1, '请求失败后等级不应改变')
      }
    },
    {
      name: '普通管理员可设置和取消金牌',
      async run(helper)
      {
        const name = helper.fixture.name
        const adminSession = helper.session
        const suffix = String(Date.now()).slice(-6)
        const roleKey = `e2e_coach_${suffix}`
        const userName = `e2e${suffix}`
        const password = 'E2eTest123'
        let roleId
        let userId
        let failure

        /** 创建成功但没能取到 ID 时，清理阶段按唯一标识兜底查询 */
        const findRoleId = async () =>
        {
          try
          {
            const roles = await apiListRoles(adminSession.token, { roleKey })
            const role = (roles.rows || []).find((item) => item.roleKey === roleKey)
            return role ? role.roleId : null
          }
          catch (error)
          {
            return null
          }
        }
        const findUserId = async () =>
        {
          try
          {
            const users = await apiListUsers(adminSession.token, { userName })
            const user = (users.rows || []).find((item) => item.userName === userName)
            return user ? user.userId : null
          }
          catch (error)
          {
            return null
          }
        }

        try
        {
          await apiCreateRole(adminSession.token, {
            roleName: `E2E教练管理${suffix}`,
            roleKey,
            roleSort: 99,
            status: '0',
            menuIds: [2000, 2003],
            remark: 'E2E 仅配置教练菜单'
          })
          roleId = await findRoleId()
          assert.ok(roleId, '新增的角色未出现在角色列表')

          await apiCreateUser(adminSession.token, {
            userName,
            nickName: 'E2E普通管理员',
            password,
            status: '0',
            roleIds: [roleId],
            remark: 'E2E 普通管理员账号'
          })
          userId = await findUserId()
          assert.ok(userId, '新增的用户未出现在用户列表')

          await helper.setLevelViaApi(1)
          const restricted = await apiAdminLogin({ username: userName, password })
          await helper.openCoachListAs(restricted, { searchName: name })
          await helper.waitLevelDisplay(name, '普通')

          const row = helper.coachRow(name)
          assert.equal(
            await row.getByRole('button', { name: '修改', exact: true }).count(),
            1,
            '有教练菜单权限的账号应能看到「修改」按钮'
          )
          assert.equal(
            await row.getByRole('button', { name: '设为金牌', exact: true }).count(),
            1,
            '普通管理员应能看到「设为金牌」'
          )
          await helper.clickLevelAction(name, '设为金牌')
          await helper.confirmDialog()
          await helper.expectSuccessToast('设为金牌成功')
          await helper.waitLevelDisplay(name, '金牌')
          assert.equal(await helper.fetchLevel(), 2, '普通管理员设置金牌后应持久化')

          await helper.clickLevelAction(name, '取消金牌')
          await helper.confirmDialog()
          await helper.expectSuccessToast('取消金牌成功')
          await helper.waitLevelDisplay(name, '普通')
          assert.equal(await helper.fetchLevel(), 1, '普通管理员取消金牌后应持久化')
        }
        catch (error)
        {
          failure = error
          throw error
        }
        finally
        {
          const cleanupErrors = []
          try
          {
            const targetUserId = userId || await findUserId()
            if (targetUserId)
            {
              await apiDeleteUser(adminSession.token, targetUserId)
            }
          }
          catch (error)
          {
            cleanupErrors.push(error)
          }
          try
          {
            const targetRoleId = roleId || await findRoleId()
            if (targetRoleId)
            {
              await apiDeleteRole(adminSession.token, targetRoleId)
            }
          }
          catch (error)
          {
            cleanupErrors.push(error)
          }
          cleanupErrors.forEach((error) => console.warn(`权限用例夹具清理失败：${error.message}`))
          if (!failure && cleanupErrors.length)
          {
            throw cleanupErrors[0]
          }
        }
      }
    }
  ]
}
