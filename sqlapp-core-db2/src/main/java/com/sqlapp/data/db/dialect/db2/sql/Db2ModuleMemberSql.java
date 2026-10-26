/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.db2.Db2ModuleMembers;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Constant;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Package;

/**
 * Conservative module reconstruction using the existing native variable
 * factory.
 */
final class Db2ModuleMemberSql {
	private Db2ModuleMemberSql() {
	}

	static List<SqlOperation> create(Package module, Db2CreateModuleFactory owner,
			java.util.function.BiFunction<String, SqlType, SqlOperation> operationBuilder) {
		if (!module.getSpecifics().containsKey(Db2ModuleMembers.SPECIFICS_KEY)) {
			if ("0".equals(module.getSpecifics().get("MEMBER_COUNT")))
				return List.of();
			throw unsupported(module);
		}
		var snapshot = Db2ModuleMembers.get(module);
		if (!snapshot.orderKnown()
				|| !Integer.toString(snapshot.members().size()).equals(module.getSpecifics().get("MEMBER_COUNT")))
			throw unsupported(module);
		boolean laterImplementation = false;
		for (var member : snapshot.members().reversed()) {
			if (member.userDefined() && member.published()
					&& "Y".equals(member.attributes().get("MODULEROUTINEIMPLEMENTED"))
					&& !Db2ModuleRoutineSql.canDeclareFirst(member))
				laterImplementation = true;
			if (laterImplementation && member.kind() == Db2ModuleMembers.Kind.VARIABLE
					&& member.attributes().containsKey("DEFAULT_EXPRESSION")
					&& !Db2LiteralDefault.isIndependent(member.attributes().get("DEFAULT_EXPRESSION")))
				throw new IllegalArgumentException(
						"Db2 module variable defaults preceding implemented published routines need a complete parent definition: catalog routine CREATE_TIME changes on implementation and cannot prove declaration dependencies: "
								+ module.getName());
		}
		List<SqlOperation> result = new ArrayList<>();
		List<SqlOperation> implementations = new ArrayList<>();
		Set<Db2ModuleMembers.Member> declaredFirst = new HashSet<>();
		for (var member : snapshot.members()) {
			if (!Db2ModuleRoutineSql.canDeclareFirst(member))
				continue;
			Db2ModuleRoutineSql.create(module, member, owner, operationBuilder); // Validate the original scope and SQL
																					// first.
			append(module, Db2ModuleRoutineSql.create(module, Db2ModuleRoutineSql.staged(member, true), owner,
					operationBuilder), result, operationBuilder);
			declaredFirst.add(member);
		}
		String name = Db2CreationContext.qualified(module.getSchemaName(), module.getName());
		Set<String> publishedTypes = new HashSet<>();
		Set<String> publishedRows = new HashSet<>();
		for (var member : snapshot.members()) {
			if (!member.userDefined()) {
				if (Db2ModuleMembers.generatedTypeOwner(snapshot, member).isPresent())
					continue;
				throw unsupported(module);
			}
			String prefix = "ALTER MODULE " + name + (member.published() ? " PUBLISH " : " ADD ");
			if (member.kind() == Db2ModuleMembers.Kind.CONDITION) {
				if (!Set.of("CONDITION_SQLSTATE", "CONDITION_REMARKS", "MEMBER_CREATE_TIME")
						.containsAll(member.attributes().keySet()))
					throw unsupported(module);
				if (member.attributes().containsKey("CONDITION_REMARKS"))
					throw unsupported(module);
				String sql = prefix + "CONDITION " + Db2CreationContext.identifier(member.name());
				String state = member.attributes().get("CONDITION_SQLSTATE");
				if (state != null) {
					if (!state.matches("[A-Z0-9]{5}") || state.startsWith("00"))
						throw unsupported(module);
					sql += " FOR SQLSTATE '" + state + "'";
				}
				result.add(operationBuilder.apply(sql, SqlType.ALTER));
			} else if (member.kind() == Db2ModuleMembers.Kind.TYPE) {
				if ("C".equals(member.attributes().get("TYPE_METATYPE"))) {
					var nativeFields = new HashMap<>(member.attributes());
					nativeFields.keySet().removeIf(key -> key.startsWith("TYPE_CURSOR_"));
					domain(module, new Db2ModuleMembers.Member(member.kind(), member.name(), null, member.published(),
							true, nativeFields));
					if (nativeFields.containsKey("TYPE_CONSTRAINT_TEXT") || "N".equals(nativeFields.get("TYPE_NULLS")))
						throw unsupported(module);
					var cursor = new com.sqlapp.data.schemas.Type(member.name()).setSchemaName(module.getSchemaName())
							.setMetaType(com.sqlapp.data.schemas.Type.MetaType.Cursor);
					member.attributes().forEach((key, value) -> {
						if (key.startsWith("TYPE_CURSOR_"))
							cursor.getSpecifics().put("DB2_" + key.substring(5), value);
					});
					Db2CreateTypeFactory factory = new Db2CreateTypeFactory() {
						@Override
						protected String createTypePrefix(com.sqlapp.data.schemas.Type object) {
							return prefix + "TYPE " + Db2CreationContext.identifier(object.getName());
						}

						@Override
						protected String renderCursorRowType(com.sqlapp.data.schemas.Type object) {
							var fields = object.getSpecifics();
							return localType(module, publishedRows, fields.get("DB2_CURSOR_ROW_SCHEMA"),
									fields.get("DB2_CURSOR_ROW_MODULE"), fields.get("DB2_CURSOR_ROW_NAME"));
						}
					};
					factory.setDialect(owner.getDialect());
					factory.setSqlFactoryRegistry(owner.getSqlFactoryRegistry());
					append(module, factory.createSql(cursor), result, operationBuilder);
					if (member.published())
						publishedTypes.add(member.name());
					continue;
				}
				if ("F".equals(member.attributes().get("TYPE_METATYPE"))) {
					var type = Db2ModuleMembers.getRowType(member);
					var fields = new HashMap<>(member.attributes());
					fields.remove("TYPE_ROW_MODEL");
					// Reuse the strict catalog-attribute whitelist, including comment/collation
					// checks.
					domain(module, new Db2ModuleMembers.Member(member.kind(), member.name(), null, member.published(),
							true, fields));
					if (!module.getSchemaName().equals(type.getSchemaName()) || !type.getDefinition().isEmpty()
							|| !type.getStatement().isEmpty() || type.getRemarks() != null
							|| fields.containsKey("TYPE_CONSTRAINT_TEXT") || fields.containsKey("SOURCEMODULENAME")
							|| "N".equals(fields.get("TYPE_NULLS")))
						throw unsupported(module);
					Db2CreateTypeFactory factory = new Db2CreateTypeFactory() {
						@Override
						protected String createTypePrefix(com.sqlapp.data.schemas.Type object) {
							return prefix + "TYPE " + Db2CreationContext.identifier(object.getName());
						}

						@Override
						protected String renderFieldType(com.sqlapp.data.schemas.Type object,
								com.sqlapp.data.schemas.TypeColumn field) {
							var attributes = field.getSpecifics();
							if (attributes.get("DB2_TYPE_MODULE") == null) {
								if (attributes.get("DB2_TYPE_SCHEMA") != null
										&& !"SYSIBM".equals(attributes.get("DB2_TYPE_SCHEMA")))
									throw unsupported(module);
								return super.renderFieldType(object, field);
							}
							return localType(module, publishedTypes, attributes.get("DB2_TYPE_SCHEMA"),
									attributes.get("DB2_TYPE_MODULE"), attributes.get("TYPENAME"));
						}
					};
					factory.setDialect(owner.getDialect());
					factory.setSqlFactoryRegistry(owner.getSqlFactoryRegistry());
					append(module, factory.createSql(type), result, operationBuilder);
					if (member.published()) {
						publishedTypes.add(member.name());
						publishedRows.add(member.name());
					}
					continue;
				}
				Domain type = domain(module, member);
				Db2CreateDomainFactory factory = new Db2CreateDomainFactory() {
					@Override
					protected String createTypePrefix(Domain object) {
						return prefix + "TYPE " + Db2CreationContext.identifier(object.getName());
					}

					@Override
					protected String renderSourceType(Domain object) {
						var fields = object.getSpecifics();
						if (fields.get("SOURCEMODULENAME") == null) {
							if (fields.get("SOURCESCHEMA") != null && !"SYSIBM".equals(fields.get("SOURCESCHEMA")))
								throw unsupported(module);
							return super.renderSourceType(object);
						}
						if (!Set.of("A", "L").contains(fields.getOrDefault("METATYPE", "")))
							throw unsupported(module);
						return localType(module, publishedTypes, fields.get("SOURCESCHEMA"),
								fields.get("SOURCEMODULENAME"), fields.get("SOURCENAME"));
					}
				};
				factory.setDialect(owner.getDialect());
				factory.setSqlFactoryRegistry(owner.getSqlFactoryRegistry());
				append(module, factory.createSql(type), result, operationBuilder);
				if (member.published())
					publishedTypes.add(member.name());
			} else if (member.kind() == Db2ModuleMembers.Kind.VARIABLE) {
				if (!Set.of("TYPESCHEMA", "TYPENAME", "TYPEMODULENAME", "LENGTH", "SCALE", "CODEPAGE",
						"TYPESTRINGUNITS", "STRINGUNITSLENGTH", "SCOPE", "READONLY", "DEFAULT_EXPRESSION", "QUALIFIER",
						"FUNC_PATH", "COLLATIONSCHEMA", "COLLATIONNAME", "VARIABLE_REMARKS", "MEMBER_CREATE_TIME")
						.containsAll(member.attributes().keySet()))
					throw unsupported(module);
				if (member.attributes().containsKey("VARIABLE_REMARKS"))
					throw unsupported(module);
				String collation = member.attributes().get("COLLATIONSCHEMA");
				if (collation != null && !"SYSIBM".equals(collation))
					throw unsupported(module);
				Constant variable = new Constant(member.name()).setSchemaName(module.getSchemaName());
				variable.getSpecifics().putAll(member.attributes());
				variable.getSpecifics().put("DB2_OBJECT_TYPE", "VARIABLE");
				variable.setStatement(member.attributes().get("DEFAULT_EXPRESSION"));
				Db2CreateGlobalVariableFactory factory = new Db2CreateGlobalVariableFactory() {
					@Override
					protected String createVariablePrefix(Constant object) {
						return prefix + "VARIABLE " + Db2CreationContext.identifier(object.getName());
					}

					@Override
					protected String renderVariableType(Constant object) {
						var fields = object.getSpecifics();
						if (fields.get("TYPEMODULENAME") == null)
							return super.renderVariableType(object);
						return localType(module, publishedTypes, fields.get("TYPESCHEMA"), fields.get("TYPEMODULENAME"),
								fields.get("TYPENAME"));
					}
				};
				factory.setDialect(owner.getDialect());
				factory.setSqlFactoryRegistry(owner.getSqlFactoryRegistry());
				append(module, factory.createSql(variable), result, operationBuilder);
			} else if (member.kind() == Db2ModuleMembers.Kind.FUNCTION
					|| member.kind() == Db2ModuleMembers.Kind.PROCEDURE) {
				var original = Db2ModuleRoutineSql.create(module, member, owner, operationBuilder);
				if ("Y".equals(member.attributes().get("MODULEROUTINEIMPLEMENTED"))) {
					if (member.published()) {
						if (!declaredFirst.contains(member))
							append(module, Db2ModuleRoutineSql.create(module, Db2ModuleRoutineSql.staged(member, true),
									owner, operationBuilder), result, operationBuilder);
						append(module, Db2ModuleRoutineSql.create(module, Db2ModuleRoutineSql.staged(member, false),
								owner, operationBuilder), implementations, operationBuilder);
					} else
						append(module, original, implementations, operationBuilder);
				} else
					append(module, original, result, operationBuilder);
			} else
				throw unsupported(module);
		}
		result.addAll(implementations);
		return result;
	}

