/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

public abstract class AbstractSequenceNextValuesFactory<S extends AbstractSqlBuilder<?>>
		extends SimpleSqlFactory<Sequence, S> {

	protected String getCountParameterName(final Sequence obj) {
		return CONTEXT;
	}

	@Override
	public List<SqlOperation> createSql(final Sequence obj) {
		final S builder = createSqlBuilder();
		addSequenceNextValues(obj, builder);
		final List<SqlOperation> sqlList = list();
		addSql(sqlList, builder, SqlType.SEQUENCE_NEXT_VALUES, obj);
		return sqlList;
	}

	private static final String CTE_TABLE_NAME = "_N";

	protected void addSequenceNextValues(final Sequence obj, final S builder) {
		builder.with().space()._add(CTE_TABLE_NAME)._add("(n) ").as().space().brackets(true, () -> {
			builder.select()._add(" 1").as()._add(" n");
			if (!CommonUtils.isEmpty(getDialect().getSelectDummyTableName())) {
				builder.lineBreak();
				builder.from().space()._add(getDialect().getSelectDummyTableName());
			}
			builder.lineBreak();
			builder.union().all();
			builder.lineBreak();
			builder.select()._add(" n + 1");
			builder.lineBreak();
			builder.from().space()._add(CTE_TABLE_NAME);
			builder.lineBreak();
			builder.where()._add(" n < ");
			builder._add(getColumnParameterExpression(getCountParameterName(obj), "1"));
		});
		addSelectNextValueFor(obj, builder);
	}

	protected void addSelectNextValueFor(final Sequence obj, final S builder) {
		builder.lineBreak();
		builder.select();
		addNextValueFor(obj, builder);
		builder.lineBreak();
		builder.from().space()._add(CTE_TABLE_NAME);
		addNextValueForOption(obj, builder);
	}

	protected void addNextValueFor(final Sequence obj, final S builder) {
		builder.next().value().for_().name(obj);
	}

	protected void addNextValueForOption(final Sequence obj, final S builder) {

	}
}
