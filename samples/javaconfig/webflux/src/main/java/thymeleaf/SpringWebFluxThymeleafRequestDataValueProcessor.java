package thymeleaf;

import java.util.Map;
import java.util.Optional;

import org.springframework.web.reactive.result.view.RequestDataValueProcessor;
import org.springframework.web.server.ServerWebExchange;
import org.thymeleaf.spring5.context.IThymeleafRequestDataValueProcessor;

/**
 * <p>
 *   Implementation of the {@link IThymeleafRequestDataValueProcessor} interface, meant to wrap a Spring
 *   {@link RequestDataValueProcessor} object.
 * </p>
 *
 * @see RequestDataValueProcessor
 *
 * @author Daniel Fern&aacute;ndez
 *
 * @since 3.0.3
 *
 */
class SpringWebFluxThymeleafRequestDataValueProcessor implements IThymeleafRequestDataValueProcessor {

	private final RequestDataValueProcessor requestDataValueProcessor;
	private final ServerWebExchange exchange;

	SpringWebFluxThymeleafRequestDataValueProcessor(
			final RequestDataValueProcessor requestDataValueProcessor, final ServerWebExchange exchange) {
		super();
		this.requestDataValueProcessor = requestDataValueProcessor;
		this.exchange = exchange;
	}

	@Override
	public String processAction(final String action, final String httpMethod) {
		if (this.requestDataValueProcessor == null) {
			// The presence of a Request Data Value Processor is optional
			return action;
		}
		return this.requestDataValueProcessor.processAction(this.exchange, action, httpMethod);
	}

	@Override
	public String processFormFieldValue(final String name, final String value, final String type) {
		if (this.requestDataValueProcessor == null) {
			// The presence of a Request Data Value Processor is optional
			return value;
		}
		return this.requestDataValueProcessor.processFormFieldValue(this.exchange, name, value, type);
	}

	@Override
	public Map<String, String> getExtraHiddenFields() {
		if (this.requestDataValueProcessor == null) {
			// The presence of a Request Data Value Processor is optional
			return null;
		}
		return this.requestDataValueProcessor.getExtraHiddenFields(this.exchange);
	}

	@Override
	public String processUrl(final String url) {
		if (this.requestDataValueProcessor == null) {
			// The presence of a Request Data Value Processor is optional
			return url;
		}
		return this.requestDataValueProcessor.processUrl(this.exchange, url);
	}

}

