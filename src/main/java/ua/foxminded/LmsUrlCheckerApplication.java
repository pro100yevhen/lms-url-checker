package ua.foxminded;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LmsUrlCheckerApplication {

	public static void main(final String[] args) {
		SpringApplication.run(LmsUrlCheckerApplication.class, args);
	}

}
