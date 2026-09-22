// pc-admin/src/views/dashboard.js
import { adminApi } from '../api.js';

const fmt = (n) => (n || 0).toLocaleString('zh-CN');

export default {
  name: 'Dashboard',
  data() {
    return { loading: true, s: {} };
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try { this.s = await adminApi.stats(); } finally { this.loading = false; }
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
          <div class="bars">
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
  computed: {},
  created() { this.fmt = fmt; }
};
