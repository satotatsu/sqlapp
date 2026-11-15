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

import java.util.List;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractMergeFactory;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * SAP HANA MERGE INTO factory.
 * <p>
 * SAP HANA rejects a derived column list on the USING subquery
 * ({@code AS _source_ (A, B)}, SQL error 257), so each source value is named
 * with a column alias instead. GENERATED ALWAYS AS columns are never inserted
 * or updated and get a NULL placeholder.
 */
public class SapHanaMergeFactory extends AbstractMergeFactory<SapHanaSqlBuilder> {

	@Override
	protected void addUsingColumnNameAlias(final Column column, final SapHanaSqlBuilder builder) {
		builder.as().name(column);
	}

	@Override
	protected void addUsingSourceColumns(final Table obj, final List<Column> columns,
			final SapHanaSqlBuilder builder) {
		// Column aliases are already part of the USING subquery.
	}

	@Override
	protected String getValueDefinitionForMerge(final Column column) {
		if (!CommonUtils.isEmpty(column.getFormula())) {
			return "NULL";
		}
		return super.getValueDefinitionForMerge(column);
	}
}
