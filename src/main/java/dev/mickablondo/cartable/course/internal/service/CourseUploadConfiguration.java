package dev.mickablondo.cartable.course.internal.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CourseUploadProperties.class)
class CourseUploadConfiguration {
}
