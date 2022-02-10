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

import static com.sqlapp.util.CommonUtils.caseInsensitiveMap;
import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.eqIgnoreCase;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.map;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.schemas.properties.CatalogNameProperty;
import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.util.CaseInsensitiveMap;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.SeparatedStringBuilder;

public abstract class AbstractNamedObjectCollection<T extends AbstractNamedObject<? super T>>
		extends AbstractDbObjectCollection<T> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 4540018510759477211L;
	/**
	 * 名称アクセスのためのマップ
	 */
	private transient Map<String, T> nameMap = null;
	/**
	 * ユニーク名称アクセスのためのマップ
	 */
	private transient Map<String, T> specificNameMap = null;

	/**
	 * コンストラクタ
	 */
	protected AbstractNamedObjectCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected AbstractNamedObjectCollection(DbCommonObject<?> parent) {
		super(parent);
	}
	
	/**
	 * コンストラクタ
	 * 
	 * @param catalog
	 */
	protected AbstractNamedObjectCollection(Catalog catalog) {
		super(catalog);
	}

	/**
	 * 名称アクセス時に大文字小文字を区別する
	 */
	private boolean caseSensitive = true;

	public boolean isCaseSensitive() {
		return caseSensitive;
	}

	public void setCaseSensitive(boolean caseSensitive) {
		if (this.caseSensitive != caseSensitive) {
			renew();
			int size = this.size();
			for (int i = 0; i < size; i++) {
				this.get(i).setCaseSensitive(caseSensitive);
			}
		}
		this.caseSensitive = caseSensitive;
	}

	protected Map<String, T> getNameMap() {
		if (nameMap == null) {
			if (caseSensitive) {
				nameMap = map(this.size());
			} else {
				nameMap = caseInsensitiveMap(this.size() * 4 / 3 + 1);
			}
			renew();
		}
		return nameMap;
	}

	protected Map<String, T> getSpecificNameMap() {
		if (specificNameMap == null) {
			if (caseSensitive) {
				specificNameMap = map(this.size() * 4 / 3 + 1);
			} else {
				specificNameMap = new CaseInsensitiveMap<T>(
						this.size() * 4 / 3 + 1);
			}
			renew();
		}
		return specificNameMap;
	}

	/**
	 * リストからマップの再生成
	 */
	@Override
	protected void renew() {
		Map<String, T> nameMap = getNameMap();
		Map<String, T> specificNameMap = getSpecificNameMap();
		nameMap.clear();
		specificNameMap.clear();
		int size = this.inner.size();
		for (int i = 0; i < size; i++) {
			T obj = this.inner.get(i);
			obj.setOrdinal(i);
			nameMap.put(obj.getName(), obj);
			specificNameMap.put(obj.getSpecificName(), obj);
		}
	}

	/**
	 * 名前を指定して値を取得します
	 * 
	 * @param name
	 */
	public T get(String name) {
		T obj = getSpecificNameMap().get(name);
		if (obj != null) {
			setElementParent(obj);
		} else {
			obj = getNameMap().get(name);
			if (obj != null) {
				setElementParent(obj);
			}
		}
		return obj;
	}

	/**
	 * 指定したオブジェクトに最も近いオブジェクトを取得します
	 * 
	 * @param obj
	 */
	@Override
	public T find(T obj) {
		T ret = getSpecificNameMap().get(obj.getSpecificName());
		if (ret == null) {
			ret = getNameMap().get(obj.getName());
		}
		return ret;
	}

	/**
	 * 名前を指定して値を取得する 名称とユニーク名称が異なる場合は、名称の一致する複数のオブジェクトを返す
	 * 
	 * @param name
	 */
	public List<T> find(String name) {
		List<T> result = list();
		for (int i = 0; i < this.size(); i++) {
			T obj = this.get(i);
			if (equalsIgnoreCase(obj.getName(), name)) {
				result.add(obj);
			}
		}
		return result;
	}

	/**
	 * 名前(複数)で値のリストを取得します
	 * 
	 * @param names
	 */
	public List<T> getAll(String... names) {
		int size = names.length;
		List<T> list = list(size);
		for (int i = 0; i < size; i++) {
			list.add(this.get(names[i]));
		}
		return list;
	}

	/**
	 * 名前(複数)で値のリストを取得します
	 * 
	 * @param names
	 */
	public List<T> getAll(List<String> names) {
		int size = names.size();
		List<T> list = list(size);
		for (int i = 0; i < size; i++) {
			list.add(this.get(names.get(i)));
		}
		return list;
	}

	/**
	 * 名前(複数)で値のリストを取得します
	 * 
	 * @param names
	 */
	public List<T> getAll(Collection<String> names) {
		List<T> list = list(names.size());
		for (String name : names) {
			T t = this.get(name);
			if (t != null) {
				list.add(t);
			}
		}
		return list;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.ArrayList#add(java.lang.Object)
	 */
	@Override
	public boolean add(T e) {
		if (!getAddDbObjectPredicate().test(this, e)) {
			return false;
		}
		boolean bool = false;
		beforeAdd(e);
		addSimple(e);
		initializeSchemaInfo(e);
		if (this.isValidateAtChange()) {
			renew();
		}
		afterAdd(e);
		e.validate();
		validate();
		return bool;
	}

	/**
	 * スキーマ情報の初期化
	 * 
	 * @param e
	 */
	protected void initializeSchemaInfo(T e) {
		if (equalsIgnoreCase(e.getCatalogName(), getCatalogName())) {
			e.setCatalogName(null);
		}
	}

	private String getCatalogName() {
		if (this instanceof HasParent) {
			HasParent<?> parent = (HasParent<?>) this;
			if (parent.getParent() == null) {
				return null;
			}
			if (parent.getParent() instanceof CatalogNameProperty) {
				String name = ((CatalogNameProperty<?>) parent.getParent())
						.getCatalogName();
				if (name != null) {
					return name;
				}
			}
		}
		return null;
	}

	protected boolean addSimple(T e) {
		boolean bool = false;
		T org = null;
		if (e.getSpecificName() == null || eq(e.getName(), e.getSpecificName())) {
			org = this.get(e.getName());
		} else {
			org = this.get(e.getSpecificName());
		}
		if (org != null) {
			e.cloneProperties(org);
		} else {
			bool = this.inner.add(e);
			this.getNameMap().put(e.getName(), e);
			this.getSpecificNameMap().put(e.getSpecificName(), e);
		}
		setElementParent(e);
		return bool;
	}

	void addSimple(int index, T e) {
		T org = null;
		if (e.getSpecificName() == null || eq(e.getName(), e.getSpecificName())) {
			org = this.get(e.getName());

		} else {
			org = this.get(e.getSpecificName());
		}
		if (org != null) {
			e.cloneProperties(org);
		} else {
			this.inner.add(index, e);
		}
		setElementParent(e);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.ArrayList#add(int, java.lang.Object)
	 */
	@Override
	public void add(int index, T element) {
		if (!getAddDbObjectPredicate().test(this, element)) {
			return;
		}
		beforeAdd(element);
		addSimple(index, element);
		initializeSchemaInfo(element);
		if (this.isValidateAtChange()) {
			renew();
		}
		afterAdd(element);
		element.validate();
		validate();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.ArrayList#addAll(java.util.Collection)
	 */
	@Override
	public boolean addAll(Collection<? extends T> c) {
		if (this == c) {
			return false;
		}
		for (T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			beforeAdd(t);
			setElementParent(t);
			initializeSchemaInfo(t);
		}
		for (T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			addSimple(t);
		}
		renew();
		for (T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			afterAdd(t);
		}
		validate();
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.ArrayList#addAll(int, java.util.Collection)
	 */
	@Override
	public boolean addAll(int index, Collection<? extends T> c) {
		for (T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			beforeAdd(t);
			setElementParent(t);
			initializeSchemaInfo(t);
		}
		for (T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			addSimple(t);
		}
		renew();
		for (T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			afterAdd(t);
		}
		validate();
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.ArrayList#clear()
	 */
	@Override
	public void clear() {
		super.clear();
		specificNameMap = null;
		nameMap = null;
	}

	/**
	 * 指定した名称のオブジェクトの存在チェック
	 * 
	 * @param name
	 */
	public boolean contains(String name) {
		if (this.getSpecificNameMap().containsKey(name)) {
			return true;
		}
		return getNameMap().containsKey(name);
	}

	/**
	 * 指定した名称のオブジェクトの存在チェック
	 * 
	 * @param names
	 */
	public boolean containsAll(String... names) {
		for (int i = 0; i < names.length; i++) {
			if (!getNameMap().containsKey(names[i])) {
				return false;
			}
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.AbstractCollection#containsAll(java.util.Collection)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public boolean containsAll(Collection<?> args) {
		for (Object arg : args) {
			if (arg instanceof String) {
				if (!contains((String) arg)) {
					return false;
				}
			} else {
				if (!contains((T) arg)) {
					return false;
				}
			}
		}
		return true;
	}

	public boolean contains(T o) {
		if (this.getSpecificNameMap().containsKey(o.getSpecificName())) {
			return true;
		}
		return getNameMap().containsKey(o.getName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.ArrayList#contains(java.lang.Object)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public boolean contains(Object o) {
		if (o instanceof String) {
			return this.contains((String) o);
		}
		return contains((T) o);
	}

	public boolean remove(String o) {
		T obj = this.get(o);
		this.beforeRemove(obj);
		boolean bool = this.inner.remove(obj);
		renew();
		this.afterRemove(obj);
		return bool;
	}

	/**
	 * 含まれる要素のユニーク名称一覧
	 * 
	 */
	public Set<String> getSpecificNames() {
		Set<String> result = com.sqlapp.util.CommonUtils.set();
		for (T obj : this) {
			result.add(obj.getSpecificName());
		}
		return result;
	}

	/**
	 * 含まれる要素の名称セット
	 * 
	 */
	public Set<String> getNameSet() {
		return getNameMap().keySet();
	}

	public Set<Map.Entry<String, T>> entrySet() {
		return getNameMap().entrySet();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.AbstractCollection#toString()
	 */
	@Override
	public String toString() {
		SeparatedStringBuilder builder = new SeparatedStringBuilder("\n");
		builder.add(this.inner);
		return builder.toString();
	}

	@Override
	public AbstractNamedObjectCollection<T> clone() {
		return cast(super.clone());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.AbstractList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof AbstractNamedObjectCollection<?>)) {
			return false;
		}
		if (equalsHandler.referenceEquals(this, obj)) {
			return true;
		}
		if (!(obj instanceof AbstractNamedObjectCollection<?>)) {
			return false;
		}
		@SuppressWarnings("unchecked")
		AbstractNamedObjectCollection<T> val = (AbstractNamedObjectCollection<T>) obj;
		if (!equalsElements(val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/**
	 * 全ての要素が等しいかを判定します
	 * 
	 * @param val
	 * @param equalsHandler
	 */
	protected boolean equalsElements(AbstractNamedObjectCollection<T> val,
			EqualsHandler equalsHandler) {
		if (!equalsHandler.valueEquals("size", this, val,
				this.inner.size(), val.inner.size(), EqualsUtils.getEqualsSupplier(this.inner.size(), val.inner.size()))) {
			return false;
		}
		int size = Math.max(this.size(), val.size());
		Set<T> set=CommonUtils.set();
		for (int i = 0; i < size; i++) {
			T thisObj1 = null;
			if (i < this.size()) {
				thisObj1 = this.inner.get(i);
			}
			T thisObj2 = null;
			if (thisObj1!=null){
				thisObj2 = val.find(thisObj1);
			} else{
				if (i < val.size()) {
					thisObj2 = val.inner.get(i);
				}
			}
			if (thisObj2!=null){
				if (!set.contains(thisObj2)){
					set.add(thisObj2);
					if (!equalsElement(thisObj1, thisObj2, equalsHandler)) {
						return false;
					}
				}
			} else{
				if (!equalsElement(thisObj1, thisObj2, equalsHandler)) {
					return false;
				}
			}
		}
		return true;
	}
	
	/**
	 * 大文字小文字を無視した比較を行います
	 * 
	 * @param obj1
	 * @param obj2
	 */
	public boolean equalsIgnoreCase(String obj1, String obj2) {
		if (this.isCaseSensitive()) {
			return eq(obj1, obj2);
		}
		return eqIgnoreCase(obj1, obj2);
	}

	@Override
	protected void setDiffAll(SeparatedStringBuilder builder) {
		SeparatedStringBuilder sepName = new SeparatedStringBuilder(",");
		sepName.setStart("{").setEnd("}");
		sepName.addNames(this);
		builder.add(sepName.toString());
	}

	@SuppressWarnings("unchecked")
	protected void cloneProperties(AbstractNamedObjectCollection<T> obj) {
		int size = this.size();
		obj.caseSensitive = this.caseSensitive;
		for (int i = 0; i < size; i++) {
			obj.add((T) this.get(i).clone());
		}
		Set<ISchemaProperty> properties=SchemaUtils.getAllSchemaProperties(this.getClass());
		for(ISchemaProperty prop:properties){
			Object value=prop.getCloneValue(this);
			prop.setValue(obj, value);
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected AbstractNamedObjectCollectionXmlReaderHandler<?> getDbObjectXmlReaderHandler(){
		if (this instanceof NewElement){
			NewElement<?,?> newElement=(NewElement<?,?>)this;
			AbstractBaseDbObject<?> dbObject=(AbstractBaseDbObject<?>)newElement.newElement();
			return new AbstractNamedObjectCollectionXmlReaderHandler(this.newInstance()) {
				@Override
				protected void initializeSetValue() {
					super.initializeSetValue();
					setChild(dbObject.getDbObjectXmlReaderHandler());
				}
			};
		}
		return null;
	}

}
