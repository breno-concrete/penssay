package br.com.breno.penssay.evaluation;

import java.util.List;

public class ScoreNormalizer {

    public int normalize(double score) {
        double rounded = Math.floor(score / 40.0 + 0.5) * 40;
        if(rounded > 200){
            return 200;
        } else if(rounded < 0){
            return 0;
        } else {
            return (int) rounded;
        }
    }

    public List<CompetencyEvaluation> normalizeAll(List<ModelCompetency> competencies) {
        CompetencyEvaluation[] sorted = new CompetencyEvaluation[5];

        for (ModelCompetency competency : competencies) {
            int index = competency.number() - 1;
            sorted[index] = new CompetencyEvaluation(
                    competency.number(),
                    normalize(competency.score()),
                    competency.justification(),
                    competency.tips());
        }

        return List.of(sorted);
    }
}
