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

import org.springframework.session.Session;
import org.springframework.util.ObjectUtils;

/**
 * Simple implementation for {@link SessionContext}.
 * 
 * @author Francisco Spaeth
 * @since 1.1
 * 
 */
public class SimpleSessionContext implements SessionContext {

	private Session session;

	public Session getSession() {
		return session;
	}

	public void setSession(Session session) {
		this.session = session;
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHashCode(session);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		SimpleSessionContext other = (SimpleSessionContext) obj;
		return ObjectUtils.nullSafeEquals(session, other.session);
	}

	@Override
	public String toString() {
		return "SimpleSessionContext [session=" + session + "]";
	}

}
