import { USE_MOCK, req, pageTo } from '@/api/core';
import mock from '@/mock';

export const walletApi = {
  withdrawals: () => USE_MOCK ? Promise.resolve(mock.withdrawals.slice())
    : req('GET', '/api/admin/withdrawals'),
  approveWithdrawal: (id) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', `/api/admin/withdrawals/${id}/approve`),
  rejectWithdrawal: (id) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', `/api/admin/withdrawals/${id}/reject`),
  reconciliation: (day) => USE_MOCK ? Promise.resolve({
      date: day || '2026-09-20',
      orderCount: 342, paySuccess: 318, payFail: 6, refundCount: 9,
      platformFeeFen: 642000, netFen: 12158000,
      details: [
        { bizNo: 'NO20260920002', type: 'pay', amountFen: 420000, feeFen: 21000, status: 'success', time: '2026-09-20 09:00' },
        { bizNo: 'NO20260919003', type: 'pay', amountFen: 520000, feeFen: 26000, status: 'success', time: '2026-09-19 12:00' },
        { bizNo: 'NO20260918004', type: 'refund', amountFen: 19900, feeFen: 0, status: 'success', time: '2026-09-18 15:30' }
      ]
    }) : req('GET', '/api/admin/reconciliation', null, { day }),
  // 结算单解冻（风控处置完成后，frozen → pending 重新进入放款队列）
  // 注：后端 AdminFinanceController 仅暴露此解冻动作端点，无结算单列表端点，故视图以「输入结算单ID」方式解冻。
  unfreezeSettlement: (id) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', `/api/admin/settlements/${id}/unfreeze`)
};
