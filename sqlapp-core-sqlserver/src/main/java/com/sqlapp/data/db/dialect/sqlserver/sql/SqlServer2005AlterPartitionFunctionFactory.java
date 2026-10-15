/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
public class SqlServer2005AlterPartitionFunctionFactory
		extends AbstractAlterPartitionFunctionFactory<SqlServerSqlBuilder> {

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference obj) {
		if (!obj.getState().isChanged()) {
			return Collections.emptyList();
		}
		if (obj.getState() == State.Added) {
			return createSql((PartitionFunction) obj.getTarget());
		}
		if (obj.getState() == State.Deleted) {
			PartitionFunction original = obj.getOriginal(PartitionFunction.class);
			return requiredFactory(original, SqlType.DROP).createSql(original);
		}
		PartitionFunction original = obj.getOriginal(PartitionFunction.class);
		PartitionFunction target = obj.getTarget(PartitionFunction.class);
		PartitionFunction normalized = target.clone();
		normalized.getValues().clear();
		normalized.getValues().addAll(original.getValues());
		if (!normalized.equals(original)) {
			throw new IllegalArgumentException(
					"SQL Server ALTER PARTITION FUNCTION supports boundary value changes only; "
							+ "migrate dependent schemes and tables explicitly before changing its name, type or RANGE LEFT/RIGHT: "
							+ original.getName());
		}
		Set<String> deleted = CommonUtils.linkedSet();
		Set<String> added = CommonUtils.linkedSet();
		for (String val : original.getValues()) {
			if (!target.getValues().contains(val)) {
				deleted.add(val);
			}
		}
		for (String val : target.getValues()) {
			if (!original.getValues().contains(val)) {
				added.add(val);
			}
		}
		List<SqlOperation> sqlList = CommonUtils.list();
		for (String val : deleted) {
			SqlServerSqlBuilder builder = this.newSqlBuilder(this.getDialect());
			builder.alter().partition().function().space().name(original)._add("()");
			builder.lineBreak().merge().range().space()._add("(")._add(val)._add(")");
			SqlOperation sqlOperation = this.createOperation(builder.toString(), SqlType.ALTER, original, target);
			this.addSql(sqlList, sqlOperation);
		}
		for (String val : added) {
			SqlServerSqlBuilder builder = this.newSqlBuilder(this.getDialect());
			builder.alter().partition().function().space().name(original)._add("()");
			builder.lineBreak().split().range().space()._add("(")._add(val)._add(")");
			SqlOperation sqlOperation = this.createOperation(builder.toString(), SqlType.ALTER, original, target);
			this.addSql(sqlList, sqlOperation);
		}
		return sqlList;
	}

	@Override
	public List<SqlOperation> createSql(PartitionFunction obj) {
		return requiredFactory(obj, SqlType.CREATE).createSql(obj);
	}

	private SqlFactory<PartitionFunction> requiredFactory(PartitionFunction object, SqlType type) {
		SqlFactory<PartitionFunction> factory = getSqlFactoryRegistry().getSqlFactory(object, type);
		if (factory == null)
			throw new IllegalStateException("No SQL Server partition function " + type + " factory is registered");
		return factory;
	}
}
