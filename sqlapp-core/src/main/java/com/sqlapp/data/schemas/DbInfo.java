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

package com.sqlapp.data.schemas;

import static com.sqlapp.util.CommonUtils.cast;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.HashCodeBuilder;

/**
 * DB毎のデータを保持するストレージ
 * 
 * @author satoh
 * 
 */
public final class DbInfo implements Map<String,String>, Serializable, Cloneable {

	/** serialVersionUID */
	private static final long serialVersionUID = 1;

	private Map<String, String> innerMap = CommonUtils.caseInsensitiveTreeMap();

	/**
	 * @param key
	 */
	public String get(String key) {
		return innerMap.get(key);
	}
	
	public <T> T get(String key, Class<T> clazz) {
		return Converters.getDefault().convertObject(get(key), clazz);
	}
	
	/**
	 * 指定したDBの指定したDB情報との差分を取得します。
	 * @param dbInfo
	 */
	public Map<String, DiffValue> getDiffDbInfo(DbInfo dbInfo){
		if (dbInfo==null){
			return getDiffDbInfo(this, null);
		}
		return getDiffDbInfo(this, dbInfo);
	}
	
	private Map<String, DiffValue> getDiffDbInfo(Map<String, String> originalMap, Map<String, String> targetMap){
		Map<String, DiffValue> map=CommonUtils.linkedMap();
		Set<String> keys=CommonUtils.linkedSet();
		if (originalMap!=null){
			keys.addAll(originalMap.keySet());
		}
		if (targetMap!=null){
			keys.addAll(targetMap.keySet());
		}
		for(String key:keys){
			String original=null;
			String target=null;
			if (originalMap!=null){
				original=originalMap.get(key);
			}
			if (targetMap!=null){
				target=targetMap.get(key);
			}
			DiffValue diffValue=new DiffValue(original, target, State.getState(original, target));
			map.put(key, diffValue);
		}
		return map;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		HashCodeBuilder builder = new HashCodeBuilder();
		builder.append(innerMap);
		return builder.hashCode();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof DbInfo)) {
			return false;
		}
		DbInfo val = cast(obj);
		if (this.innerMap.size() != val.innerMap.size()) {
			return false;
		}
		boolean ret = this.innerMap.equals(val.innerMap);
		return ret;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public DbInfo clone() {
		DbInfo clone = new DbInfo();
		clone.putAll(this);
		return clone;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		boolean first = true;
		builder.append("{");
		for (Map.Entry<String, String> entry : innerMap.entrySet()) {
			if (!first) {
				builder.append(", ");
			}
			builder.append(entry.getKey());
			builder.append("=");
			builder.append(entry.getValue());
			first = false;
		}
		builder.append("}");
		return builder.toString();
	}
	
	public static class DiffValue implements Serializable, Cloneable {
		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 1L;
		/** 変更元の値 */
		private final String original;
		/** 変更後の値 */
		private final String target;
		/**変更状態*/
		private final State state;
		
		protected DiffValue(String original, String target, State state){
			this.original=original;
			this.target=target;
			this.state=state;
		}
		
		/**
		 * @return the original
		 */
		public String getOriginal() {
			return original;
		}
		/**
		 * @return the target
		 */
		public String getTarget() {
			return target;
		}
		/**
		 * @return the state
		 */
		public State getState() {
			return state;
		}
		
		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			if (state==State.Added){
				builder.append("+:");
			} else if (state==State.Modified){
				builder.append("c:");
			} else if (state==State.Deleted){
				builder.append("+:");
			}
			builder.append("(");
			builder.append(original);
			builder.append(" -> ");
			builder.append(target);
			builder.append(")");
			return builder.toString();
		}
	}

	@Override
	public int size() {
		return this.innerMap.size();
	}

	@Override
	public boolean isEmpty() {
		return this.innerMap.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return this.innerMap.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return this.innerMap.containsValue(value);
	}

	@Override
	public String get(Object key) {
		return this.innerMap.get(key);
	}

	@Override
	public String put(String key, String value) {
		return this.innerMap.put(key, value);
	}

	public String put(String key, Object value) {
		if (value==null){
			return this.innerMap.put(key, null);
		} else{
			return this.innerMap.put(key, value.toString());
		}
	}
	
	@Override
	public String remove(Object key) {
		return this.innerMap.remove(key);
	}

	@Override
	public void putAll(Map<? extends String, ? extends String> m) {
		this.innerMap.putAll(m);
	}

	@Override
	public void clear() {
		this.innerMap.clear();
	}

	@Override
	public Set<String> keySet() {
		return this.innerMap.keySet();
	}

	@Override
	public Collection<String> values() {
		return this.innerMap.values();
	}

	@Override
	public Set<java.util.Map.Entry<String, String>> entrySet() {
		return this.innerMap.entrySet();
	}
}
