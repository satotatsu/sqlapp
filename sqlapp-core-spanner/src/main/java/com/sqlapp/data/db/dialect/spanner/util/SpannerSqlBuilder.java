/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-spanner.
 *
 * sqlapp-core-spanner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-spanner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-spanner.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.spanner.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * Spanner用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class SpannerSqlBuilder extends AbstractSqlBuilder<SpannerSqlBuilder> {

	public static final String ALLOW_COMMIT_TIMESTAMP = "ALLOW_COMMIT_TIMESTAMP";

	public static final String IDENTITY_BIT_REVERSED_POSITIVE = "IDENTITY_BIT_REVERSED_POSITIVE";

	public static final String IDENTITY_SKIP_RANGE_MIN = "IDENTITY_SKIP_RANGE_MIN";

	public static final String IDENTITY_SKIP_RANGE_MAX = "IDENTITY_SKIP_RANGE_MAX";

	public static final String VECTOR_LENGTH = "VECTOR_LENGTH";
	public static final String LOCALITY_GROUP = "LOCALITY_GROUP";

	public SpannerSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public SpannerSqlBuilder definition(final Column column, final boolean withRemarks) {
		// GoogleSQL orders NOT NULL before DEFAULT and requires a parenthesized
		// expression.
		final Column structural = column.clone().setDefaultValue(null).setOnUpdate(null);
		super.definition(structural, false);
		if (!column.isIdentity() && !CommonUtils.isEmpty(column.getDefaultValue())) {
			space().default_().space()._add("(")._add(column.getDefaultValue())._add(")");
		}
		if (!CommonUtils.isEmpty(column.getOnUpdate())) {
			onUpdateDefinition(column);
		}
		if (!CommonUtils.isEmpty(column.getFormula())) {
			space().as().space()._add("(")._add(column.getFormula())._add(")");
			if (column.isFormulaPersisted()) {
				space()._add("STORED");
			}
		}
		if (column.isHidden()) {
			space()._add("HIDDEN");
		}
		final Boolean allowCommitTimestamp = allowCommitTimestamp(column);
		final String localityGroup = column.getSpecifics().get(LOCALITY_GROUP);
		boolean hasOption = false;
		if (Boolean.TRUE.equals(allowCommitTimestamp)) {
			space()._add("OPTIONS").space()._add("(")._add("allow_commit_timestamp=true");
			hasOption = true;
		}
		if (!CommonUtils.isEmpty(localityGroup)) {
			if (!hasOption) {
				space()._add("OPTIONS").space()._add("(");
			} else {
				comma();
			}
			_add("locality_group = ").sqlChar(localityGroup);
			hasOption = true;
		}
		if (hasOption) {
			_add(')');
		}
		if (withRemarks && !CommonUtils.isEmpty(column.getRemarks())) {
			comment(column);
		}
		return this;
	}

	/**
	 * Reads the optional column flag without coercing malformed values to false.
	 */
	public static Boolean allowCommitTimestamp(final Column column) {
		final String value = column.getSpecifics().get(ALLOW_COMMIT_TIMESTAMP);
		if (value == null)
			return null;
		if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
			throw new IllegalArgumentException(
					"Cloud Spanner " + ALLOW_COMMIT_TIMESTAMP + " must be true or false: " + column.getName());
		}
		if (column.getDataType() != DataType.TIMESTAMP || column.getArrayDimension() != 0) {
			throw new IllegalArgumentException(
					"Cloud Spanner allow_commit_timestamp requires a scalar TIMESTAMP column: " + column.getName());
		}
		return Boolean.valueOf(value);
	}

	@Override
	public SpannerSqlBuilder definitionForAlterColumn(final Column column) {
		if (column.isIdentity() || !CommonUtils.isEmpty(column.getFormula())) {
			throw new UnsupportedOperationException(
					"Cloud Spanner identity/generated columns require a separate ALTER migration: " + column.getName());
		}
		typeDefinition(column);
		notNullDefinition(column);
		// These clauses are removed by a typed ALTER if omitted, so restate the target
		// values.
		if (!CommonUtils.isEmpty(column.getDefaultValue())) {
			space().default_().space()._add("(")._add(column.getDefaultValue())._add(")");
		}
		if (!CommonUtils.isEmpty(column.getOnUpdate())) {
			onUpdateDefinition(column);
		}
		return this;
	}

	@Override
	protected SpannerSqlBuilder typeDefinition(final Column column) {
		if (column.getArrayDimension() == 0) {
			if (isNumeric(column)) {
				return _add("NUMERIC");
			}
			return super.typeDefinition(column);
		}
		if (column.getArrayDimension() != 1) {
			throw new IllegalArgumentException(
					"Cloud Spanner supports only one-dimensional arrays: " + column.getName());
		}
		_add("ARRAY<");
		if (isNumeric(column)) {
			_add("NUMERIC");
		} else {
			super.typeDefinition(column);
		}
		_add(">");
		final Integer vectorLength = column.getSpecifics().get(VECTOR_LENGTH, Integer.class);
		if (vectorLength != null) {
			if (column.getDataType() != DataType.REAL && column.getDataType() != DataType.DOUBLE) {
				throw new IllegalArgumentException(
						"Cloud Spanner vector_length requires a FLOAT32 " + "or FLOAT64 array: " + column.getName());
			}
			if (vectorLength.intValue() < 0) {
				throw new IllegalArgumentException(
						"Cloud Spanner vector_length must not be negative: " + column.getName());
			}
			space()._add("(")._add("vector_length=>")._add(vectorLength)._add(")");
		}
		return this;
	}

	private static boolean isNumeric(final Column column) {
		return column.getDataType() == DataType.NUMERIC || column.getDataType() == DataType.DECIMAL;
	}

	@Override
	protected void onUpdateDefinition(final Column column) {
		space().on().space()._add("UPDATE").space()._add("(")._add(column.getOnUpdate())._add(")");
	}

	@Override
	protected SpannerSqlBuilder autoIncrement(final AbstractColumn<?> column) {
		if (column.getDataType() != DataType.BIGINT) {
			throw new IllegalArgumentException("Cloud Spanner identity requires an INT64 column: " + column.getName());
		}
		final IdentityGenerationType generationType = column.getIdentityGenerationType() == null
				? IdentityGenerationType.ByDefault
				: column.getIdentityGenerationType();
		space().generated().space()._add(generationType).space().as().space().identity();

		final Boolean bitReversed = column.getSpecifics().get(IDENTITY_BIT_REVERSED_POSITIVE, Boolean.class);
		final Long start = column.getIdentityStartValue();
		final var range = identitySkipRange(column);
		if (start != null && start.longValue() <= 0L) {
			throw new IllegalArgumentException(
					"Cloud Spanner identity start counter must be positive: " + column.getName());
		}
		if (Boolean.TRUE.equals(bitReversed) || start != null || range.minimum() != null) {
			space()._add("(");
			boolean delimiter = false;
			if (Boolean.TRUE.equals(bitReversed)) {
				_add("BIT_REVERSED_POSITIVE");
				delimiter = true;
			}
			if (start != null) {
				space(delimiter)._add("START COUNTER WITH").space()._add(start);
				delimiter = true;
			}
			if (range.minimum() != null) {
				space(delimiter);
				appendIdentitySkipRange(range);
			}
			space()._add(")");
		}
		return this;
	}

	public SpannerSqlBuilder alterIdentitySkipRange(final Column column) {
		final var range = identitySkipRange(column);
		space()._add("ALTER IDENTITY SET").space();
		if (range.minimum() == null)
			return _add("NO SKIP RANGE");
		return appendIdentitySkipRange(range);
	}

	private SpannerSqlBuilder appendIdentitySkipRange(final IdentitySkipRange range) {
		return _add("SKIP RANGE").space()._add(range.minimum())._add(",").space()._add(range.maximum());
	}

	private static IdentitySkipRange identitySkipRange(final AbstractColumn<?> column) {
		final Long minimum = column.getSpecifics().get(IDENTITY_SKIP_RANGE_MIN, Long.class);
		final Long maximum = column.getSpecifics().get(IDENTITY_SKIP_RANGE_MAX, Long.class);
		if ((minimum == null) != (maximum == null))
			throw new IllegalArgumentException("Cloud Spanner identity skip range requires both "
					+ "minimum and maximum values: " + column.getName());
		if (minimum != null && minimum > maximum)
			throw new IllegalArgumentException(
					"Cloud Spanner identity skip range minimum must not exceed maximum: " + column.getName());
		return new IdentitySkipRange(minimum, maximum);
	}

	private record IdentitySkipRange(Long minimum, Long maximum) {
	}

	@Override
	public SpannerSqlBuilder clone() {
		return (SpannerSqlBuilder) super.clone();
	}

}
