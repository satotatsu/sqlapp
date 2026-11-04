/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.metadata.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.schemas.properties.DataTypeLengthProperties;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** Set-based host routines, triggers and explicit table/column grants. */
final class Db2HostProgrammableReaders {
	private Db2HostProgrammableReaders() {
	}

	static class Procedures extends ProcedureReader {
		private final Class<?> resources;

		Procedures(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected RoutineArgumentReader<?> newRoutineArgumentReader() {
			return null;
		}

		@Override
		protected List<Procedure> doGetAll(Connection connection, ParametersContext context,
				ProductVersionInfo version) {
			Map<List<String>, Procedure> result = new LinkedHashMap<>();
			context = context.clone();
			context.put("routineKind", "P");
			execute(connection, getSqlNodeCache(resources).getString("routines.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							var key = List.of(id(rs, "SCHEMA_NAME"), id(rs, "SPECIFIC_NAME"));
							Procedure routine = result.get(key);
							if (routine == null) {
								routine = new Procedure(id(rs, "ROUTINE_NAME"));
								readRoutine(routine, rs, "PROCEDURE");
								result.put(key, routine);
							}
							readArgument(routine, rs, getDialect());
						}
					});
			return new ArrayList<>(result.values());
		}
	}

	static class Functions extends FunctionReader {
		private final Class<?> resources;

		Functions(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected RoutineArgumentReader<?> newRoutineArgumentReader() {
			return null;
		}

		@Override
		protected List<Function> doGetAll(Connection connection, ParametersContext context,
				ProductVersionInfo version) {
			Map<List<String>, Function> result = new LinkedHashMap<>();
			context = context.clone();
			context.put("routineKind", "F");
			execute(connection, getSqlNodeCache(resources).getString("routines.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							var key = List.of(id(rs, "SCHEMA_NAME"), id(rs, "SPECIFIC_NAME"));
							Function routine = result.get(key);
							if (routine == null) {
								routine = new Function(id(rs, "ROUTINE_NAME"));
								readRoutine(routine, rs, "FUNCTION");
								String form = id(rs, "FUNCTION_FORM");
								routine.setFunctionType("T".equals(form) ? FunctionType.Table
										: "C".equals(form) ? FunctionType.Aggregate : FunctionType.Scalar);
								if (form != null)
									routine.getSpecifics().put("DB2_HOST_FUNCTION_FORM", form);
								result.put(key, routine);
							}
							readArgument(routine, rs, getDialect());
						}
					});
			return new ArrayList<>(result.values());
		}
	}

	private static void readRoutine(ArgumentRoutine<?> routine, ExResultSet rs, String kind) throws SQLException {
		routine.setSchemaName(id(rs, "SCHEMA_NAME"));
		routine.setSpecificName(id(rs, "SPECIFIC_NAME"));
		routine.setLanguage(id(rs, "LANGUAGE_NAME"));
		routine.setRemarks(rs.getString("REMARKS"));
		String text = rs.getString("ROUTINE_TEXT"), textKind = id(rs, "TEXT_KIND");
		if ("D".equals(textKind)) {
			requireCreate(text, kind);
			routine.setDefinition(text);
		} else {
			if ("S".equals(textKind))
				routine.setStatement(required(text, "SQL routine body"));
			routine.getSpecifics().put("DB2_HOST_REQUIRES_DEFINITION",
					"Complete CREATE required for execution options/external implementation");
		}
		for (String field : List.of("EXTERNAL_NAME", "PARAMETER_STYLE", "SQL_DATA_ACCESS", "DETERMINISTIC",
				"NATIVE_VERSION")) {
			String value = rs.getString(field);
			if (value != null)
				routine.getSpecifics().put("DB2_HOST_" + field, value);
		}
		routine.getSpecifics().put("QUALIFIER", routine.getSchemaName());
		String path = rs.getString("SQL_PATH");
		if (path != null && !path.isBlank())
			routine.getSpecifics().put("FUNC_PATH", path);
	}

