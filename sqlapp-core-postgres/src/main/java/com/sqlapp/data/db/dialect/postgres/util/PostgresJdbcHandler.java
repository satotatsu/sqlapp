/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.postgres.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.postgresql.copy.CopyIn;
import org.postgresql.copy.CopyManager;
import org.postgresql.copy.CopyOut;
import org.postgresql.core.BaseConnection;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.AbstractJdbc;
import com.sqlapp.jdbc.JdbcLogUtils;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class PostgresJdbcHandler extends JdbcHandler{

	public PostgresJdbcHandler(SqlNode node) {
		super(node);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <T extends JdbcHandler> T execute(Connection connection,
			ParametersContext context) {
		String sql=this.getNode().toString();
		Matcher matcher=COPY_DETAIL_PATTERN.matcher(sql);
		if (matcher.matches()){
			String fromTo=matcher.group("fromTo");
			String std=matcher.group("std");
			try {
				if ("TO".equalsIgnoreCase(fromTo)){
					handleCopyOut(connection, context, std);
				} else{
					handleCopyIn(connection, context, std);
				}
				return (T)this;
			} catch (SQLException|IOException e) {
				throw new RuntimeException(e);
			}
		} else{
			return this.execute(connection, context, null);
		}
	}

	private static final Pattern COPY_DETAIL_PATTERN=Pattern.compile("\\s*COPY\\s+.*?(?<fromTo>FROM|TO)?\\s+.?(?<std>STDIN|STDOUT)?\\s+.*", Pattern.CASE_INSENSITIVE+Pattern.DOTALL+Pattern.MULTILINE);
	
	private long handleCopyIn(Connection conn, ParametersContext context, String std) throws SQLException, IOException{
		SqlParameterCollection sqlParameters = createSqlParameterCollection(context);
		BaseConnection connection=conn.unwrap(BaseConnection.class);
		AbstractJdbc<?> logConnection=conn.unwrap(AbstractJdbc.class);
		Object input=sqlParameters.getInputStream();
		CopyManager copyManager = new CopyManager(connection);
		String sql=sqlParameters.getSql();
		long start = System.currentTimeMillis();
		long result=-1;
		try{
			if ("STDIN".equals(std)){
				if (input instanceof Reader){
					result= copyManager.copyIn(sql, (Reader)input);
				}else if (input instanceof InputStream){
					result= copyManager.copyIn(sql, (InputStream)input);
				} else{
					result= copyManager.copyIn(sql, System.in);
				}
			} else{
				CopyIn copyIn=copyManager.copyIn(sql);
				result=copyIn.getHandledRowCount();
			}
			JdbcLogUtils.info(logConnection, "rowCount="+result);
			return result;
		} finally{
			long end = System.currentTimeMillis();
			JdbcLogUtils.logSql(logConnection, sql, start, end);
		}
	}
	
	private long handleCopyOut(Connection conn, ParametersContext context, String std) throws SQLException, IOException{
		SqlParameterCollection sqlParameters = createSqlParameterCollection(context);
		BaseConnection connection=conn.unwrap(BaseConnection.class);
		AbstractJdbc<?> logConnection=conn.unwrap(AbstractJdbc.class);
		Object output=sqlParameters.getOutputStream();
		CopyManager copyManager = new CopyManager(connection);
		String sql=sqlParameters.getSql();
		long result=-1;
		long start = System.currentTimeMillis();
		try{
			if ("STDOUT".equals(std)){
				if (output instanceof Writer){
					result= copyManager.copyOut(sql, (Writer)output);
				}else if (output instanceof OutputStream){
					result= copyManager.copyOut(sql, (OutputStream)output);
				} else{
					result= copyManager.copyOut(sql, System.out);
				}
			}else {
				CopyOut copyOut= copyManager.copyOut(sql);
				result=copyOut.getHandledRowCount();
			}
			JdbcLogUtils.info(logConnection, "rowCount="+result);
			return result;
		} finally{
			long end = System.currentTimeMillis();
			if (logConnection!=null){
				JdbcLogUtils.logSql(logConnection, sql, start, end);
			}
		}
	}

}
