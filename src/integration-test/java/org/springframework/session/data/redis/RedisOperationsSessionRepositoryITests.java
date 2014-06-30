package org.springframework.session.data.redis;

import static org.fest.assertions.Assertions.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class RedisOperationsSessionRepositoryITests {
	private RedisServer redisServer;

	@Autowired
	private SessionRepository repository;

	@Before
	public void setup() throws IOException {
		redisServer = new RedisServer(getPort());
		redisServer.start();
	}

	@After
	public void shutdown() throws InterruptedException {
		redisServer.stop();
	}

	@Test
	public void saves() {
		Session toSave = repository.createSession();
		toSave.setAttribute("a", "b");
		Authentication toSaveToken = new UsernamePasswordAuthenticationToken("user","password", AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext toSaveContext = SecurityContextHolder.createEmptyContext();
		toSaveContext.setAuthentication(toSaveToken);
		toSave.setAttribute("SPRING_SECURITY_CONTEXT", toSaveContext);

		repository.save(toSave);

		Session session = repository.getSession(toSave.getId());

		assertThat(session.getId()).isEqualTo(toSave.getId());
		assertThat(session.getAttributeNames()).isEqualTo(session.getAttributeNames());
		assertThat(session.getAttribute("a")).isEqualTo(toSave.getAttribute("a"));

		SecurityContext context = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");

		repository.delete(toSave.getId());

		assertThat(repository.getSession(toSave.getId())).isNull();
	}

	@Test
	public void putAllOnSingleAttrDoesNotRemoveOld() {
		Session toSave = repository.createSession();
		toSave.setAttribute("a", "b");

		repository.save(toSave);
		toSave = repository.getSession(toSave.getId());

		toSave.setAttribute("1", "2");

		repository.save(toSave);
		toSave = repository.getSession(toSave.getId());

		Session session = repository.getSession(toSave.getId());
		assertThat(session.getAttributeNames().size()).isEqualTo(2);
		assertThat(session.getAttribute("a")).isEqualTo("b");
		assertThat(session.getAttribute("1")).isEqualTo("2");
	}

	@Configuration
	static class Config {
		@Bean
		public JedisConnectionFactory connectionFactory() throws Exception {
			JedisConnectionFactory factory = new JedisConnectionFactory();
			factory.setPort(getPort());
			factory.setUsePool(false);
			return factory;
		}

		@Bean
		public RedisTemplate<String,Session> redisTemplate(RedisConnectionFactory connectionFactory) {
			RedisTemplate<String, Session> template = new RedisTemplate<String, Session>();
			template.setKeySerializer(new StringRedisSerializer());
			template.setHashKeySerializer(new StringRedisSerializer());
			template.setConnectionFactory(connectionFactory);
			return template;
		}

		@Bean
		public RedisOperationsSessionRepository sessionRepository(RedisTemplate<String, Session> redisTemplate) {
			return new RedisOperationsSessionRepository(redisTemplate);
		}
	}

	private static Integer availablePort;

	private static int getPort() throws IOException {
		if(availablePort == null) {
			ServerSocket socket = new ServerSocket(0);
			availablePort = socket.getLocalPort();
			socket.close();
		}
		return availablePort;
	}
}