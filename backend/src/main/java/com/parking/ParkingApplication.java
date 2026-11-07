package com.parking;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.parking.mapper")
public class ParkingApplication {
  public static void main(String[] args) {
    SpringApplication.run(ParkingApplication.class, args);
  }
}
