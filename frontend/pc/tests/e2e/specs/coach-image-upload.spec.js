const crypto = require('crypto')
const { assert } = require('../lib/assert')
const { apiUpdateCoach } = require('../lib/admin-api')

/**
 * 教练头像上传到 OSS 的端到端覆盖：真实页面上传 → 隔离后端 /admin/images → OSS 公网 URL 回显与保存。
 *
 * 需要后端配置 OSS 访问密钥；本进程设置 YOGA_OSS_ACCESS_KEY_ID／YOGA_OSS_ACCESS_KEY_SECRET 时，
 * 用例结束后删除本次上传的对象，未设置时打印需人工删除的对象地址。
 */

const OSS_BUCKET = process.env.E2E_OSS_BUCKET || 'techplant-yoga-images'
const OSS_PUBLIC_BASE_URL = (process.env.E2E_OSS_PUBLIC_BASE_URL ||
  `https://${OSS_BUCKET}.oss-cn-shanghai.aliyuncs.com`).replace(/\/+$/, '')

/** 1×1 像素 PNG */
const PNG_1X1 = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
  'base64'
)

async function deleteOssObject(url)
{
  const accessKeyId = process.env.YOGA_OSS_ACCESS_KEY_ID
  const accessKeySecret = process.env.YOGA_OSS_ACCESS_KEY_SECRET
  if (!accessKeyId || !accessKeySecret)
  {
    console.warn(`      未设置 OSS 访问密钥，需人工删除：${url}`)
    return
  }
  const key = url.slice(OSS_PUBLIC_BASE_URL.length + 1)
  const date = new Date().toUTCString()
  const signature = crypto.createHmac('sha1', accessKeySecret)
    .update(`DELETE\n\n\n${date}\n/${OSS_BUCKET}/${key}`)
    .digest('base64')
  const response = await fetch(url, {
    method: 'DELETE',
    headers: { date, authorization: `OSS ${accessKeyId}:${signature}` },
    signal: AbortSignal.timeout(15000)
  })
  if (response.status !== 204)
  {
    console.warn(`      删除 OSS 对象失败（HTTP ${response.status}），需人工删除：${url}`)
  }
}

module.exports = {
  id: 'coach-image-upload',
  name: '教练管理-头像上传到 OSS',
  cases: [
    {
      name: '上传头像回显 OSS 地址，保存后列表与详情一致',
      async run(helper)
      {
        const { page, fixture, session } = helper
        const original = await helper.fetchDetail()
        await helper.openCoachList({ searchName: fixture.name })
        const dialog = await helper.openEditDialog(fixture.name)

        const avatarItem = dialog.locator('.el-form-item', { hasText: '头像' }).first()
        const uploadResponse = page.waitForResponse((response) =>
          response.request().method() === 'POST' && new URL(response.url()).pathname === '/prod-api/admin/images')
        await avatarItem.locator('input[type="file"]').setInputFiles({
          name: 'avatar.png',
          mimeType: 'image/png',
          buffer: PNG_1X1
        })
        const body = await (await uploadResponse).json()
        assert.equal(body.code, 200, `上传接口失败：${JSON.stringify(body)}`)
        const url = body.url
        try
        {
          assert.ok(url.startsWith(OSS_PUBLIC_BASE_URL + '/images/'), `返回地址不在 OSS 公网前缀下：${url}`)

          const preview = avatarItem.locator('.el-upload-list__item img')
          await preview.waitFor({ state: 'visible', timeout: 10000 })
          assert.equal(await preview.getAttribute('src'), url, '上传后预览图应直接使用 OSS 地址')
          await page.waitForFunction((img) => img.complete && img.naturalWidth > 0, await preview.elementHandle(),
            { timeout: 15000 })

          await helper.submitDialog(dialog)
          await helper.expectSuccessToast('修改成功')
          const detail = await helper.fetchDetail()
          assert.equal(detail.avatarUrl, url, '后端保存的头像应为 OSS 绝对地址')

          await helper.openCoachList({ searchName: fixture.name })
          const listImage = helper.coachRow(fixture.name).locator('.el-image img')
          await listImage.waitFor({ state: 'visible', timeout: 10000 })
          assert.equal(await listImage.getAttribute('src'), url, '列表头像应使用 OSS 地址')
        }
        finally
        {
          await apiUpdateCoach(session.token, fixture.id, {
            name: original.name,
            avatarUrl: original.avatarUrl,
            intro: original.intro,
            phone: original.phone,
            gallery: original.gallery || []
          })
          await deleteOssObject(url)
        }
      }
    }
  ]
}
