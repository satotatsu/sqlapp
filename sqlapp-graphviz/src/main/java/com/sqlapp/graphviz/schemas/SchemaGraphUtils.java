/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-graphviz.
 *
 * sqlapp-graphviz is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-graphviz is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-graphviz.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.graphviz.schemas;

import com.sqlapp.data.schemas.AbstractNamedObject;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;

public class SchemaGraphUtils {

	public static String getName(AbstractSchemaObject<?> object){
		StringBuilder builder=new StringBuilder();
		Schema schema=SchemaUtils.getSchema(object);
		if (schema!=null&&schema.getName()!=null){
			builder.append(schema.getName());
			builder.append("_");
		}
		builder.append(object.getName());
		return builder.toString();
	}
	
	public static String getName(AbstractNamedObject<?> object){
		StringBuilder builder=new StringBuilder();
		builder.append(object.getName());
		return builder.toString();
	}

	public static String getName(Partition partition){
		StringBuilder builder=new StringBuilder();
		Table table=partition.getAncestor(Table.class);
		if (table!=null) {
			String tableName=table.getName();
			if (tableName!=null){
				String schemaName=partition.getSchemaName();
				if (schemaName!=null){
					builder.append(schemaName);
					builder.append(".");
				}
				builder.append(tableName);
				builder.append(".");
			}
		}
		builder.append(partition.getName());
		return builder.toString();
	}

	
	public static String getName(Column column){
		StringBuilder builder=new StringBuilder();
		String tableName=column.getTableName();
		if (tableName!=null){
			String schemaName=column.getSchemaName();
			if (schemaName!=null){
				builder.append(schemaName);
				builder.append(".");
			}
			builder.append(tableName);
			builder.append(".");
		}
		builder.append(column.getName());
		return builder.toString();
	}
	
	public static String getName(ReferenceColumn column){
		StringBuilder builder=new StringBuilder();
		String tableName=column.getTableName();
		if (tableName!=null){
			String schemaName=column.getSchemaName();
			if (schemaName!=null){
				builder.append(schemaName);
				builder.append(".");
			}
			builder.append(tableName);
			builder.append(".");
		}
		builder.append(column.getName());
		return builder.toString();
	}
	
	public static String getColumnName(String schemaName, String tableName, String columnName){
		StringBuilder builder=new StringBuilder();
		if (tableName!=null){
			if (schemaName!=null){
				builder.append(schemaName);
				builder.append(".");
			}
			builder.append(tableName);
			builder.append(".");
		}
		builder.append(columnName);
		return builder.toString();
	}
	
	public static String getPortName(Column column){
		return getName(column);
	}

	public static String getPortName(ReferenceColumn column){
		return getName(column);
	}

	public static String getFkPortName(ForeignKeyConstraint fk){
		Column column=null;
		if (fk.getColumns().length==1){
			column=fk.getColumns()[0];
		} else{
			column=fk.getColumns()[fk.getColumns().length/2];
		}
		return getPortName(column);
	}
	
	public static String getPkPortName(ForeignKeyConstraint fk){
		ReferenceColumn column=null;
		if (fk.getRelatedColumns().size()==1){
			column=fk.getRelatedColumns().get(0);
		} else{
			column=fk.getRelatedColumns().get(fk.getRelatedColumns().size()/2);
		}
		return getColumnName(fk.getRelatedTableSchemaName(), fk.getRelatedTableName(), column.getName());
	}
	
	public static String getPartitionRage(final Table table) {
		if (table.getPartitionParent()==null) {
			return null;
		}
		Table parent=table.getPartitionParent().getTable();
		if (parent==null||parent.getPartitioning()==null) {
			return null;
		}
		if (parent.getPartitioning().getPartitioningType()==null) {
			return PartitioningType.Range.toExpression(table);
		}
		return parent.getPartitioning().getPartitioningType().toExpression(table);
	}
}
