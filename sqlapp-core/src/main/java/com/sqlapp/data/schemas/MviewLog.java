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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.sql.Timestamp;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.CommitScnBasedProperty;
import com.sqlapp.data.schemas.properties.IncludeNewValuesProperty;
import com.sqlapp.data.schemas.properties.MasterTableNameProperty;
import com.sqlapp.data.schemas.properties.MasterTableSchemaNameProperty;
import com.sqlapp.data.schemas.properties.PurgeAsynchronousProperty;
import com.sqlapp.data.schemas.properties.PurgeDeferredProperty;
import com.sqlapp.data.schemas.properties.PurgeIntervalProperty;
import com.sqlapp.data.schemas.properties.PurgeStartProperty;
import com.sqlapp.data.schemas.properties.SaveFilterColumnsProperty;
import com.sqlapp.data.schemas.properties.SaveObjectIdProperty;
import com.sqlapp.data.schemas.properties.SavePrimaryKeyProperty;
import com.sqlapp.data.schemas.properties.SaveRowIdsProperty;
import com.sqlapp.data.schemas.properties.SaveSequenceProperty;
import com.sqlapp.data.schemas.properties.complex.TableSpaceProperty;
import com.sqlapp.data.schemas.properties.object.ReferenceColumnsProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * マテリアライズドビューログ
 * @author satoh
 *
 */
/**
 * @author satoh
 * 
 */
