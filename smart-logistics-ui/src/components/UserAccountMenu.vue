<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fileApi, userApi } from '@/services/api'
import { isDemoMode } from '@/services/config'
import { tokenManager } from '@/services/token'
import { useLogisticsStore } from '@/stores/logistics'
import type { UserProfile, UserRole } from '@/services/types'

const emit = defineEmits<{
  logout: []
}>()

const roleLabels: Record<UserRole, string> = {
  SHIPPER: '宠物家长服务专员',
  WAREHOUSE: '中转照护员',
  DISPATCHER: '调度员',
  DRIVER: '司机',
  ADMIN: '系统管理员',
}

const store = useLogisticsStore()
const { user, avatar } = storeToRefs(store)
const avatarInput = ref<HTMLInputElement | null>(null)
const faceInput = ref<HTMLInputElement | null>(null)
const profileDialog = ref(false)
const faceDialog = ref(false)
const profileSaving = ref(false)
const faceSaving = ref(false)
const faceLoading = ref(false)
const faceBound = ref(false)
const faceImageUrl = ref('')
const faceImageBase64 = ref('')
const facePreview = ref('')
const profileForm = reactive({
  name: '',
  phone: '',
  role: 'DISPATCHER' as UserRole,
  password: '',
})

const currentUserId = computed(() => user.value?.id || tokenManager.getUser()?.id || '')

watch(profileDialog, (open) => {
  if (!open || !user.value) return
  profileForm.name = user.value.name || ''
  profileForm.phone = user.value.phone || ''
  profileForm.role = user.value.role
  profileForm.password = ''
})

async function changeAvatar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    const result = await fileApi.uploadImage(file)
    store.setAvatar(result.url)
    ElMessage.success('头像已上传')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '头像上传失败')
  } finally {
    input.value = ''
  }
}

async function saveProfile() {
  if (!user.value) return
  if (isDemoMode()) {
    store.switchRole(profileForm.role)
    user.value.name = profileForm.name || user.value.name
    user.value.phone = profileForm.phone
    profileDialog.value = false
    ElMessage.success('资料已更新')
    return
  }
  if (!currentUserId.value) return ElMessage.error('未找到当前用户 ID，请重新登录')
  profileSaving.value = true
  try {
    const updated = await userApi.update(currentUserId.value, {
      name: profileForm.name,
      phone: profileForm.phone,
      role: profileForm.role,
      password: profileForm.password || undefined,
    })
    const previous = tokenManager.getUser()
    const profile: UserProfile = {
      ...updated,
      permissions: updated.permissions || previous?.permissions || user.value.permissions || [],
    }
    store.applyUserProfile(profile)
    profileDialog.value = false
    ElMessage.success('个人资料已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资料保存失败')
  } finally {
    profileSaving.value = false
  }
}

async function openFaceDialog() {
  faceDialog.value = true
  faceImageBase64.value = ''
  facePreview.value = ''
  await loadFaceStatus()
}

async function loadFaceStatus() {
  if (isDemoMode()) {
    faceBound.value = false
    faceImageUrl.value = ''
    return
  }
  if (!currentUserId.value) return
  faceLoading.value = true
  try {
    const status = await userApi.faceStatus(currentUserId.value)
    faceBound.value = status.bound
    faceImageUrl.value = status.faceImageUrl || ''
  } catch (error) {
    await showFaceError(error instanceof Error ? error.message : '人脸状态加载失败')
  } finally {
    faceLoading.value = false
  }
}

async function chooseFaceImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    if (!file.type.startsWith('image/')) throw new Error('请选择图片文件')
    const dataUrl = await readFileAsDataUrl(file)
    facePreview.value = dataUrl
    faceImageBase64.value = dataUrl.replace(/^data:image\/\w+;base64,/, '')
  } catch (error) {
    await showFaceError(error instanceof Error ? error.message : '人脸图片读取失败')
  } finally {
    input.value = ''
  }
}

async function saveFace() {
  if (isDemoMode()) return ElMessage.info('演示模式不需要绑定人脸')
  if (!currentUserId.value) return showFaceError('未找到当前用户 ID，请重新登录')
  if (!faceImageBase64.value) return showFaceError('请先选择一张清晰的人脸照片')
  faceSaving.value = true
  try {
    const wasBound = faceBound.value
    const payload = { imageBase64: faceImageBase64.value, remark: 'web-profile-binding' }
    const result = wasBound
      ? await userApi.updateFace(currentUserId.value, payload)
      : await userApi.registerFace(currentUserId.value, payload)
    faceBound.value = true
    faceImageUrl.value = result.faceImageUrl || facePreview.value
    faceImageBase64.value = ''
    facePreview.value = ''
    ElMessage.success(wasBound ? '人脸信息已更新' : '人脸信息已绑定')
  } catch (error) {
    await showFaceError(error instanceof Error ? error.message : '人脸保存失败')
  } finally {
    faceSaving.value = false
  }
}

