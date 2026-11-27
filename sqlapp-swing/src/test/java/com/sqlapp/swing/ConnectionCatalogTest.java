/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.datasource.DataSourceConfigFiles;

/**
 * Named connections in a Gradle-style folder, shared by every connection form.
 */
class ConnectionCatalogTest {
	@TempDir
	Path directory;

	private static final class MemoryStore implements ConnectionCatalog.FolderStore {
		Path saved;

		@Override
		public Path load() {
			return saved;
		}

		@Override
		public void save(final Path folder) {
			saved = folder;
		}
	}

	private Path config() throws Exception {
		final Path config = Files.createDirectories(directory.resolve("src/main/config"));
		Files.createDirectories(config.resolve("local"));
		Files.writeString(config.resolve("local/dataSource.properties"),
				"jdbcUrl=jdbc:hsqldb:mem:local\nusername=root\n");
		Files.createDirectories(config.resolve("sqlserver"));
		Files.writeString(config.resolve("sqlserver/dataSource.yaml"),
				"jdbcUrl: jdbc:hsqldb:mem:sqlserver\nusername: sa\npassword: in-file\nschema: PUBLIC\n");
		Files.createDirectories(config.resolve("empty"));
		return config;
	}

	@Test
	void listsSubfoldersWithSettingsFilesAndRemembersTheFolder() throws Exception {
		final Path config = config();
		final var store = new MemoryStore();
		final var catalog = new ConnectionCatalog(store);
		assertTrue(catalog.entries().isEmpty());
		catalog.setFolder(config);
		assertEquals(java.util.List.of("local", "sqlserver"),
				catalog.entries().stream().map(ConnectionCatalog.Entry::name).toList());
		assertEquals(config.resolve("local/dataSource.properties"), catalog.entries().getFirst().file());
		assertEquals(config.toAbsolutePath().normalize(), store.saved);
		assertEquals(config.toAbsolutePath().normalize(), new ConnectionCatalog(store).folder(), "remembered");
		assertThrows(IllegalArgumentException.class, () -> catalog.setFolder(config.resolve("missing")));
	}

	@Test
	void newConnectionNamesArePlainFolderNames() throws Exception {
		final var catalog = new ConnectionCatalog(new MemoryStore());
		assertThrows(IllegalArgumentException.class, () -> catalog.newConnectionFile("local"), "folder required");
		catalog.setFolder(config());
		assertEquals("dataSource.yaml", catalog.newConnectionFile("staging").getFileName().toString());
		for (final String bad : new String[] { "", "../x", "a/b", "with space", ".hidden", "1db" })
			assertThrows(IllegalArgumentException.class, () -> catalog.newConnectionFile(bad), bad);
	}

	@Test
	void newJobsUseSavedConnectionNamesWithoutJoiningDifferentDatabases() {
		assertEquals(new ConnectionCatalog.JobNames("local", "sqlserver"),
				ConnectionCatalog.jobNames("local", "sqlserver"));
		assertEquals(ConnectionCatalog.JobNames.DEFAULT, ConnectionCatalog.jobNames(null, null));
		assertEquals(new ConnectionCatalog.JobNames("local", "target"), ConnectionCatalog.jobNames("local", null));
		assertEquals(new ConnectionCatalog.JobNames("local", "local"), ConnectionCatalog.jobNames("local", "local"),
				"the same saved connection on both sides is one connection");
		assertEquals(ConnectionCatalog.JobNames.DEFAULT, ConnectionCatalog.jobNames(null, "source"),
				"an unnamed source must not share a name with a different saved target");
		assertEquals(new ConnectionCatalog.JobNames("source", "sqlserver"),
				ConnectionCatalog.jobNames("1db", "sqlserver"),
				"a folder name the job rule rejects falls back to the default");
	}

