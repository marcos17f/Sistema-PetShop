package br.com.marcoslab.petshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PetshopApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetshopApiApplication.class, args);
    }
}
