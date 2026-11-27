/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-drsum.
 *
 * sqlapp-core-drsum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-drsum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-drsum.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.drsum.metadata;

import java.util.Locale;

/**
 * Interpretation of Dr.Sum system table values.
 * <p>
 * The Dr.Sum manual lists the columns of {@code __all_tables__} but not the
 * encoding of {@code column_null} and {@code column_unique}. They are read as
 * flags: {@code 1/Y/YES/T/TRUE} set, {@code 0/N/NO/F/FALSE} or NULL/empty
 * clear. {@code column_null} is treated as the presence of the NOT NULL
 * constraint. Any other value is rejected rather than guessed.
 */
final class DrSumMetadataSupport {

	private DrSumMetadataSupport() {
	}

	/** Whether the name is a Dr.Sum system table such as {@code __all_tables__}. */
	static boolean isSystemTable(final String name) {
		return name != null && name.length() > 4 && name.startsWith("__") && name.endsWith("__");
	}

	static boolean flag(final String value, final String column, final String table, final String name) {
		if (value == null) {
			return false;
		}
		final String text = value.trim().toUpperCase(Locale.ROOT);
		switch (text) {
		case "":
		case "0":
		case "N":
		case "NO":
		case "F":
		case "FALSE":
			return false;
		case "1":
		case "Y":
		case "YES":
		case "T":
		case "TRUE":
			return true;
		default:
			throw new IllegalStateException("Unrecognized Dr.Sum __all_tables__." + column + " value '" + value
					+ "' for " + table + "." + name
					+ ". Report the value so the dialect can map it.");
		}
	}

	static boolean isEmpty(final String value) {
		return value == null || value.isEmpty();
	}
}
