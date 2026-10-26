package com.idlefish.trade.marketing.activity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 抽奖概率抽取纯函数单测（ITER-F08 / REQ-09、REQ-12）。
 * 覆盖概率合法性边界与累计概率抽取命中逻辑。
 */
class LotteryDrawerTest {

    private LotteryPrize prize(String id, double p) {
        LotteryPrize x = new LotteryPrize();
        x.setPrizeId(id);
        x.setName(id);
        x.setProbability(BigDecimal.valueOf(p));
        return x;
    }

    private LotteryPrize prizeNullProb(String id) {
        LotteryPrize x = new LotteryPrize();
        x.setPrizeId(id);
        x.setName(id);
        x.setProbability(null);
        return x;
    }

    // ===== validateProbabilities =====

    @Test
    void validate_null_false() {
        assertFalse(LotteryDrawer.validateProbabilities(null));
    }

    @Test
    void validate_empty_false() {
        assertFalse(LotteryDrawer.validateProbabilities(new ArrayList<>()));
    }

    @Test
    void validate_negative_false() {
        List<LotteryPrize> list = new ArrayList<>();
        list.add(prize("A", -0.1));
        assertFalse(LotteryDrawer.validateProbabilities(list));
    }

    @Test
    void validate_sumExceedsOne_false() {
        List<LotteryPrize> list = new ArrayList<>();
        list.add(prize("A", 0.6));
        list.add(prize("B", 0.5));
        assertFalse(LotteryDrawer.validateProbabilities(list));
    }

    @Test
    void validate_sumEqualsOne_true() {
        List<LotteryPrize> list = new ArrayList<>();
        list.add(prize("A", 0.4));
        list.add(prize("B", 0.6));
        assertTrue(LotteryDrawer.validateProbabilities(list));
    }

    @Test
    void validate_sumBelowOne_true() {
        List<LotteryPrize> list = new ArrayList<>();
        list.add(prize("A", 0.4));
        list.add(prize("B", 0.3)); // 余量 0.3 为未中奖
        assertTrue(LotteryDrawer.validateProbabilities(list));
    }

    // ===== draw(List, double) =====

    @Test
    void draw_nullPrizes_null() {
        assertNull(LotteryDrawer.draw(null, 0.5));
    }

    @Test
    void draw_rBelowZero_null() {
        List<LotteryPrize> list = new ArrayList<>();
        list.add(prize("A", 0.5));
        assertNull(LotteryDrawer.draw(list, -0.1));
    }

    @Test
    void draw_rAtOne_null() {
        List<LotteryPrize> list = new ArrayList<>();
        list.add(prize("A", 0.5));
        assertNull(LotteryDrawer.draw(list, 1.0));
    }

    @Test
    void draw_hitFirst() {
        List<LotteryPrize> list = new ArrayList<>();
        list.add(prize("A", 0.4));
        list.add(prize("B", 0.3));
        assertEquals("A", LotteryDrawer.draw(list, 0.1).getPrizeId());
    }

    @Test
    void draw_hitSecond() {
        List<LotteryPrize> list = new ArrayList<>();
        list.add(prize("A", 0.4));
        list.add(prize("B", 0.3));
        assertEquals("B", LotteryDrawer.draw(list, 0.5).getPrizeId());
    }

    @Test
    void draw_miss_returnsNull() {
        List<LotteryPrize> list = new ArrayList<>();
        list.add(prize("A", 0.4));
        list.add(prize("B", 0.3));
        assertNull(LotteryDrawer.draw(list, 0.8));
    }

    @Test
    void draw_skipNullProbability() {
        // A=0.4, 跳过 null 概率的 C, B=0.3；r=0.45 应命中 B
        List<LotteryPrize> list = new ArrayList<>();
        list.add(prize("A", 0.4));
        list.add(prizeNullProb("C"));
        list.add(prize("B", 0.3));
        assertEquals("B", LotteryDrawer.draw(list, 0.45).getPrizeId());
    }

    // ===== draw(List, Random) =====

    @Test
    void draw_withRandom_delegates() {
        List<LotteryPrize> list = new ArrayList<>();
        list.add(prize("A", 1.0));
        assertEquals("A", LotteryDrawer.draw(list, new Random(1)).getPrizeId());
    }
}
