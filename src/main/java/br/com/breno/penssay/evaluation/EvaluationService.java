package br.com.breno.penssay.evaluation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class EvaluationService {

    private static final int COMPETENCY_COUNT = 5;
    // caps each answer so a repetition loop fails fast and a runaway answer cannot burn credits
    private static final int MAX_ANSWER_TOKENS = 1024;
    private static final double RETRY_TEMPERATURE = 0.4;

    private final ChatClient chatClient;
    private final Resource systemPrompt;
    private final Resource userPrompt;
    private final List<String> rubrics; // rubrics.get(0) is competency 1
    private final String model;
    private final ScoreNormalizer scoreNormalizer;

    public EvaluationService(
            ChatClient.Builder builder,
            @Value("classpath:/prompts/evaluation.st") Resource systemPrompt,
            @Value("classpath:/prompts/evaluation-user.st") Resource userPrompt,
            @Value("${penssay.evaluation.model}") String model,
            ScoreNormalizer scoreNormalizer) throws IOException {

        this.chatClient = builder.build();
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.model = model;
        this.scoreNormalizer = scoreNormalizer;

        this.rubrics = new ArrayList<>();
        for (int number = 1; number <= COMPETENCY_COUNT; number++) {
            Resource rubric = new ClassPathResource("prompts/competency-" + number + ".st");
            rubrics.add(rubric.getContentAsString(StandardCharsets.UTF_8));
        }
    }

    public EssayEvaluation evaluate(String theme, String text) {
        // one call per competency: a small model grades one rubric at a time much more consistently than all five at once
        List<ModelCompetency> answers = new ArrayList<>();
        for (int number = 1; number <= COMPETENCY_COUNT; number++) {
            ModelCompetencyAnswer answer = evaluateCompetency(number, theme, text);
            answers.add(new ModelCompetency(number, answer.justification(), answer.score(), answer.tips()));
        }

        List<CompetencyEvaluation> competencies = scoreNormalizer.normalizeAll(answers);

        return new EssayEvaluation(competencies, scoreNormalizer.total(competencies), null);
    }

    private ModelCompetencyAnswer evaluateCompetency(int number, String theme, String text) {
        try {
            return askModel(number, theme, text, 0.0);
        } catch (JacksonException e) {
            // the model sometimes falls into a repetition loop (the answer hits the token cap and the JSON comes cut).
            // At temperature 0 the loop repeats every time, so the single retry samples a little to escape it
            return askModel(number, theme, text, RETRY_TEMPERATURE);
        }
    }

    private ModelCompetencyAnswer askModel(int number, String theme, String text, double temperature) {
        return chatClient.prompt()
                .system(s -> s.text(systemPrompt).param("rubric", rubrics.get(number - 1))) // general rules + this competency's rubric
                .user(u -> u.text(userPrompt).param("theme", theme).param("text", text))
                .options(ChatOptions.builder().model(model).maxTokens(MAX_ANSWER_TOKENS).temperature(temperature))
                .call()
                .entity(ModelCompetencyAnswer.class, spec -> spec.useProviderStructuredOutput()); // the provider is forced to answer in the record's JSON schema
    }
}
