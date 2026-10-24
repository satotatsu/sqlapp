/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationOperationalReport;

class DataMigrationPlanPanelTest {
	@TempDir
	Path directory;

	@Test
	void connectsOnlyOnActionAndInspectsAndSavesInBackground() throws Exception {
		final Path job = Files.writeString(directory.resolve("job.yaml"), "schemaFile: schema.xml");
		final var expected = result(job);
		final var finished = new CountDownLatch(1);
		final var saved = new CountDownLatch(1);
		final var calls = new AtomicInteger();
		final var secrets = new AtomicReference<char[][]>();
		final var view = new AtomicReference<DataMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new DataMigrationPlanPanel((request, source, sourcePassword, target, targetPassword) -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				calls.incrementAndGet();
				assertEquals(job, request.configurationFile());
				assertEquals("sha256:" + "a".repeat(64), request.expectedConfigurationFingerprint());
				assertEquals("jdbc:source", source.jdbcUrl());
				assertEquals("source-user", source.username());
				assertEquals("source.Driver", source.driverClass());
				assertEquals("jdbc:target", target.jdbcUrl());
				assertEquals("target-user", target.username());
				assertArrayEquals("source-secret".toCharArray(), sourcePassword);
				assertArrayEquals("target-secret".toCharArray(), targetPassword);
				secrets.set(new char[][] { sourcePassword, targetPassword });
				return expected;
			}, (result, destination) -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				assertSame(expected, result);
				assertEquals(directory.resolve("status.json"), destination);
			});
			view.set(panel);
			assertEquals(0, calls.get());
			assertNull(SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class, panel.build));
			configure(panel, job);
			panel.source.password.setText("source-secret");
			panel.target.password.setText("target-secret");
			panel.source.driverClass.setText("source.Driver");
			panel.expectedFingerprint.setText("sha256:" + "a".repeat(64));
			onEnabled(panel.build, finished);
			panel.build.doClick();
			assertFalse(panel.source.jdbcUrl.isEnabled());
			assertFalse(panel.target.jdbcUrl.isEnabled());
			assertEquals(0, panel.source.password.getPassword().length);
			assertEquals(0, panel.target.password.getPassword().length);
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertEquals(1, calls.get());
		for (final var secret : secrets.get()) {
			assertArrayEquals(new char[secret.length], secret);
		}
		SwingUtilities.invokeAndWait(() -> {
			final var panel = view.get();
			assertEquals(expected.review().summary(), panel.view.summary.getText());
			assertTrue(panel.save.isEnabled());
			assertTrue(panel.status.getText().contains("No rows migrated"));
			onEnabled(panel.save, saved);
			panel.saveTo(directory.resolve("status.json"));
		});
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(view.get().status.getText().startsWith("Saved status report:"));
			view.get().target.username.setText("changed");
			assertFalse(view.get().save.isEnabled());
			assertEquals("", view.get().view.details.getText());
		});
	}

	@Test
	void missingInputFailsBeforeConnectingAndRestoresControls() throws Exception {
		final var finished = new CountDownLatch(1);
		final var calls = new AtomicInteger();
		final var view = new AtomicReference<DataMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new DataMigrationPlanPanel((request, source, sourcePassword, target, targetPassword) -> {
				calls.incrementAndGet();
				return result(request.configurationFile());
			}, (result, destination) -> {
			});
			view.set(panel);
			panel.source.password.setText("secret");
			onEnabled(panel.build, finished);
			panel.build.doClick();
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertEquals(0, calls.get());
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(view.get().view.summary.getText().contains("Select a migration job YAML"));
			assertTrue(view.get().source.jdbcUrl.isEnabled());
			assertFalse(view.get().save.isEnabled());
			assertEquals(0, view.get().source.password.getPassword().length);
		});
	}

	@Test
	void failureClearsPreviousDetailsAndWipesBothPasswords() throws Exception {
		final Path job = Files.writeString(directory.resolve("job.yaml"), "{}");
		final var finished = new CountDownLatch(1);
		final var secrets = new AtomicReference<char[][]>();
		final var view = new AtomicReference<DataMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new DataMigrationPlanPanel((request, source, sourcePassword, target, targetPassword) -> {
				secrets.set(new char[][] { sourcePassword, targetPassword });
				throw new IllegalArgumentException("Target table was not found");
			}, (result, destination) -> {
			});
			view.set(panel);
			configure(panel, job);
			panel.source.password.setText("one");
			panel.target.password.setText("two");
			panel.view.details.setText("old result");
			onEnabled(panel.build, finished);
			panel.build.doClick();
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		for (final var secret : secrets.get()) {
			assertArrayEquals(new char[secret.length], secret);
		}
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(view.get().view.summary.getText().contains("Target table was not found"));
			assertEquals("", view.get().view.details.getText());
			assertFalse(view.get().save.isEnabled());
			assertTrue(view.get().target.jdbcUrl.isEnabled());
		});
	}

	@Test
	void discardsResultIfConfigurationChangedDuringInspection() throws Exception {
		final Path job = Files.writeString(directory.resolve("job.yaml"), "{}");
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var view = new AtomicReference<DataMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new DataMigrationPlanPanel((request, source, sourcePassword, target, targetPassword) -> {
				try {
					if (!release.await(5, TimeUnit.SECONDS)) {
						throw new IllegalStateException("Test release timed out");
					}
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(exception);
				}
				return result(job);
			}, (result, destination) -> {
			});
			view.set(panel);
			configure(panel, job);
			onEnabled(panel.build, finished);
			panel.build.doClick();
			panel.expectedFingerprint.setText("sha256:" + "a".repeat(64));
		});
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("", view.get().view.summary.getText());
			assertEquals("", view.get().view.details.getText());
			assertFalse(view.get().save.isEnabled());
			assertTrue(view.get().build.isEnabled());
		});
	}

	private void configure(final DataMigrationPlanPanel panel, final Path job) {
		panel.configurationFile.setText(job.toString());
		panel.source.jdbcUrl.setText("jdbc:source");
		panel.source.username.setText("source-user");
		panel.target.jdbcUrl.setText("jdbc:target");
		panel.target.username.setText("target-user");
	}

	private static DataMigrationPlanningService.Result result(final Path job) {
		final var report = new BulkMigrationOperationalReport(3, Instant.now(), "job", "plan", true, 0, 0, 0, List.of(),
				List.of(), null, null, List.of(), null);
		return new DataMigrationPlanningService.Result(job, report, new ReportReviewService().operational(report));
	}

	private void onEnabled(final javax.swing.JButton button, final CountDownLatch finished) {
		button.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue())) {
				finished.countDown();
			}
		});
	}
}
