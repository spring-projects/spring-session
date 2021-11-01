package sample;

import org.junit.jupiter.api.Test;

public class DependencyTest {
	@Test
	public void findsDependencyOnClasspath() {
		new Dependency();
	}
}