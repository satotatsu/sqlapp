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
	public SapHanaSqlBuilder clone(){
		return (SapHanaSqlBuilder)super.clone();
	}

	@Override
	protected SapHanaSqlBuilder typeDefinition(final Column column) {
		if (column.getDataType() != DataType.VECTOR) {
			return super.typeDefinition(column);
		}
		if (column.getVectorElementDataType() != null
				&& column.getVectorElementDataType() != DataType.REAL) {
			throw new IllegalArgumentException(
					"SAP HANA REAL_VECTOR requires REAL elements: "
					+ column.getName());
		}
		final Integer dimension = column.getVectorDimension();
		if (dimension != null
				&& (dimension.intValue() <= 0
						|| dimension.intValue() > 65000)) {
			throw new IllegalArgumentException(
					"SAP HANA REAL_VECTOR dimension must be between 1 "
					+ "and 65000: " + column.getName());
		}
		_add("REAL_VECTOR");
		if (dimension != null) {
			brackets(() -> _add(dimension));
		}
		return instance();
	}

	public SapHanaSqlBuilder cosineSimilarity(final CharSequence left,
			final CharSequence right) {
		return binaryVectorFunction("COSINE_SIMILARITY", left, right);
	}

	public SapHanaSqlBuilder l2Distance(final CharSequence left,
			final CharSequence right) {
		return binaryVectorFunction("L2DISTANCE", left, right);
	}

	public SapHanaSqlBuilder toRealVector(final CharSequence expression) {
		_add("TO_REAL_VECTOR")._add("(")._add(expression.toString())
				._add(")");
		return instance();
	}

	private SapHanaSqlBuilder binaryVectorFunction(final String function,
			final CharSequence left, final CharSequence right) {
		_add(function)._add("(")._add(left.toString()).comma()
				._add(right.toString())._add(")");
		return instance();
	}

}
