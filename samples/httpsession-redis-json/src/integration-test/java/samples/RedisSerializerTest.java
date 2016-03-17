package samples;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import sample.Application;

import static org.springframework.util.Assert.notNull;
import static org.junit.Assert.*;

/**
 * @author jitendra on 8/3/16.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(Application.class)
public class RedisSerializerTest {

    @Autowired
    RedisTemplate sessionRedisTemplate;

    @Test
    public void testRedisTemplate() {
        notNull(sessionRedisTemplate);
        notNull(sessionRedisTemplate.getDefaultSerializer());
        assertTrue(sessionRedisTemplate.getDefaultSerializer() instanceof GenericJackson2JsonRedisSerializer);
    }
}
