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

package sample;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.EnumSet;
import java.util.Map;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.web.http.SessionRepositoryFilter;

@WebListener
public class Initializer implements ServletContextListener {

	private static final String SESSION_MAP_NAME = "spring:session:sessions";

	private HazelcastInstance instance;

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		this.instance = createHazelcastInstance();
		Map<String, Session> sessions = this.instance.getMap(SESSION_MAP_NAME);

		MapSessionRepository sessionRepository = new MapSessionRepository(sessions);
		SessionRepositoryFilter<? extends Session> filter = new SessionRepositoryFilter<>(
				sessionRepository);

		Dynamic fr = sce.getServletContext().addFilter("springSessionFilter", filter);
		fr.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		this.instance.shutdown();
	}

	private HazelcastInstance createHazelcastInstance() {
		Config config = new Config();

		config.getNetworkConfig()
				.setPort(getAvailablePort())
				.getJoin().getMulticastConfig().setEnabled(false);

		config.getMapConfig(SESSION_MAP_NAME)
				.setTimeToLiveSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);

		return Hazelcast.newHazelcastInstance(config);
	}

	private static int getAvailablePort() {
		ServerSocket socket = null;
		try {
			socket = new ServerSocket(0);
			return socket.getLocalPort();
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		finally {
			try {
				socket.close();
			}
			catch (IOException ex) {
			}
		}
	}

}
