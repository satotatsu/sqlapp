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
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.AbstractObjectXmlReaderHandler.ChildObjectHolder;
import com.sqlapp.data.schemas.properties.CreatedAtProperty;
import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.data.schemas.properties.IdProperty;
import com.sqlapp.data.schemas.properties.LastAlteredAtProperty;
import com.sqlapp.data.schemas.properties.OrdinalProperty;
import com.sqlapp.data.schemas.properties.SpecificsProperty;
import com.sqlapp.data.schemas.properties.StatisticsProperty;
import com.sqlapp.data.schemas.properties.complex.DialectGetter;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.HashCodeBuilder;
import com.sqlapp.util.StaxReader;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.StringUtils;
import com.sqlapp.util.ToStringBuilder;
import com.sqlapp.util.xml.ResultHandler;

/**
 * DBオブジェクト共通の抽象クラス
 * 
 * @author satoh
 * 
 */
abstract class AbstractBaseDbObject<T extends AbstractBaseDbObject<T>>
		implements DbObject<T>, IdProperty<T>
		, Comparable<T>
		, OrdinalProperty<T>
		, CreatedAtProperty<T>
		, LastAlteredAtProperty<T>
		, SpecificsProperty<T>
		, StatisticsProperty<T>
		, DialectGetter{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 210101856540540272L;
	/** オブジェクトID */
	private String id = null;
	/** 作成日時 */
	private Timestamp createdAt = null;
	/** 最終更新日時 */
	private Timestamp lastAlteredAt = null;
	/** DB固有の情報を格納する領域 */
	private DbInfo specifics = new DbInfo();
	/** 統計情報を格納する領域 */
	private DbInfo statistics = new DbInfo();
	/**
	 * 位置情報
	 */
	private int ordinal = 0;
	/**
	 * メタデータを作成したDB
	 */
	private Dialect dialect = null;
	/**
	 * 親のオブジェクト
	 */
	private DbCommonObject<?> parent = null;

	protected DbCommonObject<?> getParent() {
		return this.parent;
	}

	protected T setParent(final DbCommonObject<?> parent) {
		this.parent = cast(parent);
		return instance();
	}

	@Override
	public int getOrdinal() {
		return ordinal;
	}

	protected T setOrdinal(final int ordinal) {
		this.ordinal = ordinal;
		return instance();
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	protected AbstractBaseDbObject() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.TimestampProperties#getCreated()
	 */
	@Override
	public Timestamp getCreatedAt() {
		return createdAt;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.TimestampProperties#setCreated(java.sql.Timestamp
	 * )
	 */
	@Override
	public T setCreatedAt(final Timestamp created) {
		this.createdAt = created;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.TimestampProperties#getLastAltered()
	 */
	@Override
	public Timestamp getLastAlteredAt() {
		return lastAlteredAt;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.TimestampProperties#setLastAltered(java.sql.Timestamp
	 * )
	 */
	@Override
	public T setLastAlteredAt(final Timestamp lastAltered) {
		this.lastAlteredAt = lastAltered;
		return instance();
	}

	/**
	 * @return the specifics
	 */
	@Override
	public DbInfo getSpecifics() {
		return specifics;
	}

	/**
	 * @param specifics
	 *            the specifics to set
	 */
	@Override
	public T setSpecifics(final DbInfo specifics) {
		this.specifics = specifics;
		return instance();
	}

	/**
	 * @return the statistics
	 */
	@Override
	public DbInfo getStatistics() {
		return statistics;
	}

	/**
	 * @param statistics
	 *            the statistics to set
	 */
	@Override
	public T setStatistics(final DbInfo statistics) {
		this.statistics = statistics;
		return instance();
	}
	
	/**
	 * @return the dialect
	 */
	@Override
	public Dialect getDialect() {
		if (this.getParent() == null) {
			return dialect;
		}
		final DbCommonObject<?> dbCommonObject = this.getParent();
		if (dbCommonObject instanceof AbstractBaseDbObject) {
			return ((AbstractBaseDbObject<?>) dbCommonObject).getDialect();
		} else if (dbCommonObject instanceof AbstractDbObjectCollection) {
			return getDialect((AbstractDbObjectCollection<?>) dbCommonObject);
		}
		return dialect;
	}

	protected Dialect getDialect(final AbstractBaseDbObject<?> abstractDbObject) {
		return abstractDbObject.getDialect();
	}

	protected Dialect getDialect(
			final AbstractDbObjectCollection<?> abstractDbObjectCollection) {
		final DbCommonObject<?> dbCommonObject = abstractDbObjectCollection
				.getParent();
		if (dbCommonObject instanceof AbstractBaseDbObject) {
			return ((AbstractBaseDbObject<?>) dbCommonObject).getDialect();
		} else if (dbCommonObject instanceof AbstractDbObjectCollection) {
			return getDialect((AbstractDbObjectCollection<?>) dbCommonObject);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	protected T instance() {
		return (T) this;
	}

	/**
	 * @param dialect
	 *            the dialect to set
	 */
	public T setDialect(final Dialect dialect) {
		this.dialect = dialect;
		return instance();
	}

	/**
	 * @return the id
	 */
	@Override
	public String getId() {
		return id;
	}

	/**
	 * @param id
	 *            the id to set
	 */
	@Override
	public T setId(final String id) {
		this.id = id;
		return instance();
	}

	/**
	 * マップでプロパティの値を取得します
	 */
	@Override
	public Map<String, Object> toMap() {
		final GetPropertyMapEqualsHandler equalsHandler = new GetPropertyMapEqualsHandler(
				this);
		this.equals(this, equalsHandler);
		return equalsHandler.getResult();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		return this.equals(obj, EqualsHandler.getInstance());
	}

	@Override
	public boolean equals(final Object obj, final EqualsHandler equalsHandler) {
		if (equalsHandler.referenceEquals(this, obj)) {
			return true;
		}
		if (!(obj instanceof AbstractBaseDbObject)) {
			return false;
		}
		@SuppressWarnings("unchecked")
		final
		T val = (T) obj;
		if (!equals(SchemaProperties.ID, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.CREATED_AT, val,
				equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.LAST_ALTERED_AT, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.SPECIFICS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.STATISTICS, val, equalsHandler)) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#like(java.lang.Object)
	 */
	@Override
	public boolean like(final Object obj) {
		return equals(obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#like(java.lang.Object,
	 * com.sqlapp.data.schemas.EqualsHandler)
	 */
	@Override
	public boolean like(final Object obj, final EqualsHandler equalsHandler) {
		return equals(obj, equalsHandler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DbObject#diff(com.sqlapp.data.schemas.DbObject)
	 */
	@Override
	public DbObjectDifference diff(final T obj) {
		final DbObjectDifference diff = new DbObjectDifference(this, obj);
		return diff;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DbObject#diff(com.sqlapp.data.schemas.DbObject,
	 * com.sqlapp.data.schemas.EqualsHandler)
	 */
	@Override
	public DbObjectDifference diff(final T obj, final EqualsHandler equalsHandler) {
		final DbObjectDifference diff = new DbObjectDifference(this, obj,
				equalsHandler);
		return diff;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		final ToStringBuilder builder = new ToStringBuilder(this.getSimpleName());
		toString(builder);
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

	/**
	 * toString()で子クラスで追加したプロパティの設定
	 * 
	 * @param builder
	 */
	protected void toString(final ToStringBuilder builder) {
		builder.add(SchemaProperties.ID, getId());
		builder.add(SchemaProperties.SPECIFICS, getSpecifics());
		builder.add(SchemaProperties.STATISTICS, getStatistics());
		builder.add(SchemaProperties.CREATED_AT, getCreatedAt());
		builder.add(SchemaProperties.LAST_ALTERED_AT, getLastAlteredAt());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final HashCodeBuilder builder = new HashCodeBuilder();
		builder.append(this.getId());
		builder.append(this.getCreatedAt());
		builder.append(this.getLastAlteredAt());
		builder.append(this.getDialect());
		return builder.hashCode();
	}

	private final String SIMPLE_NAME = getSimpleName(this.getClass());

	/**
	 * XMLでのタグ名
	 * 
	 */
	protected String getSimpleName() {
		return SIMPLE_NAME;
	}

	private static final Map<Class<?>, String> SIMPLE_NAME_MAP = new HashMap<Class<?>, String>();

	/**
	 * XMLでのタグ名を取得します
	 * 
	 */
	protected static String getSimpleName(final Class<?> clazz) {
		String name = SIMPLE_NAME_MAP.get(clazz);
		if (name != null) {
			return name;
		}
		name = StringUtils.uncapitalize(clazz.getSimpleName());
		SIMPLE_NAME_MAP.put(clazz, name);
		return name;
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
	 * 名前を指定してXML要素を書き出します
	 * 
	 * @param name
	 * @param stax
	 * @throws XMLStreamException
	 */
	protected void writeXml(final String name, final StaxWriter stax)
			throws XMLStreamException {
		stax.newLine();
		stax.indent();
		stax.writeElement(name, ()->{
			writeName(stax);
			writeXmlOptionalAttributes(stax);
			writeCommonAttribute(stax);
			final long beforeCount = stax.getWriteCount();
			stax.indent(()->{
				writeXmlOptionalValues(stax);
				writeCommonValue(stax);
			});
			final long endCount = stax.getWriteCount();
			if (beforeCount != endCount) {
				stax.newLine();
				stax.indent();
			}
		});
	}

	/**
	 * 名称のXMLへの書き込み
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	protected void writeName(final StaxWriter stax) throws XMLStreamException {

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
		final AbstractBaseDbObjectXmlReaderHandler<?> handler = getDbObjectXmlReaderHandler();
		handler.setReaderOptions(options);
		final ChildObjectHolder holder = new ChildObjectHolder(this);
		final ResultHandler resultHandler = new ResultHandler();
		resultHandler.registerChild(handler);
		resultHandler.handle(staxReader, holder);
	}

	/**
	 * ストリームからXMLを読み込みます
	 * 
	 * @param stream
	 * @throws XMLStreamException
	 */
	@Override
	public void loadXml(final InputStream stream, final XmlReaderOptions options) throws XMLStreamException {
		final StaxReader staxReader = new StaxReader(stream);
		final AbstractBaseDbObjectXmlReaderHandler<?> handler = getDbObjectXmlReaderHandler();
		handler.setReaderOptions(options);
		final ChildObjectHolder holder = new ChildObjectHolder(this);
		final ResultHandler resultHandler = new ResultHandler();
		resultHandler.registerChild(handler);
		resultHandler.handle(staxReader, holder);
	}

	/**
	 * 指定したパスからXMLを読み込みます
	 * 
	 * @param path
	 *            ファイルパス
	 * @throws XMLStreamException
	 * @throws FileNotFoundException
	 */
	@Override
	public void loadXml(final String path, final XmlReaderOptions options) throws XMLStreamException,
			FileNotFoundException {
		InputStream stream = null;
		BufferedInputStream bis = null;
		try {
			stream = FileUtils.getInputStream(path);
			if (stream == null) {
				throw new FileNotFoundException(path);
			}
			bis = new BufferedInputStream(stream);
			loadXml(bis, options);
		} finally {
			close(stream);
		}
	}

	/**
	 * 指定したパスからXMLを読み込みます
	 * 
	 * @param file
	 *            ファイルパス
	 * @throws XMLStreamException
	 * @throws FileNotFoundException
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

	protected AbstractBaseDbObjectXmlReaderHandler<T> getDbObjectXmlReaderHandler() {
		return new AbstractBaseDbObjectXmlReaderHandler<T>(this.newInstance()) {
		};
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

	/**
	 * 指定したパスにXMLとして書き込みます
	 * 
	 * @param path
	 * @throws XMLStreamException
	 * @throws FileNotFoundException
	 */
	@Override
	public void writeXml(final String path) throws XMLStreamException, IOException {
		writeXml(new File(path));
	}

	/**
	 * ファイルにXMLとして書き込みます
	 * 
	 * @param file
	 * @throws XMLStreamException
	 * @throws IOException
	 */
	@Override
	public void writeXml(final File file) throws XMLStreamException, IOException {
		FileOutputStream fos = null;
		BufferedOutputStream bos = null;
		try {
			fos = new FileOutputStream(file);
			bos = new BufferedOutputStream(fos);
			final StaxWriter stax = new StaxWriter(bos);
			writeXml(stax);
			bos.flush();
		} finally {
			close(fos);
		}
	}

	/**
	 * 共通属性要素のXMLへの書き込み
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	protected void writeCommonAttribute(final StaxWriter stax)
			throws XMLStreamException {
		writeCommonNameAttribute(stax);
		stax.writeAttribute(SchemaProperties.ID.getLabel(), this.getId());
		stax.writeAttribute(SchemaProperties.CREATED_AT.getLabel(), this.getCreatedAt());
		stax.writeAttribute(SchemaProperties.LAST_ALTERED_AT.getLabel(), this.getLastAlteredAt());
	}

	/**
	 * 共通属性要素のXMLへの書き込み
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	protected void writeCommonNameAttribute(final StaxWriter stax)
			throws XMLStreamException {
	}

	/**
	 * 共通値のXMLへの書き込み
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	protected void writeCommonValue(final StaxWriter stax) throws XMLStreamException {
		if (!this.getSpecifics().isEmpty()) {
			stax.newLine();
			stax.indent();
			stax.writeElement(SchemaProperties.SPECIFICS.getLabel(), this.getSpecifics());
		}
		if (!this.getStatistics().isEmpty()) {
			stax.newLine();
			stax.indent();
			stax.writeElement(SchemaProperties.STATISTICS.getLabel(), this.getStatistics());
		}
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

	/**
	 * XML書き込みでオプションの値を書き込みます
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	protected void writeXmlOptionalValues(final StaxWriter stax)
			throws XMLStreamException {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#clone()
	 */
	@Override
	public T clone() {
		final T clone = newInstance().get();
		cloneProperties(clone);
		clone.validate();
		return clone;
	}
	
	/**
	 * プロパティのコピー
	 * 
	 * @param clone
	 */
	protected void cloneProperties(final T clone) {
		if (clone.getDialect()==null){
			clone.setDialect(this.getDialect());
		}
		final Set<ISchemaProperty> properties=SchemaUtils.getAllSchemaProperties(this.getClass());
		for(final ISchemaProperty prop:properties){
			final Object value=prop.getCloneValue(this);
			prop.setValue(clone, value);
		}
	}

	protected boolean equals(final String propertyName, final T target,
			final Object value, final Object targetValue, final EqualsHandler equalsHandler) {
		return equalsHandler.valueEquals(propertyName, this, target, value,
				targetValue, EqualsUtils.getEqualsSupplier(value, targetValue));
	}
	
	protected boolean equals(final ISchemaProperty props, final T target, final EqualsHandler equalsHandler) {
		return equals(props.getLabel(), target,
				props.getValue(this), props.getValue(target), equalsHandler);
	}

	protected boolean equals(final String propertyName, final T target,
			final Object value, final Object targetValue, final EqualsHandler equalsHandler, final BooleanSupplier booleanSupplier) {
		return equalsHandler.valueEquals(propertyName, this, target, value,
				targetValue, booleanSupplier);
	}

	protected boolean equals(final ISchemaProperty props, final T target, final EqualsHandler equalsHandler, final BooleanSupplier booleanSupplier) {
		return  equals(props.getLabel(), target,
				props.getValue(this), props.getValue(target), equalsHandler, booleanSupplier);
	}

	protected boolean equals(final String propertyName, final T target,
			final DbObjectCollection<?> value, final DbObjectCollection<?> targetValue,
			final EqualsHandler equalsHandler) {
		return equalsHandler.valueEquals(propertyName, this, target, value,
				targetValue, getEqualsSupplier(value, targetValue, equalsHandler));
	}
	
	protected static BooleanSupplier getEqualsSupplier(final DbCommonObject<?> o1, final DbCommonObject<?> o2, final EqualsHandler equalsHandler){
		return ()->{
			if (o1!=null){
				return o1.equals(o2, equalsHandler);
			} else{
				if (o2==null){
					return true;
				}
			}
			return false;
		};
	}

	/**
	 * スキーマの内容をバリデートします
	 */
	protected void validate() {

	}

	protected String listToString(final List<String> list) {
		if (list == null) {
			return null;
		}
		final StringBuilder builder = new StringBuilder();
		final int size = list.size();
		for (int i = 0; i < size; i++) {
			builder.append(list.get(i));
			builder.append('\n');
		}
		return builder.toString();
	}
	
	protected boolean needsEscape(final String value) {
		if (value == null) {
			return false;
		}
		return value.contains("\n");
	}


	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.DbObject#applyAll(java.util.function.Consumer)
	 */
	@Override
	public T applyAll(final Consumer<DbObject<?>> consumer){
		this.equals(this, new GetAllDbObjectEqualsHandler(consumer));
		return this.instance();
	}

	protected TableSpace getTableSpaceFromParent(final TableSpace tableSpace) {
		return SchemaUtils.getTableSpaceFromParent(tableSpace, this);
	}

	protected abstract Supplier<T> newInstance();
	
}
