/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.schema.MigrationPlanArtifact;
import com.sqlapp.data.db.command.migration.schema.MigrationPlan;

class MigrationBusyStateTest {
	@TempDir
	Path directory;

	@Test
	void schemaPlanSaveReportsBusyRejectsDuplicateAndRestoresStateAfterFailure() throws Exception {
		final var entered = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var writes = new AtomicInteger();
		final var ref = new AtomicReference<SchemaMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var artifact = MigrationPlanArtifact
					.of(new MigrationPlan(false, null, 30L, 0, 0, List.of(), List.of(), null));
			final var result = new SchemaMigrationPlanningService.Result(artifact,
					new ReportReviewService().schemaPlan(artifact));
			final var panel = new SchemaMigrationPlanPanel((request, url, user, secret, driver) -> result,
					(review, file) -> {
						writes.incrementAndGet();
						entered.countDown();
						if (!release.await(5, TimeUnit.SECONDS))
							throw new java.io.IOException("timeout");
						throw new java.io.IOException("Test destination unavailable");
					});
			ref.set(panel);
			panel.jdbcUrl.setText("jdbc:example:review");
			panel.upDirectory.setText(directory.toString());
		});
		final var panel = ref.get();
		finish(panel.build, () -> panel.build.doClick(0));
		final var done = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			onFinished(panel.build, done);
			panel.saveTo(directory.resolve("plan.json"));
			assertTrue(panel.isBusy());
			assertFalse(panel.isExecuting());
			panel.saveTo(directory.resolve("duplicate.json"));
		});
		assertTrue(entered.await(5, TimeUnit.SECONDS));
		release.countDown();
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(panel.isBusy());
			assertTrue(panel.save.isEnabled());
			assertTrue(panel.statusPanel.detailsText().contains("Test destination unavailable"));
		});
		assertEquals(1, writes.get());
	}

	@Test
	void profileReadsKeepParentScreensBusyAndPreventPlanningUntilTheyComplete() throws Exception {
		final Path profileFile = directory.resolve("profile.json");
		new ConnectionProfileService()
				.write(new ConnectionProfileService.Profile(1, "jdbc:hsqldb:mem:profile_busy", "SA", ""), profileFile);
		final var schema = new AtomicReference<SchemaMigrationPlanPanel>();
		final var data = new AtomicReference<DataMigrationPlanPanel>();
		final var done = new CountDownLatch(3);
		final var planners = new AtomicInteger();
		SwingUtilities.invokeAndWait(() -> {
			final var schemaView = new SchemaMigrationPlanPanel((request, url, user, secret, driver) -> {
				planners.incrementAndGet();
				throw new AssertionError("No planning during profile IO");
			}, (result, path) -> {
			});
			final var dataView = new DataMigrationPlanPanel((request, source, sourceSecret, target, targetSecret) -> {
				planners.incrementAndGet();
				throw new AssertionError("No planning during profile IO");
			}, (result, path) -> {
			});
			schema.set(schemaView);
			data.set(dataView);
			for (final var profile : List.of(schemaView.profile, dataView.source.profile, dataView.target.profile)) {
				onFinished(profile.load, done);
				profile.loadFrom(profileFile);
				assertTrue(profile.isBusy());
			}
			assertTrue(schemaView.isBusy());
			assertTrue(dataView.isBusy());
			assertFalse(schemaView.isExecuting());
			assertFalse(dataView.isExecuting());
			schemaView.build.doClick(0);
			dataView.build.doClick(0);
			assertEquals(0, planners.get());
			assertTrue(schemaView.status.getText().contains("profile operation"));
			assertTrue(dataView.status.getText().contains("profile operations"));
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(schema.get().isBusy());
			assertFalse(data.get().isBusy());
		});
		assertEquals(0, planners.get());
	}

	private static void finish(JButton button, Runnable action) throws Exception {
		final var done = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			onFinished(button, done);
			action.run();
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
	}

	private static void onFinished(JButton button, CountDownLatch done) {
		button.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				done.countDown();
		});
	}
}
