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

import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterTriggerFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.util.CommonUtils;

/**
 * Oracle alter trigger factory
 * 
 * @author tatsuo satoh
 * 
 */
public class OracleAlterTriggerFactory extends AbstractAlterTriggerFactory<OracleSqlBuilder> {

	@Override
	protected List<SqlOperation> doCreateDiffSql(DbObjectDifference difference, Map<String, Difference<?>> allDiff){
		Difference<?> nameDiff=allDiff.get(SchemaProperties.NAME.getLabel());
		Difference<?> enableDiff=allDiff.get(SchemaProperties.ENABLE.getLabel());
		if (nameDiff==null&&enableDiff==null){
			return super.doCreateDiffSql(difference, allDiff);
		}
		long count=allDiff.entrySet().stream().filter(e->{
			if(SchemaProperties.NAME.getLabel().equals(e.getKey())){
				return false;
			}
			if(SchemaProperties.SPECIFIC_NAME.getLabel().equals(e.getKey())){
				return false;
			}
			if(SchemaProperties.ENABLE.getLabel().equals(e.getKey())){
				return false;
			}
			return true;
		}).count();
		List<SqlOperation> list=CommonUtils.list();
		if (count==0){
			SqlOperation operation=createAlterSql(difference, allDiff, nameDiff, enableDiff);
			list.add(operation);
		} else{
			return super.doCreateDiffSql(difference, allDiff);
		}
		return list;
	}

	protected SqlOperation createAlterSql(DbObjectDifference difference, Map<String, Difference<?>> allDiff, Difference<?> nameDiff, Difference<?> enableDiff){
		Trigger original=difference.getOriginal(Trigger.class);
		Trigger target=difference.getTarget(Trigger.class);
		OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().trigger().space();
		if (nameDiff!=null){
			if (this.getOptions().isDecorateSchemaName()){
				builder.name(target.getSchemaName());
				builder._add(".");
			}
			builder.name(original, false);
		} else{
			builder.name(target, this.getOptions().isDecorateSchemaName());
		}
		if (enableDiff!=null){
			Boolean bool=enableDiff.getTarget(Boolean.class);
			if (bool!=null){
				if (bool.booleanValue()){
					builder.enable();
				} else{
					builder.disable();
				}
			}
		}
		if (nameDiff!=null){
			builder.rename().to().name(target, false);
		}
		return createOperation(builder.toString(),SqlType.ALTER, original, target);
	}
}
