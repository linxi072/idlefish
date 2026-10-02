<template>
  <Login v-if="!loggedIn" @logged-in="onLoggedIn"></Login>
  <div v-else class="layout">
    <aside class="sidebar" :class="{collapsed, 'mobile-open': mobileOpen}">
      <div class="brand"><span class="logo">闲</span><span class="brand-name">闲置集后台</span></div>
      <el-menu :default-active="$route.name" @select="go" class="menu" :collapse="collapsed" :collapse-transition="false">
        <template v-for="m in menus" :key="m.key || m.group">
          <el-sub-menu v-if="m.children" :index="m.group">
            <template #title><el-icon><component :is="m.icon" /></el-icon><span>{{ m.group }}</span></template>
            <el-menu-item v-for="c in m.children" :key="c.key" :index="c.key">
              <el-icon><component :is="c.icon" /></el-icon><span>{{ c.label }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="m.key">
            <el-icon><component :is="m.icon" /></el-icon><span>{{ m.label }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </aside>
    <main class="main">
      <header class="topbar">
        <div class="tb-left">
          <el-button text class="collapse-btn" @click="toggleSidebar" :aria-label="(isMobile ? mobileOpen : collapsed) ? '展开菜单' : '收起菜单'">
            <el-icon><Fold v-if="!isMobile && !collapsed" /><Expand v-else /></el-icon>
          </el-button>
          <el-breadcrumb separator="/" class="tb-crumb">
            <el-breadcrumb-item>运营后台</el-breadcrumb-item>
            <el-breadcrumb-item>{{ activeTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="tb-right">
          <el-badge :value="bellUnread" :hidden="!bellUnread" :max="99" class="bell">
            <el-button text @click="go('notify')"><el-icon><Bell /></el-icon></el-button>
          </el-badge>
          <el-avatar class="tb-avatar" :size="30">{{ (user ? user.username : '管').slice(0, 1) }}</el-avatar>
          <span class="tb-user">{{ user ? user.username : '' }}</span>
          <el-button size="small" @click="logout">退出</el-button>
        </div>
      </header>
      <section class="content">
        <router-view></router-view>
      </section>
    </main>
    <div class="sidebar-backdrop" v-if="mobileOpen" @click="mobileOpen = false"></div>
  </div>
</template>

<script>
import Login from '@/views/auth/Login.vue';
import { notifyApi, adminApi, getToken, clearToken } from '@/api';

export default {
  name: 'App',
  components: { Login },
  data() {
    return {
      loggedIn: !!getToken(),
      user: null,
      bellUnread: 0,
      collapsed: false,
      mobileOpen: false,
      isMobile: false,
      menus: [
        { key: 'dashboard', label: '控制台', icon: 'DataLine' },
        { group: '用户管理', icon: 'User', children: [
          { key: 'users', label: '用户列表', icon: 'User' },
          { key: 'member', label: '会员等级', icon: 'Medal' },
          { key: 'invite', label: '邀请拉新', icon: 'Connection' }
        ] },
        { group: '商品与内容', icon: 'ShoppingCart', children: [
          { key: 'items', label: '商品审核', icon: 'Goods' },
          { key: 'categories', label: '类目管理', icon: 'Files' },
          { key: 'review', label: '评价审核', icon: 'Star' },
          { key: 'recommend', label: '首页推荐', icon: 'Cpu' },
          { key: 'search-term', label: '搜索词运营', icon: 'Search' }
        ] },
        { group: '订单管理', icon: 'Tickets', children: [
          { key: 'orders', label: '订单管理', icon: 'Box' },
          { key: 'dispute', label: '维权工单', icon: 'Scale' }
        ] },
        { group: '营销管理', icon: 'Promotion', children: [
          { key: 'marketing', label: '营销驾驶舱', icon: 'Aim' },
          { key: 'coupon', label: '发券管理', icon: 'Ticket' },
          { key: 'activity', label: '活动管理', icon: 'MagicStick' }
        ] },
        { group: '财务运营', icon: 'Wallet', children: [
          { key: 'wallet', label: '资金财务', icon: 'CreditCard' },
          { key: 'attributes', label: '属性模板', icon: 'PriceTag' }
        ] },
        { key: 'risk', label: '风控审计', icon: 'Shield' },
        { group: '报表管理', icon: 'DataAnalysis', children: [
          { key: 'analytics', label: '运营 BI', icon: 'TrendCharts' }
        ] },
        { key: 'notify', label: '消息中心', icon: 'Bell' },
        { group: '系统管理', icon: 'Setting', children: [
          { key: 'sysuser', label: '管理员', icon: 'UserFilled' },
          { key: 'role', label: '角色管理', icon: 'Key' },
          { key: 'organization', label: '机构管理', icon: 'OfficeBuilding' },
          { key: 'menu', label: '菜单管理', icon: 'Notebook' },
          { key: 'dict', label: '数据字典', icon: 'Collection' }
        ] }
      ]
    };
  },
  computed: {
    activeTitle() {
      const key = this.$route.name;
      for (const m of this.menus) {
        if (m.children) {
          const c = m.children.find(x => x.key === key);
          if (c) return m.group + ' / ' + c.label;
        } else if (m.key === key) {
          return m.label;
        }
      }
      return '控制台';
    }
  },
  methods: {
    onLoggedIn(u) { this.user = u; this.loggedIn = true; this.refreshBell(); },
    logout() {
      clearToken();
      this.loggedIn = false; this.user = null; this.bellUnread = 0;
      this.$router.push({ name: 'dashboard' });
    },
    go(key) { this.$router.push({ name: key }); },
    // 折叠按钮：桌面端切换 220/64px 折叠栏；移动端切换抽屉开合
    toggleSidebar() {
      if (this.isMobile) this.mobileOpen = !this.mobileOpen;
      else this.collapsed = !this.collapsed;
    },
    onResize() {
      this.isMobile = window.innerWidth <= 1024;
      if (!this.isMobile) this.mobileOpen = false; // 回到桌面端时收起抽屉，避免残留遮罩
    },
    async refreshBell() {
      try { this.bellUnread = await notifyApi.unreadCount(); } catch (e) { this.bellUnread = 0; }
    }
  },
  watch: {
    // 路由切换后自动收起移动端抽屉
    $route() { this.mobileOpen = false; }
  },
  mounted() {
    // D-18 路由守卫：初始化时若本地存在令牌，则校验其有效性（防篡改/过期令牌直接进入后台）。
    // 校验失败（401/403）清除令牌并回登录页；成功则载入当前管理员并刷新铃铛。
    if (this.loggedIn) {
      adminApi.me()
        .then(r => { this.user = r.user; this.refreshBell(); })
        .catch(() => {
          clearToken();
          this.loggedIn = false; this.user = null; this.bellUnread = 0;
        });
    }
    // 消息中心标记已读后，由 notify 视图派发事件刷新铃铛角标
    window.addEventListener('notify-unread', (e) => {
      this.bellUnread = (e.detail && e.detail.unread) || 0;
    });
    // 响应式：监听视口尺寸，切换桌面端/移动端布局
    window.addEventListener('resize', this.onResize);
    this.onResize();
  }
};
</script>
