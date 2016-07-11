package sample.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson2.CoreJackson2SimpleModule;
import org.springframework.security.web.jackson2.WebJackson2SimpleModule;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.PUBLIC_ONLY;

/**
 * @author jitendra on 3/3/16.
 */
@EnableRedisHttpSession
public class SessionConfig {

    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        return new GenericJackson2JsonRedisSerializer(objectMapper());
    }

    @Bean
    public JedisConnectionFactory connectionFactory() {
        return new JedisConnectionFactory();
    }

    /**
     * Customized {@link ObjectMapper} to add mix-in for class that doesn't have default constructors
     *
     * @return
     */
    ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setVisibility(
                mapper.getVisibilityChecker()
                        .withFieldVisibility(ANY)
                        .withSetterVisibility(PUBLIC_ONLY)
                        .withGetterVisibility(PUBLIC_ONLY)
                        .withIsGetterVisibility(PUBLIC_ONLY)
        );
        mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        mapper.registerModule(new CoreJackson2SimpleModule());
        mapper.registerModule(new WebJackson2SimpleModule());
        return mapper;
    }
}
