package com.aparcar.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Activa {@link PuntosProperties}. */
@Configuration
@EnableConfigurationProperties(PuntosProperties.class)
public class PuntosConfig {
}
