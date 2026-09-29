// pages/invite/invite.js —— 邀请拉新页（F-13.4 对齐 InviteController /api/invite/*）
const api = require('../../utils/api.js');
const store = require('../../utils/store.js');

// 当前用户 ID：优先取登录态，mock 缺省回退为 2001（与 mock.js 的 me.id 一致）
function currentUserId() {
  const u = store.getUserInfo();
  return (u && u.id) || 2001;
}

Page({
  data: {
    myCode: '',          // 我的邀请码
    bindCode: '',        // 待绑定的好友邀请码
    invitees: [],        // 我邀请的好友列表（{inviteeId, rewarded, createdAt}）
    loading: true,
    binding: false
  },

  onShow() {
    this.loadCode();
    this.loadInvitees();
  },

  // 拉取我的邀请码
  loadCode() {
    api.inviteMyCode(currentUserId()).then((res) => {
      this.setData({ myCode: (res && res.code) || '' });
    }).catch(() => {
      this.setData({ myCode: '' });
    });
  },

  // 拉取我邀请的好友列表（真实后端返回 {records,total}，mock 直接返回数组）
  loadInvitees() {
    this.setData({ loading: true });
    api.inviteInvitees(currentUserId()).then((res) => {
      const list = (res && res.records) || res || [];
      this.setData({ invitees: list, loading: false });
    }).catch(() => {
      this.setData({ invitees: [], loading: false });
    });
  },

  onBindInput(e) {
    this.setData({ bindCode: e.detail.value });
  },

  // 绑定好友邀请码（我作为被邀请人）
  onBindTap() {
    const code = (this.data.bindCode || '').trim();
    if (!code) {
      wx.showToast({ title: '请输入邀请码', icon: 'none' });
      return;
    }
    this.setData({ binding: true });
    api.inviteBind(currentUserId(), code).then(() => {
      this.setData({ binding: false, bindCode: '' });
      wx.showToast({ title: '绑定成功', icon: 'success' });
      this.loadInvitees();
    }).catch((e) => {
      this.setData({ binding: false });
      wx.showToast({ title: (e && e.msg) || '绑定失败', icon: 'none' });
    });
  },

  // 复制我的邀请码
  onCopyCode() {
    if (!this.data.myCode) return;
    wx.setClipboardData({
      data: this.data.myCode,
      success: () => { wx.showToast({ title: '已复制', icon: 'success' }); }
    });
  }
});
