package samples.mixins;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import sample.Application;

import javax.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author jitendra on 28/3/16.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(Application.class)
public class MixinsDeserilizeTest {

    @Autowired
    GenericJackson2JsonRedisSerializer redisSerializer;

    @Test
    public void defaultCsrfTokenMixin() {
        String tokenJson = "{\"@class\": \"org.springframework.security.web.csrf.DefaultCsrfToken\", \"token\": \"123456\", \"parameterName\": \"_csrf\", \"headerName\": \"x-csrf-header\"}";
        DefaultCsrfToken token = redisSerializer.deserialize(tokenJson.getBytes(), DefaultCsrfToken.class);
        assertThat(token)
                .hasFieldOrPropertyWithValue("token", "123456")
                .hasFieldOrPropertyWithValue("parameterName", "_csrf")
                .hasFieldOrPropertyWithValue("headerName", "x-csrf-header");
    }

    @Test
    public void httpCookieTest() {
        String httpCookie = "{\"@class\": \"javax.servlet.http.Cookie\", \"name\": \"SESSION\", \"value\": \"123456789\", \"maxAge\": 1000, \"path\": \"/\", \"secure\": true, \"version\": 0, \"httpOnly\": true}";
        Cookie cookie = redisSerializer.deserialize(httpCookie.getBytes(), Cookie.class);
        assertThat(cookie).hasFieldOrPropertyWithValue("name", "SESSION")
                .hasFieldOrPropertyWithValue("value", "123456789")
                .hasFieldOrPropertyWithValue("secure", true)
                .hasFieldOrPropertyWithValue("comment", "")
                .hasFieldOrPropertyWithValue("path", "/")
                .hasFieldOrPropertyWithValue("maxAge", 1000)
                .hasFieldOrPropertyWithValue("httpOnly", true);
    }

    @Test(expected = SerializationException.class)
    public void simpleGrantedAuthorityWithoutTypeIdTest() {
        String authorityJson = "{\"authority\": \"ROLE_USER\"}";
        SimpleGrantedAuthority authority = redisSerializer.deserialize(authorityJson.getBytes(), SimpleGrantedAuthority.class);
        assertThat(authority.getAuthority()).isEqualTo("ROLE_USER");
    }

    @Test
    public void simpleGrantedAuthorityWithTypeIdTest() {
        String authorityJson = "{\"@class\": \"org.springframework.security.core.authority.SimpleGrantedAuthority\", \"role\": \"ROLE_USER\"}";
        SimpleGrantedAuthority authority = redisSerializer.deserialize(authorityJson.getBytes(), SimpleGrantedAuthority.class);
        assertThat(authority.getAuthority()).isEqualTo("ROLE_USER");
    }

    @Test
    public void userTest() {
        String userJson = "{\"@class\": \"org.springframework.security.core.userdetails.User\", \"username\": \"user\", \"password\": \"password\", \"authorities\": [\"java.util.Collections$UnmodifiableSet\", [{\"@class\": \"org.springframework.security.core.authority.SimpleGrantedAuthority\", \"role\": \"ROLE_USER\"}]], \"accountNonExpired\": true, \"accountNonLocked\": true, \"credentialsNonExpired\": true, \"enabled\": true}";
        User user = redisSerializer.deserialize(userJson.getBytes(), User.class);
        assertThat(user.getUsername()).isEqualTo("user");
        assertThat(user.getPassword()).isEqualTo("password");
        assertThat(user.getAuthorities()).contains(new SimpleGrantedAuthority("ROLE_USER"));
        assertThat(user.isEnabled()).isEqualTo(true);
        assertThat(user.isAccountNonExpired()).isEqualTo(true);
        assertThat(user.isAccountNonLocked()).isEqualTo(true);
        assertThat(user.isCredentialsNonExpired()).isEqualTo(true);
    }

