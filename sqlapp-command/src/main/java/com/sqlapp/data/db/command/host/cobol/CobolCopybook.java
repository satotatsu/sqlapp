/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.cobol;

import java.util.List;

/**
 * Parsed COBOL copybook: the record descriptions (level 01, 77, or the
 * synthesized record of a copybook without a level-01 entry) with their storage
 * layout, and the warnings found while parsing.
 */
public record CobolCopybook(List<CobolItem> records, List<String> warnings) {
	public CobolCopybook {
		records = List.copyOf(records);
		warnings = List.copyOf(warnings);
	}

	/** The record with this name, ignoring case, or {@code null}. */
	public CobolItem record(String name) {
		return records.stream().filter(record -> record.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
	}
}
