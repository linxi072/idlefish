package com.idlefish.trade.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.lock.DistributedLock;
import com.idlefish.trade.common.lock.LockKeyBuilder;
import com.idlefish.trade.item.entity.Category;
import com.idlefish.trade.item.mapper.CategoryMapper;
import com.idlefish.trade.item.vo.CategoryVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 类目服务：三级类目树构建。
 * 注：生产环境类目树应缓存（Redis / 本地 Caffeine），此处每次查库构建（数据量小，可运行）。
 */
@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final DistributedLock lock;

    /** 类目树本地缓存（TTL 60s）；生产应换为 Redis / Caffeine。 */
    private volatile List<CategoryVO> treeCache;
    private final AtomicLong treeCacheTs = new AtomicLong(0);
    private static final long CACHE_TTL_MS = 60_000;
    /** 跨实例锁参数：最长等待 1s，租约 5s（重建为轻操作，5s 余量充足）。 */
    private static final long LOCK_WAIT_MS = 1000L;
    private static final long LOCK_LEASE_MS = 5000L;

    public CategoryService(CategoryMapper categoryMapper, DistributedLock lock) {
        this.categoryMapper = categoryMapper;
        this.lock = lock;
    }

    /**
     * 构建完整类目树（根为 parentId = 0）。带 60s 本地缓存。
     * <p>多实例下经 Redis 分布式锁做跨实例单飞重建；Redis 不可用 / 获取超时则退化为本实例
     * synchronized 单飞（缓存重建幂等，安全），与 V1.0.4 库存锁同样 fail-open 有界。
     */
    public List<CategoryVO> tree() {
        long now = System.currentTimeMillis();
        if (treeCache != null && now - treeCacheTs.get() < CACHE_TTL_MS) {
            return treeCache;
        }
        String token = lock.tryLock(LockKeyBuilder.category(), LOCK_WAIT_MS, LOCK_LEASE_MS);
        if (token != null) {
            try {
                return rebuildIfStale(now);
            } finally {
                lock.unlock(LockKeyBuilder.category(), token);
            }
        }
        // 降级：获取锁超时或 Redis 不可用。缓存仍在则复用稍旧值；为空则本实例 synchronized 单飞重建。
        if (treeCache != null) {
            return treeCache;
        }
        synchronized (this) {
            if (treeCache == null) {
                treeCache = buildTree();
                treeCacheTs.set(now);
            }
            return treeCache;
        }
    }

    /** 双重检查后重建类目树缓存（调用方已持分布式锁或处于 synchronized 临界区）。 */
    private List<CategoryVO> rebuildIfStale(long now) {
        if (treeCache != null && now - treeCacheTs.get() < CACHE_TTL_MS) {
            return treeCache;
        }
        treeCache = buildTree();
        treeCacheTs.set(now);
        return treeCache;
    }

    private List<CategoryVO> buildTree() {
        List<Category> all = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort));
        Map<Long, CategoryVO> nodeMap = new HashMap<>();
        for (Category c : all) {
            CategoryVO vo = new CategoryVO();
            vo.setId(c.getId());
            vo.setName(c.getName());
            vo.setIcon(c.getIcon());
            vo.setLevel(c.getLevel());
            vo.setSort(c.getSort());
            vo.setIsLeaf(c.getIsLeaf());
            nodeMap.put(c.getId(), vo);
        }
        List<CategoryVO> roots = new ArrayList<>();
        for (Category c : all) {
            CategoryVO node = nodeMap.get(c.getId());
            if (c.getParentId() == null || c.getParentId() == 0L) {
                roots.add(node);
            } else {
                CategoryVO parent = nodeMap.get(c.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(node);
                }
            }
        }
        return roots;
    }

    /** 失效本地缓存（新增类目后调用）。 */
    public void clearCache() {
        treeCache = null;
        treeCacheTs.set(0);
    }

    public Category getById(Long id) {
        Category c = categoryMapper.selectById(id);
        if (c == null) {
            throw new BizException(Code.CATEGORY_NOT_FOUND);
        }
        return c;
    }

    /** 仅取名称（聚合商品列表时用）。 */
    public String nameOf(Long id) {
        if (id == null) {
            return null;
        }
        Category c = categoryMapper.selectById(id);
        return c == null ? null : c.getName();
    }

    /** 后台新增类目（parentId=0 表示一级类目）。 */
    public Long create(Long parentId, String name, String icon, Integer level,
                       Integer sort, Integer isLeaf) {
        if (name == null || name.isBlank()) {
            throw new BizException(Code.PARAM_INVALID, "类目名称必填");
        }
        Category c = new Category();
        c.setParentId(parentId == null ? 0L : parentId);
        c.setName(name);
        c.setIcon(icon);
        c.setLevel(level == null ? 1 : level);
        c.setSort(sort == null ? 0 : sort);
        c.setIsLeaf(isLeaf == null ? 1 : isLeaf);
        categoryMapper.insert(c);
        clearCache();
        return c.getId();
    }
}
