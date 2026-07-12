<template>
  <div class="logistics-page">
    <div class="page-head">
      <div>
        <h2>告警中心</h2>
        <p>告警统计、列表、详情、确认与关闭。</p>
      </div>
      <div class="toolbar">
        <el-select v-model="severity" placeholder="级别" clearable>
          <el-option label="严重" value="CRITICAL" />
          <el-option label="警告" value="WARNING" />
          <el-option label="提示" value="INFO" />
        </el-select>
        <el-select v-model="alertStatus" placeholder="状态" clearable>
          <el-option label="待处理" value="PENDING" />
          <el-option label="已确认" value="ACKNOWLEDGED" />
          <el-option label="已关闭" value="RESOLVED" />
        </el-select>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="never"><div class="stat">0<span>待处理严重告警</span></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never"><div class="stat">0<span>待处理警告</span></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never"><div class="stat">0<span>今日已关闭</span></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never"><div class="stat">0<span>本月累计</span></div></el-card>
      </el-col>
    </el-row>

    <el-card class="mt16" shadow="never">
      <div slot="header">告警列表</div>
      <el-table :data="alerts">
        <el-table-column prop="alertId" label="告警编号" />
        <el-table-column prop="alertType" label="类型" />
        <el-table-column prop="severity" label="级别" />
        <el-table-column prop="vehiclePlate" label="车辆" />
        <el-table-column prop="status" label="状态" />
        <el-table-column label="操作" width="180">
          <el-button size="mini" plain>确认</el-button>
          <el-button size="mini" plain>关闭</el-button>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script lang="ts">
export default {
  name: 'LogisticsAlerts',
  data() {
    return {
      severity: '',
      alertStatus: '',
      alerts: []
    }
  }
}
</script>

<style lang="scss" scoped>
@import './starter.scss';
</style>
