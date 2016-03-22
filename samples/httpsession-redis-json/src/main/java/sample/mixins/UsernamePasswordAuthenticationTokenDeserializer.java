package sample.mixins;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.io.IOException;
import java.util.List;

/**
 * @author jitendra on 9/3/16.
 */
public class UsernamePasswordAuthenticationTokenDeserializer extends JsonDeserializer<UsernamePasswordAuthenticationToken> {

    @Override
    public UsernamePasswordAuthenticationToken deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        UsernamePasswordAuthenticationToken token = null;
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode jsonNode = mapper.readTree(p);
        Boolean authenticated = jsonNode.get("authenticated").asBoolean();
        String principalJson = jsonNode.get("principal").toString();
        Object principal = principalJson;
        if(principalJson.contains("@class")) {
            principal = mapper.readValue(principalJson, new TypeReference<User>() {});
        }
        Object credentials = jsonNode.get("credentials");
        List<GrantedAuthority> authorities = mapper.readValue(jsonNode.get("authorities").toString(), new TypeReference<List<GrantedAuthority>>() {
        });
        if (authenticated) {
            token = new UsernamePasswordAuthenticationToken(principal, credentials, authorities);
        } else {
            token = new UsernamePasswordAuthenticationToken(principal, credentials);
        }
        token.setDetails(jsonNode.get("details"));
        return token;
    }
}
