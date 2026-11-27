/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.schema.MigrationPlan.DatabaseIdentity;
import com.sqlapp.data.db.command.migration.transfer.*;

class StagingComparisonSaveActionsTest {
	@TempDir
	Path directory;

	@Test
	void failedSaveRetainsComparisonAndAllowsRetryWithoutChangingInput() throws Exception {
		var input = Files.writeString(directory.resolve("input.json"), "original");
		var failed = new CountDownLatch(1);
		var saved = new CountDownLatch(1);
		var busy = new AtomicBoolean();
		var ref = new AtomicReference<StagingComparisonSaveActions>();
		var opened = new AtomicReference<Path>();
		SwingUtilities.invokeAndWait(() -> {
			var actions = new StagingComparisonSaveActions(busy::get, busy::set, message -> {
				if (message.startsWith("Could not save")) {
					assertTrue(message.contains("is a file"));
					failed.countDown();
				} else if (message.startsWith("Saved staging comparison:"))
					saved.countDown();
			}, directory::toString);
			ref.set(actions);
			actions.saved.setReceiver((file, type) -> {
				opened.set(file);
				return true;
			});
			var hash = "sha256:" + "a".repeat(64);
			actions.show(new TransferStagingComparisonReportIO.Report(1, hash,
					TransferStagedReplacement.State.COMMIT_UNCERTAIN, new DatabaseIdentity("HSQL", "2.7", hash),
					new TransferStagedReplacement.Comparison(List.of(new TransferStagedReplacement.TableComparison(
							new TransferStagedReplacement.Target("task", null, "PUBLIC", "ITEMS", "STAGE"), 1, 1, true,
							true)))));
			actions.saveTo(input);
		});
		assertTrue(failed.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(busy.get());
			assertTrue(ref.get().save.isEnabled());
			assertFalse(ref.get().saved.isEnabled());
			ref.get().saveTo(directory);
		});
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> ref.get().saved.doClick(0));
		assertNotNull(opened.get());
		assertTrue(new TransferStagingComparisonReportIO().read(opened.get()).comparison().tables().getFirst()
				.matchesTarget());
		assertEquals("original", Files.readString(input));
	}

	@Test
	void recompareRetainsLastSavedSnapshotUntilNextSaveAndInputClearRevokesIt() throws Exception {
		var firstSaved = new CountDownLatch(1);
		var secondSaved = new CountDownLatch(1);
		var saves = new AtomicInteger();
		var busy = new AtomicBoolean();
		var ref = new AtomicReference<StagingComparisonSaveActions>();
		var opened = new AtomicReference<Path>();
		SwingUtilities.invokeAndWait(() -> {
			var actions = new StagingComparisonSaveActions(busy::get, busy::set, message -> {
				if (message.startsWith("Saved staging comparison:")) {
					if (saves.incrementAndGet() == 1)
						firstSaved.countDown();
					else
						secondSaved.countDown();
				}
			}, directory::toString);
			ref.set(actions);
			actions.saved.setReceiver((file, type) -> {
				opened.set(file);
				return true;
			});
			actions.show(report(true));
			actions.saveTo(directory);
		});
		assertTrue(firstSaved.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> ref.get().saved.doClick(0));
		var first = opened.get();
		SwingUtilities.invokeAndWait(() -> {
			var actions = ref.get();
			actions.beginComparison();
			assertFalse(actions.save.isEnabled());
			assertEquals("Open last saved comparison", actions.saved.getText());
			busy.set(true);
			actions.refresh();
			assertFalse(actions.saved.isEnabled());
			busy.set(false);
			actions.refresh();
			assertTrue(actions.saved.isEnabled());
			actions.show(report(false));
			actions.saved.doClick(0);
			assertEquals(first, opened.get());
			actions.saveTo(directory);
		});
		assertTrue(secondSaved.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> ref.get().saved.doClick(0));
		assertNotEquals(first, opened.get());
		var io = new TransferStagingComparisonReportIO();
		assertTrue(io.read(first).comparison().tables().getFirst().matchesTarget());
		assertFalse(io.read(opened.get()).comparison().tables().getFirst().matchesTarget());
		SwingUtilities.invokeAndWait(() -> {
			ref.get().clear();
			assertFalse(ref.get().saved.isEnabled());
			assertFalse(ref.get().save.isEnabled());
		});
	}

	private TransferStagingComparisonReportIO.Report report(boolean matches) {
		var hash = "sha256:" + "a".repeat(64);
		return new TransferStagingComparisonReportIO.Report(1, hash, TransferStagedReplacement.State.COMMIT_UNCERTAIN,
				new DatabaseIdentity("HSQL", "2.7", hash),
				new TransferStagedReplacement.Comparison(List.of(new TransferStagedReplacement.TableComparison(
						new TransferStagedReplacement.Target("task", null, "PUBLIC", "ITEMS", "STAGE"), 1,
						matches ? 1 : 0, matches, true))));
	}

	@Test
	void permissionFailureShowsActionableGuidanceWithoutRawExceptionMessage() {
		var text = StagingComparisonSaveActions
				.saveFailure(new ExecutionException(new java.nio.file.AccessDeniedException("private-secret-path")));
		assertTrue(text.contains("writable folder"));
		assertTrue(text.contains("AccessDeniedException"));
		assertFalse(text.contains("private-secret-path"));
	}
}
