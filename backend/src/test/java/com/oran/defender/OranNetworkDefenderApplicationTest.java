package com.oran.defender;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

@DisplayName("Application bootstrap")
class OranNetworkDefenderApplicationTest {

    @Test
    @DisplayName("main delegates to SpringApplication with the application class")
    void mainDelegatesToSpringApplication() {
        String[] args = {"--server.port=0"};
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            OranNetworkDefenderApplication.main(args);
            spring.verify(() -> SpringApplication.run(OranNetworkDefenderApplication.class, args));
        }
    }
}
