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
package org.springframework.session.id;

import static org.fest.assertions.Assertions.assertThat;

import java.util.Arrays;
import java.util.Locale;

import org.junit.Before;
import org.junit.Test;

import com.google.common.io.BaseEncoding;

public class Base32HexSessionIdEncoderTests {
	
	private Base32HexSessionIdEncoder encoder;
	private BaseEncoding guavaEncoder;

	@Before
	public void setup() {
		encoder = new Base32HexSessionIdEncoder();
		guavaEncoder = BaseEncoding.base32Hex().omitPadding();
	}
	
	@Test
	public void encode() {
		for (int i=20; i<30; i+=1) {
			byte[] bytes = new byte[i];
			Arrays.fill(bytes, (byte)0x00);
			assertThat(encoder.encode(bytes)).isEqualTo(guavaEncoder.encode(bytes));
			Arrays.fill(bytes, (byte)0xff);
			assertThat(encoder.encode(bytes)).isEqualTo(guavaEncoder.encode(bytes));
			for (int j=0; j<i; j++) {
				bytes[j] = (byte) (i+j);
			}
			assertThat(encoder.encode(bytes)).isEqualTo(guavaEncoder.encode(bytes));
		}
	}
	
	@Test
	public void lowerEncode() {
		Base32HexSessionIdEncoder lowerEncoder = new Base32HexSessionIdEncoder(true);
		byte[] bytes = new byte[]{'A','B','C','D'};
		assertThat(lowerEncoder.encode(bytes)).isEqualTo(guavaEncoder.encode(bytes).toLowerCase(Locale.US));
	}



}