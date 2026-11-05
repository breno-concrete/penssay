package br.com.breno.penssay.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    // injected instead of calling Instant.now(), so tests can move time forward without sleeping
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
