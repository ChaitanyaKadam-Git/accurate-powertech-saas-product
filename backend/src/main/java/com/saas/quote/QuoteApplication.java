package com.saas.quote;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.TimeZone;

@SpringBootApplication
public class QuoteApplication {
    public static void main(String[] args) {
        // Must run before Spring starts: the Postgres driver sends the JVM timezone to the database.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(QuoteApplication.class, args);
    }
}
