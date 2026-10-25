/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.SystemVersioning;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TemporalPeriod;
import com.sqlapp.data.schemas.TemporalPeriodType;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * DB2用のSQLビルダー
 *
 * @author tatsuo satoh
 *
 */
public class Db2SqlBuilder extends AbstractSqlBuilder<Db2SqlBuilder> {

	public Db2SqlBuilder(Dialect dialect) {
		super(dialect);
	}

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/*
	 * (non-Javadoc)
	 *
	 * @see com.sqlapp.util.AbstractSqlBuilder#count()
	 */
	@Override
	public Db2SqlBuilder count() {
		appendElement("COUNT_BIG");
		return instance();
	}

	public Db2SqlBuilder starting() {
		appendElement("STARTING");
		return instance();
	}

	public Db2SqlBuilder ending() {
		appendElement("ENDING");
		return instance();
	}

	public Db2SqlBuilder _long() {
		appendElement("LONG");
		return instance();
	}

	public Db2SqlBuilder mask() {
		appendElement("MASK");
		return instance();
	}

	public Db2SqlBuilder argument(NamedArgument obj) {
		argumentBefore(obj);
		if (obj.getDirection() != null) {
			this._add(obj.getDirection()).space();
		}
		if (obj.getName() != null) {
			if (getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110 && obj.getName().indexOf('"') >= 0)
				this._add(Db2CreationContext.identifier(obj.getName()));
			else
				this.name(obj.getName());
			this.space();
		}
		this.typeDefinition(obj);
		argumentAfter(obj);
		return instance();
	}

	@Override
	protected Db2SqlBuilder typeDefinition(final Column column) {
		if (column.getDomain() != null)
			return super.typeDefinition(column);
		if (column.getDataType() == DataType.SQLXML
				&& getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110) {
			_add("XML");
			appendStorageOptions(column);
			return instance();
		}
		if (column.getDataType() != DataType.VECTOR) {
			String units = column.getStringUnits();
			boolean character = column.getDataType() == DataType.CHAR || column.getDataType() == DataType.VARCHAR
					|| column.getDataType() == DataType.CLOB;
			boolean graphic = column.getDataType() == DataType.NCHAR || column.getDataType() == DataType.NVARCHAR
					|| column.getDataType() == DataType.NCLOB;
			boolean bitData = "0".equals(column.getSpecifics().get("CODEPAGE"))
					&& (column.getDataType() == DataType.CHAR || column.getDataType() == DataType.VARCHAR);
			boolean lob = column.getDataType() == DataType.BLOB || column.getDataType() == DataType.CLOB
					|| column.getDataType() == DataType.NCLOB;
			Long declaredLength = column.getLength();
			if (lob && declaredLength == null && column.getSpecifics().containsKey("DB2_LOB_LENGTH"))
				declaredLength = Long.valueOf(column.getSpecifics().get("DB2_LOB_LENGTH"));
			if (units != null || bitData || (lob && declaredLength != null)) {
				if (units != null && (!(getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1050)
						|| (!character && !graphic)
						|| !java.util.Set.of("OCTETS", "CODEUNITS16", "CODEUNITS32").contains(units)
						|| (graphic && "OCTETS".equals(units)) || (character && "CODEUNITS16".equals(units))
						|| (bitData && !"OCTETS".equals(units))))
					throw new IllegalArgumentException("Invalid or unsupported Db2 column string units: "
							+ column.getName() + " (" + column.getDataType() + ", " + units + ")");
				if (declaredLength == null || declaredLength <= 0)
					throw new IllegalArgumentException(
							"Db2 column requires its declared positive length: " + column.getName());
				Db2SqlBuilder type = new Db2SqlBuilder(getDialect());
				type.typeDefinition(column.getDataType(), column.getDataTypeName(), lob ? null : column.getLength(),
						column.getScale());
				String definition = type.toString();
				if (lob)
					definition += "(" + declaredLength + ")";
				if (units != null && !bitData) {
					if (!definition.endsWith(")"))
						throw new IllegalArgumentException("Db2 string units require a sized character type");
					definition = definition.substring(0, definition.length() - 1) + " " + units + ")";
				}
				_add(definition);
				if (bitData)
					_add(" FOR BIT DATA");
				appendStorageOptions(column);
				return instance();
			}
			super.typeDefinition(column);
			appendStorageOptions(column);
			return instance();
		}
		if (getDialect().getDbDataTypes().getDbTypeStrict(DataType.VECTOR) == null) {
			throw new IllegalArgumentException("Db2 VECTOR requires Db2 12.1.2 or later");
		}
		if (column.getVectorDimension() == null || column.getVectorDimension().intValue() <= 0) {
			throw new IllegalArgumentException("VECTOR dimension must be greater than zero: " + column.getName());
		}
		if (column.getVectorElementDataType() == null) {
			throw new IllegalArgumentException("VECTOR element data type is required: " + column.getName());
		}
		final String elementType;
		if (column.getVectorElementDataType() == DataType.REAL) {
			elementType = "FLOAT32";
		} else if (column.getVectorElementDataType() == DataType.TINYINT) {
			elementType = "INT8";
		} else {
			throw new IllegalArgumentException(
					"Unsupported Db2 VECTOR element data type: " + column.getVectorElementDataType());
		}
		_add("VECTOR").brackets(() -> {
			_add(column.getVectorDimension());
			comma()._add(elementType);
		});
		appendStorageOptions(column);
		return instance();
	}

