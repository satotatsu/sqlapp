/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;

/** Names follow the bit positions in RDB$ROLES.RDB$SYSTEM_PRIVILEGES. */
public final class FirebirdSystemPrivileges {
	public static final String ROLE_PRIVILEGES = "firebird.role.systemPrivileges";
	private static final List<String> NAMES = List.of("", "USER_MANAGEMENT", "READ_RAW_PAGES", "CREATE_USER_TYPES",
			"USE_NBACKUP_UTILITY", "CHANGE_SHUTDOWN_MODE", "TRACE_ANY_ATTACHMENT", "MONITOR_ANY_ATTACHMENT",
			"ACCESS_SHUTDOWN_DATABASE", "CREATE_DATABASE", "DROP_DATABASE", "USE_GBAK_UTILITY", "USE_GSTAT_UTILITY",
			"USE_GFIX_UTILITY", "IGNORE_DB_TRIGGERS", "CHANGE_HEADER_SETTINGS", "SELECT_ANY_OBJECT_IN_DATABASE",
			"ACCESS_ANY_OBJECT_IN_DATABASE", "MODIFY_ANY_OBJECT_IN_DATABASE", "CHANGE_MAPPING_RULES",
			"USE_GRANTED_BY_CLAUSE", "GRANT_REVOKE_ON_ANY_OBJECT", "GRANT_REVOKE_ANY_DDL_RIGHT",
			"CREATE_PRIVILEGED_ROLES", "GET_DBCRYPT_INFO", "MODIFY_EXT_CONN_POOL", "REPLICATE_INTO_DATABASE",
			"PROFILE_ANY_ATTACHMENT");

	private FirebirdSystemPrivileges() {
	}

	public static String name(int code, Dialect dialect) {
		if (code <= 0 || code >= NAMES.size())
			throw new IllegalArgumentException("Unknown Firebird system privilege code: " + code);
		String name = NAMES.get(code);
		validate(name, dialect);
		return name;
	}

	public static String decode(byte[] bytes, Dialect dialect) {
		if (bytes == null)
			return "";
		if (bytes.length != 8)
			throw new IllegalArgumentException("Firebird system privilege bitmap must contain 8 bytes");
		List<String> result = new ArrayList<>();
		for (int bit = 0; bit < bytes.length * 8; bit++) {
			if ((bytes[bit / 8] & (1 << (bit % 8))) == 0)
				continue;
			if (bit == 0 || bit >= NAMES.size())
				throw new IllegalArgumentException("Unknown Firebird system privilege bit: " + bit);
			validate(NAMES.get(bit), dialect);
			result.add(NAMES.get(bit));
		}
		return String.join(",", result);
	}

	public static String normalize(String value, Dialect dialect) {
		if (value == null)
			return "";
		if (value.isBlank())
			throw new IllegalArgumentException(
					"System privileges must be a non-empty comma-separated list; remove the specific to clear them");
		var result = new LinkedHashSet<String>();
		for (String item : value.split(",", -1)) {
			String name = item.trim().toUpperCase(Locale.ROOT);
			validate(name, dialect);
			if (!result.add(name))
				throw new IllegalArgumentException("Duplicate Firebird system privilege: " + name);
		}
		return String.join(",", result);
	}

	private static void validate(String name, Dialect dialect) {
		if (name.isEmpty() || !NAMES.contains(name))
			throw new IllegalArgumentException("Unknown Firebird system privilege: " + name);
		if (dialect.compareTo(DialectHolder.defaultDialect40) < 0)
			throw new IllegalArgumentException("System privileges require Firebird 4.0 or later");
		if ("PROFILE_ANY_ATTACHMENT".equals(name) && dialect.compareTo(DialectHolder.defaultDialect50) < 0)
			throw new IllegalArgumentException("PROFILE_ANY_ATTACHMENT requires Firebird 5.0 or later");
	}
}
