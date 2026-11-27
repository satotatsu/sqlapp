/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationFileDialogsTest {
	@TempDir
	Path directory;

	@Test
	void schemaAndJobDialogsUseCurrentInputsAndKeepOutputNamesAndFilters() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var comparison = new SchemaComparisonPanel();
			comparison.original.setText(directory.resolve("before.xml").toString());
			assertEquals(directory.toFile(), comparison.inputChooser("").getCurrentDirectory());
			comparison.target.setText(directory.resolve("after.xml").toString());
			assertEquals(directory.toFile(),
					comparison.inputChooser(comparison.target.getText()).getCurrentDirectory());
			assertEquals(directory.resolve("schema-review.txt").toFile(),
					comparison.outputChooser(false).getSelectedFile());
			assertTrue(comparison.outputChooser(true).getSelectedFile().getName().endsWith(".sql"));
			assertTrue(comparison.inputChooser("").getFileFilter().accept(directory.resolve("schema.xml").toFile()));
			final var draft = new JobDraftPanel(ignored -> fail("Browsing must not create a draft"));
			draft.schemaFile.setText(directory.resolve("schema.xml").toString());
			assertEquals(directory.toFile(), draft.chooser().getCurrentDirectory());
			assertFalse(draft.create.isEnabled());
			final var report = new ReportReviewPanel((file, type) -> {
				fail("Browsing must not load a report");
				return null;
			});
			report.file.setText(directory.resolve("report.json").toString());
			assertEquals(directory.toFile(), report.chooser().getCurrentDirectory());
			assertEquals(directory.resolve("report.json").toString(), report.file.getText());
		});
	}

	@Test
	void planDialogsChooseOnlyDirectoriesOrJsonOutputsWithoutConnecting() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var schema = new SchemaMigrationPlanPanel((request, url, user, secret, driver) -> {
				fail("No connection from browsing");
				return null;
			}, (result, path) -> fail("No writes from browsing"));
			schema.upDirectory.setText(directory.toString());
			assertEquals(directory.toFile(),
					schema.directoryChooser(schema.upDirectory.getText()).getCurrentDirectory());
			assertEquals(JFileChooser.DIRECTORIES_ONLY, schema.directoryChooser("").getFileSelectionMode());
			assertEquals(directory.toFile(), schema.directoryChooser("").getCurrentDirectory());
			assertEquals(directory.resolve("migration-plan.json").toFile(),
					schema.outputChooser("migration-plan.json").getSelectedFile());
			schema.upDirectory.setText("bad\u0000path");
			assertDoesNotThrow(() -> schema.outputChooser("migration-plan.json"));
			final var data = new DataMigrationPlanPanel();
			data.configurationFile.setText(directory.resolve("job.yaml").toString());
			assertEquals(directory.toFile(), data.inputChooser().getCurrentDirectory());
			for (final String name : new String[] { "data-migration-status.json", "data-execution-audit.json",
					"data-verification.json" }) {
				assertEquals(directory.resolve(name).toFile(), data.outputChooser(name).getSelectedFile());
				assertTrue(data.outputChooser(name).getFileFilter().accept(directory.resolve(name).toFile()));
			}
			assertFalse(schema.apply.isEnabled());
			assertFalse(data.apply.isEnabled());
		});
	}

	@Test
	void reportsRememberOnlySuccessfulReadsAfterInputIsCleared() throws Exception {
		final Path other = java.nio.file.Files.createDirectory(directory.resolve("other"));
		final var ref = new java.util.concurrent.atomic.AtomicReference<ReportReviewPanel>();
		SwingUtilities.invokeAndWait(() -> ref.set(new ReportReviewPanel((file, type) -> {
			if (file.getFileName().toString().equals("bad.json"))
				throw new IllegalArgumentException("Bad report");
			return new ReportReviewService.Review("Loaded", java.util.List.of(), java.util.List.of(), "{}");
		})));
		final var panel = ref.get();
		load(panel, directory.resolve("good.json"));
		SwingUtilities.invokeAndWait(() -> {
			panel.file.setText("");
			assertEquals(directory.toFile(), panel.chooser().getCurrentDirectory());
		});
		load(panel, other.resolve("bad.json"));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.status.getText().startsWith("Load failed"));
			panel.file.setText("");
			assertEquals(directory.toFile(), panel.chooser().getCurrentDirectory());
		});
	}

	private static void load(ReportReviewPanel panel, Path path) throws Exception {
		final var done = new java.util.concurrent.CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			panel.file.setText(path.toString());
			panel.load.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()))
					done.countDown();
			});
			panel.load.doClick(0);
		});
		assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS));
	}
}
