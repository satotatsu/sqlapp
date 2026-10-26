/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2_1110;
import com.sqlapp.data.db.dialect.db2.util.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Domain;

/**
 * Shared Domain objects represent native distinct and array data types in Db2.
 */
public class Db2CreateDomainFactory extends SimpleSqlFactory<Domain, Db2SqlBuilder> {
	static void validate(Domain type, Dialect dialect) {
		if (!(dialect instanceof Db2_1110))
			throw new IllegalArgumentException("Db2 distinct/array type recreation requires LUW 11.1 or later");
		if (!"DATA_TYPE".equals(type.getSpecifics().get("DB2_OBJECT_TYPE")))
			throw new IllegalArgumentException("Db2 Domain must explicitly represent a DATA_TYPE: " + type.getName());
		Db2CreationContext.qualified(type.getSchemaName(), type.getName());
	}

	@Override
	public List<SqlOperation> createSql(Domain type) {
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
			var fields = type.getSpecifics();
			String kind = fields.get("METATYPE");
			if (!Set.of("T", "A", "L").contains(kind == null ? "" : kind) || type.getDefaultValue() != null
					|| type.getOnUpdate() != null || !type.getChecks().isEmpty() || !type.getColumns().isEmpty())
				throw unsupported(type);
			String sql = createTypePrefix(type) + " AS " + renderSourceType(type);
			if ("T".equals(kind)) {
				if (type.getArrayDimension() != 0)
					throw unsupported(type);
				String rules = fields.get("TYPERULES");
				if (!Set.of("S", "W").contains(rules == null ? "" : rules))
					throw unsupported(type);
				if ("S".equals(rules) && (type.isNotNull() || type.getCheck() != null))
					throw unsupported(type);
				sql += " WITH " + ("S".equals(rules) ? "STRONG" : "WEAK") + " TYPE RULES";
				if (type.isNotNull())
					sql += " NOT NULL";
				if (type.getCheck() != null) {
					if (type.getCheck().isBlank() || type.getCheck().indexOf('\0') >= 0)
						throw unsupported(type);
					sql += " CHECK (" + type.getCheck() + ")";
				}
			} else {
				if (type.isNotNull() || type.getCheck() != null)
					throw unsupported(type);
				if ("A".equals(kind)) {
					int cardinality = type.getArrayDimensionUpperBound();
					if (type.getArrayDimension() != 1 || cardinality <= 0)
						throw unsupported(type);
					sql += " ARRAY[" + cardinality + "]";
				} else {
					if (type.getArrayDimension() != 1 || type.getArrayDimensionUpperBound() != 0)
						throw unsupported(type);
					if (!"SYSIBM".equals(fields.get("ARRAYINDEXTYPESCHEMA")))
						throw unsupported(type);
					sql += " ARRAY[" + arrayIndex(type) + "]";
				}
			}
			for (String context : Db2CreationContext.sql(null, fields.get("FUNC_PATH")))
				addSql(result, createSqlBuilder()._add(context), SqlType.SET_SEARCH_PATH_TO_SCHEMA, type);
			addSql(result, createSqlBuilder()._add(sql), SqlType.CREATE, type);
		}
		if (type.getRemarks() != null)
			addSql(result,
					createSqlBuilder()
							._add("COMMENT ON TYPE " + name + " IS '" + type.getRemarks().replace("'", "''") + "'"),
					SqlType.SET_COMMENT, type);
		return result;
	}

	/** Module types share the same source, constraint and array validators. */
	protected String createTypePrefix(Domain type) {
		return "CREATE TYPE " + Db2CreationContext.qualified(type.getSchemaName(), type.getName());
	}

	@Override
	protected List<Domain> sort(List<Domain> types) {
		return Db2TypeCreateOrder.sort(types).stream().map(Domain.class::cast).toList();
	}

	/** Distinct sources remain built-in; arrays may use resolved named types. */
	protected String renderSourceType(Domain type) {
		var specifics = type.getSpecifics();
		if (Set.of("A", "L").contains(specifics.getOrDefault("METATYPE", ""))
				&& specifics.get("SOURCEMODULENAME") == null && specifics.get("SOURCESCHEMA") != null
				&& !"SYSIBM".equals(specifics.get("SOURCESCHEMA")))
			return Db2CreationContext.qualified(specifics.get("SOURCESCHEMA"), specifics.get("SOURCENAME"));
		if (specifics.get("SOURCEMODULENAME") != null
				|| (specifics.get("SOURCESCHEMA") != null && !"SYSIBM".equals(specifics.get("SOURCESCHEMA"))))
			throw unsupported(type);
		String source = type.getSpecifics().get("SOURCENAME");
		if (!Set.of("SMALLINT", "INTEGER", "BIGINT", "REAL", "DOUBLE", "DATE", "TIME", "TIMESTAMP", "DECIMAL",
				"NUMERIC", "BOOLEAN", "CHAR", "CHARACTER", "VARCHAR", "GRAPHIC", "VARGRAPHIC", "BINARY", "VARBINARY",
				"BLOB", "CLOB", "DBCLOB").contains(source == null ? "" : source))
			throw unsupported(type);
		Long length = type.getLength();
		Integer scale = type.getScale();
		if (Set.of("DECIMAL", "NUMERIC").contains(source)
				&& (length == null || length < 1 || length > 31 || scale == null || scale < 0 || scale > length))
			throw unsupported(type);
		if ("TIMESTAMP".equals(source) && (scale == null || scale < 0 || scale > 12))
			throw unsupported(type);
		var fields = new HashMap<String, String>(type.getSpecifics());
		fields.put("TYPENAME", source);
		if (length != null)
			fields.put("LENGTH", length.toString());
		if (scale != null)
			fields.put("SCALE", scale.toString());
		return Db2NativeTypeSql.render(getDialect(), fields, type.getName());
	}

	private String arrayIndex(Domain type) {
		var fields = type.getSpecifics();
		String name = fields.get("ARRAYINDEXTYPENAME");
		if ("INTEGER".equals(name))
			return "INTEGER";
		if (!"VARCHAR".equals(name))
			throw unsupported(type);
		String units = fields.get("ARRAYINDEXTYPE_STRINGUNITS");
		if (units != null && !Set.of("OCTETS", "CODEUNITS32").contains(units))
			throw unsupported(type);
		String value = fields.get(units == null ? "ARRAYINDEXTYPELENGTH" : "ARRAYINDEXTYPE_STRINGUNITSLENGTH");
		long length;
		try {
			length = Long.parseLong(value);
		} catch (RuntimeException e) {
			throw unsupported(type);
		}
		if (length <= 0)
			throw unsupported(type);
		Db2SqlBuilder builder = createSqlBuilder();
		builder.typeDefinition(com.sqlapp.data.db.datatype.DataType.VARCHAR, null, length, null);
		String sql = builder.toString();
		if (units != null) {
			if (!sql.endsWith(")"))
				throw unsupported(type);
			sql = sql.substring(0, sql.length() - 1) + " " + units + ")";
		}
		return sql;
	}

	private IllegalArgumentException unsupported(Domain type) {
		return new IllegalArgumentException(
				"Db2 data type requires complete CREATE TYPE definition for unsupported or ambiguous components: "
						+ type.getName());
	}
}
