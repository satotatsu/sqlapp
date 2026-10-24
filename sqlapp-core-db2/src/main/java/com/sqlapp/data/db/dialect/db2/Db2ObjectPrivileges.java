/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.util.JsonConverter;

/**
 * Typed Db2 object grants in the canonical Catalog, without table-name
 * collisions.
 */
public final class Db2ObjectPrivileges {
	public static final String SPECIFICS_KEY = "db2.objectPrivileges.v1";

	private Db2ObjectPrivileges() {
	}

	public enum Kind {
		SEQUENCE, INDEX, SERVER, TABLESPACE, PACKAGE, MODULE, VARIABLE, WORKLOAD, XSROBJECT
	}

	public enum GranteeType {
		USER, GROUP, ROLE, PUBLIC
	}

	public record Grant(Kind kind, String schemaName, String objectName, String privilege, String grantorName,
			GranteeType granteeType, String granteeName, Boolean grantable) {
		public Grant {
			if (kind == null || granteeType == null || grantable == null)
				throw new IllegalArgumentException("Db2 object grant requires kind, grantee type and grantability");
			if (Set.of(Kind.SERVER, Kind.TABLESPACE, Kind.WORKLOAD).contains(kind)) {
				if (schemaName != null)
					throw new IllegalArgumentException("Db2 " + kind + " objects have no schema");
				Db2CreationContext.identifier(objectName);
			} else
				Db2CreationContext.qualified(schemaName, objectName);
			Db2CreationContext.identifier(granteeName);
			if (grantorName != null)
				Db2CreationContext.identifier(grantorName);
			if (granteeType == GranteeType.PUBLIC && !"PUBLIC".equals(granteeName))
				throw new IllegalArgumentException("Db2 PUBLIC grant must name PUBLIC");
			Set<String> allowed = switch (kind) {
			case INDEX -> Set.of("CONTROL");
			case SEQUENCE -> Set.of("ALTER", "USAGE");
			case SERVER -> Set.of("PASSTHRU");
			case TABLESPACE -> Set.of("USE");
			case PACKAGE -> Set.of("CONTROL", "BIND", "EXECUTE");
			case MODULE -> Set.of("EXECUTE");
			case VARIABLE -> Set.of("READ", "WRITE");
			case WORKLOAD, XSROBJECT -> Set.of("USAGE");
			};
			if (!allowed.contains(privilege == null ? "" : privilege))
				throw new IllegalArgumentException("Unsupported Db2 " + kind + " privilege: " + privilege);
			if (grantable && (Set.of(Kind.INDEX, Kind.SERVER, Kind.WORKLOAD, Kind.XSROBJECT).contains(kind)
					|| "CONTROL".equals(privilege)))
				throw new IllegalArgumentException("Db2 " + kind + " privilege cannot have WITH GRANT OPTION");
		}

		private List<Object> effectiveId() {
			return java.util.Arrays.asList(kind, schemaName, objectName, privilege, granteeType, granteeName);
		}
	}

	public record Snapshot(int format, List<Grant> grants) {
		public Snapshot {
			if (format != 1 || grants == null)
				throw new IllegalArgumentException("Invalid Db2 object-privilege snapshot");
			grants = List.copyOf(grants);
			var ids = new HashSet<List<Object>>();
			for (Grant grant : grants) {
				var id = new ArrayList<>(grant.effectiveId());
				id.add(grant.grantorName());
				if (!ids.add(id))
					throw new IllegalArgumentException("Duplicate Db2 object grant: " + id);
			}
		}
	}

	public static Snapshot get(Catalog catalog) {
		String text = catalog.getSpecifics().get(SPECIFICS_KEY);
		if (text == null)
			return new Snapshot(1, List.of());
		Snapshot snapshot = new JsonConverter().getObjectMapper().readerFor(Snapshot.class)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(text);
		if (snapshot == null)
			throw new IllegalArgumentException("Invalid Db2 object-privilege snapshot");
		return snapshot;
	}

	public static void set(Catalog catalog, List<Grant> grants) {
		Snapshot snapshot = new Snapshot(1, grants);
		if (grants.isEmpty()) {
			var retained = new com.sqlapp.data.schemas.DbInfo();
			catalog.getSpecifics().forEach((key, value) -> {
				if (!SPECIFICS_KEY.equalsIgnoreCase(key))
					retained.put(key, value);
			});
			catalog.setSpecifics(retained);
		} else
			catalog.getSpecifics().put(SPECIFICS_KEY, new JsonConverter().toJsonString(snapshot));
	}

	/**
	 * Converts tagged direct-reader results without attaching them as table grants.
	 */
	public static Grant fromModel(ObjectPrivilege privilege) {
		String type = privilege.getSpecifics().get("GRANTEE_TYPE");
		GranteeType grantee = "PUBLIC".equals(privilege.getGranteeName()) && "G".equals(type) ? GranteeType.PUBLIC
				: switch (type == null ? "" : type) {
				case "U" -> GranteeType.USER;
				case "G" -> GranteeType.GROUP;
				case "R" -> GranteeType.ROLE;
				default -> throw new IllegalArgumentException("Invalid Db2 grantee type: " + type);
				};
		return new Grant(Kind.valueOf(privilege.getSpecifics().get("OBJECT_TYPE")), privilege.getSchemaName(),
				privilege.getObjectName(), privilege.getPrivilege(), privilege.getGrantorName(), grantee,
				privilege.getGranteeName(), privilege.isGrantable());
	}

