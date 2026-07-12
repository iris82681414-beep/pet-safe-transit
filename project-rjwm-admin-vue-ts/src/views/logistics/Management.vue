<template>
  <div class="logistics-page">
    <div class="page-head">
      <div>
        <h2>车辆与货物管理台</h2>
        <p>车辆维护、运单创建、车辆货物绑定、状态上报。</p>
      </div>
      <el-button type="primary" icon="el-icon-plus">新增</el-button>
    </div>

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="车辆" name="vehicles">
          <el-table :data="vehicles">
            <el-table-column prop="plate" label="车牌" />
            <el-table-column prop="vehicleType" label="车型" />
            <el-table-column prop="capacity" label="载重" />
            <el-table-column prop="driverName" label="司机" />
            <el-table-column prop="deviceImei" label="设备 IMEI" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="货物" name="cargo">
          <el-table :data="cargo">
            <el-table-column prop="cargoId" label="运单号" />
            <el-table-column prop="cargoType" label="类型" />
            <el-table-column prop="status" label="状态" />
            <el-table-column prop="vehiclePlate" label="绑定车辆" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script lang="ts">
export default {
  name: 'LogisticsManagement',
  data() {
    return {
      activeTab: 'vehicles',
      vehicles: [],
      cargo: []
    }
  }
}
</script>

<style lang="scss" scoped>
@import './starter.scss';
</style>
