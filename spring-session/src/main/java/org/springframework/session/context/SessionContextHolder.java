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
package org.springframework.session.context;

import java.lang.reflect.Constructor;

import org.springframework.session.Session;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Associates a given {@link SessionContext} with the current execution using configured strategy.
 * <p>
 * This class provides a series of static methods that delegate to an instance of {@link SessionContextHolderStrategy}. The purpose of the class is to provide a
 * convenient way to specify the strategy that should be used for a given JVM. This is a JVM-wide setting, since everything in this class is <code>static</code>
 * to facilitate ease of use in calling code.
 * <p>
 * To specify which strategy should be used, you must provide a mode setting. A mode setting is one of the three valid <code>MODE_</code> settings defined as
 * <code>static final</code> fields, or a fully qualified classname to a concrete implementation of {@link SessionContextHolderStrategy} that provides a public
 * no-argument constructor.
 * <p>
 * There are two ways to specify the desired strategy mode <code>String</code>. The first is to specify it via the system property keyed on
 * {@link #SYSTEM_PROPERTY}. The second is to call {@link #setStrategyName(String)} before using the class. If neither approach is used, the class will default
 * to using {@link #MODE_THREADLOCAL}, which is backwards compatible, has fewer JVM incompatibilities and is appropriate on servers (whereas
 * {@link #MODE_GLOBAL} is definitely inappropriate for server use).
 *
 * @author Francisco Spaeth
 * @since 1.1
 * 
 */
public class SessionContextHolder {

	public static final String MODE_THREADLOCAL = "MODE_THREADLOCAL";
	public static final String MODE_INHERITABLETHREADLOCAL = "MODE_INHERITABLETHREADLOCAL";
	public static final String MODE_GLOBAL = "MODE_GLOBAL";
	public static final String SYSTEM_PROPERTY = "spring.session.strategy";
	private static String strategyName = System.getProperty(SYSTEM_PROPERTY);
	private static SessionContextHolderStrategy strategy;
	private static int initializeCount = 0;

	static {
		initialize();
	}

	/**
	 * Explicitly clears the context value.
	 */
	public static void clearContext() {
		strategy.clearContext();
	}

	/**
	 * Obtain the current <code>Session</code>.
	 * 
	 * @return the security context, <code>null</code> when no session available
	 */
	public static Session getSession() {
		SessionContext ctx = strategy.getContext();

		if (ctx == null) {
			return null;
		}

		return ctx.getSession();
	}

	/**
	 * Primarily for troubleshooting purposes, this method shows how many times the class has re-initialized its {@link SessionContextHolderStrategy}.
	 * 
	 * @return the count (should be one unless you've called {@link #setStrategyName(String)} to switch to an alternate strategy).
	 */
	public static int getInitializeCount() {
		return initializeCount;
	}

	private static void initialize() {
		if (StringUtils.isEmpty(strategyName)) {
			strategyName = MODE_THREADLOCAL;
		}

		if (MODE_THREADLOCAL.equals(strategyName)) {
			strategy = new ThreadLocalSessionContextHolderStrategy();
		} else if (MODE_INHERITABLETHREADLOCAL.equals(strategyName)) {
			strategy = new InheritableThreadLocalSessionContextHolderStrategy();
		} else if (MODE_GLOBAL.equals(strategyName)) {
			strategy = new GlobalSessionContextHolderStrategy();
		} else {
			instantiateCustomStrategy();
		}

		initializeCount++;
	}

	private static void instantiateCustomStrategy() {
		try {
			Class<?> clazz = Class.forName(strategyName);
			Constructor<?> customStrategy = clazz.getConstructor();
			strategy = (SessionContextHolderStrategy) customStrategy.newInstance();
		} catch (Exception ex) {
			ReflectionUtils.handleReflectionException(ex);
		}
	}

	/**
	 * Associates a new {@link SessionContext} with the current execution using configured strategy.
	 * 
	 * @param sessionContext the new {@link SessionContext} (may not be <code>null</code>)
	 */
	public static void setContext(SessionContext sessionContext) {
		Assert.notNull(sessionContext, "context must not be null");
		strategy.setContext(sessionContext);
	}

	/**
	 * Retrieves the current {@link SessionContext}.
	 * 
	 * @return the {@link SessionContext} (never <code>null</code>).
	 */
	public static SessionContext getContext() {
		return strategy.getContext();
	}

	/**
	 * Changes the preferred strategy. Do <em>NOT</em> call this method more than once for a given JVM, as it will re-initialize the strategy and adversely
	 * affect any existing threads using the old strategy.
	 *
	 * @param strategyName the fully qualified class name of the strategy that should be used.
	 */
	public static void setStrategyName(String strategyName) {
		SessionContextHolder.strategyName = strategyName;
		initialize();
	}

	/**
	 * Allows retrieval of the context strategy.
	 *
	 * @return the configured strategy for storing the session context.
	 */
	public static SessionContextHolderStrategy getContextHolderStrategy() {
		return strategy;
	}

	@Override
	public String toString() {
		return "SessionContextHolder[strategy='" + strategyName + "'; initializeCount=" + initializeCount + "]";
	}

}
