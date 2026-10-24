/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSequenceFactory;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import java.math.BigInteger;

public class FirebirdCreateSequenceFactory extends AbstractCreateSequenceFactory<FirebirdSqlBuilder> {
	@Override
	protected void addOtherDefinitions(Sequence obj, java.util.List<com.sqlapp.data.db.sql.SqlOperation> result) {
		BigInteger current = obj.getLastValue();
		if (current == null && getDialect().compareTo(DialectHolder.defaultDialect30) < 0)
			current = obj.getStartValue();
		if (current != null)
			addSql(result, createSqlBuilder()._add("SET GENERATOR ").name(obj.getName())._add(" TO ")._add(current),
					SqlType.ALTER, obj);
		if (obj.getRemarks() != null)
			result.addAll(getSqlFactoryRegistry().createSql(obj, com.sqlapp.data.db.sql.SqlType.SET_COMMENT));
	}

	@Override
	protected void addCreateObject(Sequence obj, FirebirdSqlBuilder builder) {
		if (obj.isCycle() || obj.getMinValue() != null || obj.getMaxValue() != null || obj.getCacheSize() != null
				|| obj.isOrder())
			throw new IllegalArgumentException(
					"Firebird sequences do not support CYCLE, MINVALUE, MAXVALUE, CACHE or ORDER options");
		if ((obj.getDataType() != null && obj.getDataType() != DataType.BIGINT)
				|| (obj.getDataTypeName() != null && !"BIGINT".equalsIgnoreCase(obj.getDataTypeName()))
				|| obj.getPrecision() != null || obj.getScale() != null)
			throw new IllegalArgumentException("Firebird sequences always use signed 64-bit integers");
		for (BigInteger value : new BigInteger[] { obj.getStartValue(), obj.getLastValue(), obj.getIncrementBy() }) {
			if (value != null && (value.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0
					|| value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0))
				throw new IllegalArgumentException("Firebird sequence values must fit a signed 64-bit integer");
		}
		if (obj.getIncrementBy() != null) {
			if (obj.getIncrementBy().signum() == 0)
				throw new IllegalArgumentException("Sequence increment must not be zero");
			if (getDialect().compareTo(DialectHolder.defaultDialect30) < 0
					&& !BigInteger.ONE.equals(obj.getIncrementBy()))
				throw new IllegalArgumentException("Sequence increment other than 1 requires Firebird 3.0 or later");
		}
		super.addCreateObject(obj, builder);
	}

	@Override
	protected void addDataType(Sequence obj, FirebirdSqlBuilder builder) {
		// BIGINT is intrinsic; Firebird has no AS type clause for sequences.
	}

	protected void addCreateSequence(final Sequence obj, FirebirdSqlBuilder builder) {
		builder.create().generator();
	}

	@Override
	protected void addStartWith(final Sequence obj, FirebirdSqlBuilder builder) {
	}

	@Override
	protected void addIncrementBy(final Sequence obj, FirebirdSqlBuilder builder) {
	}

	@Override
	protected void addMaxValue(final Sequence obj, FirebirdSqlBuilder builder) {
	}

	@Override
	protected void addMinValue(final Sequence obj, FirebirdSqlBuilder builder) {
	}

	@Override
	protected void addCycle(final Sequence obj, FirebirdSqlBuilder builder) {
	}

	@Override
	protected void addCache(final Sequence obj, FirebirdSqlBuilder builder) {
	}

	@Override
	protected void addOrder(final Sequence obj, FirebirdSqlBuilder builder) {
	}
}
