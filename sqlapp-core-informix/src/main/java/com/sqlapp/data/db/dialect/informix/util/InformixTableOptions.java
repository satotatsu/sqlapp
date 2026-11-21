/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.util;

import java.util.LinkedHashMap;
import java.util.Map;
import com.sqlapp.data.schemas.Table;

/** Table options that can be changed without relocating allocated extents. */
public final class InformixTableOptions {
	public static final String LOCK_MODE = "INFORMIX_LOCK_MODE";
	public static final String NEXT_SIZE_KB = "INFORMIX_NEXT_SIZE_KB";

	private InformixTableOptions() {
	}

	public static String lockMode(Table table) {
		String mode = table.getSpecifics().get(LOCK_MODE);
		if (mode != null && !"ROW".equals(mode) && !"PAGE".equals(mode))
			throw new IllegalArgumentException("Informix table lock mode must be ROW or PAGE");
		return mode;
	}

	public static Integer nextSize(Table table) {
		String value = table.getSpecifics().get(NEXT_SIZE_KB);
		if (value == null)
			return null;
		int size = Integer.parseInt(value);
		if (size <= 0)
			throw new IllegalArgumentException("Informix NEXT SIZE must be positive");
		return size;
	}

	public static Map<String, String> otherSpecifics(Table table) {
		var specifics = new LinkedHashMap<>(table.getSpecifics());
		specifics.remove(LOCK_MODE);
		specifics.remove(NEXT_SIZE_KB);
		return specifics;
	}
}
