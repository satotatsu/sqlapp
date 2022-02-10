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

package com.sqlapp.data.converter;

import static com.sqlapp.util.CommonUtils.*;

import java.sql.Connection;
import java.util.Map;

public class BooleanConverter extends AbstractConverter<Boolean>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 5692455210592650318L;
	private static final Map<String, Boolean> BOOL_MAP;
	private static final Map<String, String> BOOL_TRUE_PARE_MAP;
	private static final Map<String, String> BOOL_FALSE_PARE_MAP;
	/**
	 * true value
	 */
	private String trueString=null;
	/**
	 * false value
	 */
	private String falseString=null;
	
	static {
		BOOL_MAP=lowerMap();
		BOOL_TRUE_PARE_MAP=lowerMap();
		BOOL_FALSE_PARE_MAP=lowerMap();
		registerChar("1", "0");
		register("1.0", "0.0");
		register("x'1'", "x'0'");
		registerChar("true", "false");
		registerChar("t", "f");
		registerChar("yes", "no");
		registerChar("y", "n");
		registerChar("on", "off");
		registerChar("ok", "ng");
	}

	private static void register(String trueValue, String falseValue){
		BOOL_MAP.put(trueValue, Boolean.TRUE);
		BOOL_MAP.put(falseValue, Boolean.FALSE);
		BOOL_TRUE_PARE_MAP.put(trueValue, falseValue);
		BOOL_FALSE_PARE_MAP.put(falseValue, trueValue);
	}

	private static void registerChar(String trueValue, String falseValue){
		register(trueValue, falseValue);
		BOOL_MAP.put("'"+trueValue+"'", Boolean.TRUE);
		BOOL_MAP.put("'"+falseValue+"'", Boolean.FALSE);
		BOOL_TRUE_PARE_MAP.put("'"+trueValue+"'", "'"+falseValue+"'");
		BOOL_FALSE_PARE_MAP.put("'"+falseValue+"'", "'"+trueValue);
	}
	
	/**
	 * コンストラクタ
	 */
	public BooleanConverter(){
		super();
		this.setTrueString("1");
	}

	@Override
	public Boolean convertObject(Object value, Connection conn) {
		return convertObject(value);
	}

	@Override
	public Boolean convertObject(Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		String lower=value.toString().trim();
		if (BOOL_MAP.containsKey(lower)){
			return BOOL_MAP.get(lower);
		}
		return getDefaultValue();
	}

	@Override
	public String convertString(Boolean value) {
		if (value==null){
			if (getDefaultValue()!=null){
				if (getDefaultValue()){
					return getTrueString();
				}else{
					return getFalseString();
				}
			}
			return null;
		} else{
			if (value){
				return getTrueString();
			} else{
				return getFalseString();
			}
		}
	}

	public String getTrueString() {
		return trueString;
	}

	public BooleanConverter setTrueString(String trueString) {
		if (BOOL_TRUE_PARE_MAP.containsKey(trueString)){
			this.falseString = BOOL_TRUE_PARE_MAP.get(trueString);
		}
		this.trueString = trueString;
		return this;
	}

	public String getFalseString() {
		return falseString;
	}

	public BooleanConverter setFalseString(String falseString) {
		if (BOOL_FALSE_PARE_MAP.containsKey(falseString)){
			this.trueString = BOOL_FALSE_PARE_MAP.get(falseString);
		}
		this.falseString = falseString;
		return this;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(this)){
			return false;
		}
		if (!(obj instanceof BooleanConverter)){
			return false;
		}
		BooleanConverter con=cast(obj);
		if (!eq(this.getFalseString(), con.getFalseString())){
			return false;
		}
		if (!eq(this.getTrueString(), con.getTrueString())){
			return false;
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode(){
		return this.getClass().getName().hashCode();
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public Boolean copy(Object obj){
		return convertObject(obj);
	}
}