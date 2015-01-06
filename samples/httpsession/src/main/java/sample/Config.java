package sample;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.jedis.*;
import org.springframework.session.data.redis.config.annotation.web.http.*;

@Import(EmbeddedRedisConfiguration.class) // <1>
@EnableRedisHttpSession // <2>
public class Config {

    @Bean
    public JedisConnectionFactory connectionFactory() {
        return new JedisConnectionFactory(); // <3>
    }
}
