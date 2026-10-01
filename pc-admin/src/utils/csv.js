// pc-admin/src/utils/csv.js —— 前端 CSV 导出工具（从 api.js 抽出，与接口层解耦）
// 前端侧生成，便于 mock/真实模式统一下载。

// 将二维数组导出为带 UTF-8 BOM 的 CSV 文本（保证 Excel 正确识别中文）
export function toCsv(headers, rows) {
  const esc = (v) => {
    const s = (v == null ? '' : String(v));
    return (s.includes(',') || s.includes('"') || s.includes('\n'))
      ? '"' + s.replace(/"/g, '""') + '"' : s;
  };
  const lines = [headers.map(esc).join(',')];
  for (const r of rows) lines.push(r.map(esc).join(','));
  // 前置 UTF-8 BOM，保证 Excel 正确识别中文
  return '﻿' + lines.join('\n');
}

export function downloadCsv(filename, content) {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
