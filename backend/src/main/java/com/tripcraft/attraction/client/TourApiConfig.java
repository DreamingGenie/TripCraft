package com.tripcraft.attraction.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class TourApiConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
