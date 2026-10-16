package br.com.breno.penssay.evaluation;

import java.util.List;

// what the model answers for one competency; the number is not here because the service already knows which one it asked
// justification comes before score so the model reasons before it picks a number (JSON is generated in field order)
public record ModelCompetencyAnswer(String justification, double score, List<String> tips) {
}
