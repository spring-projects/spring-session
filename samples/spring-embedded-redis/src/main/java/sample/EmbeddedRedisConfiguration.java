/*
 * Copyright 2002-2015 the original author or authors.
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
package sample;

import java.io.IOException;
import java.net.ServerSocket;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import redis.embedded.RedisServer;

/**
 * Runs an embedded Redis instance. This is only necessary since we do not want
 * users to have to setup a Redis instance. In a production environment, this
 * would not be used since a Redis Server would be setup.
 *
 * @author Rob Winch
 */
@Configuration
class EmbeddedRedisConfiguration {
	public static final String SERVER_PORT_PROP_NAME = "spring.redis.port";

	@Bean
	public static RedisServerBean redisServer(ConfigurableEnvironment env) {
		RedisServerBean bean = new RedisServerBean();
		env.getPropertySources().addLast(bean);
		return bean;
	}

	@Bean
	public static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	/**
	 * Implements BeanDefinitionRegistryPostProcessor to ensure this Bean is initialized
	 * before any other Beans. Specifically, we want to ensure that the Redis Server is
	 * started before RedisHttpSessionConfiguration attempts to enable Keyspace
	 * notifications. We also want to ensure that we are able to register the
	 * {@link PropertySource} before any beans are initialized.
	 */
	static class RedisServerBean extends PropertySource<RedisServerBean> implements InitializingBean, DisposableBean, BeanDefinitionRegistryPostProcessor {
		private final int port = getAvailablePort();

		private RedisServer redisServer;

		public RedisServerBean() {
			super("redisServerPortPropertySource");
		}

		public void afterPropertiesSet() throws Exception {
			redisServer = new RedisServer(port);
			redisServer.start();
		}

		public void destroy() throws Exception {
			if(redisServer != null) {
				redisServer.stop();
			}
		}

		public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {}

		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {}

		@Override
		public Object getProperty(String name) {
			if(SERVER_PORT_PROP_NAME.equals(name)) {
				return port;
			}
			return null;
		}

		private static int getAvailablePort() {
			ServerSocket socket = null;
			try {
				socket = new ServerSocket(0);
				return socket.getLocalPort();
			} catch(IOException e) {
				throw new RuntimeException(e);
			} finally {
				try {
					if(socket != null) {
						socket.close();
					}
				}catch(IOException e) {}
			}
		}
	}
}