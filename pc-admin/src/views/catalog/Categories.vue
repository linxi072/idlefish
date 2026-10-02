<template>

  <div>
    <page-header title="类目管理" />
    <el-card shadow="never">
      <div class="filter-bar">
        <el-button type="primary" @click="openAdd(null)">新增一级类目</el-button>
        <span class="g-sub">共 {{ treeFlatCount }} 个类目（含子类），层级关系以缩进与「层级」列呈现</span>
      </div>
      <tree-flat-table :data="tree" :columns="columns" :loading="loading">
        <template #actions="{ row }">
          <el-button size="small" link type="primary" @click="openAdd(row)">新增子类</el-button>
        </template>
      </tree-flat-table>
    </el-card>

    <el-dialog v-model="addVisible" title="新增类目" width="420px">
      <el-form label-width="90px">
        <el-form-item label="上级类目">
          <el-input :value="form.parentId===0?'一级类目':form.parentId" disabled></el-input>
        </el-form-item>
        <el-form-item label="类目名称"><el-input v-model="form.name" placeholder="如：数码配件"></el-input></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { adminApi } from '@/api';
import { formatMixin, notifyError } from '@/utils/format';
// pc-admin/src/views/categories.js —— 类目管理（树状结构扁平为表格，保留层级与全部字段）

export default {
  name: 'Categories',
  mixins: [formatMixin],
  data() {
    return {
      tree: [], addVisible: false, form: { name: '', parentId: 0 }, loading: false
    };
  },
  computed: {
    // 扁平表格列定义：ID / 名称（缩进）/ 父级ID / 直属子类 / 层级
    columns() {
      return [
        { prop: 'id', label: 'ID', width: 70, align: 'right' },
        { prop: 'name', label: '类目名称', minWidth: 240 },
        { prop: 'parentId', label: '父级ID', width: 90, align: 'right',
          format: (v) => (v === 0 || v == null ? '顶级' : v) },
        { prop: '_childCount', label: '直属子类', width: 100, align: 'right' },
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
      try { this.tree = await adminApi.categories(); }
      catch (e) { notifyError(this, e, '加载失败'); }
      finally { this.loading = false; }
    },
    openAdd(node) {
      this.form = { name: '', parentId: node && node.id ? node.id : 0 };
      this.addVisible = true;
    },
    async save() {
      if (!this.form.name.trim()) return this.$message.warning('请输入类目名称');
      await adminApi.saveCategory(this.form);
      this.$message.success('已添加类目（演示）');
      this.addVisible = false;
      this.load();
    }
  }
}
</script>