	/**
	 * Replays effective grants; original grantor chains remain inspection metadata.
	 */
	public static List<String> createSql(Catalog catalog) {
		// Enforcing-index names can be regenerated by native constraint creation.
		// Do not silently grant CONTROL on the wrong physical object after replay.
		var generatedIndexes = new HashSet<List<String>>();
		for (var schema : catalog.getSchemas()) {
			for (var table : schema.getTables()) {
				for (var constraint : table.getConstraints().getUniqueConstraints()) {
					var index = constraint.getIndex();
					String owner = index.getSpecifics().get("INDEX_SCHEMA");
					if (owner == null)
						owner = table.getSchemaName();
					if (constraint.isEnable() && owner != null && index.getName() != null) {
						generatedIndexes.add(List.of(owner, index.getName()));
					}
				}
			}
		}
		Snapshot snapshot = get(catalog);
		for (Grant grant : snapshot.grants()) {
			if (grant.kind() == Kind.INDEX
					&& generatedIndexes.contains(List.of(grant.schemaName(), grant.objectName())))
				throw new IllegalArgumentException(
						"Db2 grant on constraint-backed index requires an explicit physical-index recreation plan: "
								+ grant.schemaName() + "." + grant.objectName());
		}
		return effective(snapshot).values().stream().map(Db2ObjectPrivileges::grantSql).toList();
	}

	public static String grantSql(Grant grant) {
		return "GRANT " + grant.privilege() + target(grant) + " TO " + grantee(grant)
				+ (grant.grantable() ? " WITH GRANT OPTION" : "");
	}

	/**
	 * Db2 revokes all grantors; sequence RESTRICT prevents dependent-object
	 * invalidation.
	 */
	public static String revokeSql(Grant grant) {
		return "REVOKE " + grant.privilege() + target(grant) + " FROM " + grantee(grant)
				+ (Set.of(Kind.SEQUENCE, Kind.VARIABLE).contains(grant.kind()) ? " RESTRICT" : "");
	}

	private static String target(Grant grant) {
		return (grant.kind() == Kind.TABLESPACE ? " OF " : " ON ") + grant.kind() + " "
				+ (Set.of(Kind.SERVER, Kind.TABLESPACE, Kind.WORKLOAD).contains(grant.kind())
						? Db2CreationContext.identifier(grant.objectName())
						: Db2CreationContext.qualified(grant.schemaName(), grant.objectName()));
	}

	/** New catalog scopes use a conservative LUW 11.1 coverage boundary. */
	public static void validateDialect(Grant grant, com.sqlapp.data.db.dialect.Dialect dialect) {
		if (!Set.of(Kind.SEQUENCE, Kind.INDEX, Kind.SERVER).contains(grant.kind()) && !(dialect instanceof Db2_1110))
			throw new IllegalArgumentException("Db2 " + grant.kind() + " privilege support requires LUW 11.1 or later");
	}

	public static void validateDialect(Catalog catalog, com.sqlapp.data.db.dialect.Dialect dialect) {
		get(catalog).grants().forEach(grant -> validateDialect(grant, dialect));
	}

	private static String grantee(Grant grant) {
		return grant.granteeType() == GranteeType.PUBLIC ? "PUBLIC"
				: grant.granteeType() + " " + Db2CreationContext.identifier(grant.granteeName());
	}

	private static Map<List<Object>, Grant> effective(Snapshot snapshot) {
		Map<List<Object>, Grant> result = new LinkedHashMap<>();
		for (Grant grant : snapshot.grants()) {
			Grant previous = result.get(grant.effectiveId());
			if (previous == null || grant.grantable())
				result.put(grant.effectiveId(), grant);
		}
		return result;
	}

	public record ChangePlan(List<String> beforeSql, List<String> afterSql) {
		public ChangePlan {
			beforeSql = List.copyOf(beforeSql);
			afterSql = List.copyOf(afterSql);
		}
	}

	/**
	 * Native removals precede object DDL; additions/upgrades follow object
	 * creation.
	 */
	public static ChangePlan diff(Catalog original, Catalog target) {
		var before = effective(get(original));
		var after = effective(get(target));
		List<String> revokes = new ArrayList<>(), grants = new ArrayList<>();
		before.forEach((id, previous) -> {
			Grant next = after.get(id);
			if (next == null)
				revokes.add(revokeSql(previous));
			else if (previous.grantable() && !next.grantable())
				throw new IllegalArgumentException(
						"Reducing Db2 object GRANT OPTION requires an explicit REVOKE/regrant plan: " + id);
		});
		after.forEach((id, next) -> {
			Grant previous = before.get(id);
			if (previous == null || (!previous.grantable() && next.grantable()))
				grants.add(grantSql(next));
		});
		return new ChangePlan(revokes, grants);
	}
}
