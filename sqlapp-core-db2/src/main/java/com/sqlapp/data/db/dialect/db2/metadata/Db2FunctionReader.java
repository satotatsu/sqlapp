/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.FunctionReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;
import com.sqlapp.data.schemas.OnNullCall;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SqlDataAccess;
/**
 * DB2の関数読み込みクラス
 * 
 * @author satoh
 * 
 */
public class Db2FunctionReader extends FunctionReader {

	protected Db2FunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Function> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Function> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Function function = createFunction(rs);
				result.add(function);
			}
		});
		return result;
	}

	protected Function createFunction(ExResultSet rs) throws SQLException {
		Function obj = new Function(getString(rs, ROUTINE_NAME));
		Db2Utils.setRutine(this.getDialect(), rs, this.getReaderOptions(), obj);
		obj.setDeterministic("Y".equalsIgnoreCase(this.getString(rs, "DETERMINISTIC")));
		obj.setParallel("Y".equalsIgnoreCase(this.getString(rs, "PARALLEL")));
		String type=this.getString(rs, "FUNCTIONTYPE");
		String definition = getString(rs, "ROUTINE_DEFINITION");
		if ("R".equalsIgnoreCase(type)){
			obj.setFunctionType(FunctionType.Row);
			setRowTableDefinition(obj, definition);
		} else if ("T".equalsIgnoreCase(type)){
			// テーブル
			obj.setFunctionType(FunctionType.Table);
			setRowTableDefinition(obj, definition);
		} else if ("C".equalsIgnoreCase(type)){
			// 列または集約
			obj.setFunctionType(FunctionType.Aggregate);
		} else if ("S".equalsIgnoreCase(type)){
			// スカラー
			obj.setFunctionType(FunctionType.Scalar);
		}
		String nullCall=this.getString(rs, "NULLCALL");
		if ("N".equalsIgnoreCase(nullCall)){
			obj.setOnNullCall(OnNullCall.ReturnsNullOnNullInput);
		}else if ("Y".equalsIgnoreCase(nullCall)){
			obj.setOnNullCall(OnNullCall.CalledOnNullInput);
		}
		String sqlDataAccess=getString(rs, "SQL_DATA_ACCESS");
		if ("C".equalsIgnoreCase(sqlDataAccess)){
			obj.setSqlDataAccess(SqlDataAccess.ContainsSql);
		}else if ("M".equalsIgnoreCase(sqlDataAccess)){
			obj.setSqlDataAccess(SqlDataAccess.ModifiesSqlData);
		}else if ("N".equalsIgnoreCase(sqlDataAccess)){
			obj.setSqlDataAccess(SqlDataAccess.NoSql);
		}else if ("R".equalsIgnoreCase(sqlDataAccess)){
			obj.setSqlDataAccess(SqlDataAccess.ReadsSqlData);
		}
		setSpecifics(rs, "DIALECT", obj);
		return obj;
	}

	private static final Pattern TABLE_PATTERN=Pattern.compile(".*\\s+RETURNS\\s+table\\s+\\((?<columnDef>.*)\\).*", Pattern.CASE_INSENSITIVE+Pattern.MULTILINE+Pattern.DOTALL);

	private static final Pattern ROW_PATTERN=Pattern.compile(".*\\s+RETURNS\\s+row\\s+\\((?<columnDef>.*)\\).*", Pattern.CASE_INSENSITIVE+Pattern.MULTILINE+Pattern.DOTALL);

	protected void setRowTableDefinition(Function obj, String definition){
		Matcher matcher=null;
		if (obj.getFunctionType().isTable()){
			matcher=TABLE_PATTERN.matcher(definition);
		}else if (obj.getFunctionType().isRow()){
			matcher=ROW_PATTERN.matcher(definition);
		}
		if (matcher!=null&&matcher.matches()){
			obj.getReturning().toTable();
			String columnDef=matcher.group("columnDef");
			setColumnDefinitions(obj, CommonUtils.trim(columnDef));
		}
	}

	protected void setColumnDefinitions(Function obj, String columnDefs){
		int end=searchEndOfColumns(columnDefs);
		columnDefs=columnDefs.substring(0, end);
		String[] splits=columnDefs.split(",");
		StringBuilder builder=new StringBuilder();
		int i=0;
		boolean needsNext=false;
		while(i<splits.length){
			String value=splits[i++];
			int startBracketPos=value.indexOf('(');
			while(startBracketPos<value.length()){
				if (startBracketPos<0){
					needsNext=false;
					break;
				}
				int endBracketPos=value.indexOf(')', startBracketPos+1);
				if (endBracketPos<0){
					needsNext=true;
					break;
				} else{
					startBracketPos=value.indexOf('(', endBracketPos+1);
					int endBracketNextPos=value.indexOf(')', endBracketPos+1);
					if (endBracketNextPos>=0&&(endBracketNextPos<startBracketPos)){
						value=value.substring(0, endBracketNextPos);
						needsNext=false;
						break;
					}
				}
			}
			if (builder.length()>0){
				builder.append(",");
			}
			builder.append(value);
			if (!needsNext){
				setColumnDefinition(obj, builder.toString().trim());
				builder=new StringBuilder();
				needsNext=false;
			}
		}
	}

	private static final Pattern COLUMN_PATTERN=Pattern.compile("^(?<name>[\\S]+)\\s+(?<def>.*)", Pattern.CASE_INSENSITIVE+Pattern.MULTILINE+Pattern.DOTALL);

	protected int searchEndOfColumns(String columnDefs){
		int i=0;
		int count=0;
		for(i=0;i<columnDefs.length();i++){
			char c=columnDefs.charAt(i);
			if (c=='('){
				count++;
			}else if (c==')'){
				count--;
			}
			if (count<0){
				return i;
			}
		}
		return i;
	}

	
	protected void setColumnDefinition(Function obj, String columnDef){
		Matcher mathcer=COLUMN_PATTERN.matcher(columnDef);
		if (mathcer.matches()){
			String name=CommonUtils.unwrap(mathcer.group("name"), this.getDialect().getOpenQuote());
			final String def=mathcer.group("def");
			obj.getReturning().getTable().getColumns().add(name, c->{
				c.setDataTypeName(def);
			});
		}
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("functions.sql");
		return node;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.metadata.RoutineReader#newRoutineArgumentReader()
	 */
	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new Db2FunctionArgumentReader(this.getDialect());
	}

}
