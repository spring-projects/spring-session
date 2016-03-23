package sample.mixins;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author jitendra on 14/3/16.
 */
public abstract class SimpleGrantedAuthorityMixin {

    @JsonCreator
    public SimpleGrantedAuthorityMixin(@JsonProperty("authority") String role) {

    }
}
