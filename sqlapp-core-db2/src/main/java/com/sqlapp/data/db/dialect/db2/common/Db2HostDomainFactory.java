/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import java.util.List;
import java.util.Set;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Domain;

/** Host native CREATE TYPE, independent of LUW-specific type options. */
public class Db2HostDomainFactory extends SimpleSqlFactory<Domain, Db2SqlBuilder> {
	public static class Drop extends SimpleSqlFactory<Domain, Db2SqlBuilder> {
		@Override
		public List<SqlOperation> createSql(Domain type) {
			validate(type);
			var result = new java.util.ArrayList<SqlOperation>();
			addSql(result,
					createSqlBuilder()._add("DROP TYPE "
							+ Db2CreationContext.qualified(type.getSchemaName(), type.getName()) + " RESTRICT"),
					SqlType.DROP, type);
			return result;
		}
	}

	@Override
	public List<SqlOperation> createSql(Domain type) {
		validate(type);
		var result = new java.util.ArrayList<SqlOperation>();
		String name = Db2CreationContext.qualified(type.getSchemaName(), type.getName());
		String text;
		if (!type.getDefinition().isEmpty()) {
			if (type.getDefinition().size() != 1 || type.getDefinition().get(0) == null
					|| type.getDefinition().get(0).indexOf('\0') >= 0
					|| !type.getDefinition().get(0).stripLeading().matches("(?is)CREATE\\s+(?:DISTINCT\\s+)?TYPE\\b.*"))
				throw unsupported(type);
			text = type.getDefinition().get(0);
		} else {
			var fields = type.getSpecifics();
			String kind = fields.get("DB2_HOST_METATYPE"), source = fields.get("DB2_HOST_SOURCE_TYPE");
			boolean array = "A".equals(kind);
			boolean zos = "db2-zos".equals(getDialect().getSimpleName());
			boolean ibmI = "db2-i".equals(getDialect().getSimpleName());
			boolean binary = (zos || ibmI) && ("BINARY".equals(source) || "VARBINARY".equals(source));
			boolean iBlob = ibmI && "BLOB".equals(source);
			boolean datetime = (zos || ibmI) && source != null && Set.of("DATE", "TIME", "TIMESTAMP").contains(source);
			boolean iDecfloat = ibmI && "DECFLOAT".equals(source);
			boolean iBoolean = ibmI && "BOOLEAN".equals(source);
			boolean iXml = ibmI && "XML".equals(source);
			boolean rowid = (zos || ibmI) && "T".equals(kind) && "ROWID".equals(source);
			boolean iDatalink = ibmI && "T".equals(kind) && "DATALINK".equals(source);
			if (ibmI && "FLOAT".equals(source)) {
				source = switch (fields.get("DB2_HOST_NATIVE_LENGTH")) {
				case "4" -> "REAL";
				case "8" -> "DOUBLE";
				case null, default -> throw unsupported(type);
				};
			}
			boolean zosString = zos && source != null && (Set.of("VARCHAR", "BLOB", "CLOB").contains(source)
					|| ("T".equals(kind) && "CHAR".equals(source)));
			boolean iString = ibmI && source != null
					&& Set.of("CHAR", "VARCHAR", "GRAPHIC", "VARGRAPHIC", "CLOB", "DBCLOB").contains(source);
			if (!"SYSIBM".equals(fields.get("DB2_HOST_SOURCE_SCHEMA")) || source == null
					|| !Set.of("T", "A").contains(kind == null ? "" : kind)
					|| (!Set.of("SMALLINT", "INTEGER", "BIGINT", "DECIMAL", "NUMERIC", "REAL", "DOUBLE",
							"DOUBLE PRECISION").contains(source) && !zosString && !iString && !binary && !datetime
							&& !iDecfloat && !iBlob && !iBoolean && !rowid && !iXml && !iDatalink)
					|| type.isNotNull() || type.getDefaultValue() != null || type.getCheck() != null
					|| !type.getChecks().isEmpty() || !type.getColumns().isEmpty() || type.getOnUpdate() != null)
				throw unsupported(type);
			var expected = new Domain();
			String mappedSource = iDecfloat ? DataType.DECIMALFLOAT.getTypeName() : source;
			if (iDatalink)
				expected.setDataType(DataType.DATALINK);
			else if (!getDialect().setDbType(mappedSource, type.getLength(), type.getScale(), expected))
				throw unsupported(type);
			if (expected.getDataType() != type.getDataType()
					&& !("DBCLOB".equals(source) && type.getDataType() == DataType.NCLOB))
				throw unsupported(type);
			if (!Set.of("DECIMAL", "NUMERIC").contains(source) && type.getScale() != null)
				throw unsupported(type);
			if (Set.of("BOOLEAN", "ROWID").contains(source) && type.getLength() != null)
				throw unsupported(type);
			String base = source;
			if (iDecfloat) {
				// QSYS2.SYSTYPES.LENGTH stores DECFLOAT's byte length.
				int precision = switch (fields.get("DB2_HOST_NATIVE_LENGTH")) {
				case "8" -> 16;
				case "16" -> 34;
				case null, default -> throw unsupported(type);
				};
				if ("DECIMAL_DIGITS".equals(fields.get("DB2_HOST_LENGTH_UNIT"))) {
					Long modelPrecision = type.getLength();
					if (modelPrecision == null || (modelPrecision != 16L && modelPrecision != 34L))
						throw unsupported(type);
					precision = modelPrecision.intValue();
				}
				base += "(" + precision + ")";
			}
			if ("TIMESTAMP".equals(source)) {
				int precision;
				try {
					precision = Integer.parseInt(fields.get("DB2_HOST_TIMESTAMP_PRECISION"));
				} catch (NumberFormatException e) {
					throw unsupported(type);
				}
				if (precision < 0 || precision > 12)
					throw unsupported(type);
				if ("FRACTIONAL_SECOND_DIGITS".equals(fields.get("DB2_HOST_LENGTH_UNIT"))) {
					Long modelPrecision = type.getLength();
					if (modelPrecision == null || modelPrecision < 0 || modelPrecision > 12)
						throw unsupported(type);
					precision = modelPrecision.intValue();
				}
				base += "(" + precision + ")";
			}
			if (binary)
				base = binarySource(type, source, zos, array);
			if (iBlob) {
				long length = lengthOrNative(type);
				if (length < 1 || length > (array ? 1048576 : Integer.MAX_VALUE))
					throw unsupported(type);
				base = "BLOB(" + length + ")" + iAllocation(type, length, array);
			}
			if (zosString)
				base = zosStringSource(type, source, array);
			if (iString)
				base = iStringSource(type, source, array);
			if (iDatalink) {
				long length = lengthOrNative(type);
				String allocated = fields.get("DB2_HOST_ALLOCATED_LENGTH");
				if (length < 1 || length > 32717 || (allocated != null && !"0".equals(allocated)))
					throw unsupported(type);
				base = "DATALINK(" + length + ")" + iCcsid(type, source);
			}
			if (iXml) {
				Long length = type.getLength();
				if (length != null && length != 2147483647L)
					throw unsupported(type);
				base = "XML" + iAllocation(type, 2147483647L, array) + iCcsid(type, source);
			}
			if (Set.of("DECIMAL", "NUMERIC").contains(source)) {
				Long precision = type.getLength();
				Integer scale = type.getScale();
				if (precision == null || precision < 1 || precision > (zos ? 31 : 63) || scale == null || scale < 0
						|| scale > precision)
					throw unsupported(type);
				base += "(" + precision + "," + scale + ")";
			}
			text = "CREATE TYPE " + name + " AS " + base;
			if ("T".equals(kind)) {
				if (type.getArrayDimension() != 0)
					throw unsupported(type);
				if (!zos) {
					String rules = fields.get("DB2_HOST_TYPE_RULES");
					if (!Set.of("S", "W").contains(rules == null ? "" : rules))
						throw unsupported(type);
					text += " WITH " + ("S".equals(rules) ? "STRONG" : "WEAK") + " TYPE RULES";
				}
			} else {
				int bound = type.getArrayDimensionUpperBound();
				if (type.getArrayDimension() != 1 || bound < 1
						|| !Integer.toString(bound).equals(fields.get("DB2_HOST_ARRAY_CARDINALITY")))
					throw unsupported(type);
				text += " ARRAY[" + bound + "]";
			}
		}
		addSql(result, createSqlBuilder()._add(text), SqlType.CREATE, type);
		if (type.getRemarks() != null)
			addSql(result, createSqlBuilder()._add("COMMENT ON TYPE " + name + " IS '")
					._add(type.getRemarks().replace("'", "''"))._add("'"), SqlType.SET_COMMENT, type);
		return result;
	}

