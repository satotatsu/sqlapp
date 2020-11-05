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
import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.DbUtils.close;
import static com.sqlapp.util.DbUtils.setColumnMetadata;
import static com.sqlapp.util.DbUtils.setPrimaryKeyInfo;
import static com.sqlapp.util.TableUtils.getAutoIncrementColumn;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.function.AddDbObjectPredicate;
import com.sqlapp.data.schemas.properties.CharacterSemanticsProperty;
import com.sqlapp.data.schemas.properties.CharacterSetProperty;
import com.sqlapp.data.schemas.properties.CollationProperty;
import com.sqlapp.data.schemas.properties.CompressionProperty;
import com.sqlapp.data.schemas.properties.PartitioningProperty;
import com.sqlapp.data.schemas.properties.ReadonlyProperty;
import com.sqlapp.data.schemas.properties.TableDataStoreTypeProperty;
import com.sqlapp.data.schemas.properties.TableTypeProperty;
import com.sqlapp.data.schemas.properties.complex.IndexTableSpaceProperty;
import com.sqlapp.data.schemas.properties.complex.LobTableSpaceProperty;
import com.sqlapp.data.schemas.properties.complex.TableSpaceProperty;
import com.sqlapp.data.schemas.properties.object.ColumnsProperty;
import com.sqlapp.data.schemas.properties.object.ConstraintsProperty;
import com.sqlapp.data.schemas.properties.object.IndexesProperty;
import com.sqlapp.data.schemas.properties.object.InheritsProperty;
import com.sqlapp.data.schemas.properties.object.PartitionParentProperty;
import com.sqlapp.data.schemas.properties.object.RowsProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * テーブルに相当するオブジェクト
 * 
 */
