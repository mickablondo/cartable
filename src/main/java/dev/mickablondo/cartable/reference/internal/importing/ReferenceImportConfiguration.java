package dev.mickablondo.cartable.reference.internal.importing;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReferenceManifestProperties.class)
class ReferenceImportConfiguration {
}
