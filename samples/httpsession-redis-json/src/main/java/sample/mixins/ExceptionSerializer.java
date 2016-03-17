package sample.mixins;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.security.authentication.BadCredentialsException;

import java.io.IOException;

/**
 * Created by jitendra on 15/3/16.
 */
public class ExceptionSerializer extends JsonSerializer<BadCredentialsException> {

    @Override
    public void serialize(BadCredentialsException value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeObject(value);
    }
}
