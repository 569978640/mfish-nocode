package cn.com.mfish.bfces.pbi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * @description: PBI专用RestTemplate配置。
 * 信任策略只作用于本RestTemplate建立的连接，不再调用HttpsURLConnection的全局默认设置，
 * 避免污染整个JVM的HTTPS行为；RestTemplate在容器中单例复用，无需每次请求重建。
 * @author: mfish
 * @date: 2026-10-13
 * @version: V1.1.0
 */
@Configuration
public class PbiRestTemplateConfig {

    /**
     * 信任所有主机名的校验器
     */
    private static final javax.net.ssl.HostnameVerifier ALL_HOSTS_TRUSTED = (hostname, session) -> true;

    /**
     * 构建PBI专用的RestTemplate
     *
     * @param properties PBI配置属性
     * @return 配置好超时、UTF-8编码、宽松SSL策略的RestTemplate
     */
    @Bean("pbiRestTemplate")
    public RestTemplate pbiRestTemplate(PbiProperties properties) {
        SSLContext trustAllSslContext = createTrustAllSslContext();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                if (connection instanceof HttpsURLConnection httpsConnection) {
                    // 仅对PBI自身连接生效，不修改JVM全局默认SSLSocketFactory与HostnameVerifier
                    httpsConnection.setSSLSocketFactory(trustAllSslContext.getSocketFactory());
                    httpsConnection.setHostnameVerifier(ALL_HOSTS_TRUSTED);
                }
            }
        };
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        RestTemplate restTemplate = new RestTemplate(requestFactory);
        // 将字符串转换器统一替换为UTF-8字符集，解决中文响应乱码问题
        List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
        converters.replaceAll(converter -> converter instanceof StringHttpMessageConverter
                ? new StringHttpMessageConverter(StandardCharsets.UTF_8)
                : converter);
        return restTemplate;
    }

    /**
     * 创建信任所有证书的SSLContext（只初始化一次，随RestTemplate单例复用）
     *
     * @return 信任所有证书的SSLContext
     */
    private SSLContext createTrustAllSslContext() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                            // PBI内网自签名证书场景，客户端不做证书链校验
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                            // PBI内网自签名证书场景，服务端不做证书链校验
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("初始化PBI信任所有证书的SSLContext失败", e);
        }
    }
}
