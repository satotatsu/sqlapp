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

	public static final String ALLOW_COMMIT_TIMESTAMP =
			"ALLOW_COMMIT_TIMESTAMP";

	public static final String IDENTITY_BIT_REVERSED_POSITIVE =
			"IDENTITY_BIT_REVERSED_POSITIVE";

	public static final String IDENTITY_SKIP_RANGE_MIN =
			"IDENTITY_SKIP_RANGE_MIN";

	public static final String IDENTITY_SKIP_RANGE_MAX =
			"IDENTITY_SKIP_RANGE_MAX";

	public static final String VECTOR_LENGTH = "VECTOR_LENGTH";

	public SpannerSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public SpannerSqlBuilder definition(final Column column,
			final boolean withRemarks) {
		super.definition(column, withRemarks);
		if (!CommonUtils.isEmpty(column.getFormula())) {
			space().as().space()._add("(")._add(column.getFormula())
					._add(")");
			if (column.isFormulaPersisted()) {
				space()._add("STORED");
			}
		}
		if (column.isHidden()) {
			space()._add("HIDDEN");
		}
		final Boolean allowCommitTimestamp = column.getSpecifics().get(
				ALLOW_COMMIT_TIMESTAMP, Boolean.class);
		if (Boolean.TRUE.equals(allowCommitTimestamp)) {
			if (column.getDataType() != DataType.TIMESTAMP) {
				throw new IllegalArgumentException(
						"Cloud Spanner allow_commit_timestamp requires "
								+ "a TIMESTAMP column: " + column.getName());
			}
			space()._add("OPTIONS").space()._add("(")
					._add("allow_commit_timestamp=true")
					._add(")");
		}
		return this;
	}

	@Override
	protected SpannerSqlBuilder typeDefinition(final Column column) {
		if (column.getArrayDimension() == 0) {
			return super.typeDefinition(column);
		}
		if (column.getArrayDimension() != 1) {
			throw new IllegalArgumentException(
					"Cloud Spanner supports only one-dimensional arrays: "
							+ column.getName());
		}
		_add("ARRAY<");
		super.typeDefinition(column);
		_add(">");
		final Integer vectorLength = column.getSpecifics().get(
				VECTOR_LENGTH, Integer.class);
		if (vectorLength != null) {
			if (column.getDataType() != DataType.REAL
					&& column.getDataType() != DataType.DOUBLE) {
				throw new IllegalArgumentException(
						"Cloud Spanner vector_length requires a FLOAT32 "
								+ "or FLOAT64 array: " + column.getName());
			}
			if (vectorLength.intValue() < 0) {
				throw new IllegalArgumentException(
						"Cloud Spanner vector_length must not be negative: "
								+ column.getName());
			}
			space()._add("(")._add("vector_length=>")
					._add(vectorLength)._add(")");
		}
		return this;
	}

	@Override
	protected void onUpdateDefinition(final Column column) {
		space().on().space()._add("UPDATE").space()._add("(")
				._add(column.getOnUpdate())._add(")");
	}

	@Override
	protected SpannerSqlBuilder autoIncrement(
			final AbstractColumn<?> column) {
		if (column.getDataType() != DataType.BIGINT) {
			throw new IllegalArgumentException(
					"Cloud Spanner identity requires an INT64 column: "
							+ column.getName());
		}
		final IdentityGenerationType generationType =
				column.getIdentityGenerationType() == null
						? IdentityGenerationType.ByDefault
						: column.getIdentityGenerationType();
		space().generated().space()._add(generationType).space().as()
				.space().identity();

		final Boolean bitReversed = column.getSpecifics().get(
				IDENTITY_BIT_REVERSED_POSITIVE, Boolean.class);
		final Long start = column.getIdentityStartValue();
		final Long skipMin = column.getSpecifics().get(
				IDENTITY_SKIP_RANGE_MIN, Long.class);
		final Long skipMax = column.getSpecifics().get(
				IDENTITY_SKIP_RANGE_MAX, Long.class);
		if ((skipMin == null) != (skipMax == null)) {
			throw new IllegalArgumentException(
					"Cloud Spanner identity skip range requires both "
							+ "minimum and maximum values: "
							+ column.getName());
		}
		if (skipMin != null && skipMin.longValue() > skipMax.longValue()) {
			throw new IllegalArgumentException(
					"Cloud Spanner identity skip range minimum must not "
							+ "exceed maximum: " + column.getName());
		}
		if (start != null && start.longValue() <= 0L) {
			throw new IllegalArgumentException(
					"Cloud Spanner identity start counter must be positive: "
							+ column.getName());
		}
		if (Boolean.TRUE.equals(bitReversed) || start != null
				|| skipMin != null) {
			space()._add("(");
			boolean delimiter = false;
			if (Boolean.TRUE.equals(bitReversed)) {
				_add("BIT_REVERSED_POSITIVE");
				delimiter = true;
			}
			if (start != null) {
				space(delimiter)._add("START COUNTER WITH").space()
						._add(start);
				delimiter = true;
			}
			if (skipMin != null) {
				space(delimiter)._add("SKIP RANGE").space()._add(skipMin)
						._add(",").space()._add(skipMax);
			}
			space()._add(")");
		}
		return this;
	}

	
	@Override
	public SpannerSqlBuilder clone(){
		return (SpannerSqlBuilder)super.clone();
	}
	
}
