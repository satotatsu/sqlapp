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

import static com.sqlapp.util.CommonUtils.list;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.schemas.AbstractObjectXmlReaderHandler.ChildObjectHolder;
import com.sqlapp.data.schemas.properties.CreatedAtProperty;
import com.sqlapp.data.schemas.properties.DataSourceDetailInfoProperty;
import com.sqlapp.data.schemas.properties.DataSourceInfoProperty;
import com.sqlapp.data.schemas.properties.DataSourceRowNumberProperty;
import com.sqlapp.data.schemas.properties.HasErrorsProperty;
import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.data.schemas.properties.LastAlteredAtProperty;
import com.sqlapp.data.schemas.properties.RowIdProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.HashCodeBuilder;
import com.sqlapp.util.SeparatedStringBuilder;
import com.sqlapp.util.StaxReader;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.xml.ResultHandler;

/**
 * Row
 * 
 */
public final class Row implements DbObject<Row>, Comparable<Row>
	,HasParent<RowCollection>
	,RowIdProperty<Row>
	,DataSourceInfoProperty<Row>
	,DataSourceDetailInfoProperty<Row>
	,DataSourceRowNumberProperty<Row>
	,CreatedAtProperty<Row>
	,LastAlteredAtProperty<Row>
	, HasErrorsProperty<Row>
	{

	protected Supplier<Row> newInstance(){
		return ()->new Row();
	}
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -765221600151712239L;
	/** 値の配列 */
	private Object[] values = null;
	/** 値の配列 */
	protected static final String VALUES = "values";
	/** 値に対するコメントの配列 */
	private String[] remarks = null;
	/** コメントのキー */
	protected static final String COMMENT = "comment";
	/** 値に対するオプションの配列 */
	private String[] options = null;
	/** オプションのキー */
	protected static final String OPTIONS = "options";
	/** オプションのキー */
	protected static final String OPTION = "option";
	protected static final String SQL_OPTION="<SQL>";
	protected static final String NULL_OPTION="<NULL>";
	/** 作成日時 */
	private Timestamp createdAt = null;
	/** 最終更新日時 */
	private Timestamp lastAlteredAt = null;
	/**
	 * 変更前の値のマップ
	 */
	// private Map<String, Object> originalValueMap=new HashMap<String,
	// Object>();
	/**
	 * 行のコレクション
	 */
	private RowCollection parent = null;
	/** 行のID */
	private Long rowId = null;
	/**
	 * データ取得(読み込み)元の情報(Excel,CSVファイル名など)
	 */
	private String dataSourceInfo = null;
	/**
	 * データ取得(読み込み)元の詳細情報(Excelシート名など)
	 */
	private String dataSourceDetailInfo = null;
	/**
	 * データ取得(読み込み)元の行番号
	 */
	private Long dataSourceRowNumber = null;
	/** エラーの有無 */
	private boolean hasErrors = (Boolean)SchemaProperties.HAS_ERRORS.getDefaultValue();
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Row clone() {
		final Row clone = this.newInstance().get();
		clone.setDataSourceRowNumber(this.getDataSourceRowNumber());
		clone.setCreatedAt(this.getCreatedAt());
		clone.setLastAlteredAt(this.getLastAlteredAt());
		clone.setDataSourceInfo(this.getDataSourceInfo());
		clone.setDataSourceDetailInfo(this.getDataSourceDetailInfo());
		if (this.values!=null){
			clone.values = new Object[this.values.length];
			if (this.remarks!=null){
				clone.remarks = new String[this.remarks.length];
			}
			if (this.options!=null){
				clone.options = new String[this.options.length];
			}
			final Converters converter = Converters.getDefault();
			for (int i = 0; i < this.values.length; i++) {
				final Object val = this.values[i];
				clone.values[i] = converter.copy(val);
				if (this.remarks!=null){
					clone.remarks[i] = this.remarks[i];
				}
				if (this.options!=null){
					clone.options[i] = this.options[i];
				}
			}
		}
		return clone;
	}

	/**
	 * 指定したカラムだけに値を絞り込みます
	 * 
	 * @param columns
	 *            絞込み対象のカラム
	 */
	protected Row compactionColumn(final Column... columns) {
		final Object[] newValues = new Object[columns.length];
		String[] newComments = null;
		if (this.remarks!=null){
			newComments = new String[columns.length];
		}
		String[] newOptions = null;
		if (this.options!=null){
			newOptions = new String[columns.length];
		}
		for (int i = 0; i < columns.length; i++) {
			final Column column = columns[i];
			if (column.getOrdinal()<this.values.length){
				newValues[i] = this.values[column.getOrdinal()];
			} else{
				newValues[i] = null;
			}
			if (this.remarks!=null){
				if (column.getOrdinal()<this.remarks.length){
					newComments[i] = this.remarks[column.getOrdinal()];
				} else{
					newComments[i]=null;
				}
			}
			if (this.options!=null){
				if (column.getOrdinal()<this.remarks.length){
					newOptions[i] = this.options[column.getOrdinal()];
				} else{
					newOptions[i] =null;
				}
			}
		}
		this.values = newValues;
		this.remarks = newComments;
		this.options = newOptions;
		return this;
	}

	/**
	 * カラムを追加します
	 * 
	 * @param columns
	 *            追加するカラム
	 */
	protected Row addColumn(final Column... columns) {
		final Table table=this.getTable();
		int size=0;
		if (table!=null){
			size=table.getColumns().size();
		}
		final Object[] newValues = new Object[size + columns.length];
		if (this.values!=null){
			System.arraycopy(values, 0, newValues, 0, values.length);
		}
		String[] newComments = null;
		if (this.remarks!=null){
			newComments = new String[size + columns.length];
			System.arraycopy(remarks, 0, newComments, 0, remarks.length);
		}
		String[] newOptions = null;
		if (this.options!=null){
			newOptions = new String[size + columns.length];
			System.arraycopy(options, 0, newOptions, 0, options.length);
		}
		this.values = newValues;
		this.remarks = newComments;
		this.options = newOptions;
		return this;
	}

	/**
	 * 値の設定を行います。
	 * 
	 * @param column
	 *            設定するカラム
	 * @param value
	 *            設定する値
	 * @return 設定前の値を返します
	 */
	public <T> T put(final Column column, final Object value) {
		checkSize(column);
		return put(column.getOrdinal(), value);
	}
	
	private void checkSize(final Column column){
		ColumnCollection cc=column.getParent();
		if (cc==null){
			final Table table=this.getTable();
			if (table!=null){
				cc=table.getColumns();
			}
		}
		if (cc==null){
			return;
		}
		final int size=cc.size();
		if (CommonUtils.size(this.values)<size){
			final Object[] vals=new Object[size];
			if (!CommonUtils.isEmpty(this.values)){
				System.arraycopy(this.values, 0, vals, 0, this.values.length);
			}
			this.values=vals;
		}
		if (CommonUtils.size(this.remarks)<size){
			final String[] vals=new String[size];
			if (!CommonUtils.isEmpty(this.remarks)){
				System.arraycopy(this.remarks, 0, vals, 0, this.remarks.length);
			}
			this.remarks=vals;
		}
		if (CommonUtils.size(this.options)<size){
			final String[] vals=new String[size];
			if (!CommonUtils.isEmpty(this.options)){
				System.arraycopy(this.options, 0, vals, 0, this.options.length);
			}
			this.options=vals;
		}
	}

	/**
	 * 指定した位置の値を設定します
	 * 
	 * @param index
	 * @param value
	 * @return 設定前の値を返します
	 */
	@SuppressWarnings("unchecked")
	public <T> T put(final int index, final Object value) {
		if (CommonUtils.size(this.values)<(index+1)){
			final Object[] vals=new Object[index+1];
			if (!CommonUtils.isEmpty(this.values)){
				System.arraycopy(this.values, 0, vals, 0, this.values.length);
			}
			this.values=vals;
		}
		final Object oldValue = this.values[index];
		final Column column = getTable().getColumns().get(index);
		this.values[index] = column.getConverter().convertObject(value);
		return (T) oldValue;
	}

	/**
	 * 値の設定を行います。値の型変換は行いません。
	 * 
	 * @param column
	 *            設定するカラム
	 * @param value
	 *            設定する値
	 * @return 設定前の値を返します
	 */
	@SuppressWarnings("unchecked")
	protected <T> T putDirect(final Column column, final Object value) {
		if (CommonUtils.size(this.values)<(column.getOrdinal()+1)){
			final Object[] vals=new Object[column.getOrdinal()+1];
			if (!CommonUtils.isEmpty(this.values)){
				System.arraycopy(this.values, 0, vals, 0, this.values.length);
			}
			this.values=vals;
		}
		final Object oldValue = this.values[column.getOrdinal()];
		this.values[column.getOrdinal()] = value;
		return (T) oldValue;
	}

	/**
	 * コメントの設定を行います。
	 * 
	 * @param column
	 *            設定するカラム
	 * @param remarks
	 *            設定するコメント
	 * @return 設定前のコメントを返します
	 */
	public String putRemarks(final Column column, final String remarks) {
		return putRemarks(column.getOrdinal(), remarks);
	}
	
	/**
	 * 指定した位置のコメントを設定します
	 * 
	 * @param index
	 * @param remarks
	 * @return 設定前のコメントを返します
	 */
	public String putRemarks(final int index, final String remarks) {
		if (CommonUtils.size(this.remarks)<(index+1)){
			final String[] vals=new String[index+1];
			if (!CommonUtils.isEmpty(this.remarks)){
				System.arraycopy(this.remarks, 0, vals, 0, this.remarks.length);
			}
			this.remarks=vals;
		}
		final String oldValue = this.getRemarksArray()[index];
		this.getRemarksArray()[index] = remarks;
		return oldValue;
	}
	
	private String[] getRemarksArray(){
		if (this.remarks==null){
			this.remarks=new String[values.length];
		}else{
			if (this.remarks.length<values.length){
				final String[] array=new String[values.length];
				System.arraycopy(this.remarks, 0, array, 0, this.remarks.length);
				this.remarks=array;
			}
		}
		return this.remarks;
	}

	/**
	 * 指定したカラム位置のコメントを取得します
	 * 
	 * @param index
	 * @return コメントの値
	 */
	public String getRemarks(final int index) {
		return getRemarksArray()[index];
	}

	/**
	 * 指定したカラムの行のコメントを取得を取得します
	 * 
	 * @param column
	 * @return コメントの値
	 */
	public String getRemarks(final Column column) {
		checkSize(column);
		return getRemarks(column.getOrdinal());
	}

	/**
	 * 指定したカラムの行のコメントを取得します
	 * 
	 * @param columnName
	 * @return 指定したカラムの行のコメント
	 */
	public String getRemarks(final String columnName) {
		final Column column = getTable().getColumns().get(columnName);
		return getRemarks(column);
	}

	/**
	 * オプションの設定を行います。
	 * 
	 * @param column
	 *            設定するカラム
	 * @param option
	 *            設定するオプション
	 * @return 設定前のオプションを返します
	 */
	public String putOption(final Column column, final String option) {
		checkSize(column);
		return putOption(column.getOrdinal(), option);
	}
	
	/**
	 * 指定した位置のオプションを設定します
	 * 
	 * @param index
	 * @param option
	 * @return 設定前のオプションを返します
	 */
	public String putOption(final int index, final String option) {
		if (CommonUtils.size(this.options)<(index+1)){
			final String[] vals=new String[index+1];
			if (!CommonUtils.isEmpty(this.options)){
				System.arraycopy(this.options, 0, vals, 0, this.options.length);
			}
			this.options=vals;
		}
		final String oldValue = this.getOptionArray()[index];
		this.getOptionArray()[index] = option;
		return oldValue;
	}
	
	private String[] getOptionArray(){
		if (this.options==null){
			this.options=new String[values.length];
		}else{
			if (this.options.length<values.length){
				final String[] array=new String[values.length];
				System.arraycopy(this.options, 0, array, 0, this.options.length);
				this.options=array;
			}
		}
		return this.options;
	}

	/**
	 * 指定したカラム位置のオプションを取得します
	 * 
	 * @param index
	 * @return オプションの値
	 */
	public String getOption(final int index) {
		return getOptionArray()[index];
	}

	/**
	 * 指定したカラムの行のオプションを取得を取得します
	 * 
	 * @param column
	 * @return オプションの値
	 */
	public String getOption(final Column column) {
		return getOption(column.getOrdinal());
	}

	/**
	 * 指定したカラムの行のオプションを取得します
	 * 
	 * @param columnName
	 * @return 指定したカラムの行のオプション
	 */
	public String getOption(final String columnName) {
		final Column column = getTable().getColumns().get(columnName);
		return getOption(column);
	}

	/**
	 * 値の設定を行います。
	 * 
	 * @param columnName
	 *            カラム名
	 * @param value
	 *            値
	 * @return 設定前の値を返します
	 */
	public <T> T put(final String columnName, final Object value) {
		final Column column = getTable().getColumns().get(columnName);
		return put(column, value);
	}

	/**
	 * 指定したカラムの行の値の取得
	 * 
	 * @param columnName
	 * @return 指定したカラムの行の値
	 */
	public <T> T get(final String columnName) {
		final Column column = getTable().getColumns().get(columnName);
		return get(column);
	}

	/**
	 * 指定したカラムの行の値の取得
	 * 
	 * @param columnNames
	 * @return 指定したカラムの行の値
	 */
	public List<Object> getAll(final String... columnNames) {
		return getAll(parent.getParent().getColumns().getAll(columnNames));
	}

	/**
	 * 値をマップとして取得します。
	 */
	public Map<String,Object> getValuesAsMap(){
		final Map<String,Object> map=CommonUtils.linkedMap();
		if (this.getTable()==null){
			return map;
		}
		for(final Column column:this.getTable().getColumns()){
			map.put(column.getName(), this.get(column));
		}
		return map;
	}

	/**
	 * 値をマップとして取得します。値がnullの場合はマップに格納されません。
	 */
	public Map<String,Object> getValuesAsMapWithoutNullValue(){
		final Map<String,Object> map=CommonUtils.linkedMap();
		if (this.getTable()==null){
			return map;
		}
		for(final Column column:this.getTable().getColumns()){
			final Object value=this.get(column);
			if (value!=null){
				map.put(column.getName(), value);
			}
		}
		return map;
	}

	/**
	 * 値をマップとして取得します。キーはキー名:PKもしくはキー名:UKとして格納されます。
	 */
	public Map<String,Object> getValuesAsMapWithKey(){
		final Map<String,Object> map=CommonUtils.linkedMap();
		if (this.getTable()==null){
			return map;
		}
		final Set<String> pks=CommonUtils.set();
		final Set<String> uks=CommonUtils.set();
		if (this.getTable().getPrimaryKeyConstraint()!=null){
			for(final ReferenceColumn rc:this.getTable().getPrimaryKeyConstraint().getColumns()){
				pks.add(rc.getName());
			}
		}
		if (pks.isEmpty()){
			for(final Column rc:this.getTable().getUniqueColumns()){
				uks.add(rc.getName());
			}
		}
		for(final Column column:this.getTable().getColumns()){
			if (pks.contains(column.getName())){
				map.put(column.getName()+"(PK)", this.get(column));
			}else if (uks.contains(column.getName())){
				map.put(column.getName()+"(UK)", this.get(column));
			} else{
				map.put(column.getName(), this.get(column));
			}
		}
		return map;
	}

	/**
	 * 指定したカラム位置の値を取得します
	 * 
	 * @param index
	 * @return 行の値
	 */
	@SuppressWarnings("unchecked")
	public <T> T get(final int index) {
		return (T) (values!=null?values[index]:null);
	}

	/**
	 * 指定したカラムの行の値を取得を取得します
	 * 
	 * @param column
	 * @return 行の値
	 */
	public <T> T get(final Column column) {
		return get(column.getOrdinal());
	}

	/**
	 * 指定したカラムの行の値を取得します
	 * 
	 * @param columns
	 * @return 行の値
	 */
	public List<Object> getAll(final Column... columns) {
		final List<Object> list = list(columns.length);
		final int size = columns.length;
		for (int i = 0; i < size; i++) {
			final Column column = columns[i];
			list.add(this.values[column.getOrdinal()]);
		}
		return list;
	}

	/**
	 * 指定したカラムの行の値を取得します
	 * 
	 * @param columns
	 * @return 行の値
	 */
	public List<Object> getAll(final Collection<Column> columns) {
		final List<Object> list = list(columns.size());
		for (final Column column : columns) {
			list.add(this.values[column.getOrdinal()]);
		}
		return list;
	}

	/**
	 * 指定したカラムの行の値がnullかどうかの判定
	 * 
	 * @param column
	 */
	public boolean isNull(final Column column) {
		return get(column) == null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Parent#getParent()
	 */
	@Override
	public RowCollection getParent() {
		return parent;
	}

	protected Row setParent(final RowCollection parent) {
		this.parent = parent;
		return instance();
	}
	
	private Row instance(){
		return this;
	}

	@Override
	public String getDataSourceInfo() {
		return dataSourceInfo;
	}

	@Override
	public Row setDataSourceInfo(final String dataSourceInfo) {
		this.dataSourceInfo = dataSourceInfo;
		return instance();
	}

	@Override
	public String getDataSourceDetailInfo() {
		return dataSourceDetailInfo;
	}

	@Override
	public Row setDataSourceDetailInfo(final String dataSourceDetailInfo) {
		this.dataSourceDetailInfo = dataSourceDetailInfo;
		return instance();
	}
	
	public Table getTable() {
		final RowCollection rc=this.getParent();
		if (rc==null){
			return null;
		}
		return rc.getParent();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder("Row[");
		final SeparatedStringBuilder sBuild = new SeparatedStringBuilder(",");
		final Table table=this.getTable();
		if (table!=null){
			for (final Column column : table.getColumns()) {
				final Object value=this.get(column);
				String comment=null;
				if (this.remarks!=null){
					comment=this.getRemarks(column);
				}
				Object option=null;
				if (this.options!=null){
					option=this.getOption(column);
				}
				final StringBuilder valueBuilder=new StringBuilder();
				valueBuilder.append("{");
				valueBuilder.append("value=");
				valueBuilder.append(value);
				if (comment!=null){
					valueBuilder.append(",comment=");
					valueBuilder.append(comment);
				}
				if (option!=null){
					valueBuilder.append(",option=");
					valueBuilder.append(option);
				}
				valueBuilder.append("}");
				sBuild.add(column.getName() + "="
						+ valueBuilder.toString());
			}
		}
		builder.append(sBuild.toString());
		builder.append("]");
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
	 * 行に格納された値の取得
	 * 
	 */
	public Object[] getValues() {
		if (values==null){
			return null;
		}
		final Object[] vals = new Object[values.length];
		System.arraycopy(values, 0, vals, 0, vals.length);
		return vals;
	}

	/**
	 * XML書き出し
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	protected void writeXml(final StaxWriter stax, final ColumnCollection columns)
			throws XMLStreamException {
		stax.newLine();
		stax.indent();
		stax.writeStartElement("row");
		stax.writeAttribute(SchemaProperties.DATA_SOURCE_INFO.getLabel(), this.getDataSourceInfo());
		stax.writeAttribute(SchemaProperties.DATA_SOURCE_DETAIL_INFO.getLabel(),
				this.getDataSourceDetailInfo());
		if (this.getHasErrors()) {
			stax.writeAttribute(SchemaProperties.HAS_ERRORS.getLabel(), this.getHasErrors());
		}
		stax.writeAttribute(SchemaProperties.CREATED_AT.getLabel(), this.getCreatedAt());
		stax.writeAttribute(SchemaProperties.LAST_ALTERED_AT.getLabel(), this.getLastAlteredAt());
		stax.addIndentLevel(1);
		final int size = columns.size();
		for (int i = 0; i < size; i++) {
			final Column column = columns.get(i);
			final int ordinal=column.getOrdinal();
			final Object val = this.get(ordinal);
			final String comment=this.getRemarks(ordinal);
			final Object option = this.getOption(ordinal);
			if (val == null&&comment==null&&option==null) {
			} else {
				stax.newLine();
				stax.indent();
				stax.writeStartElement(StaxWriter.VALUE_ELEMENT);
				stax.writeAttribute(StaxWriter.KEY_ELEMENT, column.getName());
				stax.writeAttribute(COMMENT, comment);
				stax.writeAttribute(OPTION, option);
				stax.writeCharacters(val);
				stax.writeEndElement();
			}
		}
		stax.addIndentLevel(-1);
		stax.newLine();
		stax.indent();
		stax.writeEndElement();
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
		writeXml(stax, this.getTable().getColumns());
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
	 * 指定したパスにXMLとして書き込みます
	 * 
	 * @param file
	 * @throws XMLStreamException
	 * @throws IOException
	 */
	@Override
	public void writeXml(final File file) throws XMLStreamException, IOException {
		BufferedOutputStream bos = null;
		try {
			bos = new BufferedOutputStream(new FileOutputStream(file));
			final StaxWriter stax = new StaxWriter(bos);
			writeXml(stax);
			bos.flush();
		} finally {
			close(bos);
		}
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
		final RowXmlReaderHandler handler = getDbObjectXmlReaderHandler();
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
		final RowXmlReaderHandler handler = getDbObjectXmlReaderHandler();
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#readXml(java.io.File)
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

	protected RowXmlReaderHandler getDbObjectXmlReaderHandler() {
		return new RowXmlReaderHandler();
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#writeXml(java.io.Writer)
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
	 * マップでプロパティの値を取得します
	 * 
	 */
	@Override
	public Map<String, Object> toMap() {
		final GetPropertyMapEqualsHandler equalsHandler = new GetPropertyMapEqualsHandler(
				this);
		this.equals(this, new GetPropertyMapEqualsHandler(this));
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#equals(java.lang.Object,
	 * com.sqlapp.data.schemas.EqualsHandler)
	 */
	@Override
	public boolean equals(final Object obj, final EqualsHandler equalsHandler) {
		if (!(obj instanceof Row)) {
			return false;
		}
		if (equalsHandler.referenceEquals(this, obj)) {
			return true;
		}
		final Row val = (Row) obj;
		if (!equals(VALUES, val,
				this.getValues(), val.getValues(), equalsHandler, EqualsUtils.getEqualsSupplier(this.getValues(), val.getValues()))) {
			return false;
		}
		if (!equals(SchemaProperties.REMARKS, val,
				this.remarks, val.remarks, equalsHandler, EqualsUtils.getEqualsSupplier(this.remarks, val.remarks))) {
			return false;
		}
		if (!equals(OPTIONS, val,
				this.options, val.options, equalsHandler, EqualsUtils.getEqualsSupplier(this.options, val.options))) {
			return false;
		}
		if (!equals(SchemaProperties.DATA_SOURCE_ROW_NUMBER, val, this.getDataSourceRowNumber(), val.getDataSourceRowNumber(),
				equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.CREATED_AT, val, this.getCreatedAt(), val.getCreatedAt(),
				equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.LAST_ALTERED_AT, val, this.getLastAlteredAt(),
				val.getLastAlteredAt(), equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ROW_ID, val, this.getRowId(), val.getRowId(), equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DATA_SOURCE_INFO, val, this.getDataSourceInfo(),
				val.getDataSourceInfo(), equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DATA_SOURCE_DETAIL_INFO, val,
				this.getDataSourceDetailInfo(), val.getDataSourceDetailInfo(),
				equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.HAS_ERRORS.getLabel(), val, this.getHasErrors(), val.getHasErrors(),
				equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	protected boolean equals(final String propertyName, final Row target,
			final Object value, final Object targetValue, final EqualsHandler equalsHandler, final BooleanSupplier booleanSupplier) {
		return equalsHandler.valueEquals(propertyName, this, target, value,
				targetValue, booleanSupplier);
	}

	protected boolean equals(final ISchemaProperty props, final Row target,
			final Object value, final Object targetValue, final EqualsHandler equalsHandler, final BooleanSupplier booleanSupplier) {
		return equals(props.getLabel(), target, value,
				targetValue, equalsHandler, booleanSupplier);
	}

	protected boolean equals(final ISchemaProperty props, final Row target,
			final Object value, final Object targetValue, final EqualsHandler equalsHandler) {
		return equals(props.getLabel(), target, value,
				targetValue, equalsHandler);
	}

	protected boolean equals(final String propertyName, final Row target, final Object value1,
			final Object value2, final EqualsHandler equalsHandler) {
		return equalsHandler.valueEquals(propertyName, this,
				target, value1, value2, EqualsUtils.getEqualsSupplier(value1, value2));
	}

	private final String SIMPLE_NAME = AbstractNamedObject.getSimpleName(this
			.getClass());

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final HashCodeBuilder builder = new HashCodeBuilder();
		builder.append(this.getValues());
		builder.append(this.getRowId());
		builder.append(this.getDataSourceInfo());
		builder.append(this.getDataSourceDetailInfo());
		builder.append(this.getHasErrors());
		return builder.hashCode();
	}

	/**
	 * XMLでのタグ名
	 * 
	 */
	protected String getSimpleName() {
		return SIMPLE_NAME;
	}

	@Override
	public Long getRowId() {
		return rowId;
	}

	@Override
	public Row setRowId(final Long rowId) {
		this.rowId = rowId;
		return instance();
	}

	@Override
	public boolean getHasErrors() {
		return hasErrors;
	}

	@Override
	public Row setHasErrors(final boolean hasErrors) {
		this.hasErrors = hasErrors;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(final Row o) {
		UniqueConstraint uc = getUniqueConstraint();
		if (uc == null) {
			uc = o.getUniqueConstraint();
			if (uc == null) {
				return 0;
			}
		}
		for (final ReferenceColumn column : uc.getColumns()) {
			final Object obj1 = this.get(column.getName());
			final Object obj2 = o.get(column.getName());
			final int ret = CommonUtils.compare(obj1, obj2);
			if (ret != 0) {
				return ret;
			}
		}
		return 0;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.DbObject#like(java.lang.Object)
	 */
	@Override
	public boolean like(final Object obj) {
		if (!(obj instanceof Row)) {
			return false;
		}
		final UniqueConstraint uc = getUniqueConstraint();
		if (uc == null) {
			return this.equals(obj);
		}
		final Object[] keyValues = new Object[uc.getColumns().size()];
		final ReferenceColumnCollection rcc = uc.getColumns();
		final int size = rcc.size();
		final Row cstRow = (Row) obj;
		for (int i = 0; i < size; i++) {
			final ReferenceColumn rc = rcc.get(i);
			keyValues[i] = cstRow.get(rc.getName());
		}
		boolean find = true;
		for (int i = 0; i < size; i++) {
			final ReferenceColumn rc = rcc.get(i);
			final Object val = this.get(rc.getName());
			if (!CommonUtils.eq(keyValues[i], val)) {
				find = false;
				break;
			}
		}
		return find;
	}

	protected UniqueConstraint getUniqueConstraint() {
		final Table table = this.getAncestor(Table.class);
		final List<UniqueConstraint> ucs = table.getConstraints()
				.getUniqueConstraints();
		if (ucs.size() == 0) {
			return null;
		}
		final UniqueConstraint uc = CommonUtils.first(ucs);
		return uc;
	}

	@Override
	public boolean like(final Object obj, final EqualsHandler equalsHandler) {
		if (!(obj instanceof Row)) {
			return false;
		}
		final Table table = this.getAncestor(Table.class);
		final List<UniqueConstraint> ucs = table.getConstraints()
				.getUniqueConstraints();
		if (ucs.size() == 0) {
			return this.equals(obj);
		}
		final UniqueConstraint uc = CommonUtils.first(ucs);
		final Object[] keyValues = new Object[uc.getColumns().size()];
		final ReferenceColumnCollection rcc = uc.getColumns();
		final int size = rcc.size();
		final Row cstRow = (Row) obj;
		for (int i = 0; i < size; i++) {
			final ReferenceColumn rc = rcc.get(i);
			keyValues[i] = cstRow.get(rc.getName());
		}
		boolean find = true;
		for (int i = 0; i < size; i++) {
			final ReferenceColumn rc = rcc.get(i);
			final Object val = this.get(rc.getName());
			if (equalsHandler.valueEquals(rc.getName(), this, cstRow,
					keyValues[i], val, EqualsUtils.getEqualsSupplier(keyValues[i], val))) {
				find = false;
				break;
			}
		}
		return find;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DbObject#diff(com.sqlapp.data.schemas.DbObject)
	 */
	@Override
	public DbObjectDifference diff(final Row obj) {
		final DbObjectDifference diff = new DbObjectDifference(this, obj);
		return diff;
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
	public Row setCreatedAt(final Timestamp created) {
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
	public Row setLastAlteredAt(final Timestamp lastAltered) {
		this.lastAlteredAt = lastAltered;
		return instance();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DbObject#diff(com.sqlapp.data.schemas.DbObject,
	 * com.sqlapp.data.schemas.EqualsHandler)
	 */
	@Override
	public DbObjectDifference diff(final Row obj, final EqualsHandler equalsHandler) {
		final DbObjectDifference diff = new DbObjectDifference(this, obj,
				equalsHandler);
		return diff;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.DbObject#applyAll(java.util.function.Consumer)
	 */
	@Override
	public Row applyAll(final Consumer<DbObject<?>> consumer){
		this.equals(this, new GetAllDbObjectEqualsHandler(consumer));
		return this.instance();
	}

	@Override
	public Long getDataSourceRowNumber() {
		return dataSourceRowNumber;
	}

	@Override
	public Row setDataSourceRowNumber(final Long value) {
		this.dataSourceRowNumber=value;
		return instance();
	}

}