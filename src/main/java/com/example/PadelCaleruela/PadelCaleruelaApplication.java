package com.example.PadelCaleruela;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync   // 👈 habilita soporte para @Async
public class PadelCaleruelaApplication {

	public static void main(String[] args) {
		SpringApplication.run(PadelCaleruelaApplication.class, args);

	}

}
