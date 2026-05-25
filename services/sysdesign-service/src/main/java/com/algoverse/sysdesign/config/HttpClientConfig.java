package com.algoverse.sysdesign.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

    @Value("${ai-service.base-url:http://localhost:8090}")
    private String aiServiceBaseUrl;

    @Bean("aiServiceRestClient")
    public RestClient aiServiceRestClient() {
        return RestClient.builder()
            .baseUrl(aiServiceBaseUrl)
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build();
    }
}
