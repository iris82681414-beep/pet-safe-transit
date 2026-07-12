<template>
  <div class="logistics-page">
    <div class="page-head">
      <div>
        <h2>车辆调度总览台</h2>
        <p>全队地图、车辆列表、状态筛选、调度指令。</p>
      </div>
      <div class="toolbar">
        <el-select v-model="status" placeholder="车辆状态" clearable>
          <el-option label="行驶" value="MOVING" />
          <el-option label="停靠" value="STOPPED" />
          <el-option label="离线" value="OFFLINE" />
        </el-select>
        <el-input v-model="keyword" placeholder="车牌 / 司机 / IMEI" />
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="15">
        <el-card shadow="never">
          <div slot="header">车辆地图</div>
          <div class="map-shell">
            <span>WebSocket vehicle.position 接入位</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="9">
        <el-card shadow="never">
          <div slot="header">车辆列表</div>
          <el-table :data="vehicles" height="360">
            <el-table-column prop="plate" label="车牌" />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column prop="driverName" label="司机" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script lang="ts">
export default {
  name: 'LogisticsFleet',
  data() {
    return {
      status: '',
      keyword: '',
      vehicles: []
    }
  }
}
</script>

<style lang="scss" scoped>
@import './starter.scss';
</style>
