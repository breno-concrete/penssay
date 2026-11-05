package br.com.breno.penssay.web;

import br.com.breno.penssay.evaluation.EvaluationFormatException;
import br.com.breno.penssay.evaluation.EvaluationUnavailableException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;

/**
 * Turns every failure into {@code {"message": "..."}} in Portuguese, with the status of
 * specs/004-launch-protection/contracts/api-errors.md. Responses never carry internal details, and logs record only
 * the exception type: a provider message may echo part of the request, and essay texts must never reach a log.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String TRANSCRIPTIONS_PATH = "/api/transcriptions";

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> invalidRequest(InvalidRequestException e) {
        return respond(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, "Pedido inválido. Envie o tema e o texto da redação.");
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> missingPhoto(Exception e) {
        return respond(HttpStatus.BAD_REQUEST, "Envie a foto da redação.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> wrongContentType(HttpMediaTypeNotSupportedException e,
                                                          HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, isTranscription(request)
                ? "Envie a foto da redação."
                : "Pedido inválido. Envie o tema e o texto da redação.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> photoTooLarge(MaxUploadSizeExceededException e) {
        return respond(HttpStatus.CONTENT_TOO_LARGE, "A foto é muito grande. Envie uma foto de até 10 MB.");
    }

    // a broken multipart body (not a size problem, which the handler above catches first)
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> brokenUpload(MultipartException e) {
        return respond(HttpStatus.BAD_REQUEST, "Envie a foto da redação.");
    }

    @ExceptionHandler(UsageLimitException.class)
    public ResponseEntity<ErrorResponse> limitReached(UsageLimitException e) {
        boolean photo = e.kind() == UsageKind.TRANSCRIPTION;
        String message = switch (e.scope()) {
            case ORIGIN -> photo
                    ? "Você atingiu o limite de fotos por agora. Tente de novo mais tarde."
                    : "Você atingiu o limite de correções por agora. Tente de novo mais tarde.";
            case DAILY -> photo
                    ? "O Penssay atingiu o limite de leituras de foto de hoje. Volte amanhã."
                    : "O Penssay atingiu o limite de correções de hoje. Volte amanhã.";
        };
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                .body(new ErrorResponse(message));
    }

    // the model answered twice in a shape we cannot use: never show a partial or invented grade
    @ExceptionHandler({EvaluationFormatException.class, JacksonException.class, OpenAIInvalidDataException.class})
    public ResponseEntity<ErrorResponse> badModelAnswer(Exception e, HttpServletRequest request) {
        log.warn("Model answer could not be used: {}", e.getClass().getSimpleName());
        return respond(HttpStatus.BAD_GATEWAY, isTranscription(request)
                ? "Não conseguimos ler a foto. Tente de novo."
                : "Não conseguimos concluir a correção. Tente de novo.");
    }

    // provider down, connection lost, key refused or provider rate limit
    @ExceptionHandler({EvaluationUnavailableException.class, OpenAIIoException.class, OpenAIServiceException.class})
    public ResponseEntity<ErrorResponse> providerUnavailable(Exception e, HttpServletRequest request) {
        String status = e instanceof OpenAIServiceException service ? " (status " + service.statusCode() + ")" : "";
        log.warn("Model provider unavailable: {}{}", e.getClass().getSimpleName(), status);
        return respond(HttpStatus.SERVICE_UNAVAILABLE, isTranscription(request)
                ? "O serviço de leitura está indisponível no momento. Tente de novo em alguns minutos."
                : "O serviço de correção está indisponível no momento. Tente de novo em alguns minutos.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NoResourceFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "Página não encontrada.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> wrongMethod(HttpRequestMethodNotSupportedException e) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("Unexpected error: {}", e.getClass().getName());
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Algo deu errado do nosso lado. Tente de novo.");
    }

    private static boolean isTranscription(HttpServletRequest request) {
        return request.getRequestURI().startsWith(TRANSCRIPTIONS_PATH);
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }
}
