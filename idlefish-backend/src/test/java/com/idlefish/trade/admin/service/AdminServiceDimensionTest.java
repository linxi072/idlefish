package com.idlefish.trade.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.item.service.CategoryService;
import com.idlefish.trade.item.service.ItemService;
import com.idlefish.trade.risk.mapper.AuditLogMapper;
import com.idlefish.trade.risk.mapper.RiskEventMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.user.mapper.UserMapper;
import com.idlefish.trade.user.service.CreditService;
import com.idlefish.trade.admin.service.AdminAuthService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminService.auditList 维度过滤回归测试（REQ-07 支撑）。
 * 纯 Mockito，离线可跑；验证 categoryId / conditionLevel 维度条件注入，以及 status 缺省值。
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceDimensionTest {

    @Mock
    private ItemMapper itemMapper;
    @Mock
    private ItemService itemService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RiskEventMapper riskEventMapper;
    @Mock
    private AuditLogMapper auditLogMapper;
    @Mock
    private AdminAuthService adminAuthService;
    @Mock
    private CreditService creditService;

    @InjectMocks
    private AdminService adminService;

    @BeforeAll
    static void initTableInfo() {
        // 纯 Mockito 测 MP 查询类必备：注册 Item 的 TableInfo，否则 LambdaQueryWrapper 无法生成 SQL 片段
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new Configuration(), "dummy");
        TableInfoHelper.initTableInfo(assistant, Item.class);
    }

    @Test
    void auditList_appliesDimensionFilters() {
        Page<Item> fake = new Page<>(1, 10);
        ArgumentCaptor<LambdaQueryWrapper<Item>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        when(itemMapper.selectPage(any(Page.class), captor.capture())).thenReturn(fake);

        IPage<Item> result = adminService.auditList("on_sale", "iphone", 7L, 3, 2, 10);

        assertNotNull(result);
        verify(itemMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));

        String sql = captor.getValue().getCustomSqlSegment().toLowerCase();
        assertTrue(sql.contains("status ="), "应施加 status 过滤，实际 SQL: " + sql);
        assertTrue(sql.contains("title like"), "应施加 keyword 模糊搜索，实际 SQL: " + sql);
        // 维度条件注入（列名大小写/下划线随全局命名策略，断言列片段即可）
        assertTrue(sql.contains("category"), "应施加 categoryId 维度过滤，实际 SQL: " + sql);
        assertTrue(sql.contains("condition"), "应施加 conditionLevel 维度过滤，实际 SQL: " + sql);
    }

    @Test
    void auditList_defaultsStatusAndSkipsNullDimensions() {
        Page<Item> fake = new Page<>(1, 20);
        ArgumentCaptor<LambdaQueryWrapper<Item>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        when(itemMapper.selectPage(any(Page.class), captor.capture())).thenReturn(fake);

        // status / keyword / categoryId / conditionLevel 全部为 null
        IPage<Item> result = adminService.auditList(null, null, null, null, 1, 20);

        assertNotNull(result);
        String sql = captor.getValue().getCustomSqlSegment().toLowerCase();
        // status 缺省应落 pending_review（SQL 段为 status = ?，绑定值为 pending_review 经分页对象传入）
        assertTrue(sql.contains("status ="), "status 缺省应施加过滤，实际 SQL: " + sql);
        // 维度缺失时不应出现维度条件（列名随命名策略，断言列片段即可）
        assertFalse(sql.contains("category"), "categoryId 为 null 时不应施加过滤，实际 SQL: " + sql);
        assertFalse(sql.contains("condition"), "conditionLevel 为 null 时不应施加过滤，实际 SQL: " + sql);
    }

    @Test
    void auditList_clampsInvalidPageAndSize() {
        Page<Item> fake = new Page<>(1, 1);
        ArgumentCaptor<Page<Item>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        when(itemMapper.selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class))).thenReturn(fake);

        adminService.auditList(null, null, null, null, 0, -5);

        Page<Item> used = pageCaptor.getValue();
        assertEquals(1, used.getCurrent(), "page 应被钳制为 >=1");
        assertEquals(1, used.getSize(), "size 应被钳制为 >=1");
    }
}
