/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateDimensionFactory;
import com.sqlapp.data.schemas.Dimension;
import com.sqlapp.data.schemas.DimensionLevel;
import com.sqlapp.data.schemas.DimensionLevelColumn;

public class OracleCreateDimensionFactory extends
		AbstractCreateDimensionFactory<OracleSqlBuilder> {

	@Override
	protected void addCreateObject(final Dimension obj, OracleSqlBuilder builder) {
		builder.create().space();
		builder._add(obj.getClass().getSimpleName().toUpperCase());
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		builder.appendIndent(+1);
		for (DimensionLevel level : obj.getLevels()) {
			builder.lineBreak();
			builder.level().space().name(level)
			.space().is().space()._add("(");
			boolean first = true;
			for (DimensionLevelColumn column : level.getColumns()) {
				if (!first) {
					builder._add(", ");
				}
				builder._add(column.getName());
			}
			builder._add(")");
		}
		builder.appendIndent(-1);
	}
}
