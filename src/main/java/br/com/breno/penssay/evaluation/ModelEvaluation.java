package br.com.breno.penssay.evaluation;

import java.util.List;

public record ModelEvaluation(boolean fullyOffTopic, boolean notArgumentative, List<ModelCompetency> competencies) {
}
