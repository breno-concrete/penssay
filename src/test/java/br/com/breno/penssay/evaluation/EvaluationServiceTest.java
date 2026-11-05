package br.com.breno.penssay.evaluation;

import br.com.breno.penssay.web.UsageKind;
import br.com.breno.penssay.web.UsageLimitException;
import br.com.breno.penssay.web.UsageLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EvaluationServiceTest {

    private static final String ORIGIN = "203.0.113.7";

    private final ChatClient chatClient = mock(ChatClient.class);
    private final UsageLimiter usageLimiter = mock(UsageLimiter.class);
    private EvaluationService service;

    @BeforeEach
    void setUp() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        service = new EvaluationService(builder,
                new ClassPathResource("prompts/evaluation.st"),
                new ClassPathResource("prompts/evaluation-user.st"),
                "test-model", new ScoreNormalizer(), usageLimiter);
    }

    // n words spread over lines, with a blank line in the middle, like a pasted essay
    private static String words(int n) {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            text.append("palavra").append(i);
            text.append(i % 10 == 0 ? "\n" : " ");
            if (i == n / 2) {
                text.append("\n\n");
            }
        }
        return text.toString();
    }

    @Test
    void aTextUnder70WordsGetsZeroWithoutCallingTheModelOrUsingTheLimit() {
        EssayEvaluation result = service.evaluate("Tema", words(69), ORIGIN);

        assertThat(result.totalScore()).isZero();
        assertThat(result.zeroReason()).isEqualTo(ZeroReason.TOO_SHORT);
        assertThat(result.competencies()).hasSize(5)
                .allSatisfy(c -> {
                    assertThat(c.score()).isZero();
                    assertThat(c.justification()).contains("menos de 70 palavras");
                });
        assertThat(result.competencies()).extracting(CompetencyEvaluation::number).containsExactly(1, 2, 3, 4, 5);
        verifyNoInteractions(chatClient, usageLimiter);
    }

    @Test
    void aTextWith70WordsReservesAPaidCallBeforeAskingTheModel() {
        // the limiter refuses, so the test also proves the model is never reached once a limit is hit
        UsageLimitException refused = new UsageLimitException(UsageKind.EVALUATION,
                UsageLimitException.Scope.ORIGIN, 60);
        doThrow(refused).when(usageLimiter).acquire(UsageKind.EVALUATION, ORIGIN);

        assertThatThrownBy(() -> service.evaluate("Tema", words(70), ORIGIN)).isSameAs(refused);

        verify(usageLimiter).acquire(UsageKind.EVALUATION, ORIGIN);
        verifyNoInteractions(chatClient);
    }

    @Test
    void countsWordsAcrossLinesAndExtraSpaces() {
        assertThat(EvaluationService.wordCount("  um  dois\n\ntrês\tquatro  ")).isEqualTo(4);
        assertThat(EvaluationService.wordCount("   ")).isZero();
    }
}
