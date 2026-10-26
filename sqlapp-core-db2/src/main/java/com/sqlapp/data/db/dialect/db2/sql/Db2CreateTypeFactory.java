/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2_1110;
import com.sqlapp.data.db.dialect.db2.util.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.data.schemas.TypeColumn;

/**
 * Native row/cursor types; full definitions cover unsupported targets and
 * structured types.
 */
public class Db2CreateTypeFactory extends SimpleSqlFactory<Type, Db2SqlBuilder> {
	static void validate(Type type, Dialect dialect) {
		if (!(dialect instanceof Db2_1110))
			throw new IllegalArgumentException("Db2 row/cursor/structured type recreation requires LUW 11.1 or later");
		Db2CreationContext.qualified(type.getSchemaName(), type.getName());
	}

	@Override
	public List<SqlOperation> createSql(Type type) {
		validate(type, getDialect());
		List<SqlOperation> result = new ArrayList<>();
		String name = Db2CreationContext.qualified(type.getSchemaName(), type.getName());
		if (!type.getDefinition().isEmpty()) {
			for (String sql : Db2DefinitionSql.statements(type.getDefinition(), getDialect())) {
				if (sql == null || sql.isBlank() || sql.indexOf('\0') >= 0)
					throw new IllegalArgumentException("Db2 type definition requires executable statements");
				addSql(result, createSqlBuilder()._add(sql), SqlType.CREATE, type);
			}
		} else if (type.getMetaType() == Type.MetaType.Cursor) {
			if (!type.getColumns().isEmpty() || !type.getStatement().isEmpty()
					|| !"C".equals(type.getSpecifics().getOrDefault("METATYPE", "C")))
				throw unsupported(type);
			String kind = type.getSpecifics().get("DB2_CURSOR_KIND");
			String row;
			if ("W".equals(kind)) {
				if (List.of("DB2_CURSOR_ROW_SCHEMA", "DB2_CURSOR_ROW_MODULE", "DB2_CURSOR_ROW_NAME").stream()
						.anyMatch(type.getSpecifics()::containsKey))
					throw unsupported(type);
				row = "";
			} else if ("S".equals(kind))
				row = renderCursorRowType(type) + " ";
			else
				throw unsupported(type);
			addSql(result, createSqlBuilder()._add(createTypePrefix(type) + " AS " + row + "CURSOR"), SqlType.CREATE,
					type);
		} else {
			if (type.getMetaType() != Type.MetaType.Row || type.getColumns().isEmpty()
					|| (type.getSpecifics().get("METATYPE") != null
							&& !"F".equals(type.getSpecifics().get("METATYPE"))))
				throw unsupported(type);
			List<String> fields = new ArrayList<>();
			for (TypeColumn field : type.getColumns()) {
				if (field.isNotNull() || field.getDefaultValue() != null || field.getCheck() != null
						|| (field.getSpecifics().get("COLLATIONSCHEMA") != null
								&& !"SYSIBM".equals(field.getSpecifics().get("COLLATIONSCHEMA")))
						|| !field.getDefinition().isEmpty() || !field.getStatement().isEmpty()
						|| field.getArrayDimension() != 0)
					throw unsupported(type);
				for (String option : List.of("LOGGED", "COMPACT")) {
					String value = field.getSpecifics().get(option);
					if (value != null && !value.isBlank())
						throw unsupported(type);
				}
				fields.add(Db2CreationContext.identifier(field.getName()) + " " + renderFieldType(type, field));
			}
			addSql(result,
					createSqlBuilder()._add(createTypePrefix(type) + " AS ROW (" + String.join(", ", fields) + ")"),
					SqlType.CREATE, type);
		}
		if (type.getRemarks() != null)
			addSql(result,
					createSqlBuilder()
							._add("COMMENT ON TYPE " + name + " IS '" + type.getRemarks().replace("'", "''") + "'"),
					SqlType.SET_COMMENT, type);
		return result;
	}

	@Override
	protected List<Type> sort(List<Type> types) {
		var sorted = new ArrayList<>(types);
		sorted.sort(Comparator.comparing(type -> type.getMetaType() == Type.MetaType.Cursor));
		return Db2TypeCreateOrder.sort(sorted).stream().map(Type.class::cast).toList();
	}

	/**
	 * Catalog-resolved row names retain schema and optional module qualification.
	 */
	protected String renderCursorRowType(Type type) {
		var fields = type.getSpecifics();
		String prefix = Db2CreationContext.identifier(fields.get("DB2_CURSOR_ROW_SCHEMA"));
		if (fields.containsKey("DB2_CURSOR_ROW_MODULE"))
			prefix += "." + Db2CreationContext.identifier(fields.get("DB2_CURSOR_ROW_MODULE"));
		return prefix + "." + Db2CreationContext.identifier(fields.get("DB2_CURSOR_ROW_NAME"));
	}

	/** Module members may resolve an earlier published local type here. */
	protected String renderFieldType(Type type, TypeColumn field) {
		var fields = field.getSpecifics();
		String schema = fields.get("DB2_TYPE_SCHEMA");
		if (fields.containsKey("DB2_TYPE_MODULE"))
			throw unsupported(type);
		if (schema != null && !"SYSIBM".equals(schema))
			return Db2CreationContext.qualified(schema, fields.get("TYPENAME"));
		var components = new HashMap<String, String>(field.getSpecifics());
		if (!components.containsKey("TYPENAME")) {
			if (field.getDataType() == null || field.getDataType().isOther() || field.getDataType().isDomain()
					|| field.getDataType().isType())
				throw unsupported(type);
			String source = field.getDataTypeName() == null ? field.getDataType().getTypeName()
					: field.getDataTypeName();
			if (!source.matches("[A-Za-z]+(?: [A-Za-z]+)*"))
				throw unsupported(type);
			components.put("TYPENAME", source.toUpperCase(Locale.ROOT));
		}
		if (field.getLength() != null)
			components.put("LENGTH", field.getLength().toString());
		if (field.getScale() != null)
			components.put("SCALE", field.getScale().toString());
		return Db2NativeTypeSql.render(getDialect(), components, type.getName() + "." + field.getName());
	}

	/** Module-scoped row types compose the same field validation and rendering. */
	protected String createTypePrefix(Type type) {
		return "CREATE TYPE " + Db2CreationContext.qualified(type.getSchemaName(), type.getName());
	}

	private IllegalArgumentException unsupported(Type type) {
		return new IllegalArgumentException(
				"Db2 type requires complete ordered CREATE TYPE definition for unsupported cursor targets, structured types or row fields: "
						+ type.getName());
	}
}
