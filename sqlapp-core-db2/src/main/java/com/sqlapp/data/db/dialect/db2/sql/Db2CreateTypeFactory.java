/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2_1110;
import com.sqlapp.data.db.dialect.db2.util.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.data.schemas.TypeColumn;

/** Native row types; complete definitions cover cursor and structured types. */
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
		} else {
			if (type.getMetaType() != Type.MetaType.Row || type.getColumns().isEmpty()
					|| (type.getSpecifics().get("METATYPE") != null
							&& !"F".equals(type.getSpecifics().get("METATYPE"))))
				throw unsupported(type);
			List<String> fields = new ArrayList<>();
			for (TypeColumn field : type.getColumns()) {
				if (field.getSpecifics().get("DB2_TYPE_MODULE") != null
						|| (field.getSpecifics().get("DB2_TYPE_SCHEMA") != null
								&& !"SYSIBM".equals(field.getSpecifics().get("DB2_TYPE_SCHEMA")))
						|| field.isNotNull() || field.getDefaultValue() != null || field.getCheck() != null
						|| !field.getDefinition().isEmpty() || !field.getStatement().isEmpty()
						|| field.getArrayDimension() != 0)
					throw unsupported(type);
				for (String option : List.of("LOGGED", "COMPACT")) {
					String value = field.getSpecifics().get(option);
					if (value != null && !value.isBlank())
						throw unsupported(type);
				}
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
				fields.add(Db2CreationContext.identifier(field.getName()) + " "
						+ Db2NativeTypeSql.render(getDialect(), components, name + "." + field.getName()));
			}
			addSql(result,
					createSqlBuilder()._add("CREATE TYPE " + name + " AS ROW (" + String.join(", ", fields) + ")"),
					SqlType.CREATE, type);
		}
		if (type.getRemarks() != null)
			addSql(result,
					createSqlBuilder()
							._add("COMMENT ON TYPE " + name + " IS '" + type.getRemarks().replace("'", "''") + "'"),
					SqlType.SET_COMMENT, type);
		return result;
	}

	private IllegalArgumentException unsupported(Type type) {
		return new IllegalArgumentException(
				"Db2 type requires complete ordered CREATE TYPE definition for cursor/structured types or unsupported row fields: "
						+ type.getName());
	}
}
