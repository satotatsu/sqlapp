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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StringUtils;
import com.sqlapp.util.ToStringBuilder;

public enum DbObjects {
	CATALOG(Catalog.class),
	PUBLIC_SYNONYMS(PublicSynonymCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	PUBLIC_SYNONYM(PublicSynonym.class){
		@Override
		public DbObjects getCollectionType(){
			return PUBLIC_SYNONYMS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE,VIEW,MVIEW,FUNCTION,PROCEDURE,PACKAGE);
		}
	},
	PUBLIC_DB_LINKS(PublicDbLinkCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	PUBLIC_DB_LINK(PublicDbLink.class){
		@Override
		public DbObjects getCollectionType(){
			return PUBLIC_DB_LINKS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE,VIEW);
		}
	},
	USERS(UserCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	USER(User.class){
		@Override
		public DbObjects getCollectionType(){
			return USERS;
		}
	},
	ROLES(RoleCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	ROLE(Role.class){
		@Override
		public DbObjects getCollectionType(){
			return ROLES;
		}
	},
	TABLE_SPACES(TableSpaceCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	TABLE_SPACE(TableSpace.class){
		@Override
		public DbObjects getCollectionType(){
			return TABLE_SPACES;
		}
	},
	DIRECTORIES(DirectoryCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	DIRECTORY(Directory.class){
		@Override
		public DbObjects getCollectionType(){
			return DIRECTORIES;
		}
	},
	PARTITION_FUNCTIONS(PartitionFunctionCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	PARTITION_FUNCTION(PartitionFunction.class){
		@Override
		public DbObjects getCollectionType(){
			return PARTITION_FUNCTIONS;
		}
	},
	PARTITION_SCHEMES(PartitionSchemeCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	PARTITION_SCHEME(PartitionScheme.class){
		@Override
		public DbObjects getCollectionType(){
			return PARTITION_SCHEMES;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(PARTITION_FUNCTION);
		}
	},
	ASSEMBLIES(AssemblyCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	ASSEMBLY(Assembly.class){
		@Override
		public DbObjects getCollectionType(){
			return ASSEMBLIES;
		}
	},
	OBJECT_PRIVILEGES(ObjectPrivilegeCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	OBJECT_PRIVILEGE(ObjectPrivilege.class){
		@Override
		public DbObjects getCollectionType(){
			return OBJECT_PRIVILEGES;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE,VIEW,MVIEW,FUNCTION,PROCEDURE,PACKAGE);
		}
	},
	ROUTINE_PRIVILEGES(RoutinePrivilegeCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	ROUTINE_PRIVILEGE(RoutinePrivilege.class){
		@Override
		public DbObjects getCollectionType(){
			return ROUTINE_PRIVILEGES;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(FUNCTION,PROCEDURE,PACKAGE);
		}
	},
	COLUMN_PRIVILEGES(ColumnPrivilegeCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	COLUMN_PRIVILEGE(ObjectPrivilege.class){
		@Override
		public DbObjects getCollectionType(){
			return COLUMN_PRIVILEGES;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE,VIEW,MVIEW,COLUMN);
		}
	},
	USER_PRIVILEGES(UserPrivilegeCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	USER_PRIVILEGE(UserPrivilege.class){
		@Override
		public DbObjects getCollectionType(){
			return USER_PRIVILEGES;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(USER);
		}
	},
	ROLE_PRIVILEGES(RolePrivilegeCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	ROLE_PRIVILEGE(RolePrivilege.class){
		@Override
		public DbObjects getCollectionType(){
			return ROLE_PRIVILEGES;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(ROLE);
		}
	},
	SCHEMA_PRIVILEGES(SchemaPrivilegeCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	SCHEMA_PRIVILEGE(SchemaPrivilege.class){
		@Override
		public DbObjects getCollectionType(){
			return SCHEMA_PRIVILEGES;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(SCHEMA);
		}
	},
	ROLE_MEMBERS(RoleMemberCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	ROLE_MEMBER(RoleMember.class){
		@Override
		public DbObjects getCollectionType(){
			return ROLE_MEMBERS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(ROLE);
		}
	},
	SETTINGS(SettingCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	SETTING(Setting.class){
		@Override
		public DbObjects getCollectionType(){
			return SETTINGS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(CATALOG);
		}
	},
	SCHEMAS(SchemaCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return CATALOG;
		}
	},
	SCHEMA(Schema.class){
		@Override
		public DbObjects getCollectionType(){
			return SCHEMAS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(CATALOG, TABLE_SPACES);
		}
	},
	TABLES(TableCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	TABLE(Table.class){
		@Override
		public DbObjects getCollectionType(){
			return TABLES;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(DOMAIN,TYPE,PARTITION_SCHEME);
		}
	},
	VIEWS(ViewCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	VIEW(View.class){
		@Override
		public DbObjects getCollectionType(){
			return VIEWS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE);
		}
	},
	MVIEWS(MviewCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	MVIEW(Mview.class){
		@Override
		public DbObjects getCollectionType(){
			return MVIEWS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE);
		}
	},
	EXTERNAL_TABLES(ExternalTableCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	EXTERNAL_TABLE(ExternalTable.class){
		@Override
		public DbObjects getCollectionType(){
			return EXTERNAL_TABLES;
		}
	},
	MVIEW_LOGS(MviewLogCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	MVIEW_LOG(MviewLog.class){
		@Override
		public DbObjects getCollectionType(){
			return MVIEW_LOGS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE, MVIEW);
		}
	},
	MASKS(MaskCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	MASK(Mask.class){
		@Override
		public DbObjects getCollectionType(){
			return MASKS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE, MVIEW);
		}
	},
	PROCEDURES(ProcedureCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	PROCEDURE(Procedure.class){
		@Override
		public DbObjects getCollectionType(){
			return PROCEDURES;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE, MVIEW, VIEW, FUNCTION, TYPE);
		}
	},
	FUNCTIONS(FunctionCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	FUNCTION(Function.class){
		@Override
		public DbObjects getCollectionType(){
			return FUNCTIONS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE, MVIEW, VIEW, PACKAGE, TYPE);
		}
	},
	PACKAGES(PackageCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	PACKAGE(Package.class){
		@Override
		public DbObjects getCollectionType(){
			return PACKAGES;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(DOMAIN, TYPE);
		}
	},
	PACKAGE_BODIES(PackageBodyCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	PACKAGE_BODY(Package.class){
		@Override
		public DbObjects getCollectionType(){
			return PACKAGE_BODIES;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(PACKAGE, TABLE, MVIEW, VIEW);
		}
	},
	TRIGGERS(TriggerCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	TRIGGER(Trigger.class){
		@Override
		public DbObjects getCollectionType(){
			return TRIGGERS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE, FUNCTION, PROCEDURE);
		}
	},
	SEQUENCES(SequenceCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	SEQUENCE(Sequence.class){
		@Override
		public DbObjects getCollectionType(){
			return SEQUENCES;
		}
	},
	DB_LINKS(DbLinkCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	DB_LINK(DbLink.class){
		@Override
		public DbObjects getCollectionType(){
			return DB_LINKS;
		}
	},
	TABLE_LINKS(TableLinkCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	TABLE_LINK(TableLink.class){
		@Override
		public DbObjects getCollectionType(){
			return TABLE_LINKS;
		}
	},
	SYNONYMS(SynonymCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	SYNONYM(Synonym.class){
		@Override
		public DbObjects getCollectionType(){
			return SYNONYMS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE, VIEW, MVIEW, FUNCTION, PROCEDURE);
		}
	},
	DOMAINS(DomainCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	DOMAIN(Domain.class){
		@Override
		public DbObjects getCollectionType(){
			return DOMAINS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(CONSTANT);
		}
	},
	TYPES(TypeCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	TYPE(Type.class){
		@Override
		public DbObjects getCollectionType(){
			return TYPES;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(DOMAIN);
		}
	},
	TYPE_BODIES(TypeBodyCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	TYPE_BODY(TypeBody.class){
		@Override
		public DbObjects getCollectionType(){
			return TYPE_BODIES;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TYPE);
		}
	},
	RULES(RuleCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	RULE(Rule.class){
		@Override
		public DbObjects getCollectionType(){
			return RULES;
		}
	},
	CONSTANTS(ConstantCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	CONSTANT(Constant.class){
		@Override
		public DbObjects getCollectionType(){
			return CONSTANTS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(SCHEMA);
		}
	},
	EVENTS(EventCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	EVENT(Event.class){
		@Override
		public DbObjects getCollectionType(){
			return EVENTS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(FUNCTION,PROCEDURE,PACKAGE);
		}
	},
	XML_SCHEMAS(XmlSchemaCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	XML_SCHEMA(XmlSchema.class){
		@Override
		public DbObjects getCollectionType(){
			return XML_SCHEMAS;
		}
	},
	OPERATORS(OperatorCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	OPERATOR(Operator.class){
		@Override
		public DbObjects getCollectionType(){
			return OPERATORS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(FUNCTION,DOMAIN,TYPE);
		}
	},
	OPERATOR_CLASSES(OperatorClassCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	OPERATOR_CLASS(OperatorClass.class){
		@Override
		public DbObjects getCollectionType(){
			return OPERATOR_CLASSES;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(OPERATOR, FUNCTION);
		}
	},
	DIMENSIONS(DimensionCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return SCHEMA;
		}
	},
	DIMENSION(Dimension.class){
		@Override
		public DbObjects getCollectionType(){
			return DIMENSIONS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE, VIEW, MVIEW);
		}
	},
	INDEXES(IndexCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return TABLE;
		}
	},
	INDEX(Index.class){
		@Override
		public DbObjects getCollectionType(){
			return INDEXES;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE, VIEW, MVIEW, FUNCTION, PACKAGE);
		}
	},
	UNIQUE_CONSTRAINTS("constraints", ConstraintCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return TABLE;
		}
	},
	UNIQUE_CONSTRAINT(UniqueConstraint.class){
		@Override
		public DbObjects getCollectionType(){
			return UNIQUE_CONSTRAINTS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE, FUNCTION, PACKAGE,COLUMN);
		}
	},
	FOREIGN_KEY_CONSTRAINTS("constraints", ConstraintCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return TABLE;
		}
	},
	FOREIGN_KEY_CONSTRAINT(ForeignKeyConstraint.class){
		@Override
		public DbObjects getCollectionType(){
			return FOREIGN_KEY_CONSTRAINTS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE, UNIQUE_CONSTRAINT,COLUMN);
		}
	},
	CHECK_CONSTRAINTS("constraints", ConstraintCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return TABLE;
		}
	},
	CHECK_CONSTRAINT(CheckConstraint.class){
		@Override
		public DbObjects getCollectionType(){
			return CHECK_CONSTRAINTS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE,COLUMN);
		}
	},
	EXCLUDE_CONSTRAINTS("constraints", ConstraintCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return TABLE;
		}
	},
	EXCLUDE_CONSTRAINT(ForeignKeyConstraint.class){
		@Override
		public DbObjects getCollectionType(){
			return EXCLUDE_CONSTRAINTS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE,COLUMN);
		}
	},
	COLUMNS(ColumnCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return TABLE;
		}
	},
	COLUMN(Column.class){
		@Override
		public DbObjects getCollectionType(){
			return COLUMNS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(DOMAIN,TYPE);
		}
	},
	PARTITIONS(PartitionCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return TABLE;
		}
	},
	PARTITION(Partition.class){
		@Override
		public DbObjects getCollectionType(){
			return PARTITIONS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(TABLE);
		}
	},
	SUB_PARTITIONS(SubPartitionCollection.class){
		@Override
		public boolean isCollection(){
			return true;
		}
		@Override
		public DbObjects getParentType(){
			return TABLE;
		}
	},
	SUB_PARTITION(SubPartition.class){
		@Override
		public DbObjects getCollectionType(){
			return SUB_PARTITIONS;
		}
		@Override
		public DbObjects[] getDepends(){
			return array(PARTITION);
		}
	},
	;

	private final Class<?> type;

	private final String label;

	private DbObjects(final Class<?> type){
		this.type=type;
		this.label=StringUtils.snakeToCamel(this.name());
	}

	private DbObjects(final String label, final Class<?> type){
		this.type=type;
		this.label=label;
	}

	
	public String getCamelCase(){
		return label;
	}

	public String getSnakeCase(){
		return this.name().toLowerCase();
	}

	public String getCamelCaseNameLabel(){
		return getCamelCase()+"Name";
	}

	public String getSnakeCaseNameLabel(){
		return getSnakeCase()+"_name";
	}

	
	public Class<?> getType(){
		return type;
	}

	public boolean isCollection(){
		return false;
	}

	public DbObjects getCollectionType(){
		return null;
	}

	public DbObjects getParentType(){
		return getCollectionType();
	}
	
	public static List<DbObjects> getCreateOrders(){
		final List<DbObjects> list=CommonUtils.list();
		for(final DbObjects enm:values()){
			if (enm.isCollection()){
				continue;
			}
			list.add(enm);
		}
		final DbObjectsComparator comp=new DbObjectsComparator();
		for(int i=0;i<list.size()-1;i++){
			for(int j=i+1;j<list.size();j++){
				final DbObjects val1=list.get(i);
				final DbObjects val2=list.get(j);
				final int cnt=comp.compare(val1, val2);
				if (cnt>0){
					swap(list, i,j);
				}
			}
			
		}
		return list;
	}

	public static List<DbObjects> getDropOrders(){
		final List<DbObjects> list=CommonUtils.list();
		for(final DbObjects enm:values()){
			if (enm.isCollection()){
				continue;
			}
			list.add(enm);
		}
		final DbObjectsComparator comp=new DbObjectsComparator();
		for(int i=0;i<list.size()-1;i++){
			for(int j=i+1;j<list.size();j++){
				final DbObjects val1=list.get(i);
				final DbObjects val2=list.get(j);
				final int cnt=-comp.compare(val1, val2);
				if (cnt>0){
					swap(list, i,j);
				}
			}
			
		}
		return list;
	}

	
	private static void swap(final List<DbObjects> list, final int i, final int j){
		final DbObjects val1=list.get(i);
		final DbObjects val2=list.get(j);
		list.set(i, val2);
		list.set(j, val1);
	}
	
	private List<DbObjects> getAllDepends(){
		if (CommonUtils.isEmpty(this.getDepends())){
			return Collections.emptyList();
		}
		final List<DbObjects> list=CommonUtils.list();
		createAllDepends(this, list);
		return list;
	}

	private void createAllDepends(final DbObjects current, final List<DbObjects> list){
		if (CommonUtils.isEmpty(current.getDepends())){
			return;
		}
		for(final DbObjects enm:current.getDepends()){
			if (!list.contains(enm)){
				if (!enm.isCollection()){
					list.add(enm);
				}
			}
			createAllDepends(enm, list);
		}
	}

	private List<DbObjects> getAncestors(){
		if (CommonUtils.isEmpty(this.getParentType())){
			return Collections.emptyList();
		}
		final List<DbObjects> list=CommonUtils.list();
		createAncestors(this.getParentType(), list);
		return list;
	}

	private void createAncestors(final DbObjects current, final List<DbObjects> list){
		if (!CommonUtils.isEmpty(current.getParentType())){
			if (!current.getParentType().isCollection()){
				list.add(current.getParentType());
			}
			createAncestors(current.getParentType(), list);
		}
	}

	
	public DbObjects[] getDepends(){
		return null;
	}

	private static final DbObjects[] array(final DbObjects... args){
		return args;
	}
	
	static class SortableHolder implements Comparable<SortableHolder>{
		private int point;
		private final DbObjects dbObjects;
		SortableHolder(final DbObjects dbObjects){
			this.dbObjects=dbObjects;
		}
		
		public void add(final int cnt){
			this.point=this.point+cnt;
		}
		
		public void setPoint(final int point){
			this.point=point;
		}

		public int getPoint(){
			return this.point;
		}

		public DbObjects getDbObjects(){
			return dbObjects;
		}
		@Override
		public int compareTo(final SortableHolder o) {
			return this.point-o.point;
		}
		
		@Override
		public String toString(){
			final ToStringBuilder builder=new ToStringBuilder();
			builder.add("dbObjects", dbObjects);
			builder.add("point", point);
			return builder.toString();
		}
	}

	static class DbObjectsComparator implements Comparator<DbObjects>{
		@Override
		public int compare(final DbObjects o1, final DbObjects o2) {
			final List<DbObjects> o1Ans=o1.getAncestors();
			final List<DbObjects> o2Ans=o2.getAncestors();
			int index1=o1Ans.indexOf(o2);
			int index2=o2Ans.indexOf(o1);
			int comp=indexCompare(index1, index2);
			if (comp!=0){
				return comp*1000;
			}
			final List<DbObjects> o1Dep=o1.getAllDepends();
			final List<DbObjects> o2Dep=o2.getAllDepends();
			if (o1Dep.isEmpty()){
				if (o2Dep.isEmpty()){
					return compare(o1.toString(), o2.toString());
				} else{
					return -1;
				}
			} else{
				if (o2Dep.isEmpty()){
					return 10;
				}
				if (!o1Dep.contains(o2)){
					if (!o2Dep.contains(o1)){
						return o1Dep.size()-o2Dep.size();
					} else{
						return -1000;
					}
				} else{
					if (!o2Dep.contains(o1)){
						return 1000;
					}
					index1=o1Dep.indexOf(o2);
					index2=o2Dep.indexOf(o1);
					comp=indexCompare(index1, index2);
					if (comp!=0){
						return comp*100;
					}
					return compare(o1.toString(), o2.toString());
				}
			}
		}
		
		private int indexCompare(final int index1,final int index2){
			if (index1>=0){
				if (index2>=0){
					final int comp= (index1-index2);
					if (comp!=0){
						return comp*1000;
					}
					return 0;
				} else{
					return 1;
				}
			} else{
				if (index2>=0){
					return -1;
				} else{
					return 0;
				}
			}
		}
		
		private int compare(final String val1, final String val2){
			final int comp=val1.compareTo(val2);
			if (comp>0){
				return 1;
			}else if (comp<0){
				return -1;
			}
			return 0;
		}
	}
}
