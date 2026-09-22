const api = require('../../utils/api.js');

function emptyForm() {
  return { id: null, name: '家', receiver: '', phone: '', province: '', city: '', district: '', detail: '', isDefault: false };
}

Page({
  data: { list: [], showForm: false, form: emptyForm() },

  onLoad() { this.reload(); },
  onShow() { this.reload(); },

  reload() {
    api.getAddresses().then((list) => this.setData({ list: list || [] })).catch(() => {});
  },

  addNew() { this.setData({ showForm: true, form: emptyForm() }); },
  edit(e) {
    const id = e.currentTarget.dataset.id;
    const a = this.data.list.find(x => x.id === id);
    this.setData({ showForm: true, form: Object.assign(emptyForm(), a) });
  },
  closeForm() { this.setData({ showForm: false }); },

  onField(e) {
    const key = e.currentTarget.dataset.k;
    this.setData({ ['form.' + key]: e.detail.value });
  },
  onDefault(e) { this.setData({ 'form.isDefault': e.detail.value }); },

  save() {
    const f = this.data.form;
    if (!f.receiver.trim()) return wx.showToast({ title: '请填写收货人', icon: 'none' });
    if (!/^1\d{10}$/.test(f.phone)) return wx.showToast({ title: '请填写正确手机号', icon: 'none' });
    if (!f.province || !f.detail) return wx.showToast({ title: '请填写地区与详细地址', icon: 'none' });
    wx.showLoading({ title: '保存' });
    api.saveAddress(f).then(() => {
      wx.hideLoading();
      this.setData({ showForm: false });
      this.reload();
      wx.showToast({ title: '已保存', icon: 'success' });
    }).catch((e) => { wx.hideLoading(); wx.showToast({ title: (e && e.msg) || '保存失败', icon: 'none' }); });
  },

  remove(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({ title: '删除地址', content: '确定删除该地址？', success: (r) => {
      if (r.confirm) api.deleteAddress(id).then(() => this.reload()).catch(() => {});
    } });
  },

  setDefault(e) {
    const id = e.currentTarget.dataset.id;
    const list = this.data.list.map(a => Object.assign({}, a, { isDefault: a.id === id }));
    this.setData({ list });
    api.saveAddress(list.find(a => a.id === id)).catch(() => {});
    wx.showToast({ title: '已设为默认', icon: 'none' });
  }
});
