// pc-admin/src/views/wallet.js —— 钱包 / 提现管理与资金对账（F-PC-02）
import { walletApi } from '../api.js';

export default {
  name: 'Wallet',
  data() {
    return {
      // 提现
      wdList: [], wdLoading: false, wdStatusFilter: '',
      // 对账
      reconDay: '', reconLoading: false, recon: null
    };
  },
  computed: {
    filteredWd() {
      if (!this.wdStatusFilter) return this.wdList;
      return this.wdList.filter(w => w.status === this.wdStatusFilter);
    }
  },
  mounted() { this.loadWithdrawals(); this.loadRecon(); },
  methods: {
    async loadWithdrawals() {
      this.wdLoading = true;
      try { this.wdList = await walletApi.withdrawals(); }
      catch (e) { this.$message.error('加载提现列表失败'); }
      finally { this.wdLoading = false; }
    },
    wdTag(s) {
      return { pending: 'warning', approved: 'success', rejected: 'danger', done: 'info' }[s] || 'info';
    },
    wdText(s) {
      return { pending: '待审核', approved: '已通过', rejected: '已驳回', done: '已打款' }[s] || s;
    },
    yuan(fen) { return '¥' + ((fen || 0) / 100).toFixed(2); },
    canAudit(w) { return w.status === 'pending'; },
    async approve(w) {
      try {
        await this.$confirm(`确认通过该提现申请（${this.yuan(w.amount)}）并实际出账？`, '审批通过', { type: 'warning' });
      } catch { return; }
      try {
        await walletApi.approveWithdrawal(w.id);
        this.$message.success('已通过，启动出账');
        this.loadWithdrawals();
      } catch (e) { this.$message.error('操作失败'); }
    },
    async reject(w) {
      try {
        await this.$confirm(`确认驳回该提现申请（${this.yuan(w.amount)}）？资金将解冻。`, '驳回提现', { type: 'warning' });
      } catch { return; }
      try {
        await walletApi.rejectWithdrawal(w.id);
        this.$message.warning('已驳回，资金解冻');
        this.loadWithdrawals();
      } catch (e) { this.$message.error('操作失败'); }
    },
    async loadRecon() {
      this.reconLoading = true;
      try { this.recon = await walletApi.reconciliation(this.reconDay || ''); }
      catch (e) { this.$message.error('加载对账报表失败'); this.recon = null; }
      finally { this.reconLoading = false; }
    },
    reconTypeTag(t) { return t === 'refund' ? 'danger' : 'success'; },
    reconTypeText(t) { return t === 'refund' ? '退款' : '支付'; }
  },
  template: `
  <div>
    <h2 class="page-title">钱包 / 提现与对账</h2>

    <el-tabs>
      <el-tab-pane label="提现管理">
        <el-card shadow="never">
          <el-form inline>
            <el-form-item label="状态">
              <el-select v-model="wdStatusFilter" placeholder="全部" clearable style="width:140px">
                <el-option label="待审核" value="pending"></el-option>
                <el-option label="已通过" value="approved"></el-option>
                <el-option label="已驳回" value="rejected"></el-option>
                <el-option label="已打款" value="done"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item><el-button type="primary" @click="loadWithdrawals">刷新</el-button></el-form-item>
          </el-form>

          <el-table :data="filteredWd" v-loading="wdLoading" border stripe>
            <el-table-column label="申请ID" prop="id" width="100"></el-table-column>
            <el-table-column label="用户ID" prop="userId" width="100"></el-table-column>
            <el-table-column label="提现金额" width="140">
              <template #default="{row}"><span class="amount">{{ yuan(row.amount) }}</span></template>
            </el-table-column>
            <el-table-column label="提现账户" prop="account" min-width="200"></el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{row}"><el-tag :type="wdTag(row.status)" size="small">{{ wdText(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="申请时间" prop="createdAt" width="170"></el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{row}">
                <el-button size="small" type="success" :disabled="!canAudit(row)" @click="approve(row)">通过</el-button>
                <el-button size="small" type="danger" :disabled="!canAudit(row)" @click="reject(row)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top:12px;color:#909399">共 {{ filteredWd.length }} 条；已处理的申请不可重复审批。</div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="资金对账">
        <el-card shadow="never">
          <el-form inline>
            <el-form-item label="对账日期">
              <el-date-picker v-model="reconDay" type="date" value-format="YYYY-MM-DD" placeholder="默认昨日" style="width:180px"></el-date-picker>
            </el-form-item>
            <el-form-item><el-button type="primary" :loading="reconLoading" @click="loadRecon">查询</el-button></el-form-item>
          </el-form>

          <div v-if="recon" v-loading="reconLoading">
            <el-row :gutter="16" class="recon-cards">
              <el-col :span="4"><div class="rc"><div class="rc-num">{{ recon.orderCount }}</div><div class="rc-label">交易笔数</div></div></el-col>
              <el-col :span="4"><div class="rc"><div class="rc-num success">{{ recon.paySuccess }}</div><div class="rc-label">支付成功</div></div></el-col>
              <el-col :span="4"><div class="rc"><div class="rc-num danger">{{ recon.payFail }}</div><div class="rc-label">支付失败</div></div></el-col>
              <el-col :span="4"><div class="rc"><div class="rc-num warning">{{ recon.refundCount }}</div><div class="rc-label">退款笔数</div></div></el-col>
              <el-col :span="4"><div class="rc"><div class="rc-num">{{ yuan(recon.platformFeeFen) }}</div><div class="rc-label">平台佣金</div></div></el-col>
              <el-col :span="4"><div class="rc"><div class="rc-num primary">{{ yuan(recon.netFen) }}</div><div class="rc-label">净入账</div></div></el-col>
            </el-row>
            <div class="recon-date">对账日期：{{ recon.date }}</div>

            <el-table :data="recon.details || []" border stripe style="margin-top:12px">
              <el-table-column label="业务单号" prop="bizNo" min-width="180"></el-table-column>
              <el-table-column label="类型" width="100">
                <template #default="{row}"><el-tag :type="reconTypeTag(row.type)" size="small">{{ reconTypeText(row.type) }}</el-tag></template>
              </el-table-column>
              <el-table-column label="金额" width="140"><template #default="{row}"><span class="amount">{{ yuan(row.amountFen) }}</span></template></el-table-column>
              <el-table-column label="手续费" width="140"><template #default="{row}"><span class="amount">{{ yuan(row.feeFen) }}</span></template></el-table-column>
              <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status==='success'?'success':'danger'" size="small">{{ row.status==='success'?'成功':'失败' }}</el-tag></template></el-table-column>
              <el-table-column label="时间" prop="time" width="170"></el-table-column>
            </el-table>
          </div>
          <div v-else-if="!reconLoading" class="empty-tip">暂无对账数据</div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>`
};
