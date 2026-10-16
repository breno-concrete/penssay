package br.com.breno.penssay.evaluation;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
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
        if (competencies == null || competencies.size() != 5) {
            throw invalidFormat();
        }

        CompetencyEvaluation[] sorted = new CompetencyEvaluation[5];

        for (ModelCompetency competency : competencies) {
            int index = competency.number() - 1;
            if (index < 0 || index >= 5 || sorted[index] != null) {
                throw invalidFormat();
            }
            sorted[index] = new CompetencyEvaluation(
                    competency.number(),
                    normalize(competency.score()),
                    competency.justification(),
                    competency.tips());
        }
        return List.of(sorted);
    }

    public int total(List<CompetencyEvaluation> competencies) {
        int total = 0;
        for (CompetencyEvaluation competency : competencies) {
            total += competency.score();
        }
        return total;
    }

    private EvaluationFormatException invalidFormat() {
        return new EvaluationFormatException("Não foi possível corrigir a redação agora. Tente de novo.");
    }
}
