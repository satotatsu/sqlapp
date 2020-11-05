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

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterPartitionFunctionFactory;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.PartitionFunction;
import com.sqlapp.data.schemas.State;
import com.sqlapp.util.CommonUtils;

/**
 * SQLServer2005 Alter Partition Function
 * 
 * @author tatsuo satoh
 * 
 */
public class SqlServer2005AlterPartitionFunctionFactory extends
		AbstractAlterPartitionFunctionFactory<SqlServerSqlBuilder> {


	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference obj){
		if(!obj.getState().isChanged()){
			return Collections.emptyList();
		}
		if (obj.getState()==State.Added){
			return createSql((PartitionFunction)obj.getTarget());
		}
		if (obj.getState()==State.Deleted){
			//TODO
			return Collections.emptyList();
		}
		PartitionFunction original=obj.getOriginal(PartitionFunction.class);
		PartitionFunction target=obj.getTarget(PartitionFunction.class);
		Set<String> deleted=CommonUtils.linkedSet();
		Set<String> added=CommonUtils.linkedSet();
		for(String val:original.getValues()){
			if (!target.getValues().contains(val)){
				deleted.add(val);
			}
		}
		for(String val:target.getValues()){
			if (!original.getValues().contains(val)){
				added.add(val);
			}
		}
		List<SqlOperation> sqlList=CommonUtils.list();
		for(String val:deleted){
			SqlServerSqlBuilder builder=this.newSqlBuilder(this.getDialect());
			builder.alter().partition().function().space().name(original)._add("()");
			builder.lineBreak().merge().range().space()._add("(")._add(val)._add(")");
			SqlOperation sqlOperation=this.createOperation(builder.toString(), SqlType.ALTER, original, target);
			this.addSql(sqlList, sqlOperation);
		}
		for(String val:added){
			SqlServerSqlBuilder builder=this.newSqlBuilder(this.getDialect());
			builder.alter().partition().function().space().name(original)._add("()");
			builder.lineBreak().split().range().space()._add("(")._add(val)._add(")");
			SqlOperation sqlOperation=this.createOperation(builder.toString(), SqlType.ALTER, original, target);
			this.addSql(sqlList, sqlOperation);
		}
		return sqlList;
	}
	
	@Override
	public List<SqlOperation> createSql(PartitionFunction obj) {
		SqlFactory<PartitionFunction> sqlFactory=this.getSqlFactoryRegistry().getSqlFactory(obj, SqlType.CREATE);
		if (sqlFactory!=null){
			return sqlFactory.createSql(obj);
		}
		return Collections.emptyList();
	}
}
