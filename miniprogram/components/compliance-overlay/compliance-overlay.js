// compliance-overlay —— 通用合规浮层（F-08：抽奖实名 / 秒杀风险 / 活动规则告知）
// 用法：<compliance-overlay show="{{show}}" title="" content="{{[]}}" confirm-text=""
//        bind:confirm="onConfirm" bind:close="onClose" />
Component({
  properties: {
    show: { type: Boolean, value: false },
    title: { type: String, value: '合规提示' },
    content: { type: Array, value: [] },
    confirmText: { type: String, value: '我已知晓并同意' }
  },
  methods: {
    onClose() { this.triggerEvent('close'); },
    onConfirm() { this.triggerEvent('confirm'); },
    // 阻止冒泡：点击卡片不关闭浮层
    stop() {}
  }
});
