package sample.mixins;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.security.web.PortResolver;

import javax.servlet.http.HttpServletRequest;

/**
 * @author jitendra on 8/3/16.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public abstract class DefaultCsrfTokenMixin {

    @JsonCreator
    public DefaultCsrfTokenMixin(@JsonProperty("headerName") String headerName,
                                 @JsonProperty("parameterName") String parameterName,
                                 @JsonProperty("token") String token) {
    }
}
