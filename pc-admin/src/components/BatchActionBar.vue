<template>
  <div class="batch-bar" :class="{ 'batch-bar--active': count > 0 }" role="region" aria-label="批量操作">
    <div class="batch-bar__info">
      <el-icon class="batch-bar__icon"><Select /></el-icon>
      <span>已选 <b>{{ count }}</b> 项</span>
      <el-button v-if="count > 0" text size="small" @click="$emit('clear')">清空选择</el-button>
      <span v-if="count > limit" class="batch-bar__warn">单次最多 {{ limit }} 项，超出部分将被服务端拒绝</span>
    </div>
    <div class="batch-bar__actions">
      <slot />
    </div>
  </div>
</template>

<script>
/**
 * BatchActionBar —— 批量操作栏（ITER-U03 前端）
 *
 * 用法：
 *   <BatchActionBar :count="selection.length" @clear="clearSelection">
 *     <el-button type="success" :disabled="!selection.length" @click="batchPass">批量通过</el-button>
 *   </BatchActionBar>
 *
 * 仅负责展示与计数提示；上限与服务端 BatchPolicy（200）保持一致，供操作前提示。
 */
export default {
  name: 'BatchActionBar',
  props: {
    count: { type: Number, default: 0 },
    limit: { type: Number, default: 200 }
  },
  emits: ['clear']
};
</script>

<style scoped>
.batch-bar {
  display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap;
  gap: var(--sp-3); padding: var(--sp-2) var(--sp-3); margin-bottom: var(--sp-3);
  background: var(--surface-subtle); border: 1px solid var(--border);
  border-radius: var(--radius-md); color: var(--text-2); font-size: var(--fs-sm);
}
.batch-bar--active { border-color: var(--primary); background: var(--primary-soft); color: var(--text-1); }
.batch-bar__info { display: flex; align-items: center; gap: var(--sp-2); flex-wrap: wrap; }
.batch-bar__info b { color: var(--primary-strong); font-size: var(--fs-body); }
.batch-bar__icon { color: var(--primary); }
.batch-bar__warn { color: var(--danger); font-size: var(--fs-xs); }
.batch-bar__actions { display: flex; align-items: center; gap: var(--sp-2); flex-wrap: wrap; }
</style>
