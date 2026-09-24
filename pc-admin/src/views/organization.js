// pc-admin/src/views/organization.js —— 系统管理：机构（树形管理）
import { systemApi } from '../api.js';

export default {
  name: 'Organization',
  data() {
    return {
      tree: [], loading: false, defaultProps: { children: 'children', label: 'name' },
      editVisible: false, isEdit: false, saving: false, form: {}, parentName: ''
    };
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try { this.tree = await systemApi.orgTree(); }
      finally { this.loading = false; }
    },
    openAdd(node) {
      this.isEdit = false;
      this.form = { name: '', parentId: node && node.id ? node.id : 0, leader: '', phone: '', status: 0 };
      this.parentName = node && node.name ? node.name : '顶级机构';
      this.editVisible = true;
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate());
    },
    openEdit(node) {
      this.isEdit = true;
      this.form = {
        id: node.id, name: node.name, parentId: node.parentId,
        leader: node.leader || '', phone: node.phone || '', status: node.status
      };
      this.parentName = node.parentId === 0 ? '顶级机构' : '上级机构';
      this.editVisible = true;
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate());
    },
    async save() {
      const ok = await this.$refs.form.validate().catch(() => false);
      if (!ok) return;
      this.saving = true;
      try {
        await systemApi.saveOrg(this.form);
        this.$message.success(this.isEdit ? '已保存' : '已新增机构');
        this.editVisible = false;
        this.load();
      } finally { this.saving = false; }
    },
    async remove(node) {
      if (node.children && node.children.length) { this.$message.warning('请先删除子机构'); return; }
      try { await this.$confirm('确认删除该机构？', '提示', { type: 'warning' }); }
      catch (e) { return; }
      await systemApi.deleteOrg(node.id);
      this.$message.success('已删除');
      this.load();
    }
  },
  computed: {
    rules() {
      return {
        name: [{ required: true, message: '请输入机构名称', trigger: 'blur' }],
        phone: [{ pattern: /^[\d\-+()\s]{0,20}$/, message: '电话格式不正确', trigger: 'blur' }]
      };
    }
  },
  template: `
  <div>
    <h2 class="page-title">系统管理 / 机构管理</h2>
    <el-card shadow="never" v-loading="loading">
      <div style="margin-bottom:12px">
        <el-button type="primary" @click="openAdd(null)">新增顶级机构</el-button>
      </div>
      <el-tree :data="tree" :props="defaultProps" default-expand-all>
        <template #default="{ node, data }">
          <span class="tree-node">
            <span>{{ node.label }} <el-tag v-if="data.status===1" size="small" type="info">停用</el-tag></span>
            <span class="tree-ops">
              <el-button size="small" link type="primary" @click.stop="openAdd(data)">新增子机构</el-button>
              <el-button size="small" link type="warning" @click.stop="openEdit(data)">编辑</el-button>
              <el-button size="small" link type="danger" @click.stop="remove(data)">删除</el-button>
            </span>
          </span>
        </template>
      </el-tree>
    </el-card>

    <el-dialog v-model="editVisible" :title="isEdit?'编辑机构':'新增机构'" width="460px">
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级机构"><el-input :value="parentName" disabled></el-input></el-form-item>
        <el-form-item label="机构名称" prop="name"><el-input v-model="form.name" placeholder="如：华东运营中心"></el-input></el-form-item>
        <el-form-item label="负责人" prop="leader"><el-input v-model="form.leader" placeholder="负责人姓名"></el-input></el-form-item>
        <el-form-item label="联系电话" prop="phone"><el-input v-model="form.phone" placeholder="机构联系电话"></el-input></el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="0">启用</el-radio>
            <el-radio :label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>`
};
