/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import java.util.List;
import java.util.Set;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Domain;

/** Host native CREATE TYPE, independent of LUW-specific type options. */
public class Db2HostDomainFactory extends SimpleSqlFactory<Domain, Db2SqlBuilder> {
	public static class Drop extends SimpleSqlFactory<Domain, Db2SqlBuilder> {
		@Override public List<SqlOperation> createSql(Domain type) {
			validate(type);
			var result = new java.util.ArrayList<SqlOperation>();
			addSql(result, createSqlBuilder()._add("DROP TYPE " + Db2CreationContext.qualified(type.getSchemaName(), type.getName()) + " RESTRICT"), SqlType.DROP, type);
			return result;
		}
	}

	@Override public List<SqlOperation> createSql(Domain type) {
		validate(type);
		var result = new java.util.ArrayList<SqlOperation>();
		String name = Db2CreationContext.qualified(type.getSchemaName(), type.getName());
		String text;
		if (!type.getDefinition().isEmpty()) {
			if (type.getDefinition().size() != 1 || type.getDefinition().get(0) == null
					|| type.getDefinition().get(0).indexOf('\0') >= 0
					|| !type.getDefinition().get(0).stripLeading().matches("(?is)CREATE\\s+(?:DISTINCT\\s+)?TYPE\\b.*")) throw unsupported(type);
			text = type.getDefinition().get(0);
		} else {
			var fields = type.getSpecifics();
			String kind = fields.get("DB2_HOST_METATYPE"), source = fields.get("DB2_HOST_SOURCE_TYPE");
			boolean zos = "db2-zos".equals(getDialect().getSimpleName());
			if (!"SYSIBM".equals(fields.get("DB2_HOST_SOURCE_SCHEMA")) || source == null
					|| !Set.of("T", "A").contains(kind == null ? "" : kind)
					|| !Set.of("SMALLINT", "INTEGER", "BIGINT", "DECIMAL", "NUMERIC", "REAL", "DOUBLE", "DOUBLE PRECISION").contains(source)
					|| type.isNotNull() || type.getDefaultValue() != null || type.getCheck() != null
					|| !type.getChecks().isEmpty() || !type.getColumns().isEmpty() || type.getOnUpdate() != null) throw unsupported(type);
			var expected = new Domain();
			if (!getDialect().setDbType(source, type.getLength(), type.getScale(), expected)
					|| expected.getDataType() != type.getDataType()) throw unsupported(type);
			String base = source;
			if (Set.of("DECIMAL", "NUMERIC").contains(source)) {
				Long precision = type.getLength(); Integer scale = type.getScale();
				if (precision == null || precision < 1 || precision > (zos ? 31 : 63)
						|| scale == null || scale < 0 || scale > precision) throw unsupported(type);
				base += "(" + precision + "," + scale + ")";
			}
			text = "CREATE TYPE " + name + " AS " + base;
			if ("T".equals(kind)) {
				if (type.getArrayDimension() != 0) throw unsupported(type);
				if (!zos) {
					String rules = fields.get("DB2_HOST_TYPE_RULES");
					if (!Set.of("S", "W").contains(rules == null ? "" : rules)) throw unsupported(type);
					text += " WITH " + ("S".equals(rules) ? "STRONG" : "WEAK") + " TYPE RULES";
				}
			} else {
				int bound = type.getArrayDimensionUpperBound();
				if (type.getArrayDimension() != 1 || bound < 1
						|| !Integer.toString(bound).equals(fields.get("DB2_HOST_ARRAY_CARDINALITY"))) throw unsupported(type);
				text += " ARRAY[" + bound + "]";
			}
		}
		addSql(result, createSqlBuilder()._add(text), SqlType.CREATE, type);
		if (type.getRemarks() != null)
			addSql(result, createSqlBuilder()._add("COMMENT ON TYPE " + name + " IS '")._add(type.getRemarks().replace("'", "''"))._add("'"), SqlType.SET_COMMENT, type);
		return result;
	}
	private static void validate(Domain type) {
		if (!"DATA_TYPE".equals(type.getSpecifics().get("DB2_OBJECT_TYPE"))) throw unsupported(type);
		Db2CreationContext.qualified(type.getSchemaName(), type.getName());
	}
	private static IllegalArgumentException unsupported(Domain type) {
		return new IllegalArgumentException("Host data type requires an authoritative complete CREATE TYPE definition: " + type.getName());
	}
}
