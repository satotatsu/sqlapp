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

import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterTableFactory;
import com.sqlapp.data.db.sql.AddObjectDetail;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.Table;

/**
 * SQLServer2005用alterテーブル作成
 * 
 * @author tatsuo satoh
 * 
 */
public class SqlServer2005AlterTableFactory extends
		AbstractAlterTableFactory<SqlServerSqlBuilder> {

	/**
	 * 制約定義を追加します
	 * 
	 * @param originalTable
	 * @param table
	 * @param consDiff
	 * @param result
	 */
	@Override
	protected void addConstraintDefinitions(Map<String, Difference<?>> allDiff
			, Table originalTable, Table table,
			List<DbObjectDifference> consDiff, List<SqlOperation> result) {
		Difference<?> tableProp = allDiff.get(SchemaObjectProperties.PARTITIONING.getLabel());
		if (tableProp == null) {
			for (DbObjectDifference diff : consDiff) {
				Constraint originalConstraint = diff.getOriginal(Constraint.class);
				Constraint constraint = diff.getTarget(Constraint.class);
				addConstraintDefinition(originalTable, table, originalConstraint, constraint, diff, result);
			}
		} else{
			
		}
	}

	/**
	 * Partition定義を追加します
	 * 
	 * @param partitionInfoProp
	 * @param sqlBuilder
	 */
	@Override
	protected void addPartitionDefinition(Map<String, Difference<?>> allDiff
			, Table originalTable, Table table
			, DbObjectDifference partitioningProp
			, List<SqlOperation> result) {
		SqlServerSqlBuilder builder = createSqlBuilder();
		builder.alter().table().space().name(table, this.getOptions().isDecorateSchemaName());
		AddObjectDetail<Partitioning,SqlServerSqlBuilder> addObjectDetail=this.getAddObjectDetail(table.getPartitioning(), SqlType.CREATE);
		addObjectDetail.addObjectDetail(table.getPartitioning(), builder);
	}
}
