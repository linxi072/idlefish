<template>
  <div>
    <page-header title="商品管理 / 内容审核" />
    <el-card shadow="never">
      <FilterBar :loading="loading" @search="load" @reset="reset">
        <el-form-item label="关键词">
          <el-input v-model="keyword" placeholder="商品标题" clearable style="width:200px" />
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width:140px">
            <el-option label="待审核" value="pending_review"></el-option>
            <el-option label="在售" value="onsale"></el-option>
            <el-option label="已驳回" value="rejected"></el-option>
          </el-select>
        </el-form-item>
        <template #extra>
          <FilterTemplates page-key="items" :get-filters="getFilters" :on-apply="applyFilters" />
        </template>
      </FilterBar>

      <div v-if="drillCategoryId || drillConditionLevel" class="drill-bar">
        <span class="drill-tag">下钻筛选</span>
        <el-tag v-if="drillCategoryId" size="small" type="info">类目 #{{ drillCategoryId }}</el-tag>
        <el-tag v-if="drillConditionLevel" size="small" type="info">成色 Lv.{{ drillConditionLevel }}</el-tag>
        <el-button size="small" text @click="clearDrill">清除下钻</el-button>
      </div>

      <BatchActionBar :count="selection.length" @clear="clearSelection">
        <el-button size="small" :disabled="!selection.length" @click="previewBatch">影响面预览</el-button>
        <el-button size="small" type="success" :disabled="!selection.length" :loading="batchRunning" @click="runBatchAudit(true)">批量通过</el-button>
        <el-button size="small" type="danger" :disabled="!selection.length" :loading="batchRunning" @click="openBatchReject">批量驳回</el-button>
      </BatchActionBar>

      <el-table
        ref="table" :data="list" row-key="id" v-loading="loading" border stripe :max-height="560"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="46" />
        <template #empty><EmptyState text="暂无商品" desc="调整筛选条件后重试" /></template>
        <el-table-column label="商品" min-width="220">
          <template #default="{row}">
            <div class="cell-goods"><img :src="'https://picsum.photos/seed/'+row.id+'/80/80'" class="thumb"/>
              <div><div class="g-title">{{ row.title }}</div><div class="g-sub">ID: {{ row.id }}</div></div></div>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="100"><template #default="{row}">{{ yuan(row.price) }}</template></el-table-column>
        <el-table-column label="类目" prop="categoryName" width="120"></el-table-column>
        <el-table-column label="卖家" width="120"><template #default="{row}">{{ row.sellerId || '-' }}</template></el-table-column>
        <el-table-column label="审核状态" width="120">
          <template #default="{row}"><StatusTag :value="row.auditStatus" :map="AUDIT_STATUS" /></template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{row}">
            <el-button size="small" @click="openDetail(row)">查看</el-button>
            <el-button v-if="row.auditStatus!=='passed'" size="small" type="success" @click="pass(row)">通过</el-button>
            <el-button v-if="row.auditStatus!=='rejected'" size="small" type="danger" @click="openReject(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>

      <PageBar :total="total" :page="page" :size="size" @change="onPageChange" />
    </el-card>

    <el-dialog v-model="rejectVisible" title="驳回商品" width="420px">
      <el-input v-model="reason" type="textarea" :rows="3" placeholder="请填写驳回原因（将通知卖家）"></el-input>
      <template #footer>
        <el-button @click="rejectVisible=false">取消</el-button>
        <el-button type="danger" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- ITER-U03 批量驳回 -->
    <el-dialog v-model="batchRejectVisible" title="批量驳回" width="460px">
      <p class="dialog-tip">共选择 {{ selection.length }} 个商品，驳回原因将通知卖家。</p>
      <el-input v-model="batchReason" type="textarea" :rows="3" placeholder="请填写批量驳回原因"></el-input>
      <template #footer>
        <el-button @click="batchRejectVisible=false">取消</el-button>
        <el-button type="danger" :loading="batchRunning" @click="runBatchAudit(false)">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- ITER-U03 影响面预览 -->
    <el-dialog v-model="previewVisible" title="批量影响面预览" width="460px">
      <el-descriptions v-if="preview" :column="1" border>
        <el-descriptions-item label="勾选条数">{{ preview.selected }}</el-descriptions-item>
        <el-descriptions-item label="实际存在">{{ preview.existing }}</el-descriptions-item>
        <el-descriptions-item label="待处理">{{ preview.actionable }}</el-descriptions-item>
        <el-descriptions-item label="已通过（跳过）">{{ preview.alreadyPass }}</el-descriptions-item>
        <el-descriptions-item label="已驳回（跳过）">{{ preview.alreadyReject }}</el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button type="primary" @click="previewVisible=false">知道了</el-button></template>
    </el-dialog>

    <BatchResultDialog v-model="resultVisible" :result="batchResult" @retry="retryFailed" />

    <el-dialog v-model="detailVisible" title="商品审核详情" width="640px" v-loading="detailLoading">
      <div v-if="detailRow" class="item-detail">
        <div class="id-gallery">
          <img v-for="(u,i) in detailRow.images" :key="i" :src="u" class="id-img"/>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="商品标题">{{ detailRow.title || '-' }}</el-descriptions-item>
          <el-descriptions-item label="价格">{{ yuan(detailRow.price) }}</el-descriptions-item>
          <el-descriptions-item label="类目">{{ detailRow.categoryName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="卖家">{{ detailRow.seller || detailRow.sellerId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审核状态">
            <StatusTag :value="detailRow.auditStatus" :map="AUDIT_STATUS" size="small" />
          </el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ detailRow.createdAt || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="id-desc">
          <div class="id-desc-title">商品描述</div>
          <div class="id-desc-body">{{ detailRow.description || '暂无描述' }}</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { adminApi } from '@/api';
import { formatMixin, notifyError } from '@/utils/format';
import { AUDIT_STATUS } from '@/utils/status';
// pc-admin/src/views/merchandise/Items.vue —— 商品管理 / 内容审核

export default {
  name: 'Items',
  mixins: [formatMixin],
  data() {
    return {
      list: [], total: 0, loading: false, keyword: '', statusFilter: '', page: 1, size: 20,
      // ITER-U03 REQ-07 BI 下钻携带的维度（来自路由 query，非表单输入）
      drillCategoryId: '', drillConditionLevel: '',
      rejectVisible: false, auditRow: null, reason: '',
      detailRow: null, detailVisible: false, detailLoading: false,
      // ITER-U03 批量操作状态
      selection: [], batchRunning: false, batchRejectVisible: false, batchReason: '',
      previewVisible: false, preview: null, resultVisible: false, batchResult: null, lastBatch: null,
      AUDIT_STATUS
    };
  },
  mounted() { this.syncFromQuery(); this.load(); },
  watch: {
    // REQ-07 路由维度变化（如从 BI 不同切片连续下钻）时重新回填并加载
    '$route'() { this.syncFromQuery(); this.load(); }
  },
  methods: {
    reset() { this.keyword = ''; this.statusFilter = ''; this.page = 1; this.load(); },
    // ITER-U03 REQ-07 读取路由 query 回填筛选（status / categoryId / conditionLevel）
    syncFromQuery() {
      const q = (this.$route && this.$route.query) || {};
      this.statusFilter = q.status || '';
      this.drillCategoryId = q.categoryId ? Number(q.categoryId) : '';
      this.drillConditionLevel = q.conditionLevel ? Number(q.conditionLevel) : '';
      this.page = 1;
    },
    clearDrill() {
      this.drillCategoryId = '';
      this.drillConditionLevel = '';
      this.load();
    },
    // ITER-U03 REQ-06 筛选模板：取当前筛选快照 / 回灌模板条件
    getFilters() { return { keyword: this.keyword, statusFilter: this.statusFilter }; },
    applyFilters(f) {
      f = f || {};
      this.keyword = f.keyword || '';
      this.statusFilter = f.statusFilter || '';
      this.page = 1;
      this.load();
    },
    // PageBar 统一事件：切页/改每页条数后重新拉取
    onPageChange({ page, size }) { this.page = page; this.size = size; this.load(); },

    // ===== ITER-U03 批量操作 =====
    onSelectionChange(rows) { this.selection = rows || []; },
    clearSelection() {
      this.selection = [];
      const t = this.$refs.table;
      if (t && t.clearSelection) t.clearSelection();
    },
    async previewBatch() {
      if (!this.selection.length) return;
      try {
        this.preview = await adminApi.batchPreviewItems(this.selection.map(r => r.id), true);
        this.previewVisible = true;
      } catch (e) { notifyError(this, e, '预览失败'); }
    },
    openBatchReject() { this.batchReason = ''; this.batchRejectVisible = true; },
    async runBatchAudit(pass) {
      if (!this.selection.length) return;
      if (!pass && !this.batchReason.trim()) { this.$message.warning('请填写驳回原因'); return; }
      this.batchRunning = true;
      try {
        const ids = this.selection.map(r => r.id);
        this.lastBatch = { pass, reason: pass ? null : this.batchReason };
        this.batchResult = await adminApi.batchAudit(ids, pass, this.lastBatch.reason);
        this.resultVisible = true;
        this.batchRejectVisible = false;
        this.clearSelection();
        this.load();
      } catch (e) { notifyError(this, e, '批量审核失败'); }
      finally { this.batchRunning = false; }
    },
    // 部分失败不回滚：按上次参数仅重试失败项（REQ-05）
    async retryFailed(failedIds) {
      if (!this.lastBatch || !failedIds || !failedIds.length) return;
      this.batchRunning = true;
      try {
        this.batchResult = await adminApi.batchAudit(failedIds.map(Number), this.lastBatch.pass, this.lastBatch.reason);
        this.load();
        this.$message.success('已重试失败项');
      } catch (e) { notifyError(this, e, '重试失败'); }
      finally { this.batchRunning = false; }
    },
    async load() {
      this.loading = true;
      try {
        const opts = { keyword: this.keyword, status: this.statusFilter, page: this.page, size: this.size };
        // REQ-07 下钻维度：携带 categoryId / conditionLevel 至后端维度过滤
        if (this.drillCategoryId) opts.categoryId = this.drillCategoryId;
        if (this.drillConditionLevel) opts.conditionLevel = this.drillConditionLevel;
        const r = await adminApi.items(opts);
        // 真实后端返回 status（pending_review/onsale/rejected），统一规整为审计态供展示
        this.list = (r.list || []).map(it => ({
          ...it,
          auditStatus: it.auditStatus !== undefined ? it.auditStatus
            : (it.status === 'pending_review' ? 'pending' : it.status === 'onsale' ? 'passed' : it.status === 'rejected' ? 'rejected' : it.status)
        }));
        this.total = r.total;
      } catch (e) { notifyError(this, e, '加载商品失败'); }
      finally { this.loading = false; }
    },
    async pass(row) {
      await adminApi.approve(row.id);
      this.$message.success('已通过审核');
      this.load();
    },
    openReject(row) { this.auditRow = row; this.reason = ''; this.rejectVisible = true; },
    // 打开商品审核详情弹窗（查看大图 / 描述 / 卖家资质）
    async openDetail(row) {
      this.detailLoading = true; this.detailVisible = true; this.detailRow = null;
      try {
        const d = await adminApi.itemDetail(row.id);
        // 统一规整：图片优先用后端 images，否则按 id 生成占位图；状态映射复用列表规则
        const images = (d.images && d.images.length)
          ? d.images.map(s => s.startsWith('http') ? s : `https://picsum.photos/${s}/400/400`)
          : [`https://picsum.photos/seed/${row.id}/400/400`];
        this.detailRow = {
          ...d,
          images,
          auditStatus: d.auditStatus !== undefined ? d.auditStatus
            : (d.status === 'pending_review' ? 'pending' : d.status === 'onsale' ? 'passed' : d.status === 'rejected' ? 'rejected' : d.status)
        };
      } catch (e) {
        notifyError(this, e, '加载商品详情失败');
        this.detailVisible = false;
      } finally { this.detailLoading = false; }
    },
    async doReject() {
      await adminApi.reject(this.auditRow.id, this.reason);
      this.rejectVisible = false;
      this.$message.warning('已驳回');
      this.load();
    }
  }
}
</script>
