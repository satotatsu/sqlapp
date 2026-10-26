/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcDriverPanelTest {
	@Test
	void explicitAdditionalClassFromSameJarAppearsWithoutChangingCurrentSelection() throws Exception {
		final var jars = new JdbcDriverJarFixture(directory);
		libraries.add(List.of(jars.secondShared, jars.dependency), "");
		final var panel = new AtomicReference<JdbcDriverPanel>();
		final var finished = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JdbcDriverPanel(libraries);
			panel.set(view);
			assertEquals("fixture.SecondDriver", view.selectedDriver.getSelectedItem());
			onEnabled(view, finished);
			view.loadFrom(List.of(jars.secondShared, jars.dependency), "fixture.ChildDriver");
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertEquals(2, view.selectedDriver.getItemCount());
			assertEquals("fixture.SecondDriver", view.selectedDriver.getSelectedItem());
			assertEquals("fixture.ChildDriver", view.selectedDriver.getItemAt(1));
			assertTrue(view.status.getText().startsWith("Loaded 1 JDBC driver"));
			assertFalse(view.isBusy());
		});
	}

	@Test
	void loadingAnotherDriverWithSharedDependencyRetainsSelectedClass() throws Exception {
		final var jars = new JdbcDriverJarFixture(directory);
		libraries.add(List.of(jars.driver, jars.dependency), "");
		final var panel = new AtomicReference<JdbcDriverPanel>();
		final var finished = new CountDownLatch(1);
		final var selected = new AtomicReference<String>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JdbcDriverPanel(libraries);
			panel.set(view);
			view.setConnectionReceivers(null, null, name -> {
				selected.set(name);
				return true;
			});
			assertEquals("fixture.ChildDriver", view.selectedDriver.getSelectedItem());
			onEnabled(view, finished);
			view.loadFrom(List.of(jars.secondShared, jars.dependency), "");
			assertFalse(view.selectedDriver.isEnabled());
			assertFalse(view.useTarget.isEnabled());
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertEquals(2, view.selectedDriver.getItemCount());
			assertEquals("fixture.ChildDriver", view.selectedDriver.getSelectedItem());
			view.selectedDriver.setSelectedItem("fixture.SecondDriver");
			view.useTarget.doClick(0);
			assertEquals("fixture.SecondDriver", selected.get());
			assertTrue(view.loaded.getText().contains(jars.secondShared.toString()));
			assertTrue(view.selectedDriver.isEnabled());
		});
	}

	@Test
	void selectedClassHandoffUsesRequestedConnectionAndHonorsBusyReceiver() throws Exception {
		final var jars = new JdbcDriverJarFixture(directory);
		libraries.add(List.of(jars.driver, jars.dependency), "");
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new JdbcDriverPanel(libraries);
			assertFalse(panel.useSchema.isEnabled());
			final var schema = new SchemaMigrationPlanPanel();
			final var data = new DataMigrationPlanPanel();
			schema.jdbcUrl.setText("jdbc:schema");
			schema.username.setText("schema-user");
			data.source.jdbcUrl.setText("jdbc:source");
			data.target.jdbcUrl.setText("jdbc:target");
			panel.setConnectionReceivers(schema::selectDriverClass, name -> data.selectDriverClass(name, true),
					name -> data.selectDriverClass(name, false));
			assertEquals("fixture.ChildDriver", panel.selectedDriver.getSelectedItem());
			panel.useSchema.doClick(0);
			assertEquals("fixture.ChildDriver", schema.driverClass.getText());
			assertTrue(schema.driverClass.isVisible());
			assertEquals("jdbc:schema", schema.jdbcUrl.getText());
			assertEquals("schema-user", schema.username.getText());
			panel.useSource.doClick(0);
			assertEquals("fixture.ChildDriver", data.source.driverClass.getText());
			assertEquals("", data.target.driverClass.getText());
			panel.useTarget.doClick(0);
			assertEquals("fixture.ChildDriver", data.target.driverClass.getText());
			assertTrue(data.target.driverClass.isVisible());
			assertEquals("jdbc:source", data.source.jdbcUrl.getText());
			assertEquals("jdbc:target", data.target.jdbcUrl.getText());
			panel.setConnectionReceivers(name -> false, null, null);
			panel.useSchema.doClick(0);
			assertTrue(panel.status.getText().contains("Connection form is busy"));
			assertFalse(panel.useTarget.isEnabled());
			panel.setBlockedSupplier(() -> true);
			panel.useSchema.doClick(0);
			assertTrue(panel.status.getText().contains("Wait for current operations"));
		});
	}

	@TempDir
	Path directory;
	private final JdbcDriverLibraries libraries = new JdbcDriverLibraries();

	@org.junit.jupiter.api.AfterEach
	void closeLibraries() throws Exception {
		libraries.close();
	}

	@Test
	void loadsOffEdtRejectsDuplicateWorkAndPreservesLibrariesAfterFailure() throws Exception {
		final var jars = new JdbcDriverJarFixture(directory);
		final var panel = new AtomicReference<JdbcDriverPanel>();
		final var finished = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JdbcDriverPanel(libraries);
			panel.set(view);
			onEnabled(view, finished);
			view.loadFrom(List.of(jars.driver, jars.dependency), "");
			assertTrue(view.isBusy());
			assertFalse(view.load.isEnabled());
			assertFalse(view.driverClass.isEnabled());
			view.loadFrom(List.of(directory.resolve("absent.jar")), "");
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		final var failed = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertEquals(1, libraries.libraries().size());
			assertFalse(view.isBusy());
			assertTrue(view.loaded.getText().contains("fixture.ChildDriver"));
			assertTrue(view.loaded.getText().contains(jars.dependency.toString()));
			assertTrue(view.status.getText().contains("No database connection opened"));
			onEnabled(view, failed);
			view.loadFrom(List.of(directory.resolve("absent.jar")), "");
		});
		assertTrue(failed.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertFalse(view.isBusy());
			assertTrue(view.load.isEnabled());
			assertEquals(1, libraries.libraries().size());
			assertTrue(view.loaded.getText().contains("fixture.ChildDriver"));
			assertTrue(view.statusPanel.detailsText().contains("absent.jar"));
		});
	}

	@Test
	void otherOperationsBlockLoadingWithoutTouchingFiles() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new JdbcDriverPanel(libraries);
			panel.setBlockedSupplier(() -> true);
			panel.loadFrom(List.of(directory.resolve("not-present.jar")), "");
			assertFalse(panel.isBusy());
			assertTrue(panel.load.isEnabled());
			assertTrue(panel.status.getText().contains("Wait for current operations"));
			assertEquals(0, libraries.libraries().size());
		});
	}

	private static void onEnabled(final JdbcDriverPanel panel, final CountDownLatch latch) {
		panel.load.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				latch.countDown();
		});
	}
}
