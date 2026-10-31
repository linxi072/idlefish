<template>
  <div>
    <page-header title="同城回收管理" desc="上门回收单的揽收 / 质检调价 / 放款闭环，金额单位「分」由前端折算展示" />
    <el-card shadow="never">
      <FilterBar :loading="loading" @search="load" @reset="reset">
        <el-form-item label="回收状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width:160px">
            <el-option v-for="(v,k) in RECYCLE_STATUS" :key="k" :label="v.text" :value="k"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="keyword" placeholder="回收单号/标题/联系人" clearable style="width:220px" @keyup.enter="load" />
        </el-form-item>
        <template #extra>
          <FilterTemplates page-key="recycle" :get-filters="getFilters" :on-apply="applyFilters" />
        </template>
      </FilterBar>

      <el-table :data="list" v-loading="loading" border stripe :max-height="560">
        <template #empty><EmptyState text="暂无回收单" desc="调整筛选条件后重试" /></template>
        <el-table-column label="回收单号" prop="recycleNo" width="170"></el-table-column>
        <el-table-column label="商品标题" prop="title" min-width="160" show-overflow-tooltip></el-table-column>
        <el-table-column label="成色" width="90">
          <template #default="{row}">{{ condText(row.conditionLevel) }}</template>
        </el-table-column>
        <el-table-column label="取件方式" width="110">
          <template #default="{row}"><StatusTag :value="row.pickupType" :map="RECYCLE_PICKUP_TYPE" /></template>
        </el-table-column>
        <el-table-column label="城市" prop="city" width="100"></el-table-column>
        <el-table-column label="预估价" width="110" align="right">
          <template #default="{row}">{{ yuan(row.estimateAmount) }}</template>
        </el-table-column>
        <el-table-column label="报价" width="110" align="right">
          <template #default="{row}">
            <span v-if="row.finalAmount != null">{{ yuan(row.finalAmount) }}</span>
            <span v-else class="g-sub">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{row}"><StatusTag :value="row.status" :map="RECYCLE_STATUS" /></template>
        </el-table-column>
        <el-table-column label="处理人" prop="operator" width="100">
          <template #default="{row}">{{ row.operator || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createdAt" width="155"></el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{row}">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button size="small" type="primary" :disabled="!canPickup(row)" @click="pickup(row)">揽收</el-button>
            <el-button size="small" :disabled="!canInspecting(row)" @click="inspecting(row)">质检</el-button>
            <el-button size="small" type="warning" :disabled="!canQuote(row)" @click="openQuote(row)">调价</el-button>
            <el-button size="small" type="success" :disabled="!canPay(row)" @click="pay(row)">放款</el-button>
            <el-button size="small" type="danger" :disabled="!canUnavailable(row)" @click="unavailable(row)">不可用</el-button>
          </template>
        </el-table-column>
      </el-table>

      <PageBar :total="total" :page="page" :size="size" @change="onPageChange" />
    </el-card>

    <el-dialog v-model="detailVisible" title="回收单详情" width="560px">
      <el-descriptions v-if="detailRow" :column="1" border>
        <el-descriptions-item label="回收单号">{{ detailRow.recycleNo }}</el-descriptions-item>
        <el-descriptions-item label="商品标题">{{ detailRow.title }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ detailRow.userId }}</el-descriptions-item>
        <el-descriptions-item label="成色">{{ condText(detailRow.conditionLevel) }}</el-descriptions-item>
        <el-descriptions-item label="取件方式"><StatusTag :value="detailRow.pickupType" :map="RECYCLE_PICKUP_TYPE" /></el-descriptions-item>
        <el-descriptions-item label="城市">{{ detailRow.province }} / {{ detailRow.city }} / {{ detailRow.district }}</el-descriptions-item>
        <el-descriptions-item label="取件地址">{{ detailRow.address }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detailRow.contactName }}（{{ detailRow.contactPhone }}）</el-descriptions-item>
        <el-descriptions-item label="预约时间">{{ detailRow.appointmentAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="预估价">{{ yuan(detailRow.estimateAmount) }}</el-descriptions-item>
        <el-descriptions-item label="质检报价">
          <span v-if="detailRow.finalAmount != null">{{ yuan(detailRow.finalAmount) }}</span>
          <span v-else class="g-sub">待质检</span>
        </el-descriptions-item>
        <el-descriptions-item label="调价说明">{{ detailRow.quoteRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :value="detailRow.status" :map="RECYCLE_STATUS" /></el-descriptions-item>
        <el-descriptions-item label="处理人">{{ detailRow.operator || '-' }}</el-descriptions-item>
        <el-descriptions-item label="外部单号">{{ detailRow.serialNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="取消/不可用原因">{{ detailRow.cancelReason || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="quoteVisible" title="录入质检报价" width="460px">
      <el-form label-width="96px">
        <el-form-item label="回收单号">
          <el-input :value="quoteForm.recycleNo" disabled></el-input>
        </el-form-item>
        <el-form-item label="系统预估价">
          <el-input :value="yuan(quoteForm.estimateAmount)" disabled></el-input>
        </el-form-item>
        <el-form-item label="最终报价(元)" required>
          <el-input-number v-model="quoteForm.finalYuan" :min="0" :precision="2" :step="10" controls-position="right" style="width:100%" />
          <span class="hint">服务端将按成色系数二次封顶，不得高于预估价上限</span>
        </el-form-item>
        <el-form-item label="调价说明" required>
          <el-input v-model="quoteForm.quoteRemark" type="textarea" :rows="2" placeholder="向用户明示调整依据"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quoteVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitQuote">提交报价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { localApi } from '@/api';
import { formatMixin, notifyError } from '@/utils/format';
import { RECYCLE_STATUS, RECYCLE_PICKUP_TYPE, CONDITION_LEVEL } from '@/utils/status';
// pc-admin/src/views/local/RecycleManage.vue —— 同城回收运营后台（F-05）
// 揽收 → 质检 → 调价（待用户确认）→ 放款；仅已确认报价可放款，禁止强制成交。

export default {
  name: 'RecycleManage',
  mixins: [formatMixin],
  data() {
    return {
      list: [], total: 0, loading: false, keyword: '', statusFilter: '', page: 1, size: 20,
      detailRow: null, detailVisible: false,
      quoteVisible: false, submitting: false,
      quoteForm: { id: null, recycleNo: '', estimateAmount: 0, finalYuan: 0, quoteRemark: '' },
      RECYCLE_STATUS, RECYCLE_PICKUP_TYPE
    };
  },
  mounted() { this.syncFromQuery(); this.load(); },
  watch: {
    '$route'() { this.syncFromQuery(); this.load(); }
  },
  methods: {
    condText(level) {
      const e = CONDITION_LEVEL[level];
      return e ? e.text : (level != null ? '档位' + level : '-');
    },
    reset() { this.keyword = ''; this.statusFilter = ''; this.page = 1; this.load(); },
    syncFromQuery() {
      const q = (this.$route && this.$route.query) || {};
      this.statusFilter = q.status || '';
      this.keyword = q.keyword || '';
      this.page = 1;
    },
    getFilters() { return { keyword: this.keyword, statusFilter: this.statusFilter }; },
    applyFilters(f) {
      f = f || {};
      this.keyword = f.keyword || '';
      this.statusFilter = f.statusFilter || '';
      this.page = 1;
      this.load();
    },
    onPageChange({ page, size }) { this.page = page; this.size = size; this.load(); },
    async load() {
      this.loading = true;
      try {
        const r = await localApi.recycleList(this.statusFilter, this.keyword, this.page, this.size);
        this.list = r.list || [];
        this.total = r.total || this.list.length;
      } catch (e) { notifyError(this, e, '加载回收单失败'); }
      finally { this.loading = false; }
    },
    openDetail(row) { this.detailRow = row; this.detailVisible = true; },
    canPickup(row) { return row.status === 'PENDING_PICKUP'; },
    canInspecting(row) { return row.status === 'PICKED_UP' || row.status === 'INSPECTING'; },
    canQuote(row) { return row.status === 'PICKED_UP' || row.status === 'INSPECTING'; },
    canPay(row) { return row.status === 'CONFIRMED'; },
    canUnavailable(row) {
      return ['PENDING_PICKUP', 'PICKED_UP', 'INSPECTING', 'QUOTED', 'CONFIRMED'].includes(row.status);
    },
    async pickup(row) {
      try {
        await localApi.pickup(row.id);
        this.$message.success('已揽收');
      } catch (e) { notifyError(this, e, '揽收失败'); }
      this.load();
    },
    async inspecting(row) {
      try {
        await localApi.inspecting(row.id);
        this.$message.success('已进入质检');
      } catch (e) { notifyError(this, e, '操作失败'); }
      this.load();
    },
    async pay(row) {
      try {
        await this.$confirm('确认向用户放款 ' + this.yuan(row.finalAmount) + '？仅已确认报价的单可放款', '放款确认', { type: 'warning' });
      } catch (e) { return; }
      try {
        await localApi.pay(row.id);
        this.$message.success('已放款');
      } catch (e) { notifyError(this, e, '放款失败'); }
      this.load();
    },
    async unavailable(row) {
      let reason = '';
      try {
        reason = await this.$prompt('请填写不可用原因（城市未开通/服务商异常）', '标记为不可用', {
          inputType: 'textarea', inputPlaceholder: '如：所在城区暂未开通回收服务商'
        }).then(r => r.value || '');
      } catch (e) { return; }
      try {
        await localApi.unavailable(row.id, reason);
        this.$message.success('已标记为不可用');
      } catch (e) { notifyError(this, e, '操作失败'); }
      this.load();
    },
    openQuote(row) {
      this.quoteForm = {
        id: row.id, recycleNo: row.recycleNo, estimateAmount: row.estimateAmount || 0,
        finalYuan: ((row.estimateAmount || 0) / 100), quoteRemark: row.quoteRemark || ''
      };
      this.quoteVisible = true;
    },
    async submitQuote() {
      if (!(this.quoteForm.finalYuan > 0)) return this.$message.error('请填写最终报价');
      if (!this.quoteForm.quoteRemark) return this.$message.error('请填写调价说明');
      this.submitting = true;
      try {
        const finalAmount = Math.round(this.quoteForm.finalYuan * 100);
        await localApi.quote(this.quoteForm.id, finalAmount, this.quoteForm.quoteRemark);
        this.$message.success('已提交报价，等待用户确认');
        this.quoteVisible = false;
        this.load();
      } catch (e) { notifyError(this, e, '提交报价失败'); }
      finally { this.submitting = false; }
    }
  }
};
</script>
