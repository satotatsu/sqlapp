/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateOperatorFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Operator;
import com.sqlapp.util.CommonUtils;

/**
 * CREATE OPERATOR
 * 
 * 
 */
public class PostgresCreateOperatorFactory extends
		AbstractCreateOperatorFactory<PostgresSqlBuilder> {

	@Override
	public List<SqlOperation> createSql(Operator obj) {
		List<SqlOperation> sqlList = list();
		PostgresSqlBuilder builder = createSqlBuilder();
		addCreateObject(obj, builder);
		addSql(sqlList, builder, SqlType.CREATE, obj);
		return sqlList;
	}

	protected void addCreateObject(final Operator obj,
			PostgresSqlBuilder builder) {
		builder.create().or().replace().operator();
		builder.name(obj).space()._add("(");
		builder.appendIndent(1);
		if (obj.getFunctionName()!=null){
			builder.lineBreak();
			builder.procedure().eq()._add(obj.getFunctionName());
		}
		if (obj.getLeftArgument()!=null){
			builder.lineBreak();
			builder.leftarg().eq();
			builder._add(obj.getLeftArgument().getDataTypeName());
		}
		if (obj.getRightArgument()!=null){
			builder.lineBreak();
			builder.rightarg().eq();
			builder._add(obj.getRightArgument().getDataTypeName());
		}
		if (obj.getCommutativeOperator()!=null){
			builder.lineBreak();
			builder.commutator().eq();
			if (!CommonUtils.eq(obj.getSchemaName(), obj.getCommutativeOperator().getSchemaName())&&!CommonUtils.isEmpty(obj.getCommutativeOperator().getSchemaName())){
				builder.operator()._add("("+obj.getCommutativeOperator().getSchemaName()+".");
				builder._add(obj.getCommutativeOperatorName()+")");
			} else{
				builder._add(obj.getCommutativeOperatorName());
			}
		}
		if (obj.getNegationOperator()!=null){
			builder.lineBreak();
			builder.negator().eq();
			if (!CommonUtils.eq(obj.getSchemaName(), obj.getNegationOperator().getSchemaName())&&!CommonUtils.isEmpty(obj.getNegationOperator().getSchemaName())){
				builder.operator()._add("("+obj.getNegationOperator().getSchemaName()+".");
				builder._add(obj.getNegationOperatorName()+")");
			} else{
				builder._add(obj.getNegationOperatorName());
			}
		}
		if (obj.getRestrictFunction()!=null){
			builder.lineBreak();
			builder.restrict().eq();
			if (!CommonUtils.eq(obj.getSchemaName(), obj.getRestrictFunction().getSchemaName())&&!CommonUtils.isEmpty(obj.getRestrictFunction().getSchemaName())){
				builder._add(obj.getRestrictFunction().getSchemaName()+".");
			}
			builder._add(obj.getRestrictFunctionName());
		}
		if (obj.getJoinFunction()!=null){
			builder.lineBreak();
			builder.join().eq();
			if (!CommonUtils.eq(obj.getSchemaName(), obj.getJoinFunction().getSchemaName())&&!CommonUtils.isEmpty(obj.getJoinFunction().getSchemaName())){
				builder._add(obj.getJoinFunction().getSchemaName()+".");
			}
			builder._add(obj.getJoinFunctionName());
		}
		if (obj.isHashes()){
			builder.lineBreak();
			builder.hashes();
		}
		if (obj.isMerges()){
			builder.lineBreak();
			builder.merges();
		}
		builder.appendIndent(-1);
		builder.lineBreak();
		builder._add(")");
	}

}
