package sample.mixins;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.Cookie;
import java.io.IOException;

import static sample.utils.JsonNodeExtractor.*;

/**
 * @author jitendra on 22/3/16.
 */
public class HttpCookieDeserializer extends JsonDeserializer<Cookie> {

    @Override
    public Cookie deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode jsonNode = mapper.readTree(p);
        Cookie cookie = new Cookie(getStringValue(jsonNode, "name"), getStringValue(jsonNode, "value"));
        cookie.setComment(getStringValue(jsonNode, "comment"));
        cookie.setDomain(getStringValue(jsonNode, "domain", ""));
        cookie.setMaxAge(getIntValue(jsonNode, "maxAge", -1));
        cookie.setSecure(getBooleanValue(jsonNode, "secure"));
        cookie.setVersion(getIntValue(jsonNode, "version"));
        cookie.setPath(getStringValue(jsonNode, "path"));
        cookie.setHttpOnly(getBooleanValue(jsonNode, "httpOnly", false));
        return cookie;
    }
}
