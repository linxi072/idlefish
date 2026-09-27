// pc-admin/src/views/member.js —— 会员等级与权益（F-13.2）
// 运营后台：等级配置（成长值阈值 + 权益）增删改 + 会员成长概览 + 权益说明
// 真实接口见 AdminMemberLevelController /api/admin/member/level/*
import { memberApi, toCsv, downloadCsv } from '../api.js';
import { formatMixin, notifyError } from '../utils/format.js';

const fmtInt = (n) => (n == null ? '0' : String(n));
// 千分比佣金减免 → 百分比文案（100‰ = 10%）
const discountText = (v) => v ? (v / 10).toFixed(1) + '%' : '无';

export default {
  name: 'Member',
  mixins: [formatMixin],
  data() {
    return {
      loading: true,
      tiers: [],
      growth: { list: [], total: 0 },
      growthPage: 1,
      growthSize: 20,
      dialogVisible: false,
      editing: null,
      form: { levelCode: '', levelName: '', minGrowth: 0, freeShipping: 0, priorityReview: 0, commissionDiscount: 0, icon: '', color: '#9e9e9e' },
      saving: false
    };
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try {
        const [tiers, g] = await Promise.all([
          memberApi.listTiers(),
          memberApi.growthList(this.growthPage, this.growthSize)
        ]);
        this.tiers = tiers || [];
        this.growth = g && g.list ? g : { list: [], total: 0 };
      } catch (e) {
        notifyError(this, e, '加载会员数据失败');
      } finally {
        this.loading = false;
      }
    },
    async changeGrowthPage(p) {
      this.growthPage = p;
      await this.loadGrowthOnly();
    },
    async loadGrowthOnly() {
      try {
        this.growth = await memberApi.growthList(this.growthPage, this.growthSize);
      } catch (e) {
        notifyError(this, e, '加载会员成长概览失败');
      }
    },
    openEdit(tier) {
      if (tier) {
        this.editing = tier.id;
        this.form = {
          id: tier.id, levelCode: tier.levelCode, levelName: tier.levelName,
          minGrowth: tier.minGrowth, freeShipping: tier.freeShipping ? 1 : 0,
          priorityReview: tier.priorityReview ? 1 : 0, commissionDiscount: tier.commissionDiscount || 0,
          icon: tier.icon || '', color: tier.color || '#9e9e9e'
        };
      } else {
        this.editing = null;
        this.form = { levelCode: '', levelName: '', minGrowth: 0, freeShipping: 0, priorityReview: 0, commissionDiscount: 0, icon: '🏅', color: '#9e9e9e' };
      }
      this.dialogVisible = true;
    },
    async save() {
      if (!this.form.levelCode || !this.form.levelName) {
        this.$message.error('等级编码与名称为必填');
        return;
      }
      this.saving = true;
      try {
        await memberApi.saveTier(this.form);
        this.$message.success('保存成功');
        this.dialogVisible = false;
        await this.load();
      } catch (e) {
        notifyError(this, e, '保存等级失败');
      } finally {
        this.saving = false;
      }
    },
    async del(tier) {
      try {
        await this.$confirm('确认删除等级「' + tier.levelName + '」？', '提示', { type: 'warning' });
      } catch (e) {
        return;
      }
      try {
        await memberApi.deleteTier(tier.id);
        this.$message.success('已删除');
        await this.load();
      } catch (e) {
        notifyError(this, e, '删除失败');
      }
    },
    // ===== 导出 =====
    exportTiers() {
      const headers = ['等级编码', '等级名称', '成长值阈值', '免运费', '优先审核', '佣金减免'];
      const rows = this.tiers.map(t => [
        t.levelCode, t.levelName, t.minGrowth,
        t.freeShipping ? '是' : '否', t.priorityReview ? '是' : '否', discountText(t.commissionDiscount)
      ]);
      downloadCsv('member-levels.csv', toCsv(headers, rows));
    },
    exportGrowth() {
      const headers = ['用户ID', '昵称', '成长值', '当前等级', '等级名称', '距下一级', '进度'];
      const rows = this.growth.list.map(g => [
        g.userId, g.nickname, g.growthValue, g.levelCode, g.levelName || '',
        g.growthToNext, (g.percent || 0) + '%'
      ]);
      downloadCsv('member-growth.csv', toCsv(headers, rows));
    },
    discountText,
    fmtInt
  },
  template: `
  <div v-loading="loading">
    <div class="page-head">
      <h2 class="page-title">会员等级与权益</h2>
      <div class="page-tools">
        <el-button size="small" type="primary" @click="openEdit(null)">新增等级</el-button>
        <el-button size="small" type="success" plain @click="exportTiers">导出等级配置</el-button>
      </div>
    </div>

    <el-card shadow="never" style="margin-bottom:20px">
      <template #header>
        <span>等级配置（成长值阈值 + 权益）</span>
        <el-button size="small" style="float:right" @click="load">刷新</el-button>
      </template>
      <el-table :data="tiers" size="small" border>
        <el-table-column prop="levelCode" label="编码" width="90"></el-table-column>
        <el-table-column label="等级" width="140">
          <template #default="{row}">
            <span class="level-badge" :style="{ background: row.color }">{{ row.icon }} {{ row.levelName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成长值阈值" width="120">
          <template #default="{row}">{{ fmtInt(row.minGrowth) }}</template>
        </el-table-column>
        <el-table-column label="免运费" width="90" align="center">
          <template #default="{row}"><el-tag :type="row.freeShipping ? 'success' : 'info'" size="small">{{ row.freeShipping ? '是' : '否' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="优先审核" width="90" align="center">
          <template #default="{row}"><el-tag :type="row.priorityReview ? 'success' : 'info'" size="small">{{ row.priorityReview ? '是' : '否' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="佣金减免" width="110" align="center">
          <template #default="{row}"><el-tag type="warning" size="small">{{ discountText(row.commissionDiscount) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{row}">
            <el-button size="small" type="text" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="text" danger @click="del(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-row :gutter="20">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <span>会员成长概览</span>
            <el-button size="small" style="float:right" @click="exportGrowth">导出</el-button>
          </template>
          <el-table :data="growth.list" size="small" border max-height="420">
            <el-table-column prop="userId" label="用户ID" width="90"></el-table-column>
            <el-table-column prop="nickname" label="昵称" width="120"></el-table-column>
            <el-table-column label="当前等级" width="140">
              <template #default="{row}">
                <span class="level-badge" :style="{ background: row.color }">{{ row.icon }} {{ row.levelName || row.levelCode }}</span>
              </template>
            </el-table-column>
            <el-table-column label="成长值" width="100" align="right">
              <template #default="{row}">{{ fmtInt(row.growthValue) }}</template>
            </el-table-column>
            <el-table-column label="距下一级" width="110" align="right">
              <template #default="{row}">{{ fmtInt(row.growthToNext) }}</template>
            </el-table-column>
            <el-table-column label="进度" min-width="160">
              <template #default="{row}">
                <el-progress :percentage="row.percent || 0" :stroke-width="14"></el-progress>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="growth.total" background layout="prev, pager, next, total"
            :total="growth.total" :page-size="growthSize" :current-page="growthPage"
            style="margin-top:12px" @current-change="changeGrowthPage"></el-pagination>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" header="权益说明">
          <ul class="benefit-list">
            <li><b>成长值获取：</b>每成交 1 元得 1 点成长值；每完成一次评价得 20 点。</li>
            <li><b>免运费：</b>达到对应等级后，发布/交易享平台运费减免。</li>
            <li><b>优先审核：</b>商品发布与评价进入优先审核队列，更快通过。</li>
            <li><b>佣金减免：</b>平台交易佣金按等级减免（千分比，100‰ = 10%）。</li>
            <li class="muted">成长值累计与等级重算在交易成功 / 评价通过时实时完成（best-effort，不阻断主流程）。</li>
          </ul>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog :title="editing ? '编辑等级' : '新增等级'" v-model="dialogVisible" width="440px">
      <el-form label-width="100px" size="small">
        <el-form-item label="等级编码"><el-input v-model="form.levelCode" placeholder="如 L4（唯一）"></el-input></el-form-item>
        <el-form-item label="等级名称"><el-input v-model="form.levelName" placeholder="如 黄金会员"></el-input></el-form-item>
        <el-form-item label="成长值阈值"><el-input-number v-model="form.minGrowth" :min="0" :step="100"></el-input-number></el-form-item>
        <el-form-item label="图标"><el-input v-model="form.icon" placeholder="emoji 或图标名" style="width:160px"></el-input></el-form-item>
        <el-form-item label="主题色"><el-color-picker v-model="form.color"></el-color-picker></el-form-item>
        <el-form-item label="免运费"><el-switch v-model="form.freeShipping" :active-value="1" :inactive-value="0"></el-switch></el-form-item>
        <el-form-item label="优先审核"><el-switch v-model="form.priorityReview" :active-value="1" :inactive-value="0"></el-switch></el-form-item>
        <el-form-item label="佣金减免(‰)"><el-input-number v-model="form.commissionDiscount" :min="0" :max="1000" :step="10"></el-input-number></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialogVisible=false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>`,
  created() { this.discountText = discountText; this.fmtInt = fmtInt; }
};
