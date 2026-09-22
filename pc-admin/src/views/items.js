// pc-admin/src/views/items.js —— 商品管理 / 内容审核
import { adminApi } from '../api.js';

export default {
  name: 'Items',
  data() {
    return {
      list: [], total: 0, loading: false, keyword: '', statusFilter: '',
      rejectVisible: false, auditRow: null, reason: ''
    };
  },
  computed: {
    filtered() {
      return this.list.filter(i => {
        if (this.statusFilter && i.auditStatus !== this.statusFilter) return false;
        if (this.keyword && i.title.indexOf(this.keyword) < 0) return false;
        return true;
      });
    }
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try { const r = await adminApi.items(); this.list = r.list; this.total = r.total; }
      finally { this.loading = false; }
    },
    auditTag(s) {
      return { passed: 'success', pending: 'warning', rejected: 'danger' }[s] || 'info';
    },
    auditText(s) { return { passed: '已通过', pending: '待审核', rejected: '已驳回' }[s] || s; },
    async pass(row) {
      await adminApi.approve(row.id);
      this.$message.success('已通过审核');
      this.load();
    },
    openReject(row) { this.auditRow = row; this.reason = ''; this.rejectVisible = true; },
    async doReject() {
      await adminApi.reject(this.auditRow.id, this.reason);
      this.rejectVisible = false;
      this.$message.warning('已驳回');
      this.load();
    }
  },
  template: `
  <div>
    <h2 class="page-title">商品管理 / 内容审核</h2>
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="关键词"><el-input v-model="keyword" placeholder="商品标题" clearable></el-input></el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width:140px">
            <el-option label="待审核" value="pending"></el-option>
            <el-option label="已通过" value="passed"></el-option>
            <el-option label="已驳回" value="rejected"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
      </el-form>

      <el-table :data="filtered" v-loading="loading" border stripe>
        <el-table-column label="商品" min-width="220">
          <template #default="{row}">
            <div class="cell-goods"><img :src="'https://picsum.photos/seed/'+row.id+'/80/80'" class="thumb"/>
              <div><div class="g-title">{{ row.title }}</div><div class="g-sub">ID: {{ row.id }}</div></div></div>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="100"><template #default="{row}">¥{{ (row.price/100).toFixed(2) }}</template></el-table-column>
        <el-table-column label="类目" prop="categoryName" width="120"></el-table-column>
        <el-table-column label="卖家" width="120"><template #default="{row}">{{ row.sellerId || '-' }}</template></el-table-column>
        <el-table-column label="审核状态" width="120">
          <template #default="{row}"><el-tag :type="auditTag(row.auditStatus)">{{ auditText(row.auditStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{row}">
            <el-button v-if="row.auditStatus!=='passed'" size="small" type="success" @click="pass(row)">通过</el-button>
            <el-button v-if="row.auditStatus!=='rejected'" size="small" type="danger" @click="openReject(row)">驳回</el-button>
            <span v-if="row.auditStatus==='passed'">—</span>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px">共 {{ filtered.length }} 条</div>
    </el-card>

    <el-dialog v-model="rejectVisible" title="驳回商品" width="420px">
      <el-input v-model="reason" type="textarea" :rows="3" placeholder="请填写驳回原因（将通知卖家）"></el-input>
      <template #footer>
        <el-button @click="rejectVisible=false">取消</el-button>
        <el-button type="danger" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>`
};
