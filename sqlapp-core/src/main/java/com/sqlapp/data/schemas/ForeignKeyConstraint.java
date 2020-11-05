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

import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.eqIgnoreCase;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.DeleteRuleProperty;
import com.sqlapp.data.schemas.properties.MatchOptionProperty;
import com.sqlapp.data.schemas.properties.RelatedTableNameProperty;
import com.sqlapp.data.schemas.properties.RelatedTableSchemaNameProperty;
import com.sqlapp.data.schemas.properties.UpdateRuleProperty;
import com.sqlapp.data.schemas.properties.object.RelatedColumnsProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.SeparatedStringBuilder;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * 外部キー制約クラス
 * 
 * @author satoh
 * 
 */
public final class ForeignKeyConstraint extends
		AbstractColumnConstraint<ForeignKeyConstraint> implements UpdateRuleProperty<ForeignKeyConstraint>, DeleteRuleProperty<ForeignKeyConstraint>
		, RelatedTableSchemaNameProperty<ForeignKeyConstraint>
		, RelatedTableNameProperty<ForeignKeyConstraint>
		, RelatedColumnsProperty<ForeignKeyConstraint>
		, MatchOptionProperty<ForeignKeyConstraint>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2991007538747094902L;
	
	private String relatedTableSchemaName=null;
	
	private String relatedTableName=null;
	/** 親テーブルのカラム */
	private ReferenceColumnCollection relatedColumns=new ReferenceColumnCollection(this);
	/** 親テーブルのカラムの変数名 */
	protected static final String RELATED_TABLE = "relatedTable";
	/** 更新時のルール */
	private CascadeRule updateRule = null;
	/** 削除時のルール */
	private CascadeRule deleteRule = null;
	/** マッチオプション */
	private MatchOption matchOption = null;


	/**
	 * デフォルトコンストラクタ
	 */
	public ForeignKeyConstraint() {
	}

	/**
	 * デフォルトコンストラクタ
	 */
	public ForeignKeyConstraint(String name) {
		super(name);
	}
	
	@Override
	protected Supplier<Constraint> newInstance(){
		return ()->new ForeignKeyConstraint();
	}

	/**
	 * コンストラクタ
	 * 
	 * @param constraintName
	 *            制約名
	 * @param columns
	 *            子テーブルのカラム
	 * @param relatedColumns
	 *            親テーブルのカラム
	 */
	public ForeignKeyConstraint(String constraintName, Column[] columns,
			Column[] relatedColumns) {
		super(constraintName, columns);
		setRelatedColumns(relatedColumns);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param constraintName
	 *            制約名
	 * @param column
	 *            子テーブルのカラム
	 * @param relatedColumn
	 *            親テーブルのカラム
	 */
	public ForeignKeyConstraint(String constraintName, Column column,
			Column relatedColumn) {
		super(constraintName, column);
		setRelatedColumns(relatedColumn);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param constraintName
	 *            制約名
	 * @param relatedColumns
	 *            親テーブルのカラム
	 * @param columns
	 *            子テーブルのカラム
	 */
	public ForeignKeyConstraint(String constraintName,
			List<Column> relatedColumns, List<Column> columns) {
		super(constraintName, columns);
		setRelatedColumns(relatedColumns.toArray(new Column[0]));
	}

	/**
	 * 親テーブルを取得します
	 * 
	 */
	public Table getRelatedTable() {
		if (relatedColumns == null) {
			return null;
		}
		Table table=SchemaUtils.getTableOnlyFromParent(this.getRelatedTableSchemaName(), this.getRelatedTableName(), this);
		if (table==null){
			table= relatedColumns.getTable();
		}
		return table;
	}

	/**
	 * リレーションのバリデーションを行います
	 */
	@Override
	protected void validate() {
		super.validate();
		if (!isEmpty(this.getColumns())) {
			setRelation(this.getTable(), this.getColumns());
		}
		if (!isEmpty(relatedColumns)) {
			Table relatedTable = this.getRelatedTable();
			if (relatedTable!=null){
				if (relatedTable.getChildRelations()==null){
					relatedTable.setChildRelations(CommonUtils.list());
				}
				relatedTable.addChildRelation(this);
			}
		}
	}
	
	protected void setRelation(Table table, Column... columns) {
		if (table == null) {
			return;
		}
		for (int i = 0; i < columns.length; i++) {
			Column getColumn = table.getColumns().get(columns[i].getName());
			if (getColumn == null) {
				continue;
			}
			columns[i] = getColumn;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.Constraint#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof ForeignKeyConstraint)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		ForeignKeyConstraint val = (ForeignKeyConstraint) obj;
		if (!equals(
				SchemaProperties.RELATED_TABLE_SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(
				SchemaProperties.RELATED_TABLE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(
				SchemaObjectProperties.RELATED_COLUMNS
				, val
				, equalsHandler, EqualsUtils.getEqualsSupplier(eqColumnName(this.getRelatedColumns(), val.getRelatedColumns())))) {
			return false;
		}
		if (!equals(SchemaProperties.UPDATE_RULE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DELETE_RULE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.MATCH_OPTION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.VIRTUAL, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		if (getColumns() != null && getColumns().length > 0) {
			builder.add(SchemaProperties.TABLE_NAME, this.getTableName());
		}
		super.toStringDetail(builder);
		if (getRelatedColumns() != null && !getRelatedColumns().isEmpty()) {
			String schemaName = this.getRelatedTableSchemaName();
			if (!eqIgnoreCase(schemaName, getSchemaName(getColumns()))) {
				builder.add(SchemaProperties.RELATED_TABLE_SCHEMA_NAME, this.getRelatedTableSchemaName());
			}
			builder.add(SchemaProperties.RELATED_TABLE_NAME, this.getRelatedTableName());
			builder.add(SchemaObjectProperties.RELATED_COLUMNS, this.getRelatedColumnsString());
		}
		builder.add(SchemaProperties.UPDATE_RULE, this.getUpdateRule());
		builder.add(SchemaProperties.DELETE_RULE, this.getDeleteRule());
		builder.add(SchemaProperties.MATCH_OPTION, this.getMatchOption());
		builder.add(SchemaProperties.VIRTUAL.getLabel(), this.isVirtual());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.Constraint#writeXmlOptionalAttributes(com.sqlapp
	 * .util.StaxWriter)
	 */
	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.UPDATE_RULE.getLabel(), this.getUpdateRule());
		stax.writeAttribute(SchemaProperties.DELETE_RULE.getLabel(), this.getDeleteRule());
		if (this.matchOption != MatchOption.Simple) {
			stax.writeAttribute(SchemaProperties.MATCH_OPTION.getLabel(), this.getMatchOption());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractColumnConstraint#writeXmlOptionalValues
	 * (com.sqlapp.util.StaxWriter)
	 */
	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		writeTable(stax);
		writeRelatedTable(stax);
	}

	private void writeTable(StaxWriter stax) throws XMLStreamException {
		Table table = this.getTable();
		if (this.getParent() != null && this.getParent().getTable() != table) {
		} else {
			return;
		}
		stax.newLine();
		stax.indent();
		stax.writeStartElement("table");
		if (this.getParent() != null && this.getParent().getTable() != table) {
			stax.writeAttribute(SchemaProperties.NAME.getLabel(), table.getName());
			stax.writeAttribute(SchemaProperties.SCHEMA_NAME.getLabel(), table.getSchemaName());
		}
		stax.addIndentLevel(1);
		// writeColumns("Columns", stax, this.getColumns());
		stax.addIndentLevel(-1);
		stax.newLine();
		stax.indent();
		stax.writeEndElement();
	}

	private void writeRelatedTable(StaxWriter stax) throws XMLStreamException {
		stax.newLine();
		stax.indent();
		stax.writeStartElement(RELATED_TABLE);
		stax.writeAttribute(SchemaProperties.NAME.getLabel(), this.getRelatedTableName());
		if (!CommonUtils.eq(this.getTable().getSchemaName(),
				this.getRelatedTableSchemaName())) {
			stax.writeAttribute(SchemaProperties.SCHEMA_NAME.getLabel(), this.getRelatedTableSchemaName());
		}
		stax.addIndentLevel(1);
		writeColumns(SchemaObjectProperties.COLUMNS.getLabel(), stax, this.getRelatedColumns());
		stax.addIndentLevel(-1);
		stax.newLine();
		stax.indent();
		stax.writeEndElement();
	}

	/**
	 * マッチオプション
	 */
	public enum MatchOption implements EnumProperties {
		/**
		 * FULLは全ての外部キー列がNULLとなる場合を除き、複数列外部キーのある列がNULLとなることを許可しない
		 */
		Full("FULL", "f.*"),
		/**
		 * 
		 */
		Partial("PARTIAL", "p.*"),
		/**
		 * 外部キーの他の部分がNULLでない限り、外部キーの一部をNULLとなることを許可する
		 */
		Simple("SIMPLE", "(s.*|default)");

		private final String text;

		private final Pattern pattern;

		private MatchOption(String text, String patternText) {
			this.text = text;
			pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
		}

		/**
		 * 文字列からenumオブジェクトを取得します
		 * 
		 * @param text
		 */
		public static MatchOption parse(final String text) {
			if (text==null){
				return null;
			}
			for (MatchOption e : MatchOption.values()) {
				Matcher matcher = e.pattern.matcher(text);
				if (matcher.matches()) {
					return e;
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

	@Override
	public CascadeRule getUpdateRule() {
		return updateRule;
	}

	@Override
	public ForeignKeyConstraint setUpdateRule(CascadeRule updateRule) {
		this.updateRule = updateRule;
		return this;
	}

	@Override
	public CascadeRule getDeleteRule() {
		return deleteRule;
	}

	@Override
	public ForeignKeyConstraint setDeleteRule(CascadeRule deleteRule) {
		this.deleteRule = deleteRule;
		return this;
	}

	@Override
	public ReferenceColumnCollection getRelatedColumns() {
		return relatedColumns;
	}

	@Override
	public ForeignKeyConstraint setRelatedColumns(Column... relatedColumns) {
		if (this.relatedColumns==null){
			this.relatedColumns=new ReferenceColumnCollection(this);
		} else{
			this.relatedColumns.clear();
		}
		for(Column column:relatedColumns){
			if (column.getTableName()!=null){
				this.setRelatedTableName(column.getTableName());
			}
			this.relatedColumns.add(column);
		}
		return this;
	}

	public ForeignKeyConstraint addRelatedColumn(Column relatedColumn) {
		if (this.relatedColumns==null){
			this.relatedColumns=new ReferenceColumnCollection(this);
		}
		if (relatedColumn.getTableName()!=null){
			this.setRelatedTableName(relatedColumn.getTableName());
		}
		relatedColumns.add(relatedColumn);
		return this;
	}

	public ForeignKeyConstraint addRelatedColumns(List<Column> relatedColumns) {
		for (Column column : relatedColumns) {
			addRelatedColumn(column);
		}
		return this;
	}

	@Override
	public MatchOption getMatchOption() {
		return matchOption;
	}

	@Override
	public ForeignKeyConstraint setMatchOption(MatchOption matchOption) {
		this.matchOption = matchOption;
		return this;
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<Constraint> getDbObjectXmlReaderHandler() {
		return new ForeignKeyConstraintXmlReaderHandler();
	}

	@Override
	public boolean like(Object obj) {
		if (!(obj instanceof ForeignKeyConstraint)){
			return false;
		}
		ForeignKeyConstraint con=(ForeignKeyConstraint)obj;
		if (!CommonUtils.eq(this.getName(), con.getName())){
			if (this.getParent()!=null&&con.getParent()!=null){
				if (this.getParent().contains(con.getName())||con.getParent().contains(this.getName())){
					return false;
				}
			}
		}
		if (!eq(this.getColumnsString(), con.getColumnsString())) {
			return false;
		}
		if (!eq(this.getRelatedColumnsString(), con.getRelatedColumnsString())) {
			return false;
		}
		return true;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#toStringSimple()
	 */
	@Override
	public String toStringSimple() {
		ToStringBuilder builder = new ToStringBuilder(this.getSimpleName());
		if (this.getParent()==null){
			builder.add(SchemaProperties.CATALOG_NAME, this.getCatalogName());
			builder.add(SchemaProperties.SCHEMA_NAME, this.getSchemaName());
		}
		builder.add(SchemaProperties.NAME, this.getName());
		builder.add(SchemaObjectProperties.COLUMNS, this.getColumnsString());
		if (this.getRelatedTable()!=null){
			builder.add(SchemaProperties.RELATED_TABLE_NAME, this.getRelatedTableName());
		}
		builder.add(SchemaObjectProperties.RELATED_COLUMNS, this.getRelatedColumnsString());
		return builder.toString();
	}

	private String getColumnsString() {
		SeparatedStringBuilder sep = new SeparatedStringBuilder(", ");
		sep.setStart("(").setEnd(")");
		for (Column column : getColumns()) {
			sep.add(column.getName());
		}
		return sep.toString();
	}

	private String getRelatedColumnsString() {
		SeparatedStringBuilder sep = new SeparatedStringBuilder(", ");
		sep.setStart("(").setEnd(")");
		for (ReferenceColumn column : getRelatedColumns()) {
			sep.add(column.getName());
		}
		return sep.toString();
	}

	@Override
	protected ForeignKeyConstraint instance() {
		return this;
	}
	
	@Override
	public ForeignKeyConstraint setEnable(boolean bool){
		super.setEnable(bool);
		return instance();
	}
	
	@Override
	public ForeignKeyConstraint setDeferrability(Deferrability deferrability) {
		super.setDeferrability(deferrability);
		return instance();
	}
	
	@Override
	public ForeignKeyConstraint setDeferrability(String deferrability) {
		super.setDeferrability(deferrability);
		return instance();
	}

	@Override
	public String getRelatedTableName() {
		return this.relatedTableName;
	}

	@Override
	public ForeignKeyConstraint setRelatedTableName(String value) {
		this.relatedTableName=value;
		return instance();
	}

	@Override
	public String getRelatedTableSchemaName() {
		if (!CommonUtils.isEmpty(this.relatedColumns)){
			String name= this.relatedColumns.getSchemaName();
			if (name!=null){
				return name;
			}
		}
		if(this.relatedTableSchemaName==null){
			return this.getSchemaName();
		}
		return this.relatedTableSchemaName;
	}

	@Override
	public ForeignKeyConstraint setRelatedTableSchemaName(String value) {
		this.relatedTableSchemaName=value;
		return instance();
	}

}
