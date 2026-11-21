/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix;

import java.util.Map;
import java.util.List;

/** Fixed, unambiguous SQL type names for routine privilege signatures. */
public final class InformixRoutineSignature {
	public static final String TYPES = "INFORMIX_ROUTINE_SIGNATURE_TYPES";
	private static final Map<Integer, String> TYPES_BY_CODE = Map.of(1, "SMALLINT", 2, "INTEGER", 3, "FLOAT", 4,
			"SMALLFLOAT", 7, "DATE", 17, "INT8", 52, "BIGINT");

	private InformixRoutineSignature() {
	}

	public static String builtinType(int type, int extendedType, int mode) {
		return extendedType == 0 && mode == 1 ? TYPES_BY_CODE.get(type) : null;
	}

	public static String sqlTypes(String types) {
		List<String> parameters = List.of(types.split(",", -1));
		if (parameters.stream().anyMatch(type -> !TYPES_BY_CODE.containsValue(type)))
			throw new IllegalArgumentException("Unsupported Informix routine privilege parameter type");
		return String.join(", ", parameters);
	}
}
