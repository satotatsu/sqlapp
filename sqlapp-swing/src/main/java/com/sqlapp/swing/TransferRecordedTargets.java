/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.Map;
import com.sqlapp.data.db.command.migration.transfer.TransferFileImportService;
import com.sqlapp.data.db.command.migration.transfer.TransferSchemaPreparation;

/** Recorded review identities only; never opens metadata or artifact files. */
final class TransferRecordedTargets {
	private TransferRecordedTargets() {
	}

	static boolean matches(TransferFileImportService.Outcome outcome, FileTransferDesktopService.Reviewed prior) {
		return prior != null && outcome.manifestFingerprint() != null && outcome.planFingerprint() != null
				&& outcome.manifestFingerprint().equals(prior.review().manifestFingerprint())
				&& outcome.planFingerprint().equals(prior.review().planFingerprint());
	}

	static TransferSchemaPreparation.Plan plan(Object value, FileTransferDesktopService.Reviewed prior) {
		if (value instanceof DirectTransferDesktopService.Executed executed)
			return executed.outcome().prepared().schemaPreparation();
		if (value instanceof TransferFileImportService.Outcome outcome && matches(outcome, prior))
			return prior.review().schemaPreparation();
		return null;
	}

	static Map<String, String> names(Object value, FileTransferDesktopService.Reviewed prior) {
		var plan = plan(value, prior);
		return plan == null ? Map.of()
				: plan.tables().stream()
						.collect(java.util.stream.Collectors.toMap(TransferSchemaPreparation.TablePreparation::taskId,
								target -> java.util.stream.Stream
										.of(target.targetCatalog(), target.targetSchema(), target.targetTable())
										.filter(java.util.Objects::nonNull)
										.map(name -> "\"" + name.replace("\"", "\"\"") + "\"")
										.collect(java.util.stream.Collectors.joining("."))));
	}
}