	@Test
	void schemaPlanUsesTheSavedConnectionsSettingsFile() throws Exception {
		final Path config = config();
		final Path file = config.resolve("sqlserver/dataSource.yaml");
		final var panel = new AtomicReference<SchemaMigrationPlanPanel>();
		final var spec = new AtomicReference<PlanningJdbc.Spec>();
		final var loaded = new CountDownLatch(1);
		final var planned = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var artifact = com.sqlapp.data.db.command.migration.schema.MigrationPlanArtifact
					.of(new com.sqlapp.data.db.command.migration.schema.MigrationPlan(false, null, 30L, 0, 0,
							java.util.List.of(), java.util.List.of(), null));
			final var result = new SchemaMigrationPlanningService.Result(artifact,
					new ReportReviewService().schemaPlan(artifact));
			final var view = new SchemaMigrationPlanPanel(new SchemaMigrationPlanPanel.Planner() {
				@Override
				public SchemaMigrationPlanningService.Result plan(SchemaMigrationPlanningService.Request request,
						String jdbcUrl, String username, char[] password, String driverClass) {
					throw new AssertionError("the screen must pass the whole connection");
				}

				@Override
				public SchemaMigrationPlanningService.Result plan(SchemaMigrationPlanningService.Request request,
						PlanningJdbc.Spec connection, char[] password) {
					spec.set(connection);
					planned.countDown();
					return result;
				}
			}, (artifactResult, path) -> {
			});
			panel.set(view);
			assertNotNull(view.saved.getParent(), "the saved connection list is on the form");
			view.profile.status.addPropertyChangeListener("text", event -> {
				if (String.valueOf(event.getNewValue()).startsWith("Loaded"))
					loaded.countDown();
			});
			view.profile.loadFrom(file);
		});
		assertTrue(loaded.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			panel.get().upDirectory.setText(directory.toString());
			panel.get().build.doClick();
		});
		assertTrue(planned.await(5, TimeUnit.SECONDS));
		assertEquals("jdbc:hsqldb:mem:sqlserver", spec.get().jdbcUrl());
		assertEquals(file.toAbsolutePath().normalize(), spec.get().settingsFile());
	}

	@Test
	void formsSelectAndSaveNamedConnections() throws Exception {
		final Path config = config();
		final var catalog = new ConnectionCatalog(new MemoryStore());
		catalog.setFolder(config);
		final var fields = new AtomicReference<ConnectionFields>();
		final var loaded = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var form = new ConnectionFields("Target database", catalog);
			fields.set(form);
			assertEquals(3, form.savedConnection.getItemCount(), "placeholder + local + sqlserver");
			form.profile.status.addPropertyChangeListener("text", event -> {
				if (String.valueOf(event.getNewValue()).startsWith("Loaded"))
					loaded.countDown();
			});
			form.savedConnection.setSelectedItem("sqlserver");
		});
		assertTrue(loaded.await(5, TimeUnit.SECONDS));
		final var saved = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var form = fields.get();
			assertEquals("jdbc:hsqldb:mem:sqlserver", form.jdbcUrl.getText());
			assertEquals("in-file", new String(form.password.getPassword()));
			assertEquals("sqlserver", form.savedConnection.getSelectedItem());
			assertEquals(config.resolve("sqlserver/dataSource.yaml").toAbsolutePath().normalize(),
					form.snapshot().spec().settingsFile());
			form.jdbcUrl.setText("jdbc:hsqldb:mem:edited");
			assertEquals(0, form.savedConnection.getSelectedIndex(), "an edited form is not that connection");
			assertNull(form.snapshot().spec().settingsFile());
			form.profile.status.addPropertyChangeListener("text", event -> {
				if ("Profile saved".equals(event.getNewValue()))
					saved.countDown();
			});
			form.saveAs("staging");
		});
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		final var stagingFile = config.resolve("staging/dataSource.yaml");
		assertEquals("jdbc:hsqldb:mem:edited",
				DataSourceConfigFiles.get(DataSourceConfigFiles.read(stagingFile), "jdbcUrl"));
		assertNull(DataSourceConfigFiles.get(DataSourceConfigFiles.read(stagingFile), "password"));
		SwingUtilities.invokeAndWait(() -> {
			catalog.changed();
			assertEquals("staging", fields.get().savedConnection.getSelectedItem(), "saved file matches the form");
		});
	}

	@Test
	void settingsFileSuppliesOtherSettingsButNotTheGuiSafetyLimits() throws Exception {
		final Path file = Files.writeString(directory.resolve("dataSource.yaml"),
				"jdbcUrl: jdbc:hsqldb:mem:ignored\nusername: ignored\nschema: PUBLIC\nmaximumPoolSize: 9\nreadOnly: false\n");
		final var spec = new PlanningJdbc.Spec("jdbc:hsqldb:mem:form", "SA", "", file);
		try (var pool = PlanningJdbc.open(spec, new char[0])) {
			assertEquals("jdbc:hsqldb:mem:form", pool.getJdbcUrl(), "the form's URL wins");
			assertEquals("SA", pool.getUsername());
			assertEquals("PUBLIC", pool.getSchema(), "other settings come from the file");
			assertEquals(1, pool.getMaximumPoolSize(), "GUI pool size wins");
			assertTrue(pool.isReadOnly(), "planning stays read-only");
		}
	}
}
