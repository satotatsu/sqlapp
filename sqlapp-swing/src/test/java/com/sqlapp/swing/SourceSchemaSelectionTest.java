/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SourceSchemaSelectionTest {
	@Test
	void literalFilterCannotApplyHiddenSelectionAndNamesRemainPlainText() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var selected = new AtomicReference<String>();
			final var panel = new SourceSchemaSelectionPanel(List.of("PUBLIC", "Report[1]", "<html>name"), "PUBLIC",
					selected::set, () -> {
					});
			assertTrue(panel.use.isEnabled());
			panel.filter.setText("[1]");
			assertEquals(1, panel.model.size());
			assertFalse(panel.use.isEnabled());
			panel.schemas.setSelectedIndex(0);
			assertNull(selected.get());
			panel.use.doClick(0);
			assertEquals("Report[1]", selected.get());
			panel.filter.setText("no match");
			assertFalse(panel.use.isEnabled());
			panel.current.doClick(0);
			assertEquals("", selected.get());
			panel.filter.setText("<html>");
			panel.schemas.setSelectedIndex(0);
			final var renderer = (javax.swing.JLabel) panel.schemas.getCellRenderer()
					.getListCellRendererComponent(panel.schemas, "<html>name", 0, true, false);
			assertEquals("<html>name", renderer.getText());
			assertEquals(Boolean.TRUE, renderer.getClientProperty("html.disable"));
		});
	}

	@Test
	void selectionUsesOnlySourceAndKeepsSavedJobAndPasswords() throws Exception {
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<DataMigrationPlanPanel>();
		final var secret = new AtomicReference<char[]>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel();
			panel.set(view);
			configure(view);
			view.configurationFile.setText("saved.yaml");
			onFinished(view, finished);
			view.chooseSourceSchema((connection, password) -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				assertEquals("jdbc:source", connection.jdbcUrl());
				assertArrayEquals("secret".toCharArray(), password);
				secret.set(password);
				return List.of("PUBLIC", "REPORTS");
			}, names -> {
				assertTrue(SwingUtilities.isEventDispatchThread());
				assertTrue(view.isBusy());
				return "REPORTS";
			});
			assertFalse(view.chooseSourceSchema.isEnabled());
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertArrayEquals(new char[6], secret.get());
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertEquals("REPORTS", view.sourceSchema.getText());
			assertEquals("REPORTS", view.sourceSchemaSummary.getText());
			assertFalse(view.sourceSchemaSummary.isEditable());
			assertEquals("saved.yaml", view.configurationFile.getText());
			assertArrayEquals("secret".toCharArray(), view.source.password.getPassword());
			assertEquals("", view.target.jdbcUrl.getText());
			assertFalse(view.apply.isEnabled());
			view.source.username.setText("another user");
			assertEquals("", view.sourceSchema.getText());
			assertEquals("Current schema (default)", view.sourceSchemaSummary.getText());
		});
	}

	@Test
	void cancelAndFailureRetainSchemaAndReview() throws Exception {
		for (boolean fail : new boolean[] { false, true }) {
			final var finished = new CountDownLatch(1);
			final var panel = new AtomicReference<DataMigrationPlanPanel>();
			SwingUtilities.invokeAndWait(() -> {
				final var view = panel();
				panel.set(view);
				configure(view);
				view.sourceSchema.setText("OLD");
				view.view.summary.setText("Previous review");
				onFinished(view, finished);
				view.chooseSourceSchema((connection, password) -> {
					if (fail)
						throw new IllegalArgumentException("No schema permission");
					return List.of("NEW");
				}, names -> null);
			});
			assertTrue(finished.await(5, TimeUnit.SECONDS));
			SwingUtilities.invokeAndWait(() -> {
				final var view = panel.get();
				assertEquals("OLD", view.sourceSchema.getText());
				assertEquals("Previous review", view.view.summary.getText());
				assertTrue(view.status.getText().contains(fail ? "Unable to read" : "cancelled"));
				assertFalse(view.isBusy());
			});
		}
	}

	@Test
	void staleDiscoveryAndRepeatedActionsCannotOpenPicker() throws Exception {
		final var entered = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<DataMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel();
			panel.set(view);
			configure(view);
			onFinished(view, finished);
			view.chooseSourceSchema((connection, password) -> {
				entered.countDown();
				try {
					assertTrue(release.await(5, TimeUnit.SECONDS));
				} catch (InterruptedException failure) {
					throw new RuntimeException(failure);
				}
				return List.of("OLD");
			}, names -> {
				throw new AssertionError("Stale result must not open picker");
			});
			view.chooseSourceSchema((connection, password) -> {
				throw new AssertionError("Duplicate read");
			}, names -> null);
		});
		assertTrue(entered.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> panel.get().source.jdbcUrl.setText("jdbc:new"));
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("", panel.get().sourceSchema.getText());
			assertTrue(panel.get().status.getText().contains("inputs changed"));
		});
	}

	@Test
	void missingConnectionDoesNotReadSchemas() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel();
			view.chooseSourceSchema((connection, password) -> {
				throw new AssertionError("Must not connect");
			}, names -> null);
			assertTrue(view.status.getText().contains("Source connection: enter a JDBC URL"));
			assertFalse(view.isBusy());
		});
	}

	private static DataMigrationPlanPanel panel() {
		return new DataMigrationPlanPanel((request, source, password, target, targetPassword) -> {
			throw new AssertionError("Must not inspect a migration");
		}, (result, path) -> {
			throw new AssertionError("Must not save a job");
		}, (connection, password) -> {
			throw new AssertionError("Must not test a connection");
		});
	}

	private static void configure(DataMigrationPlanPanel panel) {
		panel.source.jdbcUrl.setText("jdbc:source");
		panel.source.password.setText("secret");
	}

	private static void onFinished(DataMigrationPlanPanel panel, CountDownLatch finished) {
		panel.build.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				finished.countDown();
		});
	}
}
