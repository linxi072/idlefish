package com.idlefish.trade.notify.channel;

import com.idlefish.trade.common.IdlefishProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 短信渠道真实实现（真实短信渠道（fail-closed，凭据缺失回落日志））。
 * 当前为 fail-closed 骨架：真实短信网关调用需注入凭据（签名/模板/密钥）后在此实现；
 * 配置缺失、未启用或发送异常时回落日志，绝不外抛，避免阻断主业务流程。
 */
@Slf4j
@Component
public class RealSmsNotifyChannelImpl implements SmsNotifyChannel {

    private final IdlefishProperties.Notify notify;

    public RealSmsNotifyChannelImpl(IdlefishProperties idlefishProperties) {
        this.notify = idlefishProperties.getNotify();
    }

    @Override
    public ChannelType type() {
        return ChannelType.SMS;
    }

    @Override
    public void send(NotifyMessage msg) {
        try {
            IdlefishProperties.Notify.Sms sms = notify == null ? null : notify.getSms();
            if (sms == null || !sms.isEnabled()) {
                log.warn("[notify:sms:real] 短信未启用或配置缺失，回落日志 userId={} type={}",
                        msg.getUserId(), msg.getType());
                return;
            }
            // TODO(F-13): 调用真实短信网关（如阿里云/腾讯云短信），按 sms 配置签名/模板/密钥发送。
            log.warn("[notify:sms:real] 真实短信网关未接入（F-13 待接凭据），回落日志 userId={} type={} content={}",
                    msg.getUserId(), msg.getType(), msg.getContent());
        } catch (Exception e) {
            log.warn("[notify:sms:real] 发送异常(回落) userId={} type={}", msg.getUserId(), msg.getType(), e);
        }
    }
}
