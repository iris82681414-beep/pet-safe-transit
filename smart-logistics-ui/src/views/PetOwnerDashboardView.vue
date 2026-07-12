<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import OwnerVoiceDialog from '@/components/PetOwner/OwnerVoiceDialog.vue'

const router = useRouter()
const profileOpen = ref(false)
const voiceDialog = ref<InstanceType<typeof OwnerVoiceDialog> | null>(null)

const alerts = [
  { title: '温度偏高', meta: '沪A·12345 | 10:21', status: '未处理', level: 'warning' },
  { title: '急刹车', meta: '沪A·12345 | 09:47', status: '已处理', level: 'warning' },
  { title: '长时间停留', meta: '沪A·12345 | 昨天', status: '已处理', level: 'info' },
]

const environment = [
  { label: '温度', value: '24.6°C', icon: 'Thermometer', tone: 'red' },
  { label: '湿度', value: '58%', icon: 'Pouring', tone: 'blue' },
  { label: '空气质量', value: '优', icon: 'Cloudy', tone: 'green' },
  { label: '震动', value: '轻微', icon: 'Histogram', tone: 'cyan' },
  { label: '光照', value: '适中', icon: 'Sunny', tone: 'orange' },
]

const orbitActions = [
  { key: 'tracking', title: '实时追踪', note: '查看位置轨迹', icon: 'LocationFilled', tone: 'blue', position: 'top-left' },
  { key: 'alerts', title: '告警中心', note: '异常主动提醒', icon: 'BellFilled', tone: 'purple', position: 'top-right' },
  { key: 'tracking', title: '运输管理', note: '运单与车辆管理', icon: 'Van', tone: 'green', position: 'mid-left' },
  { key: 'profile', title: '宠物档案', note: '健康与资料管理', icon: 'Postcard', tone: 'orange', position: 'mid-right' },
  { key: 'overview', title: '数据分析', note: '运输数据统计', icon: 'TrendCharts', tone: 'indigo', position: 'bottom-left' },
  { key: 'settings', title: '系统设置', note: '偏好与账户设置', icon: 'Setting', tone: 'cyan', position: 'bottom-right' },
]

function navigate(page: string) {
  if (page === 'profile') {
    ElMessage.info('布丁的健康档案已准备好，档案详情页将在下一阶段接入')
    return
  }
  if (page === 'settings') {
    profileOpen.value = true
    ElMessage.info('可从右上角账户菜单管理个人偏好')
    return
  }
  void router.push({ name: page })
}

function startConversation() {
  void voiceDialog.value?.openAndListen()
}
</script>

