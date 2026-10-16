package br.com.breno.penssay.evaluation;

import java.util.List;

// justification comes before score so the model reasons before it picks a number (JSON is generated in field order)
public record ModelCompetency(int number, String justification, double score, List<String> tips) {
}
