<template>
  <div>
    <page-header title="营销玩法配置" />
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- 活动列表 -->
        <el-tab-pane label="活动列表" name="list">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
            <span class="muted">统一活动内核驱动的砍价 / 抽奖 / 直播秒杀，库存实时扣减、亏损自动阻断。</span>
            <el-button type="primary" @click="openCreate">+ 创建活动</el-button>
          </div>
          <el-table :data="list" v-loading="loading" border stripe>
            <el-table-column prop="id" label="ID" width="80"></el-table-column>
            <el-table-column label="玩法" width="100">
              <template #default="{row}"><el-tag :type="kindTag(row.kind)" size="small">{{ kindText(row.kind) }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="180"></el-table-column>
            <el-table-column label="活动价" width="120">
              <template #default="{row}">{{ row.kind === 'LOTTERY' ? '—' : yuan(row.activityPrice) }}</template>
            </el-table-column>
            <el-table-column prop="stock" label="库存" width="80"></el-table-column>
            <el-table-column prop="soldCount" label="已参与" width="80"></el-table-column>
            <el-table-column label="人群" width="110">
              <template #default="{row}">{{ audienceText(row.audienceType) }}</template>
            </el-table-column>
            <el-table-column label="补贴预算" width="120">
              <template #default="{row}">{{ row.subsidyBudgetFen ? yuan(row.subsidyBudgetFen) : '不限' }}</template>
            </el-table-column>
            <el-table-column label="起止时间" min-width="200">
              <template #default="{row}">{{ row.startAt }} ~ {{ row.endAt }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{row}"><el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{row}">
                <el-button v-if="row.status==='DRAFT'" size="small" type="success" @click="publish(row)">发布</el-button>
                <el-button v-if="row.status==='ONGOING'" size="small" type="warning" @click="reconcile(row)">结束对账</el-button>
                <el-button v-if="row.kind==='LOTTERY' && row.status!=='ENDED'" size="small" @click="gotoPrizes(row)">奖品</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 监控报表 -->
        <el-tab-pane label="监控报表" name="monitor">
          <div v-loading="monitorLoading" style="display:flex;flex-wrap:wrap;gap:16px">
            <el-card v-for="m in monitorCards" :key="m.label" shadow="hover" class="metric">
              <div class="m-val">{{ m.value }}</div>
              <div class="m-label">{{ m.label }}</div>
            </el-card>
          </div>
          <el-alert v-if="monitor" class="m-alert" type="info" :closable="false"
            :title="`进行中 ${monitor.ongoing} · 草稿 ${monitor.draft} · 已结束 ${monitor.ended}；今日新增参与 ${monitor.joinedToday}；风控拦截 ${monitor.riskBlocked} 次，补贴超额告警 ${monitor.subsidyBudgetExceeded} 次`" />
        </el-tab-pane>

        <!-- 抽奖奖品配置 -->
        <el-tab-pane label="抽奖奖品配置" name="prizes">
          <div style="display:flex;gap:12px;align-items:center;margin-bottom:12px">
            <el-select v-model="prizeActivityId" filterable placeholder="选择抽奖活动" style="width:320px">
              <el-option v-for="o in lotteryActs" :key="o.id" :label="`#${o.id} ${o.title}`" :value="o.id"></el-option>
            </el-select>
            <el-button type="primary" :disabled="!prizeActivityId" @click="addPrize">+ 新增奖品</el-button>
            <el-button :disabled="!prizeActivityId" @click="savePrizes">保存配置</el-button>
          </div>
          <el-table :data="prizes" border stripe>
            <el-table-column label="奖品名称" min-width="160">
              <template #default="{row}"><el-input v-model="row.name" placeholder="如：8 元无门槛券"></el-input></template>
            </el-table-column>
            <el-table-column label="类型" width="140">
              <template #default="{row}">
                <el-select v-model="row.type" style="width:100%">
                  <el-option label="优惠券" value="COUPON"></el-option>
                  <el-option label="实物" value="PHYSICAL"></el-option>
                  <el-option label="未中奖" value="NONE"></el-option>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="库存" width="120">
              <template #default="{row}"><el-input-number v-model="row.stock" :min="0" :step="1" style="width:100%"></el-input-number></template>
            </el-table-column>
            <el-table-column label="面值(元)" width="130">
              <template #default="{row}"><el-input-number v-model="row.valueYuan" :min="0" :precision="2" :step="1" style="width:100%"></el-input-number></template>
            </el-table-column>
            <el-table-column label="概率(%)" width="140">
              <template #default="{row}"><el-input-number v-model="row.probPct" :min="0" :max="100" :precision="1" :step="1" style="width:100%"></el-input-number></template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{row,$index}"><el-button type="danger" size="small" @click="prizes.splice($index,1)">删除</el-button></template>
            </el-table-column>
          </el-table>
          <div class="muted" style="margin-top:8px">概率合计：{{ probSum }}%（后台校验合计须 ≤ 100，且实物/高价值奖品触发实名，REQ-09/REQ-10）</div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 创建向导 -->
    <el-dialog v-model="dialogVisible" title="创建营销活动（向导）" width="620px">
      <el-form label-width="120px" v-loading="submitting">
        <el-form-item label="玩法类型">
          <el-radio-group v-model="form.kind">
            <el-radio label="BARGAIN">砍价</el-radio>
            <el-radio label="LOTTERY">抽奖</el-radio>
            <el-radio label="LIVE_SECKILL">直播秒杀</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="活动标题"><el-input v-model="form.title" placeholder="如：iPhone 14 Pro 砍到底"></el-input></el-form-item>

        <template v-if="form.kind!=='LOTTERY'">
          <el-form-item label="关联商品">
            <el-select v-model="form.itemId" filterable placeholder="选择商品" style="width:100%">
              <el-option v-for="o in itemOptions" :key="o.value" :label="o.label" :value="o.value"></el-option>
            </el-select>
          </el-form-item>
        </template>

        <template v-if="form.kind==='BARGAIN'">
          <el-form-item label="商品原价(元)"><el-input-number v-model="form.originYuan" :min="0" :precision="2" :step="100" style="width:200px"></el-input-number></el-form-item>
          <el-form-item label="砍价底价(元)"><el-input-number v-model="form.floorYuan" :min="0" :precision="2" :step="10" style="width:200px"></el-input-number>
            <span class="muted">（须 ≥ 成本底价，否则创建被亏损阻断 REQ-14）</span></el-form-item>
        </template>
        <template v-if="form.kind==='LIVE_SECKILL'">
          <el-form-item label="秒杀价(元)"><el-input-number v-model="form.activityPriceYuan" :min="0" :precision="2" :step="10" style="width:200px"></el-input-number>
            <span class="muted">（须 ≥ 成本底价）</span></el-form-item>
        </template>

        <el-form-item label="活动库存"><el-input-number v-model="form.stock" :min="1" :step="1" style="width:200px"></el-input-number></el-form-item>
        <el-form-item label="每人限购"><el-input-number v-model="form.limitPerUser" :min="1" :step="1" style="width:200px"></el-input-number></el-form-item>
        <el-form-item label="人群定向">
          <el-select v-model="form.audienceType" style="width:200px">
            <el-option label="全部用户" value="ALL"></el-option>
            <el-option label="新用户" value="NEW_USER"></el-option>
            <el-option label="等级用户" value="LEVEL"></el-option>
            <el-option label="实名用户" value="REALNAME"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="成本底价(元)">
          <el-input-number v-model="form.lossFloorYuan" :min="0" :precision="2" :step="10" style="width:200px"></el-input-number>
          <span class="muted">（亏损阻断阈值：玩法最终价不得低于此成本线 REQ-14）</span>
        </el-form-item>
        <el-form-item label="补贴预算(元)">
          <el-input-number v-model="form.subsidyBudgetYuan" :min="0" :precision="2" :step="100" style="width:200px"></el-input-number>
          <span class="muted">（0/空 = 不限；超额触发 SUBSIDY_BUDGET_EXCEEDED REQ-15）</span>
        </el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="form.startAt" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width:100%"></el-date-picker></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="form.endAt" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width:100%"></el-date-picker></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">创建（草稿）</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { mktActivityApi, adminApi } from '@/api';