	@Override
	public Db2SqlBuilder definition(final Column column, final boolean withRemarks) {
		String formula = column.getFormula();
		boolean rowChange = "Y".equals(column.getSpecifics().get("ROWCHANGETIMESTAMP"));
		String hidden = column.getSpecifics().get("HIDDEN");
		boolean temporal = java.util.List.of("ROWBEGIN", "ROWEND", "TRANSACTIONSTARTID").stream()
				.anyMatch(key -> "Y".equals(column.getSpecifics().get(key)));
		String generationMode = column.getSpecifics().get("GENERATED");
		if (formula != null
				&& (temporal || (generationMode != null && !generationMode.isBlank() && !"A".equals(generationMode))))
			throw new IllegalArgumentException(
					"Db2 expression column requires GENERATED ALWAYS without temporal generation: " + column.getName());
		if (getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110 && formula == null && !column.isIdentity()
				&& !rowChange && !temporal && generationMode != null && !generationMode.isBlank())
			throw new IllegalArgumentException(
					"Db2 generated column requires its expression or complete table definition: " + column.getName());
		if (formula != null || rowChange || (hidden != null && !hidden.isBlank())) {
			if (!(getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110))
				throw new IllegalArgumentException("Db2 modeled generated/hidden columns require LUW 11.1 or later");
			if ("S".equals(hidden) || (hidden != null && !hidden.isBlank() && !"I".equals(hidden)))
				throw new IllegalArgumentException(
						"Db2 system-managed or unknown hidden column requires complete table definition: "
								+ column.getName());
			if ((formula != null || rowChange) && (column.isIdentity() || column.getDefaultValue() != null))
				throw new IllegalArgumentException(
						"Db2 generated column cannot have DEFAULT or IDENTITY: " + column.getName());
			if (formula != null && (formula.isBlank() || formula.indexOf('\0') >= 0 || rowChange))
				throw new IllegalArgumentException(
						"Invalid or conflicting Db2 generated column expression: " + column.getName());
			if (rowChange && (column.getDataType() != DataType.TIMESTAMP || !column.isNotNull()
					|| (column.getScale() != null && column.getScale() != 6)))
				throw new IllegalArgumentException(
						"Db2 row change column requires TIMESTAMP(6) NOT NULL: " + column.getName());
		}
		super.definition(column, withRemarks);
		if (formula != null)
			generated().always().as().space()._add("(" + formula + ")");
		if (rowChange) {
			String mode = column.getSpecifics().get("GENERATED");
			if (!"A".equals(mode) && !"D".equals(mode))
				throw new IllegalArgumentException(
						"Db2 row change column requires explicit GENERATED A or D: " + column.getName());
			generated();
			if ("A".equals(mode))
				always();
			else
				by().default_();
			_add(" FOR EACH ROW ON UPDATE AS ROW CHANGE TIMESTAMP");
		}
		if ("I".equals(hidden))
			_add(" IMPLICITLY HIDDEN");
		final Table table = column.getTable();
		if (table == null) {
			return instance();
		}
		for (final TemporalPeriod period : table.getTemporalPeriods()) {
			if (period.getPeriodType() != TemporalPeriodType.SYSTEM_TIME) {
				continue;
			}
			if (CommonUtils.eqIgnoreCase(period.getStartColumnName(), column.getName())) {
				generated().always().as().row().space()._add("BEGIN");
			} else if (CommonUtils.eqIgnoreCase(period.getEndColumnName(), column.getName())) {
				generated().always().as().row().space()._add("END");
			}
		}
		final SystemVersioning versioning = table.getSystemVersioning();
		if (versioning != null && CommonUtils.eqIgnoreCase(versioning.getTransactionIdColumnName(), column.getName())) {
			generated().always().as().transaction().space()._add("START ID");
		}
		return instance();
	}

