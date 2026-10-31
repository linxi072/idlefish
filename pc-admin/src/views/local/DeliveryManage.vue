<template>
  <div>
    <page-header title="本地同城配送" desc="同城配送单查询与状态推进（配送中 / 已送达 / 已签收），金额单位「分」由前端折算展示" />
    <el-card shadow="never">
      <FilterBar :loading="loading" @search="load" @reset="reset">
        <el-form-item label="配送状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width:160px">
            <el-option v-for="(v,k) in DELIVERY_STATUS" :key="k" :label="v.text" :value="k"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="业务单号">
          <el-input v-model="bizNo" placeholder="订单号/回收单号" clearable style="width:200px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="city" placeholder="如 杭州市" clearable style="width:140px" @keyup.enter="load" />
        </el-form-item>
        <template #extra>
          <FilterTemplates page-key="delivery" :get-filters="getFilters" :on-apply="applyFilters" />
        </template>
      </FilterBar>

      <el-table :data="list" v-loading="loading" border stripe :max-height="560">
        <template #empty><EmptyState text="暂无配送单" desc="调整筛选条件后重试" /></template>
        <el-table-column label="配送单号" prop="deliveryNo" width="170"></el-table-column>
        <el-table-column label="业务类型" width="110">
          <template #default="{row}"><StatusTag :value="row.bizType" :map="DELIVERY_BIZ_TYPE" /></template>
        </el-table-column>
        <el-table-column label="业务单号" prop="bizNo" width="170"></el-table-column>
        <el-table-column label="城市" prop="city" width="90"></el-table-column>
        <el-table-column label="距离(km)" prop="distanceKm" width="100" align="right"></el-table-column>
        <el-table-column label="运费" width="100" align="right">
          <template #default="{row}">{{ yuan(row.freight) }}</template>
        </el-table-column>
        <el-table-column label="服务商" prop="providerName" width="110"></el-table-column>
        <el-table-column label="骑手" width="110">
          <template #default="{row}">
            <span v-if="row.courierName">{{ row.courierName }}<br><span class="g-sub">{{ row.courierPhone }}</span></span>
            <span v-else class="g-sub">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{row}"><StatusTag :value="row.status" :map="DELIVERY_STATUS" /></template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createdAt" width="155"></el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{row}">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button size="small" type="primary" :disabled="!canDelivering(row)" @click="advance(row, 'DELIVERING')">配送中</el-button>
            <el-button size="small" type="warning" :disabled="!canArrived(row)" @click="advance(row, 'ARRIVED')">已送达</el-button>
            <el-button size="small" type="success" :disabled="!canSigned(row)" @click="advance(row, 'SIGNED')">已签收</el-button>
          </template>
        </el-table-column>
      </el-table>

      <PageBar :total="total" :page="page" :size="size" @change="onPageChange" />
    </el-card>

    <el-dialog v-model="detailVisible" title="配送单详情" width="600px">
      <el-descriptions v-if="detailRow" :column="1" border>
        <el-descriptions-item label="配送单号">{{ detailRow.deliveryNo }}</el-descriptions-item>
        <el-descriptions-item label="业务类型"><StatusTag :value="detailRow.bizType" :map="DELIVERY_BIZ_TYPE" /></el-descriptions-item>
        <el-descriptions-item label="业务单号">{{ detailRow.bizNo }}</el-descriptions-item>
        <el-descriptions-item label="城市">{{ detailRow.city }}</el-descriptions-item>
        <el-descriptions-item label="起点">{{ detailRow.fromAddress }}</el-descriptions-item>
        <el-descriptions-item label="终点">{{ detailRow.toAddress }}</el-descriptions-item>
        <el-descriptions-item label="距离">{{ detailRow.distanceKm }} km</el-descriptions-item>
        <el-descriptions-item label="运费">{{ yuan(detailRow.freight) }}</el-descriptions-item>
        <el-descriptions-item label="服务商">{{ detailRow.providerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="运单号">{{ detailRow.providerNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="骑手">{{ detailRow.courierName || '-' }}（{{ detailRow.courierPhone || '-' }}）</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :value="detailRow.status" :map="DELIVERY_STATUS" /></el-descriptions-item>
        <el-descriptions-item v-if="detailRow.failReason" label="失败原因">{{ detailRow.failReason }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="tracks && tracks.length" class="lg-block">
        <div class="lg-title">配送轨迹</div>
        <el-timeline class="lg-timeline">
          <el-timeline-item
            v-for="(t, i) in tracks"
            :key="i"
            :timestamp="t.time"
            :type="trackType(t.type)"
            placement="top"
          >{{ t.desc }}</el-timeline-item>
        </el-timeline>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { localApi } from '@/api';
import { formatMixin, notifyError } from '@/utils/format';
import { DELIVERY_STATUS, DELIVERY_BIZ_TYPE } from '@/utils/status';
// pc-admin/src/views/local/DeliveryManage.vue —— 本地同城配送运营后台（F-05）
// 查询 + 状态推进（配送中 / 已送达 / 已签收），轨迹复用既有物流时间轴。

export default {
  name: 'DeliveryManage',
  mixins: [formatMixin],
  data() {
    return {
      list: [], total: 0, loading: false, bizNo: '', city: '', statusFilter: '', page: 1, size: 20,
      detailRow: null, tracks: [], detailVisible: false,
      DELIVERY_STATUS, DELIVERY_BIZ_TYPE
    };
  },
  mounted() { this.syncFromQuery(); this.load(); },
  watch: {
    '$route'() { this.syncFromQuery(); this.load(); }
  },
  methods: {
    reset() { this.bizNo = ''; this.city = ''; this.statusFilter = ''; this.page = 1; this.load(); },
    syncFromQuery() {
      const q = (this.$route && this.$route.query) || {};
      this.statusFilter = q.status || '';
      this.bizNo = q.bizNo || '';
      this.city = q.city || '';
      this.page = 1;
    },
    getFilters() { return { bizNo: this.bizNo, city: this.city, statusFilter: this.statusFilter }; },
    applyFilters(f) {
      f = f || {};
      this.bizNo = f.bizNo || '';
      this.city = f.city || '';
      this.statusFilter = f.statusFilter || '';
      this.page = 1;
      this.load();
    },
    onPageChange({ page, size }) { this.page = page; this.size = size; this.load(); },
    async load() {
      this.loading = true;
      try {
        const r = await localApi.deliveryList(this.statusFilter, this.bizNo, this.city, this.page, this.size);
        this.list = r.list || [];
        this.total = r.total || this.list.length;
      } catch (e) { notifyError(this, e, '加载配送单失败'); }
      finally { this.loading = false; }
    },
    canDelivering(row) { return row.status === 'CREATED' || row.status === 'ACCEPTED'; },
    canArrived(row) { return row.status === 'DELIVERING'; },
    canSigned(row) { return row.status === 'ARRIVED'; },
    async advance(row, status) {
      const label = { DELIVERING: '配送中', ARRIVED: '已送达', SIGNED: '已签收' }[status];
      try {
        await this.$confirm('确认将配送单『' + row.deliveryNo + '』推进为「' + label + '」？', '状态推进', { type: 'warning' });
      } catch (e) { return; }
      try {
        await localApi.advance(row.deliveryNo, status);
        this.$message.success('已推进为「' + label + '」');
      } catch (e) { notifyError(this, e, '状态推进失败'); }
      this.load();
    },
    async openDetail(row) {
      this.detailRow = row;
      this.detailVisible = true;
      this.tracks = [];
      try {
        const vo = await localApi.deliveryDetail(row.deliveryNo);
        this.tracks = (vo && vo.tracks) || [];
      } catch (e) {
        this.tracks = [];
      }
    },
    trackType(t) {
      return { sign: 'success', exception: 'danger', transit: 'primary', transport: 'primary', unknown: 'info' }[t] || 'info';
    }
  }
};
</script>
