package de.ebf.security.jwt.testapp.configurations;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

import de.ebf.security.jwt.JWTPermissionsConfig;
import de.ebf.security.jwt.testapp.controllers.TestController;

@Configuration
@EnableAutoConfiguration
@PropertySource(value = { "classpath:application.properties" })
@Import(JWTPermissionsConfig.class)
@EntityScan(basePackageClasses = { TestController.class })
public class NoPermissionModelConfiguration {

}