package com.magizhchi.share;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.magizhchi.share.config.FileProperties;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(FileProperties.class)
public class MagizhchiShareApplication {
    public static void main(String[] args) {
        SpringApplication.run(MagizhchiShareApplication.class, args);
    }
}
