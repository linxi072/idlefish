// pc-admin/src/views/dashboard.js
import { adminApi } from '../api.js';
import { formatMixin, notifyError } from '../utils/format.js';

const fmt = (n) => (n || 0).toLocaleString('zh-CN');

export default {
  name: 'Dashboard',
  mixins: [formatMixin],
  data() {
    return { loading: true, s: {}, hasEcharts: false, chart: null };
  },
  mounted() { this.load(); },
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
      const t = this.s.trend || [];
      this.chart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 36, right: 16, top: 24, bottom: 28 },
        xAxis: { type: 'category', data: t.map((_, i) => 'D' + (i + 1)), axisLine: { lineStyle: { color: '#ccc' } } },
        yAxis: { type: 'value', splitLine: { lineStyle: { color: '#f0f0f0' } } },
        series: [{
          type: 'line', smooth: true, data: t,
          symbolSize: 7, lineStyle: { width: 3, color: '#FFB300' },
          itemStyle: { color: '#FFB300' },
          areaStyle: { color: 'rgba(255,179,0,0.15)' }
        }]
      });
    }
  },
  template: `
  <div v-loading="loading">
    <h2 class="page-title">数据概览</h2>
    <div class="stat-grid">
      <div class="stat-card"><div class="stat-num">{{ fmt(s.gmv) }}</div><div class="stat-label">累计 GMV（元）</div></div>
      <div class="stat-card"><div class="stat-num">{{ s.orderCnt }}</div><div class="stat-label">订单总数</div></div>
      <div class="stat-card"><div class="stat-num">{{ s.userCnt }}</div><div class="stat-label">注册用户</div></div>
      <div class="stat-card"><div class="stat-num">{{ s.itemCnt }}</div><div class="stat-label">在售商品</div></div>
      <div class="stat-card warn"><div class="stat-num">{{ s.pendingReview }}</div><div class="stat-label">待审核商品</div></div>
      <div class="stat-card danger"><div class="stat-num">{{ s.refunding }}</div><div class="stat-label">退款处理中</div></div>
    </div>

    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="14">
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
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span>待办事项</span></template>
          <ul class="todo">
            <li><el-tag type="warning">审核</el-tag> {{ s.pendingReview }} 个商品等待人工审核</li>
            <li><el-tag type="danger">退款</el-tag> {{ s.refunding }} 笔退款需处理</li>
            <li><el-tag>新增</el-tag> 今日新增注册 {{ s.todayRegister }} 人</li>
          </ul>
        </el-card>
      </el-col>
    </el-row>
  </div>`,
  created() { this.fmt = fmt; }
};
