package sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

public class HelloServletTest {

	@Test
	public void hello() throws Exception {
		String url = System.getProperty("app.baseURI");
		try (InputStream get = new URL(url).openConnection().getInputStream()) {
			String hello = IOUtils.toString(get, Charset.defaultCharset());
			assertThat(hello).isEqualTo("Hello");
		}

	}
}
