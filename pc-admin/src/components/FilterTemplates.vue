<template>
  <span class="filter-templates">
    <el-select
      v-model="selectedId"
      placeholder="筛选模板"
      clearable
      style="width:150px"
      @change="onSelect"
    >
      <el-option
        v-for="t in templates"
        :key="t.id"
        :label="(t.isDefault ? '★ ' : '') + t.name"
        :value="t.id"
      />
    </el-select>
    <el-button @click="openSave">存为模板</el-button>
    <el-button @click="openManage">管理</el-button>

    <!-- 保存对话框 -->
    <el-dialog v-model="saveVisible" title="保存当前筛选为模板" width="420px">
      <el-form label-width="80px">
        <el-form-item label="模板名称">
          <el-input v-model="saveForm.name" maxlength="20" show-word-limit placeholder="如：待审核-手机类" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="saveForm.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saveVisible = false">取消</el-button>
        <el-button type="primary" @click="doSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 管理对话框 -->
    <el-dialog v-model="manageVisible" title="筛选模板管理" width="680px">
      <el-table :data="templates" border size="small" empty-text="暂无模板">
        <el-table-column label="模板名称" min-width="160">
          <template #default="{ row }">
            <span v-if="row.isDefault" class="tpl-star">★</span>{{ row.name }}
          </template>
        </el-table-column>
        <el-table-column label="筛选条件" min-width="220">
          <template #default="{ row }">{{ describe(row.filters) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button size="small" @click="onApply(row)">应用</el-button>
            <el-button size="small" @click="toggleDefault(row)">
              {{ row.isDefault ? '取消默认' : '设为默认' }}
            </el-button>
            <el-button size="small" type="danger" plain @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="tpl-io">
        <el-button size="small" @click="doExport">导出</el-button>
        <el-button size="small" @click="openImport">导入</el-button>
      </div>

      <template #footer>
        <el-button @click="manageVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 导入/导出对话框 -->
    <el-dialog v-model="importVisible" :title="ioMode === 'export' ? '导出模板' : '导入模板'" width="560px">
      <p class="tpl-tip">导出可复制下方 JSON 备份；导入粘贴同结构 JSON 后点击「导入」。</p>
      <el-input v-model="ioText" type="textarea" :rows="10" placeholder="在此粘贴导出的 JSON" />
      <template #footer>
        <el-button v-if="ioMode === 'export'" @click="copyExport">复制</el-button>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button v-if="ioMode === 'import'" type="primary" @click="doImport">导入</el-button>
      </template>
    </el-dialog>
  </span>
</template>

<script>
import * as ft from '@/utils/filterTemplates';
// pc-admin/src/components/FilterTemplates.vue —— ITER-U03 REQ-06 筛选模板
// 本地持久化（localStorage）的命名模板：保存当前筛选条件、应用、默认、删除、导入/导出。
// 与后端无耦合；getFilters 取当前快照，onApply 回灌给页面。

export default {
  name: 'FilterTemplates',
  props: {
    pageKey: { type: String, required: true },
    // 取当前筛选条件快照：() => ({ keyword, statusFilter, ... })
    getFilters: { type: Function, required: true },
    // 应用模板：将 filters 对象回灌到页面
    onApply: { type: Function, required: true }
  },
  data() {
    return {
      templates: [],
      selectedId: '',
      saveVisible: false,
      saveForm: { name: '', isDefault: false },
      manageVisible: false,
      importVisible: false,
      ioText: '',
      ioMode: 'import'
    };
  },
  mounted() { this.refresh(); },
  methods: {
    refresh() { this.templates = ft.list(this.pageKey); },
    onSelect(id) { if (id) this.applyById(id); },
    applyById(id) {
      const f = ft.apply(this.pageKey, id);
      if (f) {
        this.onApply(f);
        this.$message.success('已应用筛选模板');
      }
    },
    onApply(tpl) { this.applyById(tpl.id); this.manageVisible = false; },
    openSave() { this.saveForm = { name: '', isDefault: false }; this.saveVisible = true; },
    doSave() {
      const name = (this.saveForm.name || '').trim();
      if (!name) { this.$message.warning('请填写模板名称'); return; }
      try {
        ft.save(this.pageKey, name, this.getFilters(), { isDefault: this.saveForm.isDefault });
        this.refresh();
        this.saveVisible = false;
        this.$message.success('已保存模板「' + name + '」');
      } catch (e) {
        this.$message.error((e && e.message) || '保存失败');
      }
    },
    toggleDefault(tpl) {
      ft.setDefault(this.pageKey, tpl.id, !tpl.isDefault);
      this.refresh();
      if (this.selectedId === tpl.id) this.selectedId = '';
    },
    onRemove(tpl) {
      this.$confirm('确认删除模板「' + tpl.name + '」？', '提示', { type: 'warning' })
        .then(() => {
          ft.remove(this.pageKey, tpl.id);
          this.refresh();
          if (this.selectedId === tpl.id) this.selectedId = '';
          this.$message.success('已删除');
        })
        .catch(() => {});
    },
    openManage() { this.refresh(); this.manageVisible = true; },
    doExport() { this.ioMode = 'export'; this.ioText = ft.exportAll(); this.importVisible = true; },
    openImport() { this.ioMode = 'import'; this.ioText = ''; this.importVisible = true; },
    copyExport() {
      const text = this.ioText || '';
      try {
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text);
        } else {
          const ta = document.createElement('textarea');
          ta.value = text;
          document.body.appendChild(ta);
          ta.select();
          document.execCommand('copy');
          document.body.removeChild(ta);
        }
        this.$message.success('已复制到剪贴板');
      } catch (e) {
        this.$message.info('请手动复制对话框中的文本');
      }
    },
    doImport() {
      try {
        const n = ft.importAll(this.ioText, { merge: true });
        this.refresh();
        this.importVisible = false;
        this.$message.success('已导入 ' + n + ' 个模板');
      } catch (e) {
        this.$message.error((e && e.message) || '导入失败');
      }
    },
    describe(filters) {
      const f = filters || {};
      const parts = Object.keys(f)
        .filter((k) => f[k] !== '' && f[k] != null)
        .map((k) => k + '=' + f[k]);
      return parts.length ? parts.join(' | ') : '（无）';
    }
  }
};
</script>
