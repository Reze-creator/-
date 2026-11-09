package com.parking;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "parking.redis-enabled=false",
      "parking.messaging-enabled=false",
      "parking.search-enabled=false",
      "spring.datasource.url=jdbc:h2:mem:parking;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "parking.seed-enabled=false",
      "parking.expiry-delay-ms=600000",
      "parking.operations-delay-ms=600000"
    })
@AutoConfigureMockMvc
class ParkingApiTest extends PhaseTwoContractSuite {}