public class Table extends AbstractSchemaObject<Table> implements
		CollationProperty<Table>, CharacterSetProperty<Table>,
		CharacterSemanticsProperty<Table>, HasParent<TableCollection>,
		Mergeable<Table>, RowIteratorHandlerProperty
		, ColumnsProperty<Table>
		, RowsProperty<Table>
		, ConstraintsProperty<Table>
		, IndexesProperty<Table>
		, InheritsProperty<Table>
		, TableSpaceProperty<Table>
		, IndexTableSpaceProperty<Table>
		, LobTableSpaceProperty<Table>
		, TableTypeProperty<Table>
		, TableDataStoreTypeProperty<Table>
		, PartitioningProperty<Table>
		, ReadonlyProperty<Table>
		, CompressionProperty<Table>
		, PartitionParentProperty<Table>
		{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7120013699239800425L;

	/** Column collection */
	private ColumnCollection columns = new ColumnCollection(this);
	/** Row collection */
	private RowCollection rows = new RowCollection(this);
	/** 制約のコレクション */
	private ConstraintCollection constraints = new ConstraintCollection(this);
	/** インデックスのコレクション */
	private IndexCollection indexes = new IndexCollection(this);
	/** テーブルタイプ */
	private TableType tableType = null;
	/** テーブルデータ格納タイプ */
	private TableDataStoreType tableDataStoreType =null;
	/** 読み込み専用 */
	private Boolean readonly = null;
	/** 圧縮 */
	private boolean compression = false;
	/** パーティション情報 */
	private Partitioning partitioning = null;
	/** テーブルスペース */
	@SuppressWarnings("unused")
	private TableSpace tableSpace = null;
	/** インデックステーブルスペース */
	@SuppressWarnings("unused")
	private TableSpace indexTableSpace = null;
	/** LOBテーブルスペース */
	@SuppressWarnings("unused")
	private TableSpace lobTableSpace = null;
	/** カラムの文字列のセマンティックス */
	@SuppressWarnings("unused")
	private CharacterSemantics characterSemantics = null;
	/** characterSetName */
	@SuppressWarnings("unused")
	private String characterSet = null;
	/** collationName */
	@SuppressWarnings("unused")
	private String collation = null;
	/** 継承元テーブル */
	private InheritCollection inherits = new InheritCollection(this);
	/**Partition Parent*/
	private PartitionParent partitionParent;
	/**
	 * 子リレーション
	 */
	private List<ForeignKeyConstraint> childRelations=CommonUtils.list();
	
	/**
	 * デフォルトコンストラクタ
	 */
	public Table() {
	}

	@Override
	protected Supplier<Table> newInstance(){
		return ()->new Table();
	}
	
	/**
	 * @return the inherits
	 */
	@Override
	public InheritCollection getInherits() {
		return inherits;
	}

	/**
	 * コンストラクタ
	 */
	public Table(String tableName) {
		super(tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedDdlObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof Table)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		Table val = cast(obj);
		if (!equals(SchemaObjectProperties.COLUMNS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.INDEXES, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.PARTITIONING, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.PARTITION_PARENT, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.CONSTRAINTS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.READONLY, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.TABLE_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.TABLE_DATA_STORE_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.COMPRESSION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.TABLE_SPACE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.INDEX_TABLE_SPACE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.LOB_TABLE_SPACE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.INHERITS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.CHARACTER_SET, val, equalsHandler
				, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getCharacterSet(), val.getCharacterSet()))) {
			return false;
		}
		if (!equals(SchemaProperties.COLLATION, val, equalsHandler
				, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getCollation(), val.getCollation()))) {
			return false;
		}
		if (!equals(SchemaProperties.CHARACTER_SEMANTICS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.ROWS, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.TABLE_SPACE_NAME, this.getTableSpaceName());
		builder.add(SchemaProperties.INDEX_TABLE_SPACE_NAME, this.getIndexTableSpaceName());
		builder.add(SchemaProperties.LOB_TABLE_SPACE_NAME, this.getLobTableSpaceName());
		if (this.isCompression()) {
			builder.add(SchemaProperties.COMPRESSION, this.isCompression());
		}
		builder.add(SchemaProperties.READONLY, this.getReadonly());
		builder.add(SchemaProperties.TABLE_TYPE, this.getTableType());
		builder.add(SchemaProperties.TABLE_DATA_STORE_TYPE, this.getTableDataStoreType());
		builder.add(SchemaObjectProperties.INHERITS, this.getInherits());
		builder.add(SchemaProperties.CHARACTER_SET, this.getCharacterSet());
		builder.add(SchemaProperties.COLLATION, this.getCollation());
		builder.add(SchemaProperties.CHARACTER_SEMANTICS, this.getCharacterSemantics());
	}

	/**
	 * @return the tableType
	 */
	@Override
	public TableType getTableType() {
		return tableType;
	}

	/**
	 * @param tableType
	 *            the tableType to set
	 */
	@Override
	public Table setTableType(TableType tableType) {
		this.tableType = tableType;
		return instance();
	}

	/**
	 * @return the readOnly
	 */
	@Override
	public Boolean getReadonly() {
		return readonly;
	}

	/**
	 * @param readOnly
	 *            the readOnly to set
	 */
	@Override
	public Table setReadonly(Boolean readOnly) {
		this.readonly = readOnly;
		return instance();
	}

	/**
	 * @param tableType
	 *            the tableType to set
	 */
	@Override
	public Table setTableType(String tableType) {
		this.tableType = TableType.parse(tableType);
		return instance();
	}

	@Override
	public TableDataStoreType getTableDataStoreType() {
		return tableDataStoreType;
	}

	@Override
	public Table setTableDataStoreType(TableDataStoreType tableDataStoreType) {
		this.tableDataStoreType = tableDataStoreType;
		return instance();
	}

	@Override
	public ColumnCollection getColumns() {
		return columns;
	}

	protected Table setColumns(ColumnCollection columns) {
		if (columns != null) {
			columns.setParent(this);
		}
		this.columns = columns;
		return this;
	}

	@Override
	public RowCollection getRows() {
		return rows;
	}

	protected Table setRows(RowCollection rows) {
		if (rows != null) {
			rows.setParent(this);
		}
		this.rows = rows;
		return this;
	}

	/**
	 * @param inherits
	 *            the inherits to set
	 */
	protected Table setInherits(InheritCollection inherits) {
		this.inherits = inherits;
		if (this.inherits != null) {
			this.inherits.setParent(this);
		}
		return this;
	}

	/**
	 * メタデータのとデータの読み込み
	 * 
	 * @param resultSet
	 */
	public void read(Connection connection, ResultSet resultSet) {
		readMetaData(connection, resultSet);
		readData(resultSet);
	}

	/**
	 * メタデータの読み込み
	 * 
	 * @param connection
	 * @param resultSet
	 */
	public void readMetaData(Connection connection, ResultSet resultSet) {
		Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		if (this.getDialect()==null){
			this.setDialect(dialect);
		}
		setColumnMetadata(dialect, resultSet, this);
		setPrimaryKeyInfo(connection, this);
	}

	/**
	 * ResultSetからデータの読み込み
	 * 
	 * @param resultSet
	 */
	public void readData(ResultSet resultSet) {
		try {
			ResultSetMetaData metadata=resultSet.getMetaData();
			Column[] columns=new Column[metadata.getColumnCount()];
			Dialect dialect=this.getDialect();
			for(int i=1;i<=metadata.getColumnCount();i++){
				String name = metadata.getColumnLabel(i);
				if (name == null) {
					name = metadata.getColumnName(i);
				}
				Column column = getColumns().get(name);
				if (column==null){
					column=new Column();
					String productDataType=metadata.getColumnTypeName(i);
					long precision=metadata.getPrecision(i);
					int scale=metadata.getScale(i);
					if (dialect!=null){
						dialect.setDbType(productDataType, precision, scale, column);
					}
					getColumns().add(column);
				}
				columns[i-1]=column;
			}
			int size = columns.length;
			while (resultSet.next()) {
				Row row = this.newRow();
				for (int i = 1; i <= size; i++) {
					Column column = columns[i-1];
					Object obj = resultSet.getObject(column.getName());
					row.put(column, obj);
				}
				this.getRows().add(row);
			}
		} catch (SQLException e) {
			close(resultSet);
			throw new DataException(e);
		}
	}

	/**
	 * 新規行を作成します
	 * 
	 */
	public Row newRow() {
		Row obj = new Row();
		obj.setParent(this.getRows());
		return obj;
	}

	/**
	 * 新規カラムを作成します
	 * 
	 */
	public Column newColumn() {
		Column obj = new Column();
		obj.setColumns(this.getColumns());
		return obj;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Mergeable#merge(com.sqlapp.data.schemas.
	 * DbCommonObject)
	 */
	@Override
	public void merge(Table table) {
		for (Column column : table.getColumns()) {
			if (!this.getColumns().contains(column.getName())) {
				// カラムが無い場合は追加
				this.getColumns().add(column.clone());
			}
		}
		for (Row row : table.getRows()) {
			Row copyRow = this.newRow();
			for (Column column : table.getColumns()) {
				Column thisColumn = this.getColumns().get(column.getName());
				copyRow.put(thisColumn, row.get(column));
			}
			this.getRows().add(copyRow);
		}
	}

	public UniqueConstraint getPrimaryKeyConstraint() {
		return constraints.getPrimaryKeyConstraint();
	}

	/**
	 * プライマリキーの設定
	 * 
	 * @param primaryKey
	 */
	public Table setPrimaryKey(Column... primaryKey) {
		setPrimaryKey(null, primaryKey);
		return this;
	}

	/**
	 * プライマリキーを設定します
	 * 
	 * @param primaryKey
	 */
	public Table setPrimaryKey(String primaryKeyName, Column... primaryKey) {
		String constraintName = primaryKeyName;
		if (isEmpty(constraintName)) {
			constraintName = "PK_" + this.getName();
		}
		this.getConstraints().addUniqueConstraint(constraintName, true,
				primaryKey);
		return this;
	}

	/**
	 * AutoIncrementカラムの取得
	 * 
	 */
	public List<Column> getAutoIncrementColumns() {
		return getAutoIncrementColumn(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public TableCollection getParent() {
		return (TableCollection) super.getParent();
	}

	protected void setTables(TableCollection tableCollection) {
		this.setParent(tableCollection);
	}

	@Override
	public ConstraintCollection getConstraints() {
		return constraints;
	}

	protected Table setIndexes(IndexCollection indexes) {
		this.indexes = indexes;
		if (this.indexes != null) {
			this.indexes.setParent(null);
		}
		return this;
	}

	@Override
	public IndexCollection getIndexes() {
		return indexes;
	}

	@Override
	public Partitioning getPartitioning() {
		if (this.partitioning != null) {
			this.partitioning.setTable(this);
		}
		return partitioning;
	}

	@Override
	public Table setPartitioning(Partitioning partitioning) {
		if (this.partitioning != null) {
			this.partitioning.setTable(null);
		}
		if (partitioning!=null){
			partitioning.setTable(this);
		}
		this.partitioning = partitioning;
		return this;
	}

	public Table removePartitioning() {
		return this.setPartitioning(null);
	}

	@Override
	public Table setPartitionParent(PartitionParent partitionParent) {
		if (this.partitionParent != null) {
			Table parent=this.partitionParent.getParent();
			if (parent!=null) {
				if (parent.getPartitioning()!=null) {
					parent.getPartitioning().removePartitionTable(this);
				}
			}
			this.partitionParent.setParent(null);
		}
		if (partitionParent!=null){
			partitionParent.setParent(this);
			if (partitionParent.getTable()!=null) {
				Table parentPartition=SchemaUtils.getTableOnlyFromParent(partitionParent.getTable().getSchemaName(), partitionParent.getTable().getName(), this);
				partitionParent.setTable(parentPartition);
				if (partitionParent.getTable().getPartitioning()==null) {
					partitionParent.getTable().toPartitioning();
				}
			}
		}
		this.partitionParent = partitionParent;
		return instance();
	}
	
	@Override
	public PartitionParent getPartitionParent() {
		return this.partitionParent;
	}

	/**
	 * @return the compression
	 */
	@Override
	public boolean isCompression() {
		return compression;
	}

	/**
	 * @param compression
	 *            the compression to set
	 */
	@Override
	public Table setCompression(boolean compression) {
		this.compression = compression;
		return instance();
	}

	/**
	 * @param value
	 *            the characterSet to set
	 */
	@Override
	public Table setCharacterSet(String value) {
		this.characterSet = value;
		return this;
	}

	protected void writeCharacterSet(StaxWriter stax)
			throws XMLStreamException {
		String value=SchemaUtils.getParentCharacterSet(this);
		if (!CommonUtils.eqIgnoreCase(value,
				this.getCharacterSet())) {
			stax.writeAttribute(SchemaProperties.CHARACTER_SET.getLabel(), this.getCharacterSet());
		}
	}

	protected void writeCollation(StaxWriter stax)
			throws XMLStreamException {
		String value=SchemaUtils.getParentCollation(this);
		if (!CommonUtils.eqIgnoreCase(value,
				this.getCollation())) {
			stax.writeAttribute(SchemaProperties.COLLATION.getLabel(), this.getCollation());
		}
	}

	protected void writeCharacterSemantics(StaxWriter stax)
			throws XMLStreamException {
		CharacterSemantics value=SchemaUtils.getParentCharacterSemantics(this);
		if (!CommonUtils.eq(value,
				this.getCharacterSemantics())) {
			stax.writeAttribute(SchemaProperties.CHARACTER_SEMANTICS.getLabel(),
					this.getCharacterSemantics());
		}
	}

	/**
	 * @param value
	 *            the collation to set
	 */
	@Override
	public Table setCollation(String value) {
		this.collation = value;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.CharacterSemanticsProperty#setCharacterSemantics
	 * (com.sqlapp.data.schemas.CharacterSemantics)
	 */
	@Override
	public Table setCharacterSemantics(CharacterSemantics characterSemantics) {
		this.characterSemantics = characterSemantics;
		return instance();
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.READONLY.getLabel(), this.getReadonly());
		stax.writeAttribute(SchemaProperties.TABLE_TYPE.getLabel(), this.getTableType());
		stax.writeAttribute(SchemaProperties.TABLE_DATA_STORE_TYPE.getLabel(),
				this.getTableDataStoreType());
		stax.writeAttribute(SchemaProperties.TABLE_SPACE_NAME.getLabel(), this.getTableSpaceName());
		stax.writeAttribute(SchemaProperties.INDEX_TABLE_SPACE_NAME.getLabel(), this.getIndexTableSpaceName());
		stax.writeAttribute(SchemaProperties.LOB_TABLE_SPACE_NAME.getLabel(), this.getLobTableSpaceName());
		if (this.isCompression()) {
			stax.writeAttribute(SchemaProperties.COMPRESSION.getLabel(), this.isCompression());
		}
		writeCharacterSet(stax);
		writeCollation(stax);
		writeCharacterSemantics(stax);
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		if (!isEmpty(getColumns())) {
			getColumns().writeXml(stax);
		}
		if (!isEmpty(getConstraints())) {
			getConstraints().writeXml(stax);
		}
		if (!isEmpty(getIndexes())) {
			getIndexes().writeXml(stax);
		}
		if (!isEmpty(getPartitioning())) {
			getPartitioning().writeXml(stax);
		}
		if (!isEmpty(getInherits())) {
			getInherits().writeXml(stax);
		}
		if (!isEmpty(getPartitionParent())) {
			this.getPartitionParent().writeXml(stax);
		}
		writeXmlRows(stax);
		super.writeXmlOptionalValues(stax);
	}
	
	protected void writeXmlRows(StaxWriter stax) throws XMLStreamException{
		if (isTable()) {
			getRows().writeXml(stax);
		}
	}

	/**
	 * RowデータのみをXMLに出力します。
	 * @param stax
	 * @throws XMLStreamException
	 */
	public void writeRowData(StaxWriter stax)
			throws XMLStreamException {
		if (isTable()) {
			getRows().writeXml(stax);
		}
	}
	
	/**
	 * RowデータのみをXMLに出力します。
	 * @param stream
	 * @throws XMLStreamException
	 */
	public void writeRowData(OutputStream stream) throws XMLStreamException {
		StaxWriter stax = new StaxWriter(stream) {
			@Override
			protected boolean isWriteStartDocument() {
				return true;
			}
		};
		writeRowData(stax);
	}

	/**
	 * RowデータのみをXMLに出力します。
	 * @param writer
	 * @throws XMLStreamException
	 */
	public void writeRowData(Writer writer) throws XMLStreamException {
		StaxWriter stax = new StaxWriter(writer) {
			@Override
			protected boolean isWriteStartDocument() {
				return true;
			}
		};
		writeRowData(stax);
	}

	/**
	 * RowデータのみをXMLに出力します。
	 * @param path
	 * @throws XMLStreamException
	 */
	public void writeRowData(String path) throws XMLStreamException, IOException {
		writeRowData(new File(path));
	}

	/**
	 * RowデータのみをXMLに出力します。
	 * @param file
	 * @throws XMLStreamException
	 */
	public void writeRowData(File file) throws XMLStreamException, IOException {
		BufferedOutputStream stream = null;
		try {
			stream = new BufferedOutputStream(new FileOutputStream(file));
			StaxWriter stax = new StaxWriter(stream);
			writeRowData(stax);
			stream.flush();
		} finally {
			FileUtils.close(stream);
		}
	}
	
	
	private boolean isTable() {
		if (this instanceof View) {
			return false;
		} else if (this instanceof Mview) {
			return false;
		}
		return true;
	}

	/**
	 * @param constraints
	 *            the constraints to set
	 */
	protected Table setConstraints(ConstraintCollection constraints) {
		this.constraints = constraints;
		return this;
	}

	@Override
	public Table setCaseSensitive(boolean caseSensitive) {
		columns.setCaseSensitive(caseSensitive);
		constraints.setCaseSensitive(caseSensitive);
		indexes.setCaseSensitive(caseSensitive);
		if (partitioning != null) {
			partitioning.setCaseSensitive(caseSensitive);
		}
		return super.setCaseSensitive(caseSensitive);
	}

	/**
	 * テーブルのデータ格納の種類
	 * 
	 * @author TATSUO
	 * 
	 */
	public enum TableDataStoreType implements EnumProperties {
		Row, Column, Hybrid;
		public static TableDataStoreType parse(String text) {
			if (text==null){
				return null;
			}
			for (TableDataStoreType enm : values()) {
				if (enm.toString().equalsIgnoreCase(text)) {
					return enm;
				}
			}
			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.sqlapp.data.schemas.EnumProperties#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return this.toString().toUpperCase();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * com.sqlapp.data.schemas.EnumProperties#getDisplayName(java.util.Locale
		 * )
		 */
		@Override
		public String getDisplayName(Locale locale) {
			return getDisplayName();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.sqlapp.data.schemas.EnumProperties#getSqlValue()
		 */
		@Override
		public String getSqlValue() {
			return getDisplayName();
		}
	}

	/**
	 * テーブルの種類
	 * 
	 * @author TATSUO
	 * 
	 */
	public enum TableType implements EnumProperties {
		File("FILE", "F.*"), 
		/**In Memory*/
		Memory("MEMORY", "M.*")
		, 
		Cache("CACHE", "Cache.*")
		, 
		Temporary("TEMPORARY", "T.*"),
		Flex("Flex", "F.*"),
		;
		private final String text;
		private final Pattern pattern;

		TableType(String text, String patternText) {
			this.text = text;
			pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
		}

		public static TableType parse(String text) {
			for (TableType enm : values()) {
				Matcher matcher = enm.pattern.matcher(text);
				if (matcher.matches()) {
					return enm;
				}
			}
			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.sqlapp.data.schemas.EnumProperties#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return text;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * com.sqlapp.data.schemas.EnumProperties#getDisplayName(java.util.Locale
		 * )
		 */
		@Override
		public String getDisplayName(Locale locale) {
			return getDisplayName();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.sqlapp.data.schemas.EnumProperties#getSqlValue()
		 */
		@Override
		public String getSqlValue() {
			return getDisplayName();
		}
	}

	/**
	 * ユニーク性を担保するカラムのリストを取得します
	 * 
	 */
	public List<Column> getUniqueColumns() {
		for (UniqueConstraint uniqueConstraint : getConstraints()
				.getUniqueConstraints()) {
			List<Column> columns = CommonUtils.list();
			for (ReferenceColumn rColumn : uniqueConstraint.getColumns()) {
				Column column = getColumns().get(rColumn.getName());
				if (column == null) {
					break;
				}
				columns.add(column);
			}
			if (uniqueConstraint.getColumns().size() == columns.size()) {
				return columns;
			}
		}
		return null;
	}

	@Override
	protected TableXmlReaderHandler getDbObjectXmlReaderHandler() {
		return new TableXmlReaderHandler();
	}

	/**
	 * @param rowIteratorHandler
	 *            the rowIteratorHandler to set
	 */
	@Override
	public void setRowIteratorHandler(RowIteratorHandler rowIteratorHandler) {
		this.getRows().setRowIteratorHandler(rowIteratorHandler);
	}
	
	public RowCollection getRows(RowIteratorHandler rowIteratorHandler) {
		this.getRows().setRowIteratorHandler(rowIteratorHandler);
		return this.getRows();
	}
	
	public boolean isDefaultRowIteratorHandler(){
		return this.getRows().getRowIteratorHandler() instanceof DefaultRowIteratorHandler;
	}

	/**
	 * @param addDbObjectFilter
	 *            the addDbObjectFilter to set
	 */
	public void setAddDbObjectFilter(AddDbObjectPredicate addDbObjectFilter) {
		this.getColumns().setAddDbObjectPredicate(addDbObjectFilter);
		this.getRows().setAddDbObjectFilter(addDbObjectFilter);
		this.getConstraints().setAddDbObjectPredicate(addDbObjectFilter);
		this.getIndexes().setAddDbObjectPredicate(addDbObjectFilter);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#validate()
	 */
	@Override
	protected void validate() {
		super.validate();
		if (this.getTableSpace() != null) {
		}
		if (getColumns() != null) {
			getColumns().setParent(this);
			getColumns().validate();
		}
		if (getConstraints() != null) {
			getConstraints().setParent(this);
			getConstraints().validate();
		}
		if (this.getIndexes() != null) {
			getIndexes().setParent(this);
			getIndexes().validate();
		}
		if (this.getPartitioning() != null) {
			getPartitioning().setParent(this);
			getPartitioning().validate();
		}
		if (this.getPartitionParent() != null) {
			getPartitionParent().setParent(this);
			getPartitionParent().validate();
		}
		cleanupChildRelations();
	}
	
	private void cleanupChildRelations(){
		List<ForeignKeyConstraint> removeTargets=CommonUtils.list();
		Set<String> names=CommonUtils.set();
		this.getChildRelations().forEach(fk->{
			if (fk.getRelatedTable()==null){
				removeTargets.add(fk);
			}else if (!fk.getRelatedTable().equals(this)){
				removeTargets.add(fk);
			}
			if (fk.getName()!=null&&names.contains(fk.getName())){
				removeTargets.add(fk);
			} else{
				names.add(fk.getName());
			}
		});
		for(ForeignKeyConstraint fk:removeTargets){
			this.getChildRelations().remove(fk);
		}
	}
	
	/**
	 * @return the childRelations
	 */
	public List<ForeignKeyConstraint> getChildRelations() {
		return childRelations;
	}

	/**
	 * 
	 */
	public List<ForeignKeyConstraint> getChildRelations(Predicate<ForeignKeyConstraint> p) {
		List<ForeignKeyConstraint> result = list(childRelations.size());
		int size = childRelations.size();
		for (int i = 0; i < size; i++) {
			ForeignKeyConstraint c = childRelations.get(i);
			ForeignKeyConstraint cc = cast(c);
			if (p.test(cc)){
				result.add(cc);
			}
		}
		return result;
	}
	
	/**
	 * @return the childRelations
	 */
	protected Table addChildRelation(ForeignKeyConstraint fk) {
		if (!childRelations.contains(fk)){
			childRelations.add(fk);
		}
		return instance();
	}

	
	/**
	 * @param childRelations the childRelations to set
	 */
	protected void setChildRelations(List<ForeignKeyConstraint> childRelations) {
		this.childRelations = childRelations;
	}

	public static enum TableOrder{
		CREATE(new TableCreateOrderComparator()),
		DROP(new TableDropOrderComparator()),;
		
		private TableOrder(Comparator<Table> comparator){
			this.comparator=comparator;
		}
		
		private Comparator<Table> comparator;

		/**
		 * @return the comparator
		 */
		public Comparator<Table> getComparator() {
			return comparator;
		}
		
	}
}
