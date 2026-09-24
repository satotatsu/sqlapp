/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.schema;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Row;

class MigrationOutOfOrderTest {
	@Test
	void remainsPermissiveByDefaultButCanRejectPendingOlderVersions() {
		final var handler = new DbVersionHandler();
		final var history = handler.createVersionTableDefinition("changelog");
		add(history.newRow(), handler, 1L, Status.Completed);
		add(history.newRow(), handler, 2L, Status.Pending);
		add(history.newRow(), handler, 3L, Status.Completed);
		final var command = new MigrationCommand();
		assertDoesNotThrow(() -> command.validateOutOfOrder(history, handler));
		command.setRejectOutOfOrder(true);
		assertThrows(RuntimeException.class, () -> command.validateOutOfOrder(history, handler));
	}

	private static void add(final Row row, final DbVersionHandler handler, final long version, final Status status) {
		row.put(handler.getIdColumnName(), version);
		row.put(handler.getStatusColumnName(), status.toString());
		row.getTable().getRows().add(row);
	}
}
