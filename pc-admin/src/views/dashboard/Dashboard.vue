<template>

  <div v-loading="loading">
    <page-header title="数据概览" />
    <div class="stat-grid">
      <div class="stat-card"><div class="stat-num">{{ fmt(s.gmv) }}</div><div class="stat-label">累计 GMV（元）</div></div>
      <div class="stat-card" style="cursor:pointer" @click="drill('/orders')">
        <div class="stat-num">{{ s.orderCnt }}</div><div class="stat-label">订单总数</div>
      </div>
      <div class="stat-card" style="cursor:pointer" @click="drill('/users')">
        <div class="stat-num">{{ s.userCnt }}</div><div class="stat-label">注册用户</div>
      </div>
      <div class="stat-card" style="cursor:pointer" @click="drill('/items', { status: 'onsale' })">
        <div class="stat-num">{{ s.itemCnt }}</div><div class="stat-label">在售商品</div>
      </div>
      <div class="stat-card warn" style="cursor:pointer" @click="drill('/items', { status: 'pending_review' })">
        <div class="stat-num">{{ s.pendingReview }}</div><div class="stat-label">待审核商品</div>
      </div>
      <div class="stat-card danger" style="cursor:pointer" @click="drill('/orders', { status: 'refunding' })">
        <div class="stat-num">{{ s.refunding }}</div><div class="stat-label">退款处理中</div>
      </div>
    </div>

    <el-row :gutter="20" style="margin-top:20px">
      <el-col :xs="24" :sm="24" :md="14">
        <el-card shadow="never">
          <template #header><span>近 12 日成交趋势</span></template>
          <div ref="trendChart" style="height:240px" v-if="hasEcharts"></div>
          <div class="bars" v-else>
            <div class="bar-item" v-for="(v,i) in s.trend" :key="i">
              <div class="bar" :style="{height: (v/50*120)+'px'}"></div>
              <div class="bar-val">{{ v }}</div>
              <div class="bar-x">D{{ i+1 }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="10">
        <el-card shadow="never">
          <template #header><span>待办事项</span></template>
          <ul class="todo">
            <li style="cursor:pointer" @click="drill('/items', { status: 'pending_review' })">
              <el-tag type="warning">审核</el-tag> {{ s.pendingReview }} 个商品等待人工审核
            </li>
            <li style="cursor:pointer" @click="drill('/orders', { status: 'refunding' })">
              <el-tag type="danger">退款</el-tag> {{ s.refunding }} 笔退款需处理
            </li>
            <li style="cursor:pointer" @click="drill('/users')">
              <el-tag>新增</el-tag> 今日新增注册 {{ s.todayRegister }} 人
            </li>
          </ul>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { adminApi } from '@/api';
import { formatMixin, notifyError } from '@/utils/format';
import { chartPalette } from '@/utils/theme';
// pc-admin/src/views/dashboard.js

const fmt = (n) => (n || 0).toLocaleString('zh-CN');


export default {
  name: 'Dashboard',
  mixins: [formatMixin],
  data() {
    return { loading: true, s: {}, hasEcharts: false, chart: null };
  },
  methods: {
    async load() {
      this.loading = true;
      try {
        this.s = await adminApi.stats();
        if (window.echarts) {
          this.hasEcharts = true;
          this.$nextTick(() => this.renderChart());
        }
      } catch (e) { notifyError(this, e, '加载失败'); }
      finally { this.loading = false; }
    },
    renderChart() {
      if (!this.$refs.trendChart || !window.echarts) return;
      if (this.chart) this.chart.dispose();
      this.chart = window.echarts.init(this.$refs.trendChart);
      const p = chartPalette();
      const t = this.s.trend || [];
      this.chart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 36, right: 16, top: 24, bottom: 28 },
        xAxis: { type: 'category', data: t.map((_, i) => 'D' + (i + 1)), axisLine: { lineStyle: { color: p.axis } } },
        yAxis: { type: 'value', splitLine: { lineStyle: { color: p.split } } },
        series: [{
          type: 'line', smooth: true, data: t,
          symbolSize: 7, lineStyle: { width: 3, color: p.series[0] },
          itemStyle: { color: p.series[0] },
          areaStyle: { color: p.series[0], opacity: 0.15 }
        }]
      });
    },
    // REQ-07 BI 下钻统一入口：带维度 query 跳转明细列表
    drill(path, query) { this.$router.push({ path, query: query || {} }); }
  },
  created() { this.fmt = fmt; },
  mounted() {
    this.load();
    // 主题切换后按新令牌重绘图表（ECharts 不会自动跟随 CSS 变量）
    this._onTheme = () => this.renderChart();
    window.addEventListener('theme-change', this._onTheme);
  },
  beforeUnmount() {
    window.removeEventListener('theme-change', this._onTheme);
    if (this.chart) { this.chart.dispose(); this.chart = null; }
  }
}
</script>
