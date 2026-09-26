package com.idlefish.trade.file.service;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.util.SignUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 阿里云 OSS 对象存储（idlefish.oss.mock=false 真实实现）。
 * 采用 OSS 签名版本 V1（HMAC-SHA1）直接调用 PutObject REST 接口，零 SDK 依赖。
 * 生产建议开启 HTTPS、配置 CDN 与防盗链，并结合 STS 临时凭证下发前端直传。
 */
@Service
public class OssStorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(OssStorageServiceImpl.class);

    private final IdlefishProperties props;
    private final RestTemplate restTemplate;

    public OssStorageServiceImpl(IdlefishProperties props, RestTemplate restTemplate) {
        this.props = props;
        this.restTemplate = restTemplate;
    }

    @Override
    public String store(String objectKey, InputStream data, String contentType) {
        IdlefishProperties.Oss oss = props.getOss();
        byte[] body = readAll(data);
        String date = rfc1123();
        String cType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;

        // StringToSign = VERB + \n + Content-MD5 + \n + Content-Type + \n + Date + \n + CanonicalizedResource
        String resource = "/" + oss.getBucket() + "/" + objectKey;
        String stringToSign = "PUT\n\n" + cType + "\n" + date + "\n" + resource;
        String signature = SignUtils.hmacSha1(stringToSign, oss.getSecretKey());
        String authorization = "OSS " + oss.getAccessKey() + ":" + signature;

        String url = "https://" + oss.getBucket() + "." + oss.getEndpoint() + "/" + objectKey;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Date", date);
        headers.set("Authorization", authorization);
        headers.setContentType(MediaType.parseMediaType(cType));
        try {
            restTemplate.put(url, new HttpEntity<>(body, headers));
        } catch (Exception e) {
            log.error("OSS PutObject 失败 key={}", objectKey, e);
            throw new BizException(Code.FILE_ERROR, "OSS 上传失败");
        }
        return getAccessUrl(objectKey);
    }

    @Override
    public String getAccessUrl(String objectKey) {
        IdlefishProperties.Oss oss = props.getOss();
        if (oss.getBaseUrl() != null && !oss.getBaseUrl().isBlank()) {
            String base = oss.getBaseUrl();
            if (!base.endsWith("/")) {
                base = base + "/";
            }
            return base + objectKey;
        }
        return "https://" + oss.getBucket() + "." + oss.getEndpoint() + "/" + objectKey;
    }

    @Override
    public boolean delete(String objectKey) {
        IdlefishProperties.Oss oss = props.getOss();
        String date = rfc1123();
        String resource = "/" + oss.getBucket() + "/" + objectKey;
        String stringToSign = "DELETE\n\n\n" + date + "\n" + resource;
        String signature = SignUtils.hmacSha1(stringToSign, oss.getSecretKey());
        String authorization = "OSS " + oss.getAccessKey() + ":" + signature;
        String url = "https://" + oss.getBucket() + "." + oss.getEndpoint() + "/" + objectKey;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Date", date);
        headers.set("Authorization", authorization);
        try {
            restTemplate.delete(url, new HttpEntity<>(headers));
            return true;
        } catch (Exception e) {
            log.error("OSS DeleteObject 失败 key={}", objectKey, e);
            return false;
        }
    }

    private byte[] readAll(InputStream in) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        try {
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
        } catch (IOException e) {
            throw new BizException(Code.FILE_ERROR, "文件读取失败");
        }
        return bos.toByteArray();
    }

    /** RFC 1123 GMT 时间，OSS 签名要求。 */
    private String rfc1123() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }
}
