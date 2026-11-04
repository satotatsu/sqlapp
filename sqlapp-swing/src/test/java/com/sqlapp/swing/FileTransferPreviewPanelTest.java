/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.*;

class FileTransferPreviewPanelTest {
	@Test
	void pinnedCorrespondenceLabelsRequestedNamesWithoutChangingSourceValues() throws Exception {
		var snapshot = snapshot();
		var description = new TransferFileImportService.PackageDescription(snapshot, null, null,
				List.of(new TransferFileImportService.PackageTable("task-1", null, "PUBLIC", "ITEMS", "Items copy",
						"table-1.csv", 3, false, false,
						List.of(new TransferFileImportService.PackageColumn("ID", "ITEM_ID", "INT", true, true),
								new TransferFileImportService.PackageColumn("NAME", "NAME", "VARCHAR", false,
										false)))));
		var mismatch = new AtomicBoolean();
		var holder = new FileTransferPreviewPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new FileTransferPreviewPanel(snapshot.file(), path -> snapshot,
				(path, hash, task, limit) -> {
					var value = preview(snapshot, limit);
					if (!mismatch.get())
						return value;
					var changed = new TransferFilePackageIO.TableFile("task-1", null, "PUBLIC", "ITEMS",
							List.of("NAME", "ID"), "table-1.csv", 3, 0, "sha256:" + "a".repeat(64));
					return new TransferFileImportService.Preview(snapshot, changed, value.sourceTypes(), value.rows(),
							limit, true);
				}, (path, hash) -> {
				}, () -> {
				}, snapshot.fingerprint(), "task-1", description));
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> holder[0].preview.doClick(0));
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("ID → ITEM_ID / INT", holder[0].sample.table.getColumnName(1));
			assertEquals("NAME / VARCHAR", holder[0].sample.table.getColumnName(2));
			assertEquals("1", holder[0].sample.table.getValueAt(0, 1));
			assertEquals("null", holder[0].sample.table.getValueAt(0, 2));
			assertFalse(holder[0].sample.table.isCellEditable(0, 1));
			mismatch.set(true);
			holder[0].preview.doClick(0);
		});
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertNull(holder[0].sample);
			assertTrue(holder[0].status.getText().contains("columns differ"));
		});
	}

	@Test
	void nameCorrespondenceFromAnotherPackageCannotEnableSampling() throws Exception {
		var snapshot = snapshot();
		var wrong = new TransferFileImportService.PackageDescription(
				new TransferFilePackageIO.Snapshot(snapshot.file(), "wrong", snapshot.manifest()), null, null,
				List.of());
		var holder = new FileTransferPreviewPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new FileTransferPreviewPanel(snapshot.file(), path -> snapshot,
				(path, hash, task, limit) -> {
					throw new AssertionError("Wrong correspondence");
				}, (path, hash) -> {
				}, () -> {
				}, snapshot.fingerprint(), "task-1", wrong));
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(holder[0].preview.isEnabled());
			assertTrue(holder[0].status.getText().contains("different package"));
		});
	}

	@Test
	void selectedTaskOpensFromPinnedOverviewAndReloadRejectsChangedPackage() throws Exception {
		var first = snapshot();
		var second = new TransferFilePackageIO.TableFile("task-2", null, "PUBLIC", "OTHER", List.of("ID", "NAME"),
				"table-2.csv", 0, 0, "sha256:" + "a".repeat(64));
		var manifest = first.manifest();
		var selected = new TransferFilePackageIO.Snapshot(first.file(), first.fingerprint(),
				new TransferFilePackageIO.Manifest(1, manifest.jobFingerprint(), manifest.schemaFingerprint(),
						manifest.files(), List.of(manifest.tables().getFirst(), second)));
		var inspections = new AtomicInteger();
		var calls = new AtomicInteger();
		var holder = new FileTransferPreviewPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new FileTransferPreviewPanel(first.file(), path -> {
			assertFalse(SwingUtilities.isEventDispatchThread());
			return inspections.incrementAndGet() == 1 ? selected
					: new TransferFilePackageIO.Snapshot(first.file(), "changed", selected.manifest());
		}, (path, hash, task, limit) -> {
			assertEquals(first.file(), path);
			assertEquals(first.fingerprint(), hash);
			assertEquals("task-2", task);
			calls.incrementAndGet();
			return preview(first, limit);
		}, (path, hash) -> fail("Preview must not validate all values automatically"), () -> {
		}, first.fingerprint(), "task-2"));
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(1, holder[0].tables.getSelectedIndex());
			holder[0].preview.doClick(0);
		});
		idle(holder[0]);
		assertEquals(1, calls.get());
		SwingUtilities.invokeAndWait(() -> holder[0].reload.doClick(0));
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertNull(holder[0].sample);
			assertFalse(holder[0].preview.isEnabled());
			assertFalse(holder[0].validate.isEnabled());
			assertTrue(holder[0].status.getText().contains("Package changed since the overview"));
		});
	}

	@Test
	void missingRequestedTaskNeverFallsBackToFirstTable() throws Exception {
		var first = snapshot();
		var holder = new FileTransferPreviewPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new FileTransferPreviewPanel(first.file(), path -> first,
				(path, hash, task, limit) -> {
					throw new AssertionError("No fallback sample");
				}, (path, hash) -> fail("No fallback validation"), () -> {
				}, first.fingerprint(), "absent-task"));
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(0, holder[0].tables.getItemCount());
			assertFalse(holder[0].preview.isEnabled());
			assertTrue(holder[0].status.getText().contains("selected task is absent"));
		});
	}

	@Test
	void reloadPreservesExactTaskSelectionWhenManifestOrderAndFingerprintChange() throws Exception {
		var first = snapshot();
		var secondTable = new TransferFilePackageIO.TableFile("task-2", null, "PUBLIC", "OTHER", List.of("ID", "NAME"),
				"table-2.csv", 0, 0, "sha256:" + "a".repeat(64));
		var source = first.manifest();
		var original = new TransferFilePackageIO.Snapshot(first.file(), first.fingerprint(),
				new TransferFilePackageIO.Manifest(1, source.jobFingerprint(), source.schemaFingerprint(),
						source.files(), List.of(source.tables().getFirst(), secondTable)));
		var changed = new TransferFilePackageIO.Snapshot(first.file(), "sha256:" + "e".repeat(64),
				new TransferFilePackageIO.Manifest(1, source.jobFingerprint(), source.schemaFingerprint(),
						source.files(), List.of(secondTable, source.tables().getFirst())));
		var reusedTask = new TransferFilePackageIO.TableFile("task-2", null, "OTHER_SCHEMA", "OTHER",
				List.of("ID", "NAME"), "table-2.csv", 0, 0, "sha256:" + "a".repeat(64));
		var replacement = new TransferFilePackageIO.Snapshot(first.file(), "sha256:" + "f".repeat(64),
				new TransferFilePackageIO.Manifest(1, source.jobFingerprint(), source.schemaFingerprint(),
						source.files(), List.of(source.tables().getFirst(), reusedTask)));
		var inspections = new AtomicInteger();
		var holder = new FileTransferPreviewPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new FileTransferPreviewPanel(Path.of("input"),
				path -> switch (inspections.incrementAndGet()) {
				case 1 -> original;
				case 2 -> changed;
				default -> replacement;
				}, (path, hash, task, limit) -> preview(changed, limit), () -> {
				}));
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			holder[0].tables.setSelectedIndex(1);
			holder[0].reload.doClick(0);
		});
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(0, holder[0].tables.getSelectedIndex());
			assertTrue(String.valueOf(holder[0].tables.getSelectedItem()).contains("task-2"));
			assertTrue(holder[0].evidence.getText().contains(changed.fingerprint()));
			assertTrue(holder[0].evidence.getText().contains("not run"));
			holder[0].reload.doClick(0);
		});
		idle(holder[0]);
		SwingUtilities
				.invokeAndWait(() -> assertTrue(String.valueOf(holder[0].tables.getSelectedItem()).contains("task-1")));
	}

	@Test
	void inspectionFailureCanReloadAndClearsPinnedSamplesAndValidationEvidence() throws Exception {
		var snapshot = snapshot();
		var inspections = new AtomicInteger();
		var holder = new FileTransferPreviewPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new FileTransferPreviewPanel(Path.of("input"), path -> {
			assertFalse(SwingUtilities.isEventDispatchThread());
			if (inspections.incrementAndGet() == 1)
				throw new IllegalArgumentException("Missing manifest");
			return snapshot;
		}, (path, hash, task, limit) -> preview(snapshot, limit), (path, hash) -> {
		}, () -> {
		}));
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(holder[0].reload.isEnabled());
			assertFalse(holder[0].validate.isEnabled());
			assertTrue(holder[0].status.getText().contains("Missing manifest"));
			holder[0].reload.doClick(0);
		});
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> holder[0].preview.doClick(0));
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertNotNull(holder[0].sample);
			holder[0].validate.doClick(0);
		});
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(holder[0].evidence.getText().contains("passed for this fingerprint"));
			holder[0].reload.doClick(0);
			assertNull(holder[0].sample);
			assertFalse(holder[0].preview.isEnabled());
			assertFalse(holder[0].validate.isEnabled());
			assertFalse(holder[0].reload.isEnabled());
			assertEquals("", holder[0].evidence.getText());
		});
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> assertTrue(holder[0].evidence.getText().contains("not run")));
		assertEquals(3, inspections.get());
	}

	@Test
	void wholeValueValidationUsesPinnedPackageOffEdtAndFailureClearsSampleBeforeRetry() throws Exception {
		var snapshot = snapshot();
		var attempts = new AtomicInteger();
		var sampleFailure = new AtomicBoolean();
		var started = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var holder = new FileTransferPreviewPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new FileTransferPreviewPanel(Path.of("input"), path -> snapshot,
				(path, hash, task, limit) -> {
					if (sampleFailure.get())
						throw new IllegalArgumentException("Package bytes changed");
					return preview(snapshot, limit);
				}, (path, hash) -> {
					assertFalse(SwingUtilities.isEventDispatchThread());
					assertEquals(snapshot.file(), path);
					assertEquals(snapshot.fingerprint(), hash);
					if (attempts.incrementAndGet() == 1) {
						started.countDown();
						assertTrue(release.await(5, TimeUnit.SECONDS));
						throw new IllegalArgumentException("Invalid value in tail");
					}
				}, () -> {
				}));
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> holder[0].preview.doClick(0));
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			holder[0].validate.doClick(0);
			assertFalse(holder[0].sample.table.isEnabled());
			assertFalse(holder[0].close.isEnabled());
			assertFalse(holder[0].reload.isEnabled());
			assertTrue(holder[0].evidence.getText().contains("in progress"));
			holder[0].validate.setEnabled(true);
			holder[0].validate.doClick(0);
		});
		assertTrue(started.await(5, TimeUnit.SECONDS));
		release.countDown();
		idle(holder[0]);
		assertEquals(1, attempts.get());
		SwingUtilities.invokeAndWait(() -> {
			assertNull(holder[0].sample);
			assertTrue(holder[0].status.getText().contains("Invalid value in tail"));
			assertTrue(holder[0].evidence.getText().contains("failed; fix the package"));
			holder[0].validate.doClick(0);
		});
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(holder[0].evidence.getText().contains("passed for this fingerprint"));
			assertTrue(holder[0].status.getText().contains("Target compatibility and import have not been approved"));
			sampleFailure.set(true);
			holder[0].preview.doClick(0);
		});
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertNull(holder[0].sample);
			assertTrue(holder[0].evidence.getText().contains("not run"));
		});
		assertEquals(2, attempts.get());
	}

	private TransferFilePackageIO.Snapshot snapshot() {
		var table = new TransferFilePackageIO.TableFile("task-1", null, "PUBLIC", "ITEMS", List.of("ID", "NAME"),
				"table-1.csv", 3, 0, "sha256:" + "a".repeat(64));
		var manifest = new TransferFilePackageIO.Manifest(1, "sha256:" + "b".repeat(64), "sha256:" + "c".repeat(64),
				new TransferJobConfiguration.FileTransfer("packages", null, null, "__NULL__"), List.of(table));
		return new TransferFilePackageIO.Snapshot(Path.of("manifest.json"), "sha256:" + "d".repeat(64), manifest);
	}

	private TransferFileImportService.Preview preview(TransferFilePackageIO.Snapshot snapshot, int limit) {
		return new TransferFileImportService.Preview(snapshot, snapshot.manifest().tables().getFirst(),
				List.of("INT", "VARCHAR"), List.of(List.of(new TransferFileImportService.PreviewCell("1", false, false),
						new TransferFileImportService.PreviewCell("null", true, false))),
				limit, true);
	}

	private void idle(FileTransferPreviewPanel panel) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		var ready = new AtomicBoolean();
		while (!ready.get() && System.nanoTime() < deadline) {
			SwingUtilities.invokeAndWait(() -> ready.set(panel.close.isEnabled()));
			if (!ready.get())
				Thread.sleep(10);
		}
		assertTrue(ready.get());
	}

	@Test
	void explicitSampleRunsOffEdtPreservesPinnedIdentityAndClearsOnChangedOptions() throws Exception {
		var snapshot = snapshot();
		var calls = new AtomicInteger();
		var closed = new AtomicInteger();
		var holder = new FileTransferPreviewPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new FileTransferPreviewPanel(Path.of("input"), path -> {
			assertFalse(SwingUtilities.isEventDispatchThread());
			return snapshot;
		}, (path, hash, task, limit) -> {
			assertFalse(SwingUtilities.isEventDispatchThread());
			assertEquals(snapshot.file(), path);
			assertEquals(snapshot.fingerprint(), hash);
			assertEquals("task-1", task);
			assertEquals(20, limit);
			calls.incrementAndGet();
			return preview(snapshot, limit);
		}, closed::incrementAndGet));
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(0, calls.get());
			assertTrue(holder[0].preview.isEnabled());
			assertNull(holder[0].sample);
			holder[0].preview.doClick(0);
			assertFalse(holder[0].close.isEnabled());
			assertFalse(holder[0].tables.isEnabled());
		});
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(1, calls.get());
			assertEquals(1, holder[0].sample.table.getRowCount());
			assertFalse(holder[0].sample.table.isCellEditable(0, 1));
			assertTrue(holder[0].sample.count.getText().contains("sampled rows"));
			assertEquals("null", holder[0].sample.table.getValueAt(0, 2));
			assertTrue(holder[0].status.getText().contains("recorded package total: 3"));
			assertTrue(holder[0].status.getText().contains("No import is approved"));
			holder[0].rowLimit.setValue(10);
			assertNull(holder[0].sample);
			holder[0].close.doClick(0);
			assertEquals(1, closed.get());
		});
	}

	@Test
	void failuresCanRetryAndLateResultsCannotPublishForAChangedLimit() throws Exception {
		var snapshot = snapshot();
		var attempts = new AtomicInteger();
		var started = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var holder = new FileTransferPreviewPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new FileTransferPreviewPanel(Path.of("input"), path -> snapshot,
				(path, hash, task, limit) -> {
					if (attempts.incrementAndGet() == 1)
						throw new IllegalArgumentException("Package data changed");
					if (attempts.get() == 2) {
						started.countDown();
						assertTrue(release.await(5, TimeUnit.SECONDS));
					}
					return preview(snapshot, limit);
				}, () -> {
				}));
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> holder[0].preview.doClick(0));
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertNull(holder[0].sample);
			assertTrue(holder[0].status.getText().contains("Package data changed"));
			holder[0].preview.doClick(0);
		});
		assertTrue(started.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> holder[0].rowLimit.setValue(10));
		release.countDown();
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertNull(holder[0].sample);
			assertTrue(holder[0].status.getText().contains("Selection changed while reading"));
			holder[0].preview.doClick(0);
		});
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> assertNotNull(holder[0].sample));
	}
}
