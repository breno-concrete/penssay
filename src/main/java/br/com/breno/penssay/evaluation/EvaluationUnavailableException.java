package br.com.breno.penssay.evaluation;

public class EvaluationUnavailableException extends RuntimeException {

    public EvaluationUnavailableException(String message) {
        super(message);
    }

    public EvaluationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
