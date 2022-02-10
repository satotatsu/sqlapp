/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.util;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.converter.Converters;

import static com.sqlapp.util.DbUtils.*;

/**
 * SQLを実行するためのユーティリティクラス
 * @author satoh
 *
 */
public class SqlExecuter {
	/**
	 * SQL
	 */
	private StringBuilder sql=new StringBuilder();
	/**
	 * クエリのパラメタ
	 */
	private List<Object> parameters=new ArrayList<Object>();
	/**
	 * クエリの型
	 */
	private List<Integer> jdbcTypes=new FlexList<Integer>();

	/**
	 * コンストラクタ
	 */
	public SqlExecuter(){
		
	}

	/**
	 * コンストラクタ
	 */
	public SqlExecuter(String sql){
		this.sql.append(sql);
	}

	/**
	 * SQLの連結
	 * @param part
	 */
	public SqlExecuter addSql(String part){
		this.sql.append(part);
		return this;
	}

	/**
	 * SQLの連結
	 * @param part
	 */
	public SqlExecuter addAnd(String part){
		this.sql.append(" AND");
		return this;
	}

	/**
	 * SQLの連結(改行付き)
	 * @param part
	 */
	public SqlExecuter addSqlLine(String part){
		this.sql.append('\n').append(part);
		return this;
	}

	/**
	 * パラメタの追加
	 * @param jdbcType
	 * @param vals
	 */
	public SqlExecuter addParameter(int jdbcType, Object... vals){
		for(Object val:vals){
			parameters.add(val);
			jdbcTypes.add(jdbcType);
		}
		return this;
	}

