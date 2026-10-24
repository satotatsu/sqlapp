/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.util;

import java.util.Map;
import java.util.Set;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;

/** Firebird privilege identity retained in the shared model's specifics. */
public final class FirebirdPrivilege {
	public static final String OBJECT_TYPE = "firebird.objectType";
	public static final String GRANTEE_TYPE = "firebird.granteeType";
	public static final String GRANTOR = "firebird.grantor";
	public static final String REVOKE_OPTION = "firebird.revokeOption";
	private static final Set<String> GRANTEES = Set.of("USER", "ROLE", "VIEW", "TRIGGER", "PROCEDURE", "FUNCTION",
			"PACKAGE", "SYSTEM PRIVILEGE");

	private FirebirdPrivilege() {
	}

	public static String objectType(int code) {
		return switch (code) {
		case 0, 1 -> "TABLE";
		case 2 -> "TRIGGER";
		case 5 -> "PROCEDURE";
		case 7 -> "EXCEPTION";
		case 8 -> "USER";
		case 13 -> "ROLE";
		case 14 -> "SEQUENCE";
		case 15 -> "FUNCTION";
		case 17 -> "COLLATION";
		case 18 -> "PACKAGE";
		default -> throw new IllegalArgumentException("Unsupported Firebird privilege object type: " + code);
		};
	}

	public static String granteeType(int code) {
		return code == 20 ? "SYSTEM PRIVILEGE" : code == 1 ? "VIEW" : objectType(code);
	}

	public static String granteeName(String name, int type, com.sqlapp.data.db.dialect.Dialect dialect) {
		return type == 20 ? FirebirdSystemPrivileges.name(Integer.parseInt(name), dialect) : name;
	}

	public static void identifier(String name, String label) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Firebird " + label + " is required");
		}
	}

	/** An explicit option keeps the default REVOKE behavior unchanged. */
	public static void revokeOption(boolean grant, boolean role, Map<String, String> specifics,
			FirebirdSqlBuilder builder) {
		String option = specifics.get(REVOKE_OPTION);
		if (option == null)
			return;
		if (grant)
			throw new IllegalArgumentException("firebird.revokeOption is only valid for REVOKE");
		if (!role && "GRANT".equals(option))
			builder._add("GRANT OPTION FOR ");
		else if (role && "ADMIN".equals(option))
			builder._add("ADMIN OPTION FOR ");
		else if (role && "DEFAULT".equals(option)) {
			if (builder.getDialect().compareTo(DialectHolder.defaultDialect40) < 0)
				throw new IllegalArgumentException("Revoking a default role property requires Firebird 4.0 or later");
			builder._add("DEFAULT ");
		} else
			throw new IllegalArgumentException(
					"Unsupported Firebird revoke option for " + (role ? "roles: " : "privileges: ") + option);
	}

	public static void grantee(String name, Map<String, String> specifics, FirebirdSqlBuilder builder) {
		identifier(name, "grantee name");
		String type = specifics.getOrDefault(GRANTEE_TYPE, "USER");
		if (!GRANTEES.contains(type)) {
			throw new IllegalArgumentException("Unsupported Firebird grantee type: " + type);
		}
		if ("SYSTEM PRIVILEGE".equals(type)) {
			String privilege = FirebirdSystemPrivileges.normalize(name, builder.getDialect());
			if (privilege.contains(","))
				throw new IllegalArgumentException("A grant recipient must name one system privilege");
			builder._add("SYSTEM PRIVILEGE ")._add(privilege);
			return;
		}
		if (Set.of("FUNCTION", "PACKAGE").contains(type)
				&& builder.getDialect().compareTo(DialectHolder.defaultDialect30) < 0) {
			throw new IllegalArgumentException(type + " grantees require Firebird 3.0 or later");
		}
		if ("USER".equals(type) && "PUBLIC".equals(name)) {
			builder._add("PUBLIC");
		} else {
			builder._add(type).space().name(name);
		}
	}

	public static void grantor(String name, FirebirdSqlBuilder builder) {
		if (name == null)
			return;
		identifier(name, "grantor name");
		if (builder.getDialect().compareTo(DialectHolder.defaultDialect25) < 0) {
			throw new IllegalArgumentException("Preserving the Firebird grantor requires Firebird 2.5 or later");
		}
		builder._add(" GRANTED BY ").name(name);
	}

	public static void target(String type, String privilege, FirebirdSqlBuilder builder) {
		boolean table = "TABLE".equals(type);
		boolean routine = Set.of("PROCEDURE", "FUNCTION", "PACKAGE").contains(type);
		boolean usage = Set.of("SEQUENCE", "EXCEPTION").contains(type);
		if (!(table && Set.of("SELECT", "INSERT", "UPDATE", "DELETE", "REFERENCES", "ALL").contains(privilege))
				&& !(routine && "EXECUTE".equals(privilege)) && !(usage && "USAGE".equals(privilege))) {
			throw new IllegalArgumentException("Unsupported Firebird privilege/target: " + privilege + " ON " + type);
		}
		if ((usage || "FUNCTION".equals(type) || "PACKAGE".equals(type))
				&& builder.getDialect().compareTo(DialectHolder.defaultDialect30) < 0) {
			throw new IllegalArgumentException(privilege + " ON " + type + " requires Firebird 3.0 or later");
		}
	}

	public static void append(boolean grant, String type, String privilege, String object, String column, String schema,
			String grantee, String grantor, boolean grantable, com.sqlapp.data.schemas.PrivilegeState state,
			Map<String, String> specifics, FirebirdSqlBuilder builder) {
		identifier(object, "privilege target name");
		identifier(privilege, "privilege");
		if (schema != null && !schema.isBlank()) {
			throw new IllegalArgumentException("Firebird privilege targets do not have schemas");
		}
		if (state != null && state != (grant ? com.sqlapp.data.schemas.PrivilegeState.Grant
				: com.sqlapp.data.schemas.PrivilegeState.Revoke)) {
			throw new IllegalArgumentException(
					"Firebird privilege state conflicts with the requested operation: " + state);
		}
		target(type, privilege, builder);
		builder._add(grant ? "GRANT " : "REVOKE ");
		revokeOption(grant, false, specifics, builder);
		builder._add(privilege);
		if (column != null) {
			identifier(column, "column name");
			if (!"TABLE".equals(type) || !Set.of("UPDATE", "REFERENCES").contains(privilege)) {
				throw new IllegalArgumentException(
						"Firebird column privileges support UPDATE or REFERENCES on a table/view");
			}
			builder._add(" (").name(column)._add(")");
		}
		builder._add(" ON ")._add(type).space().name(object)._add(grant ? " TO " : " FROM ");
		grantee(grantee, specifics, builder);
		if (grant && grantable)
			builder._add(" WITH GRANT OPTION");
		grantor(grantor, builder);
	}
}
