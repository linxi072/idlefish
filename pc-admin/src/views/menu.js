// pc-admin/src/views/menu.js —— 系统管理：菜单（树形管理）
import { systemApi } from '../api.js';
import { formatMixin, notifyError } from '../utils/format.js';

const TYPE_OPTS = [{ label: '目录', value: 0 }, { label: '菜单', value: 1 }, { label: '按钮', value: 2 }];

export default {
  name: 'Menu',
  mixins: [formatMixin],
  data() {
    return {
      tree: [], loading: false, defaultProps: { children: 'children', label: 'name' },
      editVisible: false, isEdit: false, saving: false, form: {}, parentName: '',
      typeOptions: TYPE_OPTS
    };
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try { this.tree = await systemApi.menuTree(); }
      catch (e) { notifyError(this, e, '加载失败'); }
      finally { this.loading = false; }
    },
    typeTag(t) {
      return [{ label: '目录', type: 'info' }, { label: '菜单', type: 'primary' }, { label: '按钮', type: 'warning' }][t] || { label: t, type: 'info' };
    },
    openAdd(node) {
      this.isEdit = false;
      this.form = { name: '', parentId: node && node.id ? node.id : 0, type: 1, path: '', component: '', perms: '', icon: '', sort: 1 };
      this.parentName = node && node.name ? node.name : '顶级目录';
      this.editVisible = true;
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate());
    },
    openEdit(node) {
      this.isEdit = true;
      this.form = {
        id: node.id, name: node.name, parentId: node.parentId, type: node.type,
        path: node.path || '', component: node.component || '', perms: node.perms || '',
        icon: node.icon || '', sort: node.sort
      };
      this.parentName = node.parentId === 0 ? '顶级目录' : '上级菜单';
      this.editVisible = true;
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate());
    },
    async save() {
      const ok = await this.$refs.form.validate().catch(() => false);
      if (!ok) return;
      this.saving = true;
      try {
        await systemApi.saveMenu(this.form);
        this.$message.success(this.isEdit ? '已保存' : '已新增菜单');
        this.editVisible = false;
        this.load();
      } catch (e) { notifyError(this, e, '保存失败'); }
      finally { this.saving = false; }
    },
    async remove(node) {
      if (node.children && node.children.length) { this.$message.warning('请先删除子菜单'); return; }
      try { await this.$confirm('确认删除该菜单？', '提示', { type: 'warning' }); }
      catch (e) { return; }
      await systemApi.deleteMenu(node.id);
      this.$message.success('已删除');
      this.load();
    }
  },
  computed: {
    rules() {
      return {
        name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
        type: [{ required: true, message: '请选择类型', trigger: 'change' }],
        sort: [{ required: true, type: 'number', message: '请输入排序', trigger: 'blur' }]
      };
    }
  },
  template: `
  <div>
    <h2 class="page-title">系统管理 / 菜单管理</h2>
    <el-card shadow="never" v-loading="loading">
      <div style="margin-bottom:12px">
        <el-button type="primary" @click="openAdd(null)">新增顶级菜单</el-button>
      </div>
      <el-tree :data="tree" :props="defaultProps" default-expand-all>
        <template #default="{ node, data }">
          <span class="tree-node">
            <span>{{ node.label }}
              <el-tag size="small" :type="typeTag(data.type).type">{{ typeTag(data.type).label }}</el-tag>
              <span class="g-sub" v-if="data.perms"> · {{ data.perms }}</span>
            </span>
            <span class="tree-ops">
              <el-button size="small" link type="primary" @click.stop="openAdd(data)">新增子项</el-button>
              <el-button size="small" link type="warning" @click.stop="openEdit(data)">编辑</el-button>
              <el-button size="small" link type="danger" @click.stop="remove(data)">删除</el-button>
            </span>
          </span>
        </template>
      </el-tree>
    </el-card>

    <el-dialog v-model="editVisible" :title="isEdit?'编辑菜单':'新增菜单'" width="480px">
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级菜单"><el-input :value="parentName" disabled></el-input></el-form-item>
        <el-form-item label="菜单名称" prop="name"><el-input v-model="form.name" placeholder="如：订单管理"></el-input></el-form-item>
        <el-form-item label="菜单类型" prop="type">
          <el-select v-model="form.type" style="width:100%">
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="路由地址" prop="path"><el-input v-model="form.path" placeholder="如：/system/user"></el-input></el-form-item>
        <el-form-item label="组件路径" prop="component"><el-input v-model="form.component" placeholder="如：sysuser"></el-input></el-form-item>
        <el-form-item label="权限标识" prop="perms"><el-input v-model="form.perms" placeholder="如：system:user:list"></el-input></el-form-item>
        <el-form-item label="图标" prop="icon"><el-input v-model="form.icon" placeholder="图标名"></el-input></el-form-item>
        <el-form-item label="排序" prop="sort"><el-input-number v-model="form.sort" :min="0" :max="999"></el-input-number></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>`
};
