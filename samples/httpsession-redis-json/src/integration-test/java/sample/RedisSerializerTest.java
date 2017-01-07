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
package sample;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author jitendra on 8/3/16.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class RedisSerializerTest {

	@Autowired
	RedisTemplate<Object, Object> sessionRedisTemplate;

	@Test
	public void testRedisTemplate() {
		assertThat(this.sessionRedisTemplate).isNotNull();
		assertThat(this.sessionRedisTemplate.getDefaultSerializer()).isNotNull();
		assertThat(this.sessionRedisTemplate.getDefaultSerializer())
				.isInstanceOf(GenericJackson2JsonRedisSerializer.class);
	}
}
