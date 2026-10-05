/*
 * Copyright 2006 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2006-present the original author or authors.
 */

package io.spring.gradle.convention;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import org.springframework.gradle.maven.SpringMavenPlugin;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies only the wiring {@link SpringMavenPlugin} adds. What the applied release-tools
 * plugins do (POM generation, license checks, signing, deployment) is tested in release-tools.
 */
public class SpringMavenPluginTest {

	@Test
	public void applyWhenJavaPluginThenPublishPluginsAppliedAndJarsRegistered() {
		Project project = ProjectBuilder.builder().build();
		project.getPlugins().apply("java");
		project.getPlugins().apply(SpringMavenPlugin.class);

		assertThat(project.getPlugins().hasPlugin("maven-publish")).isTrue();
		assertThat(project.getTasks().findByName("javadocJar")).isNotNull();
		assertThat(project.getTasks().findByName("sourcesJar")).isNotNull();
	}

	@Test
	public void applyWhenJavaPluginAppliedLaterThenJarsRegistered() {
		Project project = ProjectBuilder.builder().build();
		project.getPlugins().apply(SpringMavenPlugin.class);
		project.getPlugins().apply("java");

		assertThat(project.getTasks().findByName("javadocJar")).isNotNull();
		assertThat(project.getTasks().findByName("sourcesJar")).isNotNull();
	}

	@Test
	public void applyWhenNoJavaPluginThenJarsNotRegistered() {
		Project project = ProjectBuilder.builder().build();
		project.getPlugins().apply(SpringMavenPlugin.class);

		assertThat(project.getPlugins().hasPlugin("maven-publish")).isTrue();
		assertThat(project.getTasks().findByName("javadocJar")).isNull();
	}

}
