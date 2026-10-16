package br.com.breno.penssay.evaluation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScoreNormalizerTest {


    private final ScoreNormalizer scoreNormalizer;

    public ScoreNormalizerTest() {
        this.scoreNormalizer = new ScoreNormalizer();
    }


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
}
