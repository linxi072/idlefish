// pc-admin/src/views/users.js —— 用户管理（封禁 / 解封）
import { adminApi } from '../api.js';

export default {
  name: 'Users',
  data() {
    return { list: [], total: 0, loading: false, statusFilter: '' };
  },
  computed: {
    filtered() { return this.list.filter(u => !this.statusFilter || String(u.status) === this.statusFilter); }
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try { const r = await adminApi.users(); this.list = r.list; this.total = r.total; }
      finally { this.loading = false; }
    },
    masked(phone) { return phone || '-'; },
    async toggleBan(row) {
      const ban = row.status === 0;
      await adminApi.banUser(row.id, ban);
      row.status = ban ? 1 : 0;
      this.$message.success(ban ? '已封禁该用户' : '已解封');
    }
  },
  template: `
  <div>
    <h2 class="page-title">用户管理</h2>
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width:140px">
            <el-option label="正常" value="0"></el-option>
            <el-option label="已封禁" value="1"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
      </el-form>
      <el-table :data="filtered" v-loading="loading" border stripe>
        <el-table-column label="ID" prop="id" width="90"></el-table-column>
        <el-table-column label="昵称" prop="nickname" width="140"></el-table-column>
        <el-table-column label="手机号" width="150"><template #default="{row}">{{ masked(row.phone) }}</template></el-table-column>
        <el-table-column label="信用分" prop="creditScore" width="100">
          <template #default="{row}"><el-tag :type="row.creditScore>=80?'success':row.creditScore>=60?'warning':'danger'">{{ row.creditScore }}</el-tag></template>
        </el-table-column>
        <el-table-column label="注册时间" prop="createdAt" width="140"></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status===0?'success':'danger'">{{ row.status===0?'正常':'已封禁' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{row}">
            <el-button v-if="row.status===0" size="small" type="danger" @click="toggleBan(row)">封禁</el-button>
            <el-button v-else size="small" type="success" @click="toggleBan(row)">解封</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>`
};
