<template>
  <div class="page-bar">
    <el-pagination
      :layout="layout"
      :total="total"
      :page-size="size"
      :current-page="page"
      :page-sizes="pageSizes"
      :small="small"
      @current-change="onPage"
      @size-change="onSize"
    />
  </div>
</template>

<script>
/**
 * PageBar —— 统一分页条（ITER-U04 REQ-03）
 *
 * 用法（推荐单事件）：
 *   <PageBar :total="total" :page="page" :size="size" @change="onPageChange" />
 *   onPageChange({ page, size }) { this.page = page; this.size = size; this.load(); }
 *
 * 同时兼容旧写法：@current-change / @size-change 仍会派发，
 * 便于页面渐进替换而不改动既有处理函数。
 */
export default {
  name: 'PageBar',
  props: {
    total: { type: Number, default: 0 },
    page: { type: Number, default: 1 },
    size: { type: Number, default: 20 },
    pageSizes: { type: Array, default: () => [10, 20, 50, 100] },
    layout: { type: String, default: 'total, sizes, prev, pager, next, jumper' },
    small: { type: Boolean, default: false }
  },
  emits: ['update:page', 'update:size', 'change', 'current-change', 'size-change'],
  methods: {
    onPage(p) {
      this.$emit('update:page', p);
      this.$emit('current-change', p);
      this.$emit('change', { page: p, size: this.size });
    },
    onSize(s) {
      this.$emit('update:size', s);
      this.$emit('size-change', s);
      // 切换每页条数时回到第一页，避免越界空页
      this.$emit('change', { page: 1, size: s });
    }
  }
};
</script>
