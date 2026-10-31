<template>
  <div v-loading="loading">
    <page-header title="成长值运营">
      <template #extra>
        <el-button size="small" @click="loadAll">刷新</el-button>
      </template>
    </page-header>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- ============ 规则与口径 ============ -->
      <el-tab-pane label="规则与口径" name="config">
        <el-card shadow="never" v-if="config">
          <el-descriptions :column="2" border size="small" title="成长值规则配置（只读）">
            <el-descriptions-item label="功能总开关">
              <el-tag :type="config.enabled ? 'success' : 'info'" size="small">{{ config.enabled ? '开启' : '关闭' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="规则版本">{{ config.ruleVersion }}</el-descriptions-item>

            <el-descriptions-item label="交易每元成长值">{{ config.tradeGrowthPerYuanFen }}（每 100 分）</el-descriptions-item>
            <el-descriptions-item label="交易单笔封顶">{{ config.tradeCapPerOrder || '不封顶' }}</el-descriptions-item>
            <el-descriptions-item label="发布固定">{{ config.publishFixed }}</el-descriptions-item>
            <el-descriptions-item label="评价固定">{{ config.reviewFixed }}</el-descriptions-item>
            <el-descriptions-item label="验货固定">{{ config.inspectionFixed }}</el-descriptions-item>
            <el-descriptions-item label="纠纷和解这个固定">{{ config.reconcileFixed }}</el-descriptions-item>

            <el-descriptions-item label="关联账户阈值">{{ config.maxLinkedAccounts }} 个</el-descriptions-item>
            <el-descriptions-item label="频次阈值">{{ config.maxEarnInWindow }} 次 / {{ config.earnWindowDays }} 天</el-descriptions-item>
            <el-descriptions-item label="金额门槛">{{ yuan(config.minAmountFen) }}</el-descriptions-item>
            <el-descriptions-item label="判责扣减封顶">{{ config.disputePenaltyCap }}</el-descriptions-item>

            <el-descriptions-item label="衰减开关">
              <el-tag :type="config.decayEnabled ? 'success' : 'info'" size="small">{{ config.decayEnabled ? '开启' : '关闭' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="不活跃阈值">{{ config.decayThresholdDays }} 天</el-descriptions-item>
            <el-descriptions-item label="每日衰减">{{ config.decayPerDay }}</el-descriptions-item>
            <el-descriptions-item label="衰减下限">{{ config.decayFloor }}</el-descriptions-item>
            <el-descriptions-item label="提前通知天数">{{ config.decayNotifyDays }} 天</el-descriptions-item>
            <el-descriptions-item label="告警管理员ID">{{ config.alertAdminUserId }}</el-descriptions-item>
          </el-descriptions>
          <el-alert type="info" :closable="false" style="margin-top:12px"
            title="成长值仅作等级门槛，不可消耗；用户以积分兑换权益（消耗积分）。规则变更需同步递增 ruleVersion 以支撑流水口径区分与不追溯重算（REQ-14）。" />
        </el-card>
      </el-tab-pane>

      <!-- ============ 账户与刷分 ============ -->
      <el-tab-pane label="账户与刷分" name="accounts">
        <el-card shadow="never">
          <template #header>
            <span>成长值账户（冻结名单 / 流失预警）</span>
            <el-radio-group v-model="accountFrozenOnly" size="small" style="float:right" @change="loadAccounts">
              <el-radio-button :value="false">全部账户</el-radio-button>
              <el-radio-button :value="true">仅冻结（刷分）</el-radio-button>
            </el-radio-group>
          </template>
          <el-table :data="accounts.list" size="small" border max-height="480">
            <el-table-column prop="userId" label="用户ID" width="90"></el-table-column>
            <el-table-column label="等级" width="120">
              <template #default="{row}"><el-tag size="small">{{ row.levelCode }} {{ row.levelName }}</el-tag></template>
            </el-table-column>
            <el-table-column label="成长值" width="90" align="right"><template #default="{row}">{{ fmtInt(row.growthValue) }}</template></el-table-column>
            <el-table-column label="累计获得" width="100" align="right"><template #default="{row}">{{ fmtInt(row.totalEarned) }}</template></el-table-column>
            <el-table-column label="冻结" width="80" align="center">
              <template #default="{row}"><el-tag :type="row.frozen ? 'danger' : 'success'" size="small">{{ row.frozen ? '已冻结' : '正常' }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="lastActiveAt" label="最后活跃" width="150"></el-table-column>
            <el-table-column label="距衰减" width="100" align="center">
              <template #default="{row}">
                <el-tag v-if="row.daysUntilDecay === 0" type="danger" size="small">已进入</el-tag>
                <el-tag v-else-if="row.daysUntilDecay <= config?.decayNotifyDays" type="warning" size="small">{{ row.daysUntilDecay }} 天</el-tag>
                <span v-else>{{ row.daysUntilDecay }} 天</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{row}">
                <el-button size="small" type="text" @click="openLogs(row)">流水</el-button>
                <el-button v-if="!row.frozen" size="small" type="text" danger @click="openFreeze(row)">冻结</el-button>
                <el-button v-else size="small" type="text" @click="unfreeze(row)">解冻</el-button>
                <el-button size="small" type="text" @click="openRestore(row)">申诉回补</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="accounts.total" background layout="prev, pager, next, total"
            :total="accounts.total" :page-size="accountsSize" :current-page="accountsPage"
            style="margin-top:12px" @current-change="changeAccountsPage"></el-pagination>
          <el-button size="small" style="margin-top:12px" @click="exportAccounts">导出账户</el-button>
        </el-card>
      </el-tab-pane>

      <!-- ============ 流水对账 ============ -->
      <el-tab-pane label="流水对账" name="logs">
        <el-card shadow="never">
          <el-form :inline="true" size="small" style="margin-bottom:12px">
            <el-form-item label="用户ID">
              <el-select v-model="logUserId" filterable placeholder="选择账户" style="width:160px" @change="loadLogs">
                <el-option v-for="a in accounts.list" :key="a.userId" :label="a.userId + ' (' + a.levelName + ')'" :value="a.userId"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="来源">
              <el-select v-model="logBizType" clearable placeholder="全部来源" style="width:160px" @change="loadLogs">
                <el-option v-for="(t, k) in BIZ_TEXT" :key="k" :label="t" :value="k"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item><el-button type="primary" @click="loadLogs">查询</el-button></el-form-item>
          </el-form>
          <el-table :data="logs.list" size="small" border max-height="460">
            <el-table-column prop="id" label="ID" width="70"></el-table-column>
            <el-table-column label="来源" width="120"><template #default="{row}"><el-tag size="small" :type="row.delta < 0 ? 'danger' : 'success'">{{ bizText(row.bizType) }}</el-tag></template></el-table-column>
            <el-table-column prop="bizId" label="关联单号" width="150"></el-table-column>
            <el-table-column label="增量" width="90" align="right"><template #default="{row}"><span :style="{color: row.delta < 0 ? 'var(--danger)' : 'var(--success)'}">{{ row.delta > 0 ? '+' : '' }}{{ row.delta }}</span></template></el-table-column>
            <el-table-column label="变更前" width="90" align="right"><template #default="{row}">{{ fmtInt(row.beforeValue) }}</template></el-table-column>
            <el-table-column label="变更后" width="90" align="right"><template #default="{row}">{{ fmtInt(row.afterValue) }}</template></el-table-column>
            <el-table-column prop="levelCode" label="等级" width="80"></el-table-column>
            <el-table-column prop="ruleVersion" label="规则版本" width="100"></el-table-column>
            <el-table-column prop="remark" label="说明" min-width="160"></el-table-column>
            <el-table-column prop="createdAt" label="时间" width="150"></el-table-column>
          </el-table>
          <el-pagination v-if="logs.total" background layout="prev, pager, next, total"
            :total="logs.total" :page-size="logsSize" :current-page="logsPage"
            style="margin-top:12px" @current-change="changeLogsPage"></el-pagination>
          <el-button size="small" style="margin-top:12px" @click="exportLogs">导出流水</el-button>
        </el-card>
      </el-tab-pane>

      <!-- ============ 权益配置 ============ -->
      <el-tab-pane label="权益配置" name="benefits">
        <el-card shadow="never">
          <template #header>
            <span>权益目录（积分兑换，成长值仅作门槛）</span>
            <el-button size="small" type="primary" style="float:right" @click="openBenefitEdit(null)">新增权益</el-button>
          </template>
          <el-table :data="benefits" size="small" border max-height="460">
            <el-table-column prop="benefitCode" label="编码" width="130"></el-table-column>
            <el-table-column prop="benefitName" label="名称" width="140"></el-table-column>
            <el-table-column label="类型" width="90"><template #default="{row}">{{ benefitTypeText(row.benefitType) }}</template></el-table-column>
            <el-table-column label="所需积分" width="90" align="right"><template #default="{row}">{{ fmtInt(row.costPoints) }}</template></el-table-column>
            <el-table-column prop="minLevelCode" label="等级门槛" width="90"></el-table-column>
            <el-table-column label="面额" width="90" align="right"><template #default="{row}">{{ row.voucherAmountFen ? yuan(row.voucherAmountFen) : '—' }}</template></el-table-column>
            <el-table-column label="库存" width="140">
              <template #default="{row}">{{ fmtInt(row.stock) }} / {{ fmtInt(row.totalStock) }}</template>
            </el-table-column>
            <el-table-column label="每日上限" width="90"><template #default="{row}">{{ row.dailyLimit || '不限' }}</template></el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{row}"><el-tag :type="row.status === 'ON_SHELF' ? 'success' : 'info'" size="small">{{ row.status === 'ON_SHELF' ? '上架' : '下架' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{row}">
                <el-button size="small" type="text" @click="openBenefitEdit(row)">编辑</el-button>
                <el-button size="small" type="text" @click="toggleBenefit(row)">{{ row.status === 'ON_SHELF' ? '下架' : '上架' }}</el-button>
                <el-button size="small" type="text" danger @click="delBenefit(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ============ 兑换记录 ============ -->
      <el-tab-pane label="兑换记录" name="exchanges">
        <el-card shadow="never">
          <el-form :inline="true" size="small" style="margin-bottom:12px">
            <el-form-item label="用户ID"><el-input v-model="exchangeUserId" placeholder="留空=全部" style="width:140px" @keyup.enter="loadExchanges"></el-input></el-form-item>
            <el-form-item><el-button type="primary" @click="loadExchanges">查询</el-button></el-form-item>
          </el-form>
          <el-table :data="exchanges.list" size="small" border max-height="460">
            <el-table-column prop="exchangeNo" label="兑换单号" width="160"></el-table-column>
            <el-table-column prop="benefitName" label="权益" width="130"></el-table-column>
            <el-table-column prop="userId" label="用户ID" width="90"></el-table-column>
            <el-table-column label="扣减积分" width="90" align="right"><template #default="{row}">{{ fmtInt(row.costPoints) }}</template></el-table-column>
            <el-table-column label="类型" width="90"><template #default="{row}">{{ benefitTypeText(row.benefitType) }}</template></el-table-column>
            <el-table-column label="状态" width="90" align="center"><template #default="{row}"><el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.status === 'SUCCESS' ? '成功' : '失败' }}</el-tag></template></el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140"></el-table-column>
            <el-table-column prop="createdAt" label="时间" width="150"></el-table-column>
          </el-table>
          <el-pagination v-if="exchanges.total" background layout="prev, pager, next, total"
            :total="exchanges.total" :page-size="exchangesSize" :current-page="exchangesPage"
            style="margin-top:12px" @current-change="changeExchangesPage"></el-pagination>
        </el-card>
      </el-tab-pane>

      <!-- ============ 衰减与维护 ============ -->
      <el-tab-pane label="衰减与维护" name="decay">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-card shadow="never" header="衰减任务">
              <div style="margin-bottom:12px">
                <el-button size="small" type="warning" :loading="decayRunning" @click="runDecay(false)">手动触发衰减扫描</el-button>
                <el-button size="small" type="info" :loading="decayRunning" @click="runDecay(true)">提前预警通知</el-button>
              </div>
              <el-descriptions v-if="decayResult" :column="2" border size="small" title="执行摘要">
                <el-descriptions-item label="扫描账户">{{ decayResult.scanned }}</el-descriptions-item>
                <el-descriptions-item label="发生衰减">{{ decayResult.decayed }}</el-descriptions-item>
                <el-descriptions-item label="触发降级">{{ decayResult.downgraded }}</el-descriptions-item>
                <el-descriptions-item label="通知数">{{ decayResult.notified }}</el-descriptions-item>
              </el-descriptions>
              <el-alert v-else type="info" :closable="false" title="点击上方按钮手动触发（补跑 / 演练）。定时任务已于 03:30 / 04:20 / 09:00 自动执行。" />
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never" header="存量初始化">
              <el-form label-width="120px" size="small">
                <el-form-item label="单次批量规模">
                  <el-input-number v-model="initLimit" :min="1" :max="10000" :step="100"></el-input-number>
                </el-form-item>
                <el-form-item>
                  <el-button size="small" type="primary" :loading="initRunning" @click="runInitStock">执行初始化</el-button>
                  <span style="margin-left:12px;color:var(--text-3)">为尚未建账的存量用户批量补建默认 L1 / 0 账户，可分页多次调用。</span>
                </el-form-item>
                <el-form-item v-if="initResult != null" label="本次初始化">
                  <el-tag type="success" size="small">{{ initResult }} 个</el-tag>
                </el-form-item>
              </el-form>
            </el-card>
          </el-col>
        </el-row>
        <el-card shadow="never" header="申诉回补（REQ-10）" style="margin-top:20px">
          <el-form :inline="true" size="small">
            <el-form-item label="用户ID"><el-input v-model="restoreUserId" placeholder="用户ID" style="width:140px"></el-input></el-form-item>
            <el-form-item label="工单号"><el-input v-model="restoreDisputeNo" placeholder="DP 开头" style="width:200px"></el-input></el-form-item>
            <el-form-item><el-button type="primary" :loading="restoreRunning" @click="runRestore">回补判责扣减</el-button></el-form-item>
            <el-form-item v-if="restoreResult != null"><el-tag :type="restoreResult > 0 ? 'success' : 'info'" size="small">{{ restoreResult > 0 ? ('回补 ' + restoreResult) : '无扣减/已回补' }}</el-tag></el-form-item>
          </el-form>
          <el-alert type="info" :closable="false" title="按工单号自动反查历史判责扣减量并回补，幂等；无判责扣减或已回补时返回 0。" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 冻结原因对话框 -->
    <el-dialog title="冻结发放通道" v-model="freezeVisible" width="420px">
      <el-form label-width="90px" size="small">
        <el-form-item label="用户ID"><span>{{ freezeUserId }}</span></el-form-item>
        <el-form-item label="冻结原因"><el-input v-model="freezeReason" type="textarea" rows="3" placeholder="如：命中刷分团伙 / 账号封禁"></el-input></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="freezeVisible=false">取消</el-button>
        <el-button size="small" type="primary" :loading="freezeSaving" @click="submitFreeze">确定冻结</el-button>
      </template>
    </el-dialog>

    <!-- 权益编辑对话框 -->
    <el-dialog :title="benefitEditing ? '编辑权益' : '新增权益'" v-model="benefitVisible" width="520px">
      <el-form label-width="110px" size="small">
        <el-form-item label="权益编码"><el-input v-model="benefitForm.benefitCode" :disabled="benefitEditing" placeholder="唯一编码，如 FREE_SHIP_1"></el-input></el-form-item>
        <el-form-item label="权益名称"><el-input v-model="benefitForm.benefitName" placeholder="如 免邮券"></el-input></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="benefitForm.benefitType" style="width:200px">
            <el-option v-for="(t, k) in BENEFIT_TYPE_TEXT" :key="k" :label="t" :value="k"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="benefitForm.description" type="textarea" rows="2"></el-input></el-form-item>
        <el-form-item label="所需积分"><el-input-number v-model="benefitForm.costPoints" :min="0" :step="50"></el-input-number></el-form-item>
        <el-form-item label="等级门槛"><el-input v-model="benefitForm.minLevelCode" placeholder="如 L2"></el-input></el-form-item>
        <el-form-item label="券模板ID"><el-input-number v-model="benefitForm.couponId" :min="0" :step="1"></el-input-number></el-form-item>
        <el-form-item label="面额(分)"><el-input-number v-model="benefitForm.voucherAmountFen" :min="0" :step="100"></el-input-number></el-form-item>
        <el-form-item label="剩余/总库存"><el-input-number v-model="benefitForm.stock" :min="0" :step="10"></el-input-number> / <el-input-number v-model="benefitForm.totalStock" :min="0" :step="10"></el-input-number></el-form-item>
        <el-form-item label="每日上限"><el-input-number v-model="benefitForm.dailyLimit" :min="0" :step="1"></el-input-number><span style="margin-left:8px;color:var(--text-3)">0=不限</span></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="benefitForm.status">
            <el-radio value="ON_SHELF">上架</el-radio>
            <el-radio value="OFF_SHELF">下架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="benefitForm.sortOrder" :min="0" :step="1"></el-input-number></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="benefitVisible=false">取消</el-button>
        <el-button size="small" type="primary" :loading="benefitSaving" @click="submitBenefit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { growthApi, toCsv, downloadCsv } from '@/api';
import { formatMixin, notifyError } from '@/utils/format';
import PageHeader from '@/components/PageHeader.vue';
// pc-admin/src/views/member/Growth.vue —— 成长值运营后台（ITER-F07 / PRD §六）
// 规则配置 / 账户与刷分冻结解冻 / 流水对账 / 权益目录配置 / 兑换记录 / 衰减手动触发 /
// 存量初始化 / 申诉回补。真实接口见 AdminGrowthController /api/admin/growth/*

const BIZ_TEXT = {
  EARN_TRADE: '交易获得', EARN_PUBLISH: '发布获得', EARN_REVIEW: '评价获得',
  EARN_INSPECTION: '验货获得', EARN_RECONCILE: '和解得', DEDUCT_DISPUTE: '判责扣减',
  DEDUCT_REVERSE: '退款冲正', DECAY: '不活跃衰减', RESTORE_DISPUTE: '申诉回补'
};
const BENEFIT_TYPE_TEXT = { COUPON: '券', VOUCHER: '金额券', POINT_PACKAGE: '积分包', SERVICE: '服务' };
const fmtInt = (n) => (n == null ? '0' : String(n));

export default {
  name: 'Growth',
  components: { PageHeader },
  mixins: [formatMixin],
  data() {
    return {
      loading: true,
      activeTab: 'config',
      config: null,

      // 账户
      accounts: { list: [], total: 0 },
      accountsPage: 1,
      accountsSize: 20,
      accountFrozenOnly: false,

      // 流水
      logs: { list: [], total: 0 },
      logsPage: 1,
      logsSize: 20,
      logUserId: null,
      logBizType: '',

      // 权益
      benefits: [],
      benefitVisible: false,
      benefitEditing: null,
      benefitSaving: false,
      benefitForm: this.emptyBenefit(),

      // 兑换
      exchanges: { list: [], total: 0 },
      exchangesPage: 1,
      exchangesSize: 20,
      exchangeUserId: '',

      // 衰减与维护
      decayRunning: false,
      decayResult: null,
      initLimit: 1000,
      initRunning: false,
      initResult: null,
      restoreUserId: '',
      restoreDisputeNo: '',
      restoreRunning: false,
      restoreResult: null,

      // 冻结
      freezeVisible: false,
      freezeUserId: null,
      freezeReason: '',
      freezeSaving: false,

      BIZ_TEXT,
      BENEFIT_TYPE_TEXT
    };
  },
  mounted() { this.loadAll(); },
  methods: {
    fmtInt,
    bizText(k) { return BIZ_TEXT[k] || k; },
    benefitTypeText(k) { return BENEFIT_TYPE_TEXT[k] || k; },
    emptyBenefit() {
      return {
        benefitCode: '', benefitName: '', benefitType: 'COUPON', description: '',
        costPoints: 0, minLevelCode: 'L1', couponId: null, voucherAmountFen: null,
        stock: 0, totalStock: 0, dailyLimit: 0, status: 'ON_SHELF', sortOrder: 0
      };
    },
    async loadAll() {
      this.loading = true;
      try {
        const [config, accounts] = await Promise.all([
          growthApi.config(),
          growthApi.accounts(this.accountsPage, this.accountsSize, this.accountFrozenOnly)
        ]);
        this.config = config || null;
        this.accounts = accounts && accounts.list ? accounts : { list: [], total: 0 };
        this.logUserId = this.accounts.list.length ? this.accounts.list[0].userId : null;
        await Promise.all([this.loadBenefits(), this.loadExchanges()]);
      } catch (e) {
        notifyError(this, e, '加载成长值运营数据失败');
      } finally {
        this.loading = false;
      }
    },
    onTabChange(name) {
      if (name === 'accounts') this.loadAccounts();
      else if (name === 'logs') this.loadLogs();
      else if (name === 'benefits') this.loadBenefits();
      else if (name === 'exchanges') this.loadExchanges();
    },
    // ===== 账户 =====
    async loadAccounts() {
      try {
        this.accounts = await growthApi.accounts(this.accountsPage, this.accountsSize, this.accountFrozenOnly);
        if (!this.logUserId && this.accounts.list.length) this.logUserId = this.accounts.list[0].userId;
      } catch (e) { notifyError(this, e, '加载账户失败'); }
    },
    async changeAccountsPage(p) { this.accountsPage = p; await this.loadAccounts(); },
    async openLogs(row) { this.logUserId = row.userId; this.logBizType = ''; this.logsPage = 1; this.activeTab = 'logs'; await this.loadLogs(); },
    openFreeze(row) { this.freezeUserId = row.userId; this.freezeReason = ''; this.freezeVisible = true; },
    async submitFreeze() {
      if (!this.freezeReason || !this.freezeReason.trim()) { this.$message.error('请填写冻结原因'); return; }
      this.freezeSaving = true;
      try {
        await growthApi.freeze(this.freezeUserId, this.freezeReason.trim());
        this.$message.success('已冻结');
        this.freezeVisible = false;
        await this.loadAccounts();
      } catch (e) { notifyError(this, e, '冻结失败'); }
      finally { this.freezeSaving = false; }
    },
    async unfreeze(row) {
      try {
        await this.$confirm('确认解冻用户 ' + row.userId + ' 的发放通道？', '提示', { type: 'warning' });
      } catch (e) { return; }
      try {
        await growthApi.unfreeze(row.userId);
        this.$message.success('已解冻');
        await this.loadAccounts();
      } catch (e) { notifyError(this, e, '解冻失败'); }
    },
    openRestore(row) { this.restoreUserId = String(row.userId); this.restoreDisputeNo = ''; this.restoreResult = null; this.activeTab = 'decay'; },
    exportAccounts() {
      const headers = ['用户ID', '等级', '成长值', '累计获得', '冻结', '最后活跃', '距衰减'];
      const rows = this.accounts.list.map(a => [a.userId, a.levelCode + a.levelName, a.growthValue, a.totalEarned, a.frozen ? '是' : '否', a.lastActiveAt, a.daysUntilDecay]);
      downloadCsv('growth-accounts.csv', toCsv(headers, rows));
    },
    // ===== 流水 =====
    async loadLogs() {
      if (!this.logUserId) return;
      try {
        this.logs = await growthApi.logs(this.logUserId, this.logBizType || undefined, this.logsPage, this.logsSize);
      } catch (e) { notifyError(this, e, '加载流水失败'); }
    },
    async changeLogsPage(p) { this.logsPage = p; await this.loadLogs(); },
    exportLogs() {
      const headers = ['ID', '来源', '关联单号', '增量', '变更前', '变更后', '等级', '规则版本', '说明', '时间'];
      const rows = this.logs.list.map(l => [l.id, this.bizText(l.bizType), l.bizId, l.delta, l.beforeValue, l.afterValue, l.levelCode, l.ruleVersion, l.remark, l.createdAt]);
      downloadCsv('growth-logs.csv', toCsv(headers, rows));
    },
    // ===== 权益 =====
    async loadBenefits() {
      try { this.benefits = await growthApi.benefits(); }
      catch (e) { notifyError(this, e, '加载权益失败'); }
    },
    openBenefitEdit(b) {
      if (b) {
        this.benefitEditing = b.id;
        this.benefitForm = Object.assign(this.emptyBenefit(), JSON.parse(JSON.stringify(b)));
      } else {
        this.benefitEditing = null;
        this.benefitForm = this.emptyBenefit();
      }
      this.benefitVisible = true;
    },
    async submitBenefit() {
      if (!this.benefitForm.benefitCode || !this.benefitForm.benefitName) { this.$message.error('编码与名称为必填'); return; }
      this.benefitSaving = true;
      try {
        await growthApi.saveBenefit(this.benefitForm);
        this.$message.success('保存成功');
        this.benefitVisible = false;
        await this.loadBenefits();
      } catch (e) { notifyError(this, e, '保存权益失败'); }
      finally { this.benefitSaving = false; }
    },
    async toggleBenefit(b) {
      const status = b.status === 'ON_SHELF' ? 'OFF_SHELF' : 'ON_SHELF';
      try {
        await growthApi.updateBenefitStatus(b.id, status);
        this.$message.success(status === 'ON_SHELF' ? '已上架' : '已下架');
        await this.loadBenefits();
      } catch (e) { notifyError(this, e, '切换状态失败'); }
    },
    async delBenefit(b) {
      try { await this.$confirm('确认删除权益「' + b.benefitName + '」？', '提示', { type: 'warning' }); }
      catch (e) { return; }
      try {
        await growthApi.deleteBenefit(b.id);
        this.$message.success('已删除');
        await this.loadBenefits();
      } catch (e) { notifyError(this, e, '删除失败'); }
    },
    // ===== 兑换 =====
    async loadExchanges() {
      const uid = this.exchangeUserId ? Number(this.exchangeUserId) : undefined;
      try {
        this.exchanges = await growthApi.exchanges(this.exchangesPage, this.exchangesSize, uid);
      } catch (e) { notifyError(this, e, '加载兑换记录失败'); }
    },
    async changeExchangesPage(p) { this.exchangesPage = p; await this.loadExchanges(); },
    // ===== 衰减与维护 =====
    async runDecay(notify) {
      this.decayRunning = true;
      try {
        this.decayResult = await growthApi.runDecay(notify);
        this.$message.success(notify ? '已发送预警通知' : '衰减扫描完成');
      } catch (e) { notifyError(this, e, '衰减任务失败'); }
      finally { this.decayRunning = false; }
    },
    async runInitStock() {
      this.initRunning = true; this.initResult = null;
      try {
        this.initResult = await growthApi.initStock(this.initLimit);
        this.$message.success('初始化完成');
        await this.loadAccounts();
      } catch (e) { notifyError(this, e, '初始化失败'); }
      finally { this.initRunning = false; }
    },
    async runRestore() {
      if (!this.restoreUserId || !this.restoreDisputeNo) { this.$message.error('请填写用户ID与工单号'); return; }
      this.restoreRunning = true; this.restoreResult = null;
      try {
        this.restoreResult = await growthApi.restoreByDispute(Number(this.restoreUserId), this.restoreDisputeNo.trim());
        this.$message.success('回补处理完成');
      } catch (e) { notifyError(this, e, '回补失败'); }
      finally { this.restoreRunning = false; }
    }
  }
};
</script>
