package com.idlefish.trade.notify.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 短信渠道 Mock 实现（默认启用）：仅打印日志，不真正发短信。离线/演示安全。
 */
@Slf4j
@Primary
@Component
@ConditionalOnProperty(name = "idlefish.notify.sms-mode", havingValue = "mock", matchIfMissing = true)
public class MockSmsNotifyChannelImpl implements SmsNotifyChannel {

    @Override
    public ChannelType type() {
        return ChannelType.SMS;
    }

    @Override
    public void send(NotifyMessage msg) {
        log.info("[notify:sms:mock] 模拟短信 userId={} type={} title={} -> {}",
                msg.getUserId(), msg.getType(), msg.getTitle(), msg.getContent());
    }
}