	private static void readArgument(ArgumentRoutine<?> routine, ExResultSet rs, Dialect dialect) throws SQLException {
		Integer position = rs.getInteger("PARAM_POSITION");
		if (position == null)
			return;
		String rowType = id(rs, "PARAM_ROW_TYPE"), name = id(rs, "PARAM_NAME");
		if ("P".equals(rowType)) {
			if (position != routine.getArguments().size() + 1)
				throw new SQLException("Incomplete/unordered routine arguments: " + routine.getSpecificName());
			var argument = new NamedArgument(name);
			argument.setDirection(switch (id(rs, "PARAM_MODE")) {
			case "IN" -> ParameterDirection.Input;
			case "OUT" -> ParameterDirection.Output;
			case "INOUT" -> ParameterDirection.Inout;
			default -> throw new SQLException("Unknown parameter direction");
			});
			readType(argument, rs, dialect);
			routine.getArguments().add(argument);
		} else if (("R".equals(rowType) || "C".equals(rowType)) && routine instanceof Function function) {
			if (function.getFunctionType() == FunctionType.Table) {
				var column = new Column(name);
				readType(column, rs, dialect);
				function.getReturning().toTable().getTable().getColumns().add(column);
			} else
				readType(function.getReturning(), rs, dialect);
		} else
			throw new SQLException("Unknown routine parameter row type: " + rowType);
	}

	private static void readType(DataTypeLengthProperties<?> value, ExResultSet rs, Dialect dialect)
			throws SQLException {
		String type = id(rs, "PARAM_TYPE");
		if (type == null)
			throw new SQLException("Missing parameter type");
		Long length = rs.getLongValue("PARAM_LENGTH");
		Integer scale = rs.getInteger("PARAM_SCALE");
		String schema = id(rs, "PARAM_TYPE_SCHEMA"), typeName = id(rs, "PARAM_TYPE_NAME");
		if (schema != null && typeName != null && ("DISTINCT".equals(type) || "ARRAY".equals(type))) {
			value.setDataType(DataType.OTHER);
			value.setDataTypeName(com.sqlapp.data.db.dialect.db2.util.Db2CreationContext.qualified(schema, typeName));
			value.setLength(length);
			value.setScale(scale);
			return;
		}
		if (!dialect.setDbType(type, length, scale, value)) {
			value.setDataType(DataType.OTHER);
			value.setDataTypeName(type);
			value.setLength(length);
			value.setScale(scale);
		}
	}

	static class Triggers extends TriggerReader {
		private final Class<?> resources;

