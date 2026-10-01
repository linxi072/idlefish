<template>

  <div v-loading="loading">
    <page-header title="营销驾驶舱" />

    <div class="stat-grid" v-if="overview">
      <div class="stat-card"><div class="stat-num">{{ fmt(overview.issuedCount) }}</div><div class="stat-label">券发放量</div></div>
      <div class="stat-card warn"><div class="stat-num">{{ pct(overview.redemptionRate) }}</div><div class="stat-label">核销率</div></div>
      <div class="stat-card"><div class="stat-num">{{ fmt(overview.redeemedOrderCount) }}</div><div class="stat-label">核销订单数</div></div>
      <div class="stat-card"><div class="stat-num">{{ yuan(overview.redeemedGmv) }}</div><div class="stat-label">核销 GMV</div></div>
      <div class="stat-card"><div class="stat-num">{{ yuan(overview.discountCost) }}</div><div class="stat-label">优惠成本</div></div>
      <div class="stat-card good"><div class="stat-num">{{ roiText(overview.roi) }}</div><div class="stat-label">营销 ROI</div></div>
    </div>

    <el-card shadow="never" style="margin-top:20px">
      <template #header><span>券类型发放分布</span></template>
      <div ref="typeChart" style="height:320px" v-if="hasEcharts"></div>
      <el-table :data="typeDist" size="small" max-height="320" v-else>
        <el-table-column label="券类型"><template #default="{row}">{{ TYPE_LABEL[row.type] || row.type }}</template></el-table-column>
        <el-table-column prop="count" label="发放量"></el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { marketingApi } from '@/api';
import { formatMixin, notifyError } from '@/utils/format';
// pc-admin/src/views/marketing.js —— 营销驾驶舱（F-13.5）
// 券营销 KPI（发放量 / 核销率 / 核销 GMV / 优惠成本 / ROI）+ 券类型分布
// echarts 优先渲染，无 echarts 时降级为表格

const TYPE_LABEL = { FULL_REDUCTION: '满减券', NO_THRESHOLD: '无门槛券', DISCOUNT: '折扣券' };
const fmt = (n) => (n || 0).toLocaleString('zh-CN');


export default {
  name: 'Marketing',
  mixins: [formatMixin],
  data() {
    return {
      loading: true,
      overview: null,
      typeDist: [],
      hasEcharts: false,
      chart: null
    };
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try {
        const [ov, td] = await Promise.all([
          marketingApi.overview(),
          marketingApi.typeDist()
        ]);
        this.overview = ov;
        this.typeDist = td || [];
        this.hasEcharts = !!window.echarts;
        this.$nextTick(() => this.renderChart());
      } catch (e) {
        notifyError(this, e, '加载营销数据失败');
      } finally {
        this.loading = false;
      }
    },
    renderChart() {
      if (!this.hasEcharts) return;
      const ref = this.$refs.typeChart;
      if (!ref) return;
      if (this.chart) this.chart.dispose();
      const c = window.echarts.init(ref);
      c.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        legend: { type: 'scroll', bottom: 0 },
        series: [{
          type: 'pie', radius: ['40%', '70%'],
          data: this.typeDist.map(r => ({ name: TYPE_LABEL[r.type] || r.type, value: r.count }))
        }]
      });
      this.chart = c;
    },
    pct(v) { return ((v || 0) * 100).toFixed(2) + '%'; },
    roiText(v) { return ((v || 0)).toFixed(2) + 'x'; }
  },
  created() { this.fmt = fmt; this.TYPE_LABEL = TYPE_LABEL; }
}
</script>
