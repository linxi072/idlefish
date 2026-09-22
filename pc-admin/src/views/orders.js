// pc-admin/src/views/orders.js —— 订单管理（含发货 / 退款处理）
import { adminApi } from '../api.js';

const STATUS = {
  pending_pay: '待支付', paid: '已支付', pending_ship: '待发货',
  shipping: '待收货', completed: '已完成', closed: '已关闭', refunding: '退款中'
};
const TAG = {
  pending_pay: 'info', paid: 'warning', pending_ship: 'warning',
  shipping: 'primary', completed: 'success', closed: 'info', refunding: 'danger'
};

export default {
  name: 'Orders',
  data() {
    return { list: [], total: 0, loading: false, keyword: '', statusFilter: '', detailRow: null, shipVisible: false, shipForm: { company: '', logisticNo: '' } };
  },
  mounted() { this.load(); },
  computed: {
    filtered() { return this.list; }
  },
  methods: {
    async load() {
      this.loading = true;
      try {
        const r = await adminApi.orders({ keyword: this.keyword, status: this.statusFilter, page: 1, size: 20 });
        this.list = r.list || [];
        this.total = r.total;
      } finally { this.loading = false; }
    },
    statusText(c) { return STATUS[c] || c; },
    statusTag(c) { return TAG[c] || 'info'; },
    view(row) { this.detailRow = row; },
    openShip(row) { this.detailRow = row; this.shipVisible = true; this.shipForm = { company: '顺丰速运', logisticNo: '' }; },
    async doShip() {
      try {
        await adminApi.shipOrder(this.detailRow.orderNo, this.shipForm.logisticNo);
        this.$message.success('已标记发货');
      } catch (e) { this.$message.error((e && e.msg) || '发货失败'); }
      this.shipVisible = false; this.load();
    },
    async refund(row, agree) {
      if (!agree) { this.detailRow = null; return; }
      try {
        await adminApi.refundAgree(row.orderNo);
        this.$message.success('已同意退款');
      } catch (e) { this.$message.error((e && e.msg) || '退款处理失败'); }
      this.detailRow = null; this.load();
    }
  },
  template: `
  <div>
    <h2 class="page-title">订单管理</h2>
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="关键词"><el-input v-model="keyword" placeholder="订单号/商品标题" clearable></el-input></el-form-item>
        <el-form-item label="订单状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width:150px">
            <el-option v-for="(t,k) in {'pending_pay':'待支付','paid':'已支付','pending_ship':'待发货','shipping':'待收货','completed':'已完成','closed':'已关闭','refunding':'退款中'}" :key="k" :label="t" :value="k"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
      </el-form>

      <el-table :data="filtered" v-loading="loading" border stripe>
        <el-table-column label="订单号" prop="orderNo" width="180"></el-table-column>
        <el-table-column label="商品" prop="title" min-width="160"></el-table-column>
        <el-table-column label="买家" width="100"><template #default="{row}">{{ row.buyerId || '-' }}</template></el-table-column>
        <el-table-column label="卖家" width="100"><template #default="{row}">{{ row.sellerId || '-' }}</template></el-table-column>
        <el-table-column label="金额" width="100"><template #default="{row}">¥{{ (row.amount/100).toFixed(2) }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="下单时间" prop="createdAt" width="160"></el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{row}">
            <el-button size="small" @click="view(row)">详情</el-button>
            <el-button v-if="row.status==='shipping'" size="small" type="primary" @click="openShip(row)">发货</el-button>
            <el-button v-if="row.status==='refunding'" size="small" type="success" @click="refund(row,true)">退款</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailRow !== null && !shipVisible" title="订单详情" width="520px">
      <el-descriptions v-if="detailRow" :column="1" border>
        <el-descriptions-item label="订单号">{{ detailRow.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="商品">{{ detailRow.title }}</el-descriptions-item>
        <el-descriptions-item label="买卖双方">{{ detailRow.buyerId }} → {{ detailRow.sellerId }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ (detailRow.amount/100).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusTag(detailRow.status)">{{ statusText(detailRow.status) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="物流" v-if="detailRow.logisticsNo">{{ detailRow.logisticsNo }}</el-descriptions-item>
      </el-descriptions>
      <template #footer v-if="detailRow && detailRow.status==='refunding'">
        <el-button type="success" @click="refund(detailRow,true)">同意退款</el-button>
        <el-button type="danger" @click="refund(detailRow,false)">驳回退款</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="shipVisible" title="标记发货" width="420px">
      <el-form label-width="80px">
        <el-form-item label="物流公司"><el-input v-model="shipForm.company"></el-input></el-form-item>
        <el-form-item label="运单号"><el-input v-model="shipForm.logisticNo" placeholder="请输入运单号"></el-input></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipVisible=false">取消</el-button>
        <el-button type="primary" @click="doShip">确认发货</el-button>
      </template>
    </el-dialog>
  </div>`
};
