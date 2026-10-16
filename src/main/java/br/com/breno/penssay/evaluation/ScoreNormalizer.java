package br.com.breno.penssay.evaluation;

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
}
