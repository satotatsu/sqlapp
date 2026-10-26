/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.*;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.*;

/**
 * Bulk reads for compiled dependencies and, from 3.0, public package
 * signatures.
 */
public final class FirebirdSchemaDependencyReader {
	private final boolean packages;

	public FirebirdSchemaDependencyReader(Dialect dialect) {
		if (!(dialect instanceof Firebird25))
			throw new UnsupportedOperationException("Dependency extraction requires Firebird 2.5 or later");
		packages = dialect instanceof Firebird30;
	}

	public List<Dependency> read(Connection connection) throws SQLException {
		String sql = """
				SELECT DISTINCT D.RDB$DEPENDENT_NAME, D.RDB$DEPENDENT_TYPE,
				 D.RDB$DEPENDED_ON_NAME, D.RDB$DEPENDED_ON_TYPE,
				 RF.RDB$RELATION_NAME, I.RDB$RELATION_NAME, T.RDB$RELATION_NAME, T.RDB$SYSTEM_FLAG,
				 %s, CASE WHEN R.RDB$VIEW_BLR IS NULL THEN 0 ELSE 1 END
				FROM RDB$DEPENDENCIES D
				LEFT JOIN RDB$RELATION_FIELDS RF ON D.RDB$DEPENDENT_TYPE = 3 AND RF.RDB$FIELD_SOURCE = D.RDB$DEPENDENT_NAME
				LEFT JOIN RDB$INDICES I ON D.RDB$DEPENDENT_TYPE IN (6, 10) AND I.RDB$INDEX_NAME = D.RDB$DEPENDENT_NAME
				LEFT JOIN RDB$TRIGGERS T ON D.RDB$DEPENDENT_TYPE IN (2, 4) AND T.RDB$TRIGGER_NAME = D.RDB$DEPENDENT_NAME
				LEFT JOIN RDB$RELATIONS R ON R.RDB$RELATION_NAME = COALESCE(RF.RDB$RELATION_NAME, I.RDB$RELATION_NAME, T.RDB$RELATION_NAME)
				ORDER BY 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
				"""
				.formatted(packages ? "D.RDB$PACKAGE_NAME" : "CAST(NULL AS VARCHAR(31))");
		var result = new LinkedHashSet<Dependency>();
		try (var statement = connection.prepareStatement(sql); var rows = statement.executeQuery()) {
			while (rows.next()) {
				String name = trim(rows.getString(1));
				int type = rows.getInt(2);
				if (type == 3 && rows.getString(5) != null) {
					name = trim(rows.getString(5));
					type = rows.getInt(10);
				}
				if ((type == 6 || type == 10) && rows.getString(6) != null) {
					name = trim(rows.getString(6));
					type = 0;
				}
				if ((type == 4 || type == 2 && rows.getInt(8) > 0) && rows.getString(7) != null) {
					name = trim(rows.getString(7));
					type = rows.getInt(10);
				}
				ObjectId dependent = id(type, name);
				String pkg = trim(rows.getString(9));
				int prerequisiteType = rows.getInt(4);
				ObjectId prerequisite = pkg != null && (prerequisiteType == 5 || prerequisiteType == 15)
						? new ObjectId(Kind.PACKAGE, pkg)
						: id(prerequisiteType, trim(rows.getString(3)));
				if (dependent != null && prerequisite != null && !dependent.equals(prerequisite))
					result.add(new Dependency(dependent, prerequisite));
			}
		}
		if (packages)
			readPackageSignatures(connection, result);
		return List.copyOf(result);
	}

