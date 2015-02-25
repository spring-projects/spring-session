package docs.http;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieHttpSessionStrategy;
import org.springframework.session.web.http.HttpSessionStrategy;

// tag::class[]
@Configuration
@EnableRedisHttpSession
public class CookieHttpSessionConfig {

    @Bean
    public HttpSessionStrategy httpSessionStrategy() {
        CookieHttpSessionStrategy strategy = new CookieHttpSessionStrategy();
        strategy.setCookieName("JSESSIONID");
        strategy.setMaxAge(100);
        return strategy;
    }
}
// end::class[]