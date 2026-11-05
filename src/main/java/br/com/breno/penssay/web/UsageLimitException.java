package br.com.breno.penssay.web;

/** A paid call was refused because a limit was reached; becomes HTTP 429 with Retry-After. */
public class UsageLimitException extends RuntimeException {

    public enum Scope {
        ORIGIN, // this client used up its share of the window
        DAILY   // the whole app reached the cap of the day
    }

    private final UsageKind kind;
    private final Scope scope;
    private final long retryAfterSeconds;

    public UsageLimitException(UsageKind kind, Scope scope, long retryAfterSeconds) {
        super(kind + " limit reached (" + scope + ")");
        this.kind = kind;
        this.scope = scope;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public UsageKind kind() {
        return kind;
    }

    public Scope scope() {
        return scope;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
