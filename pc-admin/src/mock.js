// pc-admin/src/mock.js —— 运营后台本地 Mock 数据（USE_MOCK=true 时启用）
export const categories = [
  { id: 1, name: '手机数码', parentId: 0, children: [
    { id: 11, name: '手机', parentId: 1, children: [
      { id: 111, name: 'iPhone', parentId: 11 }, { id: 112, name: '安卓手机', parentId: 11 } ] },
    { id: 12, name: '电脑/平板', parentId: 1, children: [
      { id: 121, name: '笔记本', parentId: 12 }, { id: 122, name: '平板电脑', parentId: 12 } ] }
  ] },
  { id: 2, name: '服饰鞋包', parentId: 0, children: [
    { id: 21, name: '女装', parentId: 2 }, { id: 22, name: '男装', parentId: 2 }, { id: 23, name: '鞋靴', parentId: 2 } ] },
  { id: 3, name: '家居家电', parentId: 0, children: [
    { id: 31, name: '大家电', parentId: 3 }, { id: 32, name: '家具', parentId: 3 } ] },
  { id: 4, name: '图书文娱', parentId: 0, children: [
    { id: 41, name: '图书', parentId: 4 }, { id: 42, name: '游戏', parentId: 4 } ] },
  { id: 5, name: '母婴用品', parentId: 0 },
  { id: 6, name: '运动户外', parentId: 0 }
];

const img = (s) => `https://picsum.photos/seed/${s}/300/300`;

export const items = [
  { id: 9001, title: 'Switch OLED 续航版 95新', price: 1380, categoryName: '游戏', seller: '数码小哥', status: 'onsale', auditStatus: 'passed', createdAt: '2026-09-21 10:12', description: '自用 Switch OLED 续航版，95 新，屏幕无划痕，配件齐全（原装充电线 + 双手柄），已贴钢化膜。电池健康，到手即用。', images: ['seed/9001a', 'seed/9001b'] },
  { id: 9002, title: 'iPhone 14 Pro 256G 暗紫色', price: 5200, categoryName: 'iPhone', seller: '数码小哥', status: 'onsale', auditStatus: 'passed', createdAt: '2026-09-21 09:30', description: '国行 iPhone 14 Pro 256G 暗紫色，无拆无修，电池效率 91%，带原装包装盒与发票。', images: ['seed/9002a', 'seed/9002b'] },
  { id: 9003, title: 'MacBook Air M1 13寸', price: 4200, categoryName: '笔记本', seller: '衣橱清仓', status: 'onsale', auditStatus: 'passed', createdAt: '2026-09-20 21:05', description: 'MacBook Air M1 13 寸 8+256，日常办公轻度使用，外观九五新，充电循环 120 次。', images: ['seed/9003a', 'seed/9003b'] },
  { id: 9004, title: '九成新羽绒服 男款 L码', price: 199, categoryName: '男装', seller: '衣橱清仓', status: 'pending_review', auditStatus: 'pending', createdAt: '2026-09-21 08:40', description: '波司登羽绒服男款 L 码，九成新，仅穿过两次，无污渍无破损，支持验货。', images: ['seed/9004a', 'seed/9004b'] },
  { id: 9005, title: '索尼 WH-1000XM4 降噪耳机', price: 1080, categoryName: '手机', seller: '数码小哥', status: 'pending_review', auditStatus: 'pending', createdAt: '2026-09-21 08:10', description: '索尼旗舰头戴降噪耳机 XM4，充新，降噪正常，附原装收纳盒与线材。', images: ['seed/9005a', 'seed/9005b'] },
  { id: 9006, title: '宜家 BILLY 书柜 白色', price: 120, categoryName: '家具', seller: '衣橱清仓', status: 'onsale', auditStatus: 'passed', createdAt: '2026-09-19 16:20', description: '宜家 BILLY 白色书柜，自提，轻微使用痕迹，结构稳固，含 adjustable 隔板。', images: ['seed/9006a', 'seed/9006b'] },
  { id: 9007, title: '疑似违规：全新虫草低价', price: 50, categoryName: '其他', seller: '新注册用户', status: 'pending_review', auditStatus: 'pending', createdAt: '2026-09-21 07:55', description: '【待核查】该商品标题与描述涉嫌违规关键词，价格明显低于市场价，需人工重点复核卖家资质与实物。', images: ['seed/9007a'] }
];

export const orders = [
  { orderNo: 'NO20260921001', title: 'Switch OLED 续航版', buyer: '我', seller: '数码小哥', amount: 1380, status: 'pending_pay', createdAt: '2026-09-21 11:00' },
  { orderNo: 'NO20260920002', title: 'MacBook Air M1', buyer: '我', seller: '衣橱清仓', amount: 4200, status: 'shipping', logistic: '顺丰速运 SF1234567890', createdAt: '2026-09-20 09:00' },
  { orderNo: 'NO20260919003', title: 'iPhone 14 Pro', buyer: '张三', seller: '数码小哥', amount: 5200, status: 'completed', createdAt: '2026-09-19 12:00' },
  { orderNo: 'NO20260918004', title: '羽绒服 男款', buyer: '李四', seller: '衣橱清仓', amount: 199, status: 'refunding', createdAt: '2026-09-18 15:30' },
  { orderNo: 'NO20260917005', title: '降噪耳机', buyer: '王五', seller: '数码小哥', amount: 1080, status: 'closed', createdAt: '2026-09-17 10:00' }
];

export const users = [
  { id: 2001, nickname: '我', phone: '138****6027', creditScore: 92, status: 0, createdAt: '2026-09-01' },
  { id: 1001, nickname: '数码小哥', phone: '139****1234', creditScore: 96, status: 0, createdAt: '2026-08-12' },
  { id: 1002, nickname: '衣橱清仓', phone: '137****5678', creditScore: 88, status: 0, createdAt: '2026-08-20' },
  { id: 1003, nickname: '新注册用户', phone: '135****0000', creditScore: 50, status: 1, createdAt: '2026-09-21' },
  { id: 1004, nickname: '高频发布号', phone: '136****9999', creditScore: 60, status: 0, createdAt: '2026-09-15' }
];

export const riskEvents = [
  { id: 'R1', userId: 1001, rule: 'R_NEW_DEVICE', score: 35, action: '放行', createdAt: '2026-09-21 09:00' },
  { id: 'R2', userId: 1004, rule: 'R_FREQ_PUBLISH', score: 72, action: '人工复核', createdAt: '2026-09-21 08:30' },
  { id: 'R3', userId: 1003, rule: 'R_SUSPECT_KEYWORD', score: 88, action: '拦截', createdAt: '2026-09-21 07:55' }
];

export const auditLogs = [
  { opId: 'A1', operator: 'admin', target: 'item:9002', action: '通过审核', createdAt: '2026-09-21 09:35' },
  { opId: 'A2', operator: 'admin', target: 'item:9003', action: '通过审核', createdAt: '2026-09-20 21:10' },
  { opId: 'A3', operator: 'risk', target: 'user:1003', action: '封禁账号', createdAt: '2026-09-21 07:56' }
];

export function stats() {
  return {
    gmv: 128600, orderCnt: 342, userCnt: 1560, itemCnt: 892,
    pendingReview: 3, refunding: 1, todayRegister: 42,
    trend: [12, 19, 15, 25, 22, 30, 28, 35, 31, 40, 38, 45]
  };
}

export default { categories, items, orders, users, riskEvents, auditLogs, stats };
