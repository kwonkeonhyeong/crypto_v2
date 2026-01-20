package com.crypto.prayer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PrayerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrayerApplication.class, args);
    }
}
