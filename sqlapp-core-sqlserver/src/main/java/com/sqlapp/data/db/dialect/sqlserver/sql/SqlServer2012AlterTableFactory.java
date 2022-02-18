/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.sqlserver.sql;

import java.util.List;
import java.util.Map;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.Table;

/**
 * SQLServer2008 alter table
 * 
 * @author tatsuo satoh
 * 
 */
public class SqlServer2012AlterTableFactory extends	SqlServer2008AlterTableFactory {

	@Override
	protected void addOtherDefinitions(Map<String, Difference<?>> allDiff
			, Table originalTable, Table table, List<SqlOperation> result){
		super.addOtherDefinitions(allDiff
				, originalTable, table, result);
		Difference<?> diff=allDiff.get("HAS_CHANGE_TRACKING");
		if (diff!=null&&diff.getState().isChanged()) {
			Boolean val = Converters.getDefault().convertObject(
					diff.getTarget(), Boolean.class);
			SqlServerSqlBuilder builder = createSqlBuilder();
			builder.alter().table().name(originalTable, this.getOptions().isDecorateSchemaName());
			builder.lineBreak();
			if (val.booleanValue()) {
				builder.enable().changeTracking();
			} else {
				builder.disable().changeTracking();
			}
		}
	}
}
