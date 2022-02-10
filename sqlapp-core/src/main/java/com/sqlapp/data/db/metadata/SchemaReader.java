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

package com.sqlapp.data.db.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaCollection;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Setting;
import com.sqlapp.data.schemas.SettingCollection;

/**
 * スキーマ読み込み
 * 
 * @author tatsuo satoh
 * 
 */
public abstract class SchemaReader extends
		AbstractCatalogNamedObjectMetadataReader<Schema> {

	private String schemaName;

	public String getSchemaName() {
		return this.schemaName;
	}

	public void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}

	protected SchemaReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaCollection getSchemaObjectList(Catalog catalog) {
		return catalog.getSchemas();
	}
	
	@Override
	protected SchemaObjectProperties getSchemaObjectProperties(){
		return SchemaObjectProperties.SCHEMAS;
	}


	/**
	 * スキーマ内のオブジェクトを詳細情報を含めて読み込みます。
	 * 
	 * @param connection
	 * @param schema
	 */
	public void loadFull(Connection connection, Schema schema)
			throws SQLException {
		setCommonBefore(connection);
		schema.setDialect(this.getDialect());
		CatalogReader.setProductInfo(connection, this.getDialect(), schema);
		setSchemaBefore(connection, schema);
		loadFull(connection, getDomainReader(), schema);
		loadFull(connection, getTypeReader(), schema);
		loadFull(connection, getTypeBodyReader(), schema);
		loadFull(connection, getTableReader(), schema);
		loadFull(connection, getViewReader(), schema);
		loadFull(connection, getMviewReader(), schema);
		loadFull(connection, getMviewLogReader(), schema);
		loadFull(connection, getMaskReader(), schema);
		loadFull(connection, getSequenceReader(), schema);
		loadFull(connection, getTriggerReader(), schema);
		loadFull(connection, getDbLinkReader(), schema);
		loadFull(connection, getRuleReader(), schema);
		loadFull(connection, getTableLinkReader(), schema);
		loadFull(connection, getFunctionReader(), schema);
		loadFull(connection, getProcedureReader(), schema);
		loadFull(connection, getPackageReader(), schema);
		loadFull(connection, getPackageBodyReader(), schema);
		loadFull(connection, getConstantReader(), schema);
		loadFull(connection, getXmlSchemaReader(), schema);
		loadFull(connection, getOperatorReader(), schema);
		loadFull(connection, getOperatorClassReader(), schema);
		loadFull(connection, getExternalTableReader(), schema);
		loadFull(connection, getEventReader(), schema);
		loadFull(connection, getDimensionReader(), schema);
		loadFull(connection, getSynonymReader(), schema);
		setSchemaAfter(connection, schema);
		SchemaUtils.validate(schema);
	}
	
	/**
	 * スキーマ内のオブジェクトを読み込みます。
	 * 
	 * @param connection
	 * @param schema
	 */
	public void load(Connection connection, Schema schema)
			throws SQLException {
		setCommonBefore(connection);
		schema.setDialect(this.getDialect());
		CatalogReader.setProductInfo(connection, this.getDialect(), schema);
		setSchemaBefore(connection, schema);
		load(connection, getDomainReader(), schema);
		load(connection, getTypeReader(), schema);
		load(connection, getTypeBodyReader(), schema);
		load(connection, getTableReader(), schema);
		load(connection, getViewReader(), schema);
		load(connection, getMviewReader(), schema);
		load(connection, getMviewLogReader(), schema);
		load(connection, getMaskReader(), schema);
		load(connection, getSequenceReader(), schema);
		load(connection, getTriggerReader(), schema);
		load(connection, getDbLinkReader(), schema);
		load(connection, getRuleReader(), schema);
		load(connection, getTableLinkReader(), schema);
		load(connection, getFunctionReader(), schema);
		load(connection, getProcedureReader(), schema);
		load(connection, getPackageReader(), schema);
		load(connection, getPackageBodyReader(), schema);
		load(connection, getConstantReader(), schema);
		load(connection, getXmlSchemaReader(), schema);
		load(connection, getOperatorReader(), schema);
		load(connection, getOperatorClassReader(), schema);
		load(connection, getExternalTableReader(), schema);
		load(connection, getEventReader(), schema);
		load(connection, getDimensionReader(), schema);
		load(connection, getSynonymReader(), schema);
		setSchemaAfter(connection, schema);
	}
	
	/**
	 * メタデータの詳細情報を設定するためのメソッドです。子クラスでのオーバーライドを想定しています。
	 * 
	 * @param connection
	 * @param obj
	 */
	@Override
	protected void setMetadataDetail(Connection connection, Schema schema)
			throws SQLException {
		loadFull(connection, schema);
	}

	/**
	 * SettingCollection
	 */
	private SettingCollection settings = null;

	/**
	 * @return the settings
	 */
	protected SettingCollection getSettings() {
		return settings;
	}

	/**
	 * @param settings
	 *            the settings to set
	 */
	protected void setSettings(SettingCollection settings) {
		this.settings = settings;
	}

	protected void setCommonBefore(Connection connection) {
		if (this.settings == null) {
			CatalogReader catalogReader = this.getParent();
			SettingReader settingReader = catalogReader.getSettingReader();
			if (settingReader != null) {
				List<Setting> list = settingReader.getAllFull(connection);
				this.settings = SchemaUtils.createInstance("settings");
				this.settings.addAll(list);
			}
		}
	}

	protected void setSchemaBefore(Connection connection, Schema schema) {

	}

	protected void setSchemaAfter(Connection connection, Schema schema) {

	}

	private void loadFull(Connection connection,
			AbstractSchemaObjectReader<?> reader, Schema schema) {
		if (reader != null) {
			reader.setCatalogName(schema.getCatalogName());
			reader.setSchemaName(schema.getName());
			reader.loadFull(connection, schema);
		}
	}

	private void load(Connection connection,
			AbstractSchemaObjectReader<?> reader, Schema schema) {
		if (reader != null) {
			reader.setCatalogName(schema.getCatalogName());
			reader.setSchemaName(schema.getName());
			reader.load(connection, schema);
		}
	}

	
	/**
	 * カレントスキーマを取得します
	 * 
	 * @param connection
	 * @return　カレントスキーマ
	 */
	public String getCurrentSchemaName(Connection connection){
		try {
			return connection.getSchema();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * テーブル読み込みクラスを取得します
	 * 
	 */
	public TableReader getTableReader() {
		TableReader reader = newTableReader();
		setReaderParameter(reader);
		return reader;
	}

	protected void setReaderParameter(AbstractSchemaObjectReader<?> reader) {
		if (reader != null) {
			reader.setCatalogName(this.getCatalogName());
			reader.setSchemaName(this.getSchemaName());
			reader.setObjectName(this.getObjectName());
			initializeChild(reader);
		}
	}

	protected abstract TableReader newTableReader();

	/**
	 * ビュー読み込みクラスを取得します
	 * 
	 */
	public ViewReader getViewReader() {
		ViewReader reader = newViewReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract ViewReader newViewReader();

	/**
	 * マテリアライズドビュー読み込みクラスを取得します
	 * 
	 */
	public MviewReader getMviewReader() {
		MviewReader reader = newMviewReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract MviewReader newMviewReader();

	/**
	 * マテリアライズドビューログ読み込みクラスを取得します
	 * 
	 */
	public MviewLogReader getMviewLogReader() {
		MviewLogReader reader = newMviewLogReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract MviewLogReader newMviewLogReader();

	/**
	 * シーケンス読み込みクラスを取得します
	 * 
	 */
	public SequenceReader getSequenceReader() {
		SequenceReader reader = newSequenceReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract SequenceReader newSequenceReader();

	/**
	 * DBリンク読み込みクラスを取得します
	 * 
	 */
	public DbLinkReader getDbLinkReader() {
		DbLinkReader reader = newDbLinkReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract DbLinkReader newDbLinkReader();

	/**
	 * ドメイン読み込みクラスを取得します
	 * 
	 */
	public DomainReader getDomainReader() {
		DomainReader reader = newDomainReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract DomainReader newDomainReader();

	/**
	 * Type読み込みクラスを取得します
	 * 
	 */
	public TypeReader getTypeReader() {
		TypeReader reader = newTypeReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract TypeReader newTypeReader();

	/**
	 * TypeBody読み込みクラスを取得します
	 * 
	 */
	public TypeBodyReader getTypeBodyReader() {
		TypeBodyReader reader = newTypeBodyReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract TypeBodyReader newTypeBodyReader();

	/**
	 * シノニム読み込みクラスを取得します
	 * 
	 */
	public SynonymReader getSynonymReader() {
		SynonymReader reader = newSynonymReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract SynonymReader newSynonymReader();

	/**
	 * テーブルリンク読み込みクラスを取得します
	 * 
	 */
	public TableLinkReader getTableLinkReader() {
		TableLinkReader reader = newTableLinkReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract TableLinkReader newTableLinkReader();

	/**
	 * ルール読み込みクラスを取得します
	 * 
	 */
	public RuleReader getRuleReader() {
		RuleReader reader = newRuleReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract RuleReader newRuleReader();

	/**
	 * ファンクション読み込みクラスを取得します
	 * 
	 */
	public FunctionReader getFunctionReader() {
		FunctionReader reader = newFunctionReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract FunctionReader newFunctionReader();

	/**
	 * プロシージャ読み込みクラスを取得します
	 * 
	 */
	public ProcedureReader getProcedureReader() {
		ProcedureReader reader = newProcedureReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract ProcedureReader newProcedureReader();

	/**
	 * パッケージ読み込みクラスを取得します
	 * 
	 */
	public PackageReader getPackageReader() {
		PackageReader reader = newPackageReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract PackageReader newPackageReader();

	/**
	 * パッケージBODY読み込みクラスを取得します
	 * 
	 */
	public PackageBodyReader getPackageBodyReader() {
		PackageBodyReader reader = newPackageBodyReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract PackageBodyReader newPackageBodyReader();

	/**
	 * 定数読み込みクラスを取得します
	 * 
	 */
	public ConstantReader getConstantReader() {
		ConstantReader reader = newConstantReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract ConstantReader newConstantReader();

	/**
	 * トリガー読み込みクラスを取得します
	 * 
	 */
	public TriggerReader getTriggerReader() {
		TriggerReader reader = newTriggerReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract TriggerReader newTriggerReader();

	/**
	 * XMLスキーマ読み込みクラスを取得します
	 * 
	 */
	public XmlSchemaReader getXmlSchemaReader() {
		XmlSchemaReader reader = newXmlSchemaReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract XmlSchemaReader newXmlSchemaReader();

	/**
	 * Operator読み込みクラスを取得します
	 * 
	 */
	public OperatorReader getOperatorReader() {
		OperatorReader reader = newOperatorReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract OperatorReader newOperatorReader();

	/**
	 * OperatorClass読み込みクラスを取得します
	 * 
	 */
	public OperatorClassReader getOperatorClassReader() {
		OperatorClassReader reader = newOperatorClassReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract OperatorClassReader newOperatorClassReader();

	/**
	 * ExternalTable読み込みクラスを取得します
	 * 
	 */
	public ExternalTableReader getExternalTableReader() {
		ExternalTableReader reader = newExternalTableReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract ExternalTableReader newExternalTableReader();

	/**
	 * Dimension読み込みクラスを取得します
	 * 
	 */
	public DimensionReader getDimensionReader() {
		DimensionReader reader = newDimensionReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract DimensionReader newDimensionReader();

	/**
	 * イベント読み込みクラスを取得します
	 * 
	 */
	public EventReader getEventReader() {
		EventReader reader = newEventReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract EventReader newEventReader();

	/**
	 * Mask Reader
	 * 
	 */
	public MaskReader getMaskReader() {
		MaskReader reader = newMaskReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract MaskReader newMaskReader();
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.metadata.AbstractCatalogMetadataReader#
	 * defaultParametersContext(java.sql.Connection)
	 */
	@Override
	protected ParametersContext defaultParametersContext(Connection connection) {
		ParametersContext context = newParametersContext(connection,
				this.getCatalogName());
		context.put(getNameLabel(),
				nativeCaseString(connection, this.getSchemaName()));
		return context;
	}

	@Override
	protected String getNameLabel() {
		return SchemaProperties.SCHEMA_NAME.getLabel();
	}

	/**
	 * 指定した名称のReaderを取得します
	 * 
	 * @param name
	 */
	@SuppressWarnings("unchecked")
	protected <T extends MetadataReader<?, ?>> T getMetadataReader(String name) {
		if ("schema".equalsIgnoreCase(name) || "schemas".equalsIgnoreCase(name)) {
			return (T) this;
		}
		return MetadataReaderUtils.getMetadataReader(this, name);
	}

}
