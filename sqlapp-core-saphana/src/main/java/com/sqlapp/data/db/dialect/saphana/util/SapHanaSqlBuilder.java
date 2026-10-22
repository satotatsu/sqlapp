/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.FunctionReturning;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * SAP HANA用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class SapHanaSqlBuilder extends AbstractSqlBuilder<SapHanaSqlBuilder> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	public SapHanaSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	@Override
	public SapHanaSqlBuilder clone() {
		return (SapHanaSqlBuilder) super.clone();
	}

	@Override
	public SapHanaSqlBuilder argument(final NamedArgument argument) {
		final ParameterDirection direction = argument.getDirection() == null ? ParameterDirection.Input
				: argument.getDirection();
		_add(direction).space().name(argument.getName()).space();
		typeDefinition(argument);
		if (argument.getDefaultValue() != null) {
			if (direction != ParameterDirection.Input) {
				throw new IllegalArgumentException("SAP HANA defaults require an IN argument: " + argument.getName());
			}
			space().default_().space()._add(argument.getDefaultValue());
		}
		return instance();
	}

	@Override
	public SapHanaSqlBuilder _add(final FunctionReturning returning) {
		if (returning == null) {
			throw new IllegalArgumentException("SAP HANA function requires a return type");
		}
		if (returning.getTable() != null) {
			if (returning.getTable().getColumns().isEmpty()) {
				throw new IllegalArgumentException("SAP HANA table function requires return columns");
			}
			_add("TABLE (");
			int position = 0;
			for (Column column : returning.getTable().getColumns()) {
				if (column.isIdentity() || column.getFormula() != null || column.getDefaultValue() != null) {
					throw new IllegalArgumentException(
							"SAP HANA table return columns support names and types: " + column.getName());
				}
				comma(position++ > 0).name(column).space().addTypeDefinition(column);
			}
			return _add(")");
		}
		if (returning.getName() != null) {
			name(returning.getName()).space();
		}
		if (returning.getDataType() == null && returning.getDataTypeName() == null) {
			throw new IllegalArgumentException("SAP HANA function requires a scalar type or table return columns");
		}
		typeDefinition(returning);
		return instance();
	}

	@Override
	public SapHanaSqlBuilder definition(final Column column, final boolean withRemarks) {
		if (column.getFormula() == null || column.getFormula().isBlank()) {
			return super.definition(column, withRemarks);
		}
		if (column.isIdentity() || column.getDefaultValue() != null) {
			throw new IllegalArgumentException(
					"SAP HANA generated column cannot have identity or default: " + column.getName());
		}
		super.definition(column, withRemarks);
		space().generated().always().as().space()._add("(")._add(column.getFormula())._add(")");
		return instance();
	}

	@Override
	protected SapHanaSqlBuilder autoIncrement(final AbstractColumn<?> column) {
		generated();
		if (column.getIdentityGenerationType() == IdentityGenerationType.ByDefault) {
			space()._add("BY DEFAULT");
		} else {
			always();
		}
		as().identity();
		if (Long.valueOf(0).equals(column.getIdentityStep())) {
			throw new IllegalArgumentException("SAP HANA identity increment must not be zero: " + column.getName());
		}
		final java.util.List<String> options = new java.util.ArrayList<>();
		if (column.getIdentityStartValue() != null)
			options.add("START WITH " + column.getIdentityStartValue());
		if (column.getIdentityStep() != null)
			options.add("INCREMENT BY " + column.getIdentityStep());
		if (column.getIdentityMinValue() != null)
			options.add("MINVALUE " + column.getIdentityMinValue());
		if (column.getIdentityMaxValue() != null)
			options.add("MAXVALUE " + column.getIdentityMaxValue());
		if (column.isIdentityCycle())
			options.add("CYCLE");
		final Integer cacheSize = column.getIdentityCacheSize();
		if (cacheSize != null && cacheSize < 0) {
			throw new IllegalArgumentException(
					"SAP HANA identity cache size must not be negative: " + column.getName());
		}
		if (!column.isIdentityCache() || cacheSize != null && cacheSize <= 1) {
			options.add("NO CACHE");
		} else if (cacheSize != null) {
			options.add("CACHE " + cacheSize);
		}
		if (column.isIdentityOrder())
			options.add("ORDER");
		if (!options.isEmpty()) {
			space()._add("(")._add(String.join(" ", options))._add(")");
		}
		return instance();
	}

	@Override
	protected SapHanaSqlBuilder typeDefinition(final Column column) {
		if (column.getDataType() == DataType.DECIMAL || column.getDataType() == DataType.NUMERIC) {
			final Long precision = column.getLength();
			final Integer scale = column.getScale();
			if (precision != null && (precision < 1 || precision > 38)
					|| scale != null && (scale < 0 || precision == null || scale > precision)) {
				throw new IllegalArgumentException(
						"SAP HANA DECIMAL requires precision 1..38 and scale 0..precision: " + column.getName());
			}
			if (precision == null && column.getDomain() == null) {
				// Unconstrained HANA DECIMAL is a floating decimal, not DECIMAL(0,0).
				return _add("DECIMAL");
			}
		}
		if (column.getDataType() != DataType.VECTOR) {
			return super.typeDefinition(column);
		}
		if (column.getVectorElementDataType() != null && column.getVectorElementDataType() != DataType.REAL) {
			throw new IllegalArgumentException("SAP HANA REAL_VECTOR requires REAL elements: " + column.getName());
		}
		final Integer dimension = column.getVectorDimension();
		if (dimension != null && (dimension.intValue() <= 0 || dimension.intValue() > 65000)) {
			throw new IllegalArgumentException(
					"SAP HANA REAL_VECTOR dimension must be between 1 " + "and 65000: " + column.getName());
		}
		_add("REAL_VECTOR");
		if (dimension != null) {
			brackets(() -> _add(dimension));
		}
		return instance();
	}

	public SapHanaSqlBuilder cosineSimilarity(final CharSequence left, final CharSequence right) {
		return binaryVectorFunction("COSINE_SIMILARITY", left, right);
	}

	public SapHanaSqlBuilder l2Distance(final CharSequence left, final CharSequence right) {
		return binaryVectorFunction("L2DISTANCE", left, right);
	}

	public SapHanaSqlBuilder toRealVector(final CharSequence expression) {
		_add("TO_REAL_VECTOR")._add("(")._add(expression.toString())._add(")");
		return instance();
	}

	private SapHanaSqlBuilder binaryVectorFunction(final String function, final CharSequence left,
			final CharSequence right) {
		_add(function)._add("(")._add(left.toString()).comma()._add(right.toString())._add(")");
		return instance();
	}

}
