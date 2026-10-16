package br.com.breno.penssay.evaluation;

import java.util.List;

public record EssayEvaluation(List<CompetencyEvaluation> competencies, int totalScore, ZeroReason zeroReason) {
}
