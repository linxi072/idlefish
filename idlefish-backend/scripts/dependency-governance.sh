#!/usr/bin/env bash
# 依赖治理与离线构建可复现校验脚本（ITER-T08 / #545）
#
# 目标：把“依赖版本收敛 + 离线可复现”从约定变成可执行的门禁。
#
# 用法：
#   ./scripts/dependency-governance.sh                # 离线静态检查 + 尽力离线动态检查（缺失则优雅降级）
#   ./scripts/dependency-governance.sh --strict       # 动态检查缺失/未通过时视为失败（CI 门禁用）
#   MVN_REPO=/path/to/writable ./scripts/dependency-governance.sh   # 指定可写本地仓库，允许在线拉取治理插件
#
# 检查内容：
#   静态（无需任何 Maven 插件，离线永远可跑）：
#     1) 禁止 SNAPSHOT / RELEASE / LATEST 动态版本
#     2) <dependencyManagement> / <pluginManagement> 须存在（版本收敛 + 插件版本固定）
#     3) 直接依赖版本来源统计（提示性）
#   离线可解析性（信息性）：
#     用 -o dependency:resolve 验证 compile/test/runtime 依赖是否全部缓存于本地仓库
#   动态（需 maven-dependency-plugin / maven-enforcer-plugin）：
#     1) dependency:analyze     —— 检测 used-undeclared / unused-declared
#     2) dependency:tree        —— 生成依赖锁文件 scripts/dependency-lock.txt（可提交，CI diff）
#     3) -Pgovernance enforcer:enforce —— 依赖版本收敛门禁（dependencyConvergence）
#   只读离线仓库缺上述插件时优雅降级（WARN + SKIP）；--strict 下失败。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"            # idlefish-backend 模块根
POM="$APP_DIR/pom.xml"

# ---- 可覆盖环境变量（默认值见下方）----
MVN_REPO="${MVN_REPO:-/Users/mezo/Documents/localRepository/mvnRepository}"
# 离线 settings（指向私服/离线镜像）；文件不存在则回退默认 settings
SETTINGS_DEFAULT="$APP_DIR/../.mvn-settings-offline.xml"
MVN_SETTINGS="${MVN_SETTINGS:-$SETTINGS_DEFAULT}"
MVN="${MVN:-mvn}"

STRICT=0
[[ "${1:-}" == "--strict" ]] && STRICT=1

log() { echo "[governance] $*"; }
warn() { echo "[governance][WARN] $*" >&2; }
die() { echo "[governance][ERROR] $*" >&2; exit 1; }

# settings 参数：文件存在才加 -gs，避免无此文件时报错
gs_arg() { [[ -f "$MVN_SETTINGS" ]] && echo "-gs $MVN_SETTINGS" || echo ""; }