public final class MviewLog extends AbstractSchemaObject<MviewLog> implements
		HasParent<MviewLogCollection>
	, MasterTableSchemaNameProperty<MviewLog>
	, MasterTableNameProperty<MviewLog>
	, ReferenceColumnsProperty<MviewLog>
	, SaveRowIdsProperty<MviewLog>
	, SavePrimaryKeyProperty<MviewLog>
	, SaveObjectIdProperty<MviewLog>
	, SaveFilterColumnsProperty<MviewLog>
	, SaveSequenceProperty<MviewLog>
	, IncludeNewValuesProperty<MviewLog>
	, TableSpaceProperty<MviewLog>
	, PurgeAsynchronousProperty<MviewLog>
	, PurgeDeferredProperty<MviewLog>
	, PurgeStartProperty<MviewLog>
	, PurgeIntervalProperty<MviewLog>
	, CommitScnBasedProperty<MviewLog>
{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2636864307288766192L;
	/** カラム名の一覧 */
	private ReferenceColumnCollection columns = new ReferenceColumnCollection(
			this);
	/** 元になるテーブル */
	private Table masterTable = null;
	/** ROWID情報を記録するかどうか? */
	private boolean saveRowIds = (Boolean)SchemaProperties.SAVE_ROW_IDS.getDefaultValue();
	/** 主キー情報を記録するかどうか? */
	private boolean savePrimaryKey = (Boolean)SchemaProperties.SAVE_PRIMARY_KEY.getDefaultValue();
	/** オブジェクト表にオブジェクト識別子情報を記録するかどうか? */
	private boolean saveObjectId = (Boolean)SchemaProperties.SAVE_OBJECT_ID.getDefaultValue();
	/** フィルタ列情報を記録するかどうか? */
	private boolean saveFilterColumns = (Boolean)SchemaProperties.SAVE_FILTER_COLUMNS.getDefaultValue();
	/** 追加の順序付け情報を提供する順序値を記録するかどうか? */
	private boolean saveSequence = (Boolean)SchemaProperties.SAVE_SEQUENCE.getDefaultValue();
	/** 新旧両方の値を記録する（true）か?古い値を記録して新しい値は記録しない（false）か? */
	private boolean includeNewValues = (Boolean)SchemaProperties.INCLUDE_NEW_VALUES.getDefaultValue();
	/** テーブルスペース */
	private TableSpace tableSpace = null;
	/**
	 * 11gR2項目 マテリアライズド・ビュー・ログが非同期的に消去されるかどうか?
	 */
	private boolean purgeAsynchronous = (Boolean)SchemaProperties.PURGE_ASYNCHRONOUS.getDefaultValue();
	/**
	 * 11gR2項目 マテリアライズド・ビュー・ログが遅延方式で消去されるかどうか?
	 */
	private boolean purgeDeferred = (Boolean)SchemaProperties.PURGE_DEFERRED.getDefaultValue();
	/**
	 * 11gR2項目 遅延消去の場合、消去が開始された日付
	 */
	private Timestamp purgeStart = null;
	/**
	 * 11gR2項目 遅延消去の場合、消去の間隔
	 */
	private String purgeInterval = null;
	/**
	 * 11gR2項目 マテリアライズド・ビュー・ログがコミットSCNベースかどうか?
	 */
	private boolean commitScnBased = (Boolean)SchemaProperties.COMMIT_SCN_BASED.getDefaultValue();

	/**
	 * コンストラクタ
	 */
	protected MviewLog() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public MviewLog(String name) {
		super(name);
	}

	@Override
	protected Supplier<MviewLog> newInstance(){
		return ()->new MviewLog();
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.MASTER_TABLE_NAME, this.getMasterTableName());
		builder.add(SchemaProperties.SAVE_ROW_IDS, this.isSaveRowIds());
		builder.add(SchemaProperties.PRIMARY_KEY, this.isSavePrimaryKey());
		builder.add(SchemaProperties.SAVE_OBJECT_ID, this.isSaveObjectId());
		builder.add(SchemaProperties.SAVE_FILTER_COLUMNS, this.isSaveFilterColumns());
		builder.add(SchemaProperties.SAVE_SEQUENCE, this.isSaveSequence());
		builder.add(SchemaProperties.INCLUDE_NEW_VALUES, this.isIncludeNewValues());
		builder.add(SchemaProperties.TABLE_SPACE_NAME, this.getTableSpaceName());
		builder.add(SchemaProperties.PURGE_ASYNCHRONOUS, this.isPurgeAsynchronous());
		builder.add(SchemaProperties.PURGE_DEFERRED, this.isPurgeDeferred());
		builder.add(SchemaProperties.PURGE_START, this.getPurgeStart());
		builder.add(SchemaProperties.PURGE_INTERVAL, this.getPurgeInterval());
		builder.add(SchemaProperties.COMMIT_SCN_BASED, this.isCommitScnBased());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof MviewLog)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		MviewLog val = (MviewLog) obj;
		if (!equals(SchemaObjectProperties.REFERENCE_COLUMNS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.MASTER_TABLE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.SAVE_ROW_IDS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PRIMARY_KEY, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.SAVE_OBJECT_ID, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.SAVE_FILTER_COLUMNS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.SAVE_SEQUENCE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.INCLUDE_NEW_VALUES, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.TABLE_SPACE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PURGE_ASYNCHRONOUS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PURGE_DEFERRED, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PURGE_START, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PURGE_INTERVAL, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.COMMIT_SCN_BASED, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		if (!CommonUtils.eq(this.getMasterTableSchemaName(), this.getSchemaName())){
			stax.writeAttribute(SchemaProperties.MASTER_TABLE_SCHEMA_NAME.getLabel(), this.getMasterTableSchemaName());
		}
		stax.writeAttribute(SchemaProperties.MASTER_TABLE_NAME.getLabel(), this.getMasterTableName());
		stax.writeAttribute(SchemaProperties.SAVE_ROW_IDS.getLabel(), this.isSaveRowIds());
		stax.writeAttribute(SchemaProperties.SAVE_PRIMARY_KEY.getLabel(), this.isSavePrimaryKey());
		stax.writeAttribute(SchemaProperties.SAVE_OBJECT_ID.getLabel(), this.isSaveObjectId());
		stax.writeAttribute(SchemaProperties.SAVE_FILTER_COLUMNS.getLabel(), this.isSaveFilterColumns());
		stax.writeAttribute(SchemaProperties.SAVE_SEQUENCE.getLabel(), this.isSaveSequence());
		stax.writeAttribute(SchemaProperties.INCLUDE_NEW_VALUES.getLabel(), this.isIncludeNewValues());
		stax.writeAttribute(SchemaProperties.TABLE_SPACE_NAME.getLabel(), this.getTableSpaceName());
		stax.writeAttribute(SchemaProperties.PURGE_ASYNCHRONOUS.getLabel(), this.isPurgeAsynchronous());
		stax.writeAttribute(SchemaProperties.PURGE_DEFERRED.getLabel(), this.isPurgeDeferred());
		stax.writeAttribute(SchemaProperties.PURGE_START.getLabel(), this.getPurgeStart());
		stax.writeAttribute(SchemaProperties.PURGE_INTERVAL.getLabel(), this.getPurgeInterval());
		stax.writeAttribute(SchemaProperties.COMMIT_SCN_BASED.getLabel(), this.isCommitScnBased());
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		if (!isEmpty(columns)) {
			columns.writeXml(stax);
		}
	}

	public Table getMasterTable() {
		if (this.masterTable != null && masterTable.getParent() == null) {
			setTableFromParent(masterTable);
		}
		return masterTable;
	}

	@Override
	public String getMasterTableName() {
		if (getMasterTable() == null) {
			return null;
		}
		return this.masterTable.getName();
	}

	public MviewLog setMasterTable(Table masterTable) {
		setTableFromParent(masterTable);
		columns.setTable(masterTable);
		return this;
	}

	protected void setColumns(ReferenceColumnCollection columns) {
		this.columns = columns;
		if (this.columns != null) {
			this.columns.setParent(this);
		}
	}

	@Override
	public MviewLog setMasterTableName(String masterTableName) {
		if (CommonUtils.isEmpty(masterTableName)){
			this.setMasterTable(null);
		} else{
			if (this.getMasterTable()==null){
				this.setMasterTable(new Table());
			}
			this.getMasterTable().setName(masterTableName);
		}
		return instance();
	}
	
	@Override
	public String getMasterTableSchemaName() {
		if (getMasterTable() == null) {
			return null;
		}
		return this.getMasterTable().getSchemaName();
	}

	@Override
	public MviewLog setMasterTableSchemaName(String masterTableSchemaName) {
		if (this.getMasterTable()==null) {
			this.setMasterTable(new Table());
		}
		this.getMasterTable().setSchemaName(masterTableSchemaName);
		return this;
	}

	private void setTableFromParent(Table masterTable) {
		if (this.masterTable == masterTable) {
			return;
		}
		if (this.getParent() == null) {
			this.masterTable = masterTable;
			return;
		}
		if (this.getParent().getSchema() == null) {
			this.masterTable = masterTable;
			return;
		}
		Table getTable = this.getParent().getSchema()
				.getTable(masterTable.getName());
		if (getTable != null) {
			this.masterTable = getTable;
			return;
		}
		this.masterTable = masterTable;
	}

	@Override
	public boolean isSaveRowIds() {
		return saveRowIds;
	}

	@Override
	public MviewLog setSaveRowIds(boolean saveRowIds) {
		this.saveRowIds = saveRowIds;
		return this;
	}

	@Override
	public boolean isSavePrimaryKey() {
		return savePrimaryKey;
	}

	@Override
	public MviewLog setSavePrimaryKey(boolean savePrimaryKey) {
		this.savePrimaryKey = savePrimaryKey;
		return this;
	}

	@Override
	public boolean isSaveObjectId() {
		return saveObjectId;
	}

	@Override
	public MviewLog setSaveObjectId(boolean saveObjectId) {
		this.saveObjectId = saveObjectId;
		return this;
	}

	@Override
	public boolean isSaveFilterColumns() {
		return saveFilterColumns;
	}

	@Override
	public MviewLog setSaveFilterColumns(boolean saveFilterColumns) {
		this.saveFilterColumns = saveFilterColumns;
		return this;
	}

	@Override
	public boolean isSaveSequence() {
		return saveSequence;
	}

	@Override
	public MviewLog setSaveSequence(boolean saveSequence) {
		this.saveSequence = saveSequence;
		return this;
	}

	@Override
	public boolean isIncludeNewValues() {
		return includeNewValues;
	}

	@Override
	public MviewLog setIncludeNewValues(boolean includeNewValues) {
		this.includeNewValues = includeNewValues;
		return this;
	}

	@Override
	public boolean isPurgeAsynchronous() {
		return purgeAsynchronous;
	}

	@Override
	public MviewLog setPurgeAsynchronous(boolean purgeAsynchronous) {
		this.purgeAsynchronous = purgeAsynchronous;
		return this;
	}

	@Override
	public boolean isPurgeDeferred() {
		return purgeDeferred;
	}

	@Override
	public MviewLog setPurgeDeferred(boolean purgeDeferred) {
		this.purgeDeferred = purgeDeferred;
		return this;
	}

	@Override
	public Timestamp getPurgeStart() {
		return purgeStart;
	}

	@Override
	public MviewLog setPurgeStart(Timestamp purgeStart) {
		this.purgeStart = purgeStart;
		return this;
	}

	@Override
	public String getPurgeInterval() {
		return purgeInterval;
	}

	@Override
	public MviewLog setPurgeInterval(String purgeInterval) {
		this.purgeInterval = purgeInterval;
		return this;
	}

	@Override
	public boolean isCommitScnBased() {
		return commitScnBased;
	}

	@Override
	public MviewLog setCommitScnBased(boolean commitScnBased) {
		this.commitScnBased = commitScnBased;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public MviewLogCollection getParent() {
		return (MviewLogCollection) super.getParent();
	}

	public ReferenceColumnCollection getColumns() {
		return columns;
	}

	@Override
	protected void validate(){
		super.validate();
		setTableSpace(this.getTableSpaceFromParent(tableSpace));
	}

}
