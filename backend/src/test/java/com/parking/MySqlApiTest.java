package com.parking;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/parking_test?connectionTimeZone=UTC&serverTimezone=UTC&characterEncoding=UTF-8",
      "parking.seed-enabled=false",
      "parking.expiry-delay-ms=600000",
      "parking.operations-delay-ms=600000"
    })
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "PARKING_MYSQL_TEST", matches = "true")
class MySqlApiTest extends PhaseTwoContractSuite {}
