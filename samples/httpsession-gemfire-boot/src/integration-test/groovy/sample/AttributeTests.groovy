/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sample

import geb.spock.GebReportingSpec
import org.springframework.boot.test.IntegrationTest
import org.springframework.boot.test.SpringApplicationContextLoader
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import sample.client.Application
import sample.pages.HomePage
import spock.lang.Stepwise
import pages.*

/**
 * Tests the CAS sample application using service tickets.
 *
 * @author Rob Winch
 * @author John Blum
 */
@Stepwise
@IntegrationTest
@WebAppConfiguration
@ContextConfiguration(classes = Application, loader = SpringApplicationContextLoader)
class AttributeTests extends GebReportingSpec {
	def 'first visit no attributes'() {
		when:
		to HomePage
		then:
		attributes.empty
	}

	def 'create attribute'() {
		when:
		createAttribute('a','b')
		then:
		attributes.size() == 2
		attributes[0..1]*.name == ['requestCount', 'a']
		attributes.find { it.name == 'requestCount' }?.value == '1'
		attributes.find { it.name == 'a' }?.value == 'b'
	}
}
