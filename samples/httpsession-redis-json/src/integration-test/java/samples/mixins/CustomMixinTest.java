package samples.mixins;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.PortResolverImpl;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import sample.Application;

import javax.servlet.http.Cookie;
import java.net.URISyntaxException;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.springframework.util.Assert.hasLength;

/**
 * @author jitendra on 22/3/16.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(Application.class)
public class CustomMixinTest {

    @Autowired
    GenericJackson2JsonRedisSerializer springSessionDefaultRedisSerializer;

    MockHttpServletRequest request;

    @Before
    public void setup() {
        request = new MockHttpServletRequest("get", "http://localhost:8080/login");
        request.setCookies(new Cookie("SESSION", "123456789"));
        request.addHeader("token", "aabedef3724");
        request.setRemoteAddr("http://localhost:8080/login");
        request.setSession(new MockHttpSession(null, "aa12bb"));
    }

    @Test
    public void testDefaultTypingIdField() {
        User user = new User("user", "password", Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
        byte[] serializedBytes = springSessionDefaultRedisSerializer.serialize(user);
        String serializedString = new String(serializedBytes);
        assertTrue(serializedString.contains("@class"));
        assertTrue(serializedString.contains("org.springframework.security.core.userdetails.User"));
        assertTrue(serializedString.contains("org.springframework.security.core.authority.SimpleGrantedAuthority"));
    }

    @Test
    public void persistFinalClass() {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("USER");
        byte[] serializedBytes = springSessionDefaultRedisSerializer.serialize(authority);
        String serializedString = new String(serializedBytes);
        assertFalse(serializedString.contains("@class"));
    }

    @Test
    public void testDefaultCsrfTokenMixin() {
        DefaultCsrfToken token = new DefaultCsrfToken("CSRF_HEADER", "CSRF", "123456789");
        byte[] bytes = springSessionDefaultRedisSerializer.serialize(token);
        String serializedString = new String(springSessionDefaultRedisSerializer.serialize(token));
        hasLength(serializedString);
        assertTrue(serializedString.contains("@class"));
        assertTrue(serializedString.contains("DefaultCsrfToken"));
        assertTrue(serializedString.contains("123456789"));
        Object object = springSessionDefaultRedisSerializer.deserialize(bytes);
        assertTrue(object instanceof DefaultCsrfToken);
    }

    @Test
    public void usernamePasswordAuthenticationTokenUnauthenticatedTokenTest() {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken("user", "password");
        System.out.println(token.isAuthenticated());
        byte[] bytes = springSessionDefaultRedisSerializer.serialize(token);
        Object authenticationToken = springSessionDefaultRedisSerializer.deserialize(bytes);
        assertNotNull(authenticationToken);
        assertTrue(authenticationToken instanceof UsernamePasswordAuthenticationToken);
        UsernamePasswordAuthenticationToken authToken = (UsernamePasswordAuthenticationToken) authenticationToken;
        assertTrue(authToken.isAuthenticated() == token.isAuthenticated());
    }

    @Test
    public void usernamePasswordAuthenticationTokenAuthenticatedTokenTest() {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken("user", "password", Arrays.asList(new SimpleGrantedAuthority("USER")));
        System.out.println(token.isAuthenticated());
        byte[] bytes = springSessionDefaultRedisSerializer.serialize(token);
        Object authenticationToken = springSessionDefaultRedisSerializer.deserialize(bytes);
        assertNotNull(authenticationToken);
        assertTrue(authenticationToken instanceof UsernamePasswordAuthenticationToken);
        UsernamePasswordAuthenticationToken authToken = (UsernamePasswordAuthenticationToken) authenticationToken;
        assertTrue(token.isAuthenticated() == authToken.isAuthenticated());
    }

    @Test
    public void defaultSavedRequestMixinTest() throws URISyntaxException {
        DefaultSavedRequest savedRequest = new DefaultSavedRequest(request, new PortResolverImpl());
        byte[] savedRequestBytes = springSessionDefaultRedisSerializer.serialize(savedRequest);
        assertNotNull(savedRequestBytes);

        DefaultSavedRequest deserializedRequest = springSessionDefaultRedisSerializer.deserialize(savedRequestBytes, DefaultSavedRequest.class);
        assertNotNull(deserializedRequest);
        assertEquals(deserializedRequest.getServerPort(), request.getServerPort());
        assertEquals(deserializedRequest.getRequestURI(), request.getRequestURI());
        assertEquals(deserializedRequest.getCookies().size(), request.getCookies().length);
        assertEquals(deserializedRequest.getHeaderValues("token").get(0), request.getHeader("token"));
    }

    @Test
    public void userMixinTestWithNullPassword() {
        User user = new User("user", "password", Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
        user.eraseCredentials();

        byte[] bytes = springSessionDefaultRedisSerializer.serialize(user);

        User deserializedUser = springSessionDefaultRedisSerializer.deserialize(bytes, User.class);
        assertNotNull(deserializedUser);
        assertEquals(deserializedUser.getPassword(), "");
        assertEquals(deserializedUser.getUsername(), user.getUsername());
    }

    @Test
    public void webAuthenticationDetailsMixinTest() {
        WebAuthenticationDetails details = new WebAuthenticationDetails(request);
        byte[] bytes = springSessionDefaultRedisSerializer.serialize(details);

        WebAuthenticationDetails authenticationDetails = springSessionDefaultRedisSerializer.deserialize(bytes, WebAuthenticationDetails.class);
        assertNotNull(authenticationDetails);
        assertEquals(authenticationDetails.getRemoteAddress(), details.getRemoteAddress());
        assertEquals(authenticationDetails.getSessionId(), details.getSessionId());
    }
}
