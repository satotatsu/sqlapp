/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.datasource;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.zaxxer.hikari.HikariConfig;

class DataSourceConfigFilesTest {
	@TempDir
	Path directory;

	@Test
	void readsEverySupportedFormat() throws Exception {
		final var yaml = Files.writeString(directory.resolve("dataSource.yaml"),
				"# local HSQLDB\njdbcUrl: jdbc:hsqldb:mem:yaml\nusername: sa\nmaximumPoolSize: 3\n");
		final var yml = Files.writeString(directory.resolve("dataSource.yml"), "jdbcUrl: jdbc:hsqldb:mem:yml\n");
		final var properties = Files.writeString(directory.resolve("dataSource.properties"),
				"jdbcUrl=jdbc:hsqldb:mem:properties\nusername=sa\n");
		final var json = Files.writeString(directory.resolve("dataSource.json"),
				"{\"jdbcUrl\":\"jdbc:hsqldb:mem:json\"}");
		final var xml = Files.writeString(directory.resolve("dataSource.xml"),
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">"
						+ "<properties><entry key=\"jdbcUrl\">jdbc:hsqldb:mem:xml</entry></properties>");
		assertEquals("jdbc:hsqldb:mem:yaml", DataSourceConfigFiles.get(DataSourceConfigFiles.read(yaml), "jdbcUrl"));
		assertEquals(3, DataSourceConfigFiles.read(yaml).get("maximumPoolSize"));
		assertEquals("jdbc:hsqldb:mem:yml", DataSourceConfigFiles.get(DataSourceConfigFiles.read(yml), "JDBCURL"));
		assertEquals("jdbc:hsqldb:mem:properties",
				DataSourceConfigFiles.get(DataSourceConfigFiles.read(properties), "jdbcUrl"));
		assertEquals("jdbc:hsqldb:mem:json", DataSourceConfigFiles.get(DataSourceConfigFiles.read(json), "jdbcUrl"));
		assertEquals("jdbc:hsqldb:mem:xml", DataSourceConfigFiles.get(DataSourceConfigFiles.read(xml), "jdbcUrl"));
	}

	@Test
	void laterFilesOverrideEarlierValuesKeyByKey() throws Exception {
		final var shared = Files.writeString(directory.resolve("dataSource.yaml"),
				"jdbcUrl: jdbc:hsqldb:mem:shared\nusername: app\npassword: committed\n");
		final var secret = Files.writeString(directory.resolve("secret.properties"), "PASSWORD=from-secret-file\n");
		final var settings = DataSourceConfigFiles.read(shared, secret);
		assertEquals("jdbc:hsqldb:mem:shared", DataSourceConfigFiles.get(settings, "jdbcUrl"));
		assertEquals("app", DataSourceConfigFiles.get(settings, "username"));
		assertEquals("from-secret-file", DataSourceConfigFiles.password(settings, name -> null));
		assertEquals(1, settings.keySet().stream().filter("password"::equalsIgnoreCase).count());
	}

	@Test
	void passwordEnvTakesThePasswordFromTheEnvironment() throws Exception {
		final var file = Files.writeString(directory.resolve("dataSource.yaml"),
				"jdbcUrl: jdbc:hsqldb:mem:env\nusername: sa\npassword: ignored\npasswordEnv: SALES_DB_PASSWORD\n");
		final var settings = DataSourceConfigFiles.read(file);
		assertEquals("from-env",
				DataSourceConfigFiles.password(settings, Map.of("SALES_DB_PASSWORD", "from-env")::get));
		final var missing = assertThrows(IllegalArgumentException.class,
				() -> DataSourceConfigFiles.password(settings, name -> null));
		assertTrue(missing.getMessage().contains("SALES_DB_PASSWORD"), missing.getMessage());
		final var config = new HikariConfig();
		DataSourceConfigFiles.applyTo(config, settings, Map.of("SALES_DB_PASSWORD", "from-env")::get);
		assertEquals("from-env", config.getPassword());
		assertEquals("sa", config.getUsername());
	}

