// pc-admin/src/views/notify.js —— 消息中心 / 站内信（F-PC-02，F-02/F-05 前端闭环）
import { notifyApi } from '../api.js';
import { formatMixin, notifyError } from '../utils/format.js';

export default {
  name: 'Notify',
  mixins: [formatMixin],
  data() {
    return {
      list: [], total: 0, loading: false, page: 1, size: 20
    };
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try {
        const r = await notifyApi.list(this.page, this.size);
        this.list = (r.list || []).map(n => ({ ...n, read: n.read ? 1 : 0 }));
        this.total = r.total || this.list.length;
        this.broadcastUnread();
      } catch (e) { this.$message.error('加载消息失败'); }
      finally { this.loading = false; }
    },
    broadcastUnread() {
      const unread = this.list.filter(n => !n.read).length;
      window.dispatchEvent(new CustomEvent('notify-unread', { detail: { unread } }));
    },
    typeTag(t) {
      return { order: 'success', risk: 'danger', finance: 'warning', system: 'info' }[t] || 'info';
    },
    typeText(t) { return { order: '订单', risk: '风控', finance: '财务', system: '系统' }[t] || '通知'; },
    async markRead(row) {
      if (row.read) return;
      try {
        await notifyApi.markRead(row.id);
        row.read = 1;
        this.broadcastUnread();
        this.$message.success('已标记已读');
      } catch (e) { this.$message.error('操作失败'); }
    },
    async markAll() {
      try {
        await notifyApi.markAllRead();
        this.list.forEach(n => n.read = 1);
        this.broadcastUnread();
        this.$message.success('已全部标记已读');
      } catch (e) { this.$message.error('操作失败'); }
    }
  },
  template: `
  <div>
    <h2 class="page-title">消息中心
      <el-button size="small" type="primary" plain style="margin-left:12px" @click="markAll">全部已读</el-button>
    </h2>
    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="类型" width="100">
          <template #default="{row}"><el-tag :type="typeTag(row.type)" size="small">{{ typeText(row.type) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="标题" prop="title" min-width="220"></el-table-column>
        <el-table-column label="内容" prop="content" min-width="320" show-overflow-tooltip></el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :type="row.read ? 'info' : 'danger'" size="small">{{ row.read ? '已读' : '未读' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" prop="createdAt" width="170"></el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{row}">
            <el-button size="small" :disabled="!!row.read" @click="markRead(row)">标记已读</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px;color:#909399">共 {{ total }} 条</div>
    </el-card>
  </div>`
};
