package br.com.breno.penssay.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cost protection limits from {@code penssay.limits.*} in application.properties.
 * Every paid model call is checked against them first (specs/004-launch-protection).
 */
@ConfigurationProperties("penssay.limits")
public record LimitsProperties(
        boolean enabled,
        Duration window,
        int evaluationsPerOrigin,
        int transcriptionsPerOrigin,
        int evaluationsPerDay,
        int transcriptionsPerDay,
        int maxTextChars,
        int maxThemeChars) {

    public int perOrigin(UsageKind kind) {
        return kind == UsageKind.EVALUATION ? evaluationsPerOrigin : transcriptionsPerOrigin;
    }

    public int perDay(UsageKind kind) {
        return kind == UsageKind.EVALUATION ? evaluationsPerDay : transcriptionsPerDay;
    }
}
