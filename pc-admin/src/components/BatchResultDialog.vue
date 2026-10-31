<template>
  <el-dialog
    :model-value="modelValue"
    title="批量操作结果"
    width="680px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-if="result" class="batch-result">
      <div class="batch-result__stats">
        <div class="bs"><span class="bs__label">总数</span><b class="bs__num">{{ result.total }}</b></div>
        <div class="bs bs--ok"><span class="bs__label">成功</span><b class="bs__num">{{ result.success }}</b></div>
        <div class="bs bs--skip"><span class="bs__label">跳过</span><b class="bs__num">{{ result.skipped }}</b></div>
        <div class="bs bs--fail"><span class="bs__label">失败</span><b class="bs__num">{{ result.failed }}</b></div>
      </div>
      <p class="batch-result__hint">
        部分失败不回滚：成功项已生效，失败项可一键重试。审计日志 ID：{{ result.auditLogId == null ? '—' : result.auditLogId }}
      </p>
      <el-table :data="result.items || []" border size="small" max-height="320">
        <el-table-column prop="targetId" label="目标 ID" width="150" />
        <el-table-column label="结果" width="110">
          <template #default="{row}"><StatusTag :value="row.outcome" :map="OUTCOME" size="small" /></template>
        </el-table-column>
        <el-table-column prop="reason" label="说明" />
      </el-table>
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
      <el-button
        v-if="result && result.failedIds && result.failedIds.length"
        type="primary"
        @click="$emit('retry', result.failedIds)"
      >
        重试失败项（{{ result.failedIds.length }}）
      </el-button>
    </template>
  </el-dialog>
</template>

<script>
/**
 * BatchResultDialog —— 批量操作结果弹窗（ITER-U03 前端）
 *
 * 展示 BatchResultVO 的 总数/成功/跳过/失败 与逐条明细；
 * failedIds 原样回传，由页面决定用哪组参数重试（部分失败不回滚，REQ-05）。
 */
const OUTCOME = {
  SUCCESS: { text: '成功', type: 'success' },
  SKIPPED: { text: '跳过', type: 'info' },
  FAILED: { text: '失败', type: 'danger' }
};

export default {
  name: 'BatchResultDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    result: { type: Object, default: null }
  },
  emits: ['update:modelValue', 'retry'],
  data() { return { OUTCOME }; }
};
</script>

<style scoped>
.batch-result__stats {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--sp-3); margin-bottom: var(--sp-3);
}
.bs {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  padding: var(--sp-3); border-radius: var(--radius-md);
  background: var(--surface-subtle); border: 1px solid var(--border);
}
.bs__label { font-size: var(--fs-xs); color: var(--text-3); }
.bs__num { font-size: var(--fs-h2); color: var(--text-1); }
.bs--ok { border-color: var(--success); }
.bs--ok .bs__num { color: var(--success); }
.bs--skip { border-color: var(--border-strong); }
.bs--skip .bs__num { color: var(--text-2); }
.bs--fail { border-color: var(--danger); }
.bs--fail .bs__num { color: var(--danger); }
.batch-result__hint { margin: 0 0 var(--sp-3); font-size: var(--fs-xs); color: var(--text-3); }
@media (max-width: 640px) {
  .batch-result__stats { grid-template-columns: repeat(2, 1fr); }
}
</style>
