/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.sqlapp.data.db.dialect.db2.Db2ModuleMembers;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2_1110;
import com.sqlapp.data.db.metadata.PackageReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Package;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * SQL modules and PL/SQL packages; aliases belong to the existing
 * SynonymReader.
 */
public class Db2ModuleReader extends PackageReader {
	public Db2ModuleReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Package> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		if (!(getDialect() instanceof Db2_1110))
			throw new IllegalArgumentException("Db2 module reader requires LUW 11.1 or later");
		List<Package> result = new ArrayList<>();
		Map<List<String>, Package> modules = new LinkedHashMap<>();
		Map<List<String>, List<Db2ModuleMembers.Member>> members = new LinkedHashMap<>();
		execute(connection, getSqlNodeCache().getString("modules.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Package module = new Package(getString(rs, "MODULENAME"));
				module.setSchemaName(getString(rs, "MODULESCHEMA"));
				module.setDialect(getDialect());
				module.setRemarks(rs.getString("REMARKS"));
				module.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
				module.getSpecifics().put("DB2_OBJECT_TYPE", "MODULE");
				for (String key : List.of("MODULETYPE", "DIALECT", "OWNER", "BASE_MODULESCHEMA", "BASE_MODULENAME",
						"MEMBER_COUNT")) {
					String value = rs.getString(key);
					if (value != null)
						module.getSpecifics().put(key, value);
				}
				List<String> identity = List.of(module.getSchemaName(), module.getName());
				if (modules.putIfAbsent(identity, module) != null)
					throw new SQLException("Duplicate Db2 module: " + identity);
				members.put(identity, new ArrayList<>());
				result.add(module);
			}
		});
		if (result.isEmpty())
			return result;
		execute(connection, getSqlNodeCache().getString("moduleMembers.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				List<String> identity = List.of(getString(rs, "OBJECTSCHEMA"), getString(rs, "OBJECTMODULENAME"));
				if (!members.containsKey(identity))
					throw new SQLException("Db2 module set changed while reading members: " + identity);
				Map<String, String> attributes = new LinkedHashMap<>();
				for (String key : List.of("CONDITION_SQLSTATE", "CONDITION_REMARKS", "TYPESCHEMA", "TYPENAME",
						"TYPEMODULENAME", "LENGTH", "SCALE", "CODEPAGE", "TYPESTRINGUNITS", "STRINGUNITSLENGTH",
						"SCOPE", "READONLY", "DEFAULT_EXPRESSION", "QUALIFIER", "FUNC_PATH", "COLLATIONSCHEMA",
						"COLLATIONNAME", "VARIABLE_REMARKS", "ROUTINE_TEXT", "ROUTINE_LANGUAGE",
						"MODULEROUTINEIMPLEMENTED", "ROUTINE_QUALIFIER", "ROUTINE_FUNC_PATH", "ROUTINE_REMARKS",
						"TYPE_METATYPE", "SOURCESCHEMA", "SOURCENAME", "SOURCEMODULENAME", "ROUTINE_ORIGIN",
						"ROUTINE_RETURN_SCHEMA", "ROUTINE_RETURN_MODULE", "ROUTINE_RETURN_NAME",
						"ROUTINE_PARAMETER_COUNT", "ROUTINE_PARAMETER_SCHEMA", "ROUTINE_PARAMETER_MODULE",
						"ROUTINE_PARAMETER_NAME", "ROUTINE_DECLARATION_INDEPENDENT", "TYPE_CURSOR_KIND",
						"TYPE_CURSOR_ROW_SCHEMA", "TYPE_CURSOR_ROW_MODULE", "TYPE_CURSOR_ROW_NAME", "TYPE_TYPERULES",
						"TYPE_NULLS", "TYPE_LENGTH", "TYPE_SCALE", "TYPE_CODEPAGE", "TYPE_TYPESTRINGUNITS",
						"TYPE_STRINGUNITSLENGTH", "TYPE_ARRAY_LENGTH", "TYPE_ARRAYINDEXTYPESCHEMA",
						"TYPE_ARRAYINDEXTYPENAME", "TYPE_ARRAYINDEXTYPELENGTH", "TYPE_ARRAYINDEXTYPE_STRINGUNITS",
						"TYPE_ARRAYINDEXTYPE_STRINGUNITSLENGTH", "TYPE_FUNC_PATH", "TYPE_CONSTRAINT_TEXT",
						"TYPE_REMARKS", "TYPE_COLLATIONSCHEMA", "TYPE_COLLATIONNAME")) {
					String value = rs.getString(key);
					if (value != null
							&& (!key.startsWith("TYPE_CURSOR_") || "C".equals(getString(rs, "TYPE_METATYPE"))))
						attributes.put(key,
								java.util.Set.of("ROUTINE_TEXT", "DEFAULT_EXPRESSION", "TYPE_CONSTRAINT_TEXT",
										"CONDITION_REMARKS", "VARIABLE_REMARKS", "ROUTINE_REMARKS", "TYPE_REMARKS")
										.contains(key) ? value : value.stripTrailing());
				}
				var created = rs.getTimestamp("MEMBER_CREATE_TIME");
				if (created != null)
					attributes.put("MEMBER_CREATE_TIME", created.toString());
				try {
					members.get(identity)
							.add(new Db2ModuleMembers.Member(Db2ModuleMembers.Kind.valueOf(getString(rs, "OBJECTTYPE")),
									getString(rs, "OBJECTNAME"), getString(rs, "SPECIFICNAME"), flag(rs, "PUBLISHED"),
									flag(rs, "USERDEFINED"), attributes));
				} catch (IllegalArgumentException exception) {
					throw new SQLException("Invalid Db2 module member: " + identity, exception);
				}
			}
		});
		readRowTypes(connection, context, members);
		for (var entry : modules.entrySet()) {
			Package module = entry.getValue();
			List<Db2ModuleMembers.Member> list = members.get(entry.getKey());
			int expected;
			try {
				expected = Integer.parseInt(module.getSpecifics().get("MEMBER_COUNT"));
			} catch (NumberFormatException exception) {
				throw new IllegalStateException("Db2 module requires a valid member count: " + module.getName(),
						exception);
			}
			if (expected != list.size())
				throw new IllegalStateException(
						"Db2 module member count changed or members are unavailable: " + module.getName());
			boolean orderKnown = true;
			var snapshot = new Db2ModuleMembers.Snapshot(1, module.getSchemaName(), module.getName(), list, true);
			var explicitMembers = list.stream()
					.filter(member -> Db2ModuleMembers.generatedTypeOwner(snapshot, member).isEmpty()).toList();
			boolean hasDependencies = explicitMembers.stream().anyMatch(Db2ModuleReader::hasDependencies);
			if (hasDependencies && list.size() > 1) {
				var times = new java.util.HashSet<String>();
				for (var member : explicitMembers) {
					String time = member.attributes().get("MEMBER_CREATE_TIME");
					if (time == null || !times.add(time))
						orderKnown = false;
				}
			}
			Db2ModuleMembers.set(module, list, orderKnown);
		}
		return result;
	}

	private static boolean hasDependencies(Db2ModuleMembers.Member member) {
		var fields = member.attributes();
		if (member.kind() == Db2ModuleMembers.Kind.FUNCTION || member.kind() == Db2ModuleMembers.Kind.PROCEDURE)
			return true;
		if (member.kind() == Db2ModuleMembers.Kind.VARIABLE)
			return fields.containsKey("DEFAULT_EXPRESSION") || fields.containsKey("TYPEMODULENAME");
		if (member.kind() != Db2ModuleMembers.Kind.TYPE)
			return false;
		if (fields.containsKey("SOURCEMODULENAME") || fields.containsKey("TYPE_CURSOR_ROW_MODULE"))
			return true;
		return "F".equals(fields.get("TYPE_METATYPE")) && Db2ModuleMembers.getRowType(member).getColumns().stream()
				.anyMatch(field -> field.getSpecifics().containsKey("DB2_TYPE_MODULE"));
	}

	private void readRowTypes(Connection connection, ParametersContext context,
			Map<List<String>, List<Db2ModuleMembers.Member>> members) {
		Map<List<String>, com.sqlapp.data.schemas.Type> types = new LinkedHashMap<>();
		for (var entry : members.entrySet())
			for (var member : entry.getValue())
				if (member.kind() == Db2ModuleMembers.Kind.TYPE
						&& "F".equals(member.attributes().get("TYPE_METATYPE"))) {
					var type = new com.sqlapp.data.schemas.Type(member.name()).setSchemaName(entry.getKey().getFirst());
					type.setMetaType(com.sqlapp.data.schemas.Type.MetaType.Row);
					type.getSpecifics().put("METATYPE", "F");
					types.put(List.of(entry.getKey().getFirst(), entry.getKey().getLast(), member.name()), type);
				}
		if (types.isEmpty())
			return;
		var fieldReader = new Db2TypeColumnReader(getDialect());
		execute(connection, getSqlNodeCache().getString("moduleRowFields.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				List<String> identity = List.of(getString(rs, "schema_name"), getString(rs, "module_name"),
						getString(rs, "type_name"));
				var type = types.get(identity);
				if (type == null)
					throw new SQLException("Db2 module row type set changed while reading fields: " + identity);
				String ordinal = rs.getString("ORDINAL");
				if (!Integer.toString(type.getColumns().size()).equals(ordinal))
					throw new SQLException(
							"Db2 module row fields require consecutive zero-based ordinals: " + identity);
				var field = fieldReader.createTypeColumn(rs);
				if (type.getColumns().get(field.getName()) != null)
					throw new SQLException("Duplicate Db2 module row field: " + field.getName());
				type.getColumns().add(field);
			}
		});
		for (var entry : members.entrySet()) {
			var list = entry.getValue();
			for (int index = 0; index < list.size(); index++) {
				var member = list.get(index);
				var type = types.get(List.of(entry.getKey().getFirst(), entry.getKey().getLast(), member.name()));
				if (member.kind() != Db2ModuleMembers.Kind.TYPE || type == null)
					continue;
				if (type.getColumns().isEmpty())
					throw new IllegalStateException("Db2 module row fields unavailable: " + member.name());
				list.set(index, Db2ModuleMembers.withRowType(member, type));
			}
		}
	}

	private static boolean flag(ExResultSet rs, String name) throws SQLException {
		String value = rs.getString(name);
		if (value == null || !java.util.Set.of("Y", "N").contains(value.stripTrailing()))
			throw new SQLException("Invalid Db2 module member flag: " + name);
		return "Y".equals(value.stripTrailing());
	}

}
