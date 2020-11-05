/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.OutputTextBuilder;

public class CountAllTablesCommand extends AbstractTableCommand{
	
	private OutputFormatType outputFormatType=OutputFormatType.TSV;

	@Override
	protected void doRun() {
		Dialect dialect=this.getDialect();
		Table result=new Table();
		result.getColumns().add(new Column("schemaName").setDataType(DataType.NVARCHAR).setLength(254));
		result.getColumns().add(new Column("tableName").setDataType(DataType.NVARCHAR).setLength(254));
		result.getColumns().add(new Column("count").setDataType(DataType.BIGINT));
		try(Connection connection=this.getConnection();
			Statement statement=connection.createStatement();){
			SchemaReader schemaReader=this.getSchemaReader(dialect);
			Map<String,Schema> schemaMap=this.getSchemas(dialect, schemaReader);
			if (!getOutputFormatType().isTable()){
				StringBuilder builder=new StringBuilder();
				for(Column column:result.getColumns()){
					builder.append(column.getName());
					builder.append(this.getOutputFormatType().getSeparator());
				}
				this.println(builder.substring(0, builder.length()-1));
			}
			for(Map.Entry<String,Schema> entry:schemaMap.entrySet()){
				for(Table table:entry.getValue().getTables()){
					Row row=result.newRow();
					row.put("schemaName", entry.getKey());
					row.put("tableName", table.getName());
					AbstractSqlBuilder<?> sqlBuilder=dialect.createSqlBuilder();
					sqlBuilder.select().count("*").from().name(table);
					StringBuilder builder=new StringBuilder();
					builder.append(entry.getKey());
					builder.append(this.getOutputFormatType().getSeparator());
					builder.append(table.getName());
					long count=selectCount(dialect, statement, table);
					row.put("count", count);
					builder.append(this.getOutputFormatType().getSeparator());
					builder.append(count);
					if (!getOutputFormatType().isTable()){
						this.println(builder);
					}
					result.getRows().add(row);
				}
			}
			if (getOutputFormatType().isTable()){
				OutputTextBuilder builder=new OutputTextBuilder();
				builder.append(result);
				this.println(builder.toString());
			}
		} catch (SQLException e) {
			this.getExceptionHandler().handle(e);
		}
	}
	
	private long selectCount(Dialect dialect, Statement statement, Table table) throws SQLException{
		AbstractSqlBuilder<?> sqlBuilder=dialect.createSqlBuilder();
		sqlBuilder.select().count("*").from().name(table);
		try(ResultSet resultSet=statement.executeQuery(sqlBuilder.toString())){
			if (resultSet.next()){
				return resultSet.getLong(1);
			}
			return 0L;
		}
	}

	/**
	 * @return the outputFormatType
	 */
	public OutputFormatType getOutputFormatType() {
		return outputFormatType;
	}

	/**
	 * @param outputFormatType the outputFormatType to set
	 */
	public void setOutputFormatType(OutputFormatType outputFormatType) {
		this.outputFormatType = outputFormatType;
	}
	
	
}
