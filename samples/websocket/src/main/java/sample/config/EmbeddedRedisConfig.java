/*
 * Copyright 2002-2013 the original author or authors.
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
package sample.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import redis.clients.jedis.Protocol;
import redis.embedded.RedisServer;

/**
 * @author Rob Winch
 */
@Configuration
public class EmbeddedRedisConfig {

    @Bean
    public RedisServerBean redisServer() {
        return new RedisServerBean();
    }

    class RedisServerBean implements InitializingBean, DisposableBean {
        private RedisServer redisServer;


        public void afterPropertiesSet() throws Exception {
            redisServer = new RedisServer(Protocol.DEFAULT_PORT);
            redisServer.start();
        }

        public void destroy() throws Exception {
            if(redisServer != null) {
                redisServer.stop();
            }
        }
    }
}
