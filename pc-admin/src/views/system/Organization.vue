<template>

  <div>
    <page-header title="系统管理 / 机构管理" />
    <el-card shadow="never" v-loading="loading">
      <div class="filter-bar">
        <el-button type="primary" @click="openAdd(null)">新增顶级机构</el-button>
        <span class="g-sub">层级关系以缩进与「层级」列呈现，共 {{ treeFlatCount }} 个机构</span>
      </div>
      <tree-flat-table :data="tree" :columns="columns">
        <template #actions="{ row }">
          <el-button size="small" link type="primary" @click="openAdd(row)">新增子机构</el-button>
          <el-button size="small" link type="warning" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </tree-flat-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit?'编辑机构':'新增机构'" width="460px">
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
// pc-admin/src/views/organization.js —— 系统管理：机构（树状结构扁平为表格，保留层级与全部字段）


export default {
  name: 'Organization',
  mixins: [formatMixin, listPageMixin, crudDialogMixin],
  data() {
    // tree 为树结构；loading 由 listPageMixin 提供；dialogVisible/submitting/form 由 crudDialogMixin 提供
    return {
      tree: [],
      isEdit: false, parentName: ''
    };
  },
  computed: {
    // 扁平表格列定义：ID / 名称（缩进）/ 父级ID / 负责人 / 电话 / 状态（标签）/ 层级
    columns() {
      return [
        { prop: 'id', label: 'ID', width: 70, align: 'right' },
        { prop: 'name', label: '机构名称', minWidth: 200 },
        { prop: 'parentId', label: '父级ID', width: 90, align: 'right',
          format: (v) => (v === 0 || v == null ? '顶级' : v) },
        { prop: 'leader', label: '负责人', width: 110 },
        { prop: 'phone', label: '联系电话', width: 150 },
        { prop: 'status', label: '状态', width: 90, align: 'center',
          tag: (v) => ({ text: v === 0 ? '启用' : '停用', type: v === 0 ? 'success' : 'info' }) },
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
      try { this.tree = await systemApi.orgTree(); }
      catch (e) { notifyError(this, e, '加载失败'); }
      finally { this.loading = false; }
    },
    openAdd(node) {
      this.isEdit = false;
      this.form = { name: '', parentId: node && node.id ? node.id : 0, leader: '', phone: '', status: 0 };
      this.parentName = node && node.name ? node.name : '顶级机构';
      this.dialogVisible = true;
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate());
    },
    openEdit(node) {
      this.isEdit = true;
      this.form = {
        id: node.id, name: node.name, parentId: node.parentId,
        leader: node.leader || '', phone: node.phone || '', status: node.status
      };
      this.parentName = node.parentId === 0 ? '顶级机构' : '上级机构';
      this.dialogVisible = true;
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate());
    },
    async save() {
      const ok = await this.$refs.form.validate().catch(() => false);
      if (!ok) return;
      this.submitting = true;
      try {
        await systemApi.saveOrg(this.form);
        this.$message.success(this.isEdit ? '已保存' : '已新增机构');
        this.dialogVisible = false;
        this.load();
      } catch (e) { notifyError(this, e, '保存失败'); }
      finally { this.submitting = false; }
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
  }
}
</script>
