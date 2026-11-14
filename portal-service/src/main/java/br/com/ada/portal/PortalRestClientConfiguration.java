package br.com.ada.portal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class PortalRestClientConfiguration {
    @Bean("transactionRestClient")
    RestClient transactionRestClient(
            RestClient.Builder builder,
            @Value("${app.services.transaction-api-url}") String baseUrl) {
        return build(builder, baseUrl);
    }

    @Bean("analysisRestClient")
    RestClient analysisRestClient(
            RestClient.Builder builder,
            @Value("${app.services.analysis-api-url}") String baseUrl) {
        return build(builder, baseUrl);
    }

    @Bean("transactionManagementClient")
    RestClient transactionManagementClient(
            RestClient.Builder builder,
            @Value("${app.services.transaction-management-url}") String baseUrl) {
        return build(builder, baseUrl);
    }

    @Bean("analysisManagementClient")
    RestClient analysisManagementClient(
            RestClient.Builder builder,
            @Value("${app.services.analysis-management-url}") String baseUrl) {
        return build(builder, baseUrl);
    }

    private RestClient build(RestClient.Builder builder, String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