	protected void argumentBefore(NamedArgument obj) {
	}

	@Override
	protected Db2SqlBuilder autoIncrement(final com.sqlapp.data.schemas.AbstractColumn<?> column) {
		if (!(getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110))
			return super.autoIncrement(column);
		if (column.getDefaultValue() != null)
			throw new IllegalArgumentException("Db2 identity cannot have DEFAULT: " + column.getName());
		var sequence = column.getSequence();
		java.math.BigInteger start = identityNumber(column, "START",
				sequence == null ? null : sequence.getStartValue());
		java.math.BigInteger step = identityNumber(column, "INCREMENT",
				sequence == null ? null : sequence.getIncrementBy());
		java.math.BigInteger min = identityNumber(column, "MINVALUE", sequence == null ? null : sequence.getMinValue());
		java.math.BigInteger max = identityNumber(column, "MAXVALUE", sequence == null ? null : sequence.getMaxValue());
		Integer cache = column.getIdentityCacheSize();
		java.math.BigInteger typeMin, typeMax;
		if (column.getDataType() == DataType.SMALLINT) {
			typeMin = java.math.BigInteger.valueOf(Short.MIN_VALUE);
			typeMax = java.math.BigInteger.valueOf(Short.MAX_VALUE);
		} else if (column.getDataType() == DataType.INT) {
			typeMin = java.math.BigInteger.valueOf(Integer.MIN_VALUE);
			typeMax = java.math.BigInteger.valueOf(Integer.MAX_VALUE);
		} else if (column.getDataType() == DataType.BIGINT) {
			typeMin = java.math.BigInteger.valueOf(Long.MIN_VALUE);
			typeMax = java.math.BigInteger.valueOf(Long.MAX_VALUE);
		} else if (column.getDataType() == DataType.DECIMAL || column.getDataType() == DataType.NUMERIC) {
			if (column.getLength() == null || column.getLength() < 1 || column.getLength() > 31
					|| (column.getScale() != null && column.getScale() != 0))
				throw new IllegalArgumentException(
						"Db2 decimal identity requires precision 1..31 and scale zero: " + column.getName());
			typeMax = java.math.BigInteger.TEN.pow(column.getLength().intValue()).subtract(java.math.BigInteger.ONE);
			typeMin = typeMax.negate();
		} else
			throw new IllegalArgumentException(
					"Db2 identity needs a supported exact numeric type or complete table definition: "
							+ column.getName());
		for (java.math.BigInteger value : new java.math.BigInteger[] { start, min, max })
			if (value != null && (value.compareTo(typeMin) < 0 || value.compareTo(typeMax) > 0))
				throw new IllegalArgumentException("Db2 identity bounds exceed the column type: " + column.getName());
		if ((step != null && step.signum() == 0) || (min != null && max != null && min.compareTo(max) > 0)
				|| (start != null
						&& ((min != null && start.compareTo(min) < 0) || (max != null && start.compareTo(max) > 0)))
				|| (cache != null && (cache < 0 || cache == 1)))
			throw new IllegalArgumentException(
					"Invalid Db2 identity bounds, increment or cache size: " + column.getName());
		generated();
		if (column.getIdentityGenerationType() == com.sqlapp.data.schemas.IdentityGenerationType.ByDefault)
			by().default_();
		else
			always();
		as().identity();
		java.util.List<String> options = new java.util.ArrayList<>();
		if (start != null)
			options.add("START WITH " + start);
		if (step != null)
			options.add("INCREMENT BY " + step);
		if (min != null)
			options.add("MINVALUE " + min);
		if (max != null)
			options.add("MAXVALUE " + max);
		options.add(column.isIdentityCycle() ? "CYCLE" : "NO CYCLE");
		if (!column.isIdentityCache() || (cache != null && cache == 0))
			options.add("NO CACHE");
		else if (cache != null)
			options.add("CACHE " + cache);
		options.add(column.isIdentityOrder() ? "ORDER" : "NO ORDER");
		_add(" (" + String.join(" ", options) + ")");
		return instance();
	}

