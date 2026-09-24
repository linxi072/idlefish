package com.idlefish.trade.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.cache.CacheService;
import com.idlefish.trade.system.entity.SysDictData;
import com.idlefish.trade.system.entity.SysDictType;
import com.idlefish.trade.system.mapper.SysDictDataMapper;
import com.idlefish.trade.system.mapper.SysDictTypeMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 数据字典服务（PC 系统管理）：字典类型 + 字典明细 CRUD，
 * 字典明细读取走缓存（CacheService，默认本地、可切 Redis），变更时按类型失效。
 */
@Service
public class SysDictService {

    private static final long DICT_TTL_MIN = 10;

    private final SysDictTypeMapper typeMapper;
    private final SysDictDataMapper dataMapper;
    private final CacheService cache;

    public SysDictService(SysDictTypeMapper typeMapper, SysDictDataMapper dataMapper, CacheService cache) {
        this.typeMapper = typeMapper;
        this.dataMapper = dataMapper;
        this.cache = cache;
    }

    // ===== 字典类型 =====

    public IPage<SysDictType> listTypes(int page, int size, String keyword) {
        LambdaQueryWrapper<SysDictType> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.like(SysDictType::getDictType, keyword).or().like(SysDictType::getDictName, keyword);
        }
        w.orderByAsc(SysDictType::getId);
        return typeMapper.selectPage(new Page<>(page, size), w);
    }

    public SysDictType getType(Long id) {
        return typeMapper.selectById(id);
    }

    public void saveType(SysDictType e) {
        typeMapper.insert(e);
    }

    public void updateType(SysDictType e) {
        typeMapper.updateById(e);
    }

    public void removeType(Long id) {
        SysDictType t = getType(id);
        if (t != null) {
            dataMapper.delete(new LambdaQueryWrapper<SysDictData>().eq(SysDictData::getDictType, t.getDictType()));
            cache.evict("dict:" + t.getDictType());
        }
        typeMapper.deleteById(id);
    }

    // ===== 字典明细 =====

    public List<SysDictData> listData(String dictType) {
        return dataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType).orderByAsc(SysDictData::getDictSort));
    }

    public SysDictData getData(Long id) {
        return dataMapper.selectById(id);
    }

    public void saveData(SysDictData e) {
        dataMapper.insert(e);
        cache.evict("dict:" + e.getDictType());
    }

    public void updateData(SysDictData e) {
        dataMapper.updateById(e);
        cache.evict("dict:" + e.getDictType());
    }

    public void removeData(Long id) {
        SysDictData d = getData(id);
        dataMapper.deleteById(id);
        if (d != null) {
            cache.evict("dict:" + d.getDictType());
        }
    }

    // ===== 缓存读取（供前端下拉 / 业务枚举） =====

    /** 按类型读取字典明细（带缓存）。 */
    @SuppressWarnings("unchecked")
    public List<SysDictData> dictCache(String dictType) {
        String key = "dict:" + dictType;
        List<SysDictData> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        List<SysDictData> list = listData(dictType);
        cache.put(key, list, DICT_TTL_MIN, TimeUnit.MINUTES);
        return list;
    }

    /** 字典下拉项：label/value，供前端 select 使用。 */
    public List<Map<String, Object>> dropdown(String dictType) {
        return dictCache(dictType).stream().map(d -> {
            Map<String, Object> m = new HashMap<>(4);
            m.put("label", d.getDictLabel());
            m.put("value", d.getDictValue());
            return m;
        }).collect(Collectors.toList());
    }

    /** 校验字典类型存在，不存在抛异常（供业务侧引用字典时使用）。 */
    public void assertTypeExists(String dictType) {
        if (typeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, dictType)) == 0) {
            throw new BizException(Code.BIZ_ERROR, "字典类型不存在：" + dictType);
        }
    }
}
