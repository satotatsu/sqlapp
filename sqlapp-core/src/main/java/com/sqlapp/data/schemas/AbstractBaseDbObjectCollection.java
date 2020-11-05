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
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.FileUtils.close;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.AbstractObjectXmlReaderHandler.ChildObjectHolder;
import com.sqlapp.data.schemas.function.AddDbObjectPredicate;
import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.HashCodeBuilder;
import com.sqlapp.util.SeparatedStringBuilder;
import com.sqlapp.util.StaxReader;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.StringUtils;
import com.sqlapp.util.xml.ResultHandler;

abstract class AbstractBaseDbObjectCollection<T extends AbstractBaseDbObject<? super T>>
		implements DbObjectCollection<T> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 4540018510759477211L;
	/**
	 * 内部のリスト
	 */
	protected ArrayList<T> inner = new ArrayList<T>();
	/**
	 * 親のオブジェクト
	 */
	private DbCommonObject<?> parent = null;
	/**
	 * 変更時のバリデートを有効化
	 */
	private transient boolean validateAtChange = true;
	/**
	 * 追加対象オブジェクト判定ハンドラー
	 */
	private transient AddDbObjectPredicate addDbObjectPredicate = (p,c)->true;

	/**
	 * コンストラクタ
	 */
	protected AbstractBaseDbObjectCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected AbstractBaseDbObjectCollection(DbCommonObject<?> parent) {
		this.parent = parent;
	}

	/**
	 * @return the addDbObjectPredicate
	 */
	protected AddDbObjectPredicate getAddDbObjectPredicate() {
		if (addDbObjectPredicate == null) {
			addDbObjectPredicate = (p,c)->true;
		}
		return addDbObjectPredicate;
	}

	/**
	 * @param addDbObjectPredicate
	 *            the addDbObjectPredicate to set
	 */
	public void setAddDbObjectPredicate(AddDbObjectPredicate addDbObjectPredicate) {
		this.addDbObjectPredicate = addDbObjectPredicate;
	}

	/**
	 * @return the validateAtChange
	 */
	protected boolean isValidateAtChange() {
		return validateAtChange;
	}

	/**
	 * @param validateAtChange
	 *            the validateAtChange to set
	 */
	protected void setValidateAtChange(boolean validateAtChange) {
		this.validateAtChange = validateAtChange;
	}

	protected DbCommonObject<?> getParent() {
		return this.parent;
	}
	
	@SuppressWarnings("unchecked")
	protected <S extends AbstractBaseDbObjectCollection<?>> S setParent(
			DbCommonObject<?> parent) {
		this.parent = cast(parent);
		return (S) (this);
	}

	/**
	 * 追加前のメソッド
	 */
	protected boolean beforeAdd(T args) {
		return true;
	}

	/**
	 * 追加後のメソッド
	 */
	protected void afterAdd(T args) {
	}

	/**
	 * 削除前のメソッド
	 */
	protected boolean beforeRemove(T args) {
		return true;
	}

	/**
	 * 削除後のメソッド
	 */
	protected void afterRemove(T args) {
	}

	/**
	 * IDを指定してオブジェクトを取得します
	 * 
	 * @param id
	 */
	public T getById(String id) {
		int size = inner.size();
		for (int i = 0; i < size; i++) {
			T t = inner.get(i);
			if (eq(t.getId(), id)) {
				setElementParent(t);
				return t;
			}
		}
		return null;
	}

	/**
	 * リストからマップの再生成
	 */
	protected void renew() {
		int size = inner.size();
		for (int i = 0; i < size; i++) {
			T obj = inner.get(i);
			obj.setOrdinal(i);
		}
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
		if (!beforeAdd(e)) {
			return false;
		}
		setElementParent(e);
		bool = inner.add(e);
		if (this.isValidateAtChange()) {
			renew();
		}
		afterAdd(e);
		e.validate();
		return bool;
	}

	/**
	 * 引数で指定された要素の親として自オブジェクトを設定します
	 * 
	 * @param e
	 */
	protected void setElementParent(T e) {
		e.setParent(this);
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
		if (!beforeAdd(element)) {
			return;
		}
		setElementParent(element);
		if (inner.contains(element)) {
			return;
		}
		inner.add(index, element);
		if (this.isValidateAtChange()) {
			renew();
		}
		afterAdd(element);
		element.validate();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.ArrayList#addAll(java.util.Collection)
	 */
	@Override
	public boolean addAll(Collection<? extends T> c) {
		Set<T> set = CommonUtils.set(c.size());
		for (T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			if (!beforeAdd(t)) {
				set.add(t);
			}
		}
		boolean bool = false;
		for (T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			if (set.contains(t)) {
				continue;
			}
			T findObj = find(t);
			if (findObj == null) {
				bool = inner.add(t);
				setElementParent(t);
			}
		}
		renew();
		for (T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			if (set.contains(t)) {
				continue;
			}
			afterAdd(t);
		}
		return bool;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.ArrayList#addAll(int, java.util.Collection)
	 */
	@Override
	public boolean addAll(int index, Collection<? extends T> c) {
		if (this == c) {
			return false;
		}
		Set<T> set = CommonUtils.set(c.size());
		for (T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			if (!beforeAdd(t)) {
				set.add(t);
			}
		}
		boolean bool = false;
		for (T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			if (set.contains(t)) {
				continue;
			}
			T findObj = find(t);
			if (findObj == null) {
				bool = inner.add(t);
				setElementParent(t);
			}
		}
		renew();
		for (T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			if (set.contains(t)) {
				continue;
			}
			afterAdd(t);
		}
		return bool;
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
			if (!contains((T) arg)) {
				return false;
			}
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean remove(Object o) {
		this.beforeRemove((T) o);
		boolean bool = inner.remove(o);
		if (this.isValidateAtChange()) {
			renew();
		}
		if (bool) {
			((T) o).setParent(null);
		}
		this.afterRemove((T) o);
		return bool;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.ArrayList#remove(int)
	 */
	@Override
	public T remove(int index) {
		T obj = this.get(index);
		this.beforeRemove((T) obj);
		T rm = inner.remove(index);
		if (this.isValidateAtChange()) {
			renew();
		}
		if (rm != null) {
			rm.setParent(null);
		}
		this.afterRemove((T) obj);
		return obj;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.AbstractCollection#removeAll(java.util.Collection)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public boolean removeAll(Collection<?> c) {
		boolean result = true;
		for (Object o : c) {
			boolean bool = inner.remove(o);
			if (!bool) {
				result = false;
			} else {
				((T) o).setParent(null);
			}
		}
		renew();
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.ArrayList#set(int, java.lang.Object)
	 */
	@Override
	public T set(int index, T element) {
		T obj = inner.set(index, element);
		element.setOrdinal(index);
		if (this.isValidateAtChange()) {
			renew();
		}
		return obj;
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#toStringSimple()
	 */
	@Override
	public String toStringSimple() {
		return toString();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public AbstractBaseDbObjectCollection<T> clone() {
		AbstractBaseDbObjectCollection clone = (AbstractBaseDbObjectCollection)this.newInstance().get();
		List<T> clones=CommonUtils.list();
		for(T obj:this) {
			clones.add((T)obj.clone());
		}
		clone.addAll(clones);
		return clone;
	}

	@Override
	public boolean equals(Object obj) {
		return this.equals(obj, EqualsHandler.getInstance());
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (equalsHandler.referenceEquals(this, obj)) {
			return true;
		}
		if (!(obj instanceof AbstractBaseDbObjectCollection<?>)) {
			return false;
		}
		@SuppressWarnings("unchecked")
		AbstractBaseDbObjectCollection<T> val = (AbstractBaseDbObjectCollection<T>) obj;
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
	private boolean equalsElements(AbstractBaseDbObjectCollection<T> val,
			EqualsHandler equalsHandler) {
		if (!equalsHandler.valueEquals("size", this, val,
				this.inner.size(), val.inner.size(), EqualsUtils.getEqualsSupplier(this.inner.size(), val.inner.size()))) {
			return false;
		}
		int size = this.inner.size();
		for (int i = 0; i < size; i++) {
			T thisObj1 = this.inner.get(i);
			T thisObj2 = null;
			if (i < val.inner.size()) {
				thisObj2 = val.inner.get(i);
			}
			if (!equalsElement(thisObj1, thisObj2, equalsHandler)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 要素が等しいかを判定します
	 * 
	 * @param t1
	 * @param t2
	 * @param equalsHandler
	 */
	protected boolean likeElement(T t1, T t2, EqualsHandler equalsHandler) {
		if (t1 == null) {
			if (t2 == null) {
				return true;
			} else {
				return false;
			}
		}
		return t1.like(t2, equalsHandler);
	}

	/**
	 * 要素が等しいかを判定します
	 * 
	 * @param t1
	 * @param t2
	 * @param equalsHandler
	 */
	protected boolean equalsElement(T t1, T t2, EqualsHandler equalsHandler) {
		if (t1 == null) {
			if (t2 == null) {
				return true;
			} else {
				return false;
			}
		}
		return t1.equals(t2, equalsHandler);
	}

	protected boolean equals(String propertyName, T target1, T target2,
			Object value1, Object value2, EqualsHandler equalsHandler) {
		return equalsHandler.valueEquals(propertyName, target1,
				target2, value1, value2, EqualsUtils.getEqualsSupplier(value1, value2));
	}

	@Override
	public int hashCode() {
		HashCodeBuilder builder = new HashCodeBuilder();
		builder.append(inner);
		return builder.hashCode();
	}

	/**
	 * ReaderからXMLを読み込みます
	 * 
	 * @param reader
	 * @param options
	 * @throws XMLStreamException
	 */
	@Override
	public void loadXml(Reader reader, XmlReaderOptions options) throws XMLStreamException {
		StaxReader staxReader = new StaxReader(reader);
		AbstractBaseDbObjectCollectionXmlReaderHandler<?> handler = getDbObjectXmlReaderHandler();
		handler.setReaderOptions(options);
		ChildObjectHolder holder = new ChildObjectHolder(this);
		ResultHandler resultHandler = new ResultHandler();
		resultHandler.registerChild(handler);
		resultHandler.handle(staxReader, holder);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#loadXml(java.io.InputStream)
	 */
	@Override
	public void loadXml(InputStream stream, XmlReaderOptions options) throws XMLStreamException {
		StaxReader staxReader = new StaxReader(stream);
		AbstractBaseDbObjectCollectionXmlReaderHandler<?> handler = getDbObjectXmlReaderHandler();
		handler.setReaderOptions(options);
		ChildObjectHolder holder = new ChildObjectHolder(this);
		ResultHandler resultHandler = new ResultHandler();
		resultHandler.registerChild(handler);
		resultHandler.handle(staxReader, holder);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#loadXml(java.lang.String)
	 */
	@Override
	public void loadXml(String path, XmlReaderOptions options) throws XMLStreamException,
			FileNotFoundException {
		InputStream stream = null;
		BufferedInputStream bis = null;
		try {
			stream = FileUtils.getInputStream(path);
			if (stream == null) {
				throw new FileNotFoundException("path=" + path);
			}
			bis = new BufferedInputStream(stream);
			loadXml(bis, options);
		} finally {
			close(stream);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#loadXml(java.io.File)
	 */
	@Override
	public void loadXml(File file, XmlReaderOptions options) throws XMLStreamException,
			FileNotFoundException {
		InputStream stream = null;
		BufferedInputStream bis = null;
		try {
			stream = new FileInputStream(file);
			bis = new BufferedInputStream(stream);
			loadXml(bis, options);
		} finally {
			close(stream);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected AbstractBaseDbObjectCollectionXmlReaderHandler<?> getDbObjectXmlReaderHandler(){
		if (this instanceof NewElement){
			NewElement<?,?> newElement=(NewElement<?,?>)this;
			AbstractBaseDbObject<?> dbObject=(AbstractBaseDbObject<?>)newElement.newElement();
			return new AbstractBaseDbObjectCollectionXmlReaderHandler(this.newInstance()) {
				@Override
				protected void initializeSetValue() {
					super.initializeSetValue();
					setChild(dbObject.getDbObjectXmlReaderHandler());
				}
			};
		}
		return null;
	}

	/**
	 * ストリームにXMLとして書き込みます
	 * 
	 * @param stream
	 * @throws XMLStreamException
	 */
	@Override
	public void writeXml(OutputStream stream) throws XMLStreamException {
		StaxWriter stax = new StaxWriter(stream) {
			@Override
			protected boolean isWriteStartDocument() {
				return true;
			}
		};
		writeXml(stax);
	}

	/**
	 * WriterにXMLとして書き込みます
	 * 
	 * @param writer
	 * @throws XMLStreamException
	 */
	@Override
	public void writeXml(Writer writer) throws XMLStreamException {
		StaxWriter stax = new StaxWriter(writer) {
			@Override
			protected boolean isWriteStartDocument() {
				return true;
			}
		};
		writeXml(stax);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DbCommonObject#writeXml(com.sqlapp.util.StaxWriter
	 * )
	 */
	@Override
	public void writeXml(StaxWriter stax) throws XMLStreamException {
		writeXml(getSimpleName(), stax);
	}

	/**
	 * XML書き出し
	 * 
	 * @param name
	 *            書き出す要素名
	 * @param stax
	 * @throws XMLStreamException
	 */
	public void writeXml(String name, StaxWriter stax)
			throws XMLStreamException {
		int size = this.size();
		stax.newLine();
		stax.indent();
		stax.writeStartElement(name);
		writeXmlOptionalAttributes(stax);
		stax.addIndentLevel(1);
		for (int i = 0; i < size; i++) {
			T obj = this.get(i);
			obj.writeXml(stax);
		}
		stax.addIndentLevel(-1);
		stax.newLine();
		stax.indent();
		stax.writeEndElement();
	}
	
	/**
	 * XML書き込みでオプション属性を書き込みます
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#writeXml(java.lang.String)
	 */
	@Override
	public void writeXml(String path) throws XMLStreamException, IOException {
		writeXml(new File(path));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#writeXml(java.io.File)
	 */
	@Override
	public void writeXml(File file) throws XMLStreamException, IOException {
		FileOutputStream fos = null;
		BufferedOutputStream stream = null;
		try {
			fos = new FileOutputStream(file);
			stream = new BufferedOutputStream(fos);
			StaxWriter stax = new StaxWriter(stream);
			writeXml(stax);
			stream.flush();
		} finally {
			close(stream);
			close(fos);
		}
	}

	protected String SIMPLE_NAME = getSimpleName(this.getClass());

	/**
	 * XMLでのタグ名
	 * 
	 */
	protected String getSimpleName() {
		return SIMPLE_NAME;
	}

	private static final Map<Class<?>, String> CACHE = new HashMap<Class<?>, String>();

	/**
	 * XMLでのタグ名を取得します
	 * 
	 */
	public static String getSimpleName(final Class<?> clazz) {
		String name = CACHE.get(clazz);
		if (name != null) {
			return name;
		}
		name = getSimpleNameNoCache(clazz);
		CACHE.put(clazz, name);
		return name;
	}

	private static String getSimpleNameNoCache(final Class<?> clazz) {
		if (clazz == ReferenceColumnCollection.class) {
			return "columns";
		}
		if (clazz == DimensionLevelColumnCollection.class) {
			return "columns";
		}
		return StringUtils.uncapitalize(SchemaUtils.getPluralName(clazz));
	}

	@SuppressWarnings("unchecked")
	protected void cloneProperties(AbstractBaseDbObjectCollection<T> obj) {
		int size = this.size();
		for (int i = 0; i < size; i++) {
			obj.add((T) this.get(i).clone());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DbObjectCollection#diff(com.sqlapp.data.schemas
	 * .DbObjectCollection)
	 */
	@Override
	public DbObjectDifferenceCollection diff(DbObjectCollection<T> obj) {
		DbObjectDifferenceCollection diff = new DbObjectDifferenceCollection(
				this, obj);
		return diff;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DbObjectCollection#diff(com.sqlapp.data.schemas
	 * .DbObjectCollection, com.sqlapp.data.schemas.EqualsHandler)
	 */
	@Override
	public DbObjectDifferenceCollection diff(DbObjectCollection<T> obj,
			EqualsHandler equalsHandler) {
		DbObjectDifferenceCollection diff = new DbObjectDifferenceCollection(
				this, obj, equalsHandler);
		return diff;
	}

	protected void setDiffAll(SeparatedStringBuilder builder) {
	}

	@Override
	public int size() {
		return this.inner.size();
	}

	@Override
	public boolean isEmpty() {
		return this.inner.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return this.inner.contains(o);
	}

	@Override
	public Iterator<T> iterator() {
		return this.inner.iterator();
	}

	@Override
	public Object[] toArray() {
		return this.inner.toArray();
	}

	@SuppressWarnings("hiding")
	@Override
	public <T> T[] toArray(T[] a) {
		return this.inner.toArray(a);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return this.inner.retainAll(c);
	}

	@Override
	public void clear() {
		this.inner.clear();
	}

	@Override
	public T get(int index) {
		T ret = this.inner.get(index);
		if (ret != null) {
			setElementParent(ret);
		}
		return ret;
	}

	@Override
	public int indexOf(Object o) {
		return this.inner.indexOf(o);
	}

	@Override
	public int lastIndexOf(Object o) {
		return this.inner.lastIndexOf(o);
	}

	@Override
	public ListIterator<T> listIterator() {
		return this.inner.listIterator();
	}

	@Override
	public ListIterator<T> listIterator(int index) {
		return this.inner.listIterator(index);
	}

	@Override
	public List<T> subList(int fromIndex, int toIndex) {
		return this.inner.subList(fromIndex, toIndex);
	}

	protected void validate() {
		if (this.isValidateAtChange()) {
			renew();
		}
		validateAllElement();
	}

	protected void validateAllElement() {
		for (T obj : this.inner) {
			obj.validate();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Sortable#sort()
	 */
	@Override
	public void sort() {
		if (!(this instanceof UnOrdered)) {
			Collections.sort(this.inner);
		}
		renew();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Sortable#sort(java.util.Comparator)
	 */
	@Override
	public void sort(Comparator<? super T> comparator) {
		Collections.sort(this.inner, comparator);
		renew();
	}

	private static final Map<Class<?>, Class<?>> COLLECTION_TYPE_CACHE = new HashMap<Class<?>, Class<?>>();

	/**
	 * コレクションのクラスを取得します
	 * 
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Class<T> getType() {
		Class<?> clazz = this.getClass();
		Class<?> typeClazz = COLLECTION_TYPE_CACHE.get(clazz);
		if (typeClazz != null) {
			return (Class<T>) typeClazz;
		}
		java.lang.reflect.Type type = clazz.getGenericSuperclass();
		ParameterizedType pType = (ParameterizedType) type;
		java.lang.reflect.Type[] aTypes = pType.getActualTypeArguments();
		typeClazz = (Class<T>) aTypes[0];
		COLLECTION_TYPE_CACHE.put(clazz, typeClazz);
		return (Class<T>) typeClazz;
	}

	/**
	 * 指定したオブジェクトに最も近いオブジェクトを取得します
	 * 
	 * @param obj
	 */
	@Override
	public T find(T obj) {
		if (obj == null) {
			return null;
		}
		for (T val : this.inner) {
			if (val.like(obj)) {
				return val;
			}
		}
		return null;
	}

	/**
	 * 指定したオブジェクトに最も近いオブジェクトを取得します
	 * 
	 * @param obj
	 */
	@SuppressWarnings("unchecked")
	@Override
	public T find(Object obj) {
		return find((T) obj);
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.DbObjectCollection#applyAll(java.util.function.Consumer)
	 */
	@Override
	public void applyAll(Consumer<DbObject<?>> consumer){
		this.equals(this, new GetAllDbObjectEqualsHandler(consumer));
	}
	
	protected boolean equals(String propertyName, List<T> target,
			Object value, Object targetValue, EqualsHandler equalsHandler) {
		return equalsHandler.valueEquals(propertyName, this, target, value,
				targetValue, EqualsUtils.getEqualsSupplier(value, targetValue));
	}
	
	protected boolean equals(ISchemaProperty props, List<T> target, EqualsHandler equalsHandler) {
		return equals(props.getLabel(), target,
				props.getValue(this), props.getValue(target), equalsHandler);
	}

	protected abstract Supplier<? extends DbObjectCollection<T>> newInstance();
}
