package org.springframework.session.data.redis;

import static org.fest.assertions.Assertions.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

@RunWith(MockitoJUnitRunner.class)
public class RedisOperationsSessionRepositoryTests {
	@Mock
	RedisOperations redisOperations;

	private RedisOperationsSessionRepository redisRepository;

	@Before
	public void setup() {
		this.redisRepository = new RedisOperationsSessionRepository(redisOperations);
	}

	@Test
	public void createSessionDefaultMaxInactiveInterval() throws Exception {
		Session session = redisRepository.createSession();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(new MapSession().getMaxInactiveInterval());
	}

	@Test
	public void createSessionCustomMaxInactiveInterval() throws Exception {
		int interval = 1;
		redisRepository.setDefaultMaxInactiveInterval(interval);
		Session session = redisRepository.createSession();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(interval);
	}
}