// pc-admin/src/views/items.js —— 商品管理 / 内容审核
import { adminApi } from '../api.js';

export default {
  name: 'Items',
  data() {
    return {
      list: [], total: 0, loading: false, keyword: '', statusFilter: '',
      rejectVisible: false, auditRow: null, reason: '',
      detailRow: null, detailVisible: false, detailLoading: false
    };
  },
  computed: {
    filtered() { return this.list; }
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try {
        const r = await adminApi.items({ keyword: this.keyword, status: this.statusFilter, page: 1, size: 20 });
        // 真实后端返回 status（pending_review/onsale/rejected），统一规整为审计态供展示
        this.list = (r.list || []).map(it => ({
          ...it,
          auditStatus: it.auditStatus !== undefined ? it.auditStatus
            : (it.status === 'pending_review' ? 'pending' : it.status === 'onsale' ? 'passed' : it.status === 'rejected' ? 'rejected' : it.status)
        }));
        this.total = r.total;
      } finally { this.loading = false; }
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
        this.$message.error('加载商品详情失败');
        this.detailVisible = false;
      } finally { this.detailLoading = false; }
    },
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
            <el-option label="待审核" value="pending_review"></el-option>
            <el-option label="在售" value="onsale"></el-option>
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
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{row}">
            <el-button size="small" @click="openDetail(row)">查看</el-button>
            <el-button v-if="row.auditStatus!=='passed'" size="small" type="success" @click="pass(row)">通过</el-button>
            <el-button v-if="row.auditStatus!=='rejected'" size="small" type="danger" @click="openReject(row)">驳回</el-button>
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

    <el-dialog v-model="detailVisible" title="商品审核详情" width="640px" v-loading="detailLoading">
      <div v-if="detailRow" class="item-detail">
        <div class="id-gallery">
          <img v-for="(u,i) in detailRow.images" :key="i" :src="u" class="id-img"/>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="商品标题">{{ detailRow.title || '-' }}</el-descriptions-item>
          <el-descriptions-item label="价格">¥{{ ((detailRow.price||0)/100).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="类目">{{ detailRow.categoryName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="卖家">{{ detailRow.seller || detailRow.sellerId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审核状态">
            <el-tag :type="auditTag(detailRow.auditStatus)" size="small">{{ auditText(detailRow.auditStatus) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ detailRow.createdAt || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="id-desc">
          <div class="id-desc-title">商品描述</div>
          <div class="id-desc-body">{{ detailRow.description || '暂无描述' }}</div>
        </div>
      </div>
    </el-dialog>
  </div>`
};
