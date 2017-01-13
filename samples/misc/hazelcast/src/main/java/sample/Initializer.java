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

package sample;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.EnumSet;
import java.util.Map;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import org.springframework.session.ExpiringSession;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;

@WebListener
public class Initializer implements ServletContextListener {
	private HazelcastInstance instance;

	public void contextInitialized(ServletContextEvent sce) {
		String sessionMapName = "spring:session:sessions";
		ServletContext sc = sce.getServletContext();

		Config cfg = new Config();
		NetworkConfig netConfig = new NetworkConfig();
		netConfig.setPort(getAvailablePort());
		cfg.setNetworkConfig(netConfig);
		SerializerConfig serializer = new SerializerConfig().setTypeClass(Object.class)
				.setImplementation(new ObjectStreamSerializer());
		cfg.getSerializationConfig().addSerializerConfig(serializer);
		MapConfig mc = new MapConfig();
		mc.setName(sessionMapName);
		mc.setTimeToLiveSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
		cfg.addMapConfig(mc);

		this.instance = Hazelcast.newHazelcastInstance(cfg);
		Map<String, ExpiringSession> sessions = this.instance.getMap(sessionMapName);

		SessionRepository<ExpiringSession> sessionRepository = new MapSessionRepository(
				sessions);
		SessionRepositoryFilter<ExpiringSession> filter = new SessionRepositoryFilter<ExpiringSession>(
				sessionRepository);
		Dynamic fr = sc.addFilter("springSessionFilter", filter);
		fr.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
	}

	public void contextDestroyed(ServletContextEvent sce) {
		this.instance.shutdown();
	}

	private static int getAvailablePort() {
		ServerSocket socket = null;
		try {
			socket = new ServerSocket(0);
			return socket.getLocalPort();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		finally {
			try {
				socket.close();
			}
			catch (IOException e) {
			}
		}
	}
}
