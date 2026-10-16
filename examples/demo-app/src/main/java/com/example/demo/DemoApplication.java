package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
@EnableScheduling
public class DemoApplication {

    private final JdbcTemplate jdbc;

    public DemoApplication(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    /** Light periodic query so the pool shows realistic activity. */
    @Scheduled(fixedDelay = 5000)
    public void tick() {
        jdbc.queryForObject("SELECT 1", Integer.class);
    }
}
