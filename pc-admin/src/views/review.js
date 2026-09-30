// pc-admin/src/views/review.js —— 评价审核管理（F-06）
// 运营后台：待审评价列表（关键字筛选 + 分页）+ 通过（触发被评价方信用重算）/ 驳回（填原因）
// 真实接口见 AdminReviewController /api/admin/reviews、/reviews/{id}/approve、/reviews/{id}/reject
// 注：后端 pendingList() 仅返回 status=0（待审核）评价；审核后该行即从待审队列移除。
import { reviewApi, adminApi } from '../api.js';
import { formatMixin } from '../utils/format.js';
import { listPageMixin, crudDialogMixin } from '../mixins/index.js';

const ROLE_TEXT = {
  BUYER_SELLER: '买家评价卖家',
  SELLER_BUYER: '卖家评价买家'
};
const STATUS_TEXT = {
  0: '待审核', 1: '已通过', 2: '已驳回'
};
const STATUS_TAG = {
  0: 'warning', 1: 'success', 2: 'danger'
};

export default {
  name: 'Review',
  mixins: [formatMixin, listPageMixin, crudDialogMixin],
  data() {
    // list/total/loading/keyword/page/size 由 listPageMixin 提供；dialogVisible/submitting/form 由 crudDialogMixin 提供
    return {
      itemMap: {}
    };
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try {
        // 后端 pendingList 仅返 status=0；reviewApi.list 已收敛为待审队列。
        // 关键字与分页在前端完成（待审量小，避免后端为单一审核队列再开分页端点）。
        const [rev, itemsResp] = await Promise.all([
          reviewApi.list(),
          adminApi.items({ page: 1, size: 200 })
        ]);
        const map = {};
        (itemsResp.list || []).forEach(i => { map[i.id] = i.title; });
        this.itemMap = map;

        const kw = (this.keyword || '').trim();
        const all = (rev.list || []).filter(r =>
          !kw ||
          (r.content || '').includes(kw) ||
          String(r.itemId).includes(kw) ||
          String(r.id).includes(kw)
        ).map(r => Object.assign({}, r, { itemTitle: map[r.itemId] || ('商品#' + r.itemId) }));

        const start = (this.page - 1) * this.size;
        this.list = all.slice(start, start + this.size);
        this.total = all.length;
      } catch (e) {
        this.$message.error('加载待审评价失败');
      } finally {
        this.loading = false;
      }
    },
    roleText(role) { return ROLE_TEXT[role] || role; },
    statusText(s) { return STATUS_TEXT[s == null ? 0 : s] || '待审核'; },
    statusTag(s) { return STATUS_TAG[s == null ? 0 : s] || 'warning'; },
    anonymousText(a) { return a === 1 ? '是' : '否'; },
    openReject(row) {
      // 复用 crudDialogMixin.openEdit：以行数据为基线并补 reason 字段
      this.openEdit(row, { reason: '' });
    },
    async submit() {
      const f = this.form;
      if (!f.id) return this.$message.error('评价不存在');
      if (!f.reason || !f.reason.trim()) return this.$message.error('请填写驳回原因');
      this.submitting = true;
      try {
        await reviewApi.reject(f.id, f.reason.trim());
        this.$message.success('已驳回');
        this.dialogVisible = false;
        this.load();
      } catch (e) {
        this.$message.error('驳回失败');
      } finally {
        this.submitting = false;
      }
    },
    async approveRow(row) {
      try {
        await this.$confirm('确认通过该评价？通过后将对被评价方进行信用重算。', '提示', { type: 'warning' });
      } catch (e) { return; }
      try {
        await reviewApi.approve(row.id);
        this.$message.success('已通过');
        this.load();
      } catch (e) {
        this.$message.error('操作失败');
      }
    }
  },
  template: `
  <div>
    <h2 class="page-title">评价审核</h2>
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="关键字">
          <el-input v-model="keyword" placeholder="评价内容 / 商品ID / 评价ID" clearable style="width:260px" @keyup.enter="search"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
        <el-form-item>
          <span class="g-sub">仅展示待审核评价，通过后即移出队列</span>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="评价ID" prop="id" width="90"></el-table-column>
        <el-table-column label="商品" min-width="200" show-overflow-tooltip>
          <template #default="{row}">
            <div>{{ row.itemTitle }}</div>
            <div class="g-sub">商品#{{ row.itemId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="130">
          <template #default="{row}">{{ roleText(row.role) }}</template>
        </el-table-column>
        <el-table-column label="评价人ID" prop="reviewerId" width="110"></el-table-column>
        <el-table-column label="被评人ID" prop="targetId" width="110"></el-table-column>
        <el-table-column label="评分" width="170">
          <template #default="{row}"><el-rate :model-value="row.rating" disabled></el-rate></template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="240" show-overflow-tooltip>
          <template #default="{row}">{{ row.content }}</template>
        </el-table-column>
        <el-table-column label="匿名" width="80" align="center">
          <template #default="{row}">
            <el-tag :type="row.anonymous === 1 ? 'info' : 'success'" size="small">{{ anonymousText(row.anonymous) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}"><el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createdAt" width="160"></el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{row}">
            <el-button size="small" type="success" @click="approveRow(row)">通过</el-button>
            <el-button size="small" type="danger" @click="openReject(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-if="total" background layout="total, sizes, prev, pager, next"
        :total="total" :current-page="page" :page-size="size"
        style="margin-top:12px" @current-change="handleCurrentChange" @size-change="handleSizeChange"></el-pagination>
    </el-card>

    <el-dialog v-model="dialogVisible" title="驳回评价" width="460px">
      <el-form label-width="90px">
        <el-form-item label="评价ID">
          <el-input :value="form.id" disabled></el-input>
        </el-form-item>
        <el-form-item label="商品">
          <el-input :value="form.itemTitle || ('商品#' + form.itemId)" disabled></el-input>
        </el-form-item>
        <el-form-item label="驳回原因" required>
          <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="请填写驳回原因（将记录至审核日志）"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submit">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>`
};
