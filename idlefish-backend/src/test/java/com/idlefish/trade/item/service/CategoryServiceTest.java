package com.idlefish.trade.item.service;

import com.idlefish.trade.common.lock.DistributedLock;
import com.idlefish.trade.item.entity.Category;
import com.idlefish.trade.item.mapper.CategoryMapper;
import com.idlefish.trade.item.vo.CategoryVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CategoryService 单测（纯 Mockito，离线可跑，不依赖真实 Redis/MySQL）。
 * 验证：① 缓存命中不重复查库；② 跨实例锁获取时仅重建一次；③ Redis 不可用（tryLock 返回 null）降级仍可构建。
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private DistributedLock lock;

    @Test
    void tree_buildsOnceWhenCacheMiss_andLockAcquired() {
        when(categoryMapper.selectList(any())).thenReturn(sampleCategories());
        when(lock.tryLock(anyString(), anyLong(), anyLong())).thenReturn("token-1");

        CategoryService svc = new CategoryService(categoryMapper, lock);
        List<CategoryVO> first = svc.tree();
        List<CategoryVO> second = svc.tree();

        assertNotNull(first);
        assertEquals(1, first.size(), "单一根类目应返回 1 个 root");
        // 第二次命中 60s 本地缓存，mapper 仅查一次
        verify(categoryMapper, times(1)).selectList(any());
        assertEquals(first, second);
    }

    @Test
    void tree_degradesToLocalRebuild_whenLockUnavailable() {
        when(categoryMapper.selectList(any())).thenReturn(sampleCategories());
        // Redis 不可用 / 超时：tryLock 返回 null → 退化为本实例 synchronized 单飞，仍可构建
        when(lock.tryLock(anyString(), anyLong(), anyLong())).thenReturn(null);

        CategoryService svc = new CategoryService(categoryMapper, lock);
        List<CategoryVO> tree = svc.tree();

        assertNotNull(tree);
        assertEquals(1, tree.size());
        verify(categoryMapper, times(1)).selectList(any());
    }

    @Test
    void tree_returnsFreshCacheFromOtherInstance_whenLockTimeout_andCachePresent() {
        when(categoryMapper.selectList(any())).thenReturn(sampleCategories());
        // 模拟：第一实例持有锁并重建；本实例获取锁超时（null），但缓存已被他实例填充
        when(lock.tryLock(anyString(), anyLong(), anyLong())).thenReturn(null);

        CategoryService svc = new CategoryService(categoryMapper, lock);
        // 预热：经由降级路径构建一次
        svc.tree();
        // 再次调用：顶部快速返回已存在的缓存，不再查库
        svc.tree();

        verify(categoryMapper, times(1)).selectList(any());
    }

    private List<Category> sampleCategories() {
        Category c = new Category();
        c.setId(1L);
        c.setParentId(0L);
        c.setName("手机");
        c.setLevel(1);
        c.setSort(1);
        c.setIsLeaf(1);
        return Arrays.asList(c);
    }
}
