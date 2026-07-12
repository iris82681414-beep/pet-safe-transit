import { VuexModule, Module, Action, Mutation, getModule } from 'vuex-module-decorators'
import { authLogin, authLogout } from '@/api/logistics'
import { getToken, setToken, removeToken,getStoreId, setStoreId, removeStoreId, setUserInfo, getUserInfo, removeUserInfo } from '@/utils/cookies'
import store from '@/store'
import Cookies from 'js-cookie'
import { Message } from 'element-ui'
export interface IUserState {
  token: string
  name: string
  avatar: string
  storeId: string
  introduction: string
  userInfo: any
  roles: string[]
  username: string
}

@Module({ 'dynamic': true, store, 'name': 'user' })
class User extends VuexModule implements IUserState {
  public token = getToken() || ''
  public name = ''
  public avatar = ''
  // @ts-ignore
  public storeId: string = getStoreId() || ''
  public introduction = ''
  public userInfo = {}
  public roles: string[] = []
  public username = Cookies.get('username') || ''

  @Mutation
  private SET_TOKEN(token: string) {
    this.token = token
  }

  @Mutation
  private SET_NAME(name: string) {
    this.name = name
  }

  @Mutation
  private SET_USERINFO(userInfo: any) {
    this.userInfo = { ...userInfo }
  }

  @Mutation
  private SET_AVATAR(avatar: string) {
    this.avatar = avatar
  }

  @Mutation
  private SET_INTRODUCTION(introduction: string) {
    this.introduction = introduction
  }

  @Mutation
  private SET_ROLES(roles: string[]) {
    this.roles = roles
  }

  @Mutation
  private SET_STOREID(storeId: string) {
    this.storeId = storeId
  }
  @Mutation
  private SET_USERNAME(name: string) {
    this.username = name
    }

  @Action
  public async Login(userInfo: { username: string, password: string }) {
    let { username, password } = userInfo
    username = username.trim()
    this.SET_USERNAME(username)
    Cookies.set('username', username)
    const { data } = await authLogin({ username, password })
    if (String(data.code) === '0') {
      const payload = data.data || {}
      const user = payload.user || {}
      const token = payload.accessToken || payload.token || ''
      const normalizedUser = {
        ...user,
        token,
        roles: user.role ? [user.role] : ['ADMIN']
      }
      this.SET_TOKEN(token)
      setToken(token)
      this.SET_USERINFO(normalizedUser)
      this.SET_ROLES(normalizedUser.roles)
      this.SET_NAME(user.name || user.username || username)
      setUserInfo(normalizedUser)
      Cookies.set('user_info', JSON.stringify(normalizedUser))
      return data
    } else if (String(data.code) === '1') {
      this.SET_TOKEN(data.data.token)
      setToken(data.data.token)
      this.SET_USERINFO(data.data)
      setUserInfo(data.data)
      Cookies.set('user_info', JSON.stringify(data.data))
      return data
    } else {
      return Message.error(data.message || data.msg || '登录失败')
    }
  }

  @Action
  public ResetToken () {
    removeToken()
    this.SET_TOKEN('')
    this.SET_ROLES([])
  }

  @Action
  public async changeStore(data: any) {
    this.SET_STOREID(data.data)
    this.SET_TOKEN(data.authorization)
    setStoreId(data.data)
    setToken(data.authorization)
  }

  @Action
  public async GetUserInfo () {
    if (this.token === '') {
      throw Error('GetUserInfo: token is undefined!')
    }

    const data = JSON.parse(<string>getUserInfo()) //  { roles: ['admin'], name: 'zhangsan', avatar: '/login', introduction: '' }
    if (!data) {
      throw Error('Verification failed, please Login again.')
    }

    const { roles, role, name, avatar, introduction, applicant, storeManagerName, storeId='' } = data // data.user
    // roles must be a non-empty array
    const normalizedRoles = roles && roles.length > 0 ? roles : (role ? [role] : ['ADMIN'])
    if (!normalizedRoles || normalizedRoles.length <= 0) {
      throw Error('GetUserInfo: roles must be a non-null array!')
    }

    this.SET_ROLES(normalizedRoles)
    this.SET_USERINFO(data)
    this.SET_NAME(name || applicant || storeManagerName)
    this.SET_AVATAR(avatar)
    this.SET_INTRODUCTION(introduction)
  }

  @Action
  public async LogOut () {
    await authLogout().catch(() => null)
    removeToken()
    this.SET_TOKEN('')
    this.SET_ROLES([])
    Cookies.remove('username')
    Cookies.remove('user_info')
    removeUserInfo()
  }
}

export const UserModule = getModule(User)
