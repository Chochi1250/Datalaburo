package com.DataLaburo.web.config;

import org.h2.server.web.JakartaWebServlet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(JakartaWebServlet.class)
@ConditionalOnProperty(prefix = "spring.h2.console", name = "enabled", havingValue = "true")
public class H2ConsoleConfig {
	@Bean
	ServletRegistrationBean<JakartaWebServlet> h2ConsoleServlet() {
		ServletRegistrationBean<JakartaWebServlet> registration =
				new ServletRegistrationBean<>(new JakartaWebServlet(), "/h2-console/*");
		registration.setName("H2Console");
		return registration;
	}
}

