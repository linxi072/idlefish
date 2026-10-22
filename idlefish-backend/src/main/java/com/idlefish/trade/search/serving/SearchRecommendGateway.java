package com.idlefish.trade.search.serving;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.item.vo.ItemVO;
import com.idlefish.trade.search.serving.dto.ItemQuery;

import java.util.List;

/**
 * 搜推服务化端口（ITER-T04 / REQ-01/06/10）：搜索与推荐的统一入口抽象。
 * <p>
 * 双模式实现（REQ-10 灰度切流）：
 * <ul>
 *   <li>{@link InlineSearchRecommendGateway} 内联模式：委托既有进程内服务（{@code SearchEnhanceService} /
 *       {@code RecommendService}），默认模式，零新增外部依赖；</li>
 *   <li>{@code HttpSearchRecommendGateway} 远程模式：经 HTTP + 服务令牌调用独立部署的搜推服务
 *       （同一份代码、不同配置的部署单元），失败由上层路由熔断降级。</li>
 * </ul>
 * 接口方法失败一律抛出 {@link SearchServingException}（运行时），由上层
 * {@link SearchServingFacade} 统一降级，绝不把异常泄漏给主流程（REQ-03/08）。
 */
public interface SearchRecommendGateway {

    /** 当前实现模式：inline / remote（用于可观测与响应标注）。 */
    String mode();

    /**
     * 增强检索（屏蔽词拦截 + 关键词/同义词融合 + 实时加权 + AB 偏移 + 零结果兜底）。
     *
     * @param q      检索条件
     * @param userId 用户 ID（可空）
     */
    IPage<ItemVO> search(ItemQuery q, Long userId);

    /**
     * 零结果兜底「猜你想找」：同义词扩展 + 热搜词召回，合并去重。
     *
     * @param keyword 归一化关键词（空则纯热搜兜底）
     * @param limit   返回条数上限
     */
    List<ItemVO> guess(String keyword, int limit);

    /**
     * 首页推荐流（同城优先 + 热度衰减 + 行为加权 + 冷启动保量）。
     */
    IPage<ItemVO> feed(String city, Long userId, int page, int size);

    /**
     * 实时行为回传（view_item / favorite / order_create / pay / search），
     * 更新 60s 窗口实时兴趣，反哺搜索与推荐（REQ-05 沿用 F04 实现）。
     */
    void recordBehavior(Long userId, String event, String bizId);
}
