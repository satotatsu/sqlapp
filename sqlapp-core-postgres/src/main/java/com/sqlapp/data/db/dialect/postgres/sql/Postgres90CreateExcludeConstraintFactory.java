/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateExcludeConstraintFactory;
import com.sqlapp.data.schemas.ExcludeConstraint;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;

/**
 * Exclude CONSTRAINT生成クラス
 * 
 * @author satoh
 * 
 */
public class Postgres90CreateExcludeConstraintFactory
		extends AbstractCreateExcludeConstraintFactory<PostgresSqlBuilder>{

	@Override
	public void addObjectDetail(final ExcludeConstraint obj, Table table, PostgresSqlBuilder builder) {
		builder.constraint().space();
		if (table!=null){
			builder.name(obj, this.getOptions().isDecorateSchemaName());
		} else{
			builder.name(obj, false);
		}
		builder.exclude();
		builder.using().gist();
		builder.space()._add('(');
		boolean first=true;
		for(ReferenceColumn column:obj.getColumns()){
			builder.comma(!first);
			builder.name(column);
			builder.with().space()._add(column.getWith());
			first=false;
		}
		builder.space()._add(')');
	}

}
