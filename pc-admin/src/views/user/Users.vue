<template>
  <div>
    <page-header title="用户管理" />
    <el-card shadow="never">
      <FilterBar :loading="loading" @search="load" @reset="reset">
        <el-form-item label="关键词">
          <el-input v-model="keyword" placeholder="昵称/手机号" clearable style="width:200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width:140px">
            <el-option label="正常" value="0"></el-option>
            <el-option label="已封禁" value="1"></el-option>
          </el-select>
        </el-form-item>
        <template #extra>
          <FilterTemplates page-key="users" :get-filters="getFilters" :on-apply="applyFilters" />
        </template>
      </FilterBar>

      <BatchActionBar :count="selection.length" @clear="clearSelection">
        <el-button size="small" type="danger" :disabled="!selection.length" :loading="batchRunning" @click="runBatchBan(1)">批量封禁</el-button>
        <el-button size="small" type="success" :disabled="!selection.length" :loading="batchRunning" @click="runBatchBan(0)">批量解封</el-button>
      </BatchActionBar>

      <el-table
        ref="table" :data="filtered" row-key="id" v-loading="loading" border stripe :max-height="560"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="46" />
        <template #empty><EmptyState text="暂无用户" desc="调整筛选条件后重试" /></template>
        <el-table-column label="ID" prop="id" width="90"></el-table-column>
        <el-table-column label="昵称" prop="nickname" width="140"></el-table-column>
        <el-table-column label="手机号" width="150"><template #default="{row}">{{ masked(row.phone) }}</template></el-table-column>
        <el-table-column label="信用分" prop="creditScore" width="100" align="right">
          <template #default="{row}">
            <StatusTag
              :text="row.creditScore == null ? '-' : String(row.creditScore)"
              :type="row.creditScore>=80?'success':row.creditScore>=60?'warning':'danger'"
            />
          </template>
        </el-table-column>
        <el-table-column label="注册时间" prop="createdAt" width="140"></el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}"><StatusTag :value="row.status" :map="ENABLE_STATUS" /></template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{row}">
            <el-button v-if="row.status===0" size="small" type="danger" plain @click="toggleBan(row)">封禁</el-button>
            <el-button v-else size="small" type="success" plain @click="toggleBan(row)">解封</el-button>
          </template>
        </el-table-column>
      </el-table>

      <PageBar :total="total" :page="page" :size="size" @change="onPageChange" />
    </el-card>

    <BatchResultDialog v-model="resultVisible" :result="batchResult" @retry="retryFailed" />
  </div>
</template>

<script>
import { adminApi } from '@/api';
import { formatMixin, notifyError } from '@/utils/format';
import { ENABLE_STATUS } from '@/utils/status';
// pc-admin/src/views/user/Users.vue —— 用户管理（封禁 / 解封）

export default {
  name: 'Users',
  mixins: [formatMixin],
  data() {
    return {
      list: [], total: 0, loading: false, keyword: '', statusFilter: '', page: 1, size: 20,
      // ITER-U03 批量操作状态
      selection: [], batchRunning: false, resultVisible: false, batchResult: null, lastBatch: null,
      ENABLE_STATUS
    };
  },
  computed: {
    filtered() { return this.list; }
  },
  mounted() { this.syncFromQuery(); this.load(); },
  watch: {
    // ITER-U03 REQ-07 路由维度（status/keyword）变化重新回填并加载
    '$route'() { this.syncFromQuery(); this.load(); }
  },
  methods: {
    reset() { this.keyword = ''; this.statusFilter = ''; this.page = 1; this.load(); },
    // 读取路由 query 回填筛选（status / keyword）
    syncFromQuery() {
      const q = (this.$route && this.$route.query) || {};
      this.statusFilter = q.status || '';
      this.keyword = q.keyword || '';
      this.page = 1;
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

    // ===== ITER-U03 批量操作（高风险：二次确认 + confirmed=true） =====
    onSelectionChange(rows) { this.selection = rows || []; },
    clearSelection() {
      this.selection = [];
      const t = this.$refs.table;
      if (t && t.clearSelection) t.clearSelection();
    },
    async runBatchBan(status) {
      if (!this.selection.length) return;
      const action = status === 1 ? '封禁' : '解封';
      try {
        await this.$confirm(
          `确认${action}选中的 ${this.selection.length} 个用户？逐条执行，部分失败不回滚。`,
          '批量操作确认',
          { type: status === 1 ? 'warning' : 'info' }
        );
      } catch (e) { return; }
      this.batchRunning = true;
      try {
        const ids = this.selection.map(r => r.id);
        this.lastBatch = { status };
        this.batchResult = await adminApi.batchBan(ids, status, true);
        this.resultVisible = true;
        this.clearSelection();
        this.load();
      } catch (e) { notifyError(this, e, '批量操作失败'); }
      finally { this.batchRunning = false; }
    },
    // 部分失败不回滚：仅重试失败项
    async retryFailed(failedIds) {
      if (!this.lastBatch || !failedIds || !failedIds.length) return;
      this.batchRunning = true;
      try {
        this.batchResult = await adminApi.batchBan(failedIds.map(Number), this.lastBatch.status, true);
        this.load();
        this.$message.success('已重试失败项');
      } catch (e) { notifyError(this, e, '重试失败'); }
      finally { this.batchRunning = false; }
    },
    async load() {
      this.loading = true;
      try {
        const r = await adminApi.users({ keyword: this.keyword, status: this.statusFilter, page: this.page, size: this.size });
        this.list = r.list || [];
        this.total = r.total;
      } catch (e) { notifyError(this, e, '加载失败'); }
      finally { this.loading = false; }
    },
    masked(phone) { return phone || '-'; },
    async toggleBan(row) {
      const ban = row.status === 0;
      try {
        await this.$confirm(`确认${ban ? '封禁' : '解封'}用户「${row.nickname || row.id}」？`, '操作确认', { type: ban ? 'warning' : 'info' });
      } catch (e) { return; }
      try {
        await adminApi.banUser(row.id, ban);
        row.status = ban ? 1 : 0;
        this.$message.success(ban ? '已封禁该用户' : '已解封');
      } catch (e) { notifyError(this, e, '操作失败'); }
    }
  }
}
</script>
