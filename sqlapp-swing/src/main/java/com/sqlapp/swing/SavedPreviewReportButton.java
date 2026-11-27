/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.SwingWorker;
import com.sqlapp.data.db.command.migration.transfer.TransferMappingPreviewReportIO;
import com.sqlapp.data.db.command.migration.transfer.TransferMappingPreviewReportIO.Saved;

/**
 * Explicit opening of the last saved HTML evidence, with asynchronous byte
 * revalidation.
 */
final class SavedPreviewReportButton extends JButton {
	private static final long serialVersionUID = 1L;

	@FunctionalInterface
	interface Verifier {
		Path verify(Saved saved) throws Exception;
	}

	@FunctionalInterface
	interface Opener {
		void open(Path file) throws Exception;
	}

	private final BooleanSupplier blocked;
	private final Consumer<String> status;
	private final Verifier verifier;
	private final Opener opener;
	private Runnable activityChanged = () -> {
	};
	private Saved saved;
	private boolean opening;
	private long revision;

	SavedPreviewReportButton(BooleanSupplier blocked, Consumer<String> status) {
		this(blocked, status, new TransferMappingPreviewReportIO()::verifySaved, file -> {
			if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
				throw new IOException("Opening a browser is unavailable. Open the saved HTML file manually: " + file);
			Desktop.getDesktop().browse(file.toUri());
		});
	}

	SavedPreviewReportButton(BooleanSupplier blocked, Consumer<String> status, Verifier verifier, Opener opener) {
		super("Open saved preview");
		this.blocked = Objects.requireNonNull(blocked);
		this.status = Objects.requireNonNull(status);
		this.verifier = Objects.requireNonNull(verifier);
		this.opener = Objects.requireNonNull(opener);
		addActionListener(event -> openSaved());
		refresh();
	}

	void setActivityObserver(Runnable observer) {
		activityChanged = Objects.requireNonNull(observer);
	}

	boolean isOpening() {
		return opening;
	}

	void saved(Saved value) {
		saved = Objects.requireNonNull(value);
		revision++;
		refresh();
	}

	void refresh() {
		setEnabled(saved != null && !opening && !blocked.getAsBoolean());
		setToolTipText(saved == null ? "Save a preview report first."
				: "Open last saved HTML preview after checking its bytes: " + saved.file()
						+ ". Current name edits are separate.");
	}

	private void openSaved() {
		if (saved == null || opening || blocked.getAsBoolean())
			return;
		Saved snapshot = saved;
		long token = revision;
		opening = true;
		refresh();
		activityChanged.run();
		status.accept("Checking saved preview report: " + snapshot.file());
		new SwingWorker<Path, Void>() {
			@Override
			protected Path doInBackground() throws Exception {
				return verifier.verify(snapshot);
			}

			@Override
			protected void done() {
				try {
					if (token != revision || saved != snapshot) {
						status.accept("The last saved preview changed. Open the current saved preview again.");
						return;
					}
					Path file = get();
					if (blocked.getAsBoolean()) {
						status.accept("Another action is running. Open the saved preview again after it finishes.");
						return;
					}
					opener.open(file);
					status.accept("Opened saved preview: " + file);
				} catch (Exception failure) {
					Throwable cause = failure;
					while (cause instanceof java.util.concurrent.ExecutionException && cause.getCause() != null)
						cause = cause.getCause();
					status.accept("Could not open saved preview: "
							+ Objects.toString(cause.getMessage(), cause.getClass().getSimpleName()));
				} finally {
					opening = false;
					refresh();
					activityChanged.run();
				}
			}
		}.execute();
	}
}