async function deleteFace() {
  if (!currentUserId.value) return
  const confirmed = await ElMessageBox.confirm('确认删除当前账号的人脸绑定吗？删除后将无法使用人脸登录。', '删除人脸绑定', {
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
    type: 'warning',
    customClass: 'account-message-box',
  }).then(() => true).catch(() => false)
  if (!confirmed) return
  try {
    await userApi.deleteFace(currentUserId.value)
    faceBound.value = false
    faceImageUrl.value = ''
    facePreview.value = ''
    ElMessage.success('人脸绑定已删除')
  } catch (error) {
    await showFaceError(error instanceof Error ? error.message : '人脸删除失败')
  }
}

function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error('图片读取失败'))
    reader.readAsDataURL(file)
  })
}

async function showFaceError(message: string) {
  ElMessage.error(message)
  await ElMessageBox.alert(message, '人脸操作失败', {
    confirmButtonText: '知道了',
    type: 'error',
    customClass: 'face-auth-message-box',
  }).catch(() => undefined)
}
</script>

<template>
  <el-dropdown trigger="click">
    <div class="portal-user-chip">
      <div class="avatar" :class="{ 'has-image': avatar }">
        <img v-if="avatar" :src="avatar" alt="用户头像" />
        <span v-else>{{ user?.name.slice(0, 1) }}</span>
      </div>
      <div><strong>{{ user?.name }}</strong><span>{{ user?.roleLabel }}</span></div>
      <el-icon><ArrowDown /></el-icon>
    </div>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item icon="User" @click="profileDialog = true">个人资料</el-dropdown-item>
        <el-dropdown-item icon="Camera" @click="openFaceDialog">人脸绑定</el-dropdown-item>
        <el-dropdown-item icon="Picture" @click="avatarInput?.click()">更换头像</el-dropdown-item>
        <el-dropdown-item divided @click="emit('logout')">退出登录</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
  <input ref="avatarInput" class="file-input-hidden" type="file" accept="image/*" @change="changeAvatar" />

  <el-dialog v-model="profileDialog" title="个人资料" width="420px" custom-class="account-dialog" append-to-body>
    <el-form label-position="top">
      <el-form-item label="账号">
        <el-input :model-value="user?.username" disabled />
      </el-form-item>
      <el-form-item label="姓名">
        <el-input v-model="profileForm.name" maxlength="32" />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="profileForm.phone" maxlength="20" />
      </el-form-item>
      <el-form-item label="身份">
        <el-select v-model="profileForm.role" style="width: 100%">
          <el-option v-for="(label, value) in roleLabels" :key="value" :label="label" :value="value" />
        </el-select>
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="profileForm.password" type="password" show-password placeholder="不修改请留空" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="profileDialog = false">取消</el-button>
      <el-button type="primary" :loading="profileSaving" @click="saveProfile">保存资料</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="faceDialog" title="人脸绑定" width="460px" custom-class="account-dialog face-bind-dialog" append-to-body>
    <div class="face-bind-panel" v-loading="faceLoading">
      <div class="face-bind-preview">
        <img v-if="facePreview || faceImageUrl" :src="facePreview || faceImageUrl" alt="人脸标准照" />
        <div v-else>
          <el-icon><Camera /></el-icon>
          <span>选择清晰正脸照片</span>
        </div>
      </div>
      <div class="face-bind-copy">
        <strong>{{ faceBound ? '已绑定人脸' : '未绑定人脸' }}</strong>
        <p>建议使用光线充足、无遮挡、正对镜头的照片。保存后将同步到百度人脸库，并在本地保存标准照。</p>
      </div>
      <input ref="faceInput" class="file-input-hidden" type="file" accept="image/*" @change="chooseFaceImage" />
    </div>
    <template #footer>
      <el-button @click="faceInput?.click()">选择照片</el-button>
      <el-button v-if="faceBound" type="danger" plain @click="deleteFace">删除绑定</el-button>
      <el-button type="primary" :loading="faceSaving" @click="saveFace">{{ faceBound ? '更新人脸' : '绑定人脸' }}</el-button>
    </template>
  </el-dialog>
</template>
