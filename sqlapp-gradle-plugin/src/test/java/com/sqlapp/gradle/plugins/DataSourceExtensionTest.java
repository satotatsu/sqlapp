/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.gradle.plugins.extension.DataSourceExtension;

class DataSourceExtensionTest {
	@TempDir
	Path directory;

	private DataSourceExtension extension() {
		final var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
		return project.getObjects().newInstance(DataSourceExtension.class);
	}

	@Test
	void readsYamlAndLayersAPasswordFile() throws Exception {
		final var shared = Files.writeString(directory.resolve("dataSource.yaml"),
				"jdbcUrl: jdbc:hsqldb:mem:plugin\nusername: sa\nmaximumPoolSize: 2\n");
		final var secret = Files.writeString(directory.resolve("secret.properties"), "password=from-file\n");
		final var extension = extension();
		extension.properties(shared.toFile(), secret.toFile());
		final var config = extension.toConfig();
		assertEquals("jdbc:hsqldb:mem:plugin", config.getJdbcUrl());
		assertEquals("sa", config.getUsername());
		assertEquals(2, config.getMaximumPoolSize());
		assertEquals("from-file", config.getPassword());
	}

	@Test
	void explicitPasswordWinsAndProblemsAreReported() throws Exception {
		final var shared = Files.writeString(directory.resolve("dataSource.yaml"),
				"jdbcUrl: jdbc:hsqldb:mem:plugin\npassword: from-file\n");
		final var extension = extension();
		extension.properties(shared.toFile());
		extension.getPassword().set("explicit");
		extension.getPasswordEnv().set("SQLAPP_TEST_UNSET_PASSWORD_VARIABLE");
		final var unset = assertThrows(IllegalArgumentException.class, extension::toConfig);
		assertTrue(unset.getMessage().contains("SQLAPP_TEST_UNSET_PASSWORD_VARIABLE"), unset.getMessage());
		extension.getPasswordEnv().unset();
		assertEquals("explicit", extension.toConfig().getPassword());

		final var missing = extension();
		missing.properties(directory.resolve("src/main/config/lcoal/dataSource.yaml").toFile());
		final var failure = assertThrows(IllegalArgumentException.class, missing::toConfig);
		assertTrue(failure.getMessage().contains("not found"), failure.getMessage());
	}
}
