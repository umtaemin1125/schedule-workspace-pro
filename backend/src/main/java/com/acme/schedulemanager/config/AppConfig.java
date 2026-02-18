package com.acme.schedulemanager.config;

import com.acme.schedulemanager.security.JwtProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationPropertiesScan(basePackageClasses = JwtProperties.class)
public class AppConfig {
}
