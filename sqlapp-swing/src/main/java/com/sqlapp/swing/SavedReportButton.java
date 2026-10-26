/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.file.Path;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

import javax.swing.JButton;

/** Explicit handoff of the last successfully saved supported report. */
final class SavedReportButton extends JButton {
	private static final long serialVersionUID = 1L;
	private final BooleanSupplier blocked;
	private BiPredicate<Path, ReportReviewService.Type> receiver;
	private Path file;
	private ReportReviewService.Type type;

	SavedReportButton(final BooleanSupplier blocked) {
		super("Open saved report");
		this.blocked = blocked;
		addActionListener(event -> {
			if (file == null || receiver == null || blocked.getAsBoolean())
				return;
			if (!receiver.test(file, type))
				setToolTipText("Report viewer is busy. Wait for it to finish, then try again. File: " + file);
			else
				refresh();
		});
		refresh();
	}

	void setReceiver(final BiPredicate<Path, ReportReviewService.Type> receiver) {
		this.receiver = receiver;
		refresh();
	}

	void saved(final Path file, final ReportReviewService.Type type) {
		this.file = java.util.Objects.requireNonNull(file).toAbsolutePath().normalize();
		this.type = java.util.Objects.requireNonNull(type);
		refresh();
	}

	void refresh() {
		setEnabled(file != null && receiver != null && !blocked.getAsBoolean());
		setToolTipText(file == null ? "Save a supported report first."
				: "Open last saved " + type + ": " + file + ". The file will be read again.");
	}
}
