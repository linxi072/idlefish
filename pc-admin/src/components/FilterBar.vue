<template>
  <el-form inline class="filter-bar" @submit.prevent>
    <slot />
    <el-form-item v-if="showActions" class="filter-bar__actions">
      <slot name="actions">
        <el-button type="primary" :loading="loading" @click="$emit('search')">{{ searchText }}</el-button>
        <el-button @click="$emit('reset')">{{ resetText }}</el-button>
      </slot>
    </el-form-item>
  </el-form>
</template>

<script>
/**
 * FilterBar —— 统一筛选栏容器（ITER-U04 REQ-03）
 *
 * 用法：
 *   <FilterBar :loading="loading" @search="load" @reset="reset">
 *     <el-form-item label="关键词"><el-input v-model="keyword" /></el-form-item>
 *   </FilterBar>
 *
 * 默认渲染「查询 / 重置」按钮；需要自定义按钮时使用 #actions 插槽（此时不再渲染默认按钮）。
 * 布局样式来自 global.css 的 .filter-bar / .filter-bar__actions，本组件不持有色值。
 */
export default {
  name: 'FilterBar',
  props: {
    loading: { type: Boolean, default: false },
    showActions: { type: Boolean, default: true },
    searchText: { type: String, default: '查询' },
    resetText: { type: String, default: '重置' }
  },
  emits: ['search', 'reset']
};
</script>