	/**
	 * パラメタの追加
	 * @param vals
	 */
	public SqlExecuter addVarcharParameter(String... vals){
		for(String val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.VARCHAR);
		}
		return this;
	}

	/**
	 * INパラメタの追加
	 * @param vals
	 */
	public SqlExecuter addInVarcharParameter(String... vals){
		for(Object val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.VARCHAR);
		}
		addInParameter(vals.length);
		return this;
	}

	/**
	 * INパラメタの追加
	 * @param vals
	 */
	public SqlExecuter addInNVarcharParameter(String... vals){
		for(Object val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.NVARCHAR);
		}
		addInParameter(vals.length);
		return this;
	}

	/**
	 * INパラメタの追加
	 * @param vals
	 */
	public SqlExecuter addInParameter(int jdbcType, Object... vals){
		for(Object val:vals){
			parameters.add(val);
			jdbcTypes.add(jdbcType);
		}
		addInParameter(vals.length);
		return this;
	}

	/**
	 * INパラメタの追加
	 * @param vals
	 */
	private void addInParameter(int paramCount){
		SeparatedStringBuilder sep=new SeparatedStringBuilder(", ");
		for(int i=0;i<paramCount;i++){
			sep.add("?");
		}
		if (paramCount>0){
			this.sql.append('(');
			this.sql.append(sep.toString());
			this.sql.append(')');
		}
	}

	/**
	 * パラメタの追加
	 * @param vals
	 */
	public SqlExecuter addCharParameter(String... vals){
		for(String val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.CHAR);
		}
		return this;
	}
	
	/**
	 * パラメタの追加
	 * @param vals
	 */
	public SqlExecuter addNVarcharParameter(String... vals){
		for(String val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.NVARCHAR);
		}
		return this;
	}

	/**
	 * パラメタの追加
	 * @param vals
	 */
	public SqlExecuter addNcharParameter(String... vals){
		for(String val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.NVARCHAR);
		}
		return this;
	}

	/**
	 * パラメタの追加
	 * @param val
	 */
	public SqlExecuter addParameter(Object val){
		parameters.add(val);
		if (val==null){
			jdbcTypes.add(java.sql.Types.NULL);
		} else{
			jdbcTypes.add(null);
		}
		return this;
	}

	/**
	 * パラメタの追加
	 * @param vals
	 */
	public SqlExecuter addParameter(Boolean... vals){
		for(Boolean val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.BOOLEAN);
		}
		return this;
	}

	/**
	 * パラメタの追加
	 * @param vals
	 */
	public SqlExecuter addParameter(Short... vals){
		for(Short val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.SMALLINT);
		}
		return this;
	}
	
	/**
	 * パラメタの追加
	 * @param vals
	 */
	public SqlExecuter addParameter(Byte... vals){
		for(Byte val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.TINYINT);
		}
		return this;
	}
	
	/**
	 * パラメタの追加
	 * @param vals
	 */
	public SqlExecuter addParameter(Integer... vals){
		for(Integer val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.INTEGER);
		}
		return this;
	}

	/**
	 * パラメタの追加
	 * @param vals
	 */
	public SqlExecuter addParameter(Long... vals){
		for(Long val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.BIGINT);
		}
		return this;
	}

	/**
	 * パラメタの追加
	 * @param vals
	 */
	public SqlExecuter addParameter(java.util.Date... vals){
		for(java.util.Date val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.TIMESTAMP);
		}
		return this;
	}

	/**
	 * パラメタの追加
	 * @param vals
	 */
	public SqlExecuter addParameter(java.sql.Date... vals){
		for(java.sql.Date val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.DATE);
		}
		return this;
	}

	/**
	 * パラメタの追加
	 * @param vals
	 */
	public SqlExecuter addParameter(java.sql.Time... vals){
		for(java.sql.Time val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.TIME);
		}
		return this;
	}

	/**
	 * パラメタの追加
	 * @param vals
	 */
	public SqlExecuter addParameter(java.sql.Timestamp... vals){
		for(java.sql.Timestamp val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.TIMESTAMP);
		}
		return this;
	}
	
	/**
	 * パラメタの追加
	 * @param vals
	 */
	public SqlExecuter addParameter(BigDecimal... vals){
		for(BigDecimal val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.DECIMAL);
		}
		return this;
	}

	/**
	 * パラメタの追加
	 * @param vals
	 */
	public SqlExecuter addParameter(Double... vals){
		for(Double val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.DOUBLE);
		}
		return this;
	}

	/**
	 * パラメタの追加
	 * @param vals
	 */
	public SqlExecuter addParameter(Float... vals){
		for(Float val:vals){
			parameters.add(val);
			jdbcTypes.add(java.sql.Types.FLOAT);
		}
		return this;
	}

	/**
	 * パラメタのサイズ
	 */
	public int parameterSize(){
		return parameters.size();
	}

	/**
	 * パラメタを設定したプリペアドステートメントの設定
	 * @param connection
	 */
	public PreparedStatement createPreparedStatement(Connection connection){
		PreparedStatement statement=null;
		try {
			statement = connection.prepareStatement(this.sql.toString());
			for(int i=0;i<this.parameters.size();i++){
				Object val=parameters.get(i);
				Integer jdbcType=jdbcTypes.get(i);
				if (jdbcType!=null){
					statement.setObject(i+1, val, jdbcType);
				} else{
					statement.setObject(i+1, val);				
				}
			}
			return statement;
		} catch (SQLException e) {
			close(statement);
			throw new RuntimeException(e);
		}
	}

	/**
	 * パラメタを設定したプリペアドステートメントの設定
	 * @param connection
	 * @param clazz 結果として取得したいオブジェクトの型
	 * @param columnIndex カラムのインデックス
	 */
	public <T> T executeScalar(final Connection connection
			, final Class<T> clazz, final int columnIndex){
		PreparedStatement statement=null;
		ResultSet resultSet=null;
		try {
			statement = createPreparedStatement(connection);
			resultSet=statement.executeQuery();
			if (!resultSet.next()){
				return null;
			}
			Object val=resultSet.getObject(columnIndex);
			return (T)Converters.getDefault().convertObject(val, clazz);
		} catch (SQLException e) {
			close(resultSet);
			close(statement);
			throw new RuntimeException(e);
		}
	}

	/**
	 * パラメタを設定したプリペアドステートメントの設定
	 * @param connection
	 */
	public <T> T executeScalar(final Connection connection
			, final Class<T> clazz, final String columnName){
		PreparedStatement statement=null;
		ResultSet resultSet=null;
		try {
			statement = createPreparedStatement(connection);
			resultSet=statement.executeQuery();
			if (!resultSet.next()){
				return null;
			}
			Object val=resultSet.getObject(columnName);
			return (T)Converters.getDefault().convertObject(val, clazz);
		} catch (SQLException e) {
			close(resultSet);
			close(statement);
			throw new RuntimeException(e);
		}
	}

	@Override
	public String toString(){
		return this.sql.toString();
	}
}
