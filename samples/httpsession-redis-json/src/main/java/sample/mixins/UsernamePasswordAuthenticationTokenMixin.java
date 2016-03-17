package sample.mixins;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * @author jitendra on 9/3/16.
 */
public abstract class UsernamePasswordAuthenticationTokenMixin {

    @JsonCreator
    UsernamePasswordAuthenticationTokenMixin(@JsonProperty("principal") Object principal,
                                             @JsonProperty("credentials") Object credentials,
                                             @JsonProperty("authorities") Collection<? extends GrantedAuthority> authorities) {
    }

    @JsonIgnore public abstract void setAuthenticated(boolean isAuthenticated);
}
