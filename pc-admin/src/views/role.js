// pc-admin/src/views/role.js —— 系统管理：角色（列表 + 增改 + 分配菜单树）
import { systemApi } from '../api.js';
import { formatMixin, notifyError } from '../utils/format.js';

export default {
  name: 'Role',
  mixins: [formatMixin],
  data() {
    return {
      list: [], total: 0, loading: false, keyword: '',
      editVisible: false, isEdit: false, saving: false, form: {},
      menuVisible: false, menuTarget: null, menuTreeData: [], menuProps: { children: 'children', label: 'name' }
    };
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try {
        const r = await systemApi.roles({ keyword: this.keyword });
        this.list = r.list || [];
        this.total = r.total;
      } catch (e) { notifyError(this, e, '加载失败'); }
      finally { this.loading = false; }
    },
    openAdd() {
      this.isEdit = false;
      this.form = { name: '', code: '', remark: '' };
      this.editVisible = true;
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate());
    },
    openEdit(row) {
      this.isEdit = true;
      this.form = { id: row.id, name: row.name, code: row.code, remark: row.remark };
      this.editVisible = true;
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate());
    },
    async save() {
      const ok = await this.$refs.form.validate().catch(() => false);
      if (!ok) return;
      this.saving = true;
      try {
        await systemApi.saveRole(this.form);
        this.$message.success(this.isEdit ? '已保存' : '已新增角色');
        this.editVisible = false;
        this.load();
      } catch (e) { notifyError(this, e, '保存失败'); }
      finally { this.saving = false; }
    },
    async remove(row) {
      if (row.id === 1) { this.$message.warning('超级管理员角色不可删除'); return; }
      try { await this.$confirm('确认删除该角色？', '提示', { type: 'warning' }); }
      catch (e) { return; }
      await systemApi.deleteRole(row.id);
      this.$message.success('已删除');
      this.load();
    },
    async openMenu(row) {
      this.menuTarget = row;
      if (!this.menuTreeData.length) this.menuTreeData = await systemApi.menuTree();
      this.menuChecked = (await systemApi.roleMenus(row.id)) || [];
      this.menuVisible = true;
    },
    async doAssignMenu() {
      await systemApi.assignMenus(this.menuTarget.id, this.$refs.menuTree.getCheckedKeys());
      this.$message.success('菜单权限已更新');
      this.menuVisible = false;
      this.load();
    }
  },
  computed: {
    rules() {
      return {
        name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
        code: [
          { required: true, message: '请输入角色编码', trigger: 'blur' },
          { pattern: /^[A-Z_]+$/, message: '大写字母 + 下划线', trigger: 'blur' }
        ]
      };
    }
  },
  template: `
  <div>
    <h2 class="page-title">系统管理 / 角色管理</h2>
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="关键词"><el-input v-model="keyword" placeholder="角色名/编码" clearable></el-input></el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
        <el-form-item><el-button type="success" @click="openAdd">新增角色</el-button></el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="ID" prop="id" width="80"></el-table-column>
        <el-table-column label="角色名称" prop="name" width="160"></el-table-column>
        <el-table-column label="角色编码" prop="code" width="160"></el-table-column>
        <el-table-column label="备注" prop="remark" min-width="200"></el-table-column>
        <el-table-column label="创建时间" prop="createdAt" width="140"></el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{row}">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="primary" @click="openMenu(row)">分配菜单</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px">共 {{ total }} 条</div>
    </el-card>

    <el-dialog v-model="editVisible" :title="isEdit?'编辑角色':'新增角色'" width="440px">
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色名称" prop="name"><el-input v-model="form.name" placeholder="如：运营"></el-input></el-form-item>
        <el-form-item label="角色编码" prop="code"><el-input v-model="form.code" placeholder="如：OPERATOR"></el-input></el-form-item>
        <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" type="textarea" :rows="2"></el-input></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="menuVisible" title="分配菜单权限" width="520px">
      <el-tree v-if="menuVisible" ref="menuTree" :data="menuTreeData" :props="menuProps"
        show-checkbox node-key="id" :default-checked-keys="menuChecked" default-expand-all></el-tree>
      <template #footer>
        <el-button @click="menuVisible=false">取消</el-button>
        <el-button type="primary" @click="doAssignMenu">确定</el-button>
      </template>
    </el-dialog>
  </div>`
};