# ---------- 1) 静态检查 ----------
run_static() {
  log "== 1) 静态检查 =="
  local rc=0
  if grep -nE "<(version)>[^<]*(SNAPSHOT|RELEASE|LATEST)" "$POM" >/dev/null 2>&1; then
    warn "检测到动态版本（SNAPSHOT/RELEASE/LATEST），违反可复现构建纪律"
    rc=1
  else
    log "  [ok] 无 SNAPSHOT/RELEASE/LATEST 动态版本"
  fi
  if grep -q "<dependencyManagement>" "$POM"; then
    log "  [ok] <dependencyManagement> 已配置（依赖版本收敛）"
  else
    warn "缺少 <dependencyManagement>，依赖版本未统一收敛"; rc=1
  fi
  if grep -q "<pluginManagement>" "$POM"; then
    log "  [ok] <pluginManagement> 已配置（插件版本固定）"
  else
    warn "缺少 <pluginManagement>，构建插件版本未固定"; rc=1
  fi
  # 提示性：统计直接依赖中未显式写 <version> 的条目（由 parent BOM 管理属正常）
  local unversioned
  unversioned=$(awk '
    /<dependencies>/ {in_dep=1}
    /<\/dependencies>/ {in_dep=0}
    in_dep && /<dependency>/ {buf=$0; has_ver=0; next}
    in_dep && /<version>/ {has_ver=1}
    in_dep && /<\/dependency>/ {if(!has_ver) print buf; buf=""; has_ver=0}
  ' "$POM" | grep -c "<dependency>")
  log "  [info] 直接依赖中由 BOM/parent 管理版本（未显式写 <version>）的条目数：$unversioned"
  return $rc
}

# ---------- 2) 离线可解析性（信息性）----------
run_offline_resolvable() {
  local repo="$MVN_REPO"
  log "== 2) 离线可解析性检查（本地仓库：${repo}）=="
  if [[ ! -d "$repo" ]]; then
    warn "本地仓库目录不存在：$repo"; return 1
  fi
  if $MVN -o $(gs_arg) -Dmaven.repo.local="$repo" dependency:resolve -DincludeScope=test -q >/dev/null 2>&1; then
    log "  [ok] 全部 compile/test/runtime 依赖可在离线仓库解析"
    return 0
  else
    warn "离线解析失败：存在未缓存于 $repo 的依赖。"
    warn "  常见根因：mysql-connector-j 传递依赖 protobuf-java 等未被预置。"
    warn "  修复：在联网/私服环境执行  mvn dependency:go-offline -Dmaven.repo.local=$repo"
    return 1
  fi
}

# ---------- 3) 动态检查（依赖分析 / 锁文件 / 收敛门禁）----------
# 离线尝试；若插件缺失且仓库可写，退化为在线拉取；否则返回 2（无法执行）
mvn_try() {
  if $MVN -o $(gs_arg) -Dmaven.repo.local="$MVN_REPO" "$@" -q >/dev/null 2>&1; then
    return 0
  fi
  if [[ -w "$MVN_REPO" ]]; then
    log "  · 离线缺失治理插件，尝试在线（可写仓库 ${MVN_REPO}）"
    if $MVN $(gs_arg) -Dmaven.repo.local="$MVN_REPO" "$@" -q >/dev/null 2>&1; then
      return 0
    fi
  fi
  return 2
}

# 捕获输出并扫描 dependency:analyze 的可疑项
analyze_capture() {
  local tmp; tmp=$(mktemp)
  local ok=0
  if $MVN -o $(gs_arg) -Dmaven.repo.local="$MVN_REPO" dependency:analyze -q >"$tmp" 2>&1; then
    ok=1
  elif [[ -w "$MVN_REPO" ]]; then
    log "  · 离线缺失插件，尝试在线"
    $MVN $(gs_arg) -Dmaven.repo.local="$MVN_REPO" dependency:analyze -q >"$tmp" 2>&1 && ok=1
  fi
  if [[ $ok -eq 1 ]]; then
    if grep -qE "Used undeclared|Unused declared" "$tmp"; then
      warn "dependency:analyze 发现可疑依赖（建议清理）："
      grep -iE "undeclared|declared" "$tmp" | sed 's/^/      /' | head -20
      rm -f "$tmp"; return 1
    else
      log "  [ok] dependency:analyze 通过（无 used-undeclared / unused-declared 阻断项）"
      rm -f "$tmp"; return 0
    fi
  else
    warn "dependency:analyze 未能执行（maven-dependency-plugin 不在离线仓库，且无在线可写仓库）。"
    warn "  预置：mvn dependency:get -Dartifact=org.apache.maven.plugins:maven-dependency-plugin:3.8.1 -Dmaven.repo.local=$MVN_REPO"
    rm -f "$tmp"; return 1
  fi
}

run_dynamic() {
  log "== 3) 动态检查（analyze / 依赖锁 / 收敛门禁）=="
  local rc=0

  analyze_capture || rc=1

  if mvn_try dependency:tree -DoutputFile="$APP_DIR/scripts/dependency-lock.txt" -DappendOutput=false; then
    log "  [ok] 已生成依赖锁文件 scripts/dependency-lock.txt（可提交，CI 比对差异）"
  else
    warn "依赖锁文件生成跳过（maven-dependency-plugin 不可用）。"
    warn "  预置：mvn dependency:get -Dartifact=org.apache.maven.plugins:maven-dependency-plugin:3.8.1 -Dmaven.repo.local=$MVN_REPO"
    rc=1
  fi

  if mvn_try -Pgovernance enforcer:enforce; then
    log "  [ok] 依赖版本收敛门禁通过（-Pgovernance）"
  else
    warn "依赖收敛门禁未执行（maven-enforcer-plugin 不在离线仓库，或无在线可写仓库）。"
    warn "  预置：mvn dependency:get -Dartifact=org.apache.maven.plugins:maven-enforcer-plugin:3.5.0 -Dmaven.repo.local=$MVN_REPO"
    rc=1
  fi
  return $rc
}

main() {
  local src=0 dyn=0
  run_static || src=$?
  run_offline_resolvable || true   # 信息性，不直接 fail（环境缺口已知）
  run_dynamic || dyn=$?
  echo
  if [[ $src -eq 0 && $dyn -eq 0 ]]; then
    log "治理校验全部通过 ✓"
  else
    if [[ $STRICT -eq 1 ]]; then
      die "STRICT 模式下存在治理问题（static=${src} dynamic=${dyn}）"
    fi
    warn "部分检查降级/未通过（static=${src} dynamic=${dyn}）。非 strict 模式不阻断构建；详见上方 WARN。"
  fi
}

main
