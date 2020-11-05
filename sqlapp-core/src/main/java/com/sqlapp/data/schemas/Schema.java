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
import static com.sqlapp.util.CommonUtils.linkedMap;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.function.AddDbObjectPredicate;
import com.sqlapp.data.schemas.properties.CharacterSemanticsProperty;
import com.sqlapp.data.schemas.properties.CharacterSetProperty;
import com.sqlapp.data.schemas.properties.CollationProperty;
import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.data.schemas.properties.ProductProperties;
import com.sqlapp.data.schemas.properties.complex.IndexTableSpaceProperty;
import com.sqlapp.data.schemas.properties.complex.LobTableSpaceProperty;
import com.sqlapp.data.schemas.properties.complex.TableSpaceProperty;
import com.sqlapp.data.schemas.properties.complex.TemporaryTableSpaceProperty;
import com.sqlapp.data.schemas.properties.object.ConstantsProperty;
import com.sqlapp.data.schemas.properties.object.DbLinksProperty;
import com.sqlapp.data.schemas.properties.object.DimensionsProperty;
import com.sqlapp.data.schemas.properties.object.DomainsProperty;
import com.sqlapp.data.schemas.properties.object.EventsProperty;
import com.sqlapp.data.schemas.properties.object.ExternalTablesProperty;
import com.sqlapp.data.schemas.properties.object.FunctionsProperty;
import com.sqlapp.data.schemas.properties.object.MasksProperty;
import com.sqlapp.data.schemas.properties.object.MviewLogsProperty;
import com.sqlapp.data.schemas.properties.object.MviewsProperty;
import com.sqlapp.data.schemas.properties.object.OperatorClassesProperty;
import com.sqlapp.data.schemas.properties.object.OperatorsProperty;
import com.sqlapp.data.schemas.properties.object.PackageBodiesProperty;
import com.sqlapp.data.schemas.properties.object.PackagesProperty;
import com.sqlapp.data.schemas.properties.object.ProceduresProperty;
import com.sqlapp.data.schemas.properties.object.RulesProperty;
import com.sqlapp.data.schemas.properties.object.SequencesProperty;
import com.sqlapp.data.schemas.properties.object.SynonymsProperty;
import com.sqlapp.data.schemas.properties.object.TableLinksProperty;
import com.sqlapp.data.schemas.properties.object.TablesProperty;
import com.sqlapp.data.schemas.properties.object.TriggersProperty;
import com.sqlapp.data.schemas.properties.object.TypeBodiesProperty;
import com.sqlapp.data.schemas.properties.object.TypesProperty;
import com.sqlapp.data.schemas.properties.object.ViewsProperty;
import com.sqlapp.data.schemas.properties.object.XmlSchemasProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.SimpleBeanUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * Schema
 * 
 */
@SuppressWarnings("rawtypes")
public final class Schema extends AbstractNamedObject<Schema> implements
		ProductProperties<Schema>,
		CharacterSemanticsProperty<Schema>, CollationProperty<Schema>,
		CharacterSetProperty<Schema>
		, Sortable
		, HasParent<SchemaCollection>
		, Mergeable<Schema>
		, RowIteratorHandlerProperty
		, TablesProperty<Schema>
		, ViewsProperty<Schema>
		, MviewsProperty<Schema>
		, ExternalTablesProperty<Schema>
		, MviewLogsProperty<Schema>
		, MasksProperty<Schema>
		, ProceduresProperty<Schema>
		, FunctionsProperty<Schema>
		, PackagesProperty<Schema>
		, PackageBodiesProperty<Schema>
		, TriggersProperty<Schema>
		, SequencesProperty<Schema>
		, DbLinksProperty<Schema>
		, TableLinksProperty<Schema>
		, SynonymsProperty<Schema>
		, DomainsProperty<Schema>
		, TypesProperty<Schema>
		, TypeBodiesProperty<Schema>
		, RulesProperty<Schema>
		, ConstantsProperty<Schema>
		, EventsProperty<Schema>
		, XmlSchemasProperty<Schema>
		, OperatorsProperty<Schema>
		, OperatorClassesProperty<Schema>
		, DimensionsProperty<Schema>
		, TableSpaceProperty<Table>
		, IndexTableSpaceProperty<Table>
		, LobTableSpaceProperty<Table>
		, TemporaryTableSpaceProperty<Table>
		{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 9009394768890692341L;
	/** テーブルのコレクション */
	private TableCollection tables = new TableCollection(this);
	/** ビューのコレクション */
	private ViewCollection views = new ViewCollection(this);
	/** マテリアライズドビューのコレクション */
	private MviewCollection mviews = new MviewCollection(this);
	/** 外部テーブルのコレクション */
	private ExternalTableCollection externalTables = new ExternalTableCollection(
			this);
	/** マテビューログのコレクション */
	private MviewLogCollection mviewLogs = new MviewLogCollection(this);
	/** Mask Collection */
	private MaskCollection masks = new MaskCollection(this);
	/** プロシージャのコレクション */
	private ProcedureCollection procedures = new ProcedureCollection(this);
	/** ファンクションのコレクション */
	private FunctionCollection functions = new FunctionCollection(this);
	/** パッケージのコレクション */
	private PackageCollection packages = new PackageCollection(this);
	/** パッケージBODYのコレクション */
	private PackageBodyCollection packageBodies = new PackageBodyCollection(
			this);
	/** トリガーのコレクション */
	private TriggerCollection triggers = new TriggerCollection(this);
	/** シーケンスのコレクション */
	private SequenceCollection sequences = new SequenceCollection(this);
	/** DBリンクのコレクション */
	private DbLinkCollection dbLinks = new DbLinkCollection(this);
	/** TABLEリンクのコレクション */
	private TableLinkCollection tableLinks = new TableLinkCollection(this);
	/** シノニムのコレクション */
	private SynonymCollection synonyms = new SynonymCollection(this);
	/** Domainのコレクション */
	private DomainCollection domains = new DomainCollection(this);
	/** DBタイプのコレクション */
	private TypeCollection types = new TypeCollection(this);
	/** DBタイプBODYのコレクション */
	private TypeBodyCollection typeBodies = new TypeBodyCollection(this);
	/** DBルールのコレクション */
	private RuleCollection rules = new RuleCollection(this);
	/** 定数のコレクション */
	private ConstantCollection constants = new ConstantCollection(this);
	/** イベントのコレクション */
	private EventCollection events = new EventCollection(this);
	/** XMLスキーマのコレクション */
	private XmlSchemaCollection xmlSchemas = new XmlSchemaCollection(this);
	/** Operatorのコレクション */
	private OperatorCollection operators = new OperatorCollection(this);
	/** Operatorクラスのコレクション */
	private OperatorClassCollection operatorClasses = new OperatorClassCollection(
			this);
	/** Dimensionクラスのコレクション */
	private DimensionCollection dimensions = new DimensionCollection(this);
	/** カラムの文字列のセマンティックス */
	@SuppressWarnings("unused")
	private CharacterSemantics characterSemantics = null;
	/** characterSet */
	@SuppressWarnings("unused")
	private String characterSet = null;
	/** collationName */
	@SuppressWarnings("unused")
	private String collation = null;
	/** DBバージョン情報 */
	private ProductVersionInfo productVersionInfo = null;

	/** テーブルスペース */
	@SuppressWarnings("unused")
	private TableSpace tableSpace = null;
	/** インデックステーブルスペース */
	@SuppressWarnings("unused")
	private TableSpace indexTableSpace = null;
	/** LOBテーブルスペース */
	@SuppressWarnings("unused")
	private TableSpace lobTableSpace = null;
	/** Temporaryテーブルスペース */
	@SuppressWarnings("unused")
	private TableSpace temporaryTableSpace = null;
	
	private Map<String, AbstractSchemaObjectCollection> objectMap = getChildObjectCollectionMap();

	/**
	 * コンストラクタ
	 */
	public Schema() {
	}

	/**
	 * @param addDbObjectFilter
	 *            the addDbObjectFilter to set
	 */
	public void setAddDbObjectFilter(AddDbObjectPredicate addDbObjectFilter) {
		for (Map.Entry<String, AbstractSchemaObjectCollection> entry : this.objectMap
				.entrySet()) {
			entry.getValue().setAddDbObjectPredicate(addDbObjectFilter);
		}
	}

	/**
	 * Schemaのもつ子オブジェクトのマップを返します。
	 * 
	 */
	public Map<String, AbstractSchemaObjectCollection> getChildObjectCollectionMap() {
		Map<String, AbstractSchemaObjectCollection> map = linkedMap();
		Set<ISchemaProperty> props=SchemaUtils.getSchemaObjectProperties(this.getClass());
		for(ISchemaProperty prop:props){
			map.put(prop.getLabel(), (AbstractSchemaObjectCollection)prop.getValue(this));
		}
		return map;
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public Schema(String name) {
		super(name);
	}
	
	@Override
	protected Supplier<Schema> newInstance(){
		return ()->new Schema();
	}

	@Override
	public TableCollection getTables() {
		return tables;
	}

	/**
	 * ビューコレクションを取得します
	 * 
	 */
	@Override
	public ViewCollection getViews() {
		return views;
	}

	/**
	 * マテリアライズドビューコレクションを取得します
	 * 
	 */
	@Override
	public MviewCollection getMviews() {
		return mviews;
	}

	/**
	 * プロシージャコレクションの取得
	 * 
	 */
	@Override
	public ProcedureCollection getProcedures() {
		return procedures;
	}

	/**
	 * ファンクションコレクションの取得
	 * 
	 */
	@Override
	public FunctionCollection getFunctions() {
		return functions;
	}

	public XmlSchemaCollection getXmlSchemas() {
		return xmlSchemas;
	}

	@Override
	public MviewLogCollection getMviewLogs() {
		return mviewLogs;
	}

	@Override
	public MaskCollection getMasks() {
		return masks;
	}

	
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof Schema)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		Schema val = (Schema) obj;
		for (Map.Entry<String, AbstractSchemaObjectCollection> entry : this.objectMap
				.entrySet()) {
			String key=entry.getKey();
			Object value=val.objectMap.get(key);
			if (!equals(key, val, entry.getValue(),
					value, equalsHandler)) {
				return false;
			}
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
		if (!equals(
				SchemaProperties.CHARACTER_SET, val, equalsHandler
				, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getCharacterSet(),
						val.getCharacterSet()))) {
			return false;
		}
		if (!equals(
				SchemaProperties.COLLATION, val, equalsHandler
				, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getCollation(),
						val.getCollation()))) {
			return false;
		}
		if (!equals(SchemaProperties.CHARACTER_SEMANTICS, val, equalsHandler)) {
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
		if (!equals(SchemaProperties.TEMPORARY_TABLE_SPACE_NAME, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {

	}

	@Override
	public ExternalTableCollection getExternalTables() {
		return externalTables;
	}

	/**
	 * プロシージャ、ファンクション、パッケージ、パッケージBODYの追加
	 * 
	 * @param routine
	 */
	public void addRoutine(AbstractSchemaObject<?> routine) {
		if (routine instanceof Procedure) {
			this.getProcedures().add((Procedure) routine);
		}
		if (routine instanceof Function) {
			this.getFunctions().add((Function) routine);
		}
		if (routine instanceof Package) {
			this.getPackages().add((Package) routine);
		}
		if (routine instanceof PackageBody) {
			this.getPackageBodies().add((PackageBody) routine);
		}
	}

	@Override
	public PackageCollection getPackages() {
		return packages;
	}

	@Override
	public PackageBodyCollection getPackageBodies() {
		return packageBodies;
	}

	@Override
	public DbLinkCollection getDbLinks() {
		return dbLinks;
	}

	@Override
	public SequenceCollection getSequences() {
		return sequences;
	}

	@Override
	public SynonymCollection getSynonyms() {
		return synonyms;
	}

	@Override
	public TriggerCollection getTriggers() {
		return triggers;
	}

	/**
	 * @return the productVersionInfo
	 */
	public ProductVersionInfo getProductVersionInfo() {
		if (this.getCatalog()!=null){
			return this.getCatalog().getProductVersionInfo();
		}
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
	 * @return the productVersionInfo
	 */
	protected ProductVersionInfo getParentProductVersionInfo() {
		Catalog parent = this.getAncestor(Catalog.class);
		if (parent == null) {
			return null;
		}
		return parent.getProductVersionInfo();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.ProductInfo#getDbProductName()
	 */
	@Override
	public String getProductName() {
		if (this.getParentProductVersionInfo() != null) {
			return getParentProductVersionInfo().getName();
		}
		return getProductVersionInfo().getName();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.ProductInfo#setDbProductName(java.lang.String)
	 */
	@Override
	public Schema setProductName(String dbProductName) {
		getProductVersionInfo().setName(dbProductName);
		return instance();
	}

	private String getParentProductName() {
		ProductVersionInfo productVersionInfo=this.getParentProductVersionInfo();
		if (productVersionInfo == null) {
			return null;
		}
		return productVersionInfo.getName();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.ProductInfo#getDbMajorVersion()
	 */
	@Override
	public Integer getProductMajorVersion() {
		if (this.getParentProductVersionInfo() != null) {
			return this.getParentProductVersionInfo().getMajorVersion();
		}
		return this.getProductVersionInfo().getMajorVersion();
	}

	private Integer getParentMajorVersion() {
		ProductVersionInfo productVersionInfo=this.getParentProductVersionInfo();
		if (productVersionInfo == null) {
			return null;
		}
		return productVersionInfo.getMajorVersion();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.ProductInfo#setDbMajorVersion(java.lang.Integer)
	 */
	@Override
	public Schema setProductMajorVersion(Integer dbMajorVersion) {
		getProductVersionInfo().setMajorVersion(dbMajorVersion);
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.ProductInfo#getDbMinorVersion()
	 */
	@Override
	public Integer getProductMinorVersion() {
		if (this.getParentProductVersionInfo() != null) {
			return getParentMinorVersion();
		}
		return getProductVersionInfo().getMinorVersion();
	}

	private Integer getParentMinorVersion() {
		ProductVersionInfo productVersionInfo=this.getParentProductVersionInfo();
		if (productVersionInfo == null) {
			return null;
		}
		return productVersionInfo.getMinorVersion();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.ProductInfo#setDbMinorVersion(java.lang.Integer)
	 */
	@Override
	public Schema setProductMinorVersion(Integer dbMinorVersion) {
		getProductVersionInfo().setMinorVersion(dbMinorVersion);
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbProductInfoProperties#getProductRevision()
	 */
	@Override
	public Integer getProductRevision() {
		if (this.getParentProductVersionInfo() != null) {
			return getParentProductRevision();
		}
		return getProductVersionInfo().getRevision();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DbProductInfoProperties#setProductRevision(java
	 * .lang.Integer)
	 */
	@Override
	public Schema setProductRevision(Integer revision) {
		getProductVersionInfo().setRevision(revision);
		return instance();
	}

	private Integer getParentProductRevision() {
		ProductVersionInfo productVersionInfo=this.getParentProductVersionInfo();
		if (productVersionInfo == null) {
			return null;
		}
		return productVersionInfo.getRevision();
	}

	@Override
	public DomainCollection getDomains() {
		return domains;
	}

	@Override
	public TableLinkCollection getTableLinks() {
		return tableLinks;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#getParent()
	 */
	@Override
	public SchemaCollection getParent() {
		return (SchemaCollection) super.getParent();
	}

	/**
	 * @param schemas
	 *            the schemas to set
	 */
	protected void setSchemas(SchemaCollection schemas) {
		this.setParent(schemas);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.CharacterSemanticsProperty#setCharacterSemantics
	 * (com.sqlapp.data.schemas.CharacterSemantics)
	 */
	@Override
	public Schema setCharacterSemantics(CharacterSemantics characterSemantics) {
		this.characterSemantics = characterSemantics;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.CharacterSetProperty#setCharacterSet(java.lang
	 * .String)
	 */
	@Override
	public Schema setCharacterSet(String characterSet) {
		this.characterSet = characterSet;
		return this;
	}

	protected Catalog getCatalog() {
		return this.getAncestor(Catalog.class);
	}

	/**
	 * @param collation
	 *            the collation to set
	 */
	@Override
	public Schema setCollation(String collation) {
		this.collation = collation;
		return this;
	}

	/**
	 * 大文字小文字を無視した比較
	 * 
	 * @param obj1
	 * @param obj2
	 */
	public boolean equalsIgnoreCase(String obj1, String obj2) {
		if (this.isCaseSensitive()) {
			return eq(obj1, obj2);
		}
		return eqIgnoreCase(obj1, obj2);
	}

	/**
	 * caseSensitiveを設定します
	 */
	@Override
	public Schema setCaseSensitive(boolean caseSensitive) {
		if (this.isCaseSensitive() != caseSensitive) {
			for (Map.Entry<String, AbstractSchemaObjectCollection> entry : this.objectMap
					.entrySet()) {
				entry.getValue().setCaseSensitive(caseSensitive);
			}
		}
		super.setCaseSensitive(caseSensitive);
		return (Schema)instance();
	}

	@Override
	public ConstantCollection getConstants() {
		return constants;
	}

	/**
	 * @return the typeBodies
	 */
	@Override
	public TypeBodyCollection getTypeBodies() {
		return typeBodies;
	}

	/**
	 * @return the types
	 */
	@Override
	public TypeCollection getTypes() {
		return types;
	}

	/**
	 * @return the events
	 */
	@Override
	public EventCollection getEvents() {
		return events;
	}

	/**
	 * @return the rules
	 */
	@Override
	public RuleCollection getRules() {
		return rules;
	}

	/**
	 * @return the operators
	 */
	@Override
	public OperatorCollection getOperators() {
		return operators;
	}

	/**
	 * @return the operatorClasses
	 */
	@Override
	public OperatorClassCollection getOperatorClasses() {
		return operatorClasses;
	}

	/**
	 * @return the dimensions
	 */
	@Override
	public DimensionCollection getDimensions() {
		return dimensions;
	}

	protected String getSimpleName() {
		return "schema";
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		if (!CommonUtils.eqIgnoreCase(this.getParentProductName(),
				this.getProductName())) {
			stax.writeAttribute(SchemaProperties.PRODUCT_NAME, this);
		}
		if (!CommonUtils.eq(this.getParentMajorVersion(),
				this.getProductMajorVersion())) {
			stax.writeAttribute(SchemaProperties.PRODUCT_MAJOR_VERSION,	this);
		}
		if (!CommonUtils.eq(this.getParentMinorVersion(),
				this.getProductMinorVersion())) {
			stax.writeAttribute(SchemaProperties.PRODUCT_MINOR_VERSION, this);
		}
		if (!CommonUtils.eq(this.getParentProductRevision(),
				this.getProductRevision())) {
			stax.writeAttribute(SchemaProperties.PRODUCT_REVISION, this);
		}
		//
		stax.writeAttribute(SchemaProperties.TABLE_SPACE_NAME, this);
		stax.writeAttribute(SchemaProperties.INDEX_TABLE_SPACE_NAME, this);
		stax.writeAttribute(SchemaProperties.LOB_TABLE_SPACE_NAME, this);
		stax.writeAttribute(SchemaProperties.TEMPORARY_TABLE_SPACE_NAME, this);
		//
		if (!CommonUtils.eq(SchemaUtils.getParentCharacterSemantics(this), this.getCharacterSemantics())) {
			stax.writeAttribute(SchemaProperties.CHARACTER_SEMANTICS,this);
		}
		if (!CommonUtils.eq(SchemaUtils.getParentCharacterSet(this), this.getCharacterSet())) {
			stax.writeAttribute(SchemaProperties.CHARACTER_SET, this);
		}
		if (!CommonUtils.eq(SchemaUtils.getParentCollation(this), this.getCollation())) {
			stax.writeAttribute(SchemaProperties.COLLATION, this);
		}
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		for (Map.Entry<String, AbstractSchemaObjectCollection> entry : this.objectMap
				.entrySet()) {
			if (CommonUtils.isEmpty(entry.getValue())) {
				continue;
			}
			if (entry.getValue() instanceof TableCollection) {
				TableCollection tables = (TableCollection) entry.getValue();
				tables.writeXml(entry.getKey(), stax);
			} else {
				entry.getValue().writeXml(stax);
			}
		}
		super.writeXmlOptionalValues(stax);
	}

	protected void setSchema(AbstractSchemaObjectCollection<?> obj) {
		if (obj != null) {
			obj.setParent(this);
		}
	}

	protected Schema setTables(TableCollection tables) {
		this.tables = tables;
		setSchema(tables);
		return this;
	}

	protected Schema setExternalTables(ExternalTableCollection externalTables) {
		this.externalTables = externalTables;
		setSchema(externalTables);
		return this;
	}

	protected Schema setXmlSchemas(XmlSchemaCollection xmlSchemas) {
		this.xmlSchemas = xmlSchemas;
		setSchema(xmlSchemas);
		return this;
	}

	protected Schema setConstants(ConstantCollection constants) {
		this.constants = constants;
		setSchema(constants);
		return this;
	}

	protected Schema setMviews(MviewCollection mviews) {
		this.mviews = mviews;
		setSchema(mviews);
		return this;
	}

	public Schema setMviewLogs(MviewLogCollection mviewLogs) {
		this.mviewLogs = mviewLogs;
		setSchema(mviews);
		return this;
	}

	/**
	 * @param views
	 *            the views to set
	 */
	protected Schema setViews(ViewCollection views) {
		this.views = views;
		setSchema(views);
		return this;
	}

	/**
	 * @param rules
	 *            the rules to set
	 */
	protected Schema setRules(RuleCollection rules) {
		this.rules = rules;
		setSchema(rules);
		return this;
	}

	/**
	 * @param procedures
	 *            the procedures to set
	 */
	protected Schema setProcedures(ProcedureCollection procedures) {
		this.procedures = procedures;
		setSchema(procedures);
		return this;
	}

	/**
	 * @param functions
	 *            the functions to set
	 */
	protected Schema setFunctions(FunctionCollection functions) {
		this.functions = functions;
		setSchema(functions);
		return this;
	}

	/**
	 * @param packages
	 *            the packages to set
	 */
	protected Schema setPackages(PackageCollection packages) {
		this.packages = packages;
		setSchema(packages);
		return this;
	}

	/**
	 * @param packageBodies
	 *            the packageBodies to set
	 */
	protected Schema setPackageBodies(PackageBodyCollection packageBodies) {
		this.packageBodies = packageBodies;
		setSchema(packageBodies);
		return this;
	}

	/**
	 * @param triggers
	 *            the triggers to set
	 */
	protected Schema setTriggers(TriggerCollection triggers) {
		this.triggers = triggers;
		setSchema(triggers);
		return this;
	}

	/**
	 * @param sequences
	 *            the sequences to set
	 */
	protected Schema setSequences(SequenceCollection sequences) {
		this.sequences = sequences;
		setSchema(sequences);
		return this;
	}

	/**
	 * @param dbLinks
	 *            the dbLinks to set
	 */
	protected Schema setDbLinks(DbLinkCollection dbLinks) {
		this.dbLinks = dbLinks;
		setSchema(dbLinks);
		return this;
	}

	/**
	 * @param tableLinks
	 *            the tableLinks to set
	 */
	protected Schema setTableLinks(TableLinkCollection tableLinks) {
		this.tableLinks = tableLinks;
		setSchema(tableLinks);
		return this;
	}

	/**
	 * @param synonyms
	 *            the synonyms to set
	 */
	protected Schema setSynonyms(SynonymCollection synonyms) {
		this.synonyms = synonyms;
		setSchema(synonyms);
		return this;
	}

	/**
	 * @param domains
	 *            the domains to set
	 */
	protected Schema setDomains(DomainCollection domains) {
		this.domains = domains;
		setSchema(domains);
		return this;
	}

	/**
	 * @param types
	 *            the types to set
	 */
	protected Schema setTypes(TypeCollection types) {
		this.types = types;
		setSchema(types);
		return this;
	}

	/**
	 * @param typeBodies
	 *            the typeBodies to set
	 */
	protected Schema setTypeBodies(TypeBodyCollection typeBodies) {
		this.typeBodies = typeBodies;
		setSchema(typeBodies);
		return this;
	}

	/**
	 * @param typeBodies
	 *            the operators to set
	 */
	protected Schema setOperators(OperatorCollection operators) {
		this.operators = operators;
		setSchema(operators);
		return this;
	}

	/**
	 * @param operatorClasses
	 *            the operatorClasses to set
	 */
	protected Schema setOperatorClasses(OperatorClassCollection operatorClasses) {
		this.operatorClasses = operatorClasses;
		setSchema(operators);
		return this;
	}

	/**
	 * @param dimensions
	 *            the dimensions to set
	 */
	protected Schema setDimensions(DimensionCollection dimensions) {
		this.dimensions = dimensions;
		setSchema(dimensions);
		return this;
	}

	/**
	 * @param events
	 *            the events to set
	 */
	protected Schema setEvents(EventCollection events) {
		this.events = events;
		setSchema(events);
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#validate()
	 */
	@Override
	protected void validate() {
		super.validate();
		this.getTables().validate();
		this.getTriggers().validate();
		this.getDimensions().validate();
		final DoubleKeyMap<String,String,List<Table>> partitionChildrenMap=CommonUtils.doubleKeyMap();
		for(Table table:this.getTables()){
			if (table.getPartitionParent()!=null) {
				String schemaName=table.getPartitionParent().getTable().getSchemaName();
				if (schemaName==null) {
					schemaName=this.getName();
				}
				List<Table> list=partitionChildrenMap.get(schemaName, table.getPartitionParent().getTable().getName());
				if (list==null) {
					list=CommonUtils.list();
					partitionChildrenMap.put(schemaName, table.getPartitionParent().getTable().getName(), list);
				}
				list.add(table);
			}
			List<ForeignKeyConstraint> fks=table.getConstraints().getForeignKeyConstraints();
			for(ForeignKeyConstraint fk:fks){
				if (CommonUtils.eq(fk.getRelatedTableSchemaName(), table.getSchemaName())){
					Table refTable=this.getTables().get(fk.getRelatedTableName());
					if (refTable!=null){
						refTable.addChildRelation(fk);
					}
				}
			}
		}
		for(Map.Entry<String, Map<String,List<Table>>> entry:partitionChildrenMap.entrySet()) {
			for(Map.Entry<String, List<Table>> entryChild:entry.getValue().entrySet()) {
				if (entry.getKey()==null||CommonUtils.eq(this.getName(), entry.getKey())) {
					Table parentTable=this.getTables().get(entryChild.getKey());
					parentTable.toPartitioning();
					parentTable.getPartitioning().addAllPartitionTable(entryChild.getValue());
				} else {
					Table parentTable=new Table(entryChild.getKey());
					parentTable=SchemaUtils.getTableFromParent(parentTable, this);
					if (parentTable!=null) {
						parentTable.toPartitioning();
						parentTable.getPartitioning().addAllPartitionTable(entryChild.getValue());
					}
				}
			}
		}
	}

	/**
	 * スキーマ内にオブジェクトが存在しないかを返します
	 * 
	 */
	public boolean isEmpty() {
		for (Map.Entry<String, AbstractSchemaObjectCollection> entry : this.objectMap
				.entrySet()) {
			if (!entry.getValue().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 指定したテーブル、マテビュー、ビュー、外部テーブルを取得します。
	 * 
	 * @param name
	 */
	public Table getTable(String name) {
		Table table = this.getTables().get(name);
		if (table != null) {
			return table;
		}
		table = this.getMviews().get(name);
		if (table != null) {
			return table;
		}
		table = this.getViews().get(name);
		if (table != null) {
			return table;
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Sortable#sort()
	 */
	@Override
	public void sort() {
		for (Map.Entry<String, AbstractSchemaObjectCollection> entry : this.objectMap
				.entrySet()) {
			entry.getValue().sort();
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
		for (Map.Entry<String, AbstractSchemaObjectCollection> entry : this.objectMap
				.entrySet()) {
			entry.getValue().sort(comparator);
		}
	}

	@Override
	protected SchemaXmlReaderHandler getDbObjectXmlReaderHandler() {
		return new SchemaXmlReaderHandler();
	}

	/**
	 * @param rowIteratorHandler
	 *            the rowIteratorHandler to set
	 */
	@Override
	public void setRowIteratorHandler(RowIteratorHandler rowIteratorHandler) {
		getTables().setRowIteratorHandler(rowIteratorHandler);
		getViews().setRowIteratorHandler(rowIteratorHandler);
		getMviews().setRowIteratorHandler(rowIteratorHandler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Mergeable#merge(com.sqlapp.data.schemas.
	 * DbCommonObject)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void merge(Schema obj) {
		Map<String, AbstractSchemaObjectCollection> myMap = getChildObjectCollectionMap();
		Map<String, AbstractSchemaObjectCollection> objMap = obj
				.getChildObjectCollectionMap();
		for (Map.Entry<String, AbstractSchemaObjectCollection> entry : objMap
				.entrySet()) {
			AbstractSchemaObjectCollection c = entry.getValue();
			myMap.get(entry.getKey()).addAll(c);
			c.sort();
		}
	}
	
	public Catalog toCatalog(){
		if (this.getParent()!=null){
			return this.getParent().toCatalog();
		}
		Catalog catalog=new Catalog();
		catalog.setDialect(this.getDialect());
		catalog.setProductName(this.getProductName());
		catalog.setProductMajorVersion(this.getProductMajorVersion());
		catalog.setProductMinorVersion(this.getProductMinorVersion());
		catalog.setProductRevision(this.getProductRevision());
		catalog.setCharacterSemantics(this.getCharacterSemantics());
		catalog.setCollation(this.getCollation());
		catalog.setCharacterSet(this.getCharacterSet());
		catalog.getSchemas().add(this);
		return catalog;
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
	public Schema setDialect(Dialect dialect){
		SimpleBeanUtils.setField(this, "dialect", dialect);
		return this;
	}
}
