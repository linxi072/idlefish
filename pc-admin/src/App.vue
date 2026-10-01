<template>
  <Login v-if="!loggedIn" @logged-in="onLoggedIn"></Login>
  <div v-else class="layout">
    <aside class="sidebar">
      <div class="brand"><span class="logo">闲</span><span class="brand-name">闲置集后台</span></div>
      <el-menu :default-active="$route.name" @select="go" class="menu">
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
        <div class="tb-title">{{ activeTitle }}</div>
        <div class="tb-right">
          <el-badge :value="bellUnread" :hidden="!bellUnread" :max="99" class="bell">
            <el-button text @click="go('notify')"><el-icon><Bell /></el-icon></el-button>
          </el-badge>
          <span class="tb-user">{{ user ? user.username : '' }}</span>
          <el-button size="small" @click="logout">退出</el-button>
        </div>
      </header>
      <section class="content">
        <router-view></router-view>
      </section>
    </main>
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
      menus: [
        { key: 'dashboard', label: '控制台', icon: 'DataLine' },
        { key: 'items', label: '商品审核', icon: 'Goods' },
        { key: 'review', label: '评价审核', icon: 'Star' },
        { key: 'orders', label: '订单管理', icon: 'Box' },
        { key: 'dispute', label: '维权工单', icon: 'Scale' },
        { key: 'users', label: '用户管理', icon: 'User' },
        { key: 'categories', label: '类目管理', icon: 'Files' },
        { key: 'risk', label: '风控审计', icon: 'Shield' },
        { key: 'analytics', label: '运营 BI', icon: 'TrendCharts' },
        { key: 'marketing', label: '营销驾驶舱', icon: 'Aim' },
        { key: 'member', label: '会员等级', icon: 'Medal' },
        { key: 'recommend', label: '首页推荐', icon: 'Cpu' },
        { key: 'invite', label: '邀请拉新', icon: 'Connection' },
        { key: 'search-term', label: '搜索词运营', icon: 'Search' },
        { group: '财务与运营', icon: 'Wallet', children: [
          { key: 'wallet', label: '资金财务', icon: 'CreditCard' },
          { key: 'attributes', label: '属性模板', icon: 'PriceTag' },
          { key: 'coupon', label: '发券管理', icon: 'Ticket' },
          { key: 'activity', label: '活动管理', icon: 'MagicStick' }
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
    async refreshBell() {
      try { this.bellUnread = await notifyApi.unreadCount(); } catch (e) { this.bellUnread = 0; }
    }
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
  }
};
</script>
