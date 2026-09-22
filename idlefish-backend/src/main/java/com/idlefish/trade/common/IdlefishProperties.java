package com.idlefish.trade.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 读取 application.yml 中 idlefish.* 业务配置。
 */
@Configuration
@ConfigurationProperties(prefix = "idlefish")
@Data
public class IdlefishProperties {

    private Jwt jwt = new Jwt();
    private boolean payMock = true;
    private boolean searchMock = true;
    private boolean mqMock = true;
    private File file = new File();

    @Data
    public static class Jwt {
        private String secret;
        private long expireSeconds = 86400L;
        private long refreshSeconds = 2592000L;
    }

    @Data
    public static class File {
        /** 是否使用伪造 URL（不落盘）。演示默认 false，真实落盘并由后端静态映射 /uploads 提供访问。 */
        private boolean mock = false;
        /** 访问基址，需与静态资源映射路径一致。 */
        private String baseUrl = "/uploads/";
    }
}
