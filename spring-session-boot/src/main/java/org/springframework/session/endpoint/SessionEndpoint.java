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

package org.springframework.session.endpoint;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.actuate.endpoint.EndpointProperties;
import org.springframework.boot.actuate.endpoint.mvc.MvcEndpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * {@link MvcEndpoint} to expose actuator session.
 *
 * @author Eddú Meléndez
 * @since 1.3.0
 */
@ConfigurationProperties("endpoints.session")
public class SessionEndpoint implements MvcEndpoint, EnvironmentAware {

	private Environment environment;

	/**
	 * Endpoint URL path.
	 */
	private String path = "/session";

	/**
	 * Enable the endpoint.
	 */
	private boolean enabled = true;

	/**
	 * Mark if the endpoint exposes sensitive information.
	 */
	private Boolean sensitive;

	@Autowired
	private FindByIndexNameSessionRepository<? extends ExpiringSession> sessionRepository;

	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	@RequestMapping(path = "/{username}", method = RequestMethod.GET)
	public Collection<? extends ExpiringSession> result(@PathVariable String username) {
		return this.sessionRepository.findByIndexNameAndIndexValue(
				FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username)
				.values();
	}

	@RequestMapping(path = "/{sessionId}", method = RequestMethod.DELETE)
	public void delete(@PathVariable String sessionId) {
		this.sessionRepository.delete(sessionId);
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getPath() {
		return this.path;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setSensitive(Boolean sensitive) {
		this.sensitive = sensitive;
	}

	public boolean isSensitive() {
		return EndpointProperties.isSensitive(this.environment, this.sensitive, false);
	}

	public Class<? extends Endpoint> getEndpointType() {
		return Endpoint.class;
	}

}