	private void readPackageSignatures(Connection connection, LinkedHashSet<Dependency> result) throws SQLException {
		// TYPE OF COLUMN/domain declarations in package headers are not all recorded in
		// RDB$DEPENDENCIES.
		String sql = """
				SELECT A.RDB$PACKAGE_NAME, A.RDB$RELATION_NAME, A.RDB$FIELD_SOURCE,
				 CASE WHEN R.RDB$VIEW_BLR IS NULL THEN 0 ELSE 1 END, C.RDB$COLLATION_NAME
				FROM RDB$FUNCTION_ARGUMENTS A
				JOIN RDB$FUNCTIONS F ON F.RDB$FUNCTION_NAME = A.RDB$FUNCTION_NAME AND F.RDB$PACKAGE_NAME = A.RDB$PACKAGE_NAME
				LEFT JOIN RDB$RELATIONS R ON R.RDB$RELATION_NAME = A.RDB$RELATION_NAME
				LEFT JOIN RDB$FIELDS AF ON AF.RDB$FIELD_NAME = A.RDB$FIELD_SOURCE
				LEFT JOIN RDB$COLLATIONS C ON C.RDB$CHARACTER_SET_ID = AF.RDB$CHARACTER_SET_ID
				 AND C.RDB$COLLATION_ID = COALESCE(A.RDB$COLLATION_ID, AF.RDB$COLLATION_ID)
				WHERE A.RDB$PACKAGE_NAME IS NOT NULL AND COALESCE(F.RDB$PRIVATE_FLAG, 0) = 0
				UNION
				SELECT A.RDB$PACKAGE_NAME, A.RDB$RELATION_NAME, A.RDB$FIELD_SOURCE,
				 CASE WHEN R.RDB$VIEW_BLR IS NULL THEN 0 ELSE 1 END, C.RDB$COLLATION_NAME
				FROM RDB$PROCEDURE_PARAMETERS A
				JOIN RDB$PROCEDURES P ON P.RDB$PROCEDURE_NAME = A.RDB$PROCEDURE_NAME AND P.RDB$PACKAGE_NAME = A.RDB$PACKAGE_NAME
				LEFT JOIN RDB$RELATIONS R ON R.RDB$RELATION_NAME = A.RDB$RELATION_NAME
				LEFT JOIN RDB$FIELDS AF ON AF.RDB$FIELD_NAME = A.RDB$FIELD_SOURCE
				LEFT JOIN RDB$COLLATIONS C ON C.RDB$CHARACTER_SET_ID = AF.RDB$CHARACTER_SET_ID
				 AND C.RDB$COLLATION_ID = COALESCE(A.RDB$COLLATION_ID, AF.RDB$COLLATION_ID)
				WHERE A.RDB$PACKAGE_NAME IS NOT NULL AND COALESCE(P.RDB$PRIVATE_FLAG, 0) = 0
				ORDER BY 1, 2, 3, 4, 5
				""";
		try (var statement = connection.prepareStatement(sql); var rows = statement.executeQuery()) {
			while (rows.next()) {
				ObjectId header = new ObjectId(Kind.PACKAGE, trim(rows.getString(1)));
				String relation = trim(rows.getString(2)), domain = trim(rows.getString(3));
				if (relation != null)
					result.add(new Dependency(header,
							new ObjectId(rows.getInt(4) == 1 ? Kind.VIEW : Kind.TABLE, relation)));
				else if (domain != null && !domain.startsWith("RDB$"))
					result.add(new Dependency(header, new ObjectId(Kind.DOMAIN, domain)));
				String collation = trim(rows.getString(5));
				if (collation != null)
					result.add(new Dependency(header, new ObjectId(Kind.COLLATION, collation)));
			}
		}
	}

	private ObjectId id(int type, String name) {
		Kind kind = switch (type) {
		case 0 -> Kind.TABLE;
		case 1 -> Kind.VIEW;
		case 2 -> Kind.TRIGGER;
		case 5 -> Kind.PROCEDURE;
		case 7 -> Kind.EXCEPTION;
		case 9 -> Kind.DOMAIN;
		case 14 -> Kind.SEQUENCE;
		case 15 -> Kind.FUNCTION;
		case 16 -> Kind.BLOB_FILTER;
		case 17 -> Kind.COLLATION;
		case 18 -> Kind.PACKAGE;
		case 19 -> Kind.PACKAGE_BODY;
		default -> null;
		};
		return kind == null ? null : new ObjectId(kind, name);
	}

	private String trim(String value) {
		return value == null ? null : value.stripTrailing();
	}
}
