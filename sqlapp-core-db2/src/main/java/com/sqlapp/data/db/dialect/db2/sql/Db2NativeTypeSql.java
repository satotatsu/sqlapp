/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.Map;
import java.util.Set;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.schemas.Column;

/**
 * Shared native source-type rendering for variables, distinct types and arrays.
 */
final class Db2NativeTypeSql {
	private Db2NativeTypeSql() {
	}

	static String render(Dialect dialect, Map<String, String> fields, String objectName) {
		Column column = new Column(objectName);
		String type = fields.get("TYPENAME");
		Long length = fields.get("LENGTH") == null ? null : Long.valueOf(fields.get("LENGTH"));
		Integer scale = fields.get("SCALE") == null ? null : Integer.valueOf(fields.get("SCALE"));
		if (Set.of("CHAR", "CHARACTER", "VARCHAR", "GRAPHIC", "VARGRAPHIC", "BLOB", "CLOB", "DBCLOB", "BINARY",
				"VARBINARY", "DECIMAL", "NUMERIC").contains(type == null ? "" : type)
				&& (length == null || length <= 0))
			throw new IllegalArgumentException(
					"Db2 native type data type requires its declared length/precision: " + objectName);
		if (Set.of("DECIMAL", "NUMERIC", "TIMESTAMP", "TIMESTAMP WITHOUT TIME ZONE").contains(type == null ? "" : type)
				&& scale == null)
			throw new IllegalArgumentException(
					"Db2 native type data type requires its declared scale/precision: " + objectName);
		if (Set.of("DECIMAL", "NUMERIC").contains(type == null ? "" : type)
				&& (length > 31 || scale < 0 || scale > length))
			throw new IllegalArgumentException("Db2 native type decimal precision/scale is invalid: " + objectName);
		if (type == null || Set.of("CURSOR", "ROW", "VECTOR", "REFERENCE").contains(type)
				|| !dialect.setDbType(type, length, scale, column) || column.getDataType() == null
				|| column.getDataType().isOther() || column.getDataType().isType() || column.getDataType().isDomain())
			throw new IllegalArgumentException(
					"Db2 native type needs a complete CREATE definition for its data type: " + objectName);
		String units = fields.get("TYPESTRINGUNITS");
		boolean graphic = Set.of("GRAPHIC", "VARGRAPHIC", "DBCLOB", "LONG VARGRAPHIC").contains(type);
		boolean character = Set.of("CHARACTER", "CHAR", "VARCHAR", "CLOB", "LONG VARCHAR").contains(type);
		if (units != null && ((!graphic && !character) || (graphic && "OCTETS".equals(units))
				|| (character && "CODEUNITS16".equals(units))))
			throw new IllegalArgumentException("Db2 native type has incompatible string units: " + objectName);
		if (Set.of("CHARACTER", "CHAR", "VARCHAR", "GRAPHIC", "VARGRAPHIC", "CLOB", "DBCLOB", "LONG VARCHAR",
				"LONG VARGRAPHIC").contains(type)) {
			String codepage = fields.get("CODEPAGE");
			if (codepage == null || (units == null && (Set.of("1208", "1200").contains(codepage)
					|| Set.of("GRAPHIC", "VARGRAPHIC", "DBCLOB", "LONG VARGRAPHIC").contains(type))))
				throw new IllegalArgumentException(
						"Db2 native type requires explicit string units or a complete CREATE definition: "
								+ objectName);
		}
		boolean binary = "0".equals(fields.get("CODEPAGE"))
				&& Set.of("CHARACTER", "CHAR", "VARCHAR", "LONG VARCHAR").contains(type);
		if (binary && units != null && !"OCTETS".equals(units))
			throw new IllegalArgumentException("Db2 FOR BIT DATA requires OCTETS: " + objectName);
		Long renderedLength = length;
		if (units != null) {
			if (!Set.of("OCTETS", "CODEUNITS16", "CODEUNITS32").contains(units)
					|| fields.get("STRINGUNITSLENGTH") == null)
				throw new IllegalArgumentException(
						"Db2 native type string units require an explicit declared length: " + objectName);
			renderedLength = Long.valueOf(fields.get("STRINGUNITSLENGTH"));
			if (renderedLength <= 0)
				throw new IllegalArgumentException("Db2 native type string unit length must be positive");
		}
		Db2SqlBuilder typeBuilder = new Db2SqlBuilder(dialect);
		typeBuilder.typeDefinition(column.getDataType(), null, renderedLength, scale);
		// The shared LOB mapping renders only the type name. LUW declarations need
		// their catalog length as well, so keep this small native difference here.
		String renderedType = Set.of("BLOB", "CLOB", "DBCLOB").contains(type) ? type + "(" + renderedLength + ")"
				: typeBuilder.toString();
		if (units != null && !binary) {
			if (!renderedType.endsWith(")"))
				throw new IllegalArgumentException("Db2 native type string units require a character type");
			renderedType = renderedType.substring(0, renderedType.length() - 1) + " " + units + ")";
		}
		if (binary)
			renderedType += " FOR BIT DATA";
		return renderedType;
	}
}
