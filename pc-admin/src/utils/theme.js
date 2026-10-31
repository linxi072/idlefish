// pc-admin/src/utils/theme.js —— 主题切换与图表配色桥（ITER-U04）
// 设计令牌定义在 styles/tokens.css；本文件只做「读写令牌 / 切换主题」的桥接，
// 唯一出现的色值是图表与数据字段的降级兜底（令牌读取失败时使用）。

const THEME_KEY = 'idlefish.admin.theme';
const DARK_CLASS = 'dark';

// 会员等级默认色 / 图标（业务数据字段，非样式；集中在此避免视图内散落色值）
export const DEFAULT_LEVEL_COLOR = '#9E9E9E';
export const DEFAULT_LEVEL_ICON = '🏅';

// 图表配色兜底（与 tokens.css 的 --chart-* / --text-* 保持一致）
const FALLBACK = {
  series: ['#FFB300', '#1677FF', '#18A058', '#FF8F1F', '#F53F3F'],
  axis: '#C9CDD4',
  split: '#F2F3F5',
  text: '#9A9FA8',
  text2: '#5A5F66',
  surface: '#FFFFFF'
};

function safeStorageGet(key) {
  try { return window.localStorage.getItem(key); } catch (e) { return null; }
}

function safeStorageSet(key, val) {
  try { window.localStorage.setItem(key, val); } catch (e) { /* 隐私模式/配额异常时静默 */ }
}

function prefersDark() {
  try {
    return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
  } catch (e) {
    return false;
  }
}

// 初始主题：显式偏好 > 系统偏好 > 亮色
export function resolveInitialTheme() {
  const stored = safeStorageGet(THEME_KEY);
  if (stored === 'dark') return true;
  if (stored === 'light') return false;
  return prefersDark();
}

/**
 * 应用主题。persist=true 时写入本地偏好（仅在用户主动切换时传 true，
 * 避免首次访问就把系统偏好固化为用户选择）。
 */
export function applyTheme(dark, persist = false) {
  if (typeof document === 'undefined') return;
  const el = document.documentElement;
  el.classList.toggle(DARK_CLASS, !!dark);
  el.dataset.theme = dark ? 'dark' : 'light';
  // 短暂开启过渡，避免切换瞬间整页闪白；尊重减少动效偏好（CSS 侧已处理）
  el.classList.add('theme-anim');
  window.setTimeout(() => el.classList.remove('theme-anim'), 260);
  if (persist) safeStorageSet(THEME_KEY, dark ? 'dark' : 'light');
  try {
    window.dispatchEvent(new CustomEvent('theme-change', { detail: { dark: !!dark } }));
  } catch (e) { /* 老浏览器不支持 CustomEvent 构造器时忽略 */ }
}

export function isDarkTheme() {
  return typeof document !== 'undefined' && document.documentElement.classList.contains(DARK_CLASS);
}

function readVar(name, fallback) {
  if (typeof document === 'undefined' || !document.documentElement) return fallback;
  try {
    const v = getComputedStyle(document.documentElement).getPropertyValue(name);
    return (v || '').trim() || fallback;
  } catch (e) {
    return fallback;
  }
}

/**
 * 图表配色：运行时从设计令牌读取，确保暗色主题下图表同步换色。
 * 用法：const palette = chartPalette(); option.yAxis.splitLine.lineStyle.color = palette.split;
 */
export function chartPalette() {
  return {
    series: [
      readVar('--chart-series-1', FALLBACK.series[0]),
      readVar('--chart-series-2', FALLBACK.series[1]),
      readVar('--chart-series-3', FALLBACK.series[2]),
      readVar('--chart-series-4', FALLBACK.series[3]),
      readVar('--chart-series-5', FALLBACK.series[4])
    ],
    axis: readVar('--chart-axis', FALLBACK.axis),
    split: readVar('--chart-split', FALLBACK.split),
    text: readVar('--text-3', FALLBACK.text),
    text2: readVar('--text-2', FALLBACK.text2),
    surface: readVar('--surface', FALLBACK.surface)
  };
}
