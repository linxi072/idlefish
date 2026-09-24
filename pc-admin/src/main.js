// pc-admin/src/main.js —— 应用入口：侧边栏布局 + 视图切换 + 登录门禁
import Login from './views/login.js';
import Dashboard from './views/dashboard.js';
import Items from './views/items.js';
import Orders from './views/orders.js';
import Users from './views/users.js';
import Categories from './views/categories.js';
import Risk from './views/risk.js';
import SysUser from './views/sysuser.js';
import Role from './views/role.js';
import Organization from './views/organization.js';
import Menu from './views/menu.js';
import Dict from './views/dict.js';

const { createApp } = window.Vue;

const App = {
  data() {
    return {
      loggedIn: !!window.localStorage.getItem('idlefish_admin_token'),
      user: null,
      active: 'dashboard',
      menus: [
        { key: 'dashboard', label: '控制台', icon: '📊' },
        { key: 'items', label: '商品审核', icon: '🛍️' },
        { key: 'orders', label: '订单管理', icon: '📦' },
        { key: 'users', label: '用户管理', icon: '👤' },
        { key: 'categories', label: '类目管理', icon: '🗂️' },
        { key: 'risk', label: '风控审计', icon: '🛡️' },
        { group: '系统管理', icon: '⚙️', children: [
          { key: 'sysuser', label: '管理员', icon: '👨‍💼' },
          { key: 'role', label: '角色管理', icon: '🔑' },
          { key: 'organization', label: '机构管理', icon: '🏢' },
          { key: 'menu', label: '菜单管理', icon: '📑' },
          { key: 'dict', label: '数据字典', icon: '📚' }
        ] }
      ]
    };
  },
  computed: {
    current() {
      return {
        dashboard: Dashboard, items: Items, orders: Orders,
        users: Users, categories: Categories, risk: Risk,
        sysuser: SysUser, role: Role, organization: Organization, menu: Menu, dict: Dict
      }[this.active];
    },
    activeTitle() {
      for (const m of this.menus) {
        if (m.children) {
          const c = m.children.find(x => x.key === this.active);
          if (c) return m.group + ' / ' + c.label;
        } else if (m.key === this.active) {
          return m.label;
        }
      }
      return '控制台';
    }
  },
  methods: {
    onLoggedIn(u) { this.user = u; this.loggedIn = true; },
    logout() {
      window.localStorage.removeItem('idlefish_admin_token');
      this.loggedIn = false; this.user = null; this.active = 'dashboard';
    },
    go(key) { this.active = key; }
  },
  template: `
  <Login v-if="!loggedIn" @logged-in="onLoggedIn"></Login>
  <div v-else class="layout">
    <aside class="sidebar">
      <div class="brand"><span class="logo">闲</span><span class="brand-name">闲置集后台</span></div>
      <el-menu :default-active="active" @select="go" class="menu">
        <template v-for="m in menus" :key="m.key || m.group">
          <el-sub-menu v-if="m.children" :index="m.group">
            <template #title><span class="m-icon">{{ m.icon }}</span><span>{{ m.group }}</span></template>
            <el-menu-item v-for="c in m.children" :key="c.key" :index="c.key">
              <span class="m-icon">{{ c.icon }}</span><span>{{ c.label }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="m.key">
            <span class="m-icon">{{ m.icon }}</span><span>{{ m.label }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </aside>
    <main class="main">
      <header class="topbar">
        <div class="tb-title">{{ activeTitle }}</div>
        <div class="tb-right">
          <span class="tb-user">{{ user ? user.username : '' }}</span>
          <el-button size="small" @click="logout">退出</el-button>
        </div>
      </header>
      <section class="content">
        <component :is="current"></component>
      </section>
    </main>
  </div>`
};

const app = createApp(App);
app.use(window.ElementPlus);
app.mount('#app');
