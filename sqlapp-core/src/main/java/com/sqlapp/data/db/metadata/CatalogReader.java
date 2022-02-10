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
import com.sqlapp.data.schemas.CatalogCollection;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.properties.ProductProperties;
import com.sqlapp.util.DbUtils;

/**
 * カタログ読み込み
 * 
 * @author satoh
 * 
 */
public abstract class CatalogReader extends
		MetadataReader<Catalog, CatalogCollection> {

	protected CatalogReader(Dialect dialect) {
		super(dialect);
		this.setReadDbObjectPredicate((r,o)->true);
		this.setReaderOptions(new ReaderOptions());
	}

	protected void setReaderParameter(
			AbstractCatalogObjectMetadataReader<?> reader) {
		if (reader != null) {
			reader.setCatalogName(this.getCatalogName());
			initializeChild(reader);
		}
	}

	/**
	 * カレントカタログを取得します
	 * 
	 * @param connection
	 * @return カレントカタログ
	 */
	public abstract String getCurrentCatalogName(Connection connection);

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.DbMetadataReader#load(java.sql.Connection
	 * , java.lang.Object)
	 */
	@Override
	public void loadFull(Connection connection, CatalogCollection catalogs) {
		List<Catalog> list = getAllFull(connection);
		int size = list.size();
		for (int i = 0; i < size; i++) {
			Catalog obj = list.get(i);
			catalogs.add(obj);
		}
	}

	/**
	 * カタログ名を含むパラメタコンテキストを作成します。
	 * 
	 */
	protected ParametersContext defaultParametersContext(Connection connection) {
		ParametersContext context = newParametersContext(connection,
				this.getCatalogName());
		return context;
	}

	/**
	 * メタデータの詳細情報を設定するためのメソッドです。子クラスでのオーバーライドを想定しています。
	 * 
	 * @param connection
	 * @param obj
	 */
	@Override
	protected void setMetadataDetail(Connection connection, Catalog catalog)
			throws SQLException {
		setCurrentCatalog(connection, catalog.getName());
		setProductInfo(connection, this.getDialect(), catalog);
		this.setCatalogName(catalog.getName());
		setCommonBefore(connection, catalog);
		load(connection, this.getSettingReader(), catalog);
		SchemaReader schemaReader = this.getSchemaReader();
		schemaReader.setSettings(catalog.getSettings());
		load(connection, schemaReader, catalog);
		load(connection, this.getAssemblyReader(), catalog);
		load(connection, this.getDirectoryReader(), catalog);
		load(connection, this.getPartitionFunctionReader(), catalog);
		load(connection, this.getPartitionSchemeReader(), catalog);
		load(connection, this.getPublicDbLinkReader(), catalog);
		load(connection, this.getPublicSynonymReader(), catalog);
		load(connection, this.getTableSpaceReader(), catalog);
		load(connection, this.getRoleReader(), catalog);
		load(connection, this.getUserReader(), catalog);
		load(connection, this.getObjectPrivilegeReader(), catalog);
		load(connection, this.getRoutinePrivilegeReader(), catalog);
		load(connection, this.getColumnPrivilegeReader(), catalog);
		load(connection, this.getUserPrivilegeReader(), catalog);
		load(connection, this.getRolePrivilegeReader(), catalog);
		catalog.validate();
		setCommonAfter(connection, catalog);
	}

	protected void setCurrentCatalog(Connection connection, String catalogName) {
		if (!this.getDialect().supportsCatalog()) {
			return;
		}
		try {
			connection.setCatalog(catalogName);
		} catch (SQLException e) {
			logger.warn(e.getMessage(), e);
		}
	}

	protected void setCommonBefore(Connection connection, Catalog catalog) {

	}

	protected void setCommonAfter(Connection connection, Catalog catalog) {

	}

	/**
	 * 製品名、バージョンを設定します
	 * 
	 * @param connection
	 * @param productInfo
	 */
	protected static void setProductInfo(Connection connection,
			Dialect dialect, ProductProperties<?> productInfo) {
		ProductVersionInfo productVersionInfo = DbUtils
				.getProductVersionInfo(connection);
		productInfo.setProductName(productVersionInfo.getName());
		productInfo
				.setProductMajorVersion(productVersionInfo.getMajorVersion());
		productInfo
				.setProductMinorVersion(productVersionInfo.getMinorVersion());
		productInfo.setProductRevision(productVersionInfo.getRevision());
	}

	protected void load(Connection connection,
			AbstractCatalogObjectMetadataReader<?> reader, Catalog catalog) {
		if (reader != null) {
			reader.loadFull(connection, catalog);
		}
	}

	/**
	 * スキーマ読み込みクラスを取得します
	 * 
	 */
	public SchemaReader getSchemaReader() {
		SchemaReader reader = newSchemaReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract SchemaReader newSchemaReader();

	/**
	 * テーブルスペース読み込みクラスを取得します
	 * 
	 */
	public TableSpaceReader getTableSpaceReader() {
		TableSpaceReader reader = newTableSpaceReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract TableSpaceReader newTableSpaceReader();

	/**
	 * ディレクトリ読み込みクラスを取得します
	 * 
	 */
	public DirectoryReader getDirectoryReader() {
		DirectoryReader reader = newDirectoryReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract DirectoryReader newDirectoryReader();

	/**
	 * パーティション関数読み込み(SQLServer2005以降専用)クラスを取得します
	 * 
	 */
	public PartitionFunctionReader getPartitionFunctionReader() {
		PartitionFunctionReader reader = newPartitionFunctionReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract PartitionFunctionReader newPartitionFunctionReader();

	/**
	 * パーティションスキーム読み込み(SQLServer2005以降専用)クラスを取得します
	 * 
	 */
	public PartitionSchemeReader getPartitionSchemeReader() {
		PartitionSchemeReader reader = newPartitionSchemeReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract PartitionSchemeReader newPartitionSchemeReader();

	/**
	 * CLRアセンブリ読み込み(SQLServer2005以降専用)クラスを取得します
	 * 
	 */
	public AssemblyReader getAssemblyReader() {
		AssemblyReader reader = newAssemblyReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract AssemblyReader newAssemblyReader();

	/**
	 * Public DB Link読み込みクラスを取得します
	 * 
	 */
	public PublicDbLinkReader getPublicDbLinkReader() {
		PublicDbLinkReader reader = newPublicDbLinkReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract PublicDbLinkReader newPublicDbLinkReader();

	/**
	 * Public Synonym読み込みクラスを取得します
	 * 
	 */
	public PublicSynonymReader getPublicSynonymReader() {
		PublicSynonymReader reader = newPublicSynonymReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract PublicSynonymReader newPublicSynonymReader();

	/**
	 * ユーザー読み込みクラスを取得します
	 * 
	 */
	public UserReader getUserReader() {
		UserReader reader = newUserReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract UserReader newUserReader();

	/**
	 * ロール読み込みクラスを取得します
	 * 
	 */
	public RoleReader getRoleReader() {
		RoleReader reader = newRoleReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract RoleReader newRoleReader();

	/**
	 * ObjectPrivilege読み込みクラスを取得します
	 * 
	 */
	public ObjectPrivilegeReader getObjectPrivilegeReader() {
		ObjectPrivilegeReader reader = newObjectPrivilegeReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract ObjectPrivilegeReader newObjectPrivilegeReader();

	/**
	 * RoutinePrivilege読み込みクラスを取得します
	 * 
	 */
	public RoutinePrivilegeReader getRoutinePrivilegeReader() {
		RoutinePrivilegeReader reader = newRoutinePrivilegeReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract RoutinePrivilegeReader newRoutinePrivilegeReader();

	/**
	 * ColumnPrivilege読み込みクラスを取得します
	 * 
	 */
	public ColumnPrivilegeReader getColumnPrivilegeReader() {
		ColumnPrivilegeReader reader = newColumnPrivilegeReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract ColumnPrivilegeReader newColumnPrivilegeReader();

	/**
	 * UserPrivilege読み込みクラスを取得します
	 * 
	 */
	public UserPrivilegeReader getUserPrivilegeReader() {
		UserPrivilegeReader reader = newUserPrivilegeReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract UserPrivilegeReader newUserPrivilegeReader();

	/**
	 * RoleMember読み込みクラスを取得します
	 * 
	 */
	public RoleMemberReader getRoleMemberReader() {
		RoleMemberReader reader = newRoleMemberReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract RoleMemberReader newRoleMemberReader();

	/**
	 * RolePrivilegeReaderを取得します
	 * 
	 */
	public RolePrivilegeReader getRolePrivilegeReader() {
		RolePrivilegeReader reader = newRolePrivilegeReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract RolePrivilegeReader newRolePrivilegeReader();

	/**
	 * SettingReaderクラスを取得します
	 * 
	 */
	public SettingReader getSettingReader() {
		SettingReader reader = newSettingReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract SettingReader newSettingReader();

	/**
	 * 指定した名称のReaderを取得します
	 * 
	 * @param name
	 */
	@SuppressWarnings("unchecked")
	protected <T extends MetadataReader<?, ?>> T getMetadataReader(String name) {
		if ("catalog".equalsIgnoreCase(name)
				|| "catalogs".equalsIgnoreCase(name)) {
			return (T) this;
		}
		return MetadataReaderUtils.getMetadataReader(this, name);
	}
}