	private static String localType(Package module, Set<String> publishedTypes, String schema, String owner,
			String name) {
		if (!module.getSchemaName().equals(schema) || !module.getName().equals(owner) || !publishedTypes.contains(name))
			throw unsupported(module);
		return Db2CreationContext.identifier(name);
	}

	private static void append(Package module, List<SqlOperation> generated, List<SqlOperation> result,
			java.util.function.BiFunction<String, SqlType, SqlOperation> operationBuilder) {
		if (generated.stream().noneMatch(operation -> operation.getSqlType() == SqlType.CREATE))
			throw unsupported(module);
		for (var operation : generated)
			result.add(operationBuilder.apply(operation.getSqlText(),
					operation.getSqlType() == SqlType.CREATE ? SqlType.ALTER : operation.getSqlType()));
	}

	private static Domain domain(Package module, Db2ModuleMembers.Member member) {
		var fields = member.attributes();
		Set<String> allowed = Set.of("TYPE_METATYPE", "SOURCESCHEMA", "SOURCENAME", "SOURCEMODULENAME",
				"TYPE_TYPERULES", "TYPE_NULLS", "TYPE_LENGTH", "TYPE_SCALE", "TYPE_CODEPAGE", "TYPE_TYPESTRINGUNITS",
				"TYPE_STRINGUNITSLENGTH", "TYPE_ARRAY_LENGTH", "TYPE_ARRAYINDEXTYPESCHEMA", "TYPE_ARRAYINDEXTYPENAME",
				"TYPE_ARRAYINDEXTYPELENGTH", "TYPE_ARRAYINDEXTYPE_STRINGUNITS", "TYPE_ARRAYINDEXTYPE_STRINGUNITSLENGTH",
				"TYPE_FUNC_PATH", "TYPE_CONSTRAINT_TEXT", "TYPE_REMARKS", "TYPE_COLLATIONSCHEMA", "TYPE_COLLATIONNAME",
				"MEMBER_CREATE_TIME");
		if (!allowed.containsAll(fields.keySet()) || fields.containsKey("TYPE_REMARKS")
				|| (fields.containsKey("TYPE_COLLATIONSCHEMA") && !"SYSIBM".equals(fields.get("TYPE_COLLATIONSCHEMA")))
				|| !Set.of("Y", "N").contains(fields.getOrDefault("TYPE_NULLS", "")))
			throw unsupported(module);
		Domain type = new Domain(member.name()).setSchemaName(module.getSchemaName());
		type.getSpecifics().put("DB2_OBJECT_TYPE", "DATA_TYPE");
		fields.forEach(
				(key, value) -> type.getSpecifics().put(key.startsWith("TYPE_") ? key.substring(5) : key, value));
		try {
			if (fields.containsKey("TYPE_LENGTH"))
				type.setLength(Long.valueOf(fields.get("TYPE_LENGTH")));
			if (fields.containsKey("TYPE_SCALE"))
				type.setScale(Integer.valueOf(fields.get("TYPE_SCALE")));
			String kind = fields.get("TYPE_METATYPE");
			if ("A".equals(kind) || "L".equals(kind)) {
				type.setArrayDimension(1);
				if ("A".equals(kind))
					type.setArrayDimensionUpperBound(Integer.parseInt(fields.get("TYPE_ARRAY_LENGTH")));
			}
		} catch (RuntimeException e) {
			throw unsupported(module);
		}
		type.setNotNull("N".equals(fields.get("TYPE_NULLS")));
		type.setCheck(fields.get("TYPE_CONSTRAINT_TEXT"));
		return type;
	}

	private static IllegalArgumentException unsupported(Package module) {
		return new IllegalArgumentException(
				"Db2 module members require complete native CREATE/ALTER definition for unsupported members, comments, scope, type or ambiguous order: "
						+ module.getName());
	}
}
