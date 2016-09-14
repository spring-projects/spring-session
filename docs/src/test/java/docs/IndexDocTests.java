/*
 * Copyright 2014-2016 the original author or authors.
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

import org.junit.Test;

import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockServletContext;
import org.springframework.session.ExpiringSession;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisOperationsSessionRepository;
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
		RepositoryDemo<MapSession> demo = new RepositoryDemo<MapSession>();
		demo.repository = new MapSessionRepository();

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

			S session = this.repository.getSession(toSave.getId()); // <5>

			// <6>
			User user = session.getAttribute(ATTR_USER);
			assertThat(user).isEqualTo(rwinch);
		}

		// ... setter methods ...
	}
	// end::repository-demo[]

	@Test
	public void expireRepositoryDemo() {
		ExpiringRepositoryDemo<MapSession> demo = new ExpiringRepositoryDemo<MapSession>();
		demo.repository = new MapSessionRepository();

		demo.demo();
	}

	// tag::expire-repository-demo[]
	public class ExpiringRepositoryDemo<S extends ExpiringSession> {
		private SessionRepository<S> repository; // <1>

		public void demo() {
			S toSave = this.repository.createSession(); // <2>
			// ...
			toSave.setMaxInactiveIntervalInSeconds(30); // <3>

			this.repository.save(toSave); // <4>

			S session = this.repository.getSession(toSave.getId()); // <5>
			// ...
		}

		// ... setter methods ...
	}
	// end::expire-repository-demo[]

	@Test
	@SuppressWarnings("unused")
	public void newRedisOperationsSessionRepository() {
		// tag::new-redisoperationssessionrepository[]
		JedisConnectionFactory factory = new JedisConnectionFactory();
		SessionRepository<? extends ExpiringSession> repository = new RedisOperationsSessionRepository(
				factory);
		// end::new-redisoperationssessionrepository[]
	}

	@Test
	@SuppressWarnings("unused")
	public void mapRepository() {
		// tag::new-mapsessionrepository[]
		SessionRepository<? extends ExpiringSession> repository = new MapSessionRepository();
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

		SessionRepository<? extends ExpiringSession> repository =
				new JdbcOperationsSessionRepository(jdbcTemplate, transactionManager);
		// end::new-jdbcoperationssessionrepository[]
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
