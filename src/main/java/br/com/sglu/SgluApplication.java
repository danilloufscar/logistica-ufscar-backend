package br.com.sglu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SgluApplication {

	public static void main(String[] args) {
		SpringApplication.run(SgluApplication.class, args);
		System.out.println("Application Started");
	}

}
