<template>

  <div>
    <h2 class="page-title">售后维权工单</h2>
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="工单状态">
          <el-select v-model="statusFilter" placeholder="全部" style="width:150px" @change="search">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="工单号/订单号">
          <el-input v-model="keyword" placeholder="支持模糊匹配" clearable style="width:220px" @keyup.enter="search"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="工单号" prop="disputeNo" width="170"></el-table-column>
        <el-table-column label="订单号" prop="orderNo" width="170"></el-table-column>
        <el-table-column label="争议类型" width="120">
          <template #default="{row}">{{ typeText(row.type) }}</template>
        </el-table-column>
        <el-table-column label="诉求" width="110">
          <template #default="{row}">{{ expectText(row.expectation) }}</template>
        </el-table-column>
        <el-table-column label="争议金额" width="110" align="right">
          <template #default="{row}">{{ yuan(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{row}"><el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="裁决结果" width="110">
          <template #default="{row}">
            <span v-if="row.result">{{ resultText(row.result) }}</span>
            <span v-else class="g-sub">未裁决</span>
          </template>
        </el-table-column>
        <el-table-column label="裁决退款" width="110" align="right">
          <template #default="{row}">
            <span v-if="row.refundAmount">{{ yuan(row.refundAmount) }}</span>
            <span v-else class="g-sub">-</span>
          </template>
        </el-table-column>
        <el-table-column label="原因" min-width="180" show-overflow-tooltip>
          <template #default="{row}">{{ row.reason }}</template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createdAt" width="150"></el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{row}">
            <el-button size="small" type="primary" :disabled="row.status === 'CLOSED' || row.status === 'RESOLVED'" @click="openResolve(row)">裁决</el-button>
            <el-button size="small" :disabled="row.status === 'CLOSED'" @click="closeRow(row)">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-if="total" background layout="total, sizes, prev, pager, next"
        :total="total" :current-page="page" :page-size="size"
        style="margin-top:12px" @current-change="handleCurrentChange" @size-change="handleSizeChange"></el-pagination>
    </el-card>

    <el-dialog v-model="dialogVisible" title="平台裁决" width="480px">
      <el-form label-width="110px">
        <el-form-item label="工单号">
          <el-input :value="form.disputeNo" disabled></el-input>
        </el-form-item>
        <el-form-item label="争议金额">
          <el-input :value="yuan(form.amount)" disabled></el-input>
        </el-form-item>
        <el-form-item label="裁决结果" required>
          <el-select v-model="form.result" style="width:100%">
            <el-option v-for="o in resultOptions" :key="o.value" :label="o.label" :value="o.value"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.result !== 'SELLER_WIN'" label="退款金额(元)">
          <el-input-number v-model="form.refundYuan" :min="0" :precision="2" :step="10" controls-position="right" style="width:100%"></el-input-number>
          <span class="hint">不得超过争议金额 {{ yuan(form.amount) }}</span>
        </el-form-item>
        <el-form-item label="裁决说明">
          <el-input v-model="form.platformRemark" type="textarea" :rows="2" placeholder="如：支持买家，全额退款"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交裁决</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { disputeApi } from '@/api';
import { formatMixin } from '@/utils/format';
import { listPageMixin, crudDialogMixin } from '@/mixins';
// pc-admin/src/views/dispute.js —— 售后维权工单（F-17）
// 运营后台：工单列表（状态/关键字筛选）+ 平台裁决（落库裁决结果与退款金额）+ 归档关闭
// 真实接口见 AdminDisputeController /api/admin/dispute/*

const STATUS_OPTS = [
  { label: '全部', value: '' },
  { label: '待卖家处理', value: 'PENDING' },
  { label: '卖家已举证', value: 'SELLER_REPLIED' },
  { label: '平台介入中', value: 'PLATFORM' },
  { label: '已裁决', value: 'RESOLVED' },
  { label: '已撤销', value: 'CANCELED' },
  { label: '已关闭', value: 'CLOSED' }
];
const RESULT_OPTS = [
  { label: '买家胜（支持退款）', value: 'BUYER_WIN' },
  { label: '卖家胜（不支持退款）', value: 'SELLER_WIN' },
  { label: '部分支持', value: 'PARTIAL' }
];


export default {
  name: 'Dispute',
  mixins: [formatMixin, listPageMixin, crudDialogMixin],
  data() {
    // list/total/loading/keyword/page/size 由 listPageMixin 提供；dialogVisible/submitting/form 由 crudDialogMixin 提供
    return {
      statusFilter: '',
      statusOptions: STATUS_OPTS,
      resultOptions: RESULT_OPTS
    };
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try {
        const r = await disputeApi.list(this.statusFilter, this.keyword, this.page, this.size);
        this.list = r.list || [];
        this.total = r.total || this.list.length;
      } catch (e) {
        this.$message.error('加载维权工单失败');
      } finally {
        this.loading = false;
      }
    },
    statusText(s) {
      return { PENDING: '待卖家处理', SELLER_REPLIED: '卖家已举证', PLATFORM: '平台介入中',
        RESOLVED: '已裁决', CLOSED: '已关闭', CANCELED: '已撤销' }[s] || s;
    },
    statusTag(s) {
      return { PENDING: 'warning', SELLER_REPLIED: 'primary', PLATFORM: 'danger',
        RESOLVED: 'success', CLOSED: 'info', CANCELED: 'info' }[s] || 'info';
    },
    typeText(t) {
      return { REFUND_REJECTED: '退款被拒', NOT_RECEIVED: '未收到货', DAMAGED: '商品损坏',
        NOT_AS_DESC: '描述不符' }[t] || t;
    },
    expectText(e) {
      return { REFUND: '仅退款', RETURN_REFUND: '退货退款' }[e] || e;
    },
    resultText(r) {
      return { BUYER_WIN: '买家胜', SELLER_WIN: '卖家胜', PARTIAL: '部分支持' }[r] || '-';
    },
    // 裁决弹窗：以工单为表单基线（refundYuan 便于运营按元输入，提交时转分）
    openResolve(row) {
      this.openEdit(row, { result: row.result || 'BUYER_WIN', refundYuan: (row.amount || 0) / 100, platformRemark: row.platformRemark || '' });
    },
    async submit() {
      const f = this.form;
      if (!f.id) return this.$message.error('工单不存在');
      if (!f.result) return this.$message.error('请选择裁决结果');
      if (f.result !== 'SELLER_WIN' && !(f.refundYuan > 0)) {
        return this.$message.error('支持退款的裁决需填写退款金额');
      }
      this.submitting = true;
      try {
        const refund = f.result === 'SELLER_WIN' ? 0 : Math.round((f.refundYuan || 0) * 100);
        await disputeApi.resolve(f.id, f.result, refund, f.platformRemark);
        this.$message.success('裁决已提交');
        this.dialogVisible = false;
        this.load();
      } catch (e) {
        this.$message.error('裁决失败');
      } finally {
        this.submitting = false;
      }
    },
    async closeRow(row) {
      try {
        await this.$confirm('确认归档关闭工单「' + row.disputeNo + '」？', '提示', { type: 'warning' });
      } catch (e) { return; }
      try {
        await disputeApi.close(row.id);
        this.$message.success('已关闭');
        this.load();
      } catch (e) {
        this.$message.error('关闭失败');
      }
    }
  }
}
</script>
