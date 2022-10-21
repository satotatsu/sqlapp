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
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.data.schemas.properties.CharacterSemanticsProperty;
import com.sqlapp.data.schemas.properties.CharacterSetProperty;
import com.sqlapp.data.schemas.properties.CollationProperty;
import com.sqlapp.data.schemas.properties.CompressionProperty;
import com.sqlapp.data.schemas.properties.CompressionTypeProperty;
import com.sqlapp.data.schemas.properties.PartitioningProperty;
import com.sqlapp.data.schemas.properties.ReadonlyProperty;
import com.sqlapp.data.schemas.properties.TableDataStoreTypeProperty;
import com.sqlapp.data.schemas.properties.TableTypeProperty;
import com.sqlapp.data.schemas.properties.UnloggedProperty;
import com.sqlapp.data.schemas.properties.complex.IndexTableSpaceProperty;
import com.sqlapp.data.schemas.properties.complex.LobTableSpaceProperty;
import com.sqlapp.data.schemas.properties.complex.TableSpaceProperty;
import com.sqlapp.data.schemas.properties.object.ColumnsProperty;
import com.sqlapp.data.schemas.properties.object.ConstraintsProperty;
import com.sqlapp.data.schemas.properties.object.IndexesProperty;
import com.sqlapp.data.schemas.properties.object.InheritsProperty;
import com.sqlapp.data.schemas.properties.object.PartitionParentProperty;
import com.sqlapp.data.schemas.properties.object.RowsProperty;
import com.sqlapp.data.schemas.rowiterator.ResultSetRowIteratorHandler;
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
		, CompressionTypeProperty<Table>
		, UnloggedProperty<Table>
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
	private boolean compression =  (Boolean)SchemaProperties.COMPRESSION.getDefaultValue();
	/** アンログ */
	private boolean unlogged =  (Boolean)SchemaProperties.UNLOGGED.getDefaultValue();
	/** 圧縮タイプ */
	private String compressionType = null;
	/** パーティション情報 */
	private Partitioning partitioning = null;
	/** テーブルスペース */
	@SuppressWarnings("unused")
	private final TableSpace tableSpace = null;
	/** インデックステーブルスペース */
	@SuppressWarnings("unused")
	private final TableSpace indexTableSpace = null;
	/** LOBテーブルスペース */
	@SuppressWarnings("unused")
	private final TableSpace lobTableSpace = null;
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

	/**
	 * デフォルトコンストラクタ
	 * @throws SQLException 
	 */
	public Table(final String name, final ResultSet rs, final RowValueConverter valueConverter) throws SQLException {
		super(name);
		this.setDialect(DialectResolver.getInstance().getDialect(rs.getStatement().getConnection()));
		this.getDialect().getCatalogReader().getSchemaReader().getTableReader();
		this.readData(rs);
		this.getRows().setRowIteratorHandler(new ResultSetRowIteratorHandler(rs, valueConverter));
	}

	/**
	 * デフォルトコンストラクタ
	 * @throws SQLException 
	 */
	public Table(final String name, final ResultSet rs) throws SQLException {
		this(name, rs, (r,c,v)->v);
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
	public Table(final String name) {
		super(name);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedDdlObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj, final EqualsHandler equalsHandler) {
		if (!(obj instanceof Table)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		final Table val = cast(obj);
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
		if (!equals(SchemaProperties.COMPRESSION_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.UNLOGGED, val, equalsHandler)) {
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
	protected void toStringDetail(final ToStringBuilder builder) {
		builder.add(SchemaProperties.TABLE_SPACE_NAME, this.getTableSpaceName());
		builder.add(SchemaProperties.INDEX_TABLE_SPACE_NAME, this.getIndexTableSpaceName());
		builder.add(SchemaProperties.LOB_TABLE_SPACE_NAME, this.getLobTableSpaceName());
		if (this.isCompression()) {
			builder.add(SchemaProperties.COMPRESSION, this.isCompression());
			builder.add(SchemaProperties.COMPRESSION_TYPE, this.getCompressionType());
		}
		if (this.isUnlogged()) {
			builder.add(SchemaProperties.UNLOGGED, this.isUnlogged());
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
	public Table setTableType(final TableType tableType) {
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
	public Table setReadonly(final Boolean readOnly) {
		this.readonly = readOnly;
		return instance();
	}

	/**
	 * @param tableType
	 *            the tableType to set
	 */
	@Override
	public Table setTableType(final String tableType) {
		this.tableType = TableType.parse(tableType);
		return instance();
	}

	@Override
	public TableDataStoreType getTableDataStoreType() {
		return tableDataStoreType;
	}

	@Override
	public Table setTableDataStoreType(final TableDataStoreType tableDataStoreType) {
		this.tableDataStoreType = tableDataStoreType;
		return instance();
	}

	@Override
	public ColumnCollection getColumns() {
		return columns;
	}

	protected Table setColumns(final ColumnCollection columns) {
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

	protected Table setRows(final RowCollection rows) {
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
	protected Table setInherits(final InheritCollection inherits) {
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
	public void read(final Connection connection, final ResultSet resultSet) {
		readMetaData(connection, resultSet);
		readData(resultSet);
	}

	/**
	 * メタデータの読み込み
	 * 
	 * @param connection
	 * @param resultSet
	 */
	public void readMetaData(final Connection connection, final ResultSet resultSet) {
		final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
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
	public void readData(final ResultSet resultSet) {
		try {
			final ResultSetMetaData metadata=resultSet.getMetaData();
			final Column[] columns=new Column[metadata.getColumnCount()];
			final Dialect dialect=this.getDialect();
			for(int i=1;i<=metadata.getColumnCount();i++){
				String name = metadata.getColumnLabel(i);
				if (name == null) {
					name = metadata.getColumnName(i);
				}
				Column column = getColumns().get(name);
				if (column==null){
					column=new Column();
					final String productDataType=metadata.getColumnTypeName(i);
					final long precision=metadata.getPrecision(i);
					final int scale=metadata.getScale(i);
					if (dialect!=null){
						dialect.setDbType(productDataType, precision, scale, column);
					}
					getColumns().add(column);
				}
				columns[i-1]=column;
			}
			final int size = columns.length;
			while (resultSet.next()) {
				final Row row = this.newRow();
				for (int i = 1; i <= size; i++) {
					final Column column = columns[i-1];
					final Object obj = resultSet.getObject(column.getName());
					row.put(column, obj);
				}
				this.getRows().add(row);
			}
		} catch (final SQLException e) {
			close(resultSet);
			throw new DataException(e);
		}
	}

	/**
	 * 新規行を作成します
	 * 
	 */
	public Row newRow() {
		final Row obj = new Row();
		obj.setParent(this.getRows());
		return obj;
	}

	/**
	 * 新規カラムを作成します
	 * 
	 */
	public Column newColumn() {
		final Column obj = new Column();
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
	public void merge(final Table table) {
		for (final Column column : table.getColumns()) {
			if (!this.getColumns().contains(column.getName())) {
				// カラムが無い場合は追加
				this.getColumns().add(column.clone());
			}
		}
		for (final Row row : table.getRows()) {
			final Row copyRow = this.newRow();
			for (final Column column : table.getColumns()) {
				final Column thisColumn = this.getColumns().get(column.getName());
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
	public Table setPrimaryKey(final Column... primaryKey) {
		setPrimaryKey(null, primaryKey);
		return this;
	}

	/**
	 * プライマリキーを設定します
	 * 
	 * @param primaryKey
	 */
	public Table setPrimaryKey(final String primaryKeyName, final Column... primaryKey) {
		String constraintName = primaryKeyName;
		if (isEmpty(constraintName)) {
			constraintName = "PK_" + this.getName();
		}
		this.getConstraints().addUniqueConstraint(constraintName, true,
				primaryKey);
		return this;
	}

	/**
	 * プライマリキーを設定します
	 * 
	 * @param primaryKeyName PK name
	 */
	public Table setPrimaryKey(final String primaryKeyName, final java.util.function.Function<Column, Order> columnOrder, final Column... primaryKey) {
		String constraintName = primaryKeyName;
		if (isEmpty(constraintName)) {
			constraintName = "PK_" + this.getName();
		}
		final List<ReferenceColumn> refCols=CommonUtils.list();
		for(final Column column:columns) {
			refCols.add(new ReferenceColumn(column, columnOrder.apply(column)));
		}
		this.getConstraints().addUniqueConstraint(constraintName, true,
				refCols.toArray(new ReferenceColumn[0]));
		return this;
	}

	/**
	 * プライマリキーを設定します
	 * 
	 * @param primaryKeyName PK name
	 * @param col1 PK column1
	 * @param order1 PK column1 order
	 */
	public Table setPrimaryKey(final String primaryKeyName, final Column col1, final Order order1
			, final Column col2) {
		String constraintName = primaryKeyName;
		if (isEmpty(constraintName)) {
			constraintName = "PK_" + this.getName();
		}
		this.getConstraints().addUniqueConstraint(constraintName, true,
				new ReferenceColumn(col1, order1));
		return this;
	}

	/**
	 * プライマリキーを設定します
	 * 
	 * @param primaryKeyName PK name
	 * @param col1 PK column1
	 * @param order1 PK column1 order
	 * @param col2 PK column2
	 * @param order2 PK column2 order
	 */
	public Table setPrimaryKey(final String primaryKeyName, final Column col1, final Order order1
			, final Column col2, final Order order2) {
		String constraintName = primaryKeyName;
		if (isEmpty(constraintName)) {
			constraintName = "PK_" + this.getName();
		}
		this.getConstraints().addUniqueConstraint(constraintName, true,
				new ReferenceColumn(col1, order1)
				, new ReferenceColumn(col2, order2));
		return this;
	}

	/**
	 * プライマリキーを設定します
	 * 
	 * @param primaryKeyName PK name
	 * @param col1 PK column1
	 * @param order1 PK column1 order
	 * @param col2 PK column2
	 * @param order2 PK column2 order
	 * @param col3 PK column3
	 * @param order3 PK column3 order
	 */
	public Table setPrimaryKey(final String primaryKeyName, final Column col1, final Order order1
			, final Column col2, final Order order2
			, final Column col3, final Order order3) {
		String constraintName = primaryKeyName;
		if (isEmpty(constraintName)) {
			constraintName = "PK_" + this.getName();
		}
		this.getConstraints().addUniqueConstraint(constraintName, true,
				new ReferenceColumn(col1, order1)
				, new ReferenceColumn(col2, order2)
				, new ReferenceColumn(col3, order3));
		return this;
	}

	/**
	 * プライマリキーを設定します
	 * 
	 * @param primaryKeyName PK name
	 * @param col1 PK column1
	 * @param order1 PK column1 order
	 * @param col2 PK column2
	 * @param order2 PK column2 order
	 * @param col3 PK column3
	 * @param order3 PK column3 order
	 * @param col4 PK column4
	 * @param order4 PK column4 order
	 */
	public Table setPrimaryKey(final String primaryKeyName, final Column col1, final Order order1
			, final Column col2, final Order order2
			, final Column col3, final Order order3
			, final Column col4, final Order order4) {
		String constraintName = primaryKeyName;
		if (isEmpty(constraintName)) {
			constraintName = "PK_" + this.getName();
		}
		this.getConstraints().addUniqueConstraint(constraintName, true,
				new ReferenceColumn(col1, order1)
				, new ReferenceColumn(col2, order2)
				, new ReferenceColumn(col3, order3)
				, new ReferenceColumn(col4, order4));
		return this;
	}

	/**
	 * プライマリキーを設定します
	 * 
	 * @param primaryKeyName PK name
	 * @param col1 PK column1
	 * @param order1 PK column1 order
	 * @param col2 PK column2
	 * @param order2 PK column2 order
	 * @param col3 PK column3
	 * @param order3 PK column3 order
	 * @param col4 PK column4
	 * @param order4 PK column4 order
	 * @param col5 PK column5
	 * @param order5 PK column5 order
	 */
	public Table setPrimaryKey(final String primaryKeyName, final Column col1, final Order order1
			, final Column col2, final Order order2
			, final Column col3, final Order order3
			, final Column col4, final Order order4
			, final Column col5, final Order order5) {
		String constraintName = primaryKeyName;
		if (isEmpty(constraintName)) {
			constraintName = "PK_" + this.getName();
		}
		this.getConstraints().addUniqueConstraint(constraintName, true,
				new ReferenceColumn(col1, order1)
				, new ReferenceColumn(col2, order2)
				, new ReferenceColumn(col3, order3)
				, new ReferenceColumn(col4, order4)
				, new ReferenceColumn(col5, order5));
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

	protected void setTables(final TableCollection tableCollection) {
		this.setParent(tableCollection);
	}

	@Override
	public ConstraintCollection getConstraints() {
		return constraints;
	}

	protected Table setIndexes(final IndexCollection indexes) {
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
	public Table setPartitioning(final Partitioning partitioning) {
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
	public Table setPartitionParent(final PartitionParent partitionParent) {
		if (this.partitionParent != null) {
			final Table parent=this.partitionParent.getParent();
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
				final Table parentPartition=SchemaUtils.getTableOnlyFromParent(partitionParent.getTable().getSchemaName(), partitionParent.getTable().getName(), this);
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
	public Table setCompression(final boolean compression) {
		this.compression = compression;
		return instance();
	}

	/**
	 * @return the unlogged
	 */
	@Override
	public boolean isUnlogged() {
		return unlogged;
	}

	/**
	 * @param unlogged
	 *            the unlogged to set
	 */
	@Override
	public Table setUnlogged(final boolean unlogged) {
		this.unlogged = unlogged;
		return instance();
	}

	/**
	 * @param compressionType
	 *            the compressionType to set
	 */
	@Override
	public Table setCompressionType(final String compressionType) {
		this.compressionType = compressionType;
		return instance();
	}

	/**
	 * @return the compressionType
	 */
	@Override
	public String getCompressionType() {
		return compressionType;
	}

	/**
	 * @param value
	 *            the characterSet to set
	 */
	@Override
	public Table setCharacterSet(final String value) {
		this.characterSet = value;
		return this;
	}

	protected void writeCharacterSet(final StaxWriter stax)
			throws XMLStreamException {
		final String value=SchemaUtils.getParentCharacterSet(this);
		if (!CommonUtils.eqIgnoreCase(value,
				this.getCharacterSet())) {
			stax.writeAttribute(SchemaProperties.CHARACTER_SET.getLabel(), this.getCharacterSet());
		}
	}

	protected void writeCollation(final StaxWriter stax)
			throws XMLStreamException {
		final String value=SchemaUtils.getParentCollation(this);
		if (!CommonUtils.eqIgnoreCase(value,
				this.getCollation())) {
			stax.writeAttribute(SchemaProperties.COLLATION.getLabel(), this.getCollation());
		}
	}

	protected void writeCharacterSemantics(final StaxWriter stax)
			throws XMLStreamException {
		final CharacterSemantics value=SchemaUtils.getParentCharacterSemantics(this);
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
	public Table setCollation(final String value) {
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
	public Table setCharacterSemantics(final CharacterSemantics characterSemantics) {
		this.characterSemantics = characterSemantics;
		return instance();
	}

	@Override
	protected void writeXmlOptionalAttributes(final StaxWriter stax)
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
			stax.writeAttribute(SchemaProperties.COMPRESSION_TYPE.getLabel(), this.getCompressionType());
		}
		if (this.isUnlogged()) {
			stax.writeAttribute(SchemaProperties.UNLOGGED.getLabel(), this.isUnlogged());
		}
		writeCharacterSet(stax);
		writeCollation(stax);
		writeCharacterSemantics(stax);
	}

	@Override
	protected void writeXmlOptionalValues(final StaxWriter stax)
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
	
	protected void writeXmlRows(final StaxWriter stax) throws XMLStreamException{
		if (isTable()) {
			getRows().writeXml(stax);
		}
	}

	/**
	 * RowデータのみをXMLに出力します。
	 * @param stax
	 * @throws XMLStreamException
	 */
	public void writeXmlRowDatas(final StaxWriter stax)
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
	public void writeXmlRowDatas(final OutputStream stream) throws XMLStreamException {
		final StaxWriter stax = new StaxWriter(stream) {
			@Override
			protected boolean isWriteStartDocument() {
				return true;
			}
		};
		writeXmlRowDatas(stax);
	}

	/**
	 * RowデータのみをXMLに出力します。
	 * @param writer
	 * @throws XMLStreamException
	 */
	public void writeXmlRowDatas(final Writer writer) throws XMLStreamException {
		final StaxWriter stax = new StaxWriter(writer) {
			@Override
			protected boolean isWriteStartDocument() {
				return true;
			}
		};
		writeXmlRowDatas(stax);
	}

	/**
	 * RowデータのみをXMLに出力します。
	 * @param file
	 * @throws XMLStreamException
	 */
	public void writeRowData(final File file) throws XMLStreamException, IOException {
		BufferedOutputStream stream = null;
		try {
			stream = new BufferedOutputStream(new FileOutputStream(file));
			final StaxWriter stax = new StaxWriter(stream);
			writeXmlRowDatas(stax);
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
	protected Table setConstraints(final ConstraintCollection constraints) {
		this.constraints = constraints;
		return this;
	}

	@Override
	public Table setCaseSensitive(final boolean caseSensitive) {
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
		Row(){
			@Override
			public boolean isRow() {
				return true;
			}
		}, Column(){
			@Override
			public boolean isColumn() {
				return true;
			}
		}, Hybrid(){
			@Override
			public boolean isRow() {
				return true;
			}
			@Override
			public boolean isColumn() {
				return true;
			}
			@Override
			public boolean isHibrid() {
				return true;
			}
		};
		public static TableDataStoreType parse(final String text) {
			if (text==null){
				return null;
			}
			for (final TableDataStoreType enm : values()) {
				if (enm.toString().equalsIgnoreCase(text)) {
					return enm;
				}
			}
			return null;
		}

		public boolean isRow() {
			return false;
		}
		
		public boolean isColumn() {
			return false;
		}

		public boolean isHibrid() {
			return false;
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
		public String getDisplayName(final Locale locale) {
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

		TableType(final String text, final String patternText) {
			this.text = text;
			pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
		}

		public static TableType parse(final String text) {
			for (final TableType enm : values()) {
				final Matcher matcher = enm.pattern.matcher(text);
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
		public String getDisplayName(final Locale locale) {
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
		for (final UniqueConstraint uniqueConstraint : getConstraints()
				.getUniqueConstraints()) {
			final List<Column> columns = CommonUtils.list();
			for (final ReferenceColumn rColumn : uniqueConstraint.getColumns()) {
				final Column column = getColumns().get(rColumn.getName());
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
	public void setRowIteratorHandler(final RowIteratorHandler rowIteratorHandler) {
		this.getRows().setRowIteratorHandler(rowIteratorHandler);
	}
	
	public RowCollection getRows(final RowIteratorHandler rowIteratorHandler) {
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
	public void setAddDbObjectFilter(final AddDbObjectPredicate addDbObjectFilter) {
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
		final List<ForeignKeyConstraint> removeTargets=CommonUtils.list();
		final Set<String> names=CommonUtils.set();
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
		for(final ForeignKeyConstraint fk:removeTargets){
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
	public List<ForeignKeyConstraint> getChildRelations(final Predicate<ForeignKeyConstraint> p) {
		final List<ForeignKeyConstraint> result = list(childRelations.size());
		final int size = childRelations.size();
		for (int i = 0; i < size; i++) {
			final ForeignKeyConstraint c = childRelations.get(i);
			final ForeignKeyConstraint cc = cast(c);
			if (p.test(cc)){
				result.add(cc);
			}
		}
		return result;
	}
	
	/**
	 * @return the childRelations
	 */
	protected Table addChildRelation(final ForeignKeyConstraint fk) {
		if (!childRelations.contains(fk)){
			childRelations.add(fk);
		}
		return instance();
	}

	
	/**
	 * @param childRelations the childRelations to set
	 */
	protected void setChildRelations(final List<ForeignKeyConstraint> childRelations) {
		this.childRelations = childRelations;
	}

	public static enum TableOrder{
		CREATE(new TableCreateOrderComparator()),
		DROP(new TableDropOrderComparator()),;
		
		private TableOrder(final Comparator<Table> comparator){
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
