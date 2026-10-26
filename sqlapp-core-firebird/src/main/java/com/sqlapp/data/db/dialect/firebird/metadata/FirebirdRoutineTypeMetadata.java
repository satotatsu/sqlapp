/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.SQLException;
import com.sqlapp.data.db.dialect.firebird.FirebirdRoutineTypeReference;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.jdbc.ExResultSet;

/** Uses fields already fetched by the versioned argument query. */
final class FirebirdRoutineTypeMetadata {
	static void apply(NamedArgument argument, ExResultSet rows) throws SQLException {
		String domain = trim(rows.getString("TYPE_DOMAIN"));
		String relation = trim(rows.getString("TYPE_RELATION"));
		String column = trim(rows.getString("TYPE_COLUMN"));
		int mechanism = rows.getInt("TYPE_MECHANISM");
		if (mechanism != 0 && mechanism != 1)
			throw new SQLException("Unknown Firebird routine type declaration mechanism: " + mechanism);
		FirebirdRoutineTypeReference reference = null;
		if (relation != null || column != null) {
			if (mechanism != 1)
				throw new SQLException("TYPE OF COLUMN requires reference declaration mechanism 1");
			reference = FirebirdRoutineTypeReference.typeOfColumn(relation, column);
		} else if (domain != null && (mechanism == 1 || !domain.startsWith("RDB$"))) {
			reference = mechanism == 1 ? FirebirdRoutineTypeReference.typeOfDomain(domain)
					: FirebirdRoutineTypeReference.domain(domain);
		}
		if (reference == null) {
			if (mechanism == 1)
				throw new SQLException("TYPE OF declaration is missing its domain or column identity");
			return;
		}
		FirebirdRoutineTypeReference.set(argument.getSpecifics(), reference);
		// Inherited domain defaults/constraints must not become local declarations.
		argument.setDefaultValue(null);
		FirebirdUtils.setDefaultConstraint(argument, trim(rows.getString("LOCAL_DEFAULT_SOURCE")));
		argument.setNullable(rows.getInt("LOCAL_NULL_FLAG") != 1);
		String collation = trim(rows.getString("LOCAL_COLLATION_NAME"));
		// AbstractColumn's inherited-property setter clears an equal local value.
		if (!java.util.Objects.equals(argument.getCollation(), collation))
			argument.setCollation(collation);
	}

	private static String trim(String value) {
		return value == null ? null : value.stripTrailing();
	}

	private FirebirdRoutineTypeMetadata() {
	}
}
