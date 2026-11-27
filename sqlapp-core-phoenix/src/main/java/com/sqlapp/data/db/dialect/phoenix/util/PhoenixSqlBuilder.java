/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-phoenix.
 *
 * sqlapp-core-phoenix is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-phoenix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-phoenix.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.phoenix.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.db.dialect.phoenix.Phoenix;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Poenix SQL Builder
 * 
 * @author tatsuo satoh
 * 
 */
public class PhoenixSqlBuilder extends AbstractSqlBuilder<PhoenixSqlBuilder> {

	public PhoenixSqlBuilder(Dialect dialect) {
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
	public PhoenixSqlBuilder count() {
		appendElement("COUNT");
		return instance();
	}

	@Override
	protected PhoenixSqlBuilder typeDefinition(final Column column) {
		if (column.getArrayDimension() < 0 || column.getArrayDimension() > 1)
			throw new IllegalArgumentException("Phoenix supports only one-dimensional arrays: " + column.getName());
		if (column.getArrayDimensionUpperBound() < 0 || (column.getArrayDimension() == 1
				&& column.getArrayDimensionLowerBound() != 0 && column.getArrayDimensionLowerBound() != 1))
			throw new IllegalArgumentException(
					"Phoenix arrays require a nonnegative capacity and one-based indexing: " + column.getName());
		final String nativeType = column.getDataTypeName();
		if (nativeType != null && java.util.Set
				.of("UNSIGNED_FLOAT", "UNSIGNED_DOUBLE", "UNSIGNED_DATE", "UNSIGNED_TIME", "UNSIGNED_TIMESTAMP")
				.contains(nativeType.toUpperCase(java.util.Locale.ROOT))) {
			_add(nativeType.toUpperCase(java.util.Locale.ROOT));
		} else if (column.getDataType() == com.sqlapp.data.db.datatype.DataType.VARCHAR
				&& (column.getLength() == null || column.getLength() == 0)) {
			_add("VARCHAR");
		} else {
			super.typeDefinition(column);
		}
		if (column.getArrayDimension() == 1) {
			if (column.getDataType() == com.sqlapp.data.db.datatype.DataType.VARBINARY)
				throw new IllegalArgumentException("Phoenix does not support VARBINARY ARRAY: " + column.getName());
			space()._add("ARRAY");
			if (column.getArrayDimensionUpperBound() > 0)
				_add("[")._add(column.getArrayDimensionUpperBound())._add("]");
		}
		return instance();
	}

	@Override
	public PhoenixSqlBuilder definition(final Column column, final boolean withRemarks) {
		if (!CommonUtils.isEmpty(column.getOnUpdate()) || !CommonUtils.isEmpty(column.getCheck()))
			throw new UnsupportedOperationException(
					"Phoenix column ON UPDATE and CHECK clauses are unsupported: " + column.getName());
		if (!CommonUtils.isEmpty(column.getDefaultValue())
				&& (!(getDialect() instanceof Phoenix phoenix) || !phoenix.supportsColumnDefault()))
			throw new UnsupportedOperationException("Phoenix column DEFAULT requires version 4.9 or later: "
					+ column.getName() + "; select a versioned dialect using the known server version");
		if (column.isIdentity() || column.getFormula() != null)
			throw new UnsupportedOperationException(
					"Phoenix columns do not support generic IDENTITY or computed formulas; "
							+ "use an explicit sequence or view expression: " + column.getName());
		if (!CommonUtils.isEmpty(column.getDefaultValue())) {
			// Phoenix's column grammar places NULL/NOT NULL before DEFAULT.
			super.definition(column.clone().setDefaultValue(null), false);
			defaultDefinition(column);
			if (withRemarks && !CommonUtils.isEmpty(column.getRemarks()))
				comment(column);
			return this;
		}
		return super.definition(column, withRemarks);
	}

	@Override
	public PhoenixSqlBuilder insert() {
		appendElement("UPSERT");
		return instance();
	}

	@Override
	public PhoenixSqlBuilder clone() {
		return (PhoenixSqlBuilder) super.clone();
	}

}
