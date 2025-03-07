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

import java.util.List;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSequenceFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Sequence;

public class Firebird20CreateSequenceFactory extends AbstractCreateSequenceFactory<FirebirdSqlBuilder> {

	@Override
	public List<SqlOperation> createSql(final Sequence obj) {
		List<SqlOperation> sqlList = super.createSql(obj);
		return sqlList;
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