<template>
  <div class="pet-owner-dashboard" @click.self="profileOpen = false">
    <header class="owner-header">
      <button class="owner-brand" type="button" aria-label="返回导航窗口" @click="router.push({ name: 'portal' })">
        <span class="brand-symbol"><el-icon><MostlyCloudy /></el-icon><i>+</i></span>
        <span class="brand-copy">
          <strong>伴生云途——智能宠物托运与全程感知平台</strong>
          <small>萌宠运输 · 安心每一程</small>
        </span>
      </button>

      <div class="header-actions">
        <button class="header-chip alert-chip" type="button" @click="navigate('alerts')">
          <el-icon><Bell /></el-icon><strong>告警</strong><b>3</b>
        </button>
        <div class="header-chip weather-chip" aria-label="当前天气">
          <el-icon><PartlyCloudy /></el-icon>
          <span><strong>26°C</strong><small>多云</small></span>
        </div>
        <div class="account-wrap">
          <button class="header-chip account-chip" type="button" @click.stop="profileOpen = !profileOpen">
            <span class="owner-avatar">👩🏻‍⚕️</span>
            <span><strong>张小爱</strong><small>宠物主人</small></span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <transition name="profile-pop">
            <div v-if="profileOpen" class="profile-menu">
              <button type="button" @click="navigate('profile')"><el-icon><Postcard /></el-icon>宠物档案</button>
              <button type="button" @click="navigate('settings')"><el-icon><Setting /></el-icon>账户设置</button>
              <button type="button" @click="router.push({ name: 'portal' })"><el-icon><Grid /></el-icon>返回原平台</button>
            </div>
          </transition>
        </div>
      </div>
    </header>

    <main class="owner-content">
      <aside class="side-column left-column">
        <section class="glass-panel overview-panel">
          <div class="panel-heading"><h2>今日运输概览</h2><span>更新时间 10:30:45</span></div>
          <div class="metric-row">
            <article><strong class="blue">2</strong><span>运输中</span></article>
            <article><strong class="green">1</strong><span>已送达</span></article>
            <article><strong class="red">0</strong><span>异常告警</span></article>
          </div>
          <div class="pet-journey-card" role="button" tabindex="0" @click="navigate('tracking')" @keydown.enter="navigate('tracking')">
            <h3>我的宠物</h3>
            <div class="pet-summary">
              <span class="pet-avatar">🐶</span>
              <span><strong>布丁</strong><small>金毛 · 2岁 · 公</small></span>
              <b>运输中</b>
            </div>
            <div class="journey-foot"><span>沪A·12345</span><strong>预计 14:38 到达</strong></div>
          </div>
        </section>

        <section class="glass-panel recent-panel">
          <div class="panel-heading"><h2>最近告警</h2><button type="button" @click="navigate('alerts')">更多 <el-icon><ArrowRight /></el-icon></button></div>
          <button v-for="item in alerts" :key="item.title" class="alert-row" type="button" @click="navigate('alerts')">
            <span class="alert-symbol" :class="item.level"><el-icon><WarningFilled v-if="item.level === 'warning'" /><Clock v-else /></el-icon></span>
            <span><strong>{{ item.title }}</strong><small>{{ item.meta }}</small></span>
            <b :class="item.status === '未处理' ? 'pending' : ''">{{ item.status }}</b>
          </button>
        </section>
      </aside>

      <section class="assistant-stage" aria-label="羊小智宠物运输助手">
        <div class="radar-ring ring-one"></div>
        <div class="radar-ring ring-two"></div>
        <div class="radar-ring ring-three"></div>
        <span class="radar-line line-a"></span><span class="radar-line line-b"></span>
        <span class="radar-line line-c"></span><span class="radar-line line-d"></span>

        <button
          v-for="item in orbitActions"
          :key="item.title"
          class="orbit-action"
          :class="[item.position, item.tone]"
          type="button"
          @click="navigate(item.key)"
        >
          <span class="orbit-icon"><el-icon><component :is="item.icon" /></el-icon></span>
          <strong>{{ item.title }}</strong><small>{{ item.note }}</small>
        </button>

        <div class="assistant-message">
          <strong>嗨~ 我是<span>智慧小羊</span></strong>
          <p>有什么可以帮您的吗？</p>
        </div>
        <div class="assistant-core">
          <span class="core-glow"></span>
          <img src="@/assets/iot-sheep-pet.png" alt="伴生云途宠物运输助手羊小智" />
        </div>
        <button class="talk-button" type="button" @click="startConversation">
          <el-icon><ChatDotRound /></el-icon><span>点击对话</span>
        </button>
      </section>

      <aside class="side-column right-column">
        <section class="glass-panel transport-panel">
          <div class="panel-heading"><h2>运输状态</h2><button type="button" @click="navigate('tracking')">更多 <el-icon><ArrowRight /></el-icon></button></div>
          <button class="transport-card" type="button" @click="navigate('tracking')">
            <div class="transport-head"><strong>沪A·12345</strong><b>运输中</b></div>
            <div class="route-line"><strong>上海市</strong><span><i></i><el-icon><Right /></el-icon></span><strong>杭州市</strong></div>
            <small>约 156 km</small>
            <div class="truck-scene">
              <span class="tree tree-one"></span><span class="tree tree-two"></span>
              <span class="road"></span><el-icon class="truck"><Van /></el-icon>
            </div>
            <div class="arrival"><span><small>预计到达</small><strong>14:38</strong></span><p>剩余 <b>2小时08分</b></p></div>
          </button>
        </section>

        <section class="glass-panel environment-panel">
          <div class="panel-heading"><h2>环境监测</h2><button type="button" @click="navigate('tracking')">更多 <el-icon><ArrowRight /></el-icon></button></div>
          <div class="environment-list">
            <div v-for="item in environment" :key="item.label" class="environment-row">
              <el-icon :class="item.tone"><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span><strong>{{ item.value }}</strong><el-icon class="ok"><CircleCheckFilled /></el-icon>
            </div>
          </div>
        </section>
      </aside>
    </main>
    <OwnerVoiceDialog ref="voiceDialog" />
  </div>
