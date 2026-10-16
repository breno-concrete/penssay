package br.com.breno.penssay.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScoreNormalizerTest {


    private final ScoreNormalizer scoreNormalizer = new ScoreNormalizer();

    @ParameterizedTest(name = "Score: {0} -> NormalizedScore: {1}")
    @CsvSource({
            "0, 0",
            "100, 120",
            "150, 160",
            "130, 120",
            "-40, 0",
            "1000, 200",
            "20, 40",
            "120, 120",
            "200, 200",
            "180, 200",
            "152.5, 160",
            "230, 200"
    })
    void testNormalizeScore(double score, int expectedNormalizedScore) {
        assertEquals(expectedNormalizedScore, scoreNormalizer.normalize(score));
    }

    @Test
    void sortsCompetenciesByNumber() {
        List<ModelCompetency> input = List.of(
                competency(3, 130),
                competency(1, 150),
                competency(5, 200),
                competency(2, 20),
                competency(4, 180)
        );

        List<CompetencyEvaluation> result = scoreNormalizer.normalizeAll(input);

        List<Integer> numbers = result.stream()
                .map(c -> c.number())
                .toList();

        assertEquals(List.of(1, 2, 3, 4, 5), numbers);
    }


    private ModelCompetency competency(int number, double score) {
        return new ModelCompetency(number, score, "qualquer", List.of());
    }
}
