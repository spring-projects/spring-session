package sample.mixins;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.Cookie;
import java.io.IOException;

/**
 * @author jitendra on 22/3/16.
 */
public class HttpCookieDeserializer extends JsonDeserializer<Cookie> {

    @Override
    public Cookie deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode jsonNode = mapper.readTree(p);
        Cookie cookie = new Cookie(jsonNode.get("name").asText(), jsonNode.get("value").asText());
        cookie.setComment(jsonNode.get("comment").asText());
        cookie.setDomain(jsonNode.get("domain").asText(""));
        cookie.setMaxAge(jsonNode.get("maxAge").asInt());
        cookie.setSecure(jsonNode.get("secure").asBoolean());
        cookie.setVersion(jsonNode.get("version").asInt());
        cookie.setHttpOnly(jsonNode.get("httpOnly").asBoolean());
        return cookie;
    }
}