	private java.math.BigInteger identityNumber(com.sqlapp.data.schemas.AbstractColumn<?> column, String key,
			java.math.BigInteger modeled) {
		String exact = column.getSpecifics().get("DB2_IDENTITY_" + key);
		java.math.BigInteger value = modeled != null ? modeled : exact == null ? null : new java.math.BigInteger(exact);
		if (value != null && value.abs().toString().length() > 31)
			throw new IllegalArgumentException("Db2 identity value exceeds 31 digits: " + column.getName());
		return value;
	}

	private void appendStorageOptions(Column column) {
		String logged = column.getSpecifics().get("LOGGED"), compact = column.getSpecifics().get("COMPACT");
		String inline = column.getSpecifics().get("INLINE_LENGTH");
		boolean hasLogged = logged != null && !logged.isBlank(), hasCompact = compact != null && !compact.isBlank();
		boolean hasInline = inline != null && !inline.isBlank() && !"0".equals(inline);
		if (!hasLogged && !hasCompact && !hasInline)
			return;
		if (!(getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110)) {
			// Older readers already retained LOGGED, but did not promise native replay.
			if (hasCompact || hasInline)
				throw storageError(column);
			return;
		}
		boolean lob = column.getDataType() == DataType.BLOB || column.getDataType() == DataType.CLOB
				|| column.getDataType() == DataType.NCLOB;
		if ((hasLogged || hasCompact) && !lob)
			throw storageError(column);
		if (hasLogged) {
			if (!java.util.Set.of("Y", "N").contains(logged))
				throw storageError(column);
			_add("Y".equals(logged) ? " LOGGED" : " NOT LOGGED");
		}
		if (hasCompact) {
			if (!java.util.Set.of("Y", "N").contains(compact))
				throw storageError(column);
			_add("Y".equals(compact) ? " COMPACT" : " NOT COMPACT");
		}
		if (hasInline) {
			if (!lob && column.getDataType() != DataType.SQLXML)
				throw storageError(column);
			final int length;
			try {
				length = Integer.parseInt(inline);
			} catch (NumberFormatException e) {
				throw storageError(column);
			}
			int minimum = 292;
			if (lob) {
				Long bytes = column.getSpecifics().containsKey("DB2_LOB_OCTET_LENGTH")
						? Long.valueOf(column.getSpecifics().get("DB2_LOB_OCTET_LENGTH"))
						: column.getLength();
				if (bytes == null && column.getSpecifics().containsKey("DB2_LOB_LENGTH"))
					bytes = Long.valueOf(column.getSpecifics().get("DB2_LOB_LENGTH"));
				if (bytes == null || bytes <= 0 || bytes > Integer.MAX_VALUE)
					throw storageError(column);
				if (!column.getSpecifics().containsKey("DB2_LOB_OCTET_LENGTH")) {
					if ("CODEUNITS32".equals(column.getStringUnits()))
						bytes = Math.multiplyExact(bytes, 4);
					else if (column.getDataType() == DataType.NCLOB)
						bytes = Math.multiplyExact(bytes, 2);
				}
				if (bytes > Integer.MAX_VALUE)
					throw storageError(column);
				long[] sizes = { 1024, 8192, 65536, 524000, 4190000, 134000000, 536000000, 1070000000, 1470000000,
						Integer.MAX_VALUE };
				int[] descriptors = { 68, 92, 116, 140, 164, 196, 220, 252, 276, 312 };
				minimum = 312;
				for (int i = 0; i < sizes.length; i++)
					if (bytes <= sizes[i]) {
						minimum = descriptors[i];
						break;
					}
			}
			if (length < minimum || length > 32673)
				throw storageError(column);
			_add(" INLINE LENGTH ")._add(length);
		}
	}

	private IllegalArgumentException storageError(Column column) {
		return new IllegalArgumentException(
				"Invalid or unsupported Db2 column storage options; supply compatible LOB/XML attributes or a complete table definition: "
						+ column.getName());
	}

	@Override
	public Db2SqlBuilder clone() {
		return (Db2SqlBuilder) super.clone();
	}
}
