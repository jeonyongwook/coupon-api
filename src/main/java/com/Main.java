package com;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        var context = SpringApplication.run(Main.class, args);

        try {
            var dataSource = context.getBean(javax.sql.DataSource.class);
            System.out.println("connected DB URL: " + dataSource.getConnection().getMetaData().getURL());
            System.out.println("DB connected Success");
        } catch (Exception e) {
            System.err.println("DB connected Fail: " + e.getMessage());
        }
    }
}