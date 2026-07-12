<template>
  <div class="logistics-page">
    <div class="page-head">
      <div>
        <h2>设备在线状态</h2>
        <p>车辆终端心跳、电量、信号、温度与离线判定。</p>
      </div>
      <el-input v-model="keyword" class="short-input" placeholder="IMEI / 车牌" />
    </div>

    <el-card shadow="never">
      <el-table :data="devices">
        <el-table-column prop="imei" label="IMEI" />
        <el-table-column prop="plate" label="车辆" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="battery" label="电量" />
        <el-table-column prop="signal" label="信号" />
        <el-table-column prop="lastHeartbeat" label="最后心跳" />
      </el-table>
    </el-card>
  </div>
</template>

<script lang="ts">
export default {
  name: 'LogisticsDevices',
  data() {
    return {
      keyword: '',
      devices: []
    }
  }
}
</script>

<style lang="scss" scoped>
@import './starter.scss';
</style>
