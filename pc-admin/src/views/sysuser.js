// pc-admin/src/views/sysuser.js —— 系统管理：管理员（列表 + 增改 + 分配角色 + 重置密码）
import { systemApi } from '../api.js';
import { formatMixin, notifyError } from '../utils/format.js';

const flattenOrg = (tree, arr = []) => {
  (tree || []).forEach(o => {
    arr.push({ id: o.id, name: (o.parentId ? '├ ' : '') + o.name });
    if (o.children) flattenOrg(o.children, arr);
  });
  return arr;
};

export default {
  name: 'SysUser',
  mixins: [formatMixin],
  data() {
    return {
      list: [], total: 0, loading: false, keyword: '', statusFilter: '',
      editVisible: false, isEdit: false, saving: false, form: {},
      orgOptions: [], roleOptions: [],
      roleVisible: false, roleTarget: null, roleChecked: [],
      resetVisible: false, resetTarget: null
    };
  },
  mounted() { this.load(); this.loadMeta(); },
  methods: {
    async loadMeta() {
      const [org, roles] = await Promise.all([systemApi.orgTree(), systemApi.roles({})]);
      this.orgOptions = flattenOrg(org || []);
      this.roleOptions = ((roles && roles.list) || roles || []).map(r => ({ id: r.id, name: r.name }));
    },
    async load() {
      this.loading = true;
      try {
        const r = await systemApi.adminUsers({ keyword: this.keyword, status: this.statusFilter, page: 1, size: 20 });
        this.list = r.list || [];
        this.total = r.total;
      } catch (e) { notifyError(this, e); } finally { this.loading = false; }
    },
    openAdd() {
      this.isEdit = false;
      this.form = { username: '', nickname: '', orgId: '', password: '', status: 0, roleIds: [] };
      this.editVisible = true;
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate());
    },
    openEdit(row) {
      this.isEdit = true;
      this.form = {
        id: row.id, username: row.username, nickname: row.nickname,
        orgId: row.orgId, status: row.status, roleIds: (row.roleIds || []).slice(), password: ''
      };
      this.editVisible = true;
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate());
    },
    async save() {
      const ok = await this.$refs.form.validate().catch(() => false);
      if (!ok) return;
      this.saving = true;
      try {
        await systemApi.saveAdminUser(this.form);
        this.$message.success(this.isEdit ? '已保存' : '已新增管理员');
        this.editVisible = false;
        this.load();
      } catch (e) { notifyError(this, e); } finally { this.saving = false; }
    },
    async remove(row) {
      if (row.id === 1) { this.$message.warning('超级管理员不可删除'); return; }
      try { await this.$confirm('确认删除该管理员？', '提示', { type: 'warning' }); }
      catch (e) { return; }
      await systemApi.deleteAdminUser(row.id);
      this.$message.success('已删除');
      this.load();
    },
    async openAssign(row) {
      this.roleTarget = row;
      this.roleChecked = (await systemApi.userRoles(row.id)) || [];
      this.roleVisible = true;
    },
    async doAssign() {
      await systemApi.assignRoles(this.roleTarget.id, this.roleChecked);
      this.$message.success('角色已更新');
      this.roleVisible = false;
      this.load();
    },
    openReset(row) { this.resetTarget = row; this.resetVisible = true; },
    async doReset() {
      await systemApi.resetPassword(this.resetTarget.id);
      this.$message.success('密码已重置为系统默认口令');
      this.resetVisible = false;
    }
  },
  computed: {
    rules() {
      const r = {
        username: [
          { required: true, message: '请输入用户名', trigger: 'blur' },
          { min: 3, max: 20, message: '长度 3-20 位', trigger: 'blur' }
        ],
        nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
        orgId: [{ required: true, message: '请选择机构', trigger: 'change' }],
        roleIds: [{ required: true, type: 'array', message: '至少选择一个角色', trigger: 'change' }]
      };
      if (!this.isEdit) {
        r.password = [
          { required: true, message: '请输入初始密码', trigger: 'blur' },
          { min: 6, max: 20, message: '长度 6-20 位', trigger: 'blur' }
        ];
      }
      return r;
    }
  },
  template: `
  <div>
    <h2 class="page-title">系统管理 / 管理员</h2>
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="关键词"><el-input v-model="keyword" placeholder="用户名/昵称" clearable></el-input></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width:140px">
            <el-option label="正常" value="0"></el-option>
            <el-option label="禁用" value="1"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
        <el-form-item><el-button type="success" @click="openAdd">新增管理员</el-button></el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="ID" prop="id" width="80"></el-table-column>
        <el-table-column label="用户名" prop="username" width="140"></el-table-column>
        <el-table-column label="昵称" prop="nickname" width="140"></el-table-column>
        <el-table-column label="所属机构" prop="orgName" width="160"></el-table-column>
        <el-table-column label="角色" min-width="170">
          <template #default="{row}">
            <el-tag v-for="r in (row.roleNames||[])" :key="r" size="small" style="margin-right:6px">{{ r }}</el-tag>
            <span v-if="!(row.roleNames||[]).length" class="g-sub">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}"><el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createdAt" width="140"></el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{row}">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="primary" @click="openAssign(row)">分配角色</el-button>
            <el-button size="small" @click="openReset(row)">重置密码</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px">共 {{ total }} 条</div>
    </el-card>

    <el-dialog v-model="editVisible" :title="isEdit?'编辑管理员':'新增管理员'" width="480px">
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" placeholder="登录用户名"></el-input>
        </el-form-item>
        <el-form-item label="昵称" prop="nickname"><el-input v-model="form.nickname" placeholder="显示昵称"></el-input></el-form-item>
        <el-form-item label="所属机构" prop="orgId">
          <el-select v-model="form.orgId" placeholder="请选择机构" style="width:100%">
            <el-option v-for="o in orgOptions" :key="o.id" :label="o.name" :value="o.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password :placeholder="isEdit?'留空则不修改':'请输入初始密码'"></el-input>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="0">正常</el-radio>
            <el-radio :label="1">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple placeholder="请选择角色" style="width:100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.name" :value="r.id"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" title="分配角色" width="420px">
      <el-checkbox-group v-model="roleChecked">
        <el-checkbox v-for="r in roleOptions" :key="r.id" :label="r.id" border style="margin:6px">{{ r.name }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleVisible=false">取消</el-button>
        <el-button type="primary" @click="doAssign">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetVisible" title="重置密码" width="380px">
      <p>确认将 <b>{{ resetTarget && resetTarget.username }}</b> 的登录密码重置为系统默认口令？该账号下次登录将被强制修改。</p>
      <template #footer>
        <el-button @click="resetVisible=false">取消</el-button>
        <el-button type="warning" @click="doReset">确认重置</el-button>
      </template>
    </el-dialog>
  </div>`
};
