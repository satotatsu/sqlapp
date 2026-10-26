/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import java.util.*;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.schemas.Package;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.util.JsonConverter;

/** Module-scoped members that cannot live in top-level Schema collections. */
public final class Db2ModuleMembers {
	public static final String SPECIFICS_KEY = "db2.moduleMembers.v1";

	private Db2ModuleMembers() {
	}

	public enum Kind {
		CONDITION, FUNCTION, PROCEDURE, TYPE, VARIABLE
	}

	/**
	 * Recognizes the catalog signature of a type's database-generated cast pair.
	 */
	public static Optional<String> generatedTypeOwner(Snapshot snapshot, Member member) {
		var fields = member.attributes();
		if (member.userDefined() || member.kind() != Kind.FUNCTION || !"S".equals(fields.get("ROUTINE_ORIGIN"))
				|| !"1".equals(fields.get("ROUTINE_PARAMETER_COUNT")) || fields.containsKey("ROUTINE_REMARKS"))
			return Optional.empty();
		if (!Set.of("ROUTINE_TEXT", "ROUTINE_LANGUAGE", "MODULEROUTINEIMPLEMENTED", "ROUTINE_QUALIFIER",
				"ROUTINE_FUNC_PATH", "MEMBER_CREATE_TIME", "ROUTINE_ORIGIN", "ROUTINE_RETURN_SCHEMA",
				"ROUTINE_RETURN_MODULE", "ROUTINE_RETURN_NAME", "ROUTINE_PARAMETER_COUNT", "ROUTINE_PARAMETER_SCHEMA",
				"ROUTINE_PARAMETER_MODULE", "ROUTINE_PARAMETER_NAME").containsAll(fields.keySet()))
			return Optional.empty();
		for (var type : snapshot.members()) {
			if (type.kind() != Kind.TYPE || !type.userDefined() || !type.published().equals(member.published()))
				continue;
			String builtin = switch (type.attributes().getOrDefault("TYPE_METATYPE", "")) {
			case "F" -> "ROW";
			case "A", "L" -> "ARRAY";
			case "C" -> "CURSOR";
			case "T" -> "SYSIBM".equals(type.attributes().get("SOURCESCHEMA"))
					&& !type.attributes().containsKey("SOURCEMODULENAME") ? type.attributes().get("SOURCENAME") : null;
			default -> null;
			};
			if (builtin == null)
				continue;
			boolean intoType = member.name().equals(type.name())
					&& matches(fields, "ROUTINE_RETURN_", snapshot.schemaName(), snapshot.moduleName(), type.name())
					&& matches(fields, "ROUTINE_PARAMETER_", "SYSIBM", null, builtin);
			boolean fromType = member.name().equals(builtin)
					&& matches(fields, "ROUTINE_PARAMETER_", snapshot.schemaName(), snapshot.moduleName(), type.name())
					&& matches(fields, "ROUTINE_RETURN_", "SYSIBM", null, builtin);
			if (intoType || fromType)
				return Optional.of(type.name());
		}
		return Optional.empty();
	}

	private static boolean matches(Map<String, String> fields, String prefix, String schema, String module,
			String name) {
		return Objects.equals(schema, fields.get(prefix + "SCHEMA"))
				&& Objects.equals(module, fields.get(prefix + "MODULE"))
				&& Objects.equals(name, fields.get(prefix + "NAME"));
	}

	/** Retains the canonical Schema row type inside a module-scoped member. */
	public static Member withRowType(Member member, Type type) {
		if (member.kind() != Kind.TYPE || !"F".equals(member.attributes().get("TYPE_METATYPE"))
				|| !member.name().equals(type.getName()) || type.getMetaType() != Type.MetaType.Row)
			throw new IllegalArgumentException("Db2 row model must match its module TYPE member");
		var attributes = new TreeMap<>(member.attributes());
		try {
			attributes.put("TYPE_ROW_MODEL", type.asXml());
		} catch (javax.xml.stream.XMLStreamException exception) {
			throw new IllegalArgumentException("Cannot retain Db2 module row type: " + member.name(), exception);
		}
		return new Member(member.kind(), member.name(), member.specificName(), member.published(), member.userDefined(),
				attributes);
	}

