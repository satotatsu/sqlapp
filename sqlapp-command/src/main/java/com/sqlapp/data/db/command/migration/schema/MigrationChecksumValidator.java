/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.sqlapp.data.db.command.migration.schema.DbVersionFileHandler.SqlFile;
import com.sqlapp.data.db.command.migration.schema.MigrationValidationResult.Entry;
import com.sqlapp.data.db.command.migration.schema.MigrationValidationResult.State;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

final class MigrationChecksumValidator {
	private MigrationChecksumValidator() {
	}

	static MigrationValidationResult validate(final Table table, final DbVersionHandler handler,
			final List<SqlFile> files) {
		final var byVersion = new HashMap<Long, SqlFile>();
		files.forEach(file -> byVersion.put(file.getVersionNumber(), file));
		final var entries = new ArrayList<Entry>();
		for (final Row row : table.getRows()) {
			final Long version = handler.getId(row);
			if (version == null || !handler.getStatus(row).isCompleted()) {
				continue;
			}
			final String expected = table.getColumns().contains(DbVersionHandler.CHECKSUM_COLUMN)
					? (String) row.get(DbVersionHandler.CHECKSUM_COLUMN) : null;
			if (expected == null) {
				entries.add(new Entry(version, State.UNVERIFIED, null, null));
				continue;
			}
			final SqlFile file = byVersion.get(version);
			if (file == null || file.getUpSqlFile() == null || !file.getUpSqlFile().isFile()) {
				entries.add(new Entry(version, State.MISSING, expected, null));
				continue;
			}
			final String actual = file.getUpSqlChecksum();
			entries.add(new Entry(version, expected.equals(actual) ? State.VERIFIED : State.CHANGED, expected, actual));
		}
		return new MigrationValidationResult(entries);
	}
}
