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

/**
 * A strategy for storing session context information.
 * <p>
 * The strategy instance is mainly used by {@link SecurityContextHolder}.
 *
 * @author Francisco Spaeth
 * @since 1.1
 * 
 */
public interface SessionContextHolderStrategy {

	/**
	 * Clears the current context.
	 */
	void clearContext();

	/**
	 * Obtains the current context.
	 *
	 * @return a context (never <code>null</code> - create a default implementation if
	 * necessary)
	 */
	SessionContext getContext();

	/**
	 * Sets the current context.
	 *
	 * @param context to the new argument
	 * 
	 * @throws IllegalArgumentException when context is null
	 */
	void setContext(SessionContext context);

	/**
	 * Creates a default {@link SessionContext}.
	 *
	 * @return a default SessionContext instance.
	 */
	SessionContext createEmptyContext();

}
