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
package org.springframework.session.data.couchbase.application;

import java.io.Serializable;

public class Message implements Serializable {

	private static final long serialVersionUID = 1L;

	private String text;
	private Integer number;

	public String getText() {
		return this.text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public Integer getNumber() {
		return this.number;
	}

	public void setNumber(Integer number) {
		this.number = number;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		Message message = (Message) o;

		return this.text != null ? this.text.equals(message.text) : message.text == null
				&& (this.number != null ? this.number.equals(message.number) : message.number == null);
	}

	@Override
	public int hashCode() {
		int result = this.text != null ? this.text.hashCode() : 0;
		result = 31 * result + (this.number != null ? this.number.hashCode() : 0);
		return result;
	}
}