	private static String binarySource(Domain type, String source, boolean zos, boolean array) {
		boolean varying = "VARBINARY".equals(source);
		int maxLength = varying ? (zos ? 32704 : 32740) : (zos ? 255 : 32766);
		Long length = type.getLength();
		if (length == null || length < 1 || length > maxLength)
			throw unsupported(type);
		String base = source + "(" + length + ")";
		if (zos || !varying)
			return base;
		return base + iAllocation(type, length, array);
	}

	private static long lengthOrNative(Domain type) {
		Long length = type.getLength();
		// Schema XML can omit LOB sizes; recover the source length from the catalog.
		if (length != null)
			return length;
		try {
			return Long.parseLong(type.getSpecifics().get("DB2_HOST_NATIVE_LENGTH"));
		} catch (NumberFormatException e) {
			throw unsupported(type);
		}
	}

	private static String zosStringSource(Domain type, String source, boolean array) {
		var fields = type.getSpecifics();
		long length = lengthOrNative(type);
		int maxLength = switch (source) {
		case "CHAR" -> 255;
		case "VARCHAR" -> 32704;
		default -> Integer.MAX_VALUE;
		};
		if (length < 1 || length > maxLength)
			throw unsupported(type);
		String base = source + "(" + length + ")";
		if (!"BLOB".equals(source)) {
			String encoding = switch (fields.get("DB2_HOST_ENCODING_SCHEME")) {
			case "A" -> "ASCII";
			case "E" -> "EBCDIC";
			case "U" -> "UNICODE";
			case null, default -> throw unsupported(type);
			};
			String subtype = switch (fields.get("DB2_HOST_SUBTYPE")) {
			case "S" -> "SBCS";
			case "M" -> "MIXED";
			case "B" -> {
				if (!Set.of("CHAR", "VARCHAR").contains(source) || (array && "CHAR".equals(source)))
					throw unsupported(type);
				yield "BIT";
			}
			case null, default -> throw unsupported(type);
			};
			base += " CCSID " + encoding + " FOR " + subtype + " DATA";
		}
		if ("BLOB".equals(source) || "CLOB".equals(source)) {
			int inlineLength;
			try {
				inlineLength = Integer.parseInt(fields.get("DB2_HOST_INLINE_LENGTH"));
			} catch (NumberFormatException e) {
				throw unsupported(type);
			}
			if (inlineLength < -1 || inlineLength > 32680 || inlineLength > length || (array && inlineLength != -1))
				throw unsupported(type);
			if (inlineLength >= 0)
				base += " INLINE LENGTH " + inlineLength;
		}
		return base;
	}

