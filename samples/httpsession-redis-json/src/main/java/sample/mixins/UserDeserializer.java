package sample.mixins;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.io.IOException;
import java.util.Set;

import static sample.utils.JsonNodeExtractor.*;

/**
 * @author jitendra on 14/3/16.
 */
public class UserDeserializer extends JsonDeserializer<User> {

    @Override
    public User deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode jsonNode = mapper.readTree(p);
        Set<GrantedAuthority> authorities = mapper.readValue(jsonNode.get("authorities").toString(), new TypeReference<Set<GrantedAuthority>>() {});
        return new User(
                getStringValue(jsonNode, "username"),
                getStringValue(jsonNode, "password", ""),
                getBooleanValue(jsonNode, "enabled"),
                getBooleanValue(jsonNode, "accountNonExpired"),
                getBooleanValue(jsonNode, "credentialsNonExpired"),
                getBooleanValue(jsonNode, "accountNonLocked"),
                authorities
        );
    }
}
