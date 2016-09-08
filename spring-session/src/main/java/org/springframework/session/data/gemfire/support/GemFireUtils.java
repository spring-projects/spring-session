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

package org.springframework.session.data.gemfire.support;

import java.io.Closeable;
import java.io.IOException;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.cache.client.ClientCache;
import com.gemstone.gemfire.cache.client.ClientRegionShortcut;
import com.gemstone.gemfire.internal.cache.GemFireCacheImpl;

/**
 * GemFireUtils is an abstract, extensible utility class for working with GemFire types
 * and functionality and is used by Spring Session's GemFire adapter support classes.
 *
 * @author John Blum
 * @since 1.1.0
 */
public abstract class GemFireUtils {

	/**
	 * Null-safe method to close the given {@link Closeable} object.
	 *
	 * @param obj the {@link Closeable} object to close.
	 * @return true if the {@link Closeable} object is not null and was successfully
	 * closed, otherwise return false.
	 * @see java.io.Closeable
	 */
	public static boolean close(Closeable obj) {
		if (obj != null) {
			try {
				obj.close();
				return true;
			}
			catch (IOException ignore) {
			}
		}

		return false;
	}

	/**
	 * Determines whether the GemFire cache is a client.
	 *
	 * @param gemFireCache a reference to the GemFire cache.
	 * @return a boolean value indicating whether the GemFire cache is a client.
	 * @see com.gemstone.gemfire.cache.client.ClientCache
	 * @see com.gemstone.gemfire.cache.GemFireCache
	 */
	public static boolean isClient(GemFireCache gemFireCache) {
		boolean client = (gemFireCache instanceof ClientCache);
		client &= (!(gemFireCache instanceof GemFireCacheImpl) || ((GemFireCacheImpl) gemFireCache).isClient());
		return client;
	}

	/**
	 * Determines whether the GemFire cache is a peer.
	 *
	 * @param gemFireCache a reference to the GemFire cache.
	 * @return a boolean value indicating whether the GemFire cache is a peer.
	 * @see com.gemstone.gemfire.cache.Cache
	 * @see com.gemstone.gemfire.cache.GemFireCache
	 */
	public static boolean isPeer(GemFireCache gemFireCache) {
		return (gemFireCache instanceof Cache && !isClient(gemFireCache));
	}

	/**
	 * Determines whether the given {@link ClientRegionShortcut} is local only.
	 *
	 * @param shortcut the ClientRegionShortcut to evaluate.
	 * @return a boolean value indicating if the {@link ClientRegionShortcut} is local or
	 * not.
	 * @see com.gemstone.gemfire.cache.client.ClientRegionShortcut
	 */
	public static boolean isLocal(ClientRegionShortcut shortcut) {
		switch (shortcut) {
		case LOCAL:
		case LOCAL_HEAP_LRU:
		case LOCAL_OVERFLOW:
		case LOCAL_PERSISTENT:
		case LOCAL_PERSISTENT_OVERFLOW:
			return true;
		default:
			return false;
		}
	}

	/**
	 * Determines whether the client {@link ClientRegionShortcut} is a proxy-based
	 * shortcut. NOTE: "proxy"-based Regions keep no local state.
	 *
	 * @param shortcut the client {@link ClientRegionShortcut} to evaluate.
	 * @return a boolean value indicating whether the client {@link ClientRegionShortcut}
	 * refers to a proxy-based shortcut.
	 * @see com.gemstone.gemfire.cache.client.ClientRegionShortcut
	 */
	public static boolean isProxy(ClientRegionShortcut shortcut) {
		switch (shortcut) {
		case PROXY:
			return true;
		default:
			return false;
		}
	}

	/**
	 * Determines whether the peer {@link RegionShortcut} is a proxy-based shortcut. NOTE:
	 * "proxy"-based Regions keep no local state.
	 *
	 * @param shortcut the peer {@link RegionShortcut} to evaluate.
	 * @return a boolean value indicating whether the peer {@link RegionShortcut} refers
	 * to a proxy-based shortcut.
	 * @see com.gemstone.gemfire.cache.RegionShortcut
	 */
	public static boolean isProxy(RegionShortcut shortcut) {
		switch (shortcut) {
		case PARTITION_PROXY:
		case PARTITION_PROXY_REDUNDANT:
		case REPLICATE_PROXY:
			return true;
		default:
			return false;
		}
	}

	/**
	 * Converts a {@link Region} name to a {@link Region} path.
	 *
	 * @param regionName a String specifying the name of the {@link Region}.
	 * @return a String path for the given {@link Region} by name.
	 * @see com.gemstone.gemfire.cache.Region#getFullPath()
	 * @see com.gemstone.gemfire.cache.Region#getName()
	 */
	public static String toRegionPath(String regionName) {
		return String.format("%1$s%2$s", Region.SEPARATOR, regionName);
	}

}
