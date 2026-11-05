package br.com.breno.penssay.evaluation;

import br.com.breno.penssay.web.LimitsProperties;
import br.com.breno.penssay.web.UsageKind;
import br.com.breno.penssay.web.UsageLimitException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.core.exc.StreamReadException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvaluationController.class)
@EnableConfigurationProperties(LimitsProperties.class) // the slice skips @ConfigurationPropertiesScan
@ExtendWith(OutputCaptureExtension.class)
class EvaluationControllerTest {

    private static final String SECRET_ESSAY = "Texto secreto da redação que nunca pode aparecer em log";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private LimitsProperties limits;

    @MockitoBean
    private EvaluationService service;

    private ResultActions send(String theme, String text) throws Exception {
        String body = "{\"theme\":" + json(theme) + ",\"text\":" + json(text) + "}";
        return mvc.perform(post("/api/evaluations").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private ResultActions failWith(RuntimeException e) throws Exception {
        // doThrow, not when(...): a second stub in the same test must not call the already failing mock
        doThrow(e).when(service).evaluate(anyString(), anyString(), anyString());
        return send("Tema", SECRET_ESSAY);
    }

    // --- US1: sizes and limits ---

    @Test
    void refusesATextAboveTheMaximumWithoutCallingTheService() throws Exception {
        send("Tema", "a".repeat(limits.maxTextChars() + 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A redação pode ter no máximo 6.000 caracteres."));
        verifyNoInteractions(service);
    }

    @Test
    void refusesAThemeAboveTheMaximumWithoutCallingTheService() throws Exception {
        send("t".repeat(limits.maxThemeChars() + 1), "texto")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O tema pode ter no máximo 300 caracteres."));
        verifyNoInteractions(service);
    }

    @Test
    void acceptsTextAndThemeExactlyAtTheMaximum() throws Exception {
        when(service.evaluate(anyString(), anyString(), anyString()))
                .thenReturn(new EssayEvaluation(List.of(), 0, null));

        send("t".repeat(limits.maxThemeChars()), "a".repeat(limits.maxTextChars())).andExpect(status().isOk());
    }

    @Test
    void aReachedLimitBecomes429WithRetryAfter() throws Exception {
        failWith(new UsageLimitException(UsageKind.EVALUATION, UsageLimitException.Scope.ORIGIN, 1200))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1200"))
                .andExpect(jsonPath("$.message")
                        .value("Você atingiu o limite de correções por agora. Tente de novo mais tarde."));
    }

    @Test
    void theDailyCapHasItsOwnMessage() throws Exception {
        failWith(new UsageLimitException(UsageKind.EVALUATION, UsageLimitException.Scope.DAILY, 3600))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("O Penssay atingiu o limite de correções de hoje. Volte amanhã."));
    }

    @Test
    void theOriginIsTheConnectionAddressNotAHeaderTheClientSends() throws Exception {
        when(service.evaluate(anyString(), anyString(), anyString()))
                .thenReturn(new EssayEvaluation(List.of(), 0, null));

        mvc.perform(post("/api/evaluations")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        })
                        .header("X-Forwarded-For", "1.2.3.4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"Tema\",\"text\":\"texto\"}"))
                .andExpect(status().isOk());

        verify(service).evaluate("Tema", "texto", "203.0.113.9");
    }

    // --- US2: empty input ---

    @Test
    void refusesAMissingOrBlankThemeOrText() throws Exception {
        send(null, "texto").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Informe o tema da redação."));
        send("   ", "texto").andExpect(status().isBadRequest());
        send("Tema", " \n ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Envie o texto da redação."));
        verifyNoInteractions(service);
    }

    // --- US3: every failure has a clear message, no details, no essay in the log ---

    @Test
    void aBodyThatIsNotJsonIs400WithoutDetails() throws Exception {
        mvc.perform(post("/api/evaluations").contentType(MediaType.APPLICATION_JSON).content("isto não é json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Pedido inválido. Envie o tema e o texto da redação."))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void anUnusableModelAnswerIs502(CapturedOutput output) throws Exception {
        failWith(new EvaluationFormatException("competency 3 missing"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Não conseguimos concluir a correção. Tente de novo."));
        failWith(new StreamReadException(null, "cut JSON: " + SECRET_ESSAY))
                .andExpect(status().isBadGateway());
        assertThat(output.getAll()).doesNotContain(SECRET_ESSAY);
    }

    @Test
    void aProviderThatIsDownOrRefusesTheKeyIs503(CapturedOutput output) throws Exception {
        failWith(new OpenAIIoException("connection reset while sending " + SECRET_ESSAY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("O serviço de correção está indisponível no momento. Tente de novo em alguns minutos."));

        UnauthorizedException refusedKey = mock(UnauthorizedException.class);
        when(refusedKey.statusCode()).thenReturn(401);
        when(refusedKey.getMessage()).thenReturn("401 invalid key, request: " + SECRET_ESSAY);
        failWith(refusedKey).andExpect(status().isServiceUnavailable());

        assertThat(output.getAll()).doesNotContain(SECRET_ESSAY);
    }

    @Test
    void anUnexpectedErrorIs500WithAGenericMessage(CapturedOutput output) throws Exception {
        failWith(new IllegalStateException("boom near " + SECRET_ESSAY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Algo deu errado do nosso lado. Tente de novo."))
                .andExpect(jsonPath("$.length()").value(1));
        assertThat(output.getAll()).doesNotContain(SECRET_ESSAY);
    }
}
