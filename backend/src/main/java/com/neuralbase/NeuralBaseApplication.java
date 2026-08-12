package com.neuralbase;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("com.neuralbase.mapper")
public class NeuralBaseApplication {
    public static void main(String[] args) {
        SpringApplication.run(NeuralBaseApplication.class, args);
    }
}
