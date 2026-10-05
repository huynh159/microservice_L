package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:postgresql://localhost:5433/notification_db",
		"spring.datasource.username=notification_service_db_user",
		"spring.datasource.password=notification_service_db_pass",
		"spring.kafka.bootstrap-servers=localhost:9092"
})
class DemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