	private static String iStringSource(Domain type, String source, boolean array) {
		boolean dbclob = "DBCLOB".equals(source);
		boolean clob = "CLOB".equals(source);
		boolean varying = "VARCHAR".equals(source) || "VARGRAPHIC".equals(source) || dbclob || clob;
		int maxLength = switch (source) {
		case "GRAPHIC" -> 16383;
		case "VARGRAPHIC" -> 16370;
		case "DBCLOB" -> array ? 524288 : 1073741823;
		case "CLOB" -> array ? 1048576 : Integer.MAX_VALUE;
		case "VARCHAR" -> 32740;
		default -> 32766;
		};
		Long length = type.getLength();
		if ((dbclob || clob) && length == null)
			length = lengthOrNative(type);
		if (length == null || length < 1 || length > maxLength)
			throw unsupported(type);

		String base = source + "(" + length + ")";
		if (varying)
			base += iAllocation(type, length, array);
		return base + iCcsid(type, source);
	}

	private static String iCcsid(Domain type, String source) {
		var fields = type.getSpecifics();
		boolean graphic = Set.of("GRAPHIC", "VARGRAPHIC", "DBCLOB").contains(source);
		boolean xml = "XML".equals(source);
		boolean bitAllowed = "CHAR".equals(source) || "VARCHAR".equals(source);
		int ccsid;
		try {
			ccsid = Integer.parseInt(fields.get("DB2_HOST_CCSID"));
		} catch (NumberFormatException e) {
			throw unsupported(type);
		}
		if (ccsid < 1 || ccsid > 65535 || (!bitAllowed && ccsid == 65535))
			throw unsupported(type);
		if ((graphic && ccsid == 1208) || (!graphic && !xml && ccsid == 1200))
			throw unsupported(type);

		String base = "";
		String normalization = fields.get("DB2_HOST_NORMALIZE_DATA");
		if (ccsid == 1208 || ccsid == 1200) {
			base += " CCSID " + ccsid;
			return base + switch (normalization) {
			case "YES" -> " NORMALIZED";
			case "NO" -> " NOT NORMALIZED";
			case null, default -> throw unsupported(type);
			};
		}
		if (normalization != null && !"NO".equals(normalization))
			throw unsupported(type);
		return base + (ccsid == 65535 ? " FOR BIT DATA" : " CCSID " + ccsid);
	}

	private static String iAllocation(Domain type, long length, boolean array) {
		String catalogLength = type.getSpecifics().get("DB2_HOST_ALLOCATED_LENGTH");
		if (array) {
			// IBM i array element syntax has no ALLOCATE clause.
			if (catalogLength != null && !"0".equals(catalogLength))
				throw unsupported(type);
			return "";
		}
		int allocated;
		try {
			allocated = Integer.parseInt(catalogLength);
		} catch (NumberFormatException e) {
			throw unsupported(type);
		}
		if (allocated < 0 || allocated > length || allocated > 32766)
			throw unsupported(type);
		return allocated == 0 ? "" : " ALLOCATE(" + allocated + ")";
	}

	private static void validate(Domain type) {
		if (!"DATA_TYPE".equals(type.getSpecifics().get("DB2_OBJECT_TYPE")))
			throw unsupported(type);
		Db2CreationContext.qualified(type.getSchemaName(), type.getName());
	}

	private static IllegalArgumentException unsupported(Domain type) {
		return new IllegalArgumentException(
				"Host data type requires an authoritative complete CREATE TYPE definition: " + type.getName());
	}
}
