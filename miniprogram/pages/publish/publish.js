const api = require('../../utils/api.js');

const CONDITIONS = ['全新', '99新', '95新', '9成新', '8成新', '7成新以下'];

Page({
  data: {
    title: '', price: '', originalPrice: '', description: '',
    images: [], cats: [], catIndex: 0, catName: '请选择类目',
    conditions: CONDITIONS, condIndex: 1, location: '', bargainEnable: false, submitting: false
  },

  onLoad() {
    api.getCategoryTree().then((tree) => {
      const leaves = [];
      const walk = (nodes, path) => nodes.forEach(n => {
        if (n.children && n.children.length) walk(n.children, path + n.name + '/');
        else leaves.push({ id: n.id, name: path + n.name });
      });
      walk(tree, '');
      this.setData({ cats: leaves });
    }).catch(() => {});
  },

  onTitle(e) { this.setData({ title: e.detail.value }); },
  onPrice(e) { this.setData({ price: e.detail.value }); },
  onOriginal(e) { this.setData({ originalPrice: e.detail.value }); },
  onDesc(e) { this.setData({ description: e.detail.value }); },
  onLocation(e) { this.setData({ location: e.detail.value }); },
  onCat(e) {
    const i = Number(e.detail.value);
    this.setData({ catIndex: i, catName: this.data.cats[i].name });
  },
  onCond(e) { this.setData({ condIndex: Number(e.detail.value) }); },
  onBargain(e) { this.setData({ bargainEnable: e.detail.value }); },

  chooseImage() {
    wx.chooseMedia({
      count: 9 - this.data.images.length, mediaType: ['image'], sourceType: ['album', 'camera'],
      success: (res) => {
        const paths = res.tempFiles.map(f => f.tempFilePath);
        this.setData({ images: this.data.images.concat(paths) });
      }
    });
  },
  removeImage(e) {
    const i = e.currentTarget.dataset.idx;
    const imgs = this.data.images.slice();
    imgs.splice(i, 1);
    this.setData({ images: imgs });
  },

  submit() {
    const d = this.data;
    if (!d.title.trim()) return wx.showToast({ title: '请输入标题', icon: 'none' });
    if (!d.price || Number(d.price) <= 0) return wx.showToast({ title: '请输入有效价格', icon: 'none' });
    if (!d.cats[d.catIndex]) return wx.showToast({ title: '请选择类目', icon: 'none' });
    if (d.submitting) return;
    this.setData({ submitting: true });
    // 先上传图片拿到访问 URL，再发布（真实/ mock 统一走 uploadImage）
    const uploadTasks = (d.images || []).map((p) => api.uploadImage(p));
    Promise.all(uploadTasks).then((urls) => {
      const payload = {
        title: d.title,
        price: Math.round(Number(d.price) * 100),
        originalPrice: d.originalPrice ? Math.round(Number(d.originalPrice) * 100) : null,
        categoryId: d.cats[d.catIndex].id, condition: d.conditions[d.condIndex], location: d.location,
        bargainEnable: d.bargainEnable, description: d.description,
        images: urls.filter(Boolean)
      };
      return api.publish(payload);
    }).then((r) => {
      api.track('item_publish', { item_id: r.id, category_id: payload.categoryId, price: payload.price });
      wx.showToast({ title: '已提交，审核中', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 800);
    }).catch((e) => {
      wx.showToast({ title: (e && e.msg) || '发布失败', icon: 'none' });
    }).finally(() => this.setData({ submitting: false }));
  }
});
