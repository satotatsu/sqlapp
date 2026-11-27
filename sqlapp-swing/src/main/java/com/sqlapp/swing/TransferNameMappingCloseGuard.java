/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.Objects;
import java.util.function.*;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingIO;

/**
 * Compare mapping intent, not filters, preview state or snapshot-only types.
 */
final class TransferNameMappingCloseGuard {
	private final Supplier<TransferNameMappingIO.Configuration> current;
	private final BooleanSupplier discard;
	private TransferNameMappingIO.Configuration baseline;

	TransferNameMappingCloseGuard(Supplier<TransferNameMappingIO.Configuration> current, BooleanSupplier discard) {
		this.current = Objects.requireNonNull(current);
		this.discard = Objects.requireNonNull(discard);
	}

	void markClean() {
		baseline = current.get();
	}

	boolean canClose() {
		if (baseline == null)
			return true; // An initial read failed; no editable document was loaded.
		try {
			if (baseline.equals(current.get()))
				return true;
		} catch (IllegalArgumentException invalidEdits) {
			// Invalid in-progress cells are still unsaved work, never a clean document.
		}
		return discard.getAsBoolean();
	}
}
