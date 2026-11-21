/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix;

import java.util.Map;
import java.util.ArrayList;
import java.util.regex.Pattern;

/** Fixed, unambiguous SQL type names for routine privilege signatures. */
public final class InformixRoutineSignature {
	public static final String TYPES = "INFORMIX_ROUTINE_SIGNATURE_TYPES";
	private static final Map<Integer, String> TYPES_BY_CODE = Map.of(1, "SMALLINT", 2, "INTEGER", 3, "FLOAT", 4,
			"SMALLFLOAT", 7, "DATE", 17, "INT8", 45, "BOOLEAN", 52, "BIGINT");
	private static final Pattern CHARACTER_TYPE = Pattern
			.compile("(CHAR|NCHAR|VARCHAR|NVARCHAR)\\(([1-9][0-9]{0,4})\\)");
	private static final Pattern NAMED_TYPE = Pattern.compile("[A-Za-z_][A-Za-z_0-9]*\\.[A-Za-z_][A-Za-z_0-9]*");
	private static final String QUOTED_IDENTIFIER = "\"(?:[^\"\\p{Cntrl}]|\"\")+\"";
	private static final Pattern PARAMETER = Pattern
			.compile(QUOTED_IDENTIFIER + "\\." + QUOTED_IDENTIFIER + "|[^,\"]+");
	private static final Pattern QUOTED_TYPE = Pattern.compile(QUOTED_IDENTIFIER + "\\." + QUOTED_IDENTIFIER);

	private InformixRoutineSignature() {
	}

	public static String builtinType(int type, int extendedType, int mode) {
		return extendedType == 0 ? builtinTypeWithLength(type, 0, mode) : null;
	}

	public static String builtinTypeWithLength(int type, int length, int mode) {
		if (mode != 1)
			return null;
		String characterType = switch (type) {
		case 0 -> "CHAR";
		case 13 -> "VARCHAR";
		case 15 -> "NCHAR";
		case 16 -> "NVARCHAR";
		default -> null;
		};
		if (characterType == null)
			return TYPES_BY_CODE.get(type);
		return length > 0 && length <= maximumCharacterLength(characterType) ? characterType + "(" + length + ")"
				: null;
	}

	public static String sqlTypes(String types) {
		var parameters = new ArrayList<String>();
		var matcher = PARAMETER.matcher(types);
		int start = 0;
		while (start < types.length() && matcher.region(start, types.length()).lookingAt()) {
			parameters.add(sqlType(matcher.group()));
			int end = matcher.end();
			if (end == types.length())
				return String.join(", ", parameters);
			if (types.charAt(end) != ',')
				break;
			start = end + 1;
		}
		throw new IllegalArgumentException("Invalid Informix routine privilege signature");
	}

	public static String namedType(String owner, String name, String kind, int mode) {
		if (mode != 1 || owner == null || name == null || !("B".equals(kind) || "D".equals(kind) || "R".equals(kind)))
			return null;
		owner = owner.stripTrailing();
		name = name.stripTrailing();
		if (owner.isBlank() || name.isBlank() || owner.chars().anyMatch(Character::isISOControl)
				|| name.chars().anyMatch(Character::isISOControl))
			return null;
		String qualified = owner + "." + name;
		if (NAMED_TYPE.matcher(qualified).matches())
			return qualified;
		return quote(owner) + "." + quote(name);
	}

	private static String sqlType(String type) {
		if (TYPES_BY_CODE.containsValue(type))
			return type;
		var character = CHARACTER_TYPE.matcher(type);
		if (character.matches() && Integer.parseInt(character.group(2)) <= maximumCharacterLength(character.group(1)))
			return type;
		if (QUOTED_TYPE.matcher(type).matches())
			return type;
		if (NAMED_TYPE.matcher(type).matches()) {
			int separator = type.indexOf('.');
			return "\"" + type.substring(0, separator) + "\".\"" + type.substring(separator + 1) + "\"";
		}
		throw new IllegalArgumentException("Unsupported Informix routine privilege parameter type");
	}

	private static int maximumCharacterLength(String type) {
		return type.endsWith("VARCHAR") ? 255 : 32767;
	}

	private static String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}
}
