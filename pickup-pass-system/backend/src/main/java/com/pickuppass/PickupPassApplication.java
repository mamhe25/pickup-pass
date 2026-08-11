package com.pickuppass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PickupPassApplication {
    public static void main(String[] args) {
        SpringApplication.run(PickupPassApplication.class, args);
    }
}
