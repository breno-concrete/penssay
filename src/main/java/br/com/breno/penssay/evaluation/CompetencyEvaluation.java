package br.com.breno.penssay.evaluation;

import java.util.List;

public record CompetencyEvaluation(int number, int score, String justification, List<String> tips) {
}