	@Test
	void appliesHikariSettingsAndDriverProperties() throws Exception {
		final var file = Files.writeString(directory.resolve("dataSource.yaml"), """
				jdbcUrl: jdbc:hsqldb:mem:apply
				driverClassName: org.hsqldb.jdbc.JDBCDriver
				maximumPoolSize: 4
				dataSourceProperties:
				  cachePrepStmts: true
				""");
		final var properties = Files.writeString(directory.resolve("dataSource.properties"),
				"jdbcUrl=jdbc:hsqldb:mem:apply2\ndataSource.prepStmtCacheSize=250\nconnectionTimeout=5000\n");
		final var yamlConfig = new HikariConfig();
		DataSourceConfigFiles.applyTo(yamlConfig, DataSourceConfigFiles.read(file), name -> null);
		assertEquals("jdbc:hsqldb:mem:apply", yamlConfig.getJdbcUrl());
		assertEquals("org.hsqldb.jdbc.JDBCDriver", yamlConfig.getDriverClassName());
		assertEquals(4, yamlConfig.getMaximumPoolSize());
		assertEquals(true, yamlConfig.getDataSourceProperties().get("cachePrepStmts"));
		final var propertiesConfig = new HikariConfig();
		DataSourceConfigFiles.applyTo(propertiesConfig, DataSourceConfigFiles.read(properties), name -> null);
		assertEquals("250", propertiesConfig.getDataSourceProperties().get("prepStmtCacheSize"));
		assertEquals(5000L, propertiesConfig.getConnectionTimeout());
	}

	@Test
	void writesYamlThatReadsBackUnchanged() throws Exception {
		final var file = directory.resolve("local").resolve("dataSource.yaml");
		Files.createDirectories(file.getParent());
		final var settings = new java.util.LinkedHashMap<String, Object>();
		settings.put("jdbcUrl", "jdbc:sqlserver://localhost;databaseName=master;encrypt=true");
		settings.put("username", "sa");
		settings.put("passwordEnv", "SQLSERVER_PASSWORD");
		DataSourceConfigFiles.writeYaml(file, settings);
		final String text = Files.readString(file);
		assertTrue(text.startsWith("# sqlapp data source settings"), text);
		assertEquals(settings, DataSourceConfigFiles.read(file));
		assertThrows(IllegalArgumentException.class,
				() -> DataSourceConfigFiles.writeYaml(directory.resolve("dataSource.properties"), settings));
	}

	@Test
	void problemsAreReportedInsteadOfIgnored() throws Exception {
		final var missing = assertThrows(IllegalArgumentException.class,
				() -> DataSourceConfigFiles.read(directory.resolve("missing.yaml")));
		assertTrue(missing.getMessage().contains("not found"), missing.getMessage());
		final var conf = Files.writeString(directory.resolve("dataSource.conf"), "jdbcUrl=x");
		assertTrue(assertThrows(IllegalArgumentException.class, () -> DataSourceConfigFiles.read(conf)).getMessage()
				.contains("Unsupported"));
		assertThrows(IllegalArgumentException.class, () -> DataSourceConfigFiles.read(directory));
		final var broken = Files.writeString(directory.resolve("broken.yaml"), "jdbcUrl: [secret-value\n");
		final var parse = assertThrows(IllegalArgumentException.class, () -> DataSourceConfigFiles.read(broken));
		assertFalse(parse.getMessage().contains("secret-value"), "parser output may contain secrets");
		final var typo = Files.writeString(directory.resolve("typo.yaml"), "jdbcUrl: jdbc:hsqldb:mem:t\njdbcUlr: x\n");
		final var config = new HikariConfig();
		assertEquals(java.util.List.of("jdbcUlr"),
				DataSourceConfigFiles.applyTo(config, DataSourceConfigFiles.read(typo), n -> null),
				"unknown keys are skipped as before but reported to the caller");
		assertEquals("jdbc:hsqldb:mem:t", config.getJdbcUrl());
	}
}
