package sample.mixins;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

/**
 * @author jitendra on 14/3/16.
 */
public abstract class UnmodifiableSetMixin {

    @JsonCreator
    UnmodifiableSetMixin(@JsonProperty("s") Set s) {
    }
}
