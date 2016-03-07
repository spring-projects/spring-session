package org.springframework.session.data.mongo;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * Utility class to extract principal name from {@code Authentication} object
 *
 * @author Jakub Kubrynski
 */
class AuthenticationParser {

	private static final String NAME_EXPRESSION = "authentication?.name";

	private static final SpelExpressionParser PARSER = new SpelExpressionParser();

	/**
	 * Extracts principal name from authentication
	 *
	 * @param authentication Authentication object
	 * @return principal name
	 */
	static String extractName(Object authentication) {
		if (authentication != null) {
			Expression expression = PARSER.parseExpression(NAME_EXPRESSION);
			return expression.getValue(authentication, String.class);
		}
		return null;
	}
}