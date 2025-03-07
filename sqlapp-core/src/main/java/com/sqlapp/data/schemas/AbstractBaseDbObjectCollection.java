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

public abstract class AbstractBaseDbObjectCollection<T extends AbstractBaseDbObject<? super T>>
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
	protected AbstractBaseDbObjectCollection(final DbCommonObject<?> parent) {
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
	public void setAddDbObjectPredicate(final AddDbObjectPredicate addDbObjectPredicate) {
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
	protected void setValidateAtChange(final boolean validateAtChange) {
		this.validateAtChange = validateAtChange;
	}

	protected DbCommonObject<?> getParent() {
		return this.parent;
	}
	
	@SuppressWarnings("unchecked")
	protected <S extends AbstractBaseDbObjectCollection<?>> S setParent(
			final DbCommonObject<?> parent) {
		this.parent = cast(parent);
		return (S) (this);
	}

	/**
	 * 追加前のメソッド
	 */
	protected boolean beforeAdd(final T args) {
		return true;
	}

	/**
	 * 追加後のメソッド
	 */
	protected void afterAdd(final T args) {
	}

	/**
	 * 削除前のメソッド
	 */
	protected boolean beforeRemove(final T args) {
		return true;
	}

	/**
	 * 削除後のメソッド
	 */
	protected void afterRemove(final T args) {
	}

	/**
	 * IDを指定してオブジェクトを取得します
	 * 
	 * @param id
	 */
	public T getById(final String id) {
		final int size = inner.size();
		for (int i = 0; i < size; i++) {
			final T t = inner.get(i);
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
		final int size = inner.size();
		for (int i = 0; i < size; i++) {
			final T obj = inner.get(i);
			obj.setOrdinal(i);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.ArrayList#add(java.lang.Object)
	 */
	@Override
	public boolean add(final T e) {
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
	protected void setElementParent(final T e) {
		e.setParent(this);
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
	public boolean addAll(final Collection<? extends T> c) {
		final Set<T> set = CommonUtils.set(c.size());
		for (final T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			if (!beforeAdd(t)) {
				set.add(t);
			}
		}
		boolean bool = false;
		for (final T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			if (set.contains(t)) {
				continue;
			}
			final T findObj = find(t);
			if (findObj == null) {
				bool = inner.add(t);
				setElementParent(t);
			}
		}
		renew();
		for (final T t : c) {
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
	public boolean addAll(final int index, final Collection<? extends T> c) {
		if (this == c) {
			return false;
		}
		final Set<T> set = CommonUtils.set(c.size());
		for (final T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			if (!beforeAdd(t)) {
				set.add(t);
			}
		}
		boolean bool = false;
		for (final T t : c) {
			if (!getAddDbObjectPredicate().test(this, t)) {
				continue;
			}
			if (set.contains(t)) {
				continue;
			}
			final T findObj = find(t);
			if (findObj == null) {
				bool = inner.add(t);
				setElementParent(t);
			}
		}
		renew();
		for (final T t : c) {
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
	@Override
	public boolean containsAll(final Collection<?> args) {
		for (final Object arg : args) {
			if (!contains(arg)) {
				return false;
			}
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean remove(final Object o) {
		this.beforeRemove((T) o);
		final boolean bool = inner.remove(o);
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
	public T remove(final int index) {
		final T obj = this.get(index);
		this.beforeRemove(obj);
		final T rm = inner.remove(index);
		if (this.isValidateAtChange()) {
			renew();
		}
		if (rm != null) {
			rm.setParent(null);
		}
		this.afterRemove(obj);
		return obj;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.AbstractCollection#removeAll(java.util.Collection)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public boolean removeAll(final Collection<?> c) {
		boolean result = true;
		for (final Object o : c) {
			final boolean bool = inner.remove(o);
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
	public T set(final int index, final T element) {
		final T obj = inner.set(index, element);
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
		final SeparatedStringBuilder builder = new SeparatedStringBuilder("\n");
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
		final AbstractBaseDbObjectCollection clone = (AbstractBaseDbObjectCollection)this.newInstance().get();
		final List<T> clones=CommonUtils.list();
		for(final T obj:this) {
			clones.add((T)obj.clone());
		}
		clone.addAll(clones);
		return clone;
	}

	@Override
	public boolean equals(final Object obj) {
		return this.equals(obj, EqualsHandler.getInstance());
	}

	@Override
	public boolean equals(final Object obj, final EqualsHandler equalsHandler) {
		if (equalsHandler.referenceEquals(this, obj)) {
			return true;
		}
		if (!(obj instanceof AbstractBaseDbObjectCollection<?>)) {
			return false;
		}
		@SuppressWarnings("unchecked")
		final
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
	private boolean equalsElements(final AbstractBaseDbObjectCollection<T> val,
			final EqualsHandler equalsHandler) {
		if (!equalsHandler.valueEquals("size", this, val,
				this.inner.size(), val.inner.size(), EqualsUtils.getEqualsSupplier(this.inner.size(), val.inner.size()))) {
			return false;
		}
		final int size = this.inner.size();
		for (int i = 0; i < size; i++) {
			final T thisObj1 = this.inner.get(i);
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
	protected boolean likeElement(final T t1, final T t2, final EqualsHandler equalsHandler) {
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
	protected boolean equalsElement(final T t1, final T t2, final EqualsHandler equalsHandler) {
		if (t1 == null) {
			if (t2 == null) {
				return true;
			} else {
				return false;
			}
		}
		return t1.equals(t2, equalsHandler);
	}

	protected boolean equals(final String propertyName, final T target1, final T target2,
			final Object value1, final Object value2, final EqualsHandler equalsHandler) {
		return equalsHandler.valueEquals(propertyName, target1,
				target2, value1, value2, EqualsUtils.getEqualsSupplier(value1, value2));
	}

	@Override
	public int hashCode() {
		final HashCodeBuilder builder = new HashCodeBuilder();
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
	public void loadXml(final Reader reader, final XmlReaderOptions options) throws XMLStreamException {
		final StaxReader staxReader = new StaxReader(reader);
		final AbstractBaseDbObjectCollectionXmlReaderHandler<?> handler = getDbObjectXmlReaderHandler();
		handler.setReaderOptions(options);
		final ChildObjectHolder holder = new ChildObjectHolder(this);
		final ResultHandler resultHandler = new ResultHandler();
		resultHandler.registerChild(handler);
		resultHandler.handle(staxReader, holder);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#loadXml(java.io.InputStream)
	 */
	@Override
	public void loadXml(final InputStream stream, final XmlReaderOptions options) throws XMLStreamException {
		final StaxReader staxReader = new StaxReader(stream);
		final AbstractBaseDbObjectCollectionXmlReaderHandler<?> handler = getDbObjectXmlReaderHandler();
		handler.setReaderOptions(options);
		final ChildObjectHolder holder = new ChildObjectHolder(this);
		final ResultHandler resultHandler = new ResultHandler();
		resultHandler.registerChild(handler);
		resultHandler.handle(staxReader, holder);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#loadXml(java.lang.String)
	 */
	@Override
	public void loadXml(final String path, final XmlReaderOptions options) throws XMLStreamException,
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
	public void loadXml(final File file, final XmlReaderOptions options) throws XMLStreamException,
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
			final NewElement<?,?> newElement=(NewElement<?,?>)this;
			final AbstractBaseDbObject<?> dbObject=(AbstractBaseDbObject<?>)newElement.newElement();
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
	public void writeXml(final OutputStream stream) throws XMLStreamException {
		final StaxWriter stax = new StaxWriter(stream) {
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
	public void writeXml(final Writer writer) throws XMLStreamException {
		final StaxWriter stax = new StaxWriter(writer) {
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
	public void writeXml(final StaxWriter stax) throws XMLStreamException {
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
	public void writeXml(final String name, final StaxWriter stax)
			throws XMLStreamException {
		final int size = this.size();
		stax.newLine();
		stax.indent();
		stax.writeStartElement(name);
		writeXmlOptionalAttributes(stax);
		stax.addIndentLevel(1);
		for (int i = 0; i < size; i++) {
			final T obj = this.get(i);
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
	protected void writeXmlOptionalAttributes(final StaxWriter stax)
			throws XMLStreamException {
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#writeXml(java.lang.String)
	 */
	@Override
	public void writeXml(final String path) throws XMLStreamException, IOException {
		writeXml(new File(path));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#writeXml(java.io.File)
	 */
	@Override
	public void writeXml(final File file) throws XMLStreamException, IOException {
		FileOutputStream fos = null;
		BufferedOutputStream stream = null;
		try {
			fos = new FileOutputStream(file);
			stream = new BufferedOutputStream(fos);
			final StaxWriter stax = new StaxWriter(stream);
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
	protected void cloneProperties(final AbstractBaseDbObjectCollection<T> obj) {
		final int size = this.size();
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
	public DbObjectDifferenceCollection diff(final DbObjectCollection<T> obj) {
		final DbObjectDifferenceCollection diff = new DbObjectDifferenceCollection(
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
	public DbObjectDifferenceCollection diff(final DbObjectCollection<T> obj,
			final EqualsHandler equalsHandler) {
		final DbObjectDifferenceCollection diff = new DbObjectDifferenceCollection(
				this, obj, equalsHandler);
		return diff;
	}

	protected void setDiffAll(final SeparatedStringBuilder builder) {
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
	public boolean contains(final Object o) {
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
	public <T> T[] toArray(final T[] a) {
		return this.inner.toArray(a);
	}

	@Override
	public boolean retainAll(final Collection<?> c) {
		return this.inner.retainAll(c);
	}

	@Override
	public void clear() {
		this.inner.clear();
	}

	@Override
	public T get(final int index) {
		final T ret = this.inner.get(index);
		if (ret != null) {
			setElementParent(ret);
		}
		return ret;
	}

	@Override
	public int indexOf(final Object o) {
		return this.inner.indexOf(o);
	}

	@Override
	public int lastIndexOf(final Object o) {
		return this.inner.lastIndexOf(o);
	}

	@Override
	public ListIterator<T> listIterator() {
		return this.inner.listIterator();
	}

	@Override
	public ListIterator<T> listIterator(final int index) {
		return this.inner.listIterator(index);
	}

	@Override
	public List<T> subList(final int fromIndex, final int toIndex) {
		return this.inner.subList(fromIndex, toIndex);
	}

	protected void validate() {
		if (this.isValidateAtChange()) {
			renew();
		}
		validateAllElement();
	}

	protected void validateAllElement() {
		for (final T obj : this.inner) {
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
	public void sort(final Comparator<? super T> comparator) {
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
		final Class<?> clazz = this.getClass();
		Class<?> typeClazz = COLLECTION_TYPE_CACHE.get(clazz);
		if (typeClazz != null) {
			return (Class<T>) typeClazz;
		}
		final java.lang.reflect.Type type = clazz.getGenericSuperclass();
		final ParameterizedType pType = (ParameterizedType) type;
		final java.lang.reflect.Type[] aTypes = pType.getActualTypeArguments();
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
	public T find(final T obj) {
		if (obj == null) {
			return null;
		}
		for (final T val : this.inner) {
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
	public T find(final Object obj) {
		return find((T) obj);
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.DbObjectCollection#applyAll(java.util.function.Consumer)
	 */
	@Override
	public void applyAll(final Consumer<DbObject<?>> consumer){
		this.equals(this, new GetAllDbObjectEqualsHandler(consumer));
	}
	
	protected boolean equals(final String propertyName, final List<T> target,
			final Object value, final Object targetValue, final EqualsHandler equalsHandler) {
		return equalsHandler.valueEquals(propertyName, this, target, value,
				targetValue, EqualsUtils.getEqualsSupplier(value, targetValue));
	}
	
	protected boolean equals(final ISchemaProperty props, final List<T> target, final EqualsHandler equalsHandler) {
		return equals(props.getLabel(), target,
				props.getValue(this), props.getValue(target), equalsHandler);
	}

	protected abstract Supplier<? extends DbObjectCollection<T>> newInstance();
}
