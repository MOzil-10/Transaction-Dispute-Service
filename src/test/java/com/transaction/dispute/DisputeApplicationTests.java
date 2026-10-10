package com.transaction.dispute;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@org.springframework.context.annotation.Import(com.transaction.dispute.config.TestSqsConfig.class)
@SpringBootTest
@ActiveProfiles("test")
class DisputeApplicationTests {

	@Test
	void contextLoads() {
	}

}
