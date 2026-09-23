package com.aparcar.api.config;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.*;

import static com.aparcar.api.config.ApplicationConstants.TEST_ENV;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
// Meta-annotation that includes the three annotations below
@ActiveProfiles(TEST_ENV)
@ExtendWith(MockitoExtension.class)
public @interface UnitTests {
}
