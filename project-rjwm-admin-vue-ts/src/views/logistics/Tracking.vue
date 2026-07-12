<template>
  <div class="logistics-page">
    <div class="page-head">
      <div>
        <h2>货物追踪工作台</h2>
        <p>货物详情、实时位置、轨迹、ETA、时间线。</p>
      </div>
      <el-input v-model="cargoId" class="cargo-search" placeholder="输入 cargoId">
        <el-button slot="append" icon="el-icon-search" />
      </el-input>
    </div>

    <el-row :gutter="16">
      <el-col :span="8">
        <el-card shadow="never">
          <div slot="header">货物详情</div>
          <div class="info-list">
            <div><span>运单号</span><strong>{{ cargoId || '-' }}</strong></div>
            <div><span>状态</span><strong>待接入 /cargo/{cargoId}</strong></div>
            <div><span>承运车辆</span><strong>待接入</strong></div>
            <div><span>司机</span><strong>待接入</strong></div>
          </div>
        </el-card>

        <el-card class="mt16" shadow="never">
          <div slot="header">ETA</div>
          <div class="metric">-- min</div>
          <el-progress :percentage="0" />
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card shadow="never">
          <div slot="header">实时地图</div>
          <div class="map-shell">
            <span>Leaflet 地图接入位</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row class="mt16" :gutter="16">
      <el-col :span="12">
        <el-card shadow="never">
          <div slot="header">轨迹点</div>
          <div class="empty-state">等待 /trajectory 数据</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <div slot="header">运输时间线</div>
          <div class="empty-state">等待 /timeline 数据</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script lang="ts">
export default {
  name: 'LogisticsTracking',
  data() {
    return {
      cargoId: 'SH-HZ-20260629-0291'
    }
  }
}
</script>

<style lang="scss" scoped>
@import './starter.scss';
</style>
