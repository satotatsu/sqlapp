/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql.sql;

import com.sqlapp.data.db.dialect.hsql.util.HsqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractSequenceNextValuesFactory;
import com.sqlapp.data.schemas.Sequence;

public class Hsql2SequenceNextValuesFactory extends AbstractSequenceNextValuesFactory<HsqlSqlBuilder> {

	@Override
	protected void addSequenceNextValues(Sequence obj, HsqlSqlBuilder builder) {
		builder.select().next().value().for_();
		builder.name(obj);
		builder.lineBreak();
		builder.from().unnest().brackets(() -> {
			builder.sequenceArray().brackets(() -> {
				builder._add(" ");
				builder._add(1);
				builder.comma();
				builder._add(getColumnParameterExpression(getCountParameterName(obj), "1"));
				builder.comma();
				builder._add(1);
			});
		});
	}
}
