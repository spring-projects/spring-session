package sample.mixins;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * @author jitendra on 8/3/16.
 */
public abstract class SavedCookieMixin {

    @JsonCreator
    SavedCookieMixin(@JsonProperty("name") String name, @JsonProperty("value") String value, @JsonProperty("comment") String comment,
                     @JsonProperty("domain") String domain, @JsonProperty("maxAge") int maxAge, @JsonProperty("path") String path,
                     @JsonProperty("secure") boolean secure, @JsonProperty("version") int version){
    }
}
