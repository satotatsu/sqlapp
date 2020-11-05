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
package com.sqlapp.data.db.dialect.oracle.util;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.datatype.LengthProperties;
import com.sqlapp.data.db.datatype.PrecisionProperties;
import com.sqlapp.data.db.datatype.ScaleProperties;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.TimesTen;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.FunctionReturning;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * Oracle用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class OracleSqlBuilder extends AbstractSqlBuilder<OracleSqlBuilder> {

	public OracleSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	private static final Set<String> storagePropertyNames=CommonUtils.upperSet();
	
	static{
		for(String arg:OracleUtils.getTableStorageKeys()){
			registerStragePropertyName(arg);
		}
	}
	
	private static void registerStragePropertyName(String name){
		storagePropertyNames.add(name.replace("_", ""));
	}
	
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * LEVEL句を追加します
	 * 
	 */
	public OracleSqlBuilder level() {
		appendElement("LEVEL");
		return instance();
	}
	
	/**
	 * DIRECTORY句を追加します
	 * 
	 */
	public OracleSqlBuilder directory() {
		appendElement("DIRECTORY");
		return instance();
	}

	/**
	 * ALTER COLUMN句を追加します
	 * 
	 */
	@Override
	public OracleSqlBuilder alterColumn() {
		return modify();
	}
	
	/**
	 * STORAGE句を追加します
	 * 
	 */
	public OracleSqlBuilder storage() {
		appendElement("STORAGE");
		return instance();
	}

	/**
	 * VARRAY句を追加します
	 * 
	 */
	public OracleSqlBuilder varray() {
		appendElement("VARRAY");
		return instance();
	}
	
	/**
	 * ROWID句を追加します
	 * 
	 */
	public OracleSqlBuilder rowid() {
		appendElement("ROWID");
		return instance();
	}
	
	/**
	 * SCN句を追加します
	 * 
	 */
	public OracleSqlBuilder scn() {
		appendElement("SCN");
		return instance();
	}

	/**
	 * FROM Sysdummy句を追加します
	 * 
	 */
	@Override
	public OracleSqlBuilder _fromSysDummy() {
		appendElement("FROM DUAL");
		return instance();
	}

	/**
	 * PURGE句を追加します
	 * 
	 */
	public OracleSqlBuilder purge() {
		appendElement("PURGE");
		return instance();
	}
	
	/**
	 * IMMEDIATE句を追加します
	 * 
	 */
	public OracleSqlBuilder immediate() {
		appendElement("IMMEDIATE");
		return instance();
	}
	
	/**
	 * SYNCHRONOUS句を追加します
	 * 
	 */
	public OracleSqlBuilder synchronous() {
		appendElement("SYNCHRONOUS");
		return instance();
	}

	/**
	 * ASYNCHRONOUS句を追加します
	 * 
	 */
	public OracleSqlBuilder asynchronous() {
		appendElement("ASYNCHRONOUS");
		return instance();
	}


	/**
	 * REPEAT句を追加します
	 * 
	 */
	public OracleSqlBuilder repeat() {
		appendElement("REPEAT");
		return instance();
	}

	
	/**
	 * OBJECT句を追加します
	 * 
	 */
	public OracleSqlBuilder object() {
		appendElement("OBJECT");
		return instance();
	}
	
	/**
	 * INCLUDING句を追加します
	 * 
	 */
	public OracleSqlBuilder including() {
		appendElement("INCLUDING");
		return instance();
	}
	
	/**
	 * OF句を追加します
	 * 
	 */
	public OracleSqlBuilder of() {
		appendElement("OF");
		return instance();
	}
	
	/**
	 * VARRAY句を追加します
	 * 
	 */
	public OracleSqlBuilder varray(int size) {
		appendElement("VARRAY");
		this._add("(");
		this._add(size);
		this._add(")");
		return instance();
	}

	/**
	 * 条件を満たす場合にインデックスタイプの追加を行います
	 * 
	 * @param value
	 * @param condition
	 */
	public OracleSqlBuilder _add(IndexType value, boolean condition) {
		if (condition) {
			if (IndexType.BTree==value){
				if (this.getDialect().getClass().equals(TimesTen.class)){
					_add(value.toString());
				}
			} else{
				_add(value.toString());
			}
		}
		return instance();
	}
	
	public OracleSqlBuilder oracleProperty(String key, String value){
		if ("LOGGING".equalsIgnoreCase(key)){
			Boolean bool=Converters.getDefault().convertObject(value, Boolean.class);
			if (bool!=null&&bool.booleanValue()){
				_add("LOGGING");
			} else{
				_add("NOLOGGING");
			}
		}else if ("INITIAL_EXTENT".equalsIgnoreCase(key)){
			key="INITIAL";
			_add(key).space()._add(value);
		}else if ("NEXT_EXTENT".equalsIgnoreCase(key)){
			key="NEXT";
			_add(key).space()._add(value);
		}else if ("FREELIST_GROUPS".equalsIgnoreCase(key)){
			key="FREELIST GROUPS";
			_add(key).space()._add(value);
		} else{
			if (!"BUFFER_POOL".equalsIgnoreCase(key)){
				key=key.replace("_", "");
			}
			_add(key).space()._add(value);
		}
		return instance();
	}
	
	public boolean isStoragePropertyName(String name){
		boolean bool= storagePropertyNames.contains(name.replace("_", "").replace(" ", ""));
		return bool;
	}

	/**
	 * 引数を追加します
	 * 
	 * @param obj
	 */
	@Override
	public OracleSqlBuilder argument(NamedArgument obj) {
		argumentBefore(obj);
		if (obj.getName() != null) {
			this._add(obj.getName());
			this.space();
		}
		argumentTypeDefinition(obj.getDataType(),
				obj.getDataTypeName(), CommonUtils.notZero(obj.getLength(), obj.getOctetLength()),
				obj.getScale());
		if (obj.getDirection() != null
				&& obj.getDirection() != ParameterDirection.Input) {
			this.space()._add(obj.getDirection());
		}
		argumentAfter(obj);
		return instance();
	}
	

	
	/**
	 * カラムの型の定義を追加します
	 * 
	 * @param column
	 *            カラム
	 */
	private void argumentTypeDefinition(DataType type, String dataTypeName,
			Long maxlength, Integer scale) {
		DbDataType<?> dbDataType = null;
		if (maxlength != null) {
			dbDataType = this.getDialect().getDbDataTypes()
					.getDbType(type, maxlength);
		} else {
			dbDataType = this.getDialect().getDbDataTypes().getDbType(type);
		}
		if (type != DataType.OTHER) {
			Long len = null;
			if (dbDataType instanceof LengthProperties) {
				len = ((LengthProperties<?>) dbDataType).getLength(maxlength);
			} else if (dbDataType instanceof PrecisionProperties) {
				len = ((PrecisionProperties<?>) dbDataType).getPrecision(
						maxlength).longValue();
			}
			if (dbDataType instanceof ScaleProperties) {
				scale = ((ScaleProperties<?>) dbDataType).getScale(scale);
			}
			if (dbDataType!=null){
				String def = dbDataType.getColumCreateDefinition(len, scale);
				this._add(removeLength(def));
			} else{
				this._add(removeLength(dataTypeName));
			}
		} else {
			this._add(removeLength(dataTypeName));
		}
	}
	
	private static final Pattern NUMBER_PATTERN=Pattern.compile("[0-9]+");
	
	private String removeLength(String val){
		if (!val.contains("(")){
			return val;
		}
		String[] args=val.split("[(),]");
		StringBuilder builder=new StringBuilder();
		for(String arg:args){
			arg=CommonUtils.trim(arg);
			Matcher matcher=NUMBER_PATTERN.matcher(arg);
			if (matcher.matches()){
				continue;
			}
			builder.append(arg);
			builder.append(' ');
		}
		return builder.substring(0, builder.length()-1);
	}
	
	/**
	 * RETURNINGを追加します
	 * 
	 * @param obj
	 */
	public OracleSqlBuilder _add(FunctionReturning obj) {
		argumentTypeDefinition(obj.getDataType(),
				obj.getDataTypeName(), CommonUtils.notZero(obj.getLength(), obj.getOctetLength()),
				obj.getScale());
		return instance();
	}

	@Override
	protected OracleSqlBuilder autoIncrement(AbstractColumn<?> column) {
		return instance();
	}
	
	@Override
	public OracleSqlBuilder clone(){
		return (OracleSqlBuilder)super.clone();
	}

}