	/**
	 * Returns an independent model; absent or mismatched row models fail
	 * explicitly.
	 */
	public static Type getRowType(Member member) {
		if (member.kind() != Kind.TYPE || !"F".equals(member.attributes().get("TYPE_METATYPE"))
				|| !member.attributes().containsKey("TYPE_ROW_MODEL"))
			throw new IllegalArgumentException("Db2 row member requires a Schema Type model");
		try {
			Object value = SchemaUtils.readXml(new java.io.StringReader(member.attributes().get("TYPE_ROW_MODEL")));
			if (!(value instanceof Type type) || !member.name().equals(type.getName())
					|| type.getMetaType() != Type.MetaType.Row)
				throw new IllegalArgumentException("Db2 row model does not match its module TYPE member");
			return type;
		} catch (Exception exception) {
			throw new IllegalArgumentException("Invalid Db2 module row type model: " + member.name(), exception);
		}
	}

	public record Member(Kind kind, String name, String specificName, Boolean published, Boolean userDefined,
			Map<String, String> attributes) {
		public Member {
			if (kind == null || published == null || userDefined == null || attributes == null)
				throw new IllegalArgumentException(
						"Db2 module member requires kind, visibility, origin and attributes");
			Db2CreationContext.identifier(name);
			if (kind == Kind.FUNCTION || kind == Kind.PROCEDURE)
				Db2CreationContext.identifier(specificName);
			else if (specificName != null)
				throw new IllegalArgumentException("Only Db2 routine members have specific names");
			var retained = new TreeMap<String, String>();
			attributes.forEach((key, value) -> {
				if (key == null || key.isBlank() || value == null || value.indexOf('\0') >= 0)
					throw new IllegalArgumentException("Invalid Db2 module member attribute");
				retained.put(key, value);
			});
			attributes = Collections.unmodifiableMap(retained);
		}
	}

	public record Snapshot(int format, String schemaName, String moduleName, List<Member> members, Boolean orderKnown) {
		public Snapshot {
			if (format != 1 || members == null || orderKnown == null)
				throw new IllegalArgumentException("Invalid Db2 module-member snapshot");
			Db2CreationContext.qualified(schemaName, moduleName);
			members = List.copyOf(members);
			var names = new HashSet<List<String>>();
			for (var member : members) {
				String identity = member.specificName() == null ? member.name() : member.specificName();
				if (!names.add(List.of(member.kind().name(), identity)))
					throw new IllegalArgumentException("Duplicate Db2 module member: " + identity);
			}
		}
	}

	public static Snapshot get(Package module) {
		String json = module.getSpecifics().get(SPECIFICS_KEY);
		if (json == null)
			return new Snapshot(1, module.getSchemaName(), module.getName(), List.of(), true);
		Snapshot snapshot = new JsonConverter().getObjectMapper().readerFor(Snapshot.class)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
		if (snapshot == null || !Objects.equals(snapshot.schemaName(), module.getSchemaName())
				|| !Objects.equals(snapshot.moduleName(), module.getName()))
			throw new IllegalArgumentException("Db2 module-member snapshot belongs to another module");
		return snapshot;
	}

	public static void set(Package module, List<Member> members) {
		set(module, members, true);
	}

	public static void set(Package module, List<Member> members, boolean orderKnown) {
		String objectType = module.getSpecifics().get("DB2_OBJECT_TYPE"),
				moduleType = module.getSpecifics().get("MODULETYPE");
		if (objectType != null && !"MODULE".equals(objectType)
				|| moduleType != null && !Set.of("M", "P").contains(moduleType))
			throw new IllegalArgumentException(
					"Db2 members belong to SQL modules or PL/SQL packages, not aliases or other objects");
		if (moduleType == null && "PL/SQL".equals(module.getSpecifics().get("DIALECT")))
			throw new IllegalArgumentException("Db2 PL/SQL member snapshots require explicit MODULETYPE P");
		var snapshot = new Snapshot(1, module.getSchemaName(), module.getName(), members, orderKnown);
		module.getSpecifics().put(SPECIFICS_KEY, new JsonConverter().toJsonString(snapshot));
		module.getSpecifics().put("DB2_OBJECT_TYPE", "MODULE");
		if (moduleType == null)
			module.getSpecifics().put("MODULETYPE", "M");
		module.getSpecifics().put("MEMBER_COUNT", Integer.toString(members.size()));
	}
}
