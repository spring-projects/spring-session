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

package docs.http;

import java.util.Collections;

import com.fasterxml.jackson.databind.Module;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.mongo.AbstractMongoSessionConverter;
import org.springframework.session.data.mongo.JacksonMongoSessionConverter;
import org.springframework.session.data.mongo.config.annotation.web.http.EnableMongoHttpSession;

/**
 *
 * @author Jakub Kubrynski
 * @author Rob Winch
 */
// tag::config[]
@Configuration
@EnableMongoHttpSession
public class MongoJacksonSessionConfiguration {

	@Bean
	public AbstractMongoSessionConverter mongoSessionConverter() {
		return new JacksonMongoSessionConverter(getJacksonModules());
	}

	public Iterable<Module> getJacksonModules() {
		return Collections.<Module>singletonList(new MyJacksonModule());
	}
}
// end::config[]
