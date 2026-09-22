// pc-admin/src/views/categories.js —— 类目管理（树 + 新增）
import { adminApi } from '../api.js';

export default {
  name: 'Categories',
  data() {
    return { tree: [], defaultProps: { children: 'children', label: 'name' }, addVisible: false, form: { name: '', parentId: 0 } };
  },
  mounted() { this.load(); },
  methods: {
    async load() { this.tree = await adminApi.categories(); },
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
  },
  template: `
  <div>
    <h2 class="page-title">类目管理</h2>
    <el-card shadow="never">
      <div style="margin-bottom:12px">
        <el-button type="primary" @click="openAdd(null)">新增一级类目</el-button>
      </div>
      <el-tree :data="tree" :props="defaultProps" default-expand-all>
        <template #default="{ node, data }">
          <span class="tree-node">
            <span>{{ node.label }}</span>
            <span class="tree-ops">
              <el-button size="small" link type="primary" @click.stop="openAdd(data)">新增子类</el-button>
            </span>
          </span>
        </template>
      </el-tree>
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
  </div>`
};
