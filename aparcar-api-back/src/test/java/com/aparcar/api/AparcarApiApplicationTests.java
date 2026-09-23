package com.aparcar.api;

import com.aparcar.api.config.WebClientTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static com.aparcar.api.config.ApplicationConstants.TEST_ENV;

@SpringBootTest
@ActiveProfiles(TEST_ENV)
@Import(WebClientTestConfig.class)
class AparcarApiApplicationTests {

    @Test
    void contextLoads() {
    }

}
