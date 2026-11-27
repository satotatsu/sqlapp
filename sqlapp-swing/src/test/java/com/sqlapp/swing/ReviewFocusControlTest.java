/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationOperationalReport;
import com.sqlapp.data.db.command.migration.schema.*;

class ReviewFocusControlTest {
	@TempDir
	Path directory;

	@Test
	void hidingInputsGivesResultsMoreSpaceAndRestoresUserDivider() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var viewport = new JScrollPane(new JTextField("retained input"));
			final var controls = new JPanel();
			controls.add(new JButton("Action"));
			final var input = new JPanel(new BorderLayout());
			input.add(viewport);
			input.add(controls, BorderLayout.SOUTH);
			final var result = new JPanel();
			final var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, input, result);
			final var toggle = new JToggleButton();
			final var advanced = new JToggleButton();
			final var focus = new ReviewFocusControl(split, viewport, controls, toggle, advanced);
			split.setSize(900, 600);
			split.setDividerLocation(320);
			layout(split);
			final int initial = result.getHeight();
			toggle.doClick(0);
			layout(split);
			assertFalse(viewport.isVisible());
			assertTrue(result.getHeight() > initial + 150);
			assertFalse(advanced.isEnabled());
			focus.showInputs();
			layout(split);
			assertTrue(viewport.isVisible());
			assertEquals(320, split.getDividerLocation());
			assertTrue(advanced.isEnabled());
			focus.setBusy(true);
			toggle.doClick(0);
			assertFalse(toggle.isSelected());
		});
	}

	@Test
	void dataReviewAndInputsAreRetainedAndDriverHandoffRestoresSettings() throws Exception {
		final Path job = Files.writeString(directory.resolve("job.yaml"), "schemaFile: schema.xml");
		final var calls = new AtomicInteger();
		final var panel = new AtomicReference<DataMigrationPlanPanel>();
		final var done = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var view = new DataMigrationPlanPanel((request, source, password, target, targetPassword) -> {
				calls.incrementAndGet();
				final var report = new BulkMigrationOperationalReport(3, Instant.now(), "job", "plan", true, 0, 0, 0,
						List.of(), List.of(), null, null, List.of(), null);
				return new DataMigrationPlanningService.Result(job, report,
						new ReportReviewService().operational(report));
			}, (result, destination) -> {
			});
			panel.set(view);
			view.source.jdbcUrl.setText("jdbc:source");
			view.target.jdbcUrl.setText("jdbc:target");
			view.configurationFile.setText(job.toString());
			view.build.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()))
					done.countDown();
			});
			view.build.doClick(0);
			assertFalse(view.focusReview.isEnabled());
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			final String summary = view.view.summary.getText();
			final boolean executionAllowed = view.apply.isEnabled();
			view.focusReview.doClick(0);
			assertTrue(view.focusReview.isSelected());
			assertEquals(summary, view.view.summary.getText());
			assertTrue(view.save.isEnabled());
			assertEquals(executionAllowed, view.apply.isEnabled());
			assertEquals(job.toString(), view.configurationFile.getText());
			assertEquals("jdbc:source", view.source.jdbcUrl.getText());
			view.focusReview.doClick(0);
			assertEquals(summary, view.view.summary.getText());
			assertEquals(1, calls.get());
			view.focusReview.doClick(0);
			assertTrue(view.selectDriverClass("example.Driver", true));
			assertFalse(view.focusReview.isSelected());
			view.focusReview.doClick(0);
			view.showInputs();
			assertFalse(view.focusReview.isSelected());
		});
	}

	@Test
	void schemaReviewSurvivesFocusAndDirectoryHandoffRestoresSettings() throws Exception {
		final var panel = new AtomicReference<SchemaMigrationPlanPanel>();
		final var done = new CountDownLatch(1);
		final var calls = new AtomicInteger();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new SchemaMigrationPlanPanel((request, url, user, password, driver) -> {
				calls.incrementAndGet();
				final var artifact = MigrationPlanArtifact
						.of(new MigrationPlan(false, null, 30L, 0, 0, List.of(), List.of(), null));
				return new SchemaMigrationPlanningService.Result(artifact,
						new ReportReviewService().schemaPlan(artifact));
			}, (result, destination) -> {
			});
			panel.set(view);
			view.jdbcUrl.setText("jdbc:source");
			view.upDirectory.setText(directory.toString());
			view.build.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()))
					done.countDown();
			});
			view.build.doClick(0);
			assertFalse(view.focusReview.isEnabled());
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			final String summary = view.view.summary.getText();
			final boolean allowed = view.apply.isEnabled();
			view.focusReview.doClick(0);
			assertEquals(summary, view.view.summary.getText());
			assertTrue(view.save.isEnabled());
			assertEquals(allowed, view.apply.isEnabled());
			view.focusReview.doClick(0);
			assertEquals(summary, view.view.summary.getText());
			assertEquals(1, calls.get());
			view.focusReview.doClick(0);
			assertTrue(view.selectUpDirectory(directory));
			assertFalse(view.focusReview.isSelected());
			view.focusReview.doClick(0);
			assertTrue(view.selectDriverClass("example.Driver"));
			assertFalse(view.focusReview.isSelected());
		});
	}

	private static void layout(Container parent) {
		parent.doLayout();
		for (var child : parent.getComponents())
			if (child instanceof Container nested)
				layout(nested);
	}
}
