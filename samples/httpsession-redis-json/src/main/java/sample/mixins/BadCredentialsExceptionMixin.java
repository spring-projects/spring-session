package sample.mixins;

import com.fasterxml.jackson.annotation.*;

/**
 * @author jitendra on 15/3/16.
 */
@JsonIdentityInfo(generator=ObjectIdGenerators.IntSequenceGenerator.class, property="@id")
@JsonIgnoreProperties({"cause", "@id"})
public class BadCredentialsExceptionMixin {

    @JsonCreator
    public BadCredentialsExceptionMixin(@JsonProperty("detailMessage") String msg) {
    }
}
