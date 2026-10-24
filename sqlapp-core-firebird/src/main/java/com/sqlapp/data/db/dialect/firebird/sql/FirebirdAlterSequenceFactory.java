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

import java.math.BigInteger;
import java.util.*;

import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterSequenceFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.util.CommonUtils;

public class FirebirdAlterSequenceFactory extends AbstractAlterSequenceFactory<FirebirdSqlBuilder> {

	@Override
	protected void addCreateObject(Sequence obj, FirebirdSqlBuilder builder) {
		BigInteger val = CommonUtils.coalesce(obj.getLastValue(), obj.getStartValue());
		if (val == null)
			throw new IllegalArgumentException("Sequence ALTER requires an explicit current value (lastValue)");
		validateValue(val);
		builder.set().generator().space().name(obj).space().to().space()._add(val);
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Sequence target = FirebirdAlterSql.target(difference, Sequence.class,
				Set.of("lastValue", "incrementBy", "remarks"));
		var changed = difference.getChangedProperties();
		List<SqlOperation> result = new ArrayList<>();
		if (changed.containsKey("incrementBy")) {
			if (getDialect().compareTo(DialectHolder.defaultDialect30) < 0)
				throw new IllegalArgumentException("Sequence increment ALTER requires Firebird 3.0 or later");
			BigInteger increment = target.getIncrementBy();
			if (increment == null || increment.signum() == 0)
				throw new IllegalArgumentException("Sequence increment must be an explicit non-zero integer");
			validateValue(increment);
			addSql(result, createSqlBuilder(getDialect())._add("ALTER SEQUENCE ").name(target.getName())
					._add(" INCREMENT BY ")._add(increment), SqlType.ALTER, target);
		}
		if (changed.containsKey("lastValue")) {
			if (target.getLastValue() == null)
				throw new IllegalArgumentException("Sequence current value cannot be cleared");
			// SET GENERATOR means the current value on every supported version.
			// RESTART WITH means the next value on 4.0+, unlike earlier releases.
			result.addAll(createSql(target));
		}
		if (changed.containsKey("remarks"))
			result.addAll(getSqlFactoryRegistry().createSql(target, SqlType.SET_COMMENT));
		return result;
	}

	private static void validateValue(BigInteger value) {
		if (value.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0
				|| value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0)
			throw new IllegalArgumentException("Firebird sequence values must fit a signed 64-bit integer");
	}

}
