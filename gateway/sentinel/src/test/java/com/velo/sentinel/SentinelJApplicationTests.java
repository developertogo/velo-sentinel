package com.velo.sentinel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@Disabled("Context load requires physical infrastructure; logic is verified in unit tests.")
@SpringBootTest
@ActiveProfiles("test")
class SentinelJApplicationTests {

	@MockitoBean
	private StringRedisTemplate redisTemplate;

	@MockitoBean
	private RedisConnectionFactory redisConnectionFactory;

	@Test
	void contextLoads() {
	}

}
