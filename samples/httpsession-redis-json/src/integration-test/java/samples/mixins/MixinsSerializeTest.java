package samples.mixins;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
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
import java.util.Arrays;

/**
 * @author jitendra on 28/3/16.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(Application.class)
public class MixinsSerializeTest {

    @Autowired
    GenericJackson2JsonRedisSerializer springSessionDefaultRedisSerializer;

    MockHttpServletRequest request;

    @Before
    public void setup() {
        request = new MockHttpServletRequest("get", "/login");
        request.setCookies(new Cookie("SESSION", "123456789"));
        request.setRemoteAddr("http://localhost:8080/login");
        request.setSession(new MockHttpSession(null, "123456789"));
    }

    @Test
    public void testDefaultTypingIdJson() {
        User user = new User("user", "password", Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
        String expectedJsonString = "{'@class': 'org.springframework.security.core.userdetails.User', 'username': 'user', 'password': 'password', 'enabled': true, 'accountNonExpired': true, 'credentialsNonExpired': true, 'accountNonLocked': true, 'authorities': ['java.util.Collections$UnmodifiableSet', [{'@class': 'org.springframework.security.core.authority.SimpleGrantedAuthority', 'authority': 'ROLE_USER', 'role': 'ROLE_USER'}]]}";
        String serializedJson = new String(springSessionDefaultRedisSerializer.serialize(user));
        JSONAssert.assertEquals(expectedJsonString, serializedJson, true);
    }

    @Test
    public void persistFinalClass() {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("USER");
        String expectedJson = "{'authority': 'USER', 'role': 'USER'}";
        String actualJson = new String(springSessionDefaultRedisSerializer.serialize(authority));
        JSONAssert.assertEquals(expectedJson, actualJson, true);
    }

    @Test
    public void testDefaultCsrfTokenMixin() {
        DefaultCsrfToken token = new DefaultCsrfToken("CSRF_HEADER", "CSRF", "123456789");
        String expectedJson = "{'@class': 'org.springframework.security.web.csrf.DefaultCsrfToken', 'token': '123456789', 'parameterName': 'CSRF', 'headerName': 'CSRF_HEADER'}";
        String serializedString = new String(springSessionDefaultRedisSerializer.serialize(token));
        JSONAssert.assertEquals(expectedJson, serializedString, true);
    }

    @Test
    public void unauthenticatedUsernamePasswordAuthenticationTokenTest() {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken("user", "password");
        String expectedJson = "{'@class': 'org.springframework.security.authentication.UsernamePasswordAuthenticationToken', 'principal': 'user', 'credentials': 'password', 'authenticated': false, 'authorities': ['java.util.ArrayList', []], 'details': null, 'name': 'user'}";
        String actualJson = new String(springSessionDefaultRedisSerializer.serialize(token));
        JSONAssert.assertEquals(expectedJson, actualJson, true);
    }

    @Test
    public void authenticatedUsernamePasswordAuthenticationTokenTest() {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken("user", "password", Arrays.asList(new SimpleGrantedAuthority("USER")));
        String expectedJson = "{'@class': 'org.springframework.security.authentication.UsernamePasswordAuthenticationToken', 'principal': 'user', 'credentials': 'password', 'authenticated': true, 'authorities': ['java.util.ArrayList', [{'@class': 'org.springframework.security.core.authority.SimpleGrantedAuthority', 'authority': 'USER', 'role': 'USER'}]], 'details': null, 'name': 'user'}";
        String actualJson = new String(springSessionDefaultRedisSerializer.serialize(token));
        JSONAssert.assertEquals(expectedJson, actualJson, true);
    }

    @Test
    public void defaultSavedRequestTest() {
        String savedRequestJson = "{ '@class': 'org.springframework.security.web.savedrequest.DefaultSavedRequest', 'serverPort': 80, 'servletPath': ''," +
                "'serverName': 'localhost', 'scheme': 'http', 'requestURL': 'http://localhost/login', 'requestURI': '/login', 'queryString': null," +
                "'pathInfo': null, 'method': 'get', 'contextPath': '', 'parameters': {'@class': 'java.util.TreeMap'}, redirectUrl: 'http://localhost/login', " +
                "'headers': {'@class': 'java.util.TreeMap'}, 'locales': ['java.util.ArrayList', ['en']], 'cookies': ['java.util.ArrayList', " +
                "[{'@class': 'javax.servlet.http.Cookie', 'name': 'SESSION', 'value': '123456789', 'comment': null, domain: null, maxAge: -1, path: null, secure: false, version: 0, 'httpOnly': false}]]," +
                "'headerNames': ['java.util.TreeMap$KeySet', []], 'parameterMap': {'@class': 'java.util.TreeMap'}, 'parameterNames': ['java.util.TreeMap$KeySet', []]}";
        DefaultSavedRequest savedRequest = new DefaultSavedRequest(request, new PortResolverImpl());
        String actualJson = new String(springSessionDefaultRedisSerializer.serialize(savedRequest));
        JSONAssert.assertEquals(savedRequestJson, actualJson, true);
    }

    @Test
    public void webAuthenticationDetailsMixinTest() {
        WebAuthenticationDetails details = new WebAuthenticationDetails(request);
        String expectedJson = "{'@class': 'org.springframework.security.web.authentication.WebAuthenticationDetails', 'remoteAddress': 'http://localhost:8080/login', 'sessionId': '123456789'}";
        String actualJson = new String(springSessionDefaultRedisSerializer.serialize(details));
        JSONAssert.assertEquals(expectedJson, actualJson, true);
    }
}
