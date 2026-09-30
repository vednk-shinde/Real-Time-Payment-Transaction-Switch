package com.paymentswitch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TransactionSwitchApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionSwitchApplication.class, args);
    }
}
