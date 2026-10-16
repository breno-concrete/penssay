package br.com.breno.penssay.evaluation;

import java.util.List;

public record ModelCompetency(int number, double score, String justification, List<String> tips) {
}
