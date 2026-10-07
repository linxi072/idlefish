#!/usr/bin/env bash
# 离线构建校验：在沙箱/CI 离线环境下编译 idlefish-backend 并运行可观测性相关单元单测
# （纯 Mockito，不依赖 MySQL，可离线跑）。用于提交前快速验证 T01 改动不破坏编译与核心契约。
# 用法：./scripts/observability/build-check.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

# 仅运行可观测性相关的 Mockito 单测（不含需要 MySQL 的 @SpringBootTest 集成测试）
OBS_TESTS="MetricsRegistryPercentileTest,AlertEvaluatorTest,ReconciliationServiceTest,ObservabilityControllerHealthTest"

log "离线编译 + 运行可观测性单测（test 过滤：$OBS_TESTS）"
log "settings=$MVN_SETTINGS  repo=$MVN_REPO"

mvn_offline -q test -Dtest="$OBS_TESTS" -DfailIfNoTests=false \
  && log "构建校验通过 ✓" \
  || die "构建校验失败，请查看上方 Maven 输出"
