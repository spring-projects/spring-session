package sample.mixins;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.springframework.security.web.PortResolverImpl;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.SavedCookie;
import org.springframework.util.ObjectUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * @author jitendra on 8/3/16.
 */
public class DefaultSavedRequestDeserializer extends StdDeserializer<DefaultSavedRequest> {

    public DefaultSavedRequestDeserializer(Class<DefaultSavedRequest> requestClass) {
        super(requestClass);
    }

    @Override
    public DefaultSavedRequest deserialize(JsonParser p, DeserializationContext context) throws IOException, JsonProcessingException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode jsonNode = mapper.readTree(p);
        DummyServletRequest request = new DummyServletRequest();
        request.setContextPath(jsonNode.get("contextPath").asText());
        request.setMethod(jsonNode.get("method").asText());
        request.setPathInfo(jsonNode.get("pathInfo").asText());
        request.setQueryString(jsonNode.get("queryString").asText());
        request.setRequestURI(jsonNode.get("requestURI").asText());
        request.setRequestURL(jsonNode.get("requestURL").asText());
        request.setScheme(jsonNode.get("scheme").asText());
        request.setServerName(jsonNode.get("serverName").asText());
        request.setServletPath(jsonNode.get("servletPath").asText());
        request.setServerPort(jsonNode.get("serverPort").asInt());
        List<SavedCookie> savedCookies = mapper.readValue(jsonNode.get("cookies").toString(), new TypeReference<List<SavedCookie>>() {
        });
        Map<String, String[]> params = mapper.readValue(jsonNode.get("parameters").toString(), new TypeReference<Map<String, String[]>>() {
        });
        ArrayList<Locale> locales = mapper.readValue(jsonNode.get("locales").toString(), new TypeReference<List<Locale>>() {
        });
        Map<String, List<String>> headers = mapper.readValue(jsonNode.get("headers").toString(), new TypeReference<Map<String, List<String>>>() {
        });
        request.setSavedCookies(savedCookies);
        request.setParameters(params);
        request.setLocales(locales);
        request.setHeaders(headers);
        return new DefaultSavedRequest(request, new PortResolverImpl());
    }

    protected static class DummyServletRequest extends HttpServletRequestWrapper {
        private static final HttpServletRequest UNSUPPORTED_REQUEST = (HttpServletRequest) Proxy
                .newProxyInstance(DummyServletRequest.class.getClassLoader(),
                        new Class[]{HttpServletRequest.class},
                        new UnsupportedOperationExceptionInvocationHandler());

        private String method;
        private String pathInfo;
        private String queryString;
        private String requestURI;
        private int serverPort;
        private String requestURL;
        private String scheme;
        private String serverName;
        private String contextPath;
        private String servletPath;
        private Cookie[] cookies;
        private String remoteAddress;
        private ArrayList<Locale> locales = new ArrayList<Locale>();
        private Map<String, List<String>> headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        private Map<String, String[]> parameters = new TreeMap<String, String[]>();
        private HttpSession session;

        public DummyServletRequest() {
            super(UNSUPPORTED_REQUEST);
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public void setPathInfo(String pathInfo) {
            this.pathInfo = pathInfo;
        }

        public void setQueryString(String queryString) {
            this.queryString = queryString;
        }

        public void setRequestURI(String requestURI) {
            this.requestURI = requestURI;
        }

        public void setServerPort(int serverPort) {
            this.serverPort = serverPort;
        }

        public void setRequestURL(String requestURL) {
            this.requestURL = requestURL;
        }

        public void setScheme(String scheme) {
            this.scheme = scheme;
        }

        public void setServerName(String serverName) {
            this.serverName = serverName;
        }

        public void setContextPath(String contextPath) {
            this.contextPath = contextPath;
        }

        public void setServletPath(String servletPath) {
            this.servletPath = servletPath;
        }

        public void setCookies(Cookie[] cookies) {
            this.cookies = cookies;
        }

        public void setSavedCookies(List<SavedCookie> cookies) {
            if (!ObjectUtils.isEmpty(cookies)) {
                Cookie[] cookieArray = new Cookie[cookies.size()];
                int index = 0;
                for (SavedCookie cookie : cookies) {
                    Cookie httpCookie = new Cookie(cookie.getName(), cookie.getValue());
                    httpCookie.setComment(cookie.getComment());
                    if (!ObjectUtils.isEmpty(cookie.getDomain()))
                        httpCookie.setDomain(cookie.getDomain());
                    httpCookie.setMaxAge(cookie.getMaxAge());
                    httpCookie.setSecure(cookie.isSecure());
                    httpCookie.setVersion(cookie.getVersion());
                    httpCookie.setPath(cookie.getPath());
                    cookieArray[index++] = httpCookie;
                }
                setCookies(cookieArray);
            }
        }

        @Override
        public Cookie[] getCookies() {
            return cookies;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return parameters;
        }

        public void setParameters(Map<String, String[]> params) {
            this.parameters = params;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            return super.getParameterValues(name);
        }

        public void setLocales(ArrayList<Locale> locales) {
            this.locales = locales;
        }

        public void setHeaders(Map<String, List<String>> headers) {
            this.headers = headers;
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return Collections.enumeration(headers.get(name));
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(headers.keySet());
        }

        @Override
        public String getMethod() {
            return method;
        }

        @Override
        public String getPathInfo() {
            return pathInfo;
        }

        @Override
        public String getQueryString() {
            return (queryString == null || "null".equals(queryString)) ? "" : queryString;
        }

        @Override
        public String getRequestURI() {
            return requestURI;
        }

        @Override
        public int getServerPort() {
            return serverPort;
        }

        @Override
        public StringBuffer getRequestURL() {
            return new StringBuffer(requestURL);
        }

        @Override
        public String getScheme() {
            return scheme;
        }

        @Override
        public String getServerName() {
            return serverName;
        }

        @Override
        public String getContextPath() {
            return contextPath;
        }

        @Override
        public String getServletPath() {
            return servletPath;
        }

        @Override
        public Enumeration<Locale> getLocales() {
            return Collections.enumeration(locales);
        }

        public Map<String, List<String>> getHeaders() {
            return headers;
        }

        public Map<String, String[]> getParameters() {
            return parameters;
        }

        public String getRemoteAddr() {
            return remoteAddress;
        }

        public void setRemoteAddr(String remoteAddress) {
            this.remoteAddress = remoteAddress;
        }

        @Override
        public HttpSession getSession() {
            return session;
        }

        @Override
        public HttpSession getSession(boolean create) {
            return session;
        }

        public void setSession(HttpSession session) {
            this.session = session;
        }
    }
}

