<template>
  <div class="empty-state" :class="'empty-state--' + size" role="status" aria-live="polite">
    <el-icon class="empty-state__icon"><component :is="icon" /></el-icon>
    <p class="empty-state__text">{{ text }}</p>
    <p v-if="desc" class="empty-state__desc">{{ desc }}</p>
    <div v-if="$slots.default" class="empty-state__extra"><slot /></div>
  </div>
</template>

<script>
/**
 * EmptyState —— 统一空态（ITER-U04 REQ-03）
 *
 * 用法：
 *   <EmptyState text="暂无用户" desc="调整筛选条件后重试" />
 *   <el-table ...><template #empty><EmptyState text="暂无订单" /></template></el-table>
 */
export default {
  name: 'EmptyState',
  props: {
    text: { type: String, default: '暂无数据' },
    desc: { type: String, default: '' },
    icon: { type: String, default: 'Box' },
    size: { type: String, default: 'md', validator: (v) => ['sm', 'md', 'lg'].includes(v) }
  }
};
</script>

<style scoped>
.empty-state {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: var(--sp-8) var(--sp-4); color: var(--text-3); text-align: center;
}
.empty-state--sm { padding: var(--sp-5) var(--sp-3); }
.empty-state--lg { padding: 56px var(--sp-6); }
.empty-state__icon { font-size: 40px; color: var(--border-strong); margin-bottom: var(--sp-3); }
.empty-state--sm .empty-state__icon { font-size: 28px; margin-bottom: var(--sp-2); }
.empty-state--lg .empty-state__icon { font-size: 52px; }
.empty-state__text { margin: 0; font-size: var(--fs-body); color: var(--text-2); }
.empty-state__desc { margin: var(--sp-1) 0 0; font-size: var(--fs-xs); color: var(--text-3); }
.empty-state__extra { margin-top: var(--sp-3); }
</style>
