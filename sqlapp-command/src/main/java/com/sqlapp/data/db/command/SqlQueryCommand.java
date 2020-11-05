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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.OutputTextBuilder;
/**
 * クエリを実行して結果を標準出力に出力します。
 * @author tatsuo satoh
 *
 */
public class SqlQueryCommand extends AbstractDataSourceCommand{

	private String sql=null;
	
	private OutputFormatType outputFormatType=OutputFormatType.TSV;
	
	@Override
	protected void doRun() {
		Dialect dialect=this.getDialect();
		try(Connection connection=this.getConnection()){
			try(Statement statement=connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)){
				try(ResultSet resultSet=statement.executeQuery(getSql())){
					Table table=new Table();
					table.setDialect(dialect);
					if (this.getOutputFormatType().isTable()){
						table.read(connection, resultSet);
						OutputTextBuilder builder=new OutputTextBuilder();
						builder.append(table);
						this.println(builder.toString());
					} else{
						table.readMetaData(connection, resultSet);
						ResultSetMetaData resultSetMetaData=resultSet.getMetaData();
						StringBuilder builder=new StringBuilder();
						int size=resultSetMetaData.getColumnCount();
						for(Column column:table.getColumns()){
							builder.append(column.getName());
							builder.append(this.getOutputFormatType().getSeparator());
						}
						this.println(builder.substring(0, builder.length()-1));
						while(resultSet.next()){
							builder=new StringBuilder();
							for(int i=1;i<=size;i++){
								Object obj=resultSet.getObject(i);
								Column column=table.getColumns().get(i-1);
								String text=dialect.getValueForDisplay(column, obj);
								builder.append(text);
								builder.append(this.getOutputFormatType().getSeparator());
							}
							this.println(builder.substring(0, builder.length()-1));
						}
					}
				}
			}
		} catch (SQLException e) {
			this.getExceptionHandler().handle(e);
		}
	}

	/**
	 * @return the sql
	 */
	public String getSql() {
		return sql;
	}

	/**
	 * @param sql the sql to set
	 */
	public void setSql(String sql) {
		this.sql = sql;
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
