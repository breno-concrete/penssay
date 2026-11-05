package br.com.breno.penssay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PenssayApplication {

    public static void main(String[] args) {
        SpringApplication.run(PenssayApplication.class, args);
    }
}
