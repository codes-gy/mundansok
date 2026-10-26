package dev.codesgy.mundansok;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MundansokApplication {

    public static void main(String[] args) {
        SpringApplication.run(MundansokApplication.class, args);
    }
}
