package com.DataLaburo.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/plugins/**")
                .allowedOriginPatterns("chrome-extension://*", "http://localhost:*", "http://127.0.0.1:*", "https://localhost:*")
                .allowedMethods("*")
                .allowedHeaders("*");

        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*");
    }
}
