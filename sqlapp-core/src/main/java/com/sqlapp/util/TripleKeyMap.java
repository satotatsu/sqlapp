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
import static com.sqlapp.util.CommonUtils.*;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
/**
 * 3つのキーを持つマップ
 * @author satoh
 *
 * @param <S>
 * @param <T>
 * @param <U>
 * @param <V>
 */
public class TripleKeyMap<S,T,U,V> implements Serializable, Cloneable{
	
	/** serialVersionUID */
	private static final long serialVersionUID = 1L;

	private Map<S, DoubleKeyMap<T,U,V>> innerMap=linkedMap();
	/**
	 * 3つのキーを指定して値を取得します
	 * @param key1 第1キー
	 * @param key2 第2キー
	 * @param key3 第3キー
	 */
	public V get(S key1, T key2, U key3){
		DoubleKeyMap<T,U,V> map2=innerMap.get(key1);
		if (map2==null){
			return null;
		}
		return map2.get(key2, key3);
	}

	public void put(S key1, T key2, U key3, V value){
		DoubleKeyMap<T,U,V> map2=innerMap.get(key1);
		if (map2==null){
			map2=new DoubleKeyMap<T,U,V>();
			innerMap.put(key1, map2);
		}
		map2.put(key2, key3, value);
	}
	
	public boolean containsKey(S key1, T key2, U key3){
		DoubleKeyMap<T,U,V> map2=innerMap.get(key1);
		if (map2!=null){
			return map2.containsKey(key2, key3);
		}
		return false;
	}

	/**
	 * 指定したキー3つの値を削除します
	 * @param key1 第1キー
	 * @param key2 第2キー
	 * @param key3 第3キー
	 */
	public V remove(S key1, T key2, U key3){
		DoubleKeyMap<T,U,V> map2=innerMap.get(key1);
		if (map2==null){
			return null;
		}
		return map2.remove(key2, key3);
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
		for(Map.Entry<S, DoubleKeyMap<T,U,V>> entry:entrySet()){
			result.addAll(entry.getValue().keySet());
		}
        return result;
    }

    /**
     * 第3のキーのセットを取得します
     * @return 第3のキーのセット
     */
    public Set<U> thirdKeySet(){
    	Set<U> result=CommonUtils.linkedSet();
		for(Map.Entry<S, DoubleKeyMap<T,U,V>> entry:entrySet()){
			result.addAll(entry.getValue().secondKeySet());
		}
        return result;
    }

	/**
	 * 値のセットを取得します
	 */
	public Set<Map.Entry<S, DoubleKeyMap<T,U,V>>> entrySet(){
		return innerMap.entrySet();
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
		DoubleKeyMap<T,U,V> map2=innerMap.get(key1);
		if (map2==null){
			return;
		}
		map2.clear();
	}

	/**
	 * 第1、第2のキーに紐づく値をクリアします
	 * @param key1
	 */
	public void clear(S key1, T key2){
		DoubleKeyMap<T,U,V> map2=innerMap.get(key1);
		if (map2==null){
			return;
		}
		map2.clear(key2);
	}

	/**
	 * リストへの変換を行います
	 */
	public List<V> toList(){
		List<V> list=list();
		for(Map.Entry<S, DoubleKeyMap<T,U,V>> entry:entrySet()){
			for(Map.Entry<T,Map<U, V>> entry1:entry.getValue().entrySet()){
				for(Map.Entry<U, V> entry2:entry1.getValue().entrySet()){
					list.add(entry2.getValue());
				}
			}
		}
		return list;
	}

	/**
	 * ダブルキーマップリストへの変換を行います
	 */
	public DoubleKeyMap<S, T, List<V>> toDoubleKeyMapList(){
		DoubleKeyMap<S, T, List<V>> dKeyMap=doubleKeyMap();
		for(Map.Entry<S, DoubleKeyMap<T,U,V>> entry:entrySet()){
			for(Map.Entry<T,Map<U, V>> entry1:entry.getValue().entrySet()){
				List<V> list=list();
				for(Map.Entry<U, V> entry2:entry1.getValue().entrySet()){
					list.add(entry2.getValue());
				}
				dKeyMap.put(entry.getKey(), entry1.getKey(), list);
			}
		}
		return dKeyMap;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString(){
		StringBuilder builder=new StringBuilder("[");
		boolean first=true;
		for(Map.Entry<S, DoubleKeyMap<T,U,V>> entry:entrySet()){
			for(Map.Entry<T,Map<U, V>> entry1:entry.getValue().entrySet()){
				for(Map.Entry<U, V> entry2:entry1.getValue().entrySet()){
					if (!first){
						builder.append(", ");
					}
					builder.append("{");
					builder.append("(");
					builder.append(entry.getKey());
					builder.append(", ");
					builder.append(entry1.getKey());
					builder.append(", ");
					builder.append(entry2.getKey());
					builder.append(")=");
					builder.append(entry2.getValue());
					builder.append("}");
					first=false;
				}
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
		for(Map.Entry<S, DoubleKeyMap<T,U,V>> entry:this.innerMap.entrySet()){
			ret=ret+entry.getValue().size();
		}
		return ret;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public TripleKeyMap<S,T,U,V> clone(){
		TripleKeyMap<S,T,U,V> clone=new TripleKeyMap<S,T,U,V>();
		for(Map.Entry<S, DoubleKeyMap<T,U,V>> entry:innerMap.entrySet()){
			clone.innerMap.put(entry.getKey(), entry.getValue().clone());
		}
		return clone;
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
		if (!(obj instanceof TripleKeyMap)){
			return false;
		}
		TripleKeyMap<?,?,?,?> cst=(TripleKeyMap<?,?,?,?>)obj;
		if (!this.innerMap.equals(cst.innerMap)){
			return false;
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode(){
		return CommonUtils.hashCode(this.innerMap);
	}
	
	/**
	 * コレクションを第1キー、第2キー、第3キーを取得する関数を利用して変換します。
	 * @param c
	 * @param func1
	 * @param func2
	 * @param func3
	 */
	public static <S,T,U,V> TripleKeyMap<S,T,U,V> toMap(Collection<V> c, Function<V,S> func1, Function<V,T> func2, Function<V,U> func3){
		if (c==null){
			return CommonUtils.tripleKeyMap();
		}
		TripleKeyMap<S,T,U,V> result=CommonUtils.tripleKeyMap();
		c.forEach(v->{
			S key1=func1.apply(v);
			T key2=func2.apply(v);
			U key3=func3.apply(v);
			result.put(key1, key2, key3, v);
		});
		return result;
	}

	/**
	 * コレクションを第1キー、第2キー、第3キーを取得する関数を利用して値をリストとするマップに変換します。
	 * @param c
	 * @param func1
	 * @param func2
	 * @param func3
	 */
	public static <S,T,U,V> TripleKeyMap<S,T,U,List<V>> toListMap(Collection<V> c, Function<V,S> func1, Function<V,T> func2, Function<V,U> func3){
		if (c==null){
			return CommonUtils.tripleKeyMap();
		}
		TripleKeyMap<S,T,U,List<V>> result=CommonUtils.tripleKeyMap();
		c.forEach(v->{
			S key1=func1.apply(v);
			T key2=func2.apply(v);
			U key3=func3.apply(v);
			List<V> list=result.get(key1, key2, key3);
			if (list==null){
				list=CommonUtils.list();
				result.put(key1, key2, key3, list);
			}
			list.add(v);
		});
		return result;
	}
}
