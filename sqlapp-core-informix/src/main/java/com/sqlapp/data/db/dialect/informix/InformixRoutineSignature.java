/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix;

import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;

/** Fixed, unambiguous SQL type names for routine privilege signatures. */
public final class InformixRoutineSignature {
	public static final String TYPES = "INFORMIX_ROUTINE_SIGNATURE_TYPES";
	private static final Map<Integer, String> TYPES_BY_CODE = Map.of(1, "SMALLINT", 2, "INTEGER", 3, "FLOAT", 4,
			"SMALLFLOAT", 7, "DATE", 17, "INT8", 52, "BIGINT");
	private static final Pattern NAMED_TYPE = Pattern.compile("[A-Za-z_][A-Za-z_0-9]*\\.[A-Za-z_][A-Za-z_0-9]*");

	private InformixRoutineSignature() {
	}

	public static String builtinType(int type, int extendedType, int mode) {
		return extendedType == 0 && mode == 1 ? TYPES_BY_CODE.get(type) : null;
	}

	public static String sqlTypes(String types) {
		List<String> parameters = List.of(types.split(",", -1));
		return String.join(", ", parameters.stream().map(InformixRoutineSignature::sqlType).toList());
	}

	public static String namedType(String owner, String name, String kind, int mode) {
		if (mode != 1 || owner == null || name == null
				|| !("B".equals(kind) || "D".equals(kind) || "R".equals(kind))) return null;
		String qualified = owner.stripTrailing() + "." + name.stripTrailing();
		return NAMED_TYPE.matcher(qualified).matches() ? qualified : null;
	}

	private static String sqlType(String type) {
		if (TYPES_BY_CODE.containsValue(type)) return type;
		if (NAMED_TYPE.matcher(type).matches()) {
			int separator = type.indexOf('.');
			return "\"" + type.substring(0, separator) + "\".\"" + type.substring(separator + 1) + "\"";
		}
		throw new IllegalArgumentException("Unsupported Informix routine privilege parameter type");
	}
}
