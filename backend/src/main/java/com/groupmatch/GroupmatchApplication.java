package com.groupmatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GroupmatchApplication {
    public static void main(String[] args) {
        SpringApplication.run(GroupmatchApplication.class, args);
    }
}