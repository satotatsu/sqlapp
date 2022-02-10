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
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.minus;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.AbstractObjectXmlReaderHandler.ChildObjectHolder;
import com.sqlapp.data.schemas.function.AddDbObjectPredicate;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.HashCodeBuilder;
import com.sqlapp.util.SeparatedStringBuilder;
import com.sqlapp.util.StaxReader;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.xml.ResultHandler;

/**
 * RowCollection
 * 
 */
public final class RowCollection implements DbObjectCollection<Row>,
		Sortable<Row>, HasParent<Table>
, NewElement<Row, RowCollection>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -185783147817960268L;

	protected Supplier<RowCollection> newInstance(){
		return ()->new RowCollection();
	}
	/**
	 * テーブル
	 */
	private Table parent = null;

	private List<Row> inner = new ArrayList<Row>();
	/**
	 * RowIteratorHandler
	 */
	private transient RowIteratorHandler rowIteratorHandler = null;
	/**
	 * 追加対象オブジェクト判定ハンドラー
	 */
	private transient AddDbObjectPredicate addDbObjectPredicate = (p,c)->{
		if (!(c instanceof Row)){
			return false;
		} else{
			return true;
		}
	};

	/**
	 * @return the addDbObjectPredicate
	 */
	protected AddDbObjectPredicate getAddDbObjectPredicate() {
		if (addDbObjectPredicate == null) {
			addDbObjectPredicate = (p,c)->{
				if (!(c instanceof Row)){
					return false;
				} else{
					return true;
				}
			};
		}
		return addDbObjectPredicate;
	}

	/**
	 * @param addDbObjectFilter
	 *            the addDbObjectFilter to set
	 */
	public void setAddDbObjectFilter(AddDbObjectPredicate addDbObjectFilter) {
		this.addDbObjectPredicate = addDbObjectFilter;
	}

	/**
	 * コンストラクタ
	 * 
	 */
	public RowCollection() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param parent
	 */
	protected RowCollection(Table parent) {
		this.parent = parent;
	}

	/**
	 * 行を追加します
	 * 
	 * @param row
	 */
	public boolean add(Row row) {
		if (!getAddDbObjectPredicate().test(this, row)) {
			return false;
		}
		row.setParent(this);
		return getRowList().add(row);
	}

	/**
	 * 指定したカラムだけに値を絞り込みます
	 * 
	 * @param columns
	 *            絞込み対象のカラム
	 */
	protected void compactionColumn(Column... columns) {
		List<Column> colList = minus(list(getParent().getColumns()), columns);
		Column[] colArray = colList.toArray(new Column[0]);
		for (int i = 0; i < inner.size(); i++) {
			inner.get(i).compactionColumn(colArray);
		}
	}

	/**
	 * 指定したカラムだけに値を絞り込みます
	 * 
	 * @param columns
	 *            絞込み対象のカラム
	 */
	protected void compactionColumn(Collection<Column> columns) {
		List<Column> colList = minus(list(getParent().getColumns()), columns);
		Column[] colArray = colList.toArray(new Column[0]);
		for (int i = 0; i < inner.size(); i++) {
			inner.get(i).compactionColumn(colArray);
		}
	}

	/**
	 * 指定した行の取得
	 * 
	 * @param i
	 */
	public Row get(int i) {
		return inner.get(i);
	}

	/**
	 * 指定したカラムの値のリストを取得します
	 * 
	 * @param column
	 */
	public List<Object> getValueList(Column column) {
		int size = this.size();
		List<Object> result = list(size);
		int index = column.getOrdinal();
		for (Row row : this) {
			result.add(row.get(index));
		}
		return result;
	}

	/**
	 * 指定したカラムの値のセットを取得します
	 * 
	 * @param column
	 */
	public Set<Object> getValueSet(Column column) {
		int size = this.size();
		Set<Object> result = CommonUtils.linkedSet(size);
		int index = column.getOrdinal();
		for (Row row : this) {
			result.add(row.get(index));
		}
		return result;
	}

	/**
	 * 指定したカラムの値のリストを取得します
	 * 
	 * @param columnName
	 *            カラムの値
	 */
	public List<Object> getValueList(String columnName) {
		ColumnCollection cc = this.getAncestor(ColumnCollection.class);
		return getValueList(cc.get(columnName));
	}

	/**
	 * 指定したカラムの値のセットを取得します
	 * 
	 * @param columnName
	 *            カラムの値
	 */
	public Set<Object> getValueSet(String columnName) {
		ColumnCollection cc = this.getAncestor(ColumnCollection.class);
		return getValueSet(cc.get(columnName));
	}

	/**
	 * 指定したカラム(複数指定可)の値のリストを取得します
	 * 
	 * @param columns
	 */
	public List<Object[]> getValueList(Column... columns) {
		int size = this.size();
		List<Object[]> result = list(size);
		for (Row row : this) {
			Object[] vals = new Object[columns.length];
			for (int j = 0; j < columns.length; j++) {
				vals[j] = row.get(columns[j].getOrdinal());
			}
			result.add(vals);
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.List#clear()
	 */
	public void clear() {
		getRowList().clear();
	}

	public boolean contains(Row o) {
		return getRowList().contains(o);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.List#isEmpty()
	 */
	public boolean isEmpty() {
		return getRowList().isEmpty();
	}

	public boolean remove(Row row) {
		row.setParent(null);
		return getRowList().remove(row);
	}

	public int size() {
		return getRowList().size();
	}

	protected List<Row> getRowList() {
		return inner;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Parent#getParent()
	 */
	@Override
	public Table getParent() {
		return parent;
	}

	protected void setParent(Table parent) {
		this.parent = parent;
	}

	/**
	 * カラムの追加
	 * 
	 * @param columns
	 *            追加するカラム
	 */
	protected void addColumn(Column... columns) {
		for (int i = 0; i < inner.size(); i++) {
			inner.get(i).addColumn(columns);
		}
	}

	/**
	 * 指定した位置に行を追加
	 */
	@Override
	public void add(int index, Row row) {
		if (!getAddDbObjectPredicate().test(this, row)) {
			return;
		}
		row.setParent(this);
		getRowList().add(index, row);
	}

	public boolean addAll(List<? extends Row> rows) {
		if (rows == this) {
			return false;
		}
		List<Row> targets=CommonUtils.list(); 
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			if (!getAddDbObjectPredicate().test(this, row)) {
				continue;
			}
			row.setParent(this);
			targets.add(row);
		}
		return getRowList().addAll(targets);
	}

	@Override
	public boolean addAll(Collection<? extends Row> c) {
		List<Row> targets=CommonUtils.list(); 
		for (Row row : c) {
			if (!getAddDbObjectPredicate().test(this, row)) {
				continue;
			}
			row.setParent(this);
			targets.add(row);
		}
		return getRowList().addAll(targets);
	}

	@Override
	public boolean addAll(int index, Collection<? extends Row> c) {
		List<Row> targets=CommonUtils.list(); 
		for (Row row : c) {
			if (!getAddDbObjectPredicate().test(this, row)) {
				continue;
			}
			row.setParent(this);
			targets.add(row);
		}
		return getRowList().addAll(index, targets);
	}

	@Override
	public boolean contains(Object o) {
		return getRowList().contains(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return getRowList().containsAll(c);
	}

	@Override
	public int indexOf(Object o) {
		return getRowList().indexOf(o);
	}

	@Override
	public int lastIndexOf(Object o) {
		return getRowList().lastIndexOf(o);
	}

	@Override
	public boolean remove(Object o) {
		boolean bool = getRowList().remove(o);
		if (bool) {
			Row row = cast(o);
			row.setParent(null);
		}
		return bool;
	}

	/**
	 * 指定した行の削除
	 */
	@Override
	public Row remove(int index) {
		Row row = getRowList().remove(index);
		if (row != null) {
			row.setParent(null);
		}
		return row;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		for (Object obj : c) {
			Row row = cast(obj);
			row.setParent(null);
		}
		return getRowList().removeAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return getRowList().removeAll(c);
	}

	@Override
	public Row set(int index, Row row) {
		row.setParent(this);
		return getRowList().set(index, row);
	}

	@Override
	public List<Row> subList(int fromIndex, int toIndex) {
		return getRowList().subList(fromIndex, toIndex);
	}

	@Override
	public Object[] toArray() {
		return getRowList().toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return getRowList().toArray(a);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		SeparatedStringBuilder builder = new SeparatedStringBuilder("\n");
		builder.add(this.getRowList());
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.List#iterator()
	 */
	@Override
	public Iterator<Row> iterator() {
		return getRowIteratorHandler().iterator(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.List#listIterator()
	 */
	@Override
	public ListIterator<Row> listIterator() {
		return getRowIteratorHandler().listIterator(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.List#listIterator(int)
	 */
	@Override
	public ListIterator<Row> listIterator(int index) {
		return getRowIteratorHandler().listIterator(this, index);
	}

	private String SIMPLE_NAME = AbstractNamedObjectCollection
			.getSimpleName(this.getClass());

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		return this.equals(obj, EqualsHandler.getInstance());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#equals(java.lang.Object,
	 * com.sqlapp.data.schemas.EqualsHandler)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof RowCollection)) {
			return false;
		}
		if (equalsHandler.referenceEquals(this, obj)) {
			return true;
		}
		RowCollection val = (RowCollection) obj;
		if (!equalsElements(val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		HashCodeBuilder builder = new HashCodeBuilder();
		builder.append(this.inner);
		return builder.hashCode();
	}

	/**
	 * 全ての要素が等しいかを判定します
	 * 
	 * @param val
	 * @param equalsHandler
	 */
	protected boolean equalsElements(RowCollection val,
			EqualsHandler equalsHandler) {
		if (!equalsHandler.valueEquals("size"
				, this, val
				, this.inner.size(), val.inner.size(), EqualsUtils.getEqualsSupplier(this.inner.size() == val.inner.size()))) {
			return false;
		}
		int size = this.inner.size();
		for (int i = 0; i < size; i++) {
			Row thisObj1 = this.inner.get(i);
			Row thisObj2 = null;
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
	protected boolean equalsElement(final Row t1, final Row t2,
			EqualsHandler equalsHandler) {
		if (t1 == null) {
			if (t2 == null) {
				return true;
			} else {
				return false;
			}
		}
		return t1.equals(t2, equalsHandler);
	}

	/**
	 * 要素が近いかを判定します
	 * 
	 * @param t1
	 * @param t2
	 * @param equalsHandler
	 */
	protected boolean likeElement(Row t1, Row t2, EqualsHandler equalsHandler) {
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
	 * XMLでのタグ名
	 * 
	 */
	protected String getSimpleName() {
		return SIMPLE_NAME;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public RowCollection clone() {
		RowCollection c = this.newInstance().get();
		int size = this.size();
		for (int i = 0; i < size; i++) {
			c.add(inner.get(i).clone());
		}
		return c;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Sortable#sort()
	 */
	@Override
	public void sort() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Sortable#sort(java.util.Comparator)
	 */
	@Override
	public void sort(Comparator<? super Row> comparator) {
		List<Row> result = CommonUtils.list(this.inner);
		Collections.sort(result, comparator);
	}

	/**
	 * コレクションのクラスを取得します
	 * 
	 */
	@Override
	public Class<Row> getType() {
		return Row.class;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DbObjectCollection#find(com.sqlapp.data.schemas
	 * .DbObject)
	 */
	@Override
	public Row find(Row obj) {
		List<UniqueConstraint> ucs = parent.getConstraints()
				.getUniqueConstraints();
		if (ucs.size() == 0) {
			for (Row row : this) {
				if (row.equals(obj)) {
					return obj;
				}
			}
			return null;
		}
		UniqueConstraint uc = CommonUtils.first(ucs);
		Object[] keyValues = new Object[uc.getColumns().size()];
		ReferenceColumnCollection rcc = uc.getColumns();
		int size = rcc.size();
		for (int i = 0; i < size; i++) {
			ReferenceColumn rc = rcc.get(i);
			keyValues[i] = obj.get(rc.getName());
		}
		for (Row row : this) {
			boolean find = true;
			for (int i = 0; i < size; i++) {
				ReferenceColumn rc = rcc.get(i);
				Object val = row.get(rc.getName());
				if (!CommonUtils.eq(keyValues[i], val)) {
					find = false;
					break;
				}
			}
			if (find) {
				return row;
			}
		}
		return null;
	}

	@Override
	public Row find(Object obj) {
		return this.find((Row) obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DbObjectCollection#diff(com.sqlapp.data.schemas
	 * .DbObjectCollection)
	 */
	@Override
	public DbObjectDifferenceCollection diff(DbObjectCollection<Row> obj) {
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
	public DbObjectDifferenceCollection diff(DbObjectCollection<Row> obj,
			EqualsHandler equalsHandler) {
		DbObjectDifferenceCollection diff = new DbObjectDifferenceCollection(
				this, obj, equalsHandler);
		return diff;
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
		RowCollectionXmlReaderHandler handler = getDbObjectXmlReaderHandler();
		handler.setReaderOptions(options);
		ChildObjectHolder holder = new ChildObjectHolder(this);
		ResultHandler resultHandler = new ResultHandler();
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
	public void loadXml(InputStream stream, XmlReaderOptions options) throws XMLStreamException {
		StaxReader staxReader = new StaxReader(stream);
		RowCollectionXmlReaderHandler handler = getDbObjectXmlReaderHandler();
		handler.setReaderOptions(options);
		ChildObjectHolder holder = new ChildObjectHolder(this);
		ResultHandler resultHandler = new ResultHandler();
		resultHandler.registerChild(handler);
		resultHandler.handle(staxReader, holder);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#readXml(java.lang.String)
	 */
	@Override
	public void loadXml(String path, XmlReaderOptions options) throws XMLStreamException,
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
			close(bis);
			close(stream);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#readXml(java.io.File)
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
			close(bis);
			close(stream);
		}
	}

	protected RowCollectionXmlReaderHandler getDbObjectXmlReaderHandler() {
		return new RowCollectionXmlReaderHandler();
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
		if (this.getRowIteratorHandler() instanceof DefaultRowIteratorHandler){
			if (this.size()>0){
				writeXml(SchemaObjectProperties.ROWS.getLabel(), stax);
			}
		} else{
			writeXml(SchemaObjectProperties.ROWS.getLabel(), stax);
		}
	}

	/**
	 * XML書き出し
	 * 
	 * @param name
	 *            書き出す要素名
	 * @param stax
	 * @throws XMLStreamException
	 */
	protected void writeXml(String name, StaxWriter stax)
			throws XMLStreamException {
		stax.newLine();
		stax.indent();
		stax.writeStartElement(name);
		stax.addIndentLevel(1);
		ColumnCollection columns = this.getParent().getColumns();
		long beforeCount = stax.getWriteCount();
		for (Row row : this) {
			row.writeXml(stax, columns);
		}
		stax.addIndentLevel(-1);
		long endCount = stax.getWriteCount();
		if (beforeCount != endCount) {
			stax.newLine();
			stax.indent();
		}
		stax.writeEndElement();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DbCommonObject#writeXml(java.io.OutputStream)
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#writeXml(java.io.Writer)
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
		BufferedOutputStream stream = null;
		try {
			stream = new BufferedOutputStream(new FileOutputStream(file));
			StaxWriter stax = new StaxWriter(stream);
			writeXml(stax);
			stream.flush();
		} finally {
			close(stream);
		}
	}

	/**
	 * @return the rowIteratorHandler
	 */
	protected RowIteratorHandler getRowIteratorHandler() {
		if (rowIteratorHandler == null) {
			this.rowIteratorHandler = new DefaultRowIteratorHandler();
		}
		return rowIteratorHandler;
	}

	/**
	 * @param rowIteratorHandler
	 *            the rowIteratorHandler to set
	 */
	protected void setRowIteratorHandler(RowIteratorHandler rowIteratorHandler) {
		this.rowIteratorHandler = rowIteratorHandler;
	}

	protected void setDiffAll(SeparatedStringBuilder builder) {
		SeparatedStringBuilder sepVals = new SeparatedStringBuilder(",");
		sepVals.setStart("{").setEnd("}");
		sepVals.add(this);
		builder.add(sepVals.toString());
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.DbObjectCollection#applyAll(java.util.function.Consumer)
	 */
	@Override
	public void applyAll(Consumer<DbObject<?>> consumer){
		this.equals(this, new GetAllDbObjectEqualsHandler(consumer));
	}

	@Override
	public Row newElement() {
		Row row=new Row();
		row.setParent(this);
		return row;
	}
}
