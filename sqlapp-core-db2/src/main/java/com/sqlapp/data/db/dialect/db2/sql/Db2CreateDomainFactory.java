/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2_1110;
import com.sqlapp.data.db.dialect.db2.util.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Domain;

/** Shared Domain objects represent native distinct and array data types in Db2. */
public class Db2CreateDomainFactory extends SimpleSqlFactory<Domain, Db2SqlBuilder> {
	static void validate(Domain type, Dialect dialect) {
		if (!(dialect instanceof Db2_1110)) throw new IllegalArgumentException("Db2 distinct/array type recreation requires LUW 11.1 or later");
		if (!"DATA_TYPE".equals(type.getSpecifics().get("DB2_OBJECT_TYPE"))) throw new IllegalArgumentException("Db2 Domain must explicitly represent a DATA_TYPE: " + type.getName());
		Db2CreationContext.qualified(type.getSchemaName(), type.getName());
	}
	@Override public List<SqlOperation> createSql(Domain type) {
		validate(type, getDialect());
		List<SqlOperation> result = new ArrayList<>();
		String name = Db2CreationContext.qualified(type.getSchemaName(), type.getName());
		if (!type.getDefinition().isEmpty()) {
			for (String sql : type.getDefinition()) {
				if (sql == null || sql.isBlank() || sql.indexOf('\0') >= 0) throw new IllegalArgumentException("Db2 type definition requires executable statements");
				addSql(result, createSqlBuilder()._add(sql), SqlType.CREATE, type);
			}
		} else {
			var fields = type.getSpecifics(); String kind = fields.get("METATYPE");
			if (!Set.of("T", "A", "L").contains(kind == null ? "" : kind) || fields.get("SOURCEMODULENAME") != null
					|| (fields.get("SOURCESCHEMA") != null && !"SYSIBM".equals(fields.get("SOURCESCHEMA")))
					|| type.getDefaultValue() != null || type.getOnUpdate() != null || !type.getChecks().isEmpty() || !type.getColumns().isEmpty()) throw unsupported(type);
			String sql = "CREATE TYPE " + name + " AS " + source(type);
			if ("T".equals(kind)) {
				if (type.getArrayDimension() != 0) throw unsupported(type);
				String rules = fields.get("TYPERULES");
				if (!Set.of("S", "W").contains(rules == null ? "" : rules)) throw unsupported(type);
				if ("S".equals(rules) && (type.isNotNull() || type.getCheck() != null)) throw unsupported(type);
				sql += " WITH " + ("S".equals(rules) ? "STRONG" : "WEAK") + " TYPE RULES";
				if (type.isNotNull()) sql += " NOT NULL";
				if (type.getCheck() != null) {
					if (type.getCheck().isBlank() || type.getCheck().indexOf('\0') >= 0) throw unsupported(type);
					sql += " CHECK (" + type.getCheck() + ")";
				}
			} else {
				if (type.isNotNull() || type.getCheck() != null) throw unsupported(type);
				if ("A".equals(kind)) {
					int cardinality = type.getArrayDimensionUpperBound();
					if (type.getArrayDimension() != 1 || cardinality <= 0) throw unsupported(type);
					sql += " ARRAY[" + cardinality + "]";
				} else {
					if (type.getArrayDimension() != 1 || type.getArrayDimensionUpperBound() != 0) throw unsupported(type);
					if (!"SYSIBM".equals(fields.get("ARRAYINDEXTYPESCHEMA")) || !"INTEGER".equals(fields.get("ARRAYINDEXTYPENAME"))) throw unsupported(type);
					sql += " ARRAY[INTEGER]";
				}
			}
			for (String context : Db2CreationContext.sql(null, fields.get("FUNC_PATH"))) addSql(result, createSqlBuilder()._add(context), SqlType.SET_SEARCH_PATH_TO_SCHEMA, type);
			addSql(result, createSqlBuilder()._add(sql), SqlType.CREATE, type);
		}
		if (type.getRemarks() != null) addSql(result, createSqlBuilder()._add("COMMENT ON TYPE " + name + " IS '" + type.getRemarks().replace("'", "''") + "'"), SqlType.SET_COMMENT, type);
		return result;
	}
	private String source(Domain type) {
		String source = type.getSpecifics().get("SOURCENAME");
		if (!Set.of("SMALLINT", "INTEGER", "BIGINT", "REAL", "DOUBLE", "DATE", "TIME", "TIMESTAMP", "DECIMAL", "NUMERIC", "BOOLEAN").contains(source == null ? "" : source)) throw unsupported(type);
		Long length = type.getLength(); Integer scale = type.getScale();
		if (Set.of("DECIMAL", "NUMERIC").contains(source) && (length == null || length < 1 || length > 31 || scale == null || scale < 0 || scale > length)) throw unsupported(type);
		if ("TIMESTAMP".equals(source) && (scale == null || scale < 0 || scale > 12)) throw unsupported(type);
		Column column = new Column("SOURCE");
		if (!getDialect().setDbType(source, length, scale, column) || column.getDataType() == null) throw unsupported(type);
		Db2SqlBuilder builder = createSqlBuilder(); builder.typeDefinition(column.getDataType(), null, length, scale);
		return builder.toString();
	}
	private IllegalArgumentException unsupported(Domain type) { return new IllegalArgumentException("Db2 data type requires complete CREATE TYPE definition for unsupported or ambiguous components: " + type.getName()); }
}
