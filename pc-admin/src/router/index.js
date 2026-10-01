// pc-admin/src/router/index.js —— 视图路由表（菜单 key 与 route name 一一对应）
import { createRouter, createWebHashHistory } from 'vue-router';

import Dashboard from '@/views/dashboard/Dashboard.vue';
import Items from '@/views/merchandise/Items.vue';
import Review from '@/views/merchandise/Review.vue';
import Orders from '@/views/trade/Orders.vue';
import Dispute from '@/views/trade/Dispute.vue';
import Users from '@/views/user/Users.vue';
import Categories from '@/views/catalog/Categories.vue';
import Attributes from '@/views/catalog/Attributes.vue';
import Risk from '@/views/risk/Risk.vue';
import Analytics from '@/views/analytics/Analytics.vue';
import Marketing from '@/views/marketing/Marketing.vue';
import Member from '@/views/marketing/Member.vue';
import Recommend from '@/views/marketing/Recommend.vue';
import Invite from '@/views/marketing/Invite.vue';
import SearchTerm from '@/views/marketing/SearchTerm.vue';
import Coupon from '@/views/marketing/Coupon.vue';
import Activity from '@/views/marketing/Activity.vue';
import Wallet from '@/views/finance/Wallet.vue';
import Notify from '@/views/notify/Notify.vue';
import SysUser from '@/views/system/SysUser.vue';
import Role from '@/views/system/Role.vue';
import Organization from '@/views/system/Organization.vue';
import Menu from '@/views/system/Menu.vue';
import Dict from '@/views/system/Dict.vue';

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'dashboard', component: Dashboard },
  { path: '/items', name: 'items', component: Items },
  { path: '/review', name: 'review', component: Review },
  { path: '/orders', name: 'orders', component: Orders },
  { path: '/dispute', name: 'dispute', component: Dispute },
  { path: '/users', name: 'users', component: Users },
  { path: '/categories', name: 'categories', component: Categories },
  { path: '/risk', name: 'risk', component: Risk },
  { path: '/analytics', name: 'analytics', component: Analytics },
  { path: '/marketing', name: 'marketing', component: Marketing },
  { path: '/member', name: 'member', component: Member },
  { path: '/recommend', name: 'recommend', component: Recommend },
  { path: '/invite', name: 'invite', component: Invite },
  { path: '/search-term', name: 'search-term', component: SearchTerm },
  { path: '/wallet', name: 'wallet', component: Wallet },
  { path: '/attributes', name: 'attributes', component: Attributes },
  { path: '/coupon', name: 'coupon', component: Coupon },
  { path: '/activity', name: 'activity', component: Activity },
  { path: '/notify', name: 'notify', component: Notify },
  { path: '/sysuser', name: 'sysuser', component: SysUser },
  { path: '/role', name: 'role', component: Role },
  { path: '/organization', name: 'organization', component: Organization },
  { path: '/menu', name: 'menu', component: Menu },
  { path: '/dict', name: 'dict', component: Dict }
];

const router = createRouter({
  history: createWebHashHistory(),
  routes
});

export default router;
