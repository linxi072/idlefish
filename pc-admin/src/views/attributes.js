// pc-admin/src/views/attributes.js —— 类目属性模板（F-PC-02）
import { adminApi, attributeApi } from '../api.js';

export default {
  name: 'Attributes',
  data() {
    return {
      categoryOptions: [],           // 类目级联树
      categoryPath: [],              // 选中的类目路径（末级为叶子）
      leafCategoryId: null,
      list: [], loading: false,
      dialogVisible: false,
      form: { name: '', optionsText: '', required: false, sort: 0 },
      rules: {
        name: [{ required: true, message: '请输入属性名称', trigger: 'blur' }],
        sort: [{ required: true, message: '请输入排序', trigger: 'blur' },
               { type: 'number', message: '排序须为数字', trigger: 'blur' }]
      }
    };
  },
  mounted() { this.loadCategories(); },
  methods: {
    async loadCategories() {
      try {
        const tree = await adminApi.categories();
        this.categoryOptions = this.toOptions(tree);
      } catch (e) { this.$message.error('加载类目失败'); }
    },
    // 树 → el-cascader 选项（仅末级可选：有 children 的不作为叶子入口，但 cascader 允许逐级选）
    toOptions(nodes) {
      return (nodes || []).map(n => ({
        value: n.id, label: n.name,
        children: n.children && n.children.length ? this.toOptions(n.children) : undefined
      }));
    },
    onCategoryChange(path) {
      this.categoryPath = path || [];
      this.leafCategoryId = path && path.length ? path[path.length - 1] : null;
      if (this.leafCategoryId) this.loadTemplates();
      else this.list = [];
    },
    async loadTemplates() {
      if (!this.leafCategoryId) return;
      this.loading = true;
      try { this.list = await attributeApi.attrTemplates(this.leafCategoryId); }
      catch (e) { this.$message.error('加载属性模板失败'); this.list = []; }
      finally { this.loading = false; }
    },
    // options JSON 字符串 → 逗号文本（用于展示）
    optionsText(options) {
      if (!options) return '-';
      try {
        const arr = JSON.parse(options);
        return Array.isArray(arr) ? arr.join('、') : options;
      } catch { return options; }
    },
    reqText(r) { return r ? '必填' : '选填'; },
    openCreate() {
      if (!this.leafCategoryId) { this.$message.warning('请先选择叶子类目'); return; }
      this.form = { name: '', optionsText: '', required: false, sort: 0 };
      this.dialogVisible = true;
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate());
    },
    async save() {
      const ok = await this.$refs.form.validate().catch(() => false);
      if (!ok) return;
      // 选项：逗号分隔 → JSON 数组字符串
      const arr = this.form.optionsText.split(/[,，]/).map(s => s.trim()).filter(Boolean);
      const payload = {
        name: this.form.name,
        options: JSON.stringify(arr),
        required: this.form.required,
        sort: Number(this.form.sort) || 0
      };
      try {
        await attributeApi.saveAttrTemplate(this.leafCategoryId, payload);
        this.$message.success('已新增属性模板');
        this.dialogVisible = false;
        this.loadTemplates();
      } catch (e) { this.$message.error('保存失败'); }
    }
  },
  template: `
  <div>
    <h2 class="page-title">类目属性模板</h2>
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="选择类目">
          <el-cascader v-model="categoryPath" :options="categoryOptions"
            :props="{ expandTrigger: 'hover', emitPath: true }"
            placeholder="请选择叶子类目" clearable style="width:320px"
            @change="onCategoryChange"></el-cascader>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!leafCategoryId" @click="openCreate">新增属性</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe empty-text="请选择类目后查看属性模板">
        <el-table-column label="属性名称" prop="name" min-width="160"></el-table-column>
        <el-table-column label="可选值" min-width="260">
          <template #default="{row}"><span class="opt-tags">{{ optionsText(row.options) }}</span></template>
        </el-table-column>
        <el-table-column label="是否必填" width="110">
          <template #default="{row}"><el-tag :type="row.required ? 'danger' : 'info'" size="small">{{ reqText(row.required) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="排序" prop="sort" width="90"></el-table-column>
        <el-table-column label="创建时间" prop="createdAt" width="130"></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增属性模板" width="460px">
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="属性名称" prop="name">
          <el-input v-model="form.name" placeholder="如：成色、内存、尺码" maxlength="20"></el-input>
        </el-form-item>
        <el-form-item label="可选值">
          <el-input v-model="form.optionsText" type="textarea" :rows="2"
            placeholder="多个值用逗号分隔，如：99新,95新,9成新（可留空）"></el-input>
        </el-form-item>
        <el-form-item label="是否必填" prop="required">
          <el-switch v-model="form.required"></el-switch>
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="999"></el-input-number>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>`
};
