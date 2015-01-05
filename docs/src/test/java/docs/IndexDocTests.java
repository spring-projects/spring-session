/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package docs;

import org.junit.Test;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.session.*;
import org.springframework.session.data.redis.RedisOperationsSessionRepository;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Rob Winch
 */
public class IndexDocTests {
    static final String ATTR_USER = "user";

    @Test
    public void repositoryDemo() {
        ExpiringRepositoryDemo<ExpiringSession> demo = new ExpiringRepositoryDemo<ExpiringSession>();
        demo.repository = new MapSessionRepository();

        demo.demo();
    }

    // tag::repository-demo[]
    public class RepositoryDemo<S extends Session> {
        private SessionRepository<S> repository; // <1>

        public void demo() {
            S toSave = repository.createSession(); // <2>

            // <3>
            User rwinch = new User("rwinch");
            toSave.setAttribute(ATTR_USER, rwinch);

            repository.save(toSave); // <4>

            S session = repository.getSession(toSave.getId()); // <5>

            // <6>
            User user = session.getAttribute(ATTR_USER);
            assertThat(user).isEqualTo(rwinch);
        }

        // ... setter methods ...
    }
    // end::repository-demo[]


    @Test
    public void expireRepositoryDemo() {
        ExpiringRepositoryDemo<ExpiringSession> demo = new ExpiringRepositoryDemo<ExpiringSession>();
        demo.repository = new MapSessionRepository();

        demo.demo();
    }

    // tag::expire-repository-demo[]
    public class ExpiringRepositoryDemo<S extends ExpiringSession> {
        private SessionRepository<S> repository; // <1>

        public void demo() {
            S toSave = repository.createSession(); // <2>
            // ...
            toSave.setMaxInactiveIntervalInSeconds(30); // <3>

            repository.save(toSave); // <4>

            S session = repository.getSession(toSave.getId()); // <5>
            // ...
        }

        // ... setter methods ...
    }
    // end::expire-repository-demo[]

    @Test
    public void newRedisOperationsSessionRepository() {
        // tag::new-redisoperationssessionrepository[]
        JedisConnectionFactory factory = new JedisConnectionFactory();
        SessionRepository<? extends ExpiringSession> repository =
                new RedisOperationsSessionRepository(factory);
        // end::new-redisoperationssessionrepository[]
    }

    @Test
    public void mapRepository() {
        // tag::new-mapsessionrepository[]
        SessionRepository<? extends ExpiringSession> repository = new MapSessionRepository();
        // end::new-mapsessionrepository[]
    }

    private static class User {
        private User(String username) {}
    }
}
