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

package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractSelectForAppFactory;
import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.data.schemas.Table;

/**
 * SELECT for applications with SAP HANA paging.
 * <p>
 * SAP HANA uses {@code LIMIT <rows> [OFFSET <offset>]} and has no OFFSET-only
 * form, so an offset without a row count uses the largest INTEGER limit. Paging
 * is not applied to the COUNT query.
 */
public class SapHanaSelectForAppFactory extends AbstractSelectForAppFactory<SapHanaSqlBuilder> {

	static final String UNBOUNDED_LIMIT = Integer.toString(Integer.MAX_VALUE);

	@Override
	protected void addOffsetRowsOnly(final Table table, final SapHanaSqlBuilder builder) {
		final String rows = toIsNotEmptyExpression(ParameterDefinition.ROW_KEY_PARANETER_NAME);
		final String offset = toIsNotEmptyExpression(ParameterDefinition.OFFSET_KEY_PARANETER_NAME);
		final String dataQuery = "!" + ParameterDefinition.COUNTSQL_KEY_PARANETER_NAME;
		builder.lineBreak()._add(toIfExpression(dataQuery + " && " + rows));
		builder.lineBreak()._add("LIMIT /*" + ParameterDefinition.ROW_KEY_PARANETER_NAME + "*/1");
		builder.lineBreak()._add(getEndIfExpression());
		builder.lineBreak()._add(toIfExpression(dataQuery + " && !(" + rows + ") && " + offset));
		builder.lineBreak()._add("LIMIT " + UNBOUNDED_LIMIT);
		builder.lineBreak()._add(getEndIfExpression());
		builder.lineBreak()._add(toIfExpression(dataQuery + " && " + offset));
		builder.lineBreak()._add("OFFSET /*" + ParameterDefinition.OFFSET_KEY_PARANETER_NAME + "*/0");
		builder.lineBreak()._add(getEndIfExpression());
	}
}