import { formatMixin, notifyError } from '@/utils/format';

const KIND = {
  BARGAIN: { tag: 'warning', text: '砍价' },
  LOTTERY: { tag: 'purple', text: '抽奖' },
  LIVE_SECKILL: { tag: 'danger', text: '直播秒杀' }
};
const STATUS = {
  DRAFT: { tag: 'info', text: '草稿' },
  ONGOING: { tag: 'success', text: '进行中' },
  ENDED: { tag: 'warning', text: '已结束' }
};
const AUDIENCE = { ALL: '全部', NEW_USER: '新用户', LEVEL: '等级', REALNAME: '实名' };

export default {
  name: 'MktActivity',
  mixins: [formatMixin],
  data() {
    return {
      activeTab: 'list',
      list: [], loading: false,
      itemOptions: [],
      monitor: null, monitorLoading: false,
      lotteryActs: [], prizeActivityId: null, prizes: [],
      dialogVisible: false, submitting: false,
      form: this.blankForm()
    };
  },
  computed: {
    monitorCards() {
      if (!this.monitor) return [];
      return [
        { label: '进行中活动', value: this.monitor.ongoing },
        { label: '草稿活动', value: this.monitor.draft },
        { label: '已结束', value: this.monitor.ended },
        { label: '今日新增参与', value: this.monitor.joinedToday },
        { label: '总库存', value: this.monitor.totalStock },
        { label: '风控拦截', value: this.monitor.riskBlocked },
        { label: '补贴超额告警', value: this.monitor.subsidyBudgetExceeded },
        { label: '砍价成团', value: this.monitor.bargainQualified },
        { label: '抽奖中奖', value: this.monitor.lotteryWon }
      ];
    },
    probSum() {
      return this.prizes.reduce((s, p) => s + (Number(p.probPct) || 0), 0).toFixed(1);
    }
  },
  mounted() { this.load(); },
  watch: {
    activeTab(v) {
      if (v === 'monitor' && !this.monitor) this.loadMonitor();
      if (v === 'prizes') this.loadLotteryActs();
    }
  },
  methods: {
    blankForm() {
      return {
        kind: 'BARGAIN', title: '', itemId: null,
        originYuan: null, floorYuan: null, activityPriceYuan: null,
        stock: 50, limitPerUser: 1, audienceType: 'ALL',
        lossFloorYuan: null, subsidyBudgetYuan: null,
        startAt: this.nowStr(), endAt: this.addDaysStr(7)
      };
    },
    kindTag(k) { return (KIND[k] || {}).tag || 'info'; },
    kindText(k) { return (KIND[k] || {}).text || k; },
    statusTag(s) { return (STATUS[s] || {}).tag || 'info'; },
    statusText(s) { return (STATUS[s] || {}).text || s; },
    audienceText(a) { return AUDIENCE[a] || a; },
    fmt(d) {
      const p = (n) => (n < 10 ? '0' : '') + n;
      return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' '
        + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    },
    nowStr() { return this.fmt(new Date()); },
    addDaysStr(n) { const d = new Date(); d.setDate(d.getDate() + n); return this.fmt(d); },
    toIso(s) { return s ? s.replace(' ', 'T') : s; },
    async load() {
      this.loading = true;
      try {
        const acts = await mktActivityApi.list();
        this.list = (acts || []).map(a => Object.assign({}, a));
      } catch (e) { notifyError(this, e, '加载活动列表失败'); }
      finally { this.loading = false; }
    },
    async loadMonitor() {
      this.monitorLoading = true;
      try { this.monitor = await mktActivityApi.monitor(); } catch (e) { notifyError(this, e, '加载监控失败'); }
      finally { this.monitorLoading = false; }
    },
    async loadLotteryActs() {
      if (this.lotteryActs.length) return;
      try { this.lotteryActs = (await mktActivityApi.list()).filter(a => a.kind === 'LOTTERY' && a.status !== 'ENDED'); }
      catch (e) { notifyError(this, e, '加载抽奖活动失败'); }
    },
    async ensureItems() {
      if (this.itemOptions.length) return;
      try {
        const r = await adminApi.items({ page: 1, size: 200 });
        this.itemOptions = (r.list || []).map(it => ({ label: (it.title || '') + ' (#' + it.id + ')', value: it.id }));
      } catch (e) { notifyError(this, e, '加载商品失败'); }
    },
    openCreate() { this.form = this.blankForm(); this.submitting = false; this.dialogVisible = true; this.ensureItems(); },
    async submit() {
      const f = this.form;
      if (!f.title) { this.$message.warning('请输入活动标题'); return; }
      if (!(f.stock > 0)) { this.$message.warning('请输入活动库存'); return; }
      if (!f.startAt || !f.endAt) { this.$message.warning('请选择起止时间'); return; }
      if (f.endAt <= f.startAt) { this.$message.warning('结束时间需晚于开始时间'); return; }
      if (f.kind === 'BARGAIN' && !(f.originYuan > 0 && f.floorYuan > 0)) { this.$message.warning('砍价需填写原价与底价'); return; }
      if (f.kind === 'LIVE_SECKILL' && !(f.activityPriceYuan > 0)) { this.$message.warning('秒杀需填写秒杀价'); return; }

      const dto = {
        kind: f.kind, title: f.title, itemId: f.itemId,
        stock: f.stock, limitPerUser: f.limitPerUser || 1,
        audienceType: f.audienceType,
        lossFloorFen: f.lossFloorYuan != null ? Math.round(f.lossFloorYuan * 100) : null,
        subsidyBudgetFen: f.subsidyBudgetYuan != null ? Math.round(f.subsidyBudgetYuan * 100) : null,
        startAt: this.toIso(f.startAt), endAt: this.toIso(f.endAt),
        playConfig: null
      };
      if (f.kind === 'BARGAIN') {
        dto.activityPrice = Math.round(f.floorYuan * 100); // 实体展示价取砍价底价
        dto.playConfig = JSON.stringify({ originPriceFen: Math.round(f.originYuan * 100), floorPriceFen: Math.round(f.floorYuan * 100) });
      } else if (f.kind === 'LIVE_SECKILL') {
        dto.activityPrice = Math.round(f.activityPriceYuan * 100);
      } else {
        dto.activityPrice = 0;
      }
      this.submitting = true;
      try {
        await mktActivityApi.create(dto);
        this.$message.success('活动创建成功（草稿态，发布后开放）');
        this.dialogVisible = false;
        this.load();
      } catch (e) { notifyError(this, e, '创建失败（成本底价高于玩法价将触发亏损阻断）'); }
      finally { this.submitting = false; }
    },
    async publish(row) {
      try {
        await mktActivityApi.publish(row.id);
        this.$message.success('已发布并开始预热');
        this.load();
      } catch (e) { notifyError(this, e, '发布失败'); }
    },
    async reconcile(row) {
      this.$confirm('结束活动并结算对账？', '提示', { type: 'warning' }).then(async () => {
        try {
          const r = await mktActivityApi.reconcile(row.id);
          this.$message.success(`对账完成：售出 ${r.stockSold}，回收库存 ${r.stockRecycled}，补贴 ${this.yuan(r.subsidyTotalFen)}`);
          this.load();
          if (this.monitor) this.loadMonitor();
        } catch (e) { notifyError(this, e, '对账失败'); }
      }).catch(() => {});
    },
    gotoPrizes(row) {
      this.activeTab = 'prizes';
      this.lotteryActs = (this.lotteryActs.length ? this.lotteryActs : this.list).filter(a => a.kind === 'LOTTERY' && a.status !== 'ENDED');
      this.prizeActivityId = row.id;
    },
    addPrize() {
      this.prizes.push({ name: '', type: 'COUPON', stock: 100, valueYuan: 0, probPct: 0 });
    },
    async savePrizes() {
      if (!this.prizeActivityId) { this.$message.warning('请先选择抽奖活动'); return; }
      if (this.prizes.some(p => !p.name)) { this.$message.warning('请填写奖品名称'); return; }
      const body = this.prizes.map(p => ({
        name: p.name, type: p.type, stock: p.stock,
        valueFen: Math.round((p.valueYuan || 0) * 100),
        probability: (Number(p.probPct) || 0) / 100
      }));
      try {
        await mktActivityApi.savePrizes(this.prizeActivityId, body);
        this.$message.success('奖品配置已保存（概率留痕 + 亏损校验）');
      } catch (e) { notifyError(this, e, '保存奖品失败'); }
    }
  }
};
</script>
