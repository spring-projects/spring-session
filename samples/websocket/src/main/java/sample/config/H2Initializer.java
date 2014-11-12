/*
 * Copyright 2002-2013 the original author or authors.
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
package sample.config;

import org.h2.server.web.WebServlet;
import org.springframework.boot.context.embedded.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Initializes the H2 {@link WebServlet} so we can access our in memory database
 * from the URL "/h2".
 *
 * @author Rob Winch
 */
@Configuration
public class H2Initializer {

	@Bean
	public ServletRegistrationBean h2Servlet() {
		ServletRegistrationBean servletBean = new ServletRegistrationBean();
		servletBean.addUrlMappings("/h2/*"); 
		servletBean.setServlet(new WebServlet());
		return servletBean;
	}
}