    @Test
    public void unauthenticatedUsernamePasswordAuthenticationTokenTest() {
        String unauthenticatedTokenJson = "{\"@class\": \"org.springframework.security.authentication.UsernamePasswordAuthenticationToken\"," +
                "\"principal\": \"user\", \"credentials\": \"password\", \"details\": null, \"authorities\": [\"java.util.ArrayList\", []]," +
                "\"authenticated\": false}";
        UsernamePasswordAuthenticationToken token = redisSerializer.deserialize(unauthenticatedTokenJson.getBytes(), UsernamePasswordAuthenticationToken.class);
        assertThat(token.getPrincipal()).isEqualTo("user");
        assertThat(token.getCredentials()).isEqualTo("password");
        assertThat(token.isAuthenticated()).isEqualTo(false);
        assertThat(token.getAuthorities()).hasSize(0);
    }

    @Test
    public void unauthenticatedUsernamePasswordAuthenticationTokenWithUserAsPrincipalTest() {
        String unauthenticatedTokenJson = "{\"@class\": \"org.springframework.security.authentication.UsernamePasswordAuthenticationToken\"," +
                "\"principal\": {\"@class\": \"org.springframework.security.core.userdetails.User\", \"username\": \"user\", \"password\": \"password\", " +
                "\"authorities\": [\"java.util.Collections$UnmodifiableSet\", [{\"@class\": \"org.springframework.security.core.authority.SimpleGrantedAuthority\"," +
                " \"role\": \"ROLE_USER\"}]], \"accountNonExpired\": true, \"accountNonLocked\": true, \"credentialsNonExpired\": true, \"enabled\": true}, " +
                "\"credentials\": \"password\", \"details\": null, \"authorities\": [\"java.util.ArrayList\", []], \"authenticated\": false}";
        UsernamePasswordAuthenticationToken token = redisSerializer.deserialize(unauthenticatedTokenJson.getBytes(), UsernamePasswordAuthenticationToken.class);
        assertThat(token.getPrincipal()).isInstanceOf(User.class);
        User user = (User) token.getPrincipal();
        assertThat(user.getUsername()).isEqualTo("user");
        assertThat(user.getPassword()).isEqualTo("password");
        assertThat(user.getAuthorities()).contains(new SimpleGrantedAuthority("ROLE_USER"));
        assertThat(user.isEnabled()).isEqualTo(true);
        assertThat(user.isAccountNonExpired()).isEqualTo(true);
        assertThat(user.isAccountNonLocked()).isEqualTo(true);
        assertThat(user.isCredentialsNonExpired()).isEqualTo(true);
    }

    @Test
    public void authenticatedUsernamePasswordAuthenticationTokenTest() {
        String unauthenticatedTokenJson = "{\"@class\": \"org.springframework.security.authentication.UsernamePasswordAuthenticationToken\"," +
                "\"principal\": \"user\", \"credentials\": \"password\", \"details\": null, \"authorities\": [\"java.util.ArrayList\", " +
                "[{\"@class\": \"org.springframework.security.core.authority.SimpleGrantedAuthority\", \"role\": \"ROLE_USER\"}]]," +
                "\"authenticated\": true}";
        UsernamePasswordAuthenticationToken authenticationToken = redisSerializer.deserialize(unauthenticatedTokenJson.getBytes(), UsernamePasswordAuthenticationToken.class);
        assertThat(authenticationToken.getPrincipal()).isEqualTo("user");
        assertThat(authenticationToken.getCredentials()).isEqualTo("password");
        assertThat(authenticationToken.isAuthenticated()).isEqualTo(true);
        assertThat(authenticationToken.getAuthorities()).hasSize(1);
        assertThat(authenticationToken.getAuthorities()).contains(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    public void webAuthenticationDetailTest() {
        String authenticationDetailJson = "{\"@class\": \"org.springframework.security.web.authentication.WebAuthenticationDetails\"," +
                "\"remoteAddress\": \"http://localhost/login\", \"sessionId\": \"123456789\"}";
        WebAuthenticationDetails details = redisSerializer.deserialize(authenticationDetailJson.getBytes(), WebAuthenticationDetails.class);
        assertThat(details.getRemoteAddress()).isEqualTo("http://localhost/login");
        assertThat(details.getSessionId()).isEqualTo("123456789");
    }
}
