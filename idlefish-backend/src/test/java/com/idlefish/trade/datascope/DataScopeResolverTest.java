package com.idlefish.trade.datascope;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据范围解析纯函数单测（REQ-02/REQ-09）：四种范围 + 默认从严。
 */
class DataScopeResolverTest {

    private OrgHierarchy hierarchy() {
        return new OrgHierarchy(Arrays.asList(
                new OrgRef(1, OrgRef.ROOT_PARENT),
                new OrgRef(2, 1),
                new OrgRef(3, 2),
                new OrgRef(4, 2)
        ));
    }

    private DataScopeResolver resolver() {
        return new DataScopeResolver(hierarchy());
    }

    @Test
    void self_onlyOwnOrg() {
        Set<Long> r = resolver().resolve(2, DataScope.SELF, null);
        assertEquals(new LinkedHashSet<>(Arrays.asList(2L)), r);
    }

    @Test
    void selfAndChild_includesDescendants() {
        Set<Long> r = resolver().resolve(1, DataScope.SELF_AND_CHILD, null);
        assertEquals(new LinkedHashSet<>(Arrays.asList(1L, 2L, 3L, 4L)), r);
    }

    @Test
    void appoint_usesSpecifiedSet() {
        Set<Long> appointed = new LinkedHashSet<>(Arrays.asList(3L, 4L));
        Set<Long> r = resolver().resolve(1, DataScope.APPOINT, appointed);
        assertEquals(appointed, r);
    }

    @Test
    void appoint_ignoresNullAndSelfWhenNotInSet() {
        Set<Long> appointed = new LinkedHashSet<>(Arrays.asList(3L, null, 4L));
        Set<Long> r = resolver().resolve(1, DataScope.APPOINT, appointed);
        assertEquals(new LinkedHashSet<>(Arrays.asList(3L, 4L)), r);
    }

    @Test
    void all_returnsNull_unrestricted() {
        Set<Long> r = resolver().resolve(1, DataScope.ALL, null);
        assertNull(r);
    }

    @Test
    void nullScope_fallsBackToDefaultStrictSelf() {
        // REQ-09：未声明范围 → 仅本机构
        Set<Long> r = resolver().resolve(2, null, null);
        assertEquals(new LinkedHashSet<>(Arrays.asList(2L)), r);
    }

    @Test
    void emptyAppointed_noOrgs() {
        Set<Long> r = resolver().resolve(1, DataScope.APPOINT, Collections.emptySet());
        assertTrue(r.isEmpty());
    }
}
