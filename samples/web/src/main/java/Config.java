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

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.ExpiringSession;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisOperationsSessionRepository;
import org.springframework.session.data.redis.RedisOperationsSessionRepository.RedisSession;
import org.springframework.session.web.http.SessionRepositoryFilter;

import redis.clients.jedis.Protocol;
import redis.embedded.RedisServer;

/**
 * @author Rob Winch
 */
@Configuration
public class Config {

    @Bean
    public RedisServerBean redisServer() {
        return new RedisServerBean();
    }

    class RedisServerBean implements InitializingBean, DisposableBean {
        private RedisServer redisServer;


        @Override
        public void afterPropertiesSet() throws Exception {
            redisServer = new RedisServer(Protocol.DEFAULT_PORT);
            redisServer.start();
        }

        @Override
        public void destroy() throws Exception {
            if(redisServer != null) {
                redisServer.stop();
            }
        }
    }

    @Bean
    public JedisConnectionFactory connectionFactory() throws Exception {
        return new JedisConnectionFactory();
    }

    @Bean
    public RedisTemplate<String,ExpiringSession> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, ExpiringSession> template = new RedisTemplate<String, ExpiringSession>();
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setConnectionFactory(connectionFactory);
        return template;
    }

    @Bean
    public RedisOperationsSessionRepository sessionRepository(RedisTemplate<String, ExpiringSession> redisTemplate) {
        return new RedisOperationsSessionRepository(redisTemplate);
    }

    @Bean
    public SessionRepositoryFilter<RedisSession> sessionFilter(SessionRepository<RedisSession> sessionRepository) {
        return new SessionRepositoryFilter<RedisSession>(sessionRepository);
    }
}
