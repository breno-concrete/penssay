package br.com.breno.penssay.evaluation;

// answer of the screening call that runs before grading (US2): any true means every score is zero
public record ModelScreening(boolean fullyOffTopic, boolean notArgumentative) {
}
