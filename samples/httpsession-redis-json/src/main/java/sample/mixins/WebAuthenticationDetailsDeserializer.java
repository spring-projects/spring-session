package sample.mixins;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.io.IOException;
import java.util.Enumeration;

/**
 * Created by jitendra on 14/3/16.
 */
public class WebAuthenticationDetailsDeserializer extends JsonDeserializer<WebAuthenticationDetails> {


    @Override
    public WebAuthenticationDetails deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode jsonNode = mapper.readTree(p);
        DummyHttpSession session = new DummyHttpSession();
        session.setId(jsonNode.get("sessionId").asText());
        DefaultSavedRequestDeserializer.DummyServletRequest request = new DefaultSavedRequestDeserializer.DummyServletRequest();
        request.setRemoteAddr(jsonNode.get("remoteAddress").asText());
        request.setSession(session);
        return new WebAuthenticationDetails(request);
    }

    protected static class DummyHttpSession implements HttpSession {

        private String id;

        @Override
        public long getCreationTime() {
            return 0;
        }

        @Override
        public String getId() {
            return this.id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @Override
        public long getLastAccessedTime() {
            return 0;
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public void setMaxInactiveInterval(int interval) {

        }

        @Override
        public int getMaxInactiveInterval() {
            return 0;
        }

        @Override
        public HttpSessionContext getSessionContext() {
            return null;
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public Object getValue(String name) {
            return null;
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return null;
        }

        @Override
        public String[] getValueNames() {
            return new String[0];
        }

        @Override
        public void setAttribute(String name, Object value) {

        }

        @Override
        public void putValue(String name, Object value) {

        }

        @Override
        public void removeAttribute(String name) {

        }

        @Override
        public void removeValue(String name) {

        }

        @Override
        public void invalidate() {

        }

        @Override
        public boolean isNew() {
            return false;
        }
    }
}
