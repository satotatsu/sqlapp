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

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static com.sqlapp.util.CommonUtils.*;
/**
 * キーが２つあるマップ
 * @author satoh
 *
 * @param <S>
 * @param <T>
 * @param <U>
 */
public class DoubleKeyMap<S,T,U> implements Serializable, Cloneable{
	
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	private Map<S, Map<T,U>> innerMap=linkedMap();

	/**
	 * キー2つを指定して値を取得します
	 * @param key1
	 * @param key2
	 */
	public U get(S key1, T key2){
		Map<T,U> map2=innerMap.get(key1);
		if (map2==null){
			return null;
		}
		return map2.get(key2);
	}

	/**
	 * 第1のキーに紐づく第2キーと値のマップを返す
	 * @param key1
	 */
	public Map<T,U> get(S key1){
		return linkedMap(innerMap.get(key1));
	}

	/**
	 * 指定したキー2つの値を削除します
	 * @param key1
	 * @param key2
	 * @return 削除前の値
	 */
	public U remove(S key1, T key2){
		Map<T,U> map2=innerMap.get(key1);
		if (map2==null){
			return null;
		}
		return map2.remove(key2);
	}

	/**
	 * 第1のキーのセットを取得します
	 */
	public Set<S> keySet(){
		return innerMap.keySet();
	}

    /**
     * 第2のキーのセットを取得します
     * @return 第2のキーのセット
     */
    public Set<T> secondKeySet(){
    	Set<T> result=CommonUtils.linkedSet();
		for(Map.Entry<S, Map<T,U>> entry:entrySet()){
			result.addAll(entry.getValue().keySet());
		}
        return result;
    }

	/**
	 * 値のセットを取得します
	 */
	public Set<Map.Entry<S, Map<T,U>>> entrySet(){
		return innerMap.entrySet();
	}

	/**
	 * 2つのキーを指定して値を設定します。
	 * @param key1
	 * @param key2
	 * @param value
	 */
	public void put(S key1, T key2, U value){
		Map<T,U> map2=innerMap.get(key1);
		if (map2==null){
			map2=linkedMap();
			innerMap.put(key1, map2);
		}
		map2.put(key2, value);
	}
	
	public boolean containsKey(S key1, T key2){
		Map<T,U> map2=innerMap.get(key1);
		if (map2!=null){
			return map2.containsKey(key2);
		}
		return false;
	}

	/**
	 * 値をクリアします
	 */
	public void clear(){
		innerMap.clear();
	}

	/**
	 * 第1のキーに紐づく値をクリアします
	 * @param key1
	 */
	public void clear(S key1){
		Map<T,U> map2=innerMap.get(key1);
		if (map2==null){
			return;
		}
		map2.clear();
	}

	/**
	 * リストへの変換を行います
	 */
	public List<U> toList(){
		List<U> list=list();
		for(Map.Entry<S, Map<T,U>> entry:entrySet()){
			for(Map.Entry<T,U> entry1:entry.getValue().entrySet()){
				list.add(entry1.getValue());
			}
		}
		return list;
	}

	/**
	 * マップリストへの変換を行います
	 */
	public Map<S, List<U>> toMapList(){
		Map<S, List<U>> map=map();
		for(Map.Entry<S, Map<T,U>> entry:entrySet()){
			List<U> list=list();
			for(Map.Entry<T,U> entry1:entry.getValue().entrySet()){
				list.add(entry1.getValue());
			}
			map.put(entry.getKey(), list);
		}
		return map;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString(){
		StringBuilder builder=new StringBuilder("[");
		boolean first=true;
		for(Map.Entry<S, Map<T,U>> entry:entrySet()){
			for(Map.Entry<T,U> entry1:entry.getValue().entrySet()){
				if (!first){
					builder.append(", ");
				}
				builder.append("{");
				builder.append("(");
				builder.append(entry.getKey());
				builder.append(", ");
				builder.append(entry1.getKey());
				builder.append(")=");
				builder.append(entry1.getValue());
				builder.append("}");
				first=false;
			}
		}
		builder.append("]");
		return builder.toString();
	}

	/**
	 * サイズを返します
	 */
	public int size(){
		int ret=0;
		for(Map.Entry<S, Map<T,U>> entry:this.innerMap.entrySet()){
			ret=ret+entry.getValue().size();
		}
		return ret;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public DoubleKeyMap<S,T,U> clone(){
		DoubleKeyMap<S,T,U> clone=new DoubleKeyMap<S,T,U>();
		clone.innerMap=linkedMap(this.innerMap);
		return clone;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode(){
		return CommonUtils.hashCode(this.innerMap);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (obj==null){
			return false;
		}
		if (this==obj){
			return true;
		}
		if (!(obj instanceof DoubleKeyMap)){
			return false;
		}
		DoubleKeyMap<?,?,?> cst=(DoubleKeyMap<?,?,?>)obj;
		if (!this.innerMap.equals(cst.innerMap)){
			return false;
		}
		return true;
	}

	/**
	 * コレクションを第1キー、第2キーを取得する関数を利用して変換します。
	 * @param c
	 * @param func1
	 * @param func2
	 */
	public static <U,S,T> DoubleKeyMap<S,T,U> toMap(Collection<U> c, Function<U,S> func1, Function<U,T> func2){
		if (c==null){
			return CommonUtils.doubleKeyMap();
		}
		DoubleKeyMap<S,T,U> result=new DoubleKeyMap<S,T,U>();
		c.forEach(v->{
			S key1=func1.apply(v);
			T key2=func2.apply(v);
			result.put(key1, key2, v);
		});
		return result;
	}

	/**
	 * コレクションを第1キー、第2キーを取得する関数を利用して値をリストとするマップに変換します。
	 * @param c
	 * @param func1
	 * @param func2
	 */
	public static <S,T,U> DoubleKeyMap<S,T,List<U>> toListMap(Collection<U> c, Function<U,S> func1, Function<U,T> func2){
		if (c==null){
			return CommonUtils.doubleKeyMap();
		}
		DoubleKeyMap<S,T,List<U>> result=new DoubleKeyMap<S,T,List<U>>();
		c.forEach(v->{
			S key1=func1.apply(v);
			T key2=func2.apply(v);
			List<U> list=result.get(key1, key2);
			if (list==null){
				list=CommonUtils.list();
				result.put(key1, key2, list);
			}
			list.add(v);
		});
		return result;
	}

}