<template>

  <div>
    <page-header title="邀请拉新" />

    <el-card shadow="never" style="margin-bottom:16px">
      <el-form inline>
        <el-form-item label="查询用户 ID">
          <el-input v-model="userId" placeholder="买家用户 ID" style="width:160px" @keyup.enter.native="load"></el-input>
        </el-form-item>
        <el-form-item><el-button type="primary" :loading="codeLoading" @click="load">刷新</el-button></el-form-item>
      </el-form>

      <el-divider content-position="left">我的邀请码</el-divider>
      <div class="invite-code-box">
        <span class="invite-code">{{ myCode || '—' }}</span>
        <el-button size="small" :disabled="!myCode" @click="copyCode">复制</el-button>
      </div>

      <el-divider content-position="left">绑定邀请码</el-divider>
      <el-form inline>
        <el-form-item label="邀请码">
          <el-input v-model="bindInput" placeholder="输入他人邀请码" style="width:220px" clearable></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="success" :loading="bindLoading" @click="doBind">绑定</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-divider content-position="left">我的邀请列表（共 {{ total }} 人）</el-divider>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="被邀请人 ID" prop="inviteeId" width="140"></el-table-column>
        <el-table-column label="奖励券模板" width="140">
          <template #default="{row}">{{ row.rewardCouponId || '-' }}</template>
        </el-table-column>
        <el-table-column label="奖励状态" width="120">
          <template #default="{row}"><el-tag :type="rewardTag(row)">{{ rewardText(row) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="绑定时间" prop="createdAt" min-width="180"></el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { inviteApi } from '@/api';
import { formatMixin, notifyError } from '@/utils/format';
// pc-admin/src/views/invite.js —— 邀请拉新（F-13.4，对齐 InviteController /api/invite/*）
// 个人邀请页：获取/生成我的邀请码、绑定他人邀请码、查看我的邀请列表。
// 后端按 userId 维度隔离（买家侧能力），此处 demo 默认 2001 并支持切换查询用户。


export default {
  name: 'Invite',
  mixins: [formatMixin],
  data() {
    return {
      // 运营/买家维度用户 ID（demo 默认 2001，可切换查询）
      userId: 2001,
      // 我的邀请码
      myCode: '',
      codeLoading: false,
      // 绑定邀请码
      bindInput: '',
      bindLoading: false,
      // 我的邀请列表
      list: [], total: 0, loading: false
    };
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      await Promise.all([this.fetchCode(), this.fetchInvitees()]);
    },
    async fetchCode() {
      this.codeLoading = true;
      try {
        const r = await inviteApi.myCode(this.userId);
        // 兼容两种返回形态：mock 为 {code}，真实后端 Result<String> 经 req 解包为字符串
        this.myCode = (typeof r === 'string' ? r : (r && r.code)) || '';
      } catch (e) {
        notifyError(this, e, '获取邀请码失败');
      } finally { this.codeLoading = false; }
    },
    async fetchInvitees() {
      this.loading = true;
      try {
        const r = await inviteApi.invitees(this.userId);
        this.list = (r.list || []).map(x => ({
          ...x,
          // rewarded 归一为可读标签态：0 未奖励 / 1 已奖励
          rewardedText: x.rewarded === 1 ? '已奖励' : '未奖励'
        }));
        this.total = r.total;
      } catch (e) {
        notifyError(this, e, '加载邀请列表失败');
      } finally { this.loading = false; }
    },
    rewardTag(r) {
      return r.rewarded === 1 ? 'success' : 'info';
    },
    rewardText(r) { return r.rewarded === 1 ? '已奖励' : '未奖励'; },
    copyCode() {
      if (!this.myCode) return;
      const done = () => this.$message.success('已复制邀请码：' + this.myCode);
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(this.myCode).then(done).catch(() => done());
      } else {
        done();
      }
    },
    async doBind() {
      const code = (this.bindInput || '').trim();
      if (!code) { this.$message.warning('请输入邀请码'); return; }
      this.bindLoading = true;
      try {
        await inviteApi.bind(this.userId, code);
        this.$message.success('绑定成功');
        this.bindInput = '';
        this.fetchInvitees();
      } catch (e) {
        notifyError(this, e, '绑定失败');
      } finally { this.bindLoading = false; }
    }
  }
}
</script>
