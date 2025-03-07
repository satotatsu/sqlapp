/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect;

import java.util.Set;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.util.CommonUtils;

public enum ClassDefaultTypes {
	/**String*/
	TEXT(new Class<?>[]{String.class}, DataType.VARCHAR,1024L),
	/**String*/
	LONGTEXT(new Class<?>[]{String.class}, DataType.VARCHAR,CommonUtils.LEN_2GB),
	/**boolean*/
	BOOLEAN(new Class<?>[]{boolean.class, Boolean.class}, DataType.BOOLEAN),
	/**Integer*/
	INTEGER(new Class<?>[]{int.class, Integer.class}, DataType.INT),
	/**Long*/
	BIGINT(new Class<?>[]{long.class, Long.class}, DataType.BIGINT),
	/**Date*/
	Date(new Class<?>[]{java.util.Date.class}, DataType.DATETIME),
	;
	private ClassDefaultTypes(Class<?>[] clazzes, DataType types, Long maxlength){
		this.clazzes=CommonUtils.set(clazzes);
		this.types=types;
		this.maxlength=maxlength;
	}

	private ClassDefaultTypes(Class<?>[] clazzes, DataType types){
		this.clazzes=CommonUtils.set(clazzes);
		this.types=types;
		this.maxlength=null;
	}
	
	/**
	 * Javaの型
	 */
	private final Set<Class<?>> clazzes;
	/**
	 * DB型
	 */
	private final DataType types;
	/**
	 * 最大長
	 */
	private final Long maxlength;
	/**
	 * @return the clazzes
	 */
	public Set<Class<?>> getClazzes() {
		return clazzes;
	}

	/**
	 * @return the types
	 */
	public DataType getTypes() {
		return types;
	}

	/**
	 * @return the maxlength
	 */
	public Long getMaxlength() {
		return maxlength;
	}

	/**
	 * Javaのクラスと最大長から対応するEnumを取得します。
	 * @param clazz Javaのクラス
	 * @param maxlength 最大長
	 * @return enum
	 */
	public static ClassDefaultTypes getTypes(Class<?> clazz, Long maxlength){
		for(ClassDefaultTypes val:values()){
			if (val.getClazzes().contains(clazz)){
				if (maxlength!=null&&val.getMaxlength()!=null){
					if (val.getMaxlength().compareTo(maxlength)>=0){
						return val;
					}
				}
			}
		}
		for(ClassDefaultTypes val:values()){
			if (val.getClazzes().contains(clazz)){
				return val;
			}
		}
		return TEXT;
	}

	/**
	 * Javaのクラスと最大長から対応するEnumを取得します。
	 * @param clazz Javaのクラス
	 * @param maxlength 最大長
	 * @return enum
	 */
	public static ClassDefaultTypes getTypes(Class<?> clazz, long maxlength){
		return getTypes(clazz, Long.valueOf(maxlength));
	}

	
	/**
	 * Javaのクラスから対応するEnumを取得します。
	 * @param clazz Javaのクラス
	 * @return enum
	 */
	public static ClassDefaultTypes getTypes(Class<?> clazz){
		for(ClassDefaultTypes val:values()){
			if (val.getClazzes().contains(clazz)){
				return val;
			}
		}
		return TEXT;
	}

}
