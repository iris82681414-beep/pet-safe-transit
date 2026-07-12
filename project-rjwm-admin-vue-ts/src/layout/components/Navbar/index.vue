<template>
  <div class="navbar">
    <div class="left-area">
      <hamburger
        id="hamburger-container"
        :is-active="sidebar.opened"
        class="hamburger-container"
        @toggleClick="toggleSideBar"
      />
      <div class="platform-title">
        <strong>智慧物流 IoT 平台</strong>
        <span>Starter</span>
      </div>
    </div>

    <div class="right-menu">
      <span class="role-tag">{{ role }}</span>
      <el-dropdown trigger="click" @command="handleCommand">
        <el-button type="primary" class="user-button">
          {{ name }}<i class="el-icon-arrow-down el-icon--right" />
        </el-button>
        <el-dropdown-menu slot="dropdown">
          <el-dropdown-item command="logout">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </el-dropdown>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import { AppModule } from '@/store/modules/app'
import { UserModule } from '@/store/modules/user'
import Hamburger from '@/components/Hamburger/index.vue'
import Cookies from 'js-cookie'

@Component({
  name: 'Navbar',
  components: {
    Hamburger,
  },
})
export default class extends Vue {
  get sidebar() {
    return AppModule.sidebar
  }

  get cookieUser() {
    try {
      const raw = Cookies.get('user_info')
      return raw ? JSON.parse(raw) : {}
    } catch (e) {
      return {}
    }
  }

  get name() {
    const userInfo = UserModule.userInfo as any
    return userInfo.name || this.cookieUser.name || this.cookieUser.username || '未登录'
  }

  get role() {
    const userInfo = UserModule.userInfo as any
    return userInfo.role || this.cookieUser.role || 'ROLE'
  }

  private toggleSideBar() {
    AppModule.ToggleSideBar(false)
  }

  private handleCommand(command: string) {
    if (command === 'logout') {
      this.logout()
    }
  }

  private async logout() {
    await UserModule.LogOut()
    this.$router.replace({ path: '/login' })
  }
}
</script>

<style lang="scss" scoped>
.navbar {
  height: 60px;
  position: relative;
  background: #ffffff;
  border-bottom: 1px solid #e5e9f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 24px;
}

.left-area {
  display: flex;
  align-items: center;
  height: 100%;
}

.hamburger-container {
  padding: 0 18px 0 20px;
  cursor: pointer;
}

.platform-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.platform-title strong {
  color: #1f2933;
  font-size: 16px;
}

.platform-title span {
  color: #667085;
  font-size: 12px;
}

.right-menu {
  display: flex;
  align-items: center;
  gap: 12px;
}

.role-tag {
  height: 28px;
  line-height: 28px;
  padding: 0 10px;
  border-radius: 4px;
  background: #eef2f6;
  color: #475467;
  font-size: 12px;
}

.user-button {
  height: 32px;
  padding: 0 12px;
  border: 0;
  background: #1f2933;
}
</style>
