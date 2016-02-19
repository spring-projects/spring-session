package org.springframework.session;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * Utility class to extract principal name from {@code Authentication} object
 *
 * @author Rob Winch
 * @author Jakub Kubrynski
 */
public final class AuthenticationParser {

    private static final String NAME_EXPRESSION = "authentication?.name";

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    /**
     * Extracts principal name from authentication
     *
     * @param authentication Authentication object
     * @return principal name
     */
    public static String extractName(Object authentication) {
        if (authentication != null) {
            Expression expression = PARSER.parseExpression(NAME_EXPRESSION);
            return expression.getValue(authentication, String.class);
        }
        return null;
    }
}
