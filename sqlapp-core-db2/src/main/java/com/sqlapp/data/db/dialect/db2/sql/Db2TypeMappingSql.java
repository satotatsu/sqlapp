/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2TypeMappings;
import com.sqlapp.data.db.dialect.db2.Db2TypeMappings.*;
import com.sqlapp.data.db.dialect.db2.Db2_1110;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Column;

final class Db2TypeMappingSql {
	private static final Set<String> SIMPLE = Set.of("SMALLINT", "INTEGER", "INT", "BIGINT", "REAL", "DOUBLE",
			"DOUBLE PRECISION", "DATE", "TIME");

	private Db2TypeMappingSql() {
	}

	static List<String> create(Catalog catalog, Dialect dialect) {
		var snapshot = validate(catalog, dialect);
		if (!snapshot.orderKnown() && snapshot.mappings().size() > 1)
			throw new IllegalArgumentException(
					"Db2 type mappings with tied or missing creation times require an explicit intended order via Db2TypeMappings.set(catalog, orderedMappings)");
		return create(snapshot.mappings(), dialect);
	}

	private static Snapshot validate(Catalog catalog, Dialect dialect) {
		var snapshot = Db2TypeMappings.get(catalog);
		if (!snapshot.mappings().isEmpty() && !(dialect instanceof Db2_1110))
			throw new IllegalArgumentException("Db2 type mapping generation requires LUW 11.1 or later");
		return snapshot;
	}

	private static List<String> create(List<Mapping> mappings, Dialect dialect) {
		List<String> result = new ArrayList<>();
		for (var mapping : mappings) {
			result.add(create(mapping, dialect));
			if (mapping.remarks() != null)
				result.add("COMMENT ON TYPE MAPPING " + id(mapping.name()) + " IS " + literal(mapping.remarks()));
		}
		return result;
	}

	private static String create(Mapping mapping, Dialect dialect) {
		if (mapping.definition() != null)
			return mapping.definition();
		var a = mapping.attributes();
		if ((a.get("TYPESCHEMA") != null && !"SYSIBM".equals(a.get("TYPESCHEMA"))) || a.get("REMOTE_TYPESCHEMA") != null
				|| (a.get("REMOTE_META_TYPE") != null && !"S".equals(a.get("REMOTE_META_TYPE")))
				|| a.get("S_OPR_P") != null || a.get("REMOTE_S_OPR_P") != null)
			throw unsupported(mapping);
		for (String key : List.of("BIT_DATA", "REMOTE_BIT_DATA"))
			if (a.get(key) != null && !"N".equals(a.get(key)))
				throw unsupported(mapping);
		String local = localType(mapping, dialect), remote = remoteType(mapping);
		String scope;
		if (a.get("SERVERNAME") != null) {
			if (a.get("SERVERTYPE") != null || a.get("SERVERVERSION") != null || a.get("WRAPNAME") != null)
				throw unsupported(mapping);
			scope = "SERVER " + id(a.get("SERVERNAME"));
		} else {
			if (a.get("SERVERTYPE") == null)
				throw unsupported(mapping);
			scope = "SERVER TYPE " + id(a.get("SERVERTYPE"));
			if (a.get("SERVERVERSION") != null)
				scope += " VERSION " + literal(a.get("SERVERVERSION"));
			if (a.get("WRAPNAME") != null)
				scope += " WRAPPER " + id(a.get("WRAPNAME"));
		}
		boolean forward = mapping.direction() == Direction.FORWARD;
		return "CREATE TYPE MAPPING " + id(mapping.name()) + (forward ? " FROM" : " TO") + " LOCAL TYPE " + local
				+ (forward ? " TO " : " FROM ") + scope + " REMOTE TYPE " + remote;
	}

