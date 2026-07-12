<template>
  <div class="logistics-page">
    <div class="page-head">
      <div>
        <h2>智能问答</h2>
        <p>推荐问题、RAG 回答、来源文档展示。</p>
      </div>
    </div>

    <el-card shadow="never">
      <div class="chat-shell">
        <div class="empty-state">等待 /assistant/chat 接入</div>
      </div>
      <div class="chat-input">
        <el-input v-model="question" placeholder="输入物流异常、告警或运单相关问题" />
        <el-button type="primary">发送</el-button>
      </div>
    </el-card>
  </div>
</template>

<script lang="ts">
export default {
  name: 'LogisticsAssistant',
  data() {
    return {
      question: ''
    }
  }
}
</script>

<style lang="scss" scoped>
@import './starter.scss';
</style>
