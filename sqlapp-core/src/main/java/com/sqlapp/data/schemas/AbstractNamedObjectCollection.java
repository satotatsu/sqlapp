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
	protected AbstractNamedObjectCollection(final DbCommonObject<?> parent) {
		super(parent);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param catalog
	 */
	protected AbstractNamedObjectCollection(final Catalog catalog) {
		super(catalog);
	}

	/**
	 * 名称アクセス時に大文字小文字を区別する
	 */
	private boolean caseSensitive = true;

	public boolean isCaseSensitive() {
		return caseSensitive;
	}

	public void setCaseSensitive(final boolean caseSensitive) {
		if (this.caseSensitive == caseSensitive) {
			return;
		}
		this.caseSensitive = caseSensitive;
		// The cached maps compare keys in the previous mode; recreate them lazily.
		this.nameMap = null;
		this.specificNameMap = null;
		final int size = this.size();
		for (int i = 0; i < size; i++) {
			this.get(i).setCaseSensitive(caseSensitive);
		}
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
				specificNameMap = new CaseInsensitiveMap<T>(this.size() * 4 / 3 + 1);
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
		final Map<String, T> nameMap = getNameMap();
		final Map<String, T> specificNameMap = getSpecificNameMap();
		nameMap.clear();
		specificNameMap.clear();
		final int size = this.inner.size();
		for (int i = 0; i < size; i++) {
			final T obj = this.inner.get(i);
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
	public T get(final String name) {
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
	public T find(final T obj) {
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
	public List<T> find(final String name) {
		final List<T> result = list();
		for (int i = 0; i < this.size(); i++) {
			final T obj = this.get(i);
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
	public List<T> getAll(final String... names) {
		final int size = names.length;
		final List<T> list = list(size);
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
	public List<T> getAll(final List<String> names) {
		final int size = names.size();
		final List<T> list = list(size);
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
	public List<T> getAll(final Collection<String> names) {
		final List<T> list = list(names.size());
		for (final String name : names) {
			final T t = this.get(name);
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
	/**
	 * Adds the element unless the predicate or {@link #beforeAdd} rejects it. An
	 * element with the same name, class and ID as an existing element is merged
	 * into that element instead of being inserted.
	 *
	 * @return {@code true} when the element was inserted as a new element
	 */
	@Override
	public boolean add(final T e) {
		if (!getAddDbObjectPredicate().test(this, e)) {
			return false;
		}
		if (!beforeAdd(e)) {
			return false;
		}
		final boolean added = addSimple(e);
		initializeSchemaInfo(e);
		if (this.isValidateAtChange()) {
			renew();
		}
		afterAdd(e);
		e.validate();
		validate();
		return added;
	}

	/**
	 * スキーマ情報の初期化
	 * 
	 * @param e
	 */
	protected void initializeSchemaInfo(final T e) {
		if (equalsIgnoreCase(e.getCatalogName(), getCatalogName())) {
			e.setCatalogName(null);
		}
	}

	private String getCatalogName() {
		if (this instanceof HasParent) {
			final HasParent<?> parent = (HasParent<?>) this;
			if (parent.getParent() == null) {
				return null;
			}
			if (parent.getParent() instanceof CatalogNameProperty) {
				final String name = ((CatalogNameProperty<?>) parent.getParent()).getCatalogName();
				if (name != null) {
					return name;
				}
			}
		}
		return null;
	}

	protected boolean addSimple(final T e) {
		boolean bool = false;
		T org = null;
		if (e.getSpecificName() == null || eq(e.getName(), e.getSpecificName())) {
			org = this.get(e.getName());
		} else {
			org = this.get(e.getSpecificName());
		}
		if (org != null && org.getClass().equals(e.getClass()) && eq(org.getId(), e.getId())) {
			e.cloneProperties(org);
		} else {
			bool = this.inner.add(e);
			this.getNameMap().put(e.getName(), e);
			this.getSpecificNameMap().put(e.getSpecificName(), e);
		}
		setElementParent(e);
		return bool;
	}

	void addSimple(final int index, final T e) {
		T org = null;
		if (e.getSpecificName() == null || eq(e.getName(), e.getSpecificName())) {
			org = this.get(e.getName());

		} else {
			org = this.get(e.getSpecificName());
		}
		if (org != null && org.getClass().equals(e.getClass()) && eq(org.getId(), e.getId())) {
			e.cloneProperties(org);
		} else {
			this.inner.add(index, e);
			this.getNameMap().put(e.getName(), e);
			this.getSpecificNameMap().put(e.getSpecificName(), e);
		}
		setElementParent(e);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.ArrayList#add(int, java.lang.Object)
	 */
	@Override
	public void add(final int index, final T element) {
		if (!getAddDbObjectPredicate().test(this, element)) {
			return;
		}
		if (!beforeAdd(element)) {
			return;
		}
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
	/**
	 * Adds the elements accepted by the predicate and {@link #beforeAdd}.
	 *
	 * @return {@code true} when at least one new element was inserted
	 */
	@Override
	public boolean addAll(final Collection<? extends T> c) {
		if (this == c) {
			return false;
		}
		final List<T> accepted = prepareAddAll(c);
		boolean changed = false;
		for (final T t : accepted) {
			changed |= addSimple(t);
		}
		completeAddAll(accepted);
		return changed;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.ArrayList#addAll(int, java.util.Collection)
	 */
	/**
	 * Inserts the elements accepted by the predicate and {@link #beforeAdd} at the
	 * specified position, preserving their order. Elements merged into an existing
	 * element do not occupy a position.
	 *
	 * @return {@code true} when at least one new element was inserted
	 */
	@Override
	public boolean addAll(final int index, final Collection<? extends T> c) {
		if (index < 0 || index > this.size()) {
			throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + this.size());
		}
		final List<T> accepted = prepareAddAll(this == c ? new java.util.ArrayList<T>(c) : c);
		int cursor = index;
		for (final T t : accepted) {
			// addSimple keeps the subclass naming and merge rules; move only a newly
			// appended element to its requested position.
			if (addSimple(t)) {
				this.inner.add(cursor++, this.inner.remove(this.inner.size() - 1));
			}
		}
		completeAddAll(accepted);
		return cursor != index;
	}

	private List<T> prepareAddAll(final Collection<? extends T> c) {
		final List<T> accepted = list(c.size());
		for (final T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			if (!beforeAdd(t)) {
				continue;
			}
			setElementParent(t);
			initializeSchemaInfo(t);
			accepted.add(t);
		}
		return accepted;
	}

	private void completeAddAll(final List<T> accepted) {
		renew();
		for (final T t : accepted) {
			afterAdd(t);
		}
		validate();
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
	public boolean contains(final String name) {
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
	public boolean containsAll(final String... names) {
		for (int i = 0; i < names.length; i++) {
			if (!contains(names[i])) {
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
	public boolean containsAll(final Collection<?> args) {
		for (final Object arg : args) {
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

	public boolean contains(final T o) {
		if (o == null) {
			return false;
		}
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
	public boolean contains(final Object o) {
		if (o instanceof String) {
			return this.contains((String) o);
		}
		if (o instanceof AbstractNamedObject) {
			return contains((T) o);
		}
		return false;
	}

	public boolean remove(final String o) {
		final T obj = this.get(o);
		if (obj == null) {
			return false;
		}
		final int size = this.inner.size();
		for (int i = 0; i < size; i++) {
			if (this.inner.get(i) == obj) {
				removeAt(i);
				return true;
			}
		}
		return false;
	}

	@Override
	protected void structureChanged() {
		if (this.isValidateAtChange()) {
			renew();
		} else {
			// renew() is deferred; name lookups must not return removed or replaced
			// elements meanwhile, so rebuild them lazily.
			this.nameMap = null;
			this.specificNameMap = null;
		}
	}

	/**
	 * 含まれる要素のユニーク名称一覧
	 * 
	 */
	public Set<String> getSpecificNames() {
		final Set<String> result = com.sqlapp.util.CommonUtils.set();
		for (final T obj : this) {
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
		final SeparatedStringBuilder builder = new SeparatedStringBuilder("\n");
		builder.add(this.inner);
		return builder.toString();
	}

	@Override
	public AbstractNamedObjectCollection<T> clone() {
		final AbstractNamedObjectCollection<T> clone = cast(super.clone());
		clone.setCaseSensitive(this.caseSensitive);
		return clone;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.AbstractList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj, final EqualsHandler equalsHandler) {
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
		final AbstractNamedObjectCollection<T> val = (AbstractNamedObjectCollection<T>) obj;
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
	protected boolean equalsElements(final AbstractNamedObjectCollection<T> val, final EqualsHandler equalsHandler) {
		if (!equalsHandler.valueEquals("size", this, val, this.inner.size(), val.inner.size(),
				EqualsUtils.getEqualsSupplier(this.inner.size(), val.inner.size()))) {
			return false;
		}
		final int size = Math.max(this.size(), val.size());
		final Set<T> set = CommonUtils.set();
		for (int i = 0; i < size; i++) {
			T thisObj1 = null;
			if (i < this.size()) {
				thisObj1 = this.inner.get(i);
			}
			T thisObj2 = null;
			if (thisObj1 != null) {
				thisObj2 = val.find(thisObj1);
			} else {
				if (i < val.size()) {
					thisObj2 = val.inner.get(i);
				}
			}
			if (thisObj2 != null) {
				if (!set.contains(thisObj2)) {
					set.add(thisObj2);
					if (!equalsElement(thisObj1, thisObj2, equalsHandler)) {
						return false;
					}
				}
			} else {
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
	public boolean equalsIgnoreCase(final String obj1, final String obj2) {
		if (this.isCaseSensitive()) {
			return eq(obj1, obj2);
		}
		return eqIgnoreCase(obj1, obj2);
	}

	@Override
	protected void setDiffAll(final SeparatedStringBuilder builder) {
		final SeparatedStringBuilder sepName = new SeparatedStringBuilder(",");
		sepName.setStart("{").setEnd("}");
		sepName.addNames(this);
		builder.add(sepName.toString());
	}

	@SuppressWarnings("unchecked")
	protected void cloneProperties(final AbstractNamedObjectCollection<T> obj) {
		final int size = this.size();
		obj.setCaseSensitive(this.caseSensitive);
		for (int i = 0; i < size; i++) {
			obj.add((T) this.get(i).clone());
		}
		final Set<ISchemaProperty> properties = SchemaUtils.getAllSchemaProperties(this.getClass());
		for (final ISchemaProperty prop : properties) {
			final Object value = prop.getCloneValue(this);
			prop.setValue(obj, value);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected AbstractNamedObjectCollectionXmlReaderHandler<?> getDbObjectXmlReaderHandler() {
		if (this instanceof NewElement) {
			final NewElement<?, ?> newElement = (NewElement<?, ?>) this;
			final AbstractBaseDbObject<?> dbObject = (AbstractBaseDbObject<?>) newElement.newElement();
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