	private static String localType(Mapping mapping, Dialect dialect) {
		var a = mapping.attributes();
		String name = a.get("TYPENAME");
		if (name == null)
			throw unsupported(mapping);
		Long length = null;
		Integer scale = null;
		if (Set.of("DECIMAL", "NUMERIC").contains(name)) {
			length = number(mapping, "LENGTH");
			long fractional = number(mapping, "SCALE");
			if (length < 1 || length > 31 || fractional < 0 || fractional > length)
				throw unsupported(mapping);
			scale = (int) fractional;
			for (String key : List.of("LOWER_LEN", "UPPER_LEN"))
				if (a.get(key) != null && number(mapping, key) != length)
					throw unsupported(mapping);
			for (String key : List.of("LOWER_SCALE", "UPPER_SCALE"))
				if (a.get(key) != null && number(mapping, key) != fractional)
					throw unsupported(mapping);
		} else if (!SIMPLE.contains(name))
			throw unsupported(mapping);
		else
			for (String key : List.of("LOWER_LEN", "UPPER_LEN", "LOWER_SCALE", "UPPER_SCALE", "SCALE"))
				if (a.get(key) != null && number(mapping, key) != 0 && number(mapping, key) != -1)
					throw unsupported(mapping);
		Column column = new Column("TYPE");
		if (!dialect.setDbType(name, length, scale, column) || column.getDataType() == null)
			throw unsupported(mapping);
		Db2SqlBuilder builder = new Db2SqlBuilder(dialect);
		builder.typeDefinition(column.getDataType(), null, length, scale);
		return builder.toString();
	}

	private static String remoteType(Mapping mapping) {
		var a = mapping.attributes();
		String name = a.get("REMOTE_TYPENAME");
		if (name == null)
			throw unsupported(mapping);
		if (SIMPLE.contains(name)) {
			for (String key : List.of("REMOTE_LOWER_LEN", "REMOTE_UPPER_LEN", "REMOTE_LOWER_SCALE",
					"REMOTE_UPPER_SCALE"))
				if (a.get(key) != null && number(mapping, key) != 0 && number(mapping, key) != -1)
					throw unsupported(mapping);
			return name;
		}
		if (!Set.of("DECIMAL", "NUMERIC", "NUMBER").contains(name))
			throw unsupported(mapping);
		long p1 = number(mapping, "REMOTE_LOWER_LEN"), p2 = number(mapping, "REMOTE_UPPER_LEN"),
				s1 = number(mapping, "REMOTE_LOWER_SCALE"), s2 = number(mapping, "REMOTE_UPPER_SCALE");
		if (p1 < 1 || p2 < p1 || s1 < 0 || s2 < s1 || s2 > p1)
			throw unsupported(mapping);
		if (mapping.direction() == Direction.REVERSE && (p1 != p2 || s1 != s2))
			throw unsupported(mapping);
		return name + "(" + range(p1, p2) + "," + range(s1, s2) + ")";
	}

	private static String range(long lower, long upper) {
		return lower == upper ? Long.toString(lower) : "[" + lower + ".." + upper + "]";
	}

	private static long number(Mapping mapping, String key) {
		try {
			return Long.parseLong(mapping.attributes().get(key));
		} catch (RuntimeException e) {
			throw unsupported(mapping);
		}
	}

	static List<String> diff(Catalog original, Catalog target, Dialect dialect) {
		var old = validate(original, dialect);
		var next = validate(target, dialect);
		if (old.equals(next))
			return List.of();
		if (!old.orderKnown() || !next.orderKnown())
			throw new IllegalArgumentException("Db2 type mapping changes require resolved precedence");
		var before = old.mappings();
		var after = next.mappings();
		if (after.size() < before.size() || !after.subList(0, before.size()).equals(before))
			throw new IllegalArgumentException(
					"Db2 type mapping removal, modification or reordering requires an explicit native plan to preserve precedence");
		return create(after.subList(before.size(), after.size()), dialect);
	}

	private static IllegalArgumentException unsupported(Mapping mapping) {
		return new IllegalArgumentException(
				"Db2 type mapping requires a complete CREATE definition for unsupported or ambiguous type conditions: "
						+ mapping.name());
	}

	private static String id(String value) {
		return Db2CreationContext.identifier(value);
	}

	private static String literal(String value) {
		return "'" + value.replace("'", "''") + "'";
	}
}
