<template>

  <div>
    <page-header title="系统管理 / 菜单管理" />
    <el-card shadow="never" v-loading="loading">
      <div class="filter-bar">
        <el-button type="primary" @click="openAdd(null)">新增顶级菜单</el-button>
        <span class="g-sub">层级关系以缩进与「层级」列呈现，共 {{ treeFlatCount }} 个菜单项</span>
      </div>
      <tree-flat-table :data="tree" :columns="columns">
        <template #actions="{ row }">
          <el-button size="small" link type="primary" @click="openAdd(row)">新增子项</el-button>
          <el-button size="small" link type="warning" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </tree-flat-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit?'编辑菜单':'新增菜单'" width="480px">
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
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { systemApi } from '@/api';
import { formatMixin, notifyError } from '@/utils/format';
import { listPageMixin, crudDialogMixin } from '@/mixins';
// pc-admin/src/views/menu.js —— 系统管理：菜单（树状结构扁平为表格，保留层级与全部字段）

const TYPE_OPTS = [{ label: '目录', value: 0 }, { label: '菜单', value: 1 }, { label: '按钮', value: 2 }];
const TYPE_MAP = [{ label: '目录', type: 'info' }, { label: '菜单', type: 'primary' }, { label: '按钮', type: 'warning' }];


export default {
  name: 'Menu',
  mixins: [formatMixin, listPageMixin, crudDialogMixin],
  data() {
    // tree 为树结构；loading 由 listPageMixin 提供；dialogVisible/submitting/form 由 crudDialogMixin 提供
    return {
      tree: [],
      isEdit: false, parentName: '',
      typeOptions: TYPE_OPTS
    };
  },
  computed: {
    // 扁平表格列定义：ID / 名称（缩进）/ 类型 / 路由 / 组件 / 权限标识 / 排序 / 父级ID / 层级
    columns() {
      return [
        { prop: 'id', label: 'ID', width: 70, align: 'right' },
        { prop: 'name', label: '菜单名称', minWidth: 200 },
        { prop: 'type', label: '类型', width: 90, align: 'center',
          tag: (v) => TYPE_MAP[v] || { label: v, type: 'info' } },
        { prop: 'path', label: '路由地址', minWidth: 150, format: (v) => v || '—' },
        { prop: 'component', label: '组件', minWidth: 110, format: (v) => v || '—' },
        { prop: 'perms', label: '权限标识', minWidth: 160, format: (v) => v || '—' },
        { prop: 'sort', label: '排序', width: 80, align: 'right' },
        { prop: 'parentId', label: '父级ID', width: 90, align: 'right',
          format: (v) => (v === 0 || v == null ? '顶级' : v) },
        { prop: '_depth', label: '层级', width: 80, align: 'center' }
      ];
    },
    treeFlatCount() {
      let n = 0;
      const walk = (l) => (l || []).forEach(x => { n++; if (x.children) walk(x.children); });
      walk(this.tree);
      return n;
    }
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try { this.tree = await systemApi.menuTree(); }
      catch (e) { notifyError(this, e, '加载失败'); }
      finally { this.loading = false; }
    },
    openAdd(node) {
      this.isEdit = false;
      this.form = { name: '', parentId: node && node.id ? node.id : 0, type: 1, path: '', component: '', perms: '', icon: '', sort: 1 };
      this.parentName = node && node.name ? node.name : '顶级目录';
      this.dialogVisible = true;
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
      this.dialogVisible = true;
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate());
    },
    async save() {
      const ok = await this.$refs.form.validate().catch(() => false);
      if (!ok) return;
      this.submitting = true;
      try {
        await systemApi.saveMenu(this.form);
        this.$message.success(this.isEdit ? '已保存' : '已新增菜单');
        this.dialogVisible = false;
        this.load();
      } catch (e) { notifyError(this, e, '保存失败'); }
      finally { this.submitting = false; }
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
  }
}
</script>
