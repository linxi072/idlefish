// pc-admin/src/views/risk.js —— 风控事件 + 审计日志
import { adminApi } from '../api.js';
import { formatMixin, notifyError } from '../utils/format.js';

export default {
  name: 'Risk',
  mixins: [formatMixin],
  data() {
    return {
      tab: 'risk', keyword: '', events: [], logs: [], rules: [], loading: false,
      ruleDialog: false, ruleForm: null, saving: false,
      ruleTypes: ['FREQUENCY', 'DEVICE', 'KEYWORD', 'CREDIT', 'PRICE']
    };
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try {
        if (this.tab === 'risk') {
          const r = await adminApi.risk({ keyword: this.keyword });
          this.events = Array.isArray(r) ? r : (r.list || []);
        } else if (this.tab === 'audit') {
          const r = await adminApi.auditLog({ keyword: this.keyword });
          const arr = Array.isArray(r) ? r : (r.list || []);
          this.logs = arr.map(l => ({
            opId: l.opId || l.operatorId,
            operator: l.operator || l.operatorId,
            target: l.target || (l.targetType && l.targetId ? l.targetType + ':' + l.targetId : ''),
            action: l.action,
            createdAt: l.createdAt
          }));
        } else if (this.tab === 'rules') {
          this.rules = await adminApi.riskRules();
        }
      } catch (e) { notifyError(this, e, '加载失败'); }
      finally { this.loading = false; }
    },
    onTab(name) { this.tab = name; this.load(); },
    actionTag(a) {
      return { '放行': 'success', '人工复核': 'warning', '拦截': 'danger', '通过审核': 'success', '封禁账号': 'danger' }[a] || 'info';
    },
    levelTag(l) { return { low: 'info', mid: 'warning', high: 'danger' }[l] || 'info'; },
    scopeTag(s) { return { user: 'primary', device: 'success' }[s] || 'info'; },
    blankRule() {
      return { code: '', name: '', type: 'FREQUENCY', level: 'low', scope: 'user', threshold: 0, windowMin: 1, priority: 99, enabled: 1, description: '' };
    },
    addRule() {
      const r = this.blankRule();
      this.rules.push(r);
      this.ruleForm = r;
      this.ruleDialog = true;
    },
    editRule(r) { this.ruleForm = r; this.ruleDialog = true; },
    saveRule() { this.ruleDialog = false; },
    removeRule(r) {
      const i = this.rules.indexOf(r);
      if (i >= 0) this.rules.splice(i, 1);
    },
    async saveAllRules() {
      this.saving = true;
      try {
        await adminApi.saveRiskRules(this.rules);
        this.$message.success('规则已保存并热加载');
      } catch (e) { notifyError(this, e, '保存失败'); }
      finally { this.saving = false; }
    }
  },
  template: `
  <div>
    <h2 class="page-title">风控与审计</h2>
      <el-card shadow="never">
      <el-form inline style="margin-bottom:12px">
        <el-form-item label="关键词"><el-input v-model="keyword" placeholder="规则名 / 动作 / 对象" clearable></el-input></el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
      </el-form>
      <el-tabs @tab-change="onTab">
        <el-tab-pane label="风控事件" name="risk"></el-tab-pane>
        <el-tab-pane label="审计日志" name="audit"></el-tab-pane>
        <el-tab-pane label="规则库" name="rules"></el-tab-pane>
      </el-tabs>
      <el-table v-if="tab==='risk'" :data="events" v-loading="loading" border stripe>
        <el-table-column label="事件ID" prop="id" width="90"></el-table-column>
        <el-table-column label="用户ID" prop="userId" width="100"></el-table-column>
        <el-table-column label="命中规则" prop="rule" width="180"></el-table-column>
        <el-table-column label="风险分" prop="score" width="100"><template #default="{row}"><el-tag :type="row.score>=80?'danger':row.score>=60?'warning':'success'">{{ row.score }}</el-tag></template></el-table-column>
        <el-table-column label="处置" width="120"><template #default="{row}"><el-tag :type="actionTag(row.action)">{{ row.action }}</el-tag></template></el-table-column>
        <el-table-column label="时间" prop="createdAt" width="180"></el-table-column>
      </el-table>
      <el-table v-if="tab==='audit'" :data="logs" v-loading="loading" border stripe>
        <el-table-column label="操作ID" prop="opId" width="90"></el-table-column>
        <el-table-column label="操作人" prop="operator" width="120"></el-table-column>
        <el-table-column label="对象" prop="target" width="160"></el-table-column>
        <el-table-column label="动作" width="140"><template #default="{row}"><el-tag :type="actionTag(row.action)">{{ row.action }}</el-tag></template></el-table-column>
        <el-table-column label="时间" prop="createdAt" width="180"></el-table-column>
      </el-table>

      <el-card v-if="tab==='rules'" shadow="never" v-loading="loading">
        <div style="margin-bottom:12px;display:flex;gap:8px;align-items:center;flex-wrap:wrap">
          <el-button type="primary" @click="addRule">新增规则</el-button>
          <el-button type="success" :loading="saving" @click="saveAllRules">保存全部（热加载）</el-button>
          <span style="color:#909399;font-size:12px">配置改动经「保存全部」批量落库并由引擎热加载，无需重启</span>
        </div>
        <el-table :data="rules" border stripe>
          <el-table-column label="规则码" prop="code" width="170"></el-table-column>
          <el-table-column label="名称" prop="name" min-width="120"></el-table-column>
          <el-table-column label="类型" width="120"><template #default="{row}"><el-tag>{{ row.type }}</el-tag></template></el-table-column>
          <el-table-column label="等级" width="90"><template #default="{row}"><el-tag :type="levelTag(row.level)">{{ row.level }}</el-tag></template></el-table-column>
          <el-table-column label="维度" width="90"><template #default="{row}"><el-tag :type="scopeTag(row.scope)">{{ row.scope }}</el-tag></template></el-table-column>
          <el-table-column label="阈值" prop="threshold" width="90"></el-table-column>
          <el-table-column label="窗口(分)" prop="windowMin" width="100"></el-table-column>
          <el-table-column label="优先级" prop="priority" width="80"></el-table-column>
          <el-table-column label="启用" width="90"><template #default="{row}"><el-switch v-model="row.enabled" :active-value="1" :inactive-value="0"></el-switch></template></el-table-column>
          <el-table-column label="说明" prop="description" min-width="160"></el-table-column>
          <el-table-column label="操作" width="140" fixed="right"><template #default="{row}">
            <el-button size="small" @click="editRule(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="removeRule(row)">删除</el-button>
          </template></el-table-column>
        </el-table>

        <el-dialog v-model="ruleDialog" title="编辑风控规则" width="520px">
          <el-form label-width="90px" v-if="ruleForm">
            <el-form-item label="规则码"><el-input v-model="ruleForm.code" :disabled="!!ruleForm.id"></el-input></el-form-item>
            <el-form-item label="名称"><el-input v-model="ruleForm.name"></el-input></el-form-item>
            <el-form-item label="类型"><el-select v-model="ruleForm.type" style="width:100%">
              <el-option v-for="t in ruleTypes" :key="t" :label="t" :value="t"></el-option></el-select></el-form-item>
            <el-form-item label="等级"><el-select v-model="ruleForm.level" style="width:100%">
              <el-option label="low" value="low"></el-option><el-option label="mid" value="mid"></el-option><el-option label="high" value="high"></el-option></el-select></el-form-item>
            <el-form-item label="维度"><el-select v-model="ruleForm.scope" style="width:100%">
              <el-option label="user" value="user"></el-option><el-option label="device" value="device"></el-option></el-select></el-form-item>
            <el-form-item label="阈值"><el-input-number v-model="ruleForm.threshold"></el-input-number></el-form-item>
            <el-form-item label="窗口(分)"><el-input-number v-model="ruleForm.windowMin" :min="1"></el-input-number></el-form-item>
            <el-form-item label="优先级"><el-input-number v-model="ruleForm.priority"></el-input-number></el-form-item>
            <el-form-item label="启用"><el-switch v-model="ruleForm.enabled" :active-value="1" :inactive-value="0"></el-switch></el-form-item>
            <el-form-item label="说明"><el-input type="textarea" v-model="ruleForm.description"></el-input></el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="ruleDialog=false">取消</el-button>
            <el-button type="primary" @click="saveRule">确定</el-button>
          </template>
        </el-dialog>
      </el-card>
    </el-card>
  </div>`
};
