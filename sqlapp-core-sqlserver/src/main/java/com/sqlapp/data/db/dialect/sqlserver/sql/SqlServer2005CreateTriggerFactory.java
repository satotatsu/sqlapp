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

import static com.sqlapp.util.CommonUtils.isEmpty;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTriggerFactory;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SeparatedStringBuilder;

public class SqlServer2005CreateTriggerFactory extends
		AbstractCreateTriggerFactory<SqlServerSqlBuilder> {

	@Override
	protected void addCreateObject(final Trigger obj, SqlServerSqlBuilder builder) {
		if (!isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
		} else {
			builder.create().trigger();
			builder.name(obj, this.getOptions().isDecorateSchemaName());
			addCreateObjectDetail(obj, builder);
		}
	}

	protected void addCreateObjectDetail(final Trigger obj, SqlServerSqlBuilder builder) {
		builder.on();
		if (!CommonUtils.eq(obj.getSchemaName(), obj.getTableSchemaName())) {
			builder.name(obj.getTableSchemaName());
			builder._add(".");
		}
		builder.name(obj.getTableName());
		addEventManipulationText(obj, builder);
		String val=obj.getSpecifics().get("is_not_for_replication");
		Boolean bool=Converters.getDefault().convertObject(val, Boolean.class);
		if (bool!=null&&bool.booleanValue()){
			builder.lineBreak();
			builder.not()._for().replication();
		}
		builder.lineBreak();
		addCreateTriggerBody(obj, builder);
	}

	protected void addEventManipulationText(final Trigger obj, SqlServerSqlBuilder builder) {
		SeparatedStringBuilder sepBuilder = new SeparatedStringBuilder(" , ");
		sepBuilder.add(obj.getEventManipulation());
		if (obj.getActionTiming()!=null){
			builder.lineBreak();
			builder._add(obj.getActionTiming());
		}
		if (!CommonUtils.isEmpty(obj.getEventManipulation())){
			builder.lineBreak();
			builder._add(sepBuilder.toString());
		}
	}

	protected void addCreateTriggerBody(final Trigger obj, SqlServerSqlBuilder builder) {
		builder.lineBreak();
		builder.forEach().row();
		builder.lineBreak();
		builder._add(toString(obj.getStatement()));
	}

}
