// pc-admin/src/mixins/crud-dialog.js —— 弹窗表单通用脚手架
// 消除各视图重复的「dialogVisible / submitting / form」数据与打开/关闭样板。
// 视图实现 submit() 完成实际保存；openCreate/openEdit 已封装。
// 免构建（选项式 API），不引入 element-plus。

export const crudDialogMixin = {
  data() {
    return {
      dialogVisible: false,
      submitting: false,
      form: {}
    };
  },
  methods: {
    openCreate(emptyForm) {
      this.form = emptyForm || {};
      this.dialogVisible = true;
    },
    openEdit(row, override) {
      this.form = Object.assign({}, row, override || {});
      this.dialogVisible = true;
    },
    closeDialog() {
      this.dialogVisible = false;
    },
    // 子类必须实现：完成实际保存
    async submit() {
      throw new Error('crudDialogMixin: 请实现 submit()');
    }
  }
};
