/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractSequenceNextValuesGenerateSeriesFactory;
import com.sqlapp.data.schemas.Sequence;

public class SapHanaSequenceNextValuesFactory
		extends AbstractSequenceNextValuesGenerateSeriesFactory<SapHanaSqlBuilder> {

	@Override
	protected void addNextValueFor(final Sequence obj, final SapHanaSqlBuilder builder) {
		builder.name(obj)._add(".NEXTVAL");
	}

	@Override
	protected void addGenerateSeries(final Sequence obj, final SapHanaSqlBuilder builder) {
		builder.lineBreak();
		builder.from()._add(" SERIES_GENERATE_INTEGER").brackets(() -> {
			builder._add(" ");
			builder._add(1);
			builder.comma();
			builder._add(getColumnParameterExpression(getCountParameterName(obj), "1"));
		});
	}
}
