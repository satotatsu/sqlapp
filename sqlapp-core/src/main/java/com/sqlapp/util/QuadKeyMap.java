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

package com.sqlapp.util;

import static com.sqlapp.util.CommonUtils.*;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
/**
 * 4つのキーを持つマップ
 * @author satoh
 *
 * @param <S> key1
 * @param <T> key2
 * @param <U> key3
 * @param <V> key4
 * @param <W> key5
 */
public class QuadKeyMap<S,T,U,V,W> implements Serializable, Cloneable{
	/** serialVersionUID */
	private static final long serialVersionUID = 1L;

	private Map<S, TripleKeyMap<T,U,V,W>> innerMap=linkedMap();
	/**
	 * 4つのキーを指定して値を取得します
	 * @param key1 第1キー
	 * @param key2 第2キー
	 * @param key3 第3キー
	 * @param key4 第4キー
	 * @return 値
	 */
	public W get(S key1, T key2, U key3, V key4){
		TripleKeyMap<T,U,V,W> map2=innerMap.get(key1);
		if (map2==null){
			return null;
		}
		return map2.get(key2, key3, key4);
	}

	/**
	 * 4つのキーを指定して値を設定します
	 * @param key1 第1キー
	 * @param key2 第2キー
	 * @param key3 第3キー
	 * @param key4 第4キー
	 * @param value 値
	 */
	public void put(S key1, T key2, U key3, V key4, W value){
		TripleKeyMap<T,U,V,W> map2=innerMap.get(key1);
		if (map2==null){
			map2=new TripleKeyMap<T,U,V,W>();
			innerMap.put(key1, map2);
		}
		map2.put(key2, key3, key4, value);
	}

	/**
	 * 4つのキーを指定して存在するかを確認します
	 * @param key1 第1キー
	 * @param key2 第2キー
	 * @param key3 第3キー
	 * @param key4 第4キー
	 * @return <code>true</code>:存在する
	 */
	public boolean containsKey(S key1, T key2, U key3, V key4){
		TripleKeyMap<T,U,V,W> map2=innerMap.get(key1);
		if (map2!=null){
			return map2.containsKey(key2, key3, key4);
		}
		return false;
	}

	/**
	 * 指定したキー4つの値を削除します
	 * @param key1 第1キー
	 * @param key2 第2キー
	 * @param key3 第3キー
	 * @param key4 第4キー
     * @return 削除された値
	 */
	public W remove(S key1, T key2, U key3, V key4){
		TripleKeyMap<T,U,V,W> map2=innerMap.get(key1);
		if (map2==null){
			return null;
		}
		return map2.remove(key2, key3, key4);
	}

	/**
	 * 第1のキーのセットを取得します
     * @return 第1のキーのセット
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
		for(Map.Entry<S, TripleKeyMap<T,U,V,W>> entry:entrySet()){
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
		for(Map.Entry<S, TripleKeyMap<T,U,V,W>> entry:entrySet()){
			result.addAll(entry.getValue().secondKeySet());
		}
        return result;
    }

    /**
     * 第4のキーのセットを取得します
     * @return 第4のキーのセット
     */
    public Set<V> fourthKeySet(){
    	Set<V> result=CommonUtils.linkedSet();
		for(Map.Entry<S, TripleKeyMap<T,U,V,W>> entry:entrySet()){
			result.addAll(entry.getValue().thirdKeySet());
		}
        return result;
    }

