package com.idlefish.trade.datascope;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 机构层级纯函数单测：森林索引、含自身的后代计算、多根、自环/回环防御。
 */
class OrgHierarchyTest {

    /** 单根三层：1 → 2 → 3,4 ；2 → 5。 */
    private List<OrgRef> singleRoot() {
        return Arrays.asList(
                new OrgRef(1, OrgRef.ROOT_PARENT),
                new OrgRef(2, 1),
                new OrgRef(3, 2),
                new OrgRef(4, 2),
                new OrgRef(5, 2)
        );
    }

    @Test
    void descendantsOf_includesSelfAndAllChildren() {
        OrgHierarchy h = new OrgHierarchy(singleRoot());
        assertEquals(Arrays.asList(1L, 2L, 3L, 4L, 5L), h.descendantsOf(1));
        assertEquals(Arrays.asList(2L, 3L, 4L, 5L), h.descendantsOf(2));
        assertEquals(Arrays.asList(3L), h.descendantsOf(3));
    }

    @Test
    void descendantsOf_missingRoot_returnsEmpty() {
        OrgHierarchy h = new OrgHierarchy(singleRoot());
        assertTrue(h.descendantsOf(999).isEmpty());
    }

    @Test
    void contains_reflectsMembership() {
        OrgHierarchy h = new OrgHierarchy(singleRoot());
        assertTrue(h.contains(3));
        assertFalse(h.contains(999));
    }

    @Test
    void multipleRoots_independentForests() {
        List<OrgRef> refs = Arrays.asList(
                new OrgRef(10, OrgRef.ROOT_PARENT),
                new OrgRef(11, 10),
                new OrgRef(20, OrgRef.ROOT_PARENT),
                new OrgRef(21, 20)
        );
        OrgHierarchy h = new OrgHierarchy(refs);
        assertEquals(Arrays.asList(10L, 11L), h.descendantsOf(10));
        assertEquals(Arrays.asList(20L, 21L), h.descendantsOf(20));
    }

    @Test
    void cycle_isTerminatedSafely() {
        // 2 → 3 → 2 形成环；不能死循环
        List<OrgRef> refs = Arrays.asList(
                new OrgRef(2, 1),
                new OrgRef(3, 2),
                new OrgRef(2, 3) // 回边，制造环
        );
        OrgHierarchy h = new OrgHierarchy(refs);
        List<Long> descendants = h.descendantsOf(2);
        // 环内节点各出现一次（visited 去重）
        assertEquals(2, descendants.size());
        assertTrue(descendants.contains(2L));
        assertTrue(descendants.contains(3L));
    }

    @Test
    void selfLoop_collapsesToRootParent() {
        // id==parentId 视为非法，强制收敛到根父，不应形成自环
        List<OrgRef> refs = Arrays.asList(
                new OrgRef(1, OrgRef.ROOT_PARENT),
                new OrgRef(7, 7) // 自环
        );
        OrgHierarchy h = new OrgHierarchy(refs);
        // 7 不会指向自己，作为叶子存在；从 1 不可达 7
        assertEquals(Arrays.asList(1L), h.descendantsOf(1));
        assertEquals(Arrays.asList(7L), h.descendantsOf(7));
    }

    @Test
    void emptyInput_safe() {
        OrgHierarchy h = new OrgHierarchy(null);
        assertTrue(h.descendantsOf(1).isEmpty());
        assertFalse(h.contains(1));
    }
}
