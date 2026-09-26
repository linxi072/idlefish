package com.idlefish.trade.trade.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import com.idlefish.trade.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RealWechatEscrowServiceImplTest {

    @Mock
    private IdlefishProperties props;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private PayOrderMapper payOrderMapper;
    @Mock
    private UserService userService;

    private RealWechatEscrowServiceImpl service;
    private IdlefishProperties.Pay configuredPay;

    @BeforeEach
    void setUp() throws Exception {
        // 生成合法 PKCS#8 测试私钥，使微信 v3 签名可正常执行（无需真实商户号）
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";

        configuredPay = mock(IdlefishProperties.Pay.class);
        when(configuredPay.getMchid()).thenReturn("mch");
        when(configuredPay.getAppid()).thenReturn("appid");
        when(configuredPay.getApiV3Key()).thenReturn("v3key");
        when(configuredPay.getSerialNo()).thenReturn("serial");
        when(configuredPay.getPrivateKey()).thenReturn(pem);
        when(configuredPay.getGateway()).thenReturn("https://api.mch.weixin.qq.com");
        when(configuredPay.getTransferSceneId()).thenReturn("1000");
        when(props.getPay()).thenReturn(configuredPay);

        // 手动构造，注入真实 ObjectMapper（@InjectMocks 不会自动实例化非 mock 构造参数）
        service = new RealWechatEscrowServiceImpl(props, restTemplate, new ObjectMapper(),
                payOrderMapper, userService);
    }

    @Test
    void transfer_notConfigured_throwsConfigMissing() {
        when(configuredPay.getPrivateKey()).thenReturn(null); // 配置缺失 → 硬网关不实际出款
        BizException ex = assertThrows(BizException.class, () -> service.transfer("WD1", 1000L, "openid"));
        assertEquals(Code.CONFIG_MISSING.getCode(), ex.getCode());
    }

    @Test
    void transfer_success_returnsBillNo() {
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"state\":\"PROCESSING\"}", HttpStatus.OK));
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"transfer_bill_no\":\"BILL123\"}");
        String bill = service.transfer("WD1", 1000L, "openid");
        assertEquals("BILL123", bill);
        verify(restTemplate).postForObject(anyString(), any(), eq(String.class));
    }

    @Test
    void transfer_idempotent_noPostWhenAlreadySuccess() {
        // 先查命中 SUCCESS → 直接返回幂等号，绝不二次出款
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"state\":\"SUCCESS\"}", HttpStatus.OK));
        String bill = service.transfer("WD1", 1000L, "openid");
        assertEquals("WD1", bill);
        verify(restTemplate, never()).postForObject(anyString(), any(), eq(String.class));
    }

    @Test
    void transfer_channelFailure_throwsFundTransferFailed() {
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"state\":\"PROCESSING\"}", HttpStatus.OK));
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"errcode\":\"FAIL\"}"); // 无 transfer_bill_no
        BizException ex = assertThrows(BizException.class, () -> service.transfer("WD1", 1000L, "openid"));
        assertEquals(Code.FUND_TRANSFER_FAILED.getCode(), ex.getCode());
    }

    @Test
    void queryTransfer_success() {
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"state\":\"SUCCESS\"}", HttpStatus.OK));
        assertTrue(service.queryTransfer("WD1"));
    }

    @Test
    void queryTransfer_notSuccess() {
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"state\":\"PROCESSING\"}", HttpStatus.OK));
        assertFalse(service.queryTransfer("WD1"));
    }

    @Test
 void queryTransfer_exception_returnsFalse() {
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("network"));
        assertFalse(service.queryTransfer("WD1"));
    }
}
