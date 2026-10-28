/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import com.sqlapp.data.db.command.migration.transfer.TransferSchemaPreparation;

/**
 * Only shared, controlled validation text; excludes JDBC cleanup
 * causes/suppression.
 */
final class TransferPreparationFailure extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

	TransferPreparationFailure(TransferSchemaPreparation.ValidationException failure) {
		super(failure.getMessage());
	}
}
