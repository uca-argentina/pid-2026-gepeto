package com.aparcar.api.config;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.*;

import static com.aparcar.api.config.ApplicationConstants.TEST_ENV;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
// Meta-annotation that includes the annotations below
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(TEST_ENV)
public @interface IntegrationTests {
}
