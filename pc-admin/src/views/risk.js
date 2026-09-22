// pc-admin/src/views/risk.js —— 风控事件 + 审计日志
import { adminApi } from '../api.js';

export default {
  name: 'Risk',
  data() {
    return { tab: 'risk', events: [], logs: [], loading: false };
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try {
        if (this.tab === 'risk') this.events = (await adminApi.risk()).list || [];
        else this.logs = await adminApi.auditLog();
      } finally { this.loading = false; }
    },
    onTab(v) { this.tab = v.name; this.load(); },
    actionTag(a) {
      return { '放行': 'success', '人工复核': 'warning', '拦截': 'danger', '通过审核': 'success', '封禁账号': 'danger' }[a] || 'info';
    }
  },
  template: `
  <div>
    <h2 class="page-title">风控与审计</h2>
    <el-card shadow="never">
      <el-tabs @tab-change="onTab">
        <el-tab-pane label="风控事件" name="risk"></el-tab-pane>
        <el-tab-pane label="审计日志" name="audit"></el-tab-pane>
      </el-tabs>
      <el-table v-if="tab==='risk'" :data="events" v-loading="loading" border stripe>
        <el-table-column label="事件ID" prop="id" width="90"></el-table-column>
        <el-table-column label="用户ID" prop="userId" width="100"></el-table-column>
        <el-table-column label="命中规则" prop="rule" width="180"></el-table-column>
        <el-table-column label="风险分" prop="score" width="100"><template #default="{row}"><el-tag :type="row.score>=80?'danger':row.score>=60?'warning':'success'">{{ row.score }}</el-tag></template></el-table-column>
        <el-table-column label="处置" width="120"><template #default="{row}"><el-tag :type="actionTag(row.action)">{{ row.action }}</el-tag></template></el-table-column>
        <el-table-column label="时间" prop="createdAt" width="180"></el-table-column>
      </el-table>
      <el-table v-else :data="logs" v-loading="loading" border stripe>
        <el-table-column label="操作ID" prop="opId" width="90"></el-table-column>
        <el-table-column label="操作人" prop="operator" width="120"></el-table-column>
        <el-table-column label="对象" prop="target" width="160"></el-table-column>
        <el-table-column label="动作" width="140"><template #default="{row}"><el-tag :type="actionTag(row.action)">{{ row.action }}</el-tag></template></el-table-column>
        <el-table-column label="时间" prop="createdAt" width="180"></el-table-column>
      </el-table>
    </el-card>
  </div>`
};
