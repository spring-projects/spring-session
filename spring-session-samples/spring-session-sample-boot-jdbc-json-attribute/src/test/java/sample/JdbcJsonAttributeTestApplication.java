package sample;

import org.springframework.boot.SpringApplication;

public class JdbcJsonAttributeTestApplication {

	public static void main(String[] args) {
		SpringApplication.from(JdbcJsonAttributeApplication::main).with(TestContainersConfig.class).run(args);
	}

}
