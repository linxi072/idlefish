// pc-admin/src/views/dict.js —— 系统管理：数据字典（类型 + 明细两级）
import { systemApi } from '../api.js';
import { formatMixin, notifyError } from '../utils/format.js';

export default {
  name: 'Dict',
  mixins: [formatMixin],
  data() {
    return {
      types: [], total: 0, loading: false, keyword: '',
      typeVisible: false, isEditType: false, savingType: false, typeForm: {},
      activeType: null, dataList: [], dataLoading: false,
      dataVisible: false, isEditData: false, savingData: false, dataForm: {}
    };
  },
  mounted() { this.loadTypes(); },
  methods: {
    async loadTypes() {
      this.loading = true;
      try {
        const r = await systemApi.dictTypes({ keyword: this.keyword });
        this.types = r.list || [];
        this.total = r.total;
      } catch (e) { notifyError(this, e, '加载失败'); }
      finally { this.loading = false; }
    },
    statusTag(s) { return s === 0 ? 'success' : 'info'; },
    statusText(s) { return s === 0 ? '正常' : '停用'; },
    openAddType() {
      this.isEditType = false;
      this.typeForm = { type: '', name: '', remark: '', status: 0 };
      this.typeVisible = true;
      this.$nextTick(() => this.$refs.typeForm && this.$refs.typeForm.clearValidate());
    },
    openEditType(row) {
      this.isEditType = true;
      this.typeForm = { id: row.id, type: row.type, name: row.name, remark: row.remark, status: row.status };
      this.typeVisible = true;
      this.$nextTick(() => this.$refs.typeForm && this.$refs.typeForm.clearValidate());
    },
    async saveType() {
      const ok = await this.$refs.typeForm.validate().catch(() => false);
      if (!ok) return;
      this.savingType = true;
      try {
        await systemApi.saveDictType(this.typeForm);
        this.$message.success(this.isEditType ? '已保存' : '已新增字典类型');
        this.typeVisible = false;
        this.loadTypes();
      } catch (e) { notifyError(this, e, '保存失败'); }
      finally { this.savingType = false; }
    },
    async removeType(row) {
      try { await this.$confirm('确认删除该字典类型？其下明细将一并删除。', '提示', { type: 'warning' }); }
      catch (e) { return; }
      await systemApi.deleteDictType(row.id);
      this.$message.success('已删除');
      if (this.activeType === row.type) this.activeType = null;
      this.loadTypes();
    },
    async openData(type) {
      this.activeType = type;
      this.dataLoading = true;
      try { this.dataList = await systemApi.dictData(type); }
      catch (e) { notifyError(this, e, '加载失败'); }
      finally { this.dataLoading = false; }
    },
    openAddData() {
      if (!this.activeType) return this.$message.warning('请先选择字典类型');
      this.isEditData = false;
      this.dataForm = {
        type: this.activeType, label: '', value: '',
        sort: (this.dataList.length || 0) + 1, status: 0, remark: ''
      };
      this.dataVisible = true;
      this.$nextTick(() => this.$refs.dataForm && this.$refs.dataForm.clearValidate());
    },
    openEditData(row) {
      this.isEditData = true;
      this.dataForm = {
        id: row.id, type: row.type, label: row.label, value: row.value,
        sort: row.sort, status: row.status, remark: row.remark
      };
      this.dataVisible = true;
      this.$nextTick(() => this.$refs.dataForm && this.$refs.dataForm.clearValidate());
    },
    async saveData() {
      const ok = await this.$refs.dataForm.validate().catch(() => false);
      if (!ok) return;
      this.savingData = true;
      try {
        await systemApi.saveDictData(this.dataForm);
        this.$message.success(this.isEditData ? '已保存' : '已新增字典项');
        this.dataVisible = false;
        this.openData(this.activeType);
      } catch (e) { notifyError(this, e, '保存失败'); }
      finally { this.savingData = false; }
    },
    async removeData(row) {
      try { await this.$confirm('确认删除该字典项？', '提示', { type: 'warning' }); }
      catch (e) { return; }
      await systemApi.deleteDictData(row.id);
      this.$message.success('已删除');
      this.openData(this.activeType);
    }
  },
  computed: {
    typeRules() {
      return {
        type: [
          { required: true, message: '请输入类型编码', trigger: 'blur' },
          { pattern: /^[a-z_]+$/, message: '小写字母 + 下划线', trigger: 'blur' }
        ],
        name: [{ required: true, message: '请输入类型名称', trigger: 'blur' }]
      };
    },
    dataRules() {
      return {
        label: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
        value: [{ required: true, message: '请输入数据值', trigger: 'blur' }],
        sort: [{ required: true, type: 'number', message: '请输入排序', trigger: 'blur' }]
      };
    }
  },
  template: `
  <div>
    <h2 class="page-title">系统管理 / 数据字典</h2>
    <el-row :gutter="16">
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>字典类型</span>
              <el-button size="small" type="success" @click="openAddType">新增类型</el-button>
            </div>
          </template>
          <el-form inline style="margin-bottom:10px">
            <el-form-item><el-input v-model="keyword" placeholder="类型/名称" clearable style="width:200px"></el-input></el-form-item>
            <el-form-item><el-button type="primary" @click="loadTypes">查询</el-button></el-form-item>
          </el-form>
          <el-table :data="types" v-loading="loading" border stripe highlight-current-row
            @current-change="(c)=>c&&openData(c.type)" style="width:100%;cursor:pointer">
            <el-table-column label="类型编码" prop="type" width="150"></el-table-column>
            <el-table-column label="名称" prop="name" width="110"></el-table-column>
            <el-table-column label="状态" width="70">
              <template #default="{row}"><el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{row}">
                <el-button size="small" link type="warning" @click="openEditType(row)">编辑</el-button>
                <el-button size="small" link type="danger" @click="removeType(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top:10px">共 {{ total }} 条</div>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>字典明细{{ activeType ? '：' + activeType : '' }}</span>
              <el-button size="small" type="success" @click="openAddData">新增明细</el-button>
            </div>
          </template>
          <el-table :data="dataList" v-loading="dataLoading" border stripe>
            <el-table-column label="显示名称" prop="label" width="160"></el-table-column>
            <el-table-column label="数据值" prop="value" width="160"></el-table-column>
            <el-table-column label="排序" prop="sort" width="70"></el-table-column>
            <el-table-column label="状态" width="70">
              <template #default="{row}"><el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="备注" prop="remark" min-width="120"></el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{row}">
                <el-button size="small" link type="warning" @click="openEditData(row)">编辑</el-button>
                <el-button size="small" link type="danger" @click="removeData(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!activeType" description="请选择左侧字典类型查看明细"></el-empty>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="typeVisible" :title="isEditType?'编辑字典类型':'新增字典类型'" width="420px">
      <el-form ref="typeForm" :model="typeForm" :rules="typeRules" label-width="90px">
        <el-form-item label="类型编码" prop="type"><el-input v-model="typeForm.type" :disabled="isEditType" placeholder="如：order_status"></el-input></el-form-item>
        <el-form-item label="类型名称" prop="name"><el-input v-model="typeForm.name" placeholder="如：订单状态"></el-input></el-form-item>
        <el-form-item label="备注" prop="remark"><el-input v-model="typeForm.remark" type="textarea" :rows="2"></el-input></el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="typeForm.status"><el-radio :label="0">正常</el-radio><el-radio :label="1">停用</el-radio></el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeVisible=false">取消</el-button>
        <el-button type="primary" :loading="savingType" @click="saveType">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dataVisible" :title="isEditData?'编辑字典项':'新增字典项'" width="420px">
      <el-form ref="dataForm" :model="dataForm" :rules="dataRules" label-width="90px">
        <el-form-item label="显示名称" prop="label"><el-input v-model="dataForm.label" placeholder="如：待支付"></el-input></el-form-item>
        <el-form-item label="数据值" prop="value"><el-input v-model="dataForm.value" placeholder="如：pending_pay"></el-input></el-form-item>
        <el-form-item label="排序" prop="sort"><el-input-number v-model="dataForm.sort" :min="0" :max="999"></el-input-number></el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="dataForm.status"><el-radio :label="0">正常</el-radio><el-radio :label="1">停用</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark"><el-input v-model="dataForm.remark" type="textarea" :rows="2"></el-input></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataVisible=false">取消</el-button>
        <el-button type="primary" :loading="savingData" @click="saveData">保存</el-button>
      </template>
    </el-dialog>
  </div>`
};
