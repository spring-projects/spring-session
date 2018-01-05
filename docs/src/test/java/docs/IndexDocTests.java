/*
 * Copyright 2014-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package docs;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.Test;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockServletContext;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.ReactiveRedisOperationsSessionRepository;
import org.springframework.session.data.redis.RedisOperationsSessionRepository;
import org.springframework.session.hazelcast.HazelcastSessionRepository;
import org.springframework.session.jdbc.JdbcOperationsSessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Rob Winch
 * @author Vedran Pavic
 */
public class IndexDocTests {
	static final String ATTR_USER = "user";

	@Test
	public void repositoryDemo() {
		RepositoryDemo<MapSession> demo = new RepositoryDemo<>();
		demo.repository = new MapSessionRepository(new ConcurrentHashMap<>());

		demo.demo();
	}

	// tag::repository-demo[]
	public class RepositoryDemo<S extends Session> {
		private SessionRepository<S> repository; // <1>

		public void demo() {
			S toSave = this.repository.createSession(); // <2>

			// <3>
			User rwinch = new User("rwinch");
			toSave.setAttribute(ATTR_USER, rwinch);

			this.repository.save(toSave); // <4>

			S session = this.repository.findById(toSave.getId()); // <5>

			// <6>
			User user = session.getAttribute(ATTR_USER);
			assertThat(user).isEqualTo(rwinch);
		}

		// ... setter methods ...
	}
	// end::repository-demo[]

	@Test
	public void expireRepositoryDemo() {
		ExpiringRepositoryDemo<MapSession> demo = new ExpiringRepositoryDemo<>();
		demo.repository = new MapSessionRepository(new ConcurrentHashMap<>());

		demo.demo();
	}

	// tag::expire-repository-demo[]
	public class ExpiringRepositoryDemo<S extends Session> {
		private SessionRepository<S> repository; // <1>

		public void demo() {
			S toSave = this.repository.createSession(); // <2>
			// ...
			toSave.setMaxInactiveInterval(Duration.ofSeconds(30)); // <3>

			this.repository.save(toSave); // <4>

			S session = this.repository.findById(toSave.getId()); // <5>
			// ...
		}

		// ... setter methods ...
	}
	// end::expire-repository-demo[]

	@Test
	@SuppressWarnings("unused")
	public void newRedisOperationsSessionRepository() {
		// tag::new-redisoperationssessionrepository[]
		RedisTemplate<Object, Object> redisTemplate = new RedisTemplate<>();

		// ... configure redisTemplate ...

		SessionRepository<? extends Session> repository =
				new RedisOperationsSessionRepository(redisTemplate);
		// end::new-redisoperationssessionrepository[]
	}

	@Test
	@SuppressWarnings("unused")
	public void newReactiveRedisOperationsSessionRepository() {
		LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory();
		RedisSerializationContext<String, Object> serializationContext = RedisSerializationContext
				.<String, Object>newSerializationContext(
						new JdkSerializationRedisSerializer())
				.build();

		// tag::new-reactiveredisoperationssessionrepository[]
		// ... create and configure connectionFactory and serializationContext ...

		ReactiveRedisTemplate<String, Object> redisTemplate = new ReactiveRedisTemplate<>(
				connectionFactory, serializationContext);

		ReactiveSessionRepository<? extends Session> repository =
				new ReactiveRedisOperationsSessionRepository(redisTemplate);
		// end::new-reactiveredisoperationssessionrepository[]
	}

	@Test
	@SuppressWarnings("unused")
	public void mapRepository() {
		// tag::new-mapsessionrepository[]
		SessionRepository<? extends Session> repository = new MapSessionRepository(
				new ConcurrentHashMap<>());
		// end::new-mapsessionrepository[]
	}

	@Test
	@SuppressWarnings("unused")
	public void newJdbcOperationsSessionRepository() {
		// tag::new-jdbcoperationssessionrepository[]
		JdbcTemplate jdbcTemplate = new JdbcTemplate();

		// ... configure JdbcTemplate ...

		PlatformTransactionManager transactionManager = new DataSourceTransactionManager();

		// ... configure transactionManager ...

		SessionRepository<? extends Session> repository =
				new JdbcOperationsSessionRepository(jdbcTemplate, transactionManager);
		// end::new-jdbcoperationssessionrepository[]
	}

	@Test
	@SuppressWarnings("unused")
	public void newHazelcastSessionRepository() {
		// tag::new-hazelcastsessionrepository[]

		Config config = new Config();

		// ... configure Hazelcast ...

		HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);

		HazelcastSessionRepository repository =
				new HazelcastSessionRepository(hazelcastInstance);
		// end::new-hazelcastsessionrepository[]
	}

	@Test
	public void runSpringHttpSessionConfig() {
		AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
		context.register(SpringHttpSessionConfig.class);
		context.setServletContext(new MockServletContext());
		context.refresh();

		try {
			context.getBean(SessionRepositoryFilter.class);
		}
		finally {
			context.close();
		}
	}

	private static final class User {
		private User(String username) {
		}
	}
}
