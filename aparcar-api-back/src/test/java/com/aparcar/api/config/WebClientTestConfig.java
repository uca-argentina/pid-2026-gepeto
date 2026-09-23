package com.aparcar.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static com.aparcar.api.config.ApplicationConstants.TEST_ENV;

@Configuration
@Profile(TEST_ENV)
public class WebClientTestConfig {
    @Bean
    public WebClientResponseMock responseMock() {
        return new WebClientResponseMock();
    }

    @Bean
    public WebClient webClient(WebClientResponseMock responseMock) {
        return WebClient.builder()
                .exchangeFunction(req -> Mono.just(
                        ClientResponse
                                .create(responseMock.getStatus())
                                .header("Content-Type", "application/json")
                                .body(responseMock.getBody())
                                .build()
                ))
                .build();
    }

    public static class WebClientResponseMock {
        private HttpStatus status = HttpStatus.OK;
        private String body = "{}";

        public HttpStatus getStatus() {
            return status;
        }

        public String getBody() {
            return body;
        }

        public void setStatus(HttpStatus s) {
            this.status = s;
        }

        public void setBody(String b) {
            this.body = b;
        }
    }
}
