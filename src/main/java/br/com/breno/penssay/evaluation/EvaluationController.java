package br.com.breno.penssay.evaluation;

import br.com.breno.penssay.web.InvalidRequestException;
import br.com.breno.penssay.web.LimitsProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final LimitsProperties limits;

    public EvaluationController(EvaluationService evaluationService, LimitsProperties limits) {
        this.evaluationService = evaluationService;
        this.limits = limits;
    }

    @PostMapping
    public EssayEvaluation evaluate(@RequestBody EvaluationRequest request, HttpServletRequest http) {
        String theme = request.theme();
        String text = request.text();
        if (theme == null || theme.isBlank()) {
            throw new InvalidRequestException("Informe o tema da redação.");
        }
        if (text == null || text.isBlank()) {
            throw new InvalidRequestException("Envie o texto da redação.");
        }
        // checked before the model is called: a huge text would cost many times a real essay
        if (theme.length() > limits.maxThemeChars()) {
            throw new InvalidRequestException("O tema pode ter no máximo %,d caracteres."
                    .formatted(limits.maxThemeChars()).replace(',', '.'));
        }
        if (text.length() > limits.maxTextChars()) {
            throw new InvalidRequestException("A redação pode ter no máximo %,d caracteres."
                    .formatted(limits.maxTextChars()).replace(',', '.'));
        }
        // with server.forward-headers-strategy=native this is the client's address, not the host's proxy
        return evaluationService.evaluate(theme, text, http.getRemoteAddr());
    }
}
