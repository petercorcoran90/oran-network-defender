package com.oran.defender;

import com.intuit.karate.junit5.Karate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KarateApiTest {

    @LocalServerPort
    int port;

    @Karate.Test
    Karate api() {
        System.setProperty("karate.baseUrl", "http://localhost:" + port);
        return Karate.run("classpath:com/oran/defender/api/session-flow.feature");
    }
}
