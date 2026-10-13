import { defineStore } from 'pinia'
import { ref } from 'vue'
import config from '@/config'
import storage from '@/utils/storage'
import constant from '@/utils/constant'
import { isHttp, isEmpty } from "@/utils/validate"
import { getInfo, login, logout } from '@/api/login'
import { getToken, removeToken, setToken } from '@/utils/auth'
import { clearSessionStorage, isSessionValid, saveSession } from '@/utils/session'
// 默认头像：若依脚手架原来用 /static/images/profile.jpg，本项目换成品牌资产（mock 阶段页面不依赖它）
import defAva from '@/static/images/mine/avatar.jpg'

const baseUrl = config.baseUrl

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const id = ref(storage.get(constant.id))
  const name = ref(storage.get(constant.name))
  const avatar = ref(storage.get(constant.avatar))
  const roles = ref(storage.get(constant.roles))
  const permissions = ref(storage.get(constant.permissions))

  const SET_TOKEN = (val) => {
    token.value = val
  }
  const SET_ID = (val) => {
    id.value = val
    storage.set(constant.id, val)
  }
  const SET_NAME = (val) => {
    name.value = val
    storage.set(constant.name, val)
  }
  const SET_AVATAR = (val) => {
    avatar.value = val
    storage.set(constant.avatar, val)
  }
  const SET_ROLES = (val) => {
    roles.value = val
    storage.set(constant.roles, val)
  }
  const SET_PERMISSIONS = (val) => {
    permissions.value = val
    storage.set(constant.permissions, val)
  }

  // 登录
  const loginAction = (userInfo) => {
    const username = userInfo.username.trim()
    const password = userInfo.password
    const code = userInfo.code
    const uuid = userInfo.uuid
    return new Promise((resolve, reject) => {
      login(username, password, code, uuid).then(res => {
        setToken(res.token)
        SET_TOKEN(res.token)
        resolve()
      }).catch(error => {
        reject(error)
      })
    })
  }

  // 获取用户信息
  const getInfoAction = () => {
    return new Promise((resolve, reject) => {
      getInfo().then(res => {
        const user = res.user
        let avatar = user.avatar || ""
        if (!isHttp(avatar)) {
          avatar = (isEmpty(avatar)) ? defAva : baseUrl + avatar
        }
        const userid = (isEmpty(user) || isEmpty(user.userId)) ? "" : user.userId
        const username = (isEmpty(user) || isEmpty(user.userName)) ? "" : user.userName
        if (res.roles && res.roles.length > 0) {
          SET_ROLES(res.roles)
          SET_PERMISSIONS(res.permissions)
        } else {
          SET_ROLES(['ROLE_DEFAULT'])
        }
		SET_ID(userid)
        SET_NAME(username)
        SET_AVATAR(avatar)
        resolve(res)
      }).catch(error => {
        reject(error)
      })
    })
  }

  // 退出系统
  const logOutAction = () => {
    return new Promise((resolve, reject) => {
      logout(token.value).then(() => {
        SET_TOKEN('')
        SET_ROLES([])
        SET_PERMISSIONS([])
        removeToken()
        storage.clean()
        resolve()
      }).catch(error => {
        reject(error)
      })
    })
  }

  /**
   * 写入会员登录态（登录成功后调用）
   *
   * 存储层（token + 会员展示信息 + 服务端 expireTime）交给 `@/utils/session` 的 saveSession，
   * store 状态同步在这里做，避免页面各写一遍（调用方先校验返回体完整性）。
   */
  const applySession = (data, phone) => {
    saveSession(data, phone)
    SET_TOKEN(data.token)
    SET_ID(data.memberId)
    SET_NAME(data.memberName)
  }

  /**
   * 统一清理本地登录态（401、会话自然过期、退出登录都走这里）
   *
   * 清存储层 + 复位 store 里的 token / 会员 ID / 显示名。
   */
  const clearSession = () => {
    SET_TOKEN('')
    SET_ID('')
    SET_NAME('')
    clearSessionStorage()
  }

  /**
   * 校验本地登录态，无效就顺手清干净
   *
   * @returns {boolean} 有效返回 true；无 token、缺过期时间或已过期返回 false（并已清理）
   */
  const ensureValidSession = () => {
    if (isSessionValid()) {
      return true
    }
    clearSession()
    return false
  }

  return {
    token,
    id,
    name,
    avatar,
    roles,
    permissions,
    // 五个 setter 都要显式返回：setup store 只有 return 出去的函数才会成为 action，
    // 之前只返回了 SET_AVATAR，登录/退出时调 SET_TOKEN / SET_ID / SET_NAME 会报
    // 「userStore.SET_TOKEN is not a function」（2026-10-13 踩过）
    SET_TOKEN,
    SET_ID,
    SET_NAME,
    SET_AVATAR,
    SET_ROLES,
    SET_PERMISSIONS,
    applySession,
    clearSession,
    ensureValidSession,
    login: loginAction,
    getInfo: getInfoAction,
    logOut: logOutAction
  }
})