	/**
	 * 値のセットを取得します
	 */
	public Set<Map.Entry<S, TripleKeyMap<T,U,V,W>>> entrySet(){
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
	 * @param key1 第1キー
	 */
	public void clear(S key1){
		TripleKeyMap<T,U,V,W> map2=innerMap.get(key1);
		if (map2==null){
			return;
		}
		map2.clear();
	}

	/**
	 * 第1、第2のキーに紐づく値をクリアします
	 * @param key1 第1キー
	 * @param key2 第2キー
	 */
	public void clear(S key1, T key2){
		TripleKeyMap<T,U,V,W> map2=innerMap.get(key1);
		if (map2==null){
			return;
		}
		map2.clear(key2);
	}

	/**
	 * 第1、第2、第3のキーに紐づく値をクリアします
	 * @param key1 第1キー
	 * @param key2 第2キー
	 * @param key3 第3キー
	 */
	public void clear(S key1, T key2, U key3){
		TripleKeyMap<T,U,V,W> map2=innerMap.get(key1);
		if (map2==null){
			return;
		}
		map2.clear(key2, key3);
	}

	/**
	 * リストへの変換を行います
	 */
	public List<W> toList(){
		List<W> list=list();
		for(Map.Entry<S, TripleKeyMap<T,U,V,W>> entry:entrySet()){
			for(Map.Entry<T,DoubleKeyMap<U,V,W>> entry1:entry.getValue().entrySet()){
				for(Map.Entry<U,Map<V, W>> entry2:entry1.getValue().entrySet()){
					for(Map.Entry<V, W> entry3:entry2.getValue().entrySet()){
						list.add(entry3.getValue());
					}
				}
			}
		}
		return list;
	}

	/**
	 * トリプルキーマップリストへの変換を行います
	 */
	public TripleKeyMap<S,T,U,List<W>> toTripleKeyMapList(){
		TripleKeyMap<S,T,U, List<W>> tKeyMap=tripleKeyMap();
		for(Map.Entry<S, TripleKeyMap<T,U,V,W>> entry:entrySet()){
			for(Map.Entry<T,DoubleKeyMap<U,V,W>> entry1:entry.getValue().entrySet()){
				for(Map.Entry<U,Map<V, W>> entry2:entry1.getValue().entrySet()){
					List<W> list=list();
					for(Map.Entry<V, W> entry3:entry2.getValue().entrySet()){
						list.add(entry3.getValue());
					}
				}
			}
		}
		return tKeyMap;
	}

	/**
	 * サイズを返します
	 */
	public int size(){
		int ret=0;
		for(Map.Entry<S, TripleKeyMap<T,U,V,W>> entry:this.innerMap.entrySet()){
			ret=ret+entry.getValue().size();
		}
		return ret;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString(){
		StringBuilder builder=new StringBuilder("[");
		boolean first=true;
		for(Map.Entry<S, TripleKeyMap<T,U,V,W>> entry:entrySet()){
			for(Map.Entry<T,DoubleKeyMap<U,V,W>> entry1:entry.getValue().entrySet()){
				for(Map.Entry<U,Map<V, W>> entry2:entry1.getValue().entrySet()){
					for(Map.Entry<V, W> entry3:entry2.getValue().entrySet()){
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
						builder.append(", ");
						builder.append(entry3.getKey());
						builder.append(")=");
						builder.append(entry3.getValue());
						builder.append("}");
						first=false;
					}
				}
			}
		}
		builder.append("]");
		return builder.toString();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public QuadKeyMap<S,T,U,V,W> clone(){
		QuadKeyMap<S,T,U,V,W> clone=new QuadKeyMap<S,T,U,V,W>();
		for(Map.Entry<S, TripleKeyMap<T,U,V,W>> entry:innerMap.entrySet()){
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
		if (!(obj instanceof QuadKeyMap)){
			return false;
		}
		QuadKeyMap<?,?,?,?,?> cst=(QuadKeyMap<?,?,?,?,?>)obj;
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
	 * コレクションを第1キー、第2キー、第3キー、第4キーを取得する関数を利用して変換します。
	 * @param c collection
	 * @param func1
	 * @param func2
	 * @param func3
	 * @param func4
	 */
	public static <S,T,U,V,W> QuadKeyMap<S,T,U,V,W> toMap(Collection<W> c, Function<W,S> func1, Function<W,T> func2, Function<W,U> func3, Function<W,V> func4){
		if (c==null){
			return CommonUtils.quadKeyMap();
		}
		QuadKeyMap<S,T,U,V,W> result=CommonUtils.quadKeyMap();
		c.forEach(v->{
			S key1=func1.apply(v);
			T key2=func2.apply(v);
			U key3=func3.apply(v);
			V key4=func4.apply(v);
			result.put(key1, key2, key3, key4, v);
		});
		return result;
	}

	/**
	 * コレクションを第1キー、第2キー、第3キー、第4キーを取得する関数を利用して値をリストとするマップに変換します。
	 * @param c collection
	 * @param func1
	 * @param func2
	 * @param func3
	 * @param func4
	 */
	public static <S,T,U,V,W> QuadKeyMap<S,T,U,V,List<W>> toListMap(Collection<W> c, Function<W,S> func1, Function<W,T> func2, Function<W,U> func3, Function<W,V> func4){
		if (c==null){
			return CommonUtils.quadKeyMap();
		}
		QuadKeyMap<S,T,U,V,List<W>> result=CommonUtils.quadKeyMap();
		c.forEach(v->{
			S key1=func1.apply(v);
			T key2=func2.apply(v);
			U key3=func3.apply(v);
			V key4=func4.apply(v);
			List<W> list=result.get(key1, key2, key3, key4);
			if (list==null){
				list = CommonUtils.list();
				result.put(key1, key2, key3, key4, list);
			}
			list.add(v);
		});
		return result;
	}
}
