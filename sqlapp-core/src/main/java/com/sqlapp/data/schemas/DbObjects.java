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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StringUtils;
import com.sqlapp.util.ToStringBuilder;

public enum DbObjects {
	CATALOG(Catalog.class) {
		@Override
		public Catalog newInstance() {
			return new Catalog();
		}
	},
	PUBLIC_SYNONYMS(PublicSynonymCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public PublicSynonymCollection newInstance() {
			return new PublicSynonymCollection();
		}
	},
	PUBLIC_SYNONYM(PublicSynonym.class) {
		@Override
		public DbObjects getCollectionType() {
			return PUBLIC_SYNONYMS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE, VIEW, MVIEW, FUNCTION, PROCEDURE, PACKAGE);
		}

		@Override
		public PublicSynonym newInstance() {
			return new PublicSynonym();
		}
	},
	PUBLIC_DB_LINKS(PublicDbLinkCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public PublicDbLinkCollection newInstance() {
			return new PublicDbLinkCollection();
		}
	},
	PUBLIC_DB_LINK(PublicDbLink.class) {
		@Override
		public DbObjects getCollectionType() {
			return PUBLIC_DB_LINKS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE, VIEW);
		}

		@Override
		public PublicDbLink newInstance() {
			return new PublicDbLink();
		}
	},
	TABLE_SPACE_FILES(TableSpaceFileCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return TABLE_SPACE;
		}

		@Override
		public TableSpaceFileCollection newInstance() {
			return new TableSpaceFileCollection();
		}
	},
	TABLE_SPACE_FILE(TableSpaceFile.class) {
		@Override
		public DbObjects getCollectionType() {
			return TABLE_SPACE_FILES;
		}

		@Override
		public TableSpaceFile newInstance() {
			return new TableSpaceFile();
		}
	},
	TYPE_COLUMNS(TypeColumnCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return TYPE;
		}

		@Override
		public TypeColumnCollection newInstance() {
			return new TypeColumnCollection();
		}
	},
	TYPE_COLUMN(TypeColumn.class) {
		@Override
		public DbObjects getCollectionType() {
			return TYPE_COLUMNS;
		}

		@Override
		public TypeColumn newInstance() {
			return new TypeColumn();
		}
	},
	USERS(UserCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public UserCollection newInstance() {
			return new UserCollection();
		}
	},
	USER(User.class) {
		@Override
		public DbObjects getCollectionType() {
			return USERS;
		}

		@Override
		public User newInstance() {
			return new User();
		}
	},
	ROLES(RoleCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public RoleCollection newInstance() {
			return new RoleCollection();
		}
	},
	ROLE(Role.class) {
		@Override
		public DbObjects getCollectionType() {
			return ROLES;
		}

		@Override
		public Role newInstance() {
			return new Role();
		}
	},
	TABLE_SPACES(TableSpaceCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public TableSpaceCollection newInstance() {
			return new TableSpaceCollection();
		}
	},
	TABLE_SPACE(TableSpace.class) {
		@Override
		public DbObjects getCollectionType() {
			return TABLE_SPACES;
		}

		@Override
		public TableSpace newInstance() {
			return new TableSpace();
		}
	},
	DIRECTORIES(DirectoryCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public DirectoryCollection newInstance() {
			return new DirectoryCollection();
		}
	},
	DIRECTORY(Directory.class) {
		@Override
		public DbObjects getCollectionType() {
			return DIRECTORIES;
		}

		@Override
		public Directory newInstance() {
			return new Directory();
		}
	},
	PARTITION_FUNCTIONS(PartitionFunctionCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public PartitionFunctionCollection newInstance() {
			return new PartitionFunctionCollection();
		}
	},
	PARTITION_FUNCTION(PartitionFunction.class) {
		@Override
		public DbObjects getCollectionType() {
			return PARTITION_FUNCTIONS;
		}

		@Override
		public PartitionFunction newInstance() {
			return new PartitionFunction();
		}
	},
	PARTITION_SCHEMES(PartitionSchemeCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public PartitionSchemeCollection newInstance() {
			return new PartitionSchemeCollection();
		}
	},
	PARTITION_SCHEME(PartitionScheme.class) {
		@Override
		public DbObjects getCollectionType() {
			return PARTITION_SCHEMES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(PARTITION_FUNCTION);
		}

		@Override
		public PartitionScheme newInstance() {
			return new PartitionScheme();
		}
	},
	ASSEMBLIES(AssemblyCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public AssemblyCollection newInstance() {
			return new AssemblyCollection();
		}
	},
	ASSEMBLY(Assembly.class) {
		@Override
		public DbObjects getCollectionType() {
			return ASSEMBLIES;
		}

		@Override
		public Assembly newInstance() {
			return new Assembly();
		}
	},
	ASSEMBLY_FILES(AssemblyFileCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return ASSEMBLY;
		}

		@Override
		public AssemblyFileCollection newInstance() {
			return new AssemblyFileCollection();
		}
	},
	ASSEMBLY_FILE(AssemblyFile.class) {
		@Override
		public DbObjects getCollectionType() {
			return ASSEMBLY_FILES;
		}

		@Override
		public AssemblyFile newInstance() {
			return new AssemblyFile();
		}
	},
	OBJECT_PRIVILEGES(ObjectPrivilegeCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public ObjectPrivilegeCollection newInstance() {
			return new ObjectPrivilegeCollection();
		}
	},
	OBJECT_PRIVILEGE(ObjectPrivilege.class) {
		@Override
		public DbObjects getCollectionType() {
			return OBJECT_PRIVILEGES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE, VIEW, MVIEW, FUNCTION, PROCEDURE, PACKAGE);
		}

		@Override
		public ObjectPrivilege newInstance() {
			return new ObjectPrivilege();
		}
	},
	ROUTINE_PRIVILEGES(RoutinePrivilegeCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public RoutinePrivilegeCollection newInstance() {
			return new RoutinePrivilegeCollection();
		}
	},
	ROUTINE_PRIVILEGE(RoutinePrivilege.class) {
		@Override
		public DbObjects getCollectionType() {
			return ROUTINE_PRIVILEGES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(FUNCTION, PROCEDURE, PACKAGE);
		}

		@Override
		public RoutinePrivilege newInstance() {
			return new RoutinePrivilege();
		}
	},
	COLUMN_PRIVILEGES(ColumnPrivilegeCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public ColumnPrivilegeCollection newInstance() {
			return new ColumnPrivilegeCollection();
		}
	},
	COLUMN_PRIVILEGE(ColumnPrivilege.class) {
		@Override
		public DbObjects getCollectionType() {
			return COLUMN_PRIVILEGES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE, VIEW, MVIEW, COLUMN);
		}

		@Override
		public ColumnPrivilege newInstance() {
			return new ColumnPrivilege();
		}
	},
	USER_PRIVILEGES(UserPrivilegeCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public UserPrivilegeCollection newInstance() {
			return new UserPrivilegeCollection();
		}
	},
	USER_PRIVILEGE(UserPrivilege.class) {
		@Override
		public DbObjects getCollectionType() {
			return USER_PRIVILEGES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(USER);
		}

		@Override
		public UserPrivilege newInstance() {
			return new UserPrivilege();
		}
	},
	ROLE_PRIVILEGES(RolePrivilegeCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public RolePrivilegeCollection newInstance() {
			return new RolePrivilegeCollection();
		}
	},
	ROLE_PRIVILEGE(RolePrivilege.class) {
		@Override
		public DbObjects getCollectionType() {
			return ROLE_PRIVILEGES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(ROLE);
		}

		@Override
		public RolePrivilege newInstance() {
			return new RolePrivilege();
		}
	},
	SCHEMA_PRIVILEGES(SchemaPrivilegeCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public SchemaPrivilegeCollection newInstance() {
			return new SchemaPrivilegeCollection();
		}
	},
	SCHEMA_PRIVILEGE(SchemaPrivilege.class) {
		@Override
		public DbObjects getCollectionType() {
			return SCHEMA_PRIVILEGES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(SCHEMA);
		}

		@Override
		public SchemaPrivilege newInstance() {
			return new SchemaPrivilege();
		}
	},
	ROLE_MEMBERS(RoleMemberCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public RoleMemberCollection newInstance() {
			return new RoleMemberCollection();
		}
	},
	ROLE_MEMBER(RoleMember.class) {
		@Override
		public DbObjects getCollectionType() {
			return ROLE_MEMBERS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(ROLE);
		}

		@Override
		public RoleMember newInstance() {
			return new RoleMember();
		}
	},
	SETTINGS(SettingCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public SettingCollection newInstance() {
			return new SettingCollection();
		}
	},
	SETTING(Setting.class) {
		@Override
		public DbObjects getCollectionType() {
			return SETTINGS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(CATALOG);
		}

		@Override
		public Setting newInstance() {
			return new Setting();
		}
	},
	SCHEMAS(SchemaCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return CATALOG;
		}

		@Override
		public SchemaCollection newInstance() {
			return new SchemaCollection();
		}
	},
	SCHEMA(Schema.class) {
		@Override
		public DbObjects getCollectionType() {
			return SCHEMAS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(CATALOG, TABLE_SPACES);
		}

		@Override
		public Schema newInstance() {
			return new Schema();
		}
	},
	TABLES(TableCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public TableCollection newInstance() {
			return new TableCollection();
		}
	},
	TABLE(Table.class) {
		@Override
		public DbObjects getCollectionType() {
			return TABLES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(DOMAIN, TYPE, PARTITION_SCHEME);
		}

		@Override
		public Table newInstance() {
			return new Table();
		}
	},
	VIEWS(ViewCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public ViewCollection newInstance() {
			return new ViewCollection();
		}
	},
	VIEW(View.class) {
		@Override
		public DbObjects getCollectionType() {
			return VIEWS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE);
		}

		@Override
		public View newInstance() {
			return new View();
		}
	},
	MVIEWS(MviewCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public MviewCollection newInstance() {
			return new MviewCollection();
		}
	},
	MVIEW(Mview.class) {
		@Override
		public DbObjects getCollectionType() {
			return MVIEWS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE);
		}

		@Override
		public Mview newInstance() {
			return new Mview();
		}
	},
	EXTERNAL_TABLES(ExternalTableCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public ExternalTableCollection newInstance() {
			return new ExternalTableCollection();
		}
	},
	EXTERNAL_TABLE(ExternalTable.class) {
		@Override
		public DbObjects getCollectionType() {
			return EXTERNAL_TABLES;
		}

		@Override
		public ExternalTable newInstance() {
			return new ExternalTable();
		}
	},
	MVIEW_LOGS(MviewLogCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public MviewLogCollection newInstance() {
			return new MviewLogCollection();
		}
	},
	MVIEW_LOG(MviewLog.class) {
		@Override
		public DbObjects getCollectionType() {
			return MVIEW_LOGS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE, MVIEW);
		}

		@Override
		public MviewLog newInstance() {
			return new MviewLog();
		}
	},
	MASKS(MaskCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public MaskCollection newInstance() {
			return new MaskCollection();
		}
	},
	MASK(Mask.class) {
		@Override
		public DbObjects getCollectionType() {
			return MASKS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE, MVIEW);
		}

		@Override
		public Mask newInstance() {
			return new Mask();
		}
	},
	PROCEDURES(ProcedureCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public ProcedureCollection newInstance() {
			return new ProcedureCollection();
		}
	},
	PROCEDURE(Procedure.class) {
		@Override
		public DbObjects getCollectionType() {
			return PROCEDURES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE, MVIEW, VIEW, FUNCTION, TYPE);
		}

		@Override
		public Procedure newInstance() {
			return new Procedure();
		}
	},
	FUNCTIONS(FunctionCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public FunctionCollection newInstance() {
			return new FunctionCollection();
		}
	},
	FUNCTION(Function.class) {
		@Override
		public DbObjects getCollectionType() {
			return FUNCTIONS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE, MVIEW, VIEW, PACKAGE, TYPE);
		}

		@Override
		public Function newInstance() {
			return new Function();
		}
	},
	FUNCTION_FAMILIES(FunctionFamilyCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return OPERATOR_CLASS;
		}

		@Override
		public FunctionFamilyCollection newInstance() {
			return new FunctionFamilyCollection();
		}
	},
	FUNCTION_FAMILY(FunctionFamily.class) {
		@Override
		public DbObjects getCollectionType() {
			return FUNCTION_FAMILIES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(FUNCTION_FAMILIES);
		}

		@Override
		public FunctionFamily newInstance() {
			return new FunctionFamily();
		}
	},
	FUNCTION_RETURNING(FunctionReturning.class) {

		@Override
		public DbObjects[] getDepends() {
			return array(FUNCTION);
		}

		@Override
		public FunctionReturning newInstance() {
			return new FunctionReturning();
		}
	},
	FUNCTION_RETURNING_REFERENCE_TABLE(FunctionReturningReferenceTable.class) {

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE);
		}

		@Override
		public FunctionReturningReferenceTable newInstance() {
			return new FunctionReturningReferenceTable();
		}
	},
	NAMED_ARGUMENTS(NamedArgumentCollection.class) {

		@Override
		public boolean isCollection() {
			return true;
		}

		@SuppressWarnings("rawtypes")
		@Override
		public NamedArgumentCollection<?> newInstance() {
			return new NamedArgumentCollection();
		}
	},
	NAMED_ARGUMENT(NamedArgument.class) {
		@Override
		public DbObjects getCollectionType() {
			return NAMED_ARGUMENTS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(NAMED_ARGUMENTS);
		}

		@Override
		public NamedArgument newInstance() {
			return new NamedArgument();
		}
	},
	PACKAGES(PackageCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public PackageCollection newInstance() {
			return new PackageCollection();
		}
	},
	PACKAGE(Package.class) {
		@Override
		public DbObjects getCollectionType() {
			return PACKAGES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(DOMAIN, TYPE);
		}

		@Override
		public Package newInstance() {
			return new Package();
		}
	},
	PACKAGE_BODIES(PackageBodyCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public PackageBodyCollection newInstance() {
			return new PackageBodyCollection();
		}
	},
	PACKAGE_BODY(PackageBody.class) {
		@Override
		public DbObjects getCollectionType() {
			return PACKAGE_BODIES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(PACKAGE, TABLE, MVIEW, VIEW);
		}

		@Override
		public PackageBody newInstance() {
			return new PackageBody();
		}
	},
	TRIGGERS(TriggerCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public TriggerCollection newInstance() {
			return new TriggerCollection();
		}
	},
	TRIGGER(Trigger.class) {
		@Override
		public DbObjects getCollectionType() {
			return TRIGGERS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE, FUNCTION, PROCEDURE);
		}

		@Override
		public Trigger newInstance() {
			return new Trigger();
		}
	},
	SEQUENCES(SequenceCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public SequenceCollection newInstance() {
			return new SequenceCollection();
		}
	},
	SEQUENCE(Sequence.class) {
		@Override
		public DbObjects getCollectionType() {
			return SEQUENCES;
		}

		@Override
		public Sequence newInstance() {
			return new Sequence();
		}
	},
	DB_LINKS(DbLinkCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public DbLinkCollection newInstance() {
			return new DbLinkCollection();
		}
	},
	DB_LINK(DbLink.class) {
		@Override
		public DbObjects getCollectionType() {
			return DB_LINKS;
		}

		@Override
		public DbLink newInstance() {
			return new DbLink();
		}
	},
	TABLE_LINKS(TableLinkCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public TableLinkCollection newInstance() {
			return new TableLinkCollection();
		}
	},
	TABLE_LINK(TableLink.class) {
		@Override
		public DbObjects getCollectionType() {
			return TABLE_LINKS;
		}

		@Override
		public TableLink newInstance() {
			return new TableLink();
		}
	},
	SYNONYMS(SynonymCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public SynonymCollection newInstance() {
			return new SynonymCollection();
		}
	},
	SYNONYM(Synonym.class) {
		@Override
		public DbObjects getCollectionType() {
			return SYNONYMS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE, VIEW, MVIEW, FUNCTION, PROCEDURE);
		}

		@Override
		public Synonym newInstance() {
			return new Synonym();
		}
	},
	DOMAINS(DomainCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public DomainCollection newInstance() {
			return new DomainCollection();
		}
	},
	DOMAIN(Domain.class) {
		@Override
		public DbObjects getCollectionType() {
			return DOMAINS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(CONSTANT);
		}

		@Override
		public Domain newInstance() {
			return new Domain();
		}
	},
	TYPES(TypeCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public TypeCollection newInstance() {
			return new TypeCollection();
		}
	},
	TYPE(Type.class) {
		@Override
		public DbObjects getCollectionType() {
			return TYPES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(DOMAIN);
		}

		@Override
		public Type newInstance() {
			return new Type();
		}
	},
	TYPE_BODIES(TypeBodyCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public TypeBodyCollection newInstance() {
			return new TypeBodyCollection();
		}
	},
	TYPE_BODY(TypeBody.class) {
		@Override
		public DbObjects getCollectionType() {
			return TYPE_BODIES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TYPE);
		}

		@Override
		public TypeBody newInstance() {
			return new TypeBody();
		}
	},
	RULES(RuleCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public RuleCollection newInstance() {
			return new RuleCollection();
		}
	},
	RULE(Rule.class) {
		@Override
		public DbObjects getCollectionType() {
			return RULES;
		}

		@Override
		public Rule newInstance() {
			return new Rule();
		}
	},
	CONSTANTS(ConstantCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public ConstantCollection newInstance() {
			return new ConstantCollection();
		}
	},
	CONSTANT(Constant.class) {
		@Override
		public DbObjects getCollectionType() {
			return CONSTANTS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(SCHEMA);
		}

		@Override
		public Constant newInstance() {
			return new Constant();
		}
	},
	EVENTS(EventCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public EventCollection newInstance() {
			return new EventCollection();
		}
	},
	EVENT(Event.class) {
		@Override
		public DbObjects getCollectionType() {
			return EVENTS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(FUNCTION, PROCEDURE, PACKAGE);
		}

		@Override
		public Event newInstance() {
			return new Event();
		}
	},
	XML_SCHEMAS(XmlSchemaCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public XmlSchemaCollection newInstance() {
			return new XmlSchemaCollection();
		}
	},
	XML_SCHEMA(XmlSchema.class) {
		@Override
		public DbObjects getCollectionType() {
			return XML_SCHEMAS;
		}

		@Override
		public XmlSchema newInstance() {
			return new XmlSchema();
		}
	},
	OPERATORS(OperatorCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public OperatorCollection newInstance() {
			return new OperatorCollection();
		}
	},
	OPERATOR(Operator.class) {
		@Override
		public DbObjects getCollectionType() {
			return OPERATORS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(FUNCTION, DOMAIN, TYPE);
		}

		@Override
		public Operator newInstance() {
			return new Operator();
		}
	},
	OPERATOR_ARGUMENT(OperatorArgument.class) {
		@Override
		public DbObjects[] getDepends() {
			return array(OPERATOR);
		}

		@Override
		public OperatorArgument newInstance() {
			return new OperatorArgument();
		}
	},
	OPERATOR_BINDINGS(OperatorBindingCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return OPERATOR;
		}

		@Override
		public OperatorBindingCollection newInstance() {
			return new OperatorBindingCollection();
		}
	},
	OPERATOR_BINDING(OperatorBinding.class) {
		@Override
		public DbObjects getCollectionType() {
			return OPERATOR_BINDINGS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(OPERATOR_BINDINGS);
		}

		@Override
		public OperatorBinding newInstance() {
			return new OperatorBinding();
		}
	},
	OPERATOR_BINDING_ARGUMENTS(OperatorBindingArgumentCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return OPERATOR_BINDING;
		}

		@Override
		public OperatorBindingArgumentCollection newInstance() {
			return new OperatorBindingArgumentCollection();
		}
	},
	OPERATOR_BINDING_ARGUMENT(OperatorBindingArgument.class) {
		@Override
		public DbObjects getCollectionType() {
			return OPERATOR_BINDING_ARGUMENTS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(OPERATOR_BINDING_ARGUMENTS);
		}

		@Override
		public OperatorBindingArgument newInstance() {
			return new OperatorBindingArgument();
		}
	},
	OPERATOR_FAMILIES(OperatorFamilyCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return OPERATOR_CLASS;
		}

		@Override
		public OperatorFamilyCollection newInstance() {
			return new OperatorFamilyCollection();
		}
	},
	OPERATOR_FAMILY(OperatorFamily.class) {
		@Override
		public DbObjects getCollectionType() {
			return OPERATOR_FAMILIES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(OPERATOR_FAMILIES);
		}

		@Override
		public OperatorFamily newInstance() {
			return new OperatorFamily();
		}
	},
	OPERATOR_CLASSES(OperatorClassCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public OperatorClassCollection newInstance() {
			return new OperatorClassCollection();
		}
	},
	OPERATOR_CLASS(OperatorClass.class) {
		@Override
		public DbObjects getCollectionType() {
			return OPERATOR_CLASSES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(OPERATOR, FUNCTION);
		}

		@Override
		public OperatorClass newInstance() {
			return new OperatorClass();
		}
	},
	DIMENSIONS(DimensionCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return SCHEMA;
		}

		@Override
		public DimensionCollection newInstance() {
			return new DimensionCollection();
		}
	},
	DIMENSION(Dimension.class) {
		@Override
		public DbObjects getCollectionType() {
			return DIMENSIONS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE, VIEW, MVIEW);
		}

		@Override
		public Dimension newInstance() {
			return new Dimension();
		}
	},
	DIMENSION_LEVELS(DimensionLevelCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return DIMENSION;
		}

		@Override
		public DimensionLevelCollection newInstance() {
			return new DimensionLevelCollection();
		}
	},
	DIMENSION_LEVEL(DimensionLevel.class) {
		@Override
		public DbObjects getCollectionType() {
			return DIMENSION_LEVELS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(DIMENSION_LEVELS);
		}

		@Override
		public DimensionLevel newInstance() {
			return new DimensionLevel();
		}
	},
	DIMENSION_LEVEL_COLUMNS(DimensionLevelColumnCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return DIMENSION_LEVEL;
		}

		@Override
		public DimensionLevelColumnCollection newInstance() {
			return new DimensionLevelColumnCollection();
		}
	},
	DIMENSION_LEVEL_COLUMN(DimensionLevelColumn.class) {
		@Override
		public DbObjects getCollectionType() {
			return DIMENSION_LEVEL_COLUMNS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(DIMENSION_LEVEL_COLUMNS);
		}

		@Override
		public DimensionLevelColumn newInstance() {
			return new DimensionLevelColumn();
		}
	},
	DIMENSION_ATTRIBUTES(DimensionAttributeCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return DIMENSION;
		}

		@Override
		public DimensionAttributeCollection newInstance() {
			return new DimensionAttributeCollection();
		}
	},
	DIMENSION_ATTRIBUTE(DimensionAttribute.class) {
		@Override
		public DbObjects getCollectionType() {
			return DIMENSION_ATTRIBUTES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(DIMENSION);
		}

		@Override
		public DimensionAttribute newInstance() {
			return new DimensionAttribute();
		}
	},
	DIMENSION_ATTRIBUTE_COLUMNS(DimensionAttributeColumnCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return DIMENSION_ATTRIBUTE;
		}

		@Override
		public DimensionAttributeColumnCollection newInstance() {
			return new DimensionAttributeColumnCollection();
		}
	},
	DIMENSION_ATTRIBUTE_COLUMN(DimensionAttributeColumn.class) {
		@Override
		public DbObjects getCollectionType() {
			return DIMENSION_ATTRIBUTE_COLUMNS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(DIMENSION_ATTRIBUTE_COLUMNS);
		}

		@Override
		public DimensionAttributeColumn newInstance() {
			return new DimensionAttributeColumn();
		}
	},
	DIMENSION_HIERARCHIES(DimensionHierarchyCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return DIMENSION;
		}

		@Override
		public DimensionHierarchyCollection newInstance() {
			return new DimensionHierarchyCollection();
		}
	},
	DIMENSION_HIERARCHY(DimensionHierarchy.class) {
		@Override
		public DbObjects getCollectionType() {
			return DIMENSION_HIERARCHIES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(DIMENSION_HIERARCHIES);
		}

		@Override
		public DimensionHierarchy newInstance() {
			return new DimensionHierarchy();
		}
	},
	DIMENSION_HIERARCHY_JOIN_KEYS(DimensionHierarchyJoinKeyCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return DIMENSION_HIERARCHY;
		}

		@Override
		public DimensionHierarchyJoinKeyCollection newInstance() {
			return new DimensionHierarchyJoinKeyCollection();
		}
	},
	DIMENSION_HIERARCHY_JOIN_KEY(DimensionHierarchyJoinKey.class) {
		@Override
		public DbObjects getCollectionType() {
			return DIMENSION_HIERARCHY_JOIN_KEYS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(DIMENSION_HIERARCHY_JOIN_KEYS);
		}

		@Override
		public DimensionHierarchyJoinKey newInstance() {
			return new DimensionHierarchyJoinKey();
		}
	},
	DIMENSION_HIERARCHY_JOIN_KEY_COLUMNS(DimensionHierarchyJoinKeyColumnCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return DIMENSION_HIERARCHY_JOIN_KEY;
		}

		@Override
		public DimensionHierarchyJoinKeyColumnCollection newInstance() {
			return new DimensionHierarchyJoinKeyColumnCollection();
		}
	},
	DIMENSION_HIERARCHY_JOIN_KEY_COLUMN(DimensionHierarchyJoinKeyColumn.class) {
		@Override
		public DbObjects getCollectionType() {
			return DIMENSION_HIERARCHY_JOIN_KEY_COLUMNS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(DIMENSION_HIERARCHY_JOIN_KEY_COLUMNS);
		}

		@Override
		public DimensionHierarchyJoinKeyColumn newInstance() {
			return new DimensionHierarchyJoinKeyColumn();
		}
	},
	DIMENSION_HIERARCHY_LEVELS(DimensionHierarchyLevelCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return DIMENSION_HIERARCHY;
		}

		@Override
		public DimensionHierarchyLevelCollection newInstance() {
			return new DimensionHierarchyLevelCollection();
		}
	},
	DIMENSION_HIERARCHY_LEVEL(DimensionHierarchyLevel.class) {
		@Override
		public DbObjects getCollectionType() {
			return DIMENSION_HIERARCHY_LEVELS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(DIMENSION_HIERARCHY_LEVELS);
		}

		@Override
		public DimensionHierarchyLevel newInstance() {
			return new DimensionHierarchyLevel();
		}
	},
	INDEXES(IndexCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return TABLE;
		}

		@Override
		public IndexCollection newInstance() {
			return new IndexCollection();
		}
	},
	INDEX(Index.class) {
		@Override
		public DbObjects getCollectionType() {
			return INDEXES;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE, VIEW, MVIEW, FUNCTION, PACKAGE);
		}

		@Override
		public Index newInstance() {
			return new Index();
		}
	},
	UNIQUE_CONSTRAINTS("constraints", ConstraintCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return TABLE;
		}

		@Override
		public ConstraintCollection newInstance() {
			return new ConstraintCollection();
		}
	},
	UNIQUE_CONSTRAINT(UniqueConstraint.class) {
		@Override
		public DbObjects getCollectionType() {
			return UNIQUE_CONSTRAINTS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE, FUNCTION, PACKAGE, COLUMN);
		}

		@Override
		public UniqueConstraint newInstance() {
			return new UniqueConstraint();
		}
	},
	FOREIGN_KEY_CONSTRAINTS("constraints", ConstraintCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return TABLE;
		}

		@Override
		public ConstraintCollection newInstance() {
			return new ConstraintCollection();
		}
	},
	FOREIGN_KEY_CONSTRAINT(ForeignKeyConstraint.class) {
		@Override
		public DbObjects getCollectionType() {
			return FOREIGN_KEY_CONSTRAINTS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE, UNIQUE_CONSTRAINT, COLUMN);
		}

		@Override
		public ForeignKeyConstraint newInstance() {
			return new ForeignKeyConstraint();
		}
	},
	CHECK_CONSTRAINTS("constraints", ConstraintCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return TABLE;
		}

		@Override
		public ConstraintCollection newInstance() {
			return new ConstraintCollection();
		}
	},
	CHECK_CONSTRAINT(CheckConstraint.class) {
		@Override
		public DbObjects getCollectionType() {
			return CHECK_CONSTRAINTS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE, COLUMN);
		}

		@Override
		public CheckConstraint newInstance() {
			return new CheckConstraint();
		}
	},
	EXCLUDE_CONSTRAINTS("constraints", ConstraintCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return TABLE;
		}

		@Override
		public ConstraintCollection newInstance() {
			return new ConstraintCollection();
		}
	},
	EXCLUDE_CONSTRAINT(ExcludeConstraint.class) {
		@Override
		public DbObjects getCollectionType() {
			return EXCLUDE_CONSTRAINTS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(EXCLUDE_CONSTRAINTS);
		}

		@Override
		public ExcludeConstraint newInstance() {
			return new ExcludeConstraint();
		}
	},
	COLUMNS(ColumnCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return TABLE;
		}

		@Override
		public ColumnCollection newInstance() {
			return new ColumnCollection();
		}
	},
	COLUMN(Column.class) {
		@Override
		public DbObjects getCollectionType() {
			return COLUMNS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(DOMAIN, TYPE);
		}

		@Override
		public Column newInstance() {
			return new Column();
		}
	},
	PARTITION_PARENT(PartitionParent.class) {

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE);
		}

		@Override
		public PartitionParent newInstance() {
			return new PartitionParent();
		}
	},
	PARTITIONING(Partitioning.class) {

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE);
		}

		@Override
		public Partitioning newInstance() {
			return new Partitioning();
		}
	},
	PARTITIONS(PartitionCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return TABLE;
		}

		@Override
		public PartitionCollection newInstance() {
			return new PartitionCollection();
		}
	},
	PARTITION(Partition.class) {
		@Override
		public DbObjects getCollectionType() {
			return PARTITIONS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(TABLE);
		}

		@Override
		public Partition newInstance() {
			return new Partition();
		}
	},
	SUB_PARTITIONS(SubPartitionCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return TABLE;
		}

		@Override
		public SubPartitionCollection newInstance() {
			return new SubPartitionCollection();
		}
	},
	SUB_PARTITION(SubPartition.class) {
		@Override
		public DbObjects getCollectionType() {
			return SUB_PARTITIONS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(PARTITION);
		}

		@Override
		public SubPartition newInstance() {
			return new SubPartition();
		}
	},
	ROWS(RowCollection.class) {
		@Override
		public boolean isCollection() {
			return true;
		}

		@Override
		public DbObjects getParentType() {
			return TABLE;
		}

		@Override
		public RowCollection newInstance() {
			return new RowCollection();
		}
	},
	ROW(Row.class) {
		@Override
		public DbObjects getCollectionType() {
			return ROWS;
		}

		@Override
		public DbObjects[] getDepends() {
			return array(ROWS);
		}

		@Override
		public Row newInstance() {
			return new Row();
		}
	},;

	@SuppressWarnings("rawtypes")
	private final Class<? extends DbCommonObject> type;

	private final String label;

	@SuppressWarnings("rawtypes")
	private DbObjects(final Class<? extends DbCommonObject> type) {
		this.type = type;
		this.label = StringUtils.snakeToCamel(this.name());
	}

	@SuppressWarnings("rawtypes")
	private DbObjects(final String label, final Class<? extends DbCommonObject> type) {
		this.type = type;
		this.label = label;
	}

	public String getCamelCase() {
		return label;
	}

	public String getSnakeCase() {
		return this.name().toLowerCase();
	}

	public String getCamelCaseNameLabel() {
		return getCamelCase() + "Name";
	}

	public String getSnakeCaseNameLabel() {
		return getSnakeCase() + "_name";
	}

	@SuppressWarnings("rawtypes")
	public Class<? extends DbCommonObject> getType() {
		return type;
	}

	public boolean isCollection() {
		return false;
	}

	public DbObjects getCollectionType() {
		return null;
	}

	public DbObjects getParentType() {
		return getCollectionType();
	}

	public static List<DbObjects> getCreateOrders() {
		final List<DbObjects> list = CommonUtils.list();
		for (final DbObjects enm : values()) {
			if (enm.isCollection()) {
				continue;
			}
			list.add(enm);
		}
		final DbObjectsComparator comp = new DbObjectsComparator();
		for (int i = 0; i < list.size() - 1; i++) {
			for (int j = i + 1; j < list.size(); j++) {
				final DbObjects val1 = list.get(i);
				final DbObjects val2 = list.get(j);
				final int cnt = comp.compare(val1, val2);
				if (cnt > 0) {
					swap(list, i, j);
				}
			}

		}
		return list;
	}

	private static final Map<String, DbObjects> nameCache = new ConcurrentHashMap<>();

	private static final Map<Class<?>, DbObjects> typeCache = new ConcurrentHashMap<>();

	public static DbObjects getByName(final String name) {
		if (nameCache.size() == 0) {
			for (final DbObjects enm : values()) {
				nameCache.put(enm.getCamelCase(), enm);
				nameCache.put(enm.getSnakeCase(), enm);
				nameCache.put(enm.getType().getSimpleName(), enm);
			}
		}
		return nameCache.get(name);
	}

	public static DbObjects getByName(final Class<?> type) {
		if (typeCache.size() == 0) {
			for (final DbObjects enm : values()) {
				typeCache.put(enm.getType(), enm);
			}
		}
		return typeCache.get(type);
	}

	public static List<DbObjects> getDropOrders() {
		final List<DbObjects> list = CommonUtils.list();
		for (final DbObjects enm : values()) {
			if (enm.isCollection()) {
				continue;
			}
			list.add(enm);
		}
		final DbObjectsComparator comp = new DbObjectsComparator();
		for (int i = 0; i < list.size() - 1; i++) {
			for (int j = i + 1; j < list.size(); j++) {
				final DbObjects val1 = list.get(i);
				final DbObjects val2 = list.get(j);
				final int cnt = -comp.compare(val1, val2);
				if (cnt > 0) {
					swap(list, i, j);
				}
			}

		}
		return list;
	}

	private static void swap(final List<DbObjects> list, final int i, final int j) {
		final DbObjects val1 = list.get(i);
		final DbObjects val2 = list.get(j);
		list.set(i, val2);
		list.set(j, val1);
	}

	private List<DbObjects> getAllDepends() {
		if (CommonUtils.isEmpty(this.getDepends())) {
			return Collections.emptyList();
		}
		final List<DbObjects> list = CommonUtils.list();
		createAllDepends(this, list);
		return list;
	}

	private void createAllDepends(final DbObjects current, final List<DbObjects> list) {
		if (CommonUtils.isEmpty(current.getDepends())) {
			return;
		}
		for (final DbObjects enm : current.getDepends()) {
			if (!list.contains(enm)) {
				if (!enm.isCollection()) {
					list.add(enm);
				}
			}
			createAllDepends(enm, list);
		}
	}

	private List<DbObjects> getAncestors() {
		if (CommonUtils.isEmpty(this.getParentType())) {
			return Collections.emptyList();
		}
		final List<DbObjects> list = CommonUtils.list();
		createAncestors(this.getParentType(), list);
		return list;
	}

	private void createAncestors(final DbObjects current, final List<DbObjects> list) {
		if (!CommonUtils.isEmpty(current.getParentType())) {
			if (!current.getParentType().isCollection()) {
				list.add(current.getParentType());
			}
			createAncestors(current.getParentType(), list);
		}
	}

	public DbObjects[] getDepends() {
		return null;
	}

	public DbCommonObject<?> newInstance() {
		return null;
	}

	private static final DbObjects[] array(final DbObjects... args) {
		return args;
	}

	static class SortableHolder implements Comparable<SortableHolder> {
		private int point;
		private final DbObjects dbObjects;

		SortableHolder(final DbObjects dbObjects) {
			this.dbObjects = dbObjects;
		}

		public void add(final int cnt) {
			this.point = this.point + cnt;
		}

		public void setPoint(final int point) {
			this.point = point;
		}

		public int getPoint() {
			return this.point;
		}

		public DbObjects getDbObjects() {
			return dbObjects;
		}

		@Override
		public int compareTo(final SortableHolder o) {
			return this.point - o.point;
		}

		@Override
		public String toString() {
			final ToStringBuilder builder = new ToStringBuilder();
			builder.add("dbObjects", dbObjects);
			builder.add("point", point);
			return builder.toString();
		}
	}

	static class DbObjectsComparator implements Comparator<DbObjects> {
		@Override
		public int compare(final DbObjects o1, final DbObjects o2) {
			final List<DbObjects> o1Ans = o1.getAncestors();
			final List<DbObjects> o2Ans = o2.getAncestors();
			int index1 = o1Ans.indexOf(o2);
			int index2 = o2Ans.indexOf(o1);
			int comp = indexCompare(index1, index2);
			if (comp != 0) {
				return comp * 1000;
			}
			final List<DbObjects> o1Dep = o1.getAllDepends();
			final List<DbObjects> o2Dep = o2.getAllDepends();
			if (o1Dep.isEmpty()) {
				if (o2Dep.isEmpty()) {
					return compare(o1.toString(), o2.toString());
				} else {
					return -1;
				}
			} else {
				if (o2Dep.isEmpty()) {
					return 10;
				}
				if (!o1Dep.contains(o2)) {
					if (!o2Dep.contains(o1)) {
						return o1Dep.size() - o2Dep.size();
					} else {
						return -1000;
					}
				} else {
					if (!o2Dep.contains(o1)) {
						return 1000;
					}
					index1 = o1Dep.indexOf(o2);
					index2 = o2Dep.indexOf(o1);
					comp = indexCompare(index1, index2);
					if (comp != 0) {
						return comp * 100;
					}
					return compare(o1.toString(), o2.toString());
				}
			}
		}

		private int indexCompare(final int index1, final int index2) {
			if (index1 >= 0) {
				if (index2 >= 0) {
					final int comp = (index1 - index2);
					if (comp != 0) {
						return comp * 1000;
					}
					return 0;
				} else {
					return 1;
				}
			} else {
				if (index2 >= 0) {
					return -1;
				} else {
					return 0;
				}
			}
		}

		private int compare(final String val1, final String val2) {
			final int comp = val1.compareTo(val2);
			if (comp > 0) {
				return 1;
			} else if (comp < 0) {
				return -1;
			}
			return 0;
		}
	}
}