		Triggers(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected List<Trigger> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
			List<Trigger> result = new ArrayList<>();
			execute(connection, getSqlNodeCache(resources).getString("triggers.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							var trigger = new Trigger(id(rs, "TRIGGER_NAME"));
							trigger.setSchemaName(id(rs, "SCHEMA_NAME"));
							trigger.setTableSchemaName(id(rs, "TABLE_SCHEMA"));
							trigger.setTableName(id(rs, "TABLE_NAME"));
							trigger.setActionTiming(id(rs, "ACTION_TIMING"));
							trigger.setActionOrientation(id(rs, "ACTION_ORIENTATION"));
							trigger.setActionReferenceOldRow(id(rs, "OLD_ROW"));
							trigger.setActionReferenceNewRow(id(rs, "NEW_ROW"));
							trigger.setActionCondition(rs.getString("WHEN_TEXT"));
							String events = id(rs, "EVENT_NAMES");
							if (events != null && !events.isBlank())
								trigger.setEventManipulation(events.split(","));
							trigger.setEnable(flag(rs, "ENABLED_FLAG"));
							trigger.setCreatedAt(rs.getTimestamp("CREATED_AT"));
							trigger.setRemarks(rs.getString("REMARKS"));
							String text = rs.getString("TRIGGER_TEXT");
							if ("D".equals(id(rs, "TEXT_KIND"))) {
								requireCreate(text, "TRIGGER");
								trigger.setDefinition(text);
							} else {
								if (text != null)
									trigger.setStatement(text);
								trigger.getSpecifics().put("DB2_HOST_REQUIRES_DEFINITION",
										"Complete CREATE required for trigger options/update-column list");
							}
							for (String field : List.of("OLD_TABLE", "NEW_TABLE", "TRIGGER_MODE", "ACTION_ORDER",
									"NATIVE_VERSION")) {
								String value = rs.getString(field);
								if (value != null)
									trigger.getSpecifics().put("DB2_HOST_" + field, value);
							}
							trigger.getSpecifics().put("QUALIFIER", trigger.getSchemaName());
							String path = rs.getString("SQL_PATH");
							if (path != null && !path.isBlank())
								trigger.getSpecifics().put("FUNC_PATH", path);
							result.add(trigger);
						}
					});
			return result;
		}
	}

	static class ObjectGrants extends ObjectPrivilegeReader {
		private final Class<?> resources;

		ObjectGrants(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected List<ObjectPrivilege> doGetAll(Connection connection, ParametersContext context,
				ProductVersionInfo version) {
			List<ObjectPrivilege> result = new ArrayList<>();
			context = context.clone();
			if (context.get("schemaName") == null)
				context.put("schemaName", getSchemaName());
			execute(connection, getSqlNodeCache(resources).getString("objectGrants.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							result.add(objectGrant(rs));
						}
					});
			return result;
		}
	}

	static class ColumnGrants extends ColumnPrivilegeReader {
		private final Class<?> resources;

		ColumnGrants(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected List<ColumnPrivilege> doGetAll(Connection connection, ParametersContext context,
				ProductVersionInfo version) {
			List<ColumnPrivilege> result = new ArrayList<>();
			context = context.clone();
			if (context.get("schemaName") == null)
				context.put("schemaName", getSchemaName());
			execute(connection, getSqlNodeCache(resources).getString("columnGrants.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							var object = objectGrant(rs);
							var column = new ColumnPrivilege();
							column.setSchemaName(object.getSchemaName()).setObjectName(object.getObjectName());
							column.setColumnName(id(rs, "COLUMN_NAME"));
							column.setGrantorName(object.getGrantorName()).setGranteeName(object.getGranteeName());
							column.setPrivilege(object.getPrivilege()).setGrantable(object.isGrantable());
							column.getSpecifics().putAll(object.getSpecifics());
							result.add(column);
						}
					});
			return result;
		}
	}

	static class RoutineGrants extends RoutinePrivilegeReader {
		private final Class<?> resources;

		RoutineGrants(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected List<RoutinePrivilege> doGetAll(Connection connection, ParametersContext context,
				ProductVersionInfo version) {
			List<RoutinePrivilege> result = new ArrayList<>();
			execute(connection, getSqlNodeCache(resources).getString("routineGrants.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							var object = objectGrant(rs);
							var privilege = new RoutinePrivilege();
							privilege.setSchemaName(required(object.getSchemaName(), "routine privilege schema"));
							privilege.setObjectName(object.getObjectName());
							privilege.setSpecificName(required(id(rs, "SPECIFIC_NAME"), "routine specific name"));
							privilege.setGrantorName(object.getGrantorName()).setGranteeName(object.getGranteeName());
							privilege.setPrivilege(object.getPrivilege()).setGrantable(object.isGrantable());
							privilege.setHierachy("*".equals(privilege.getSpecificName()));
							privilege.getSpecifics().putAll(object.getSpecifics());
							String kind = id(rs, "ROUTINE_KIND");
							privilege.getSpecifics().put("DB2_HOST_ROUTINE_KIND", kind == null ? "?" : kind);
							privilege.getSpecifics().put("DB2_HOST_ROUTINE_SCOPE",
									privilege.isHierachy() ? "SCHEMA" : "OBJECT");
							if (privilege.isHierachy())
								privilege.setObjectName(("P".equals(kind) ? "PROCEDURE" : "FUNCTION") + " *");
							if (!flag(rs, "RESOLVED_FLAG"))
								privilege.getSpecifics().put("DB2_HOST_ROUTINE_UNRESOLVED", "Y");
							for (String field : List.of("PACKAGE_COLLECTION", "PACKAGE_TOKEN", "AUTHHOWGOT")) {
								String value = id(rs, field);
								if (value != null && !value.isBlank())
									privilege.getSpecifics().put("DB2_HOST_" + field, value);
							}
							result.add(privilege);
						}
					});
			return result;
		}
	}

	static class SchemaGrants extends com.sqlapp.data.db.dialect.db2.metadata.Db2SchemaPrivilegeReader {
		private final Class<?> resources;

		SchemaGrants(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected List<SchemaPrivilege> doGetAll(Connection connection, ParametersContext context,
				ProductVersionInfo version) {
			List<SchemaPrivilege> result = new ArrayList<>();
			context = context.clone();
			if (context.get("schemaName") == null)
				context.put("schemaName", getSchemaName());
			execute(connection, getSqlNodeCache(resources).getString("schemaGrants.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							var object = objectGrant(rs);
							var privilege = new SchemaPrivilege();
							privilege.setSchemaName(required(object.getSchemaName(), "schema privilege target"));
							privilege.setGrantorName(object.getGrantorName()).setGranteeName(object.getGranteeName());
							privilege.setPrivilege(object.getPrivilege()).setGrantable(object.isGrantable());
							privilege.getSpecifics().putAll(object.getSpecifics());
							if ("*".equals(privilege.getSchemaName()))
								privilege.getSpecifics().put("DB2_HOST_SCHEMA_SCOPE", "ALL");
							result.add(privilege);
						}
					});
			return result;
		}
	}

	private static ObjectPrivilege objectGrant(ExResultSet rs) throws SQLException {
		var privilege = new ObjectPrivilege();
		privilege.setHierachy(false);
		privilege.setSchemaName(id(rs, "SCHEMA_NAME")).setObjectName(id(rs, "OBJECT_NAME"));
		String objectKind = id(rs, "OBJECT_KIND");
		if (objectKind != null) {
			if (!List.of("TABLE", "SEQUENCE", "TYPE").contains(objectKind))
				throw new SQLException("Unknown host privilege object kind: " + objectKind);
			privilege.getSpecifics().put("DB2_HOST_OBJECT_KIND", objectKind);
			if (!"TABLE".equals(objectKind)) {
				required(privilege.getSchemaName(), "typed privilege schema");
				required(privilege.getObjectName(), "typed privilege object name");
				// ObjectPrivilege identity includes specificName, unlike its specifics map.
				// A prefixed identity remains distinct even when the SQL name equals its kind.
				privilege.setSpecificName(objectKind + ":" + privilege.getObjectName());
			}
		}
		privilege.setGrantorName(id(rs, "GRANTOR_NAME")).setGranteeName(id(rs, "GRANTEE_NAME"));
		privilege.setPrivilege(id(rs, "PRIVILEGE_NAME"));
		String grantable = id(rs, "GRANTABLE_FLAG");
		if ("?".equals(grantable))
			privilege.getSpecifics().put("DB2_HOST_GRANTABILITY_UNKNOWN", "Y");
		else
			privilege.setGrantable(flag(rs, "GRANTABLE_FLAG"));
		for (String field : List.of("GRANTEE_TYPE", "GRANTOR_TYPE", "AUTHORIZATION_LIST", "PACKAGE_COLLECTION",
				"PACKAGE_TOKEN", "AUTHHOWGOT")) {
			String value = id(rs, field);
			if (value != null && !value.isBlank())
				privilege.getSpecifics().put("DB2_HOST_" + field, value);
		}
		return privilege;
	}

	static void requireCreate(String text, String kind) throws SQLException {
		if (!required(text, "complete CREATE " + kind).stripLeading()
				.matches("(?is)CREATE\\s+(?:OR\\s+REPLACE\\s+)?" + kind + "\\b.*"))
			throw new SQLException("Catalog text is not a complete CREATE " + kind);
	}

	private static String required(String text, String label) throws SQLException {
		if (text == null || text.isBlank() || text.indexOf('\0') >= 0)
			throw new SQLException("Missing/invalid " + label);
		return text;
	}

	private static String id(ExResultSet rs, String name) throws SQLException {
		String value = rs.getString(name);
		return value == null ? null : value.stripTrailing();
	}

	private static boolean flag(ExResultSet rs, String name) throws SQLException {
		String value = id(rs, name);
		if (!"Y".equals(value) && !"N".equals(value))
			throw new SQLException("Invalid catalog flag " + name + ": " + value);
		return "Y".equals(value);
	}
}
