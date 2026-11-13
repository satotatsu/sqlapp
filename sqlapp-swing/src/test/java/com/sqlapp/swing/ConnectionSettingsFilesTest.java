/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.datasource.DataSourceConfigFiles;

/**
 * Connection forms read and write the data source settings files used by
 * Gradle.
 */
class ConnectionSettingsFilesTest {
	@TempDir
	Path directory;

	@Test
	void readsSettingsWithPasswordFromFileOrEnvironment() throws Exception {
		final var service = new ConnectionProfileService(Map.of("SQLSERVER_PASSWORD", "from-env")::get);
		final var properties = Files.writeString(directory.resolve("dataSource.properties"),
				"driverClassName=org.hsqldb.jdbc.JDBCDriver\njdbcUrl=jdbc:hsqldb:mem:p\nusername=root\npassword=in-file\n");
		final var fromFile = service.readAny(properties);
		assertEquals("jdbc:hsqldb:mem:p", fromFile.profile().jdbcUrl());
		assertEquals("root", fromFile.profile().username());
		assertEquals("org.hsqldb.jdbc.JDBCDriver", fromFile.profile().driverClass());
		assertArrayEquals("in-file".toCharArray(), fromFile.password());
		assertEquals("Loaded; password from file", fromFile.note());
		assertEquals(properties.toAbsolutePath().normalize(), fromFile.settingsFile());

		final var yaml = Files.writeString(directory.resolve("dataSource.yaml"),
				"jdbcUrl: jdbc:hsqldb:mem:y\nusername: sa\npasswordEnv: SQLSERVER_PASSWORD\n");
		final var fromEnv = service.readAny(yaml);
		assertArrayEquals("from-env".toCharArray(), fromEnv.password());
		assertEquals("Loaded; password from SQLSERVER_PASSWORD", fromEnv.note());

		final var unset = new ConnectionProfileService(name -> null).readAny(yaml);
		assertNull(unset.password());
		assertEquals("Loaded; SQLSERVER_PASSWORD is not set, enter password", unset.note());
	}

	@Test
	void legacyJsonProfilesStillLoadWithoutASettingsFile() throws Exception {
		final var legacy = Files.writeString(directory.resolve("connection.json"),
				"{\"formatVersion\":1,\"jdbcUrl\":\"jdbc:hsqldb:mem:legacy\",\"username\":\"sa\"}");
		final var loaded = new ConnectionProfileService().readAny(legacy);
		assertEquals("jdbc:hsqldb:mem:legacy", loaded.profile().jdbcUrl());
		assertNull(loaded.password());
		assertNull(loaded.settingsFile(), "legacy profiles are not HikariCP settings for Gradle");
		final var settingsJson = Files.writeString(directory.resolve("dataSource.json"),
				"{\"jdbcUrl\":\"jdbc:hsqldb:mem:json\"}");
		assertNotNull(new ConnectionProfileService().readAny(settingsJson).settingsFile());
	}

	@Test
	void savesYamlWithoutPasswordAndKeepsOtherSettings() throws Exception {
		final var file = directory.resolve("dataSource.yaml");
		Files.writeString(file, "jdbcUrl: jdbc:hsqldb:mem:old\nmaximumPoolSize: 3\npasswordEnv: DB_PASSWORD\n");
		new ConnectionProfileService().write(new ConnectionProfileService.Profile(1, "jdbc:hsqldb:mem:new", "sa", ""),
				file);
		final var saved = DataSourceConfigFiles.read(file);
		assertEquals("jdbc:hsqldb:mem:new", DataSourceConfigFiles.get(saved, "jdbcUrl"));
		assertEquals("sa", DataSourceConfigFiles.get(saved, "username"));
		assertEquals(3, saved.get("maximumPoolSize"));
		assertEquals("DB_PASSWORD", DataSourceConfigFiles.get(saved, "passwordEnv"));
		assertNull(DataSourceConfigFiles.get(saved, "driverClassName"));
		assertNull(DataSourceConfigFiles.get(saved, "password"));
		assertThrows(IllegalArgumentException.class,
				() -> new ConnectionProfileService().write(
						new ConnectionProfileService.Profile(1, "jdbc:hsqldb:mem:x", "", ""),
						directory.resolve("a.txt")));
	}

	@Test
	void loadedSettingsFileIsOfferedToGradleUntilTheFormChanges() throws Exception {
		final var file = Files.writeString(directory.resolve("dataSource.yaml"),
				"jdbcUrl: jdbc:hsqldb:mem:form\nusername: sa\npassword: session-only\n");
		final var panel = new AtomicReference<ConnectionProfilePanel>();
		final var loaded = new CountDownLatch(1);
		final var password = new JPasswordField();
		final var url = new JTextField();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new ConnectionProfilePanel(url, new JTextField(), new JTextField(), password);
			panel.set(view);
			view.status.addPropertyChangeListener("text", event -> {
				if (String.valueOf(event.getNewValue()).startsWith("Loaded"))
					loaded.countDown();
			});
			view.loadFrom(file);
		});
		assertTrue(loaded.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("jdbc:hsqldb:mem:form", url.getText());
			assertEquals("session-only", new String(password.getPassword()));
			assertEquals(file.toAbsolutePath().normalize(), panel.get().settingsFile());
			url.setText("jdbc:hsqldb:mem:edited");
			assertNull(panel.get().settingsFile(), "an edited form no longer matches the file");
		});
	}

	@Test
	void gradleGuidesReferenceSettingsFilesWhenKnown() throws Exception {
		final var job = Files.writeString(directory.resolve("transfer.yaml"), "formatVersion: 1\n");
		final var configuration = new com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration("local",
				"sqlserver", java.util.List.of("PUBLIC.ITEMS"),
				com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING);
		final var snapshot = new com.sqlapp.data.db.command.migration.transfer.TransferJobConfigurationIO.Snapshot(job,
				"formatVersion: 1\n", "sha256:" + "a".repeat(64), configuration);
		final var source = directory.resolve("config/local/dataSource.yaml");
		final String both = DirectTransferGradleGuide.text(snapshot, source, null);
		assertTrue(both.contains(
				"sourceDataSource { properties '" + source.toAbsolutePath().normalize().toString().replace('\\', '/')),
				both);
		assertFalse(both.contains("SOURCE_JDBC_URL"), both);
		assertTrue(both.contains("TARGET_JDBC_URL"), "an unknown target keeps the environment form");
		assertEquals(DirectTransferGradleGuide.text(snapshot), DirectTransferGradleGuide.text(snapshot, null, null));
		final String file = FileTransferGradleGuide.text(job, directory.resolve("manifest.json"), "fp", source,
				directory.resolve("config/sqlserver/dataSource.yaml"));
		assertTrue(file.contains("dataSource.properties("), file);
		assertFalse(file.contains("_JDBC_URL"), file);
	}
}
