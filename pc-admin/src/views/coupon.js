// pc-admin/src/views/coupon.js —— 优惠券运营（F-10 营销：发券管理）
import { couponApi } from '../api.js';
import { formatMixin, notifyError } from '../utils/format.js';
import { listPageMixin, crudDialogMixin } from '../mixins/index.js';

export default {
  name: 'Coupon',
  mixins: [formatMixin, listPageMixin, crudDialogMixin],
  data() {
    // list/total/loading 由 listPageMixin 提供；dialogVisible/submitting/form 由 crudDialogMixin 提供
    return {};
  },
  mounted() { this.load(); },
  methods: {
    emptyForm() {
      return {
        name: '', type: 'FULL_REDUCTION',
        thresholdYuan: null, reduceYuan: null, discountZhe: null, maxDiscountYuan: null,
        scope: 'ALL', scopeId: null,
        totalCount: 1000, perUserLimit: 1,
        startAt: '', endAt: ''
      };
    },
    async load() {
      this.loading = true;
      try {
        const r = await couponApi.list({});
        this.list = (r.list || []).map(c => ({ ...c, claimed: c.claimedCount || 0, total: c.totalCount || 0 }));
        this.total = r.total || this.list.length;
      } catch (e) { this.$message.error('加载优惠券失败'); }
      finally { this.loading = false; }
    },
    typeText(t) {
      return { FULL_REDUCTION: '满减券', NO_THRESHOLD: '无门槛券', DISCOUNT: '折扣券' }[t] || t;
    },
    typeTag(t) {
      return { FULL_REDUCTION: 'warning', NO_THRESHOLD: 'success', DISCOUNT: 'info' }[t] || 'info';
    },
    statusText(s) {
      return { ACTIVE: '发放中', PAUSED: '已暂停', ENDED: '已结束' }[s] || s;
    },
    statusTag(s) {
      return { ACTIVE: 'success', PAUSED: 'warning', ENDED: 'info' }[s] || 'info';
    },
    scopeText(c) {
      if (c.scope === 'ALL') return '全场通用';
      if (c.scope === 'CATEGORY') return '指定类目 #' + c.scopeId;
      if (c.scope === 'ITEM') return '指定商品 #' + c.scopeId;
      return '全场通用';
    },
    fenToYuan(fen) {
      const v = Number(fen || 0) / 100;
      return isNaN(v) ? '0.00' : v.toFixed(2);
    },
    descText(c) {
      if (!c) return '';
      if (c.type === 'FULL_REDUCTION') return '满' + this.fenToYuan(c.thresholdAmount) + '减' + this.fenToYuan(c.reduceAmount);
      if (c.type === 'NO_THRESHOLD') return '立减' + this.fenToYuan(c.reduceAmount) + '元';
      if (c.type === 'DISCOUNT') {
        const z = Math.round((c.discountRate == null ? 1 : c.discountRate) * 100) / 10;
        const rateText = Number.isInteger(z) ? (z + '') : z.toFixed(1);
        const cap = c.maxDiscountAmount ? '(封顶' + this.fenToYuan(c.maxDiscountAmount) + ')' : '';
        return rateText + '折' + cap;
      }
      return '';
    },
    async submitCreate() {
      const f = this.form;
      if (!f.name || !f.name.trim()) return this.$message.error('请填写券名称');
      if (!f.type) return this.$message.error('请选择券类型');
      if (f.type === 'FULL_REDUCTION') {
        if (f.thresholdYuan == null || f.reduceYuan == null) return this.$message.error('满减券需填写门槛与减免金额');
      } else if (f.type === 'NO_THRESHOLD') {
        if (f.reduceYuan == null) return this.$message.error('无门槛券需填写减免金额');
      } else if (f.type === 'DISCOUNT') {
        if (f.discountZhe == null || f.discountZhe <= 0 || f.discountZhe >= 10) return this.$message.error('折扣率需在 0~10 之间（如 9 表示 9 折）');
      }
      if (f.scope !== 'ALL' && (f.scopeId == null)) return this.$message.error('请填写适用的类目/商品 ID');
      if (!f.totalCount || f.totalCount <= 0) return this.$message.error('发放总量需大于 0');
      if (!f.startAt || !f.endAt) return this.$message.error('请选择生效/失效时间');
      if (new Date(f.startAt).getTime() >= new Date(f.endAt).getTime()) return this.$message.error('失效时间须晚于生效时间');

      const dto = {
        name: f.name.trim(),
        type: f.type,
        thresholdAmount: Math.round((f.thresholdYuan || 0) * 100),
        reduceAmount: Math.round((f.reduceYuan || 0) * 100),
        discountRate: f.type === 'DISCOUNT' ? Math.round(f.discountZhe * 10) / 100 : 1.0,
        maxDiscountAmount: Math.round((f.maxDiscountYuan || 0) * 100),
        scope: f.scope,
        scopeId: f.scope === 'ALL' ? null : Number(f.scopeId),
        totalCount: Number(f.totalCount),
        perUserLimit: Number(f.perUserLimit || 1),
        startAt: f.startAt,
        endAt: f.endAt
      };
      this.submitting = true;
      try {
        await couponApi.create(dto);
        this.$message.success('发券成功，已自动进入发放中');
        this.dialogVisible = false;
        this.load();
      } catch (e) { this.$message.error('发券失败'); }
      finally { this.submitting = false; }
    },
    async setStatus(row, status) {
      const tip = { ACTIVE: '启用发放', PAUSED: '暂停发放', ENDED: '结束发放' }[status];
      try {
        await this.$confirm('确认' + tip + '优惠券「' + row.name + '」？', '提示', { type: 'warning' });
      } catch (e) { return; }
      try {
        await couponApi.setStatus(row.id, status);
        this.$message.success(tip + '成功');
        row.status = status;
      } catch (e) { this.$message.error('操作失败'); }
    }
  },
  template: `
  <div>
    <h2 class="page-title">发券管理
      <el-button size="small" type="primary" style="margin-left:12px" @click="openCreate(emptyForm())">新建优惠券</el-button>
    </h2>
    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="券名称" prop="name" min-width="180"></el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{row}"><el-tag :type="typeTag(row.type)" size="small">{{ typeText(row.type) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="优惠内容" min-width="160">
          <template #default="{row}">{{ descText(row) }}</template>
        </el-table-column>
        <el-table-column label="适用范围" min-width="140">
          <template #default="{row}">{{ scopeText(row) }}</template>
        </el-table-column>
        <el-table-column label="领取进度" width="160">
          <template #default="{row}">
            <el-progress :percentage="row.total ? Math.min(100, Math.round(row.claimed / row.total * 100)) : 0"
              :stroke-width="10"></el-progress>
            <div style="font-size:12px;color:#909399">{{ row.claimed }} / {{ row.total }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}"><el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="有效期" width="180">
          <template #default="{row}"><span style="font-size:12px">{{ row.startAt }}<br/>{{ row.endAt }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{row}">
            <el-button size="small" v-if="row.status !== 'ACTIVE'" type="success" plain @click="setStatus(row,'ACTIVE')">启用</el-button>
            <el-button size="small" v-if="row.status === 'ACTIVE'" type="warning" plain @click="setStatus(row,'PAUSED')">暂停</el-button>
            <el-button size="small" v-if="row.status !== 'ENDED'" type="danger" plain @click="setStatus(row,'ENDED')">结束</el-button>
            <span v-if="row.status === 'ENDED'" style="font-size:12px;color:#909399">已结束</span>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px;color:#909399">共 {{ total }} 张优惠券</div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新建优惠券" width="560px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="券名称"><el-input v-model="form.name" placeholder="如：全场满100减20" maxlength="30"></el-input></el-form-item>
        <el-form-item label="券类型">
          <el-select v-model="form.type" style="width:100%">
            <el-option label="满减券" value="FULL_REDUCTION"></el-option>
            <el-option label="无门槛券" value="NO_THRESHOLD"></el-option>
            <el-option label="折扣券" value="DISCOUNT"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.type === 'FULL_REDUCTION'" label="满减(元)">
          <el-input-number v-model="form.thresholdYuan" :min="0" :precision="2" :step="10" controls-position="right" style="width:100%"></el-input-number>
          <span class="hint">门槛金额</span>
        </el-form-item>
        <el-form-item v-if="form.type !== 'DISCOUNT'" label="减免(元)">
          <el-input-number v-model="form.reduceYuan" :min="0" :precision="2" :step="5" controls-position="right" style="width:100%"></el-input-number>
          <span class="hint">无门槛券/满减券的立减金额</span>
        </el-form-item>
        <el-form-item v-if="form.type === 'DISCOUNT'" label="折扣率(折)">
          <el-input-number v-model="form.discountZhe" :min="0.1" :max="9.9" :precision="1" :step="0.1" controls-position="right" style="width:100%"></el-input-number>
          <span class="hint">如 9 表示 9 折（rate=0.9）</span>
        </el-form-item>
        <el-form-item v-if="form.type === 'DISCOUNT'" label="封顶(元)">
          <el-input-number v-model="form.maxDiscountYuan" :min="0" :precision="2" :step="10" controls-position="right" style="width:100%"></el-input-number>
          <span class="hint">0 表示不封顶</span>
        </el-form-item>
        <el-form-item label="适用范围">
          <el-select v-model="form.scope" style="width:100%">
            <el-option label="全场通用" value="ALL"></el-option>
            <el-option label="指定类目" value="CATEGORY"></el-option>
            <el-option label="指定商品" value="ITEM"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scope !== 'ALL'" label="适用对象ID">
          <el-input-number v-model="form.scopeId" :min="1" :precision="0" controls-position="right" style="width:100%"></el-input-number>
          <span class="hint">{{ form.scope === 'CATEGORY' ? '类目 ID' : '商品 ID' }}</span>
        </el-form-item>
        <el-form-item label="发放总量">
          <el-input-number v-model="form.totalCount" :min="1" :precision="0" controls-position="right" style="width:100%"></el-input-number>
        </el-form-item>
        <el-form-item label="每人限领">
          <el-input-number v-model="form.perUserLimit" :min="1" :precision="0" :max="20" controls-position="right" style="width:100%"></el-input-number>
        </el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker v-model="form.startAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择生效时间" style="width:100%"></el-date-picker>
        </el-form-item>
        <el-form-item label="失效时间">
          <el-date-picker v-model="form.endAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择失效时间" style="width:100%"></el-date-picker>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确认发券</el-button>
      </template>
    </el-dialog>
  </div>`
};
