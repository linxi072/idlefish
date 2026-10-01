<template>

  <div>
    <page-header title="用户管理" />
    <el-card shadow="never">
      <el-form inline class="filter-bar">
        <el-form-item label="关键词"><el-input v-model="keyword" placeholder="昵称/手机号" clearable style="width:200px"></el-input></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width:140px">
            <el-option label="正常" value="0"></el-option>
            <el-option label="已封禁" value="1"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="filtered" v-loading="loading" border stripe :max-height="560">
        <el-table-column label="ID" prop="id" width="90"></el-table-column>
        <el-table-column label="昵称" prop="nickname" width="140"></el-table-column>
        <el-table-column label="手机号" width="150"><template #default="{row}">{{ masked(row.phone) }}</template></el-table-column>
        <el-table-column label="信用分" prop="creditScore" width="100" align="right">
          <template #default="{row}"><el-tag :type="row.creditScore>=80?'success':row.creditScore>=60?'warning':'danger'">{{ row.creditScore }}</el-tag></template>
        </el-table-column>
        <el-table-column label="注册时间" prop="createdAt" width="140"></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status===0?'success':'danger'">{{ row.status===0?'正常':'已封禁' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{row}">
            <el-button v-if="row.status===0" size="small" type="danger" plain @click="toggleBan(row)">封禁</el-button>
            <el-button v-else size="small" type="success" plain @click="toggleBan(row)">解封</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="page-bar" layout="total, sizes, prev, pager, next, jumper"
        :total="total" :page-size="size" :current-page="page" :page-sizes="[10,20,50,100]"
        @current-change="p=>{page=p;load()}" @size-change="s=>{size=s;page=1;load()}">
      </el-pagination>
    </el-card>
  </div>
</template>

<script>
import { adminApi } from '@/api';
import { formatMixin, notifyError } from '@/utils/format';
// pc-admin/src/views/users.js —— 用户管理（封禁 / 解封）


export default {
  name: 'Users',
  mixins: [formatMixin],
  data() {
    return { list: [], total: 0, loading: false, keyword: '', statusFilter: '', page: 1, size: 20 };
  },
  computed: {
    filtered() { return this.list; }
  },
  mounted() { this.load(); },
  methods: {
    reset() { this.keyword = ''; this.statusFilter = ''; this.page = 1; this.load(); },
    async load() {
      this.loading = true;
      try {
        const r = await adminApi.users({ keyword: this.keyword, status: this.statusFilter, page: this.page, size: this.size });
        this.list = r.list || [];
        this.total = r.total;
      } catch (e) { notifyError(this, e, '加载失败'); }
      finally { this.loading = false; }
    },
    masked(phone) { return phone || '-'; },
    async toggleBan(row) {
      const ban = row.status === 0;
      try {
        await this.$confirm(`确认${ban ? '封禁' : '解封'}用户「${row.nickname || row.id}」？`, '操作确认', { type: ban ? 'warning' : 'info' });
      } catch (e) { return; }
      try {
        await adminApi.banUser(row.id, ban);
        row.status = ban ? 1 : 0;
        this.$message.success(ban ? '已封禁该用户' : '已解封');
      } catch (e) { notifyError(this, e, '操作失败'); }
    }
  }
}
</script>
