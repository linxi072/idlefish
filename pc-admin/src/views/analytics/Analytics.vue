<template>

  <div v-loading="loading">
    <page-header title="运营 BI 驾驶舱">
      <template #extra>
        <el-select v-model="days" size="small" style="width:120px" @change="load">
          <el-option :value="7" label="近 7 天"></el-option>
          <el-option :value="30" label="近 30 天"></el-option>
          <el-option :value="90" label="近 90 天"></el-option>
        </el-select>
        <el-button size="small" type="primary" plain @click="exportSummary">导出概览</el-button>
      </template>
    </page-header>

    <div class="stat-grid" v-if="overview">
      <div class="stat-card"><div class="stat-num">{{ yuan(overview.gmv) }}</div><div class="stat-label">GMV（元）</div></div>
      <div class="stat-card"><div class="stat-num">{{ fmt(overview.paidOrderCount) }}</div><div class="stat-label">支付订单</div></div>
      <div class="stat-card warn"><div class="stat-num">{{ pct(overview.refundRate) }}</div><div class="stat-label">退款率</div></div>
      <div class="stat-card"><div class="stat-num">{{ yuan(overview.avgOrderValue) }}</div><div class="stat-label">客单价（元）</div></div>
      <div class="stat-card"><div class="stat-num">{{ fmt(overview.userCount) }}</div><div class="stat-label">注册用户</div></div>
      <div class="stat-card"><div class="stat-num">{{ fmt(overview.itemOnsaleCount) }}</div><div class="stat-label">在售商品</div></div>
    </div>

    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>转化漏斗</span>
            <el-button size="small" style="float:right" @click="exportFunnel">导出</el-button>
          </template>
          <div ref="funnelChart" style="height:300px" v-if="hasEcharts"></div>
          <div class="bars" v-else>
            <div class="bar-item" v-for="(s,i) in funnel" :key="i">
              <div class="bar" :style="{height:(s.count/1300)+'px'}"></div>
              <div class="bar-val">{{ fmt(s.count) }}</div>
              <div class="bar-x">{{ s.stage }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>品类 GMV 分布</span>
            <el-button size="small" style="float:right" @click="exportCategory">导出</el-button>
          </template>
          <div ref="categoryChart" style="height:300px" v-if="hasEcharts"></div>
          <el-table :data="category" size="small" max-height="300" v-else>
            <el-table-column prop="categoryName" label="类目"></el-table-column>
            <el-table-column prop="orderCount" label="订单数"></el-table-column>
            <el-table-column label="GMV(元)"><template #default="{row}">{{ yuan(row.gmv) }}</template></el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>商品成色分布</span>
            <el-button size="small" style="float:right" @click="exportCondition">导出</el-button>
          </template>
          <div ref="conditionChart" style="height:280px" v-if="hasEcharts"></div>
          <el-table :data="condition" size="small" max-height="280" v-else>
            <el-table-column label="成色"><template #default="{row}">{{ COND_LABEL[row.conditionLevel] || row.conditionLevel }}</template></el-table-column>
            <el-table-column prop="count" label="商品数"></el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span>每日 GMV 趋势</span></template>
          <el-table :data="(overview && overview.trend || [])" size="small" max-height="280">
            <el-table-column prop="day" label="日期"></el-table-column>
            <el-table-column label="GMV(元)"><template #default="{row}">{{ yuan(row.gmv) }}</template></el-table-column>
            <el-table-column prop="paidCount" label="支付订单"></el-table-column>
            <el-table-column prop="orderCount" label="订单总数"></el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { analyticsApi, toCsv, downloadCsv } from '@/api';
import { formatMixin, notifyError } from '@/utils/format';
// pc-admin/src/views/analytics.js —— 运营 BI 驾驶舱（F-15.5）
// KPI 卡片 + 转化漏斗 + 品类 GMV 分布 + 商品成色分布 + CSV 导出
// echarts 优先渲染，无 echarts 时降级为条形图

const COND_LABEL = { 1: '全新', 2: '95新', 3: '9成新', 4: '8成新', 5: '功能完好' };
const fmt = (n) => (n || 0).toLocaleString('zh-CN');


export default {
  name: 'Analytics',
  mixins: [formatMixin],
  data() {
    return {
      loading: true,
      days: 30,
      overview: null,
      funnel: [],
      category: [],
      condition: [],
      hasEcharts: false,
      charts: {}
    };
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try {
        const [ov, fn, ct, cd] = await Promise.all([
          analyticsApi.overview(this.days),
          analyticsApi.funnel(this.days),
          analyticsApi.category(this.days),
          analyticsApi.condition()
        ]);
        this.overview = ov;
        this.funnel = fn;
        this.category = ct;
        this.condition = cd;
        this.hasEcharts = !!window.echarts;
        this.$nextTick(() => this.renderCharts());
      } catch (e) {
        notifyError(this, e, '加载运营数据失败');
      } finally {
        this.loading = false;
      }
    },
    renderCharts() {
      if (!this.hasEcharts) return;
      this.renderFunnel();
      this.renderCategory();
      this.renderCondition();
    },
    renderFunnel() {
      const ref = this.$refs.funnelChart;
      if (!ref) return;
      if (this.charts.funnel) this.charts.funnel.dispose();
      const c = window.echarts.init(ref);
      c.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        series: [{
          type: 'funnel', sort: 'none', gap: 2,
          label: { formatter: '{b}\n{c}' },
          data: this.funnel.map(s => ({ name: s.stage, value: s.count }))
        }]
      });
      this.charts.funnel = c;
    },
    renderCategory() {
      const ref = this.$refs.categoryChart;
      if (!ref) return;
      if (this.charts.category) this.charts.category.dispose();
      const c = window.echarts.init(ref);
      c.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        legend: { type: 'scroll', bottom: 0 },
        series: [{
          type: 'pie', radius: ['40%', '70%'],
          data: this.category.map(r => ({ name: r.categoryName, value: r.gmv }))
        }]
      });
      this.charts.category = c;
    },
    renderCondition() {
      const ref = this.$refs.conditionChart;
      if (!ref) return;
      if (this.charts.condition) this.charts.condition.dispose();
      const c = window.echarts.init(ref);
      c.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        series: [{
          type: 'pie', radius: ['40%', '70%'],
          data: this.condition.map(r => ({ name: COND_LABEL[r.conditionLevel] || r.conditionLevel, value: r.count }))
        }]
      });
      this.charts.condition = c;
    },
    pct(v) { return ((v || 0) * 100).toFixed(2) + '%'; },
    // ===== 导出 =====
    exportSummary() {
      const o = this.overview;
      if (!o) return;
      const headers = ['指标', '数值'];
      const rows = [
        ['GMV(元)', (o.gmv / 100).toFixed(2)],
        ['支付订单数', o.paidOrderCount],
        ['订单总数', o.totalOrderCount],
        ['退款订单数', o.refundOrderCount],
        ['退款率', this.pct(o.refundRate)],
        ['客单价(元)', (o.avgOrderValue / 100).toFixed(2)],
        ['注册用户数', o.userCount],
        ['在售商品数', o.itemOnsaleCount],
        ['商品总数', o.itemTotalCount]
      ];
      downloadCsv('analytics-summary-' + this.days + 'd.csv', toCsv(headers, rows));
    },
    exportFunnel() {
      const headers = ['阶段', '事件数', '环比转化率', '整体转化率'];
      const rows = this.funnel.map(s => [s.stage, s.count, this.pct(s.stepRate), this.pct(s.overallRate)]);
      downloadCsv('analytics-funnel-' + this.days + 'd.csv', toCsv(headers, rows));
    },
    exportCategory() {
      const headers = ['类目ID', '类目名称', '订单数', 'GMV(分)', 'GMV(元)'];
      const rows = this.category.map(c => [c.categoryId, c.categoryName, c.orderCount, c.gmv, (c.gmv / 100).toFixed(2)]);
      downloadCsv('analytics-category-' + this.days + 'd.csv', toCsv(headers, rows));
    },
    exportCondition() {
      const headers = ['成色等级', '成色描述', '商品数'];
      const rows = this.condition.map(c => [c.conditionLevel, COND_LABEL[c.conditionLevel] || c.conditionLevel, c.count]);
      downloadCsv('analytics-condition.csv', toCsv(headers, rows));
    }
  },
  created() { this.fmt = fmt; this.COND_LABEL = COND_LABEL; }
}
</script>