</template>

<style scoped>
.pet-owner-dashboard {
  min-height: 100vh;
  overflow: hidden;
  color: #102a61;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  background:
    radial-gradient(circle at 50% 46%, rgba(103, 190, 255, .18), transparent 34%),
    linear-gradient(rgba(72, 147, 255, .035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(72, 147, 255, .035) 1px, transparent 1px),
    linear-gradient(145deg, #fbfdff 0%, #eef6ff 52%, #f9fcff 100%);
  background-size: auto, 40px 40px, 40px 40px, auto;
}

.owner-header { height: 94px; display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 0 32px; border-bottom: 1px solid #d5e7fb; background: rgba(255, 255, 255, .72); box-shadow: 0 8px 28px rgba(61, 112, 173, .08); backdrop-filter: blur(18px); }
button { font: inherit; }
.owner-brand { display: flex; align-items: center; gap: 14px; color: inherit; text-align: left; background: transparent; }
.brand-symbol { position: relative; width: 55px; height: 55px; display: grid; place-items: center; border: 3px solid #2f9df4; border-radius: 18px; color: #25b7ef; background: #fff; box-shadow: 0 7px 18px rgba(38, 143, 233, .18), inset 0 0 0 4px #eaf7ff; }
.brand-symbol .el-icon { font-size: 32px; }
.brand-symbol i { position: absolute; left: 20px; top: 16px; color: #1778f2; font-size: 18px; font-weight: 900; font-style: normal; }
.brand-copy { display: flex; flex-direction: column; }
.brand-copy strong { color: #0b2251 !important; font-size: 24px; font-weight: 800; }
.brand-copy small { margin-top: 5px; color: #3685df !important; font-size: 14px; font-weight: 600; }
.header-actions { display: flex; align-items: center; gap: 16px; }
.header-chip { height: 56px; display: flex; align-items: center; gap: 10px; padding: 0 18px; border: 1px solid #c8def8; border-radius: 16px; color: #17366d; background: rgba(255,255,255,.74); box-shadow: 0 7px 20px rgba(51, 110, 182, .09); }
.header-chip:hover { border-color: #7bbcff; transform: translateY(-1px); box-shadow: 0 10px 24px rgba(51, 110, 182, .15); }
.header-chip > .el-icon { color: #3562a8; font-size: 22px; }
.header-chip strong { color: #17366d !important; font-size: 15px; }
.header-chip small { display: block; margin-top: 3px; color: #657da5 !important; font-size: 12px; }
.alert-chip b { width: 22px; height: 22px; display: grid; place-items: center; border-radius: 50%; color: #fff; background: #f26756; font-size: 12px; }
.weather-chip > .el-icon { color: #ffb82e; font-size: 31px; }
.weather-chip span, .account-chip > span:nth-child(2) { text-align: left; }
.account-wrap { position: relative; }
.account-chip { min-width: 168px; }
.owner-avatar { width: 36px; height: 36px; display: grid; place-items: center; border-radius: 50%; background: #f9e6d7; font-size: 23px; }
.account-chip > .el-icon { margin-left: auto; font-size: 14px; }
.profile-menu { position: absolute; right: 0; top: 64px; z-index: 20; width: 180px; padding: 7px; border: 1px solid #c8def8; border-radius: 12px; background: rgba(255,255,255,.96); box-shadow: 0 18px 42px rgba(40, 83, 139, .18); backdrop-filter: blur(16px); }
.profile-menu button { width: 100%; display: flex; align-items: center; gap: 10px; padding: 10px 11px; border-radius: 8px; color: #38537a; background: transparent; text-align: left; }
.profile-menu button:hover { color: #176fe8; background: #edf6ff; }
.profile-pop-enter-active, .profile-pop-leave-active { transition: opacity .16s ease, transform .16s ease; }
.profile-pop-enter-from, .profile-pop-leave-to { opacity: 0; transform: translateY(-6px); }

.owner-content { min-height: calc(100vh - 94px); display: grid; grid-template-columns: minmax(258px, 296px) minmax(510px, 1fr) minmax(270px, 304px); gap: 22px; padding: 22px 24px 26px; }
.side-column { min-width: 0; display: grid; grid-template-rows: minmax(310px, .94fr) minmax(278px, .86fr); gap: 22px; }
.glass-panel { overflow: hidden; padding: 16px; border: 1px solid #c4dcf7; border-radius: 16px; background: rgba(255,255,255,.64); box-shadow: 0 12px 30px rgba(49, 104, 172, .08), inset 0 1px 0 rgba(255,255,255,.9); backdrop-filter: blur(14px); }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.panel-heading h2 { margin: 0; color: #102f6e !important; font-size: 15px; font-weight: 800; }
.panel-heading > span { color: #6f85a8 !important; font-size: 11px; }
.panel-heading button { display: inline-flex; align-items: center; gap: 2px; color: #6380a9; background: transparent; font-size: 11px; }
.panel-heading button:hover { color: #1675ec; }
.metric-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.metric-row article { min-height: 82px; display: flex; flex-direction: column; align-items: center; justify-content: center; border: 1px solid #e0ebf8; border-radius: 11px; background: rgba(255,255,255,.72); box-shadow: 0 6px 17px rgba(47, 103, 169, .05); }
.metric-row strong { font-size: 27px; line-height: 1; }.metric-row span { margin-top: 11px; color: #263f68 !important; font-size: 12px; }.blue { color: #217bf2 !important; }.green { color: #20ab53 !important; }.red { color: #ef5946 !important; }
.pet-journey-card { margin-top: 12px; padding: 12px; border: 1px solid #d5e7f9; border-radius: 11px; background: rgba(255,255,255,.72); cursor: pointer; transition: border-color .18s ease, transform .18s ease; }
.pet-journey-card:hover { border-color: #74b8fb; transform: translateY(-1px); }
.pet-journey-card h3 { margin: 0 0 10px; color: #17396f !important; font-size: 12px; }
.pet-summary { display: flex; align-items: center; gap: 10px; }
.pet-avatar { width: 44px; height: 44px; display: grid; place-items: center; border-radius: 50%; background: #f0dfcb; font-size: 29px; }
.pet-summary > span:nth-child(2) { min-width: 0; display: flex; flex: 1; flex-direction: column; }.pet-summary strong { color: #183765 !important; font-size: 13px; }.pet-summary small { margin-top: 4px; color: #6e83a3 !important; font-size: 11px; }.pet-summary b { padding: 4px 8px; border-radius: 10px; color: #1472df; background: #e8f3ff; font-size: 10px; }
.journey-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; padding-top: 9px; border-top: 1px solid #e4edf8; }.journey-foot span { color: #2a4670 !important; font-size: 11px; }.journey-foot strong { color: #1fa352 !important; font-size: 11px; }
.recent-panel { padding-bottom: 8px; }
.alert-row { width: 100%; display: grid; grid-template-columns: 36px minmax(0,1fr) auto; align-items: center; gap: 9px; padding: 11px 0; border-top: 1px solid #e4edf8; color: inherit; background: transparent; text-align: left; }
.alert-row:hover > span:nth-child(2) strong { color: #176fe8 !important; }.alert-symbol { width: 34px; height: 34px; display: grid; place-items: center; border-radius: 50%; color: #ec5c4d; background: #ffe9e6; }.alert-symbol.info { color: #397fe8; background: #e8f2ff; }.alert-row > span:nth-child(2) { min-width: 0; display: flex; flex-direction: column; }.alert-row strong { color: #263f68 !important; font-size: 12px; }.alert-row small { margin-top: 4px; overflow: hidden; color: #7589a6 !important; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }.alert-row b { padding: 4px 7px; border: 1px solid #cadbef; border-radius: 9px; color: #6680a4; font-size: 9px; }.alert-row b.pending { border-color: #ffb0a5; color: #f05b4a; background: #fff5f3; }

.assistant-stage { position: relative; min-height: 626px; overflow: visible; border-radius: 46% 46% 40% 40%; background: radial-gradient(circle at 50% 49%, rgba(255,255,255,.98) 0 18%, rgba(210,237,255,.76) 39%, rgba(188,224,255,.28) 56%, transparent 70%); }
.radar-ring { position: absolute; left: 50%; top: 51%; border: 1px solid rgba(86, 167, 245, .24); border-radius: 50%; transform: translate(-50%, -50%); box-shadow: inset 0 0 32px rgba(68, 164, 255, .08), 0 0 26px rgba(80, 171, 255, .07); }.ring-one { width: 270px; height: 270px; }.ring-two { width: 430px; height: 430px; }.ring-three { width: 570px; height: 570px; border-style: dashed; animation: radar-spin 28s linear infinite; }
.radar-line { position: absolute; left: 50%; top: 51%; width: 250px; height: 1px; background: linear-gradient(90deg, rgba(73,166,244,.55), transparent); transform-origin: left center; }.line-a { transform: rotate(0deg); }.line-b { transform: rotate(90deg); }.line-c { transform: rotate(180deg); }.line-d { transform: rotate(270deg); }
.assistant-core { position: absolute; left: 50%; top: 51%; width: 290px; height: 370px; display: flex; align-items: center; justify-content: center; transform: translate(-50%, -46%); }.assistant-core img { position: relative; z-index: 2; width: 100%; height: 100%; object-fit: contain; filter: drop-shadow(0 18px 22px rgba(38, 107, 182, .17)); animation: pet-float 4s ease-in-out infinite; }.core-glow { position: absolute; inset: 18% 5% 10%; border-radius: 50%; background: rgba(91, 189, 255, .2); filter: blur(26px); }
.assistant-message { position: absolute; left: 50%; top: 76px; z-index: 4; min-width: 208px; padding: 14px 20px; border: 1px solid #bddcff; border-radius: 18px; color: #203d6b; background: rgba(255,255,255,.9); box-shadow: 0 10px 24px rgba(47, 107, 179, .12); text-align: center; transform: translateX(-50%); }.assistant-message::after { content: ''; position: absolute; left: 50%; bottom: -9px; width: 16px; height: 16px; border-right: 1px solid #bddcff; border-bottom: 1px solid #bddcff; background: #fff; transform: translateX(-50%) rotate(45deg); }.assistant-message strong { color: #15366b !important; font-size: 16px; }.assistant-message strong span { margin-left: 5px; color: #1877ec !important; }.assistant-message p { margin: 5px 0 0; color: #425c81 !important; font-size: 12px; }
.orbit-action { position: absolute; z-index: 5; width: 130px; height: 144px; display: flex; flex-direction: column; align-items: center; justify-content: center; border: 1px solid rgba(181, 215, 247, .86); border-radius: 50%; color: #173970; background: rgba(255,255,255,.75); box-shadow: 0 14px 30px rgba(50, 106, 173, .10), inset 0 0 24px rgba(128, 201, 255, .12); backdrop-filter: blur(10px); transition: transform .2s ease, box-shadow .2s ease; }.orbit-action:hover { transform: translateY(-4px) scale(1.02); box-shadow: 0 19px 35px rgba(50, 106, 173, .18), inset 0 0 24px rgba(128, 201, 255, .18); }.orbit-icon { width: 61px; height: 61px; display: grid; place-items: center; margin-bottom: 7px; border-radius: 50%; color: #fff; box-shadow: inset 0 2px 4px rgba(255,255,255,.35), 0 8px 18px currentColor; }.orbit-icon .el-icon { font-size: 30px; }.orbit-action strong { color: #163a75 !important; font-size: 14px; }.orbit-action small { margin-top: 4px; color: #627b9e !important; font-size: 9px; }.orbit-action.blue .orbit-icon { background: linear-gradient(145deg,#5fcfff,#1688f2); color: rgba(45,154,244,.24); }.orbit-action.purple .orbit-icon { background: linear-gradient(145deg,#a98aff,#6949ed); color: rgba(119,76,239,.23); }.orbit-action.green .orbit-icon { background: linear-gradient(145deg,#61df9f,#17ae67); color: rgba(28,181,105,.22); }.orbit-action.orange .orbit-icon { background: linear-gradient(145deg,#ffc464,#f59024); color: rgba(246,151,38,.22); }.orbit-action.indigo .orbit-icon { background: linear-gradient(145deg,#70a8ff,#3b66e8); color: rgba(62,104,232,.22); }.orbit-action.cyan .orbit-icon { background: linear-gradient(145deg,#5de0e8,#14aabd); color: rgba(25,178,194,.22); }
.top-left { left: 15px; top: 28px; }.top-right { right: 15px; top: 28px; }.mid-left { left: -10px; top: 265px; }.mid-right { right: -10px; top: 265px; }.bottom-left { left: 48px; bottom: 9px; }.bottom-right { right: 48px; bottom: 9px; }
.talk-button { position: absolute; left: 50%; bottom: 61px; z-index: 7; min-width: 170px; height: 48px; display: flex; align-items: center; justify-content: center; gap: 8px; border-radius: 24px; color: #fff; background: linear-gradient(135deg, #54b5ff, #346cf0); box-shadow: 0 12px 24px rgba(49, 111, 237, .28), inset 0 1px 0 rgba(255,255,255,.4); transform: translateX(-50%); }.talk-button:hover { filter: brightness(1.06); }.talk-button .el-icon { font-size: 21px; }.talk-button span { color: #fff !important; font-size: 15px; font-weight: 700; }

.transport-card { width: 100%; min-height: 255px; display: flex; flex-direction: column; padding: 16px; border: 1px solid #c8def8; border-radius: 13px; color: inherit; background: linear-gradient(145deg, rgba(255,255,255,.85), rgba(228,242,255,.75)); text-align: left; }.transport-card:hover { border-color: #78b9f7; }.transport-head, .route-line, .arrival { display: flex; align-items: center; justify-content: space-between; }.transport-head strong { color: #142e61 !important; font-size: 15px; }.transport-head b { padding: 5px 9px; border-radius: 10px; color: #2474e8; background: #e5f0ff; font-size: 10px; }.route-line { margin-top: 17px; }.route-line strong { color: #1d3968 !important; font-size: 13px; }.route-line span { flex: 1; display: flex; align-items: center; margin: 0 12px; color: #2c79ef; }.route-line i { flex: 1; height: 2px; background: linear-gradient(90deg, #b4dcff, #2d83f4); }.transport-card > small { margin-top: 7px; color: #7085a4 !important; font-size: 10px; }.truck-scene { position: relative; height: 82px; margin: 8px -16px 0; overflow: hidden; background: linear-gradient(#f8fcff 55%, #dff3eb 56%, #e8f2fb 78%); }.road { position: absolute; left: 0; right: 0; bottom: 13px; height: 2px; background: repeating-linear-gradient(90deg,#9dc4e8 0 18px,transparent 18px 35px); }.truck { position: absolute; left: 47%; bottom: 15px; color: #1c7dd7; font-size: 53px; filter: drop-shadow(0 6px 5px rgba(27,90,143,.18)); }.tree { position: absolute; bottom: 23px; width: 7px; height: 35px; border-radius: 7px; background: #6cc596; }.tree::before { content: ''; position: absolute; left: -7px; top: -8px; width: 21px; height: 25px; border-radius: 50%; background: #77d4a0; }.tree-one { left: 12px; }.tree-two { right: 16px; transform: scale(.8); }.arrival { margin-top: auto; padding-top: 9px; }.arrival > span { display: flex; flex-direction: column; }.arrival small { color: #6b82a3 !important; font-size: 10px; }.arrival strong { margin-top: 2px; color: #276de2 !important; font-size: 23px; }.arrival p { margin: 15px 0 0; color: #647b9e !important; font-size: 10px; }.arrival p b { color: #223d68; font-size: 12px; }
.environment-list { display: flex; flex-direction: column; gap: 7px; }.environment-row { display: grid; grid-template-columns: 25px 1fr auto 18px; align-items: center; gap: 5px; min-height: 34px; }.environment-row > .el-icon:first-child { font-size: 17px; }.environment-row span { color: #30496f !important; font-size: 12px; }.environment-row strong { color: #2d4367 !important; font-size: 11px; }.environment-row .ok { color: #26ae5d; font-size: 13px; }.environment-row .red { color: #e45b50 !important; }.environment-row .blue { color: #2f8df4 !important; }.environment-row .green { color: #25ae63 !important; }.environment-row .cyan { color: #28a9d5 !important; }.environment-row .orange { color: #f4a51e !important; }

@keyframes pet-float { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-8px); } }
@keyframes radar-spin { to { transform: translate(-50%, -50%) rotate(360deg); } }

:global(.app-shell.pet-owner-mode) { display: block !important; width: 100% !important; min-width: 0 !important; color: #102a61 !important; background: #f4f9ff !important; }
:global(.app-shell.pet-owner-mode::before) { display: none !important; }
:global(.app-shell.pet-owner-mode .workspace), :global(.app-shell.pet-owner-mode .page-content) { width: 100% !important; min-width: 0 !important; min-height: 100vh !important; padding: 0 !important; color: #102a61 !important; background: transparent !important; }
:global(.app-shell.pet-owner-mode .voice-assistant-button) { display: none !important; }

@media (max-width: 1120px) {
  .owner-content { grid-template-columns: minmax(480px, 1.4fr) minmax(270px, .7fr); }
  .assistant-stage { grid-column: 1; grid-row: 1 / span 2; }
  .left-column { grid-column: 2; grid-row: 1; }.right-column { grid-column: 2; grid-row: 2; }
  .side-column { grid-template-rows: auto auto; }
  .overview-panel, .transport-panel { min-height: 310px; }
  .recent-panel, .environment-panel { min-height: 270px; }
  .header-actions { gap: 8px; }.header-chip { padding: 0 12px; }.weather-chip { display: none; }
}

@media (max-width: 820px) {
  .pet-owner-dashboard { overflow-x: hidden; overflow-y: visible; }
  .owner-header { height: auto; min-height: 86px; padding: 14px 18px; }
  .brand-symbol { width: 46px; height: 46px; border-radius: 14px; }.brand-copy strong { font-size: 18px; }.brand-copy small { font-size: 11px; }
  .alert-chip { width: 48px; padding: 0; justify-content: center; }.alert-chip strong { display: none; }.account-chip { min-width: 0; width: 48px; padding: 0; justify-content: center; }.account-chip > span:nth-child(2), .account-chip > .el-icon { display: none; }
  .owner-content { display: flex; flex-direction: column; padding: 14px; }
  .assistant-stage { order: -1; min-height: 610px; }
  .left-column, .right-column { display: grid; grid-template-columns: 1fr 1fr; }
}

@media (max-width: 620px) {
  .owner-header { gap: 8px; }.brand-copy strong { font-size: 16px; }.brand-copy small { display: none; }.brand-symbol { width: 42px; height: 42px; }.header-actions { gap: 6px; }.header-chip { height: 46px; }
  .assistant-stage { min-height: 580px; margin: 0 -8px; overflow: hidden; }.ring-three { width: 500px; height: 500px; }.ring-two { width: 360px; height: 360px; }
  .orbit-action { width: 105px; height: 116px; }.orbit-icon { width: 47px; height: 47px; }.orbit-icon .el-icon { font-size: 24px; }.orbit-action strong { font-size: 12px; }.orbit-action small { display: none; }
  .top-left { left: 4px; top: 35px; }.top-right { right: 4px; top: 35px; }.mid-left { left: -13px; top: 238px; }.mid-right { right: -13px; top: 238px; }.bottom-left { left: 20px; bottom: 22px; }.bottom-right { right: 20px; bottom: 22px; }
  .assistant-message { top: 104px; min-width: 180px; padding: 11px 14px; }.assistant-core { width: 250px; height: 330px; }.talk-button { bottom: 80px; }
  .left-column, .right-column { grid-template-columns: 1fr; }
}

@media (prefers-reduced-motion: reduce) { .assistant-core img, .ring-three { animation: none; } * { scroll-behavior: auto !important; } }
</style>
