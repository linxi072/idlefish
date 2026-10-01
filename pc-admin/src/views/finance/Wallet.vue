<template>

  <div>
    <h2 class="page-title">资金财务（提现 / 对账 / 结算解冻）</h2>

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

      <el-tab-pane label="结算解冻">
        <el-card shadow="never">
          <el-alert type="info" :closable="false" show-icon
            title="结算单在 T+1 放款时，若卖家存在未处置的高危风控事件会被冻结（status=frozen）；待风控事件处置完成后，在此输入结算单 ID 手动解冻，使其重新进入放款队列（frozen → pending）。"></el-alert>
          <el-form inline style="margin-top:16px">
            <el-form-item label="结算单 ID">
              <el-input v-model="unfreezeId" placeholder="请输入结算单 ID（数字）" style="width:240px" clearable></el-input>
            </el-form-item>
            <el-form-item>
              <el-button type="warning" :loading="unfreezing" :disabled="!unfreezeId" @click="unfreeze">解冻结算单</el-button>
            </el-form-item>
          </el-form>
          <div class="empty-tip">提示：冻结结算单由结算定时任务（processDue）与风控联动自动产生；当前后端未提供冻结列表端点，需凭结算单 ID 操作。</div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import { walletApi } from '@/api';
import { formatMixin, notifyError } from '@/utils/format';
// pc-admin/src/views/wallet.js —— 钱包 / 提现管理与资金对账（F-PC-02）


export default {
  name: 'Wallet',
  mixins: [formatMixin],
  data() {
    return {
      // 提现
      wdList: [], wdLoading: false, wdStatusFilter: '',
      // 对账
      reconDay: '', reconLoading: false, recon: null,
      // 结算解冻
      unfreezeId: '', unfreezing: false
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
      catch (e) { notifyError(this, e, '加载提现列表失败'); }
      finally { this.wdLoading = false; }
    },
    wdTag(s) {
      return { pending: 'warning', approved: 'success', rejected: 'danger', done: 'info' }[s] || 'info';
    },
    wdText(s) {
      return { pending: '待审核', approved: '已通过', rejected: '已驳回', done: '已打款' }[s] || s;
    },
    canAudit(w) { return w.status === 'pending'; },
    async approve(w) {
      try {
        await this.$confirm(`确认通过该提现申请（${this.yuan(w.amount)}）并实际出账？`, '审批通过', { type: 'warning' });
      } catch { return; }
      try {
        await walletApi.approveWithdrawal(w.id);
        this.$message.success('已通过，启动出账');
        this.loadWithdrawals();
      } catch (e) { notifyError(this, e, '操作失败'); }
    },
    async reject(w) {
      try {
        await this.$confirm(`确认驳回该提现申请（${this.yuan(w.amount)}）？资金将解冻。`, '驳回提现', { type: 'warning' });
      } catch { return; }
      try {
        await walletApi.rejectWithdrawal(w.id);
        this.$message.warning('已驳回，资金解冻');
        this.loadWithdrawals();
      } catch (e) { notifyError(this, e, '操作失败'); }
    },
    async loadRecon() {
      this.reconLoading = true;
      try { this.recon = await walletApi.reconciliation(this.reconDay || ''); }
      catch (e) { notifyError(this, e, '加载对账报表失败'); this.recon = null; }
      finally { this.reconLoading = false; }
    },
    reconTypeTag(t) { return t === 'refund' ? 'danger' : 'success'; },
    reconTypeText(t) { return t === 'refund' ? '退款' : '支付'; },
    async unfreeze() {
      const id = Number(this.unfreezeId);
      if (!id || id <= 0) { this.$message.error('请输入有效的结算单 ID'); return; }
      try {
        await this.$confirm(`确认解冻结算单 #${id}？解冻后该单将重新进入 T+1 放款队列。`, '结算解冻', { type: 'warning' });
      } catch { return; }
      this.unfreezing = true;
      try {
        await walletApi.unfreezeSettlement(id);
        this.$message.success('已提交解冻，结算单将重新进入放款队列');
        this.unfreezeId = '';
      } catch (e) { notifyError(this, e, '解冻失败'); }
      finally { this.unfreezing = false; }
    }
  }
}
</script>
