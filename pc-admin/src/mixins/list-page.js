// pc-admin/src/mixins/list-page.js —— 列表页通用脚手架
// 消除各视图重复的「分页/关键字/加载态」数据字段与分页事件处理样板。
// 视图只需实现 load()：依据 this.page / this.size / this.keyword 加载数据并赋值 this.list 与 this.total。
// 免构建（选项式 API），不引入 element-plus。

export const listPageMixin = {
  data() {
    return {
      page: 1,
      size: 20,
      total: 0,
      list: [],
      loading: false,
      keyword: ''
    };
  },
  methods: {
    // 子类必须实现：加载数据并写入 this.list / this.total
    async load() {
      throw new Error('listPageMixin: 请实现 load()');
    },
    async search() {
      this.page = 1;
      await this.load();
    },
    async reset() {
      this.keyword = '';
      this.page = 1;
      await this.load();
    },
    async handleSizeChange(s) {
      this.size = s;
      await this.load();
    },
    async handleCurrentChange(p) {
      this.page = p;
      await this.load();
    }
  }
};
