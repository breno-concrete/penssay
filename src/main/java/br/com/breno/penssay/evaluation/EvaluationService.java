package br.com.breno.penssay.evaluation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EvaluationService {

    private final ChatClient chatClient;
    private final Resource systemPrompt;
    private final Resource userPrompt;
    private final String model;
    private final ScoreNormalizer scoreNormalizer;

    public EvaluationService(
            ChatClient.Builder builder,
            @Value("classpath:/prompts/evaluation.st") Resource systemPrompt,
            @Value("classpath:/prompts/evaluation-user.st") Resource userPrompt,
            @Value("${penssay.evaluation.model}") String model,
            ScoreNormalizer scoreNormalizer) {

        this.chatClient = builder.build();
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.model = model;
        this.scoreNormalizer = scoreNormalizer;
    }

    public EssayEvaluation evaluate(String theme, String text) {
        ModelEvaluation response = chatClient.prompt()
                .system(systemPrompt) // the C1-C5 rubric
                .user(u -> u.text(userPrompt).param("theme", theme).param("text", text))
                .options(OllamaChatOptions.builder().model(model))
                .call()
                .entity(ModelEvaluation.class, spec -> spec.useProviderStructuredOutput()); // Ollama is forced to answer in the record's JSON schema

        List<CompetencyEvaluation> competencies = scoreNormalizer.normalizeAll(response.competencies());

        return new EssayEvaluation(competencies, scoreNormalizer.total(competencies), null);
    }
}
