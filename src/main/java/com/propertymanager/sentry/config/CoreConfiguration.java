package com.propertymanager.sentry.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CoreConfiguration {

    /** Injected wherever "now" is needed, so tests can pin time. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
