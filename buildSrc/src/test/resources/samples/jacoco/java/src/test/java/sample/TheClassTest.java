package sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class TheClassTest {
	TheClass theClass = new TheClass();

	@Test
	public void doStuffWhenTrueThenTrue() {
		assertThat(theClass.doStuff(true)).isTrue();
	}

	@Test
	public void doStuffWhenTrueThenFalse() {
		assertThat(theClass.doStuff(false)).isFalse();
	}
}