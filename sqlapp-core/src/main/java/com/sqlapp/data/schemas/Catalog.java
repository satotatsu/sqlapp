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
import static com.sqlapp.util.CommonUtils.linkedMap;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.function.AddDbObjectPredicate;
import com.sqlapp.data.schemas.properties.CaseSensitiveProperty;
import com.sqlapp.data.schemas.properties.CharacterSemanticsProperty;
import com.sqlapp.data.schemas.properties.CharacterSetProperty;
import com.sqlapp.data.schemas.properties.CollationProperty;
import com.sqlapp.data.schemas.properties.ColumnPrivilegesProperty;
import com.sqlapp.data.schemas.properties.DisplayNameProperty;
import com.sqlapp.data.schemas.properties.DisplayRemarksProperty;
import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.data.schemas.properties.ProductProperties;
import com.sqlapp.data.schemas.properties.RemarksProperty;
import com.sqlapp.data.schemas.properties.SchemaPrivilegesProperty;
import com.sqlapp.data.schemas.properties.object.AssembliesProperty;
import com.sqlapp.data.schemas.properties.object.DirectoriesProperty;
import com.sqlapp.data.schemas.properties.object.ObjectPrivilegesProperty;
import com.sqlapp.data.schemas.properties.object.PartitionFunctionsProperty;
import com.sqlapp.data.schemas.properties.object.PartitionSchemesProperty;
import com.sqlapp.data.schemas.properties.object.PublicDbLinksProperty;
import com.sqlapp.data.schemas.properties.object.PublicSynonymsProperty;
import com.sqlapp.data.schemas.properties.object.RoleMembersProperty;
import com.sqlapp.data.schemas.properties.object.RolePrivilegesProperty;
import com.sqlapp.data.schemas.properties.object.RolesProperty;
import com.sqlapp.data.schemas.properties.object.RoutinePrivilegesProperty;
import com.sqlapp.data.schemas.properties.object.SchemasProperty;
import com.sqlapp.data.schemas.properties.object.SettingsProperty;
import com.sqlapp.data.schemas.properties.object.TableSpacesProperty;
import com.sqlapp.data.schemas.properties.object.UserPrivilegesProperty;
import com.sqlapp.data.schemas.properties.object.UsersProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;
import com.sqlapp.util.StaxWriter;

/**
 * DBのカタログに相当するクラス
 * 
 * @author satoh
 * 
 */
@SuppressWarnings("rawtypes")
public final class Catalog extends AbstractBaseDbObject<Catalog> implements
		NameProperty<Catalog>
		, DisplayNameProperty<Catalog>
		, RemarksProperty<Catalog>
		, DisplayRemarksProperty<Catalog>
		, CaseSensitiveProperty<Catalog>
		, ProductProperties<Catalog>
		, CharacterSemanticsProperty<Catalog>
		, CharacterSetProperty<Catalog>
		, CollationProperty<Catalog>
		, Sortable
		, Mergeable<Catalog>
		, SchemasProperty<Catalog>
		, PublicSynonymsProperty<Catalog>
		, PublicDbLinksProperty<Catalog>
		, UsersProperty<Catalog>
		, RolesProperty<Catalog>
		, TableSpacesProperty<Catalog>
		, DirectoriesProperty<Catalog>
		, PartitionFunctionsProperty<Catalog>
		, PartitionSchemesProperty<Catalog>
		, AssembliesProperty<Catalog>
		, ObjectPrivilegesProperty<Catalog>
		, RoutinePrivilegesProperty<Catalog>
		, UserPrivilegesProperty<Catalog>
		, ColumnPrivilegesProperty<Catalog>
		, RolePrivilegesProperty<Catalog>
		, SchemaPrivilegesProperty<Catalog>
		, RoleMembersProperty<Catalog>
		, SettingsProperty<Catalog>
		, HasParent<CatalogCollection>, RowIteratorHandlerProperty {

	/** serialVersionUID */
	private static final long serialVersionUID = 1L;
	/** name */
	private String name = null;
	/** displayName */
	private String displayName = null;
	/** description */
	private String displayRemarks = null;
	/** DBコメント */
	private String remarks = null;
	private boolean caseSensitive=(Boolean)SchemaProperties.CASE_SENSITIVE.getDefaultValue();
	/** カラムの文字列のセマンティックス */
	private CharacterSemantics characterSemantics = null;
	/** characterSet */
	private String characterSet = null;
	/** collation */
	private String collation = null;
	/** DBバージョン情報 */
	private ProductVersionInfo productVersionInfo = null;
	/** スキーマのコレクション */
	private SchemaCollection schemas = new SchemaCollection(this);
	/** PUBLIC SYNONYMコレクション */
	private PublicSynonymCollection publicSynonyms = new PublicSynonymCollection(
			this);
	/** PUBLIC DB LINKコレクション */
	private PublicDbLinkCollection publicDbLinks = new PublicDbLinkCollection(
			this);
	/** ユーザーコレクション */
	private UserCollection users = new UserCollection(this);
	/** ロールコレクション */
	private RoleCollection roles = new RoleCollection(this);
	/** テーブルスペースコレクション */
	private TableSpaceCollection tableSpaces = new TableSpaceCollection(this);
	/** ディレクトリコレクション */
	private DirectoryCollection directories = new DirectoryCollection(this);
	/** パーティション関数コレクション */
	private PartitionFunctionCollection partitionFunctions = new PartitionFunctionCollection(
			this);
	/** パーティションスキームコレクション */
	private PartitionSchemeCollection partitionSchemes = new PartitionSchemeCollection(
			this);
	/** アセンブリコレクション */
	private AssemblyCollection assemblies = new AssemblyCollection(this);
	/** オブジェクト権限コレクション */
	private ObjectPrivilegeCollection objectPrivileges = new ObjectPrivilegeCollection(
			this);
	/** Routine権限コレクション */
	private RoutinePrivilegeCollection routinePrivileges = new RoutinePrivilegeCollection(
			this);
	/** カラム権限コレクション */
	private ColumnPrivilegeCollection columnPrivileges = new ColumnPrivilegeCollection(
			this);
	/** ユーザー権限コレクション */
	private UserPrivilegeCollection userPrivileges = new UserPrivilegeCollection(
			this);
	/** ROLE権限コレクション */
	private RolePrivilegeCollection rolePrivileges = new RolePrivilegeCollection(
			this);
	/** スキーマ権限コレクション */
	private SchemaPrivilegeCollection schemaPrivileges = new SchemaPrivilegeCollection(
			this);
	/** ロールメンバーコレクション */
	private RoleMemberCollection roleMembers = new RoleMemberCollection(this);
	/** Settingコレクション */
	private SettingCollection settings = new SettingCollection(this);

	private Map<String, AbstractDbObjectCollection> objectMap = getObjectMap();

	/**
	 * コンストラクタ
	 */
	public Catalog() {

	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 *            カタログ名
	 */
	public Catalog(String name) {
		this.name=name;
	}
	
	@Override
	protected Supplier<Catalog> newInstance(){
		return ()->new Catalog();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Catalog setName(String name) {
		this.name = name;
		return this;
	}

	private Map<String, AbstractDbObjectCollection> getObjectMap() {
		Map<String, AbstractDbObjectCollection> map = linkedMap();
		Set<ISchemaProperty> schemaProperties=SchemaUtils.getSchemaObjectProperties(this.getClass());
		for(ISchemaProperty schemaProperty:schemaProperties){
			map.put(schemaProperty.getLabel(), (AbstractDbObjectCollection)schemaProperty.getValue(this));
		}
		return map;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void cloneProperties(Catalog clone) {
		super.cloneProperties(clone);
		Map<String, AbstractDbObjectCollection> map = clone.getObjectMap();
		for (Map.Entry<String, AbstractDbObjectCollection> entry : this
				.getObjectMap().entrySet()) {
			map.get(entry.getKey()).clear();
			AbstractDbObjectCollection cc = entry.getValue().clone();
			map.get(entry.getKey()).addAll(cc);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof Catalog)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		Catalog val = (Catalog) obj;
		if (!equals(SchemaProperties.NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DISPLAY_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PRODUCT_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PRODUCT_MAJOR_VERSION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PRODUCT_MINOR_VERSION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PRODUCT_REVISION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.CHARACTER_SEMANTICS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.CHARACTER_SET, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.COLLATION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.REMARKS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DISPLAY_REMARKS, val, equalsHandler)) {
			return false;
		}
		for (Map.Entry<String, AbstractDbObjectCollection> entry : this.objectMap
				.entrySet()) {
			String key=entry.getKey();
			if (!equals(key, val, entry.getValue(),
					val.objectMap.get(key), equalsHandler)) {
				return false;
			}
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.NAME.getLabel(), this.getName());
		stax.writeAttribute(SchemaProperties.DISPLAY_NAME.getLabel(), getDisplayName());
		stax.writeAttribute(SchemaProperties.PRODUCT_NAME.getLabel(), this.getProductName());
		stax.writeAttribute(SchemaProperties.PRODUCT_MAJOR_VERSION.getLabel(),
				this.getProductMajorVersion());
		stax.writeAttribute(SchemaProperties.PRODUCT_MINOR_VERSION.getLabel(),
				this.getProductMinorVersion());
		stax.writeAttribute(SchemaProperties.PRODUCT_REVISION.getLabel(), this.getProductRevision());
		stax.writeAttribute(SchemaProperties.CHARACTER_SEMANTICS.getLabel(), this.getCharacterSemantics());
		stax.writeAttribute(SchemaProperties.CHARACTER_SET.getLabel(), this.getCharacterSet());
		stax.writeAttribute(SchemaProperties.COLLATION.getLabel(), this.getCollation());
		if (!needsEscape(this.getRemarks())) {
			stax.writeAttribute(SchemaProperties.REMARKS.getLabel(), this.getRemarks());
		}
		if (!needsEscape(this.getDisplayRemarks())) {
			stax.writeAttribute(SchemaProperties.DISPLAY_REMARKS.getLabel(), this.getDisplayRemarks());
		}
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		for (Map.Entry<String, AbstractDbObjectCollection> entry : this.objectMap
				.entrySet()) {
			AbstractDbObjectCollection list = entry.getValue();
			if (!isEmpty(list)) {
				list.writeXml(stax);
			}
		}
		if (needsEscape(this.getRemarks())) {
			stax.newLine();
			stax.indent();
			stax.writeCData(SchemaProperties.REMARKS.getLabel(), this.getRemarks());
		}
		if (needsEscape(this.getDisplayRemarks())) {
			stax.newLine();
			stax.indent();
			stax.writeCData(SchemaProperties.DISPLAY_REMARKS.getLabel(), this.getDisplayRemarks());
		}
		super.writeXmlOptionalValues(stax);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.CharacterSemanticsProperty#getCharacterSemantics
	 * ()
	 */
	@Override
	public CharacterSemantics getCharacterSemantics() {
		return characterSemantics;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.CharacterSemanticsProperty#setCharacterSemantics
	 * (com.sqlapp.data.schemas.CharacterSemantics)
	 */
	@Override
	public Catalog setCharacterSemantics(CharacterSemantics characterSemantics) {
		this.characterSemantics = characterSemantics;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.CollationProperty#getCollation()
	 */
	@Override
	public String getCollation() {
		return collation;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.CollationProperty#setCollation(java.lang.String)
	 */
	@Override
	public Catalog setCollation(String collation) {
		this.collation = collation;
		return this;
	}

	/**
	 * @return the productVersionInfo
	 */
	protected ProductVersionInfo getProductVersionInfo() {
		if (productVersionInfo == null) {
			productVersionInfo = new ProductVersionInfo();
		}
		return productVersionInfo;
	}

	/**
	 * @param productVersionInfo
	 *            the productVersionInfo to set
	 */
	protected void setProductVersionInfo(ProductVersionInfo productVersionInfo) {
		this.productVersionInfo = productVersionInfo;
	}

	/**
	 * @return DB製品名を取得します
	 */
	@Override
	public String getProductName() {
		return getProductVersionInfo().getName();
	}

	/**
	 * @param dbProductName
	 *            DB製品名を設定します
	 */
	@Override
	public Catalog setProductName(String dbProductName) {
		getProductVersionInfo().setName(dbProductName);
		return instance();
	}

	/**
	 * @return the dbMajorVersion
	 */
	@Override
	public Integer getProductMajorVersion() {
		return getProductVersionInfo().getMajorVersion();
	}

	/**
	 * @param dbMajorVersion
	 *            the dbMajorVersion to set
	 */
	@Override
	public Catalog setProductMajorVersion(Integer dbMajorVersion) {
		getProductVersionInfo().setMajorVersion(dbMajorVersion);
		return instance();
	}

	/**
	 * @return the dbMinorVersion
	 */
	@Override
	public Integer getProductMinorVersion() {
		return getProductVersionInfo().getMinorVersion();
	}

	/**
	 * @param dbMinorVersion
	 *            the dbMinorVersion to set
	 */
	@Override
	public Catalog setProductMinorVersion(Integer dbMinorVersion) {
		getProductVersionInfo().setMinorVersion(dbMinorVersion);
		return instance();
	}

	/**
	 * @return the revision
	 */
	public Integer getProductRevision() {
		return getProductVersionInfo().getRevision();
	}

	/**
	 * @param revision
	 *            the revision to set
	 */
	public Catalog setProductRevision(Integer revision) {
		getProductVersionInfo().setRevision(revision);
		return instance();
	}

	/**
	 * @return the schemas
	 */
	@Override
	public SchemaCollection getSchemas() {
		return schemas;
	}

	/**
	 * @return the users
	 */
	@Override
	public UserCollection getUsers() {
		return users;
	}

	/**
	 * @param schemas
	 *            the schemas to set
	 */
	protected Catalog setSchemas(SchemaCollection schemas) {
		if (this.schemas != null) {
			this.schemas.setParent(this);
		}
		return this;
	}

	protected Catalog setUsers(UserCollection users) {
		this.users = users;
		if (this.users != null) {
			this.users.setParent(this);
		}
		return this;
	}

	@Override
	public RoleCollection getRoles() {
		return roles;
	}

	protected Catalog setRoles(RoleCollection roles) {
		this.roles = roles;
		if (this.roles != null) {
			this.roles.setParent(this);
		}
		return this;
	}

	/**
	 * @return the tableSpaces
	 */
	@Override
	public TableSpaceCollection getTableSpaces() {
		return tableSpaces;
	}

	/**
	 * @param tableSpaces
	 *            the tableSpaces to set
	 */
	protected Catalog setTableSpaces(TableSpaceCollection tableSpaces) {
		this.tableSpaces = tableSpaces;
		if (this.tableSpaces != null) {
			this.tableSpaces.setParent(this);
		}
		return this;
	}

	/**
	 * @return the directories
	 */
	@Override
	public DirectoryCollection getDirectories() {
		return directories;
	}

	/**
	 * @param directories
	 *            the directories to set
	 */
	protected Catalog setDirectories(DirectoryCollection directories) {
		this.directories = directories;
		if (this.directories != null) {
			this.directories.setParent(this);
		}
		return this;
	}

	/**
	 * @return the partitionFunctions
	 */
	@Override
	public PartitionFunctionCollection getPartitionFunctions() {
		return partitionFunctions;
	}

	/**
	 * @param partitionFunctions
	 *            the partitionFunctions to set
	 */
	protected Catalog setPartitionFunctions(
			PartitionFunctionCollection partitionFunctions) {
		this.partitionFunctions = partitionFunctions;
		if (this.partitionFunctions != null) {
			this.partitionFunctions.setParent(this);
		}
		return this;
	}

	@Override
	public PartitionSchemeCollection getPartitionSchemes() {
		return partitionSchemes;
	}

	protected Catalog setPartitionSchemes(
			PartitionSchemeCollection partitionSchemes) {
		this.partitionSchemes = partitionSchemes;
		if (this.partitionSchemes != null) {
			this.partitionSchemes.setParent(this);
		}
		return this;
	}

	@Override
	public AssemblyCollection getAssemblies() {
		return assemblies;
	}

	protected Catalog setAssemblies(AssemblyCollection assemblies) {
		this.assemblies = assemblies;
		if (this.assemblies != null) {
			this.assemblies.setParent(this);
		}
		return this;
	}

	@Override
	public ObjectPrivilegeCollection getObjectPrivileges() {
		return objectPrivileges;
	}

	protected Catalog setObjectPrivileges(
			ObjectPrivilegeCollection objectPrivileges) {
		this.objectPrivileges = objectPrivileges;
		if (this.objectPrivileges != null) {
			this.objectPrivileges.setParent(this);
		}
		return this;
	}
	
	@Override
	public RoutinePrivilegeCollection getRoutinePrivileges() {
		return routinePrivileges;
	}

	protected Catalog setRoutinePrivileges(
			RoutinePrivilegeCollection routinePrivileges) {
		this.routinePrivileges = routinePrivileges;
		if (this.routinePrivileges != null) {
			this.routinePrivileges.setParent(this);
		}
		return this;
	}

	@Override
	public ColumnPrivilegeCollection getColumnPrivileges() {
		return columnPrivileges;
	}

	protected Catalog setColumnPrivileges(
			ColumnPrivilegeCollection columnPrivileges) {
		this.columnPrivileges = columnPrivileges;
		if (this.columnPrivileges != null) {
			this.columnPrivileges.setParent(this);
		}
		return this;
	}

	/**
	 * @return the publicSynonyms
	 */
	@Override
	public PublicSynonymCollection getPublicSynonyms() {
		return publicSynonyms;
	}

	/**
	 * @param publicSynonyms
	 *            the publicSynonyms to set
	 */
	protected Catalog setPublicSynonyms(PublicSynonymCollection publicSynonyms) {
		this.publicSynonyms = publicSynonyms;
		if (this.publicSynonyms != null) {
			this.publicSynonyms.setParent(this);
		}
		return instance();
	}

	/**
	 * @return the publicDbLinks
	 */
	@Override
	public PublicDbLinkCollection getPublicDbLinks() {
		return publicDbLinks;
	}

	/**
	 * @param publicDbLinks
	 *            the publicDbLinks to set
	 */
	protected Catalog setPublicDbLinks(PublicDbLinkCollection publicDbLinks) {
		this.publicDbLinks = publicDbLinks;
		if (this.publicDbLinks != null) {
			this.publicDbLinks.setParent(this);
		}
		return instance();
	}

	public UserPrivilegeCollection getUserPrivileges() {
		return userPrivileges;
	}

	protected Catalog setUserPrivileges(UserPrivilegeCollection userPrivileges) {
		this.userPrivileges = userPrivileges;
		if (this.userPrivileges != null) {
			this.userPrivileges.setParent(this);
		}
		return this;
	}

	@Override
	public SchemaPrivilegeCollection getSchemaPrivileges() {
		return schemaPrivileges;
	}

	/**
	 * @return the rolePrivileges
	 */
	@Override
	public RolePrivilegeCollection getRolePrivileges() {
		return rolePrivileges;
	}

	/**
	 * @param rolePrivileges
	 *            the rolePrivileges to set
	 */
	protected Catalog setRolePrivileges(RolePrivilegeCollection rolePrivileges) {
		this.rolePrivileges = rolePrivileges;
		if (this.rolePrivileges != null) {
			this.rolePrivileges.setParent(this);
		}
		return this;
	}

	protected Catalog setSchemaPrivileges(
			SchemaPrivilegeCollection schemaPrivileges) {
		this.schemaPrivileges = schemaPrivileges;
		if (this.schemaPrivileges != null) {
			this.schemaPrivileges.setParent(this);
		}
		return this;
	}

	@Override
	public RoleMemberCollection getRoleMembers() {
		return roleMembers;
	}

	protected Catalog setRoleMembers(RoleMemberCollection roleMembers) {
		this.roleMembers = roleMembers;
		if (this.roleMembers != null) {
			this.roleMembers.setParent(this);
		}
		return this;
	}

	@Override
	public SettingCollection getSettings() {
		return this.settings;
	}

	protected Catalog setSettings(SettingCollection settings) {
		this.settings = settings;
		if (this.settings != null) {
			this.settings.setParent(this);
		}
		return this;
	}

	/**
	 * カタログの内容をバリデートします
	 */
	public void validate() {
		super.validate();
		for (Schema schema : this.getSchemas()) {
			schema.validate();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Sortable#sort()
	 */
	@Override
	public void sort() {
		for (Map.Entry<String, AbstractDbObjectCollection> entry : this.objectMap
				.entrySet()) {
			if (entry.getValue() instanceof Sortable) {
				Sortable sortable = (Sortable) entry.getValue();
				sortable.sort();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Sortable#sort(java.util.Comparator)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void sort(Comparator comparator) {
		for (Map.Entry<String, AbstractDbObjectCollection> entry : this.objectMap
				.entrySet()) {
			if (entry.getValue() instanceof Sortable) {
				Sortable sortable = (Sortable) entry.getValue();
				sortable.sort(comparator);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(Catalog o) {
		if (o == null) {
			return 1;
		}
		return CommonUtils.compare(this.getName(), o.getName());
	}

	/**
	 * @param rowIteratorHandler
	 *            the rowIteratorHandler to set
	 */
	@Override
	public void setRowIteratorHandler(RowIteratorHandler rowIteratorHandler) {
		getSchemas().setRowIteratorHandler(rowIteratorHandler);
	}

	/**
	 * @param addDbObjectFilter
	 *            the addDbObjectFilter to set
	 */
	public void setAddDbObjectFilter(AddDbObjectPredicate addDbObjectFilter) {
		this.getSchemas().setAddDbObjectPredicate(addDbObjectFilter);
		for (Map.Entry<String, AbstractDbObjectCollection> entry : this.objectMap
				.entrySet()) {
			AbstractDbObjectCollection target = entry.getValue();
			target.setAddDbObjectPredicate(addDbObjectFilter);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Mergeable#merge(com.sqlapp.data.schemas.
	 * DbCommonObject)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void merge(Catalog obj) {
		Map<String, AbstractDbObjectCollection> myMap = getObjectMap();
		Map<String, AbstractDbObjectCollection> objMap = obj.getObjectMap();
		for (Map.Entry<String, AbstractDbObjectCollection> entry : objMap
				.entrySet()) {
			AbstractDbObjectCollection c = myMap.get(entry.getKey());
			c.addAll(entry.getValue());
			c.sort();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#getParent()
	 */
	@Override
	public CatalogCollection getParent() {
		return (CatalogCollection) super.getParent();
	}

	@Override
	public boolean isCaseSensitive() {
		return this.caseSensitive;
	}

	@Override
	public Catalog setCaseSensitive(boolean value) {
		this.caseSensitive=value;
		return instance();
	}

	@Override
	public String getDisplayRemarks() {
		return this.displayRemarks;
	}

	@Override
	public Catalog setDisplayRemarks(String value) {
		this.displayRemarks=value;
		return instance();
	}

	@Override
	public String getRemarks() {
		return this.remarks;
	}

	@Override
	public Catalog setRemarks(String value) {
		this.remarks=value;
		return instance();
	}

	@Override
	public String getDisplayName() {
		return this.displayName;
	}

	@Override
	public Catalog setDisplayName(String value) {
		this.displayName=value;
		return instance();
	}

	@Override
	public String getCharacterSet() {
		return this.characterSet;
	}

	@Override
	public Catalog setCharacterSet(String value) {
		this.characterSet=value;
		return instance();
	}
	
	/**
	 * @return the dialect
	 */
	@Override
	public Dialect getDialect() {
		Dialect dialect=SimpleBeanUtils.getField(this, "dialect");
		if (dialect==null){
			if (getProductVersionInfo()!=null){
				dialect=getProductVersionInfo().toDialect();
				this.setDialect(dialect);
			}
		}
		return dialect;
	}

	@Override
	public Catalog setDialect(Dialect dialect){
		SimpleBeanUtils.setField(this, "dialect", dialect);
		return this;
	}
}
