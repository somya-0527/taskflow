package com.taskflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class TaskflowApplication {
  
    public static void main(String[] args) {
        SpringApplication.run(TaskflowApplication.class, args);
    }

}
