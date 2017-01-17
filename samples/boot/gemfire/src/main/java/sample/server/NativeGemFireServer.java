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

package sample.server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.ExpirationAction;
import org.apache.geode.cache.ExpirationAttributes;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionFactory;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.server.CacheServer;

import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.util.StringUtils;

/**
 * The {@link NativeGemFireServer} class uses the GemFire API to create a GemFire (cache) instance.
 *
 * @author John Blum
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.server.CacheServer
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @since 1.3.0
 */
@SuppressWarnings("unused")
public final class NativeGemFireServer implements Runnable {

	private static final int GEMFIRE_CACHE_SERVER_PORT =
		Integer.getInteger("spring-session-data-gemfire.cache.server.port", 12480);

	private static final String GEMFIRE_CACHE_SERVER_HOST = "localhost";
	private static final String GEMFIRE_CACHE_SERVER_HOSTNAME_FOR_CLIENTS = GEMFIRE_CACHE_SERVER_HOST;
	private static final String GEMFIRE_LOG_FILENAME_PATTERN =
		String.format("%s", NativeGemFireServer.class.getSimpleName()).concat("-%s.log");

	public static void main(String[] args) {
		newNativeGemFireServer(args).run();
	}

	private final String[] args;

	private static File newGemFireLogFile(String suffix) {
		return new File(String.format(GEMFIRE_LOG_FILENAME_PATTERN, suffix));
	}

	private static NativeGemFireServer newNativeGemFireServer(String[] args) {
		return new NativeGemFireServer(args);
	}

	private static String[] nullSafeStringArray(String[] array) {
		return (array != null ? array.clone() : new String[0]);
	}

	private static void writeStringTo(File file, String fileContents) {
		PrintWriter fileWriter = null;

		try {
			fileWriter = new PrintWriter(new BufferedWriter(new FileWriter(file, true)), true);
			fileWriter.println(fileContents);
			fileWriter.flush();
		}
		catch (IOException e) {
			throw new RuntimeException(String.format("Failed to write [%s] to file [%s]", fileContents, file), e);
		}
		finally {
			if (fileWriter != null) {
				fileWriter.close();
			}
		}
	}
	private NativeGemFireServer(String[] args) {
		this.args = nullSafeStringArray(args);
	}

	/**
	 * @inheritDoc
	 */
	public void run() {
		run(this.args);
	}

	private void run(String[] args) {
		try {
			writeStringTo(newGemFireLogFile("stdout"), "Before");

			registerShutdownHook(addCacheServer(createRegion(gemfireCache(
				gemfireProperties(applicationName())))));

			writeStringTo(newGemFireLogFile("stdout"), "After");
		}
		catch (Throwable e) {
			writeStringTo(newGemFireLogFile("stderr"), e.toString());
		}
	}

	private String applicationName() {
		return applicationName(null);
	}

	private String applicationName(String applicationName) {
		return StringUtils.hasText(applicationName) ? applicationName
			: "spring-session-data-gemfire.boot.sample." + NativeGemFireServer.class.getSimpleName();
	}

	private Properties gemfireProperties(String applicationName) {
		Properties gemfireProperties = new Properties();

		gemfireProperties.setProperty("name", applicationName);
		gemfireProperties.setProperty("log-file", "gemfire-server.log");
		gemfireProperties.setProperty("log-level", "config");
		//gemfireProperties.setProperty("jmx-manager", "true");
		//gemfireProperties.setProperty("jmx-manager-start", "true");

		return gemfireProperties;
	}

	private Cache gemfireCache(Properties gemfireProperties) {
		return new CacheFactory(gemfireProperties).create();
	}

	private Cache createRegion(Cache gemfireCache) {
		RegionFactory<Object, AbstractGemFireOperationsSessionRepository.GemFireSession> regionFactory =
			gemfireCache.createRegionFactory(RegionShortcut.PARTITION);

		regionFactory.setKeyConstraint(Object.class);
		regionFactory.setValueConstraint(AbstractGemFireOperationsSessionRepository.GemFireSession.class);
		regionFactory.setStatisticsEnabled(true);
		regionFactory.setEntryIdleTimeout(newExpirationAttributes(1800, ExpirationAction.INVALIDATE));

		Region region = regionFactory.create(
			GemFireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);

		return gemfireCache;
	}

	private ExpirationAttributes newExpirationAttributes(int expirationTime, ExpirationAction expirationAction) {
		return new ExpirationAttributes(expirationTime, expirationAction);
	}

	private Cache addCacheServer(Cache gemfireCache) throws IOException {
		CacheServer cacheServer = gemfireCache.addCacheServer();

		cacheServer.setBindAddress(GEMFIRE_CACHE_SERVER_HOST);
		cacheServer.setHostnameForClients(GEMFIRE_CACHE_SERVER_HOSTNAME_FOR_CLIENTS);
		cacheServer.setPort(GEMFIRE_CACHE_SERVER_PORT);
		cacheServer.start();

		return gemfireCache;
	}

	private Cache registerShutdownHook(final Cache gemfireCache) {
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				if (gemfireCache != null) {
					gemfireCache.close();
				}
			}
		}));

		return gemfireCache;
	}
}
