package sample;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ViewResolverRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;

/**
 * @author Rob Winch
 * @since 5.0
 */
@Configuration
public class ThymeleafWebfluxConfig implements WebFluxConfigurer {

	@Autowired(required = false)
	List<ViewResolver> views = new ArrayList<>();

	@Override
	public void configureViewResolvers(ViewResolverRegistry registry) {
		for(ViewResolver view : views) {
			registry.viewResolver(view);
		}
	}
}
