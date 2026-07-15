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

public abstract class AbstractSequenceNextValuesGenerateSeriesFactory<S extends AbstractSqlBuilder<?>>
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

	protected void addSequenceNextValues(Sequence obj, S builder) {
		builder.select();
		addNextValueFor(obj, builder);
		addGenerateSeries(obj, builder);
	}

	protected void addNextValueFor(final Sequence obj, final S builder) {
		builder.next().value().for_().name(obj);
	}

	protected void addGenerateSeries(final Sequence obj, final S builder) {
		builder.lineBreak();
		builder.from().generateSeries().brackets(() -> {
			builder._add(" ");
			builder._add(1);
			builder.comma();
			builder._add(getColumnParameterExpression(getCountParameterName(obj), "1"));
		});
	}

}
