package com.aparcar.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Activa {@link DiscapacidadProperties}. */
@Configuration
@EnableConfigurationProperties(DiscapacidadProperties.class)
public class DiscapacidadConfig {
}
