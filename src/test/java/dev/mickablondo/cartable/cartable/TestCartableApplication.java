package dev.mickablondo.cartable.cartable;

import org.springframework.boot.SpringApplication;

public class TestCartableApplication {

	public static void main(String[] args) {
		SpringApplication.from(CartableApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
