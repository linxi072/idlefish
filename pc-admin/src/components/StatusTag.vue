<template>
  <el-tag :type="resolved.type" :size="size" :effect="effect" :round="round" disable-transitions>
    {{ resolved.text }}
  </el-tag>
</template>

<script>
/**
 * StatusTag —— 统一状态标签（ITER-U04 REQ-03）
 *
 * 三种用法：
 *   1) 显式文案 + 语义：<StatusTag text="已完成" type="success" />
 *   2) 值 + 字典：      <StatusTag :value="row.status" :map="ORDER_STATUS" />
 *   3) 仅值（原样展示）：<StatusTag :value="row.status" />
 *
 * 字典项支持 { text, type } 或旧格式 { text, tag }，缺省 type 为 info。
 */
export default {
  name: 'StatusTag',
  props: {
    value: { type: [String, Number], default: null },
    text: { type: String, default: null },
    type: { type: String, default: 'info' },
    map: { type: Object, default: null },
    size: { type: String, default: 'default' },
    effect: { type: String, default: 'light' },
    round: { type: Boolean, default: false }
  },
  computed: {
    resolved() {
      const entry = this.map && this.value != null ? this.map[this.value] : null;
      if (entry != null) {
        if (typeof entry === 'object') {
          return {
            text: entry.text != null ? entry.text : (entry.label != null ? entry.label : String(this.value)),
            type: entry.type || entry.tag || 'info'
          };
        }
        return { text: String(entry), type: this.type };
      }
      if (this.text != null) return { text: this.text, type: this.type };
      return { text: this.value == null ? '' : String(this.value), type: this.type };
    }
  }
};
</script>
