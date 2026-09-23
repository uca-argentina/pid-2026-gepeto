package com.aparcar.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
public class WebConfig {

    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        // This filter is used to handle forwarded headers correctly,
        // especially in reverse proxy setups like Azure.
        return new ForwardedHeaderFilter();
    }
}
