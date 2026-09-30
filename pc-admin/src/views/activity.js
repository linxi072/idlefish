// pc-admin/src/views/activity.js —— 促销活动管理（F-13.3 拼团/秒杀，对齐 ActivityController /api/activity/*）
// 运营端：查看进行中活动列表 + 创建活动。金额单位：分（表单以「元」录入，提交换算为分）。
// 后端 /api/activity/list 仅返回 ONGOING 活动；create 创建即生效。无独立 admin 列表/编辑/终止端点，本视图按契约收敛。
import { activityApi, adminApi } from '../api.js';
import { formatMixin, notifyError } from '../utils/format.js';

const ACTIVITY_TYPE = {
  SECKILL: { tag: 'danger', text: '秒杀' },
  GROUP: { tag: 'success', text: '拼团' }
};
const ACTIVITY_STATUS = {
  ONGOING: { tag: 'success', text: '进行中' },
  PENDING: { tag: 'info', text: '待开始' },
  ENDED: { tag: 'warning', text: '已结束' }
};

export default {
  name: 'Activity',
  mixins: [formatMixin],
  data() {
    return {
      list: [], total: 0, loading: false,
      itemOptions: [],                 // 商品下拉（id→title）
      itemTitleMap: {},                // 列表富化：id→title
      dialogVisible: false, submitting: false,
      form: this.blankForm()
    };
  },
  mounted() { this.load(); },
  methods: {
    blankForm() {
      return {
        itemId: null, type: 'SECKILL',
        activityPriceYuan: null, stock: null, limitPerUser: 1,
        groupSize: null, groupValidMinutes: null,
        startAt: this.nowStr(), endAt: this.addDaysStr(7)
      };
    },
    typeTag(t) { return (ACTIVITY_TYPE[t] || {}).tag || 'info'; },
    typeText(t) { return (ACTIVITY_TYPE[t] || {}).text || t; },
    statusTag(s) { return (ACTIVITY_STATUS[s] || {}).tag || 'info'; },
    statusText(s) { return (ACTIVITY_STATUS[s] || {}).text || s; },
    fmt(d) {
      const p = (n) => (n < 10 ? '0' : '') + n;
      return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' '
        + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    },
    nowStr() { return this.fmt(new Date()); },
    addDaysStr(n) { const d = new Date(); d.setDate(d.getDate() + n); return this.fmt(d); },
    // 后端 LocalDateTime 经 Jackson 默认 ISO 反序列化（需 'T' 分隔），前端表单用空格分隔，提交前转换
    toIso(s) { return s ? s.replace(' ', 'T') : s; },
    progressText(a) {
      const stock = a.stock || 0, sold = a.soldCount || 0;
      return stock > 0 ? Math.min(100, Math.round(sold / stock * 100)) + '%' : '0%';
    },
    async load() {
      this.loading = true;
      try {
        const [acts, items] = await Promise.all([activityApi.list(), adminApi.items({ page: 1, size: 200 })]);
        this.itemTitleMap = {};
        for (const it of (items.list || [])) this.itemTitleMap[it.id] = it.title;
        this.list = (acts.list || []).map(a => Object.assign({}, a, { itemTitle: this.itemTitleMap[a.itemId] || ('商品#' + a.itemId) }));
        this.total = acts.total;
      } catch (e) {
        notifyError(this, e, '加载活动列表失败');
      } finally { this.loading = false; }
    },
    async ensureItems() {
      if (this.itemOptions.length) return;
      try {
        const r = await adminApi.items({ page: 1, size: 200 });
        this.itemOptions = (r.list || []).map(it => ({ label: (it.title || '') + ' (#' + it.id + ')', value: it.id }));
      } catch (e) {
        notifyError(this, e, '加载商品列表失败');
      }
    },
    async openCreate() {
      this.form = this.blankForm();
      this.submitting = false;
      this.dialogVisible = true;
      await this.ensureItems();
    },
    async submit() {
      const f = this.form;
      if (!f.itemId) { this.$message.warning('请选择活动商品'); return; }
      if (!(f.activityPriceYuan > 0)) { this.$message.warning('请输入活动价（元）'); return; }
      if (!(f.stock > 0)) { this.$message.warning('请输入活动库存'); return; }
      if (!f.startAt || !f.endAt) { this.$message.warning('请选择活动起止时间'); return; }
      if (f.endAt <= f.startAt) { this.$message.warning('结束时间需晚于开始时间'); return; }
      if (f.type === 'GROUP' && !(f.groupSize >= 2)) { this.$message.warning('拼团成团人数至少 2 人'); return; }
      const dto = {
        itemId: f.itemId, type: f.type,
        activityPrice: Math.round(f.activityPriceYuan * 100),   // 元→分
        stock: f.stock, limitPerUser: f.limitPerUser || 1,
        groupSize: f.type === 'GROUP' ? f.groupSize : null,
        groupValidMinutes: f.type === 'GROUP' ? (f.groupValidMinutes || 1440) : null,
        startAt: this.toIso(f.startAt), endAt: this.toIso(f.endAt)
      };
      this.submitting = true;
      try {
        await activityApi.create(dto);
        this.$message.success('活动创建成功（已生效）');
        this.dialogVisible = false;
        this.load();
      } catch (e) {
        notifyError(this, e, '创建活动失败');
      } finally { this.submitting = false; }
    }
  },
  template: `
  <div>
    <h2 class="page-title">促销活动管理</h2>
    <el-card shadow="never">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
        <span class="muted">运营创建秒杀 / 拼团活动，用户端「限时活动」页实时展示并参与。</span>
        <el-button type="primary" @click="openCreate">+ 创建活动</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="商品" min-width="220">
          <template #default="{row}">
            <div class="cell-goods">
              <img :src="'https://picsum.photos/seed/'+row.itemId+'/80/80'" class="thumb"/>
              <div><div class="g-title">{{ row.itemTitle }}</div><div class="g-sub">商品 ID: {{ row.itemId }}</div></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{row}"><el-tag :type="typeTag(row.type)" size="small">{{ typeText(row.type) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="活动价" width="120"><template #default="{row}">{{ yuan(row.activityPrice) }}</template></el-table-column>
        <el-table-column label="活动库存" width="100" prop="stock"></el-table-column>
        <el-table-column label="已售" width="80" prop="soldCount"></el-table-column>
        <el-table-column label="售出进度" width="120">
          <template #default="{row}">{{ progressText(row) }}</template>
        </el-table-column>
        <el-table-column label="每人限购" width="100"><template #default="{row}">{{ row.limitPerUser > 0 ? row.limitPerUser : '不限' }}</template></el-table-column>
        <el-table-column label="拼团" width="120">
          <template #default="{row}">
            <span v-if="row.type==='GROUP'">{{ row.groupSize }} 人 / {{ (row.groupValidMinutes||0)/60 }}h</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="起止时间" min-width="200">
          <template #default="{row}">{{ row.startAt }} ~ {{ row.endAt }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}"><el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px" class="muted">共 {{ total }} 个进行中活动</div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="创建促销活动" width="560px">
      <el-form label-width="110px" v-loading="submitting">
        <el-form-item label="活动商品">
          <el-select v-model="form.itemId" placeholder="选择商品" filterable style="width:100%">
            <el-option v-for="o in itemOptions" :key="o.value" :label="o.label" :value="o.value"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="活动类型">
          <el-radio-group v-model="form.type">
            <el-radio label="SECKILL">秒杀</el-radio>
            <el-radio label="GROUP">拼团</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="活动价（元）">
          <el-input-number v-model="form.activityPriceYuan" :min="0" :precision="2" :step="10" style="width:180px"></el-input-number>
          <span class="muted">（提交时按 ×100 换算为「分」）</span>
        </el-form-item>
        <el-form-item label="活动库存">
          <el-input-number v-model="form.stock" :min="1" :step="1" style="width:180px"></el-input-number>
        </el-form-item>
        <el-form-item label="每人限购">
          <el-input-number v-model="form.limitPerUser" :min="1" :step="1" style="width:180px"></el-input-number>
          <span class="muted">（库存配额，非 0 即上限）</span>
        </el-form-item>
        <template v-if="form.type==='GROUP'">
          <el-form-item label="成团人数">
            <el-input-number v-model="form.groupSize" :min="2" :step="1" style="width:180px"></el-input-number>
          </el-form-item>
          <el-form-item label="拼团有效">
            <el-input-number v-model="form.groupValidMinutes" :min="1" :step="60" style="width:180px"></el-input-number>
            <span class="muted">分钟（默认 1440=24h）</span>
          </el-form-item>
        </template>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startAt" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width:100%"></el-date-picker>
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endAt" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width:100%"></el-date-picker>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">创建并生效</el-button>
      </template>
    </el-dialog>
  </div>`
};
