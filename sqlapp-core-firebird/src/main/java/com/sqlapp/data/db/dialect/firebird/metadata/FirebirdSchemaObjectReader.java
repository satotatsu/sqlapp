/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObject;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObject.Kind;

/** Firebird 2.5+ set-based catalog reads. No external library is loaded. */
public final class FirebirdSchemaObjectReader {
	public FirebirdSchemaObjectReader(Dialect dialect) {
		if (!(dialect instanceof com.sqlapp.data.db.dialect.firebird.Firebird25))
			throw new UnsupportedOperationException("Native schema object extraction requires Firebird 2.5 or later.");
	}

	public List<FirebirdSchemaObject> read(Connection connection) throws SQLException {
		return read(connection, false);
	}

	public List<FirebirdSchemaObject> read(Connection connection, boolean excludeSystemObjects) throws SQLException {
		var objects = new ArrayList<FirebirdSchemaObject>();
		String where = excludeSystemObjects ? " WHERE COALESCE(RDB$SYSTEM_FLAG, 0) = 0" : "";
		try (var statement = connection
				.prepareStatement("SELECT RDB$EXCEPTION_NAME, RDB$MESSAGE, RDB$DESCRIPTION FROM RDB$EXCEPTIONS" + where
						+ " ORDER BY RDB$EXCEPTION_NAME");
				var rows = statement.executeQuery()) {
			while (rows.next()) {
				String name = rows.getString(1).stripTrailing();
				objects.add(object(Kind.EXCEPTION, name,
						"CREATE EXCEPTION " + quote(name) + " " + literal(rows.getString(2)), rows.getString(3)));
			}
		}
		try (var statement = connection.prepareStatement(
				"SELECT RDB$FUNCTION_NAME, RDB$INPUT_SUB_TYPE, RDB$OUTPUT_SUB_TYPE, RDB$ENTRYPOINT, RDB$MODULE_NAME, RDB$DESCRIPTION FROM RDB$FILTERS"
						+ where + " ORDER BY RDB$FUNCTION_NAME");
				var rows = statement.executeQuery()) {
			while (rows.next()) {
				String name = rows.getString(1).stripTrailing();
				objects.add(object(Kind.BLOB_FILTER, name,
						"DECLARE FILTER " + quote(name) + " INPUT_TYPE " + rows.getShort(2) + " OUTPUT_TYPE "
								+ rows.getShort(3) + " ENTRY_POINT " + literal(rows.getString(4).stripTrailing())
								+ " MODULE_NAME " + literal(rows.getString(5)),
						rows.getString(6)));
			}
		}
		readCollations(connection, objects);
		return List.copyOf(objects);
	}

	private void readCollations(Connection connection, List<FirebirdSchemaObject> objects) throws SQLException {
		// Built-in collations are installed by Firebird and must never become CREATE
		// statements.
		try (var statement = connection.prepareStatement("""
				SELECT C.RDB$COLLATION_NAME, CS.RDB$CHARACTER_SET_NAME, C.RDB$BASE_COLLATION_NAME,
				       C.RDB$COLLATION_ATTRIBUTES, C.RDB$SPECIFIC_ATTRIBUTES, C.RDB$DESCRIPTION
				FROM RDB$COLLATIONS C
				JOIN RDB$CHARACTER_SETS CS ON CS.RDB$CHARACTER_SET_ID = C.RDB$CHARACTER_SET_ID
				WHERE COALESCE(C.RDB$SYSTEM_FLAG, 0) = 0
				ORDER BY CS.RDB$CHARACTER_SET_NAME, C.RDB$COLLATION_NAME
				"""); var rows = statement.executeQuery()) {
			while (rows.next()) {
				String name = rows.getString(1).stripTrailing();
				String charset = rows.getString(2).stripTrailing();
				String base = rows.getString(3);
				int attributes = rows.getInt(4);
				if ((attributes & ~7) != 0)
					throw new SQLException(
							"Unsupported Firebird collation attribute mask for " + name + ": " + attributes);
				// Like isql extraction, use the external base, not a dependency on another
				// database collation.
				String create = "CREATE COLLATION " + quote(name) + " FOR " + quote(charset)
						+ (base == null ? "" : " FROM EXTERNAL (" + literal(base.stripTrailing()) + ")")
						+ ((attributes & 1) == 0 ? " NO PAD" : " PAD SPACE")
						+ ((attributes & 2) == 0 ? " CASE SENSITIVE" : " CASE INSENSITIVE")
						+ ((attributes & 4) == 0 ? " ACCENT SENSITIVE" : " ACCENT INSENSITIVE");
				String specific = rows.getString(5);
				if (specific != null)
					create += " " + literal(specific);
				objects.add(object(Kind.COLLATION, name, create, rows.getString(6)));
			}
		}
	}

	private FirebirdSchemaObject object(Kind kind, String name, String create, String comment) {
		String type = switch (kind) {
		case EXCEPTION -> "EXCEPTION";
		case BLOB_FILTER -> "FILTER";
		case COLLATION -> "COLLATION";
		};
		var definitions = new ArrayList<String>();
		definitions.add(create);
		if (comment != null)
			definitions.add("COMMENT ON " + type + " " + quote(name) + " IS " + literal(comment));
		return new FirebirdSchemaObject(kind, name, definitions, "DROP " + type + " " + quote(name));
	}

	private String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	private String literal(String value) {
		return "'" + value.replace("'", "''") + "'";
	}
}
