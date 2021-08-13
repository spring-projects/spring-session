/*
 * Copyright 2014-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.mongodb.examples;

import java.util.Objects;

/**
 * @author Rob Winch
 * @author Greg Turnquist
 * @since 5.0
 */
public class SessionAttributeForm {

	private String attributeName;

	private String attributeValue;

	public String getAttributeName() {
		return attributeName;
	}

	public void setAttributeName(String attributeName) {
		this.attributeName = attributeName;
	}

	public String getAttributeValue() {
		return attributeValue;
	}

	public void setAttributeValue(String attributeValue) {
		this.attributeValue = attributeValue;
	}

	@Override
	public boolean equals(Object o) {

		if (this == o)
			return true;
		if (!(o instanceof SessionAttributeForm))
			return false;
		SessionAttributeForm that = (SessionAttributeForm) o;
		return Objects.equals(attributeName, that.attributeName) && Objects.equals(attributeValue, that.attributeValue);
	}

	@Override
	public int hashCode() {
		return Objects.hash(attributeName, attributeValue);
	}

	@Override
	public String toString() {

		return "SessionAttributeForm{" + "attributeName='" + attributeName + '\'' + ", attributeValue='"
				+ attributeValue + '\'' + '}';
	}

}
