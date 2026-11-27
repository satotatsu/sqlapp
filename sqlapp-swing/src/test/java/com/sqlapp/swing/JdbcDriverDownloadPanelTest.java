/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.driver.JdbcDriverCatalog;
import com.sqlapp.data.db.command.driver.JdbcDriverStore;
import com.sqlapp.data.db.command.driver.MavenCoordinate;

/**
 * Downloading drivers from a local {@code file:} Maven repository; no network.
 */
class JdbcDriverDownloadPanelTest {
	@TempDir
	Path directory;
	private final JdbcDriverLibraries libraries = new JdbcDriverLibraries();
	private final JdbcDriverLibraries nextStart = new JdbcDriverLibraries();

	@AfterEach
	void close() throws Exception {
		libraries.close();
		nextStart.close();
		MissingJdbcDriver.install(null);
	}

	private static void publish(final Path repository, final String path, final byte[] content) throws Exception {
		final Path file = repository.resolve(path);
		Files.createDirectories(file.getParent());
		Files.write(file, content);
		Files.writeString(file.resolveSibling(file.getFileName() + ".sha1"),
				HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(content)));
	}

	private Path repository() throws Exception {
		final var jars = new JdbcDriverJarFixture(directory);
		final Path repository = Files.createDirectories(directory.resolve("maven"));
		publish(repository, "fixture/gui-driver/1.0/gui-driver-1.0.pom",
				("<project><groupId>fixture</groupId><artifactId>gui-driver</artifactId><version>1.0</version>"
						+ "<dependencies><dependency><groupId>fixture</groupId><artifactId>prefix</artifactId>"
						+ "<version>1.0</version></dependency></dependencies></project>")
						.getBytes(StandardCharsets.UTF_8));
		publish(repository, "fixture/gui-driver/1.0/gui-driver-1.0.jar", Files.readAllBytes(jars.driver));
		publish(repository, "fixture/prefix/1.0/prefix-1.0.pom",
				"<project><groupId>fixture</groupId><artifactId>prefix</artifactId><version>1.0</version></project>"
						.getBytes(StandardCharsets.UTF_8));
		publish(repository, "fixture/prefix/1.0/prefix-1.0.jar", Files.readAllBytes(jars.dependency));
		publish(repository, "fixture/gui-driver/maven-metadata.xml",
				"<metadata><versioning><release>1.0</release><versions><version>0.9</version><version>1.0</version></versions></versioning></metadata>"
						.getBytes(StandardCharsets.UTF_8));
		return repository;
	}

	private static void awaitIdle(final JdbcDriverPanel panel) throws Exception {
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		final var busy = new AtomicReference<Boolean>(true);
		while (busy.get()) {
			assertTrue(System.nanoTime() < deadline, "panel stayed busy");
			Thread.sleep(50);
			SwingUtilities.invokeAndWait(() -> busy.set(panel.isBusy()));
		}
	}

	@Test
	void downloadsVerifiesSavesAndLoadsAgainAtTheNextStart() throws Exception {
		final Path repository = repository();
		final var store = new JdbcDriverStore(directory.resolve("home"));
		store.setRepositories(List.of(repository.toUri()));
		final var panel = new AtomicReference<JdbcDriverPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JdbcDriverPanel(libraries, store);
			panel.set(view);
			assertTrue(view.repositoryList.getText().contains(repository.toUri().toString()));
			view.catalog.setSelectedItem(JdbcDriverPanel.OTHER);
			assertEquals("", view.coordinate.getText());
			view.coordinate.setText("fixture:gui-driver");
			view.findVersions();
			assertFalse(view.download.isEnabled(), "busy while reading versions");
			assertTrue(view.cancel.isEnabled());
		});
		awaitIdle(panel.get());
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertEquals(2, view.version.getItemCount());
			assertEquals("1.0", view.version.getSelectedItem(), "release selected");
			view.downloadSelected();
		});
		awaitIdle(panel.get());
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertTrue(
					view.status.getText()
							.startsWith("Downloaded and loaded fixture:gui-driver:1.0: fixture.ChildDriver"),
					view.status.getText());
			assertEquals("fixture.ChildDriver", view.selectedDriver.getSelectedItem());
			assertEquals(1, view.installedModel.size());
			assertFalse(view.cancel.isEnabled());
		});
		assertEquals(2, store.installed().getFirst().files().size(), "driver and its dependency");

		// The next start loads the saved driver without the repository.
		Files.walk(repository).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		final var restarted = new AtomicReference<JdbcDriverPanel>();
		SwingUtilities.invokeAndWait(() -> {
			restarted.set(new JdbcDriverPanel(nextStart, new JdbcDriverStore(directory.resolve("home"))));
			restarted.get().loadInstalled();
		});
		awaitIdle(restarted.get());
		SwingUtilities.invokeAndWait(() -> {
			final var view = restarted.get();
			assertEquals("Loaded 1 downloaded JDBC driver(s) from " + store.home() + ".", view.status.getText());
			assertEquals(List.of("fixture.ChildDriver"), nextStart.libraries().getFirst().classes());
			view.installed.setSelectedIndex(0);
			assertTrue(view.remove.isEnabled());
			view.removeInstalled(MavenCoordinate.parse("fixture:gui-driver:1.0"));
			assertEquals(0, view.installedModel.size());
		});
		assertTrue(store.installed().isEmpty());
	}

	@Test
	void failedDownloadsAreReportedAndNotRemembered() throws Exception {
		final Path repository = repository();
		Files.writeString(repository.resolve("fixture/prefix/1.0/prefix-1.0.jar"), "tampered");
		final var store = new JdbcDriverStore(directory.resolve("home"));
		store.setRepositories(List.of(repository.toUri()));
		final var panel = new AtomicReference<JdbcDriverPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JdbcDriverPanel(libraries, store);
			panel.set(view);
			view.catalog.setSelectedItem(JdbcDriverPanel.OTHER);
			view.coordinate.setText("fixture:gui-driver");
			view.downloadSelected();
			assertEquals("Choose a version first: use Find versions or type one.", view.status.getText());
			view.version.setSelectedItem("1.0");
			view.downloadSelected();
		});
		awaitIdle(panel.get());
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertTrue(view.status.getText().startsWith("Download failed: Checksum mismatch"), view.status.getText());
			assertTrue(view.statusPanel.detailsText().contains("Repositories: " + repository.toUri()));
			assertEquals(0, view.installedModel.size());
			assertNull(view.selectedDriver.getSelectedItem());
		});
	}

	@Test
	void catalogChoiceFillsCoordinateAndDriverClass() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JdbcDriverPanel(libraries, new JdbcDriverStore(directory.resolve("home")));
			final var oracle = JdbcDriverCatalog.forUrl("jdbc:oracle:thin:@h").orElseThrow();
			view.catalog.setSelectedItem(oracle);
			assertEquals("com.oracle.database.jdbc:ojdbc11", view.coordinate.getText());
			assertEquals("oracle.jdbc.OracleDriver", view.downloadClass.getText());
			assertEquals(0, view.installedModel.size());
		});
	}

	@Test
	void missingDriverIsExplainedAndOfferedOncePerUrlType() throws Exception {
		final var offers = new java.util.concurrent.CopyOnWriteArrayList<JdbcDriverCatalog.Entry>();
		MissingJdbcDriver.install(offers::add);
		MissingJdbcDriver.check("jdbc:hsqldb:mem:bundled");
		for (int attempt = 0; attempt < 2; attempt++) {
			final var missing = assertThrows(MissingJdbcDriver.Missing.class,
					() -> MissingJdbcDriver.check("jdbc:oracle:thin:@//db:1521/app"));
			assertEquals("Oracle", missing.entry().name());
			assertTrue(missing.getMessage().contains("Tools › JDBC drivers"));
		}
		final var unknown = assertThrows(MissingJdbcDriver.Missing.class,
				() -> PlanningJdbc.open(new PlanningJdbc.Spec("jdbc:nothing-here:x", "", ""), new char[0]));
		assertNull(unknown.entry());
		SwingUtilities.invokeAndWait(() -> {
		});
		assertEquals(1, offers.size(), "offered once, never downloaded automatically");
	}
}
