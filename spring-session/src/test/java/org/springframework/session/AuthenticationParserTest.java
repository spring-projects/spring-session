package org.springframework.session;

import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jakub Kubrynski
 */
public class AuthenticationParserTest {

    @Test
    public void shouldExtractName() {
        //given
        String principalName = "john_the_springer";
        SecurityContextImpl context = new SecurityContextImpl();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principalName, null));

        //when
        String extractedName = AuthenticationParser.extractName(context);

        //then
        assertThat(extractedName).isEqualTo(principalName);
    }
}