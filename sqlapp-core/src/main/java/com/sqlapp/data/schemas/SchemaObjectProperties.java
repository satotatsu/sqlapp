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

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.schemas.properties.ColumnPrivilegesGetter;
import com.sqlapp.data.schemas.properties.ColumnPrivilegesProperty;
import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.data.schemas.properties.IncludeColumnsGetter;
import com.sqlapp.data.schemas.properties.IncludeColumnsProperty;
import com.sqlapp.data.schemas.properties.PartitioningProperty;
import com.sqlapp.data.schemas.properties.SchemaPrivilegesGetter;
import com.sqlapp.data.schemas.properties.SchemaPrivilegesProperty;
import com.sqlapp.data.schemas.properties.object.*;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StringUtils;
import com.sqlapp.util.xml.StaxElementHandler;

public enum SchemaObjectProperties implements ISchemaProperty {
	ASSEMBLY_FILES{
		@Override
		public final Class<?> getPropertyClass(){
			return AssemblyFilesProperty.class;
		}
		@Override
		public final Class<AssemblyFilesGetter> getGetterPropertyClass(){
			return AssemblyFilesGetter.class;
		}
		@Override
		public Class<AssemblyFileCollection> getValueClass(){
			return AssemblyFileCollection.class;
		}
		@Override
		public AssemblyFileCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof AssemblyFilesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof AssemblyFilesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public AssemblyFileCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((AssemblyFilesGetter)obj).getAssemblyFiles();
			}
			return null;
		}
	},
	SCHEMAS{
		@Override
		public final Class<?> getPropertyClass(){
			return SchemasProperty.class;
		}
		@Override
		public final Class<SchemasGetter> getGetterPropertyClass(){
			return SchemasGetter.class;
		}
		@Override
		public Class<SchemaCollection> getValueClass(){
			return SchemaCollection.class;
		}
		@Override
		public SchemaCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SchemasProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof SchemasGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public SchemaCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SchemasGetter)obj).getSchemas();
			}
			return null;
		}
	}
	,
	PUBLIC_SYNONYMS{
		@Override
		public final Class<?> getPropertyClass(){
			return PublicSynonymsProperty.class;
		}
		@Override
		public final Class<PublicSynonymsGetter> getGetterPropertyClass(){
			return PublicSynonymsGetter.class;
		}
		@Override
		public Class<PublicSynonymCollection> getValueClass(){
			return PublicSynonymCollection.class;
		}
		@Override
		public PublicSynonymCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PublicSynonymsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof PublicSynonymsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public PublicSynonymCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PublicSynonymsGetter)obj).getPublicSynonyms();
			}
			return null;
		}
	}
	,
	PUBLIC_DB_LINKS{
		@Override
		public final Class<?> getPropertyClass(){
			return PublicDbLinksProperty.class;
		}
		@Override
		public final Class<PublicDbLinksGetter> getGetterPropertyClass(){
			return PublicDbLinksGetter.class;
		}
		@Override
		public Class<PublicDbLinkCollection> getValueClass(){
			return PublicDbLinkCollection.class;
		}
		@Override
		public PublicDbLinkCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PublicDbLinksProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof PublicDbLinksGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public PublicDbLinkCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PublicDbLinksGetter)obj).getPublicDbLinks();
			}
			return null;
		}
	},
	USERS{
		@Override
		public final Class<?> getPropertyClass(){
			return UsersProperty.class;
		}
		@Override
		public final Class<UsersGetter> getGetterPropertyClass(){
			return UsersGetter.class;
		}
		@Override
		public Class<UserCollection> getValueClass(){
			return UserCollection.class;
		}
		@Override
		public UserCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof UsersProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof UsersGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public UserCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((UsersGetter)obj).getUsers();
			}
			return null;
		}
	},
	ROLES{
		@Override
		public final Class<?> getPropertyClass(){
			return RolesProperty.class;
		}
		@Override
		public final Class<RolesGetter> getGetterPropertyClass(){
			return RolesGetter.class;
		}
		@Override
		public Class<RoleCollection> getValueClass(){
			return RoleCollection.class;
		}
		@Override
		public RoleCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof RolesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof RolesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public RoleCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((RolesGetter)obj).getRoles();
			}
			return null;
		}
	},
	TABLE_SPACES{
		@Override
		public final Class<?> getPropertyClass(){
			return TableSpacesProperty.class;
		}
		@Override
		public final Class<TableSpacesGetter> getGetterPropertyClass(){
			return TableSpacesGetter.class;
		}
		@Override
		public Class<TableSpaceCollection> getValueClass(){
			return TableSpaceCollection.class;
		}
		@Override
		public TableSpaceCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof TableSpacesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof TableSpacesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public TableSpaceCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((TableSpacesGetter)obj).getTableSpaces();
			}
			return null;
		}
	},
	DIRECTORIES{
		@Override
		public final Class<?> getPropertyClass(){
			return DirectoriesProperty.class;
		}
		@Override
		public final Class<DirectoriesGetter> getGetterPropertyClass(){
			return DirectoriesGetter.class;
		}
		@Override
		public Class<DirectoryCollection> getValueClass(){
			return DirectoryCollection.class;
		}
		@Override
		public DirectoryCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DirectoriesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof DirectoriesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public DirectoryCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DirectoriesGetter)obj).getDirectories();
			}
			return null;
		}
	},
	PARTITION_FUNCTIONS{
		@Override
		public final Class<?> getPropertyClass(){
			return PartitionFunctionsProperty.class;
		}
		@Override
		public final Class<PartitionFunctionsGetter> getGetterPropertyClass(){
			return PartitionFunctionsGetter.class;
		}
		@Override
		public Class<PartitionFunctionCollection> getValueClass(){
			return PartitionFunctionCollection.class;
		}
		@Override
		public PartitionFunctionCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PartitionFunctionsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof PartitionFunctionsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public PartitionFunctionCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PartitionFunctionsGetter)obj).getPartitionFunctions();
			}
			return null;
		}
	},
	PARTITION_SCHEMES{
		@Override
		public final Class<?> getPropertyClass(){
			return PartitionSchemesProperty.class;
		}
		@Override
		public final Class<PartitionSchemesGetter> getGetterPropertyClass(){
			return PartitionSchemesGetter.class;
		}
		@Override
		public Class<PartitionSchemeCollection> getValueClass(){
			return PartitionSchemeCollection.class;
		}
		@Override
		public PartitionSchemeCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PartitionSchemesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof PartitionSchemesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public PartitionSchemeCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PartitionSchemesGetter)obj).getPartitionSchemes();
			}
			return null;
		}
	},
	ASSEMBLIES{
		@Override
		public final Class<?> getPropertyClass(){
			return AssembliesProperty.class;
		}
		@Override
		public final Class<AssembliesGetter> getGetterPropertyClass(){
			return AssembliesGetter.class;
		}
		@Override
		public Class<AssemblyCollection> getValueClass(){
			return AssemblyCollection.class;
		}
		@Override
		public AssemblyCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof AssembliesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof AssembliesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public AssemblyCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((AssembliesGetter)obj).getAssemblies();
			}
			return null;
		}
	},
	USER_PRIVILEGES{
		@Override
		public final Class<?> getPropertyClass(){
			return UserPrivilegesProperty.class;
		}
		@Override
		public final Class<UserPrivilegesGetter> getGetterPropertyClass(){
			return UserPrivilegesGetter.class;
		}
		@Override
		public Class<UserPrivilegeCollection> getValueClass(){
			return UserPrivilegeCollection.class;
		}
		@Override
		public UserPrivilegeCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof UserPrivilegesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof UserPrivilegesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public UserPrivilegeCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((UserPrivilegesGetter)obj).getUserPrivileges();
			}
			return null;
		}
	},
	OBJECT_PRIVILEGES{
		@Override
		public final Class<?> getPropertyClass(){
			return ObjectPrivilegesProperty.class;
		}
		@Override
		public final Class<ObjectPrivilegesGetter> getGetterPropertyClass(){
			return ObjectPrivilegesGetter.class;
		}
		@Override
		public Class<ObjectPrivilegeCollection> getValueClass(){
			return ObjectPrivilegeCollection.class;
		}
		@Override
		public ObjectPrivilegeCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ObjectPrivilegesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof ObjectPrivilegesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public ObjectPrivilegeCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ObjectPrivilegesGetter)obj).getObjectPrivileges();
			}
			return null;
		}
	},
	ROUTINE_PRIVILEGES{
		@Override
		public final Class<?> getPropertyClass(){
			return RoutinePrivilegesProperty.class;
		}
		@Override
		public final Class<RoutinePrivilegesGetter> getGetterPropertyClass(){
			return RoutinePrivilegesGetter.class;
		}
		@Override
		public Class<RoutinePrivilegeCollection> getValueClass(){
			return RoutinePrivilegeCollection.class;
		}
		@Override
		public RoutinePrivilegeCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof RoutinePrivilegesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof RoutinePrivilegesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public RoutinePrivilegeCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((RoutinePrivilegesGetter)obj).getRoutinePrivileges();
			}
			return null;
		}
	},
	COLUMN_PRIVILEGES{
		@Override
		public final Class<?> getPropertyClass(){
			return ColumnPrivilegesProperty.class;
		}
		@Override
		public final Class<ColumnPrivilegesGetter> getGetterPropertyClass(){
			return ColumnPrivilegesGetter.class;
		}
		@Override
		public Class<ColumnPrivilegeCollection> getValueClass(){
			return ColumnPrivilegeCollection.class;
		}
		@Override
		public ColumnPrivilegeCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ColumnPrivilegesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof ColumnPrivilegesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public ColumnPrivilegeCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ColumnPrivilegesGetter)obj).getColumnPrivileges();
			}
			return null;
		}
	},
	ROLE_PRIVILEGES{
		@Override
		public final Class<?> getPropertyClass(){
			return RolePrivilegesProperty.class;
		}
		@Override
		public final Class<RolePrivilegesGetter> getGetterPropertyClass(){
			return RolePrivilegesGetter.class;
		}
		@Override
		public Class<RolePrivilegeCollection> getValueClass(){
			return RolePrivilegeCollection.class;
		}
		@Override
		public RolePrivilegeCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof RolePrivilegesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof RolePrivilegesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public RolePrivilegeCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((RolePrivilegesGetter)obj).getRolePrivileges();
			}
			return null;
		}
	},
	SCHEMA_PRIVILEGES{
		@Override
		public final Class<?> getPropertyClass(){
			return SchemaPrivilegesProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return SchemaPrivilegesGetter.class;
		}
		@Override
		public Class<SchemaPrivilegeCollection> getValueClass(){
			return SchemaPrivilegeCollection.class;
		}
		@Override
		public SchemaPrivilegeCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SchemaPrivilegesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof SchemaPrivilegesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public SchemaPrivilegeCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SchemaPrivilegesGetter)obj).getSchemaPrivileges();
			}
			return null;
		}
	},
	ROLE_MEMBERS{
		@Override
		public final Class<?> getPropertyClass(){
			return RoleMembersProperty.class;
		}
		@Override
		public final Class<RoleMembersGetter> getGetterPropertyClass(){
			return RoleMembersGetter.class;
		}
		@Override
		public Class<RoleMemberCollection> getValueClass(){
			return RoleMemberCollection.class;
		}
		@Override
		public RoleMemberCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof RoleMembersProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof RoleMembersGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public RoleMemberCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((RoleMembersGetter)obj).getRoleMembers();
			}
			return null;
		}
	},
	SETTINGS{
		@Override
		public final Class<?> getPropertyClass(){
			return SettingsProperty.class;
		}
		@Override
		public final Class<SettingsGetter> getGetterPropertyClass(){
			return SettingsGetter.class;
		}
		@Override
		public Class<SettingCollection> getValueClass(){
			return SettingCollection.class;
		}
		@Override
		public SettingCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SettingsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof SettingsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public SettingCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SettingsGetter)obj).getSettings();
			}
			return null;
		}
	},
	COLUMN_ARRAY("columns"){
		@Override
		public final Class<?> getPropertyClass(){
			return ColumnArrayProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Column[].class;
		}
		@Override
		public Column[] getDefaultValue(){
			return new Column[0];
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ColumnArrayProperty;
		}
		@Override
		public Column[] getCloneValue(final Object obj){
			final Column[] value=getValue(obj);
			if (value==null){
				return null;
			}
			final Column[] clone=new Column[value.length];
			for(int i=0;i<value.length;i++){
				if (value[i]!=null){
					clone[i]=value[i].clone();
				}
			}
			return clone;
		}
		@SuppressWarnings("unchecked")
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final ColumnArrayProperty<?> cst=(ColumnArrayProperty<?>)obj;
			if (value instanceof Column[]){
				cst.setColumns((Column[])value);
				return true;
			}else if (value instanceof ColumnCollection){
				cst.setColumns((ColumnCollection)value);
				return true;
			}else if (value instanceof Collection){
				cst.setColumns((Collection<Column>)value);
				return true;
			}
			return false;
		}
		@Override
		public Column[] getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ColumnArrayProperty<?>)obj).getColumns();
			}
			return null;
		}
	},
	RELATED_COLUMNS{
		@Override
		public final Class<?> getPropertyClass(){
			return RelatedColumnsProperty.class;
		}
		@Override
		public final Class<RelatedColumnsGetter> getGetterPropertyClass(){
			return RelatedColumnsGetter.class;
		}
		@Override
		public Class<ReferenceColumnCollection> getValueClass(){
			return ReferenceColumnCollection.class;
		}
		@Override
		public ReferenceColumnCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof RelatedColumnsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof RelatedColumnsGetter;
		}
		@Override
		public List<StaxElementHandler> getXmlHandlers(){
			return Collections.emptyList();
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public ReferenceColumnCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((RelatedColumnsGetter)obj).getRelatedColumns();
			}
			return null;
		}
	},
	REFERENCE_COLUMNS("columns"){
		@Override
		public final Class<?> getPropertyClass(){
			return ReferenceColumnsProperty.class;
		}
		@Override
		public final Class<ReferenceColumnsGetter> getGetterPropertyClass(){
			return ReferenceColumnsGetter.class;
		}
		@Override
		public Class<ReferenceColumnCollection> getValueClass(){
			return ReferenceColumnCollection.class;
		}
		@Override
		public ReferenceColumnCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ReferenceColumnsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof ReferenceColumnsGetter;
		}
		@Override
		public List<StaxElementHandler> getXmlHandlers(){
			final List<StaxElementHandler> handlers=CommonUtils.list();
			handlers.add(new ReferenceColumnCollectionXmlReaderHandler());
			return handlers;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public ReferenceColumnCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ReferenceColumnsGetter)obj).getColumns();
			}
			return null;
		}
	},
	INCLUDED_COLUMNS("includes"){
		@Override
		public final Class<?> getPropertyClass(){
			return IncludeColumnsProperty.class;
		}
		@Override
		public final Class<IncludeColumnsGetter> getGetterPropertyClass(){
			return IncludeColumnsGetter.class;
		}
		@Override
		public Class<ReferenceColumnCollection> getValueClass(){
			return ReferenceColumnCollection.class;
		}
		@Override
		public ReferenceColumnCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IncludeColumnsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof IncludeColumnsGetter;
		}
		@Override
		public List<StaxElementHandler> getXmlHandlers(){
			final List<StaxElementHandler> handlers=CommonUtils.list();
			handlers.add(new ReferenceColumnCollectionXmlReaderHandler(){
				@Override
				public String getLocalName(){
					return getLabel();
				}
				@Override
				protected ReferenceColumnCollection getInstance(final Object parentObject,
						final String name, final String schemaName, final ReferenceColumnCollection obj) {
					if (parentObject instanceof Index) {
						final Index index = (Index) parentObject;
						return index.getIncludes();
					}
					return obj;
				}
			});
			return handlers;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public ReferenceColumnCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IncludeColumnsGetter)obj).getIncludes();
			}
			return null;
		}
	},
	ARGUMENTS{
		@Override
		public final Class<?> getPropertyClass(){
			return ArgumentsProperty.class;
		}
		@SuppressWarnings("rawtypes")
		@Override
		public Class<NamedArgumentCollection> getValueClass(){
			return NamedArgumentCollection.class;
		}
		@SuppressWarnings("rawtypes")
		@Override
		public NamedArgumentCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ArgumentsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof ArgumentsProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public NamedArgumentCollection<?> getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ArgumentsProperty<?>)obj).getArguments();
			}
			return null;
		}
	},
	DIMENSION_LEVELS("levels"){
		@Override
		public final Class<?> getPropertyClass(){
			return DimensionLevelsProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return DimensionLevelsGetter.class;
		}
		@Override
		public Class<DimensionLevelCollection> getValueClass(){
			return DimensionLevelCollection.class;
		}
		@Override
		public DimensionLevelCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DimensionLevelsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof DimensionLevelsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public DimensionLevelCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DimensionLevelsGetter)obj).getLevels();
			}
			return null;
		}
	},
	DIMENSION_HIERARCHIES("hierarchies"){
		@Override
		public final Class<?> getPropertyClass(){
			return DimensionHierarchiesProperty.class;
		}
		@Override
		public final Class<DimensionHierarchiesGetter> getGetterPropertyClass(){
			return DimensionHierarchiesGetter.class;
		}
		@Override
		public Class<DimensionHierarchyCollection> getValueClass(){
			return DimensionHierarchyCollection.class;
		}
		@Override
		public DimensionHierarchyCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DimensionHierarchiesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof DimensionHierarchiesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public DimensionHierarchyCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DimensionHierarchiesGetter)obj).getHierarchies();
			}
			return null;
		}
	}
	,
	DIMENSION_ATTRIBUTES("attributes"){
		@Override
		public final Class<?> getPropertyClass(){
			return DimensionAttributesProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return DimensionAttributesGetter.class;
		}
		@Override
		public Class<DimensionAttributeCollection> getValueClass(){
			return DimensionAttributeCollection.class;
		}
		@Override
		public DimensionAttributeCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DimensionAttributesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof DimensionAttributesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public DimensionAttributeCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DimensionAttributesGetter)obj).getAttributes();
			}
			return null;
		}
	}
	,
	DIMENSION_ATTRIBUTE_COLUMNS("columns"){
		@Override
		public final Class<?> getPropertyClass(){
			return DimensionAttributeColumnsProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return DimensionAttributeColumnsGetter.class;
		}
		@Override
		public Class<DimensionAttributeColumnCollection> getValueClass(){
			return DimensionAttributeColumnCollection.class;
		}
		@Override
		public DimensionAttributeColumnCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DimensionAttributeColumnsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof DimensionAttributeColumnsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public DimensionAttributeColumnCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DimensionAttributeColumnsGetter)obj).getColumns();
			}
			return null;
		}
	}
	,
	DIMENSION_HIERARCHY_LEVELS("levels"){
		@Override
		public final Class<?> getPropertyClass(){
			return DimensionHierarchyLevelsProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return DimensionHierarchyLevelsGetter.class;
		}
		@Override
		public Class<DimensionHierarchyLevelCollection> getValueClass(){
			return DimensionHierarchyLevelCollection.class;
		}
		@Override
		public DimensionHierarchyLevelCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DimensionHierarchyLevelsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof DimensionHierarchyLevelsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public DimensionHierarchyLevelCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DimensionHierarchyLevelsGetter)obj).getLevels();
			}
			return null;
		}
	}
	,
	DIMENSION_HIERARCHY_JOIN_KEYS("joinKeys"){
		@Override
		public final Class<?> getPropertyClass(){
			return DimensionHierarchyJoinKeysProperty.class;
		}
		@Override
		public final Class<DimensionHierarchyJoinKeysGetter> getGetterPropertyClass(){
			return DimensionHierarchyJoinKeysGetter.class;
		}
		@Override
		public Class<DimensionHierarchyJoinKeyCollection> getValueClass(){
			return DimensionHierarchyJoinKeyCollection.class;
		}
		@Override
		public DimensionHierarchyJoinKeyCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DimensionHierarchyJoinKeysProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof DimensionHierarchyJoinKeysGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return setValueCollectionInternal(obj, value);
		}
		@Override
		public DimensionHierarchyJoinKeyCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DimensionHierarchyJoinKeysGetter)obj).getJoinKeys();
			}
			return null;
		}
	}
	,
	DIMENSION_LEVEL_COLUMNS("columns"){
		@Override
		public final Class<?> getPropertyClass(){
			return DimensionLevelColumnsProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return DimensionLevelColumnsGetter.class;
		}
		@Override
		public Class<DimensionLevelColumnCollection> getValueClass(){
			return DimensionLevelColumnCollection.class;
		}
		@Override
		public DimensionLevelColumnCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DimensionLevelColumnsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof DimensionLevelColumnsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public DimensionLevelColumnCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DimensionLevelColumnsGetter)obj).getColumns();
			}
			return null;
		}
	}
	,
	DIMENSION_HIERARCHY_JOIN_KEY_COLUMNS("columns"){
		@Override
		public final Class<?> getPropertyClass(){
			return DimensionHierarchyJoinKeyColumnsProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return DimensionHierarchyJoinKeyColumnsGetter.class;
		}
		@Override
		public Class<DimensionHierarchyJoinKeyColumnCollection> getValueClass(){
			return DimensionHierarchyJoinKeyColumnCollection.class;
		}
		@Override
		public DimensionHierarchyJoinKeyColumnCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DimensionHierarchyJoinKeyColumnsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof DimensionHierarchyJoinKeyColumnsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return setValueCollectionInternal(obj, value);
		}
		@Override
		public DimensionHierarchyJoinKeyColumnCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DimensionHierarchyJoinKeyColumnsGetter)obj).getColumns();
			}
			return null;
		}
	}
	,
	FUNCTION_RETURNING("returning"){
		@Override
		public final Class<?> getPropertyClass(){
			return FunctionReturningProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return FunctionReturningGetter.class;
		}
		@Override
		public Class<FunctionReturning> getValueClass(){
			return FunctionReturning.class;
		}
		@Override
		public FunctionReturning getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof FunctionReturningProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof FunctionReturningGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final FunctionReturningProperty<?> cst=(FunctionReturningProperty<?>)obj;
			if ((value instanceof FunctionReturning)||value==null){
				cst.setReturning((FunctionReturning)value);
				return true;
			}
			return false;
		}
		@Override
		public FunctionReturning getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((FunctionReturningGetter)obj).getReturning();
			}
			return null;
		}
	},
	FUNCTION_RETURNING_REFERENCE_TABLE("table"){
		@Override
		public final Class<?> getPropertyClass(){
			return FunctionReturningReferenceTableProperty.class;
		}
		@Override
		public Class<FunctionReturningReferenceTable> getValueClass(){
			return FunctionReturningReferenceTable.class;
		}
		@Override
		public FunctionReturningReferenceTable getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof FunctionReturningReferenceTableProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final FunctionReturningReferenceTableProperty<?> cst=(FunctionReturningReferenceTableProperty<?>)obj;
			if ((value instanceof FunctionReturningReferenceTable)||value==null){
				cst.setTable((FunctionReturningReferenceTable)value);
				return true;
			}else if (value instanceof Table){
				cst.setTable(getDefaultValue().setTable((Table)value));
				return true;
			}
			return false;
		}
		@Override
		public FunctionReturningReferenceTable getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((FunctionReturningReferenceTableProperty<?>)obj).getTable();
			}
			return null;
		}
	}
	,
	OPERATOR_LEFT_ARGUMENT("leftArgument"){
		@Override
		public final Class<?> getPropertyClass(){
			return OperatorLeftArgumentProperty.class;
		}
		@Override
		public Class<OperatorArgument> getValueClass(){
			return OperatorArgument.class;
		}
		@Override
		public OperatorArgument getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OperatorLeftArgumentProperty;
		}
		@Override
		public List<StaxElementHandler> getXmlHandlers(){
			final List<StaxElementHandler> handlers=CommonUtils.list();
			handlers.add(new OperatorArgumentXmlReaderHandler() {
				@Override
				public String getLocalName() {
					return getLabel();
				}
			});
			return handlers;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final OperatorLeftArgumentProperty<?> cst=(OperatorLeftArgumentProperty<?>)obj;
			if ((value instanceof OperatorArgument)||value==null){
				cst.setLeftArgument((OperatorArgument)value);
				return true;
			}else if (value instanceof String){
				cst.setLeftArgument((String)value);
				return true;
			}
			return false;
		}
		@Override
		public OperatorArgument getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OperatorLeftArgumentProperty<?>)obj).getLeftArgument();
			}
			return null;
		}
	}
	,
	OPERATOR_RIGHT_ARGUMENT("rightArgument"){
		@Override
		public final Class<?> getPropertyClass(){
			return OperatorRightArgumentProperty.class;
		}
		@Override
		public Class<OperatorArgument> getValueClass(){
			return OperatorArgument.class;
		}
		@Override
		public OperatorArgument getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OperatorRightArgumentProperty;
		}
		@Override
		public List<StaxElementHandler> getXmlHandlers(){
			final List<StaxElementHandler> handlers=CommonUtils.list();
			handlers.add(new OperatorArgumentXmlReaderHandler() {
				@Override
				public String getLocalName() {
					return getLabel();
				}
			});
			return handlers;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final OperatorRightArgumentProperty<?> cst=(OperatorRightArgumentProperty<?>)obj;
			if ((value instanceof OperatorArgument)||value==null){
				cst.setRightArgument((OperatorArgument)value);
				return true;
			}else if (value instanceof String){
				cst.setRightArgument((String)value);
				return true;
			}
			return false;
		}
		@Override
		public OperatorArgument getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OperatorRightArgumentProperty<?>)obj).getRightArgument();
			}
			return null;
		}
	}
	,
	PARTITIONING(){
		@Override
		public final Class<?> getPropertyClass(){
			return PartitioningProperty.class;
		}
		@Override
		public Class<Partitioning> getValueClass(){
			return Partitioning.class;
		}
		@Override
		public Partitioning getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PartitioningProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final PartitioningProperty<?> cst=(PartitioningProperty<?>)obj;
			if ((value instanceof Partitioning)||value==null){
				cst.setPartitioning((Partitioning)value);
				return true;
			}
			return false;
		}
		@Override
		public Partitioning getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PartitioningProperty<?>)obj).getPartitioning();
			}
			return null;
		}
	}
	,
	PARTITION_PARENT(){
		@Override
		public final Class<?> getPropertyClass(){
			return PartitionParentProperty.class;
		}
		@Override
		public Class<PartitionParent> getValueClass(){
			return PartitionParent.class;
		}
		@Override
		public PartitionParent getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PartitionParentProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final PartitionParentProperty<?> cst=(PartitionParentProperty<?>)obj;
			if ((value instanceof PartitionParent)||value==null){
				cst.setPartitionParent((PartitionParent)value);
				return true;
			}
			return false;
		}
		@Override
		public PartitionParent getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PartitionParentProperty<?>)obj).getPartitionParent();
			}
			return null;
		}
	}
	,
	OPERATOR_BINDINGS("bindings"){
		@Override
		public final Class<?> getPropertyClass(){
			return OperatorBindingsProperty.class;
		}
		@Override
		public final Class<OperatorBindingsGetter> getGetterPropertyClass(){
			return OperatorBindingsGetter.class;
		}
		@Override
		public Class<OperatorBindingCollection> getValueClass(){
			return OperatorBindingCollection.class;
		}
		@Override
		public OperatorBindingCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OperatorBindingsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof OperatorBindingsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public OperatorBindingCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OperatorBindingsGetter)obj).getBindings();
			}
			return null;
		}
	}
	,
	OPERATOR_BINDING_ARGUMENTS("arguments"){
		@Override
		public final Class<?> getPropertyClass(){
			return OperatorBindingArgumentsProperty.class;
		}
		@Override
		public final Class<OperatorBindingArgumentsGetter> getGetterPropertyClass(){
			return OperatorBindingArgumentsGetter.class;
		}
		@Override
		public Class<OperatorBindingArgumentCollection> getValueClass(){
			return OperatorBindingArgumentCollection.class;
		}
		@Override
		public OperatorBindingArgumentCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OperatorBindingArgumentsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof OperatorBindingArgumentsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public OperatorBindingArgumentCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OperatorBindingArgumentsGetter)obj).getArguments();
			}
			return null;
		}
	},
	OPERATOR_FAMILIES{
		@Override
		public final Class<?> getPropertyClass(){
			return OperatorFamiliesProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return OperatorFamiliesGetter.class;
		}
		@Override
		public Class<OperatorFamilyCollection> getValueClass(){
			return OperatorFamilyCollection.class;
		}
		@Override
		public OperatorFamilyCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OperatorFamiliesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof OperatorFamiliesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public OperatorFamilyCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OperatorFamiliesGetter)obj).getOperatorFamilies();
			}
			return null;
		}
	},
	FUNCTION_FAMILIES{
		@Override
		public final Class<?> getPropertyClass(){
			return FunctionFamiliesProperty.class;
		}
		@Override
		public final Class<FunctionFamiliesGetter> getGetterPropertyClass(){
			return FunctionFamiliesGetter.class;
		}
		@Override
		public Class<FunctionFamilyCollection> getValueClass(){
			return FunctionFamilyCollection.class;
		}
		@Override
		public FunctionFamilyCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof FunctionFamiliesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof FunctionFamiliesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public FunctionFamilyCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((FunctionFamiliesGetter)obj).getFunctionFamilies();
			}
			return null;
		}
	},
	PARTITIONING_COLUMNS{
		@Override
		public final Class<?> getPropertyClass(){
			return PartitioningColumnsProperty.class;
		}
		@Override
		public final Class<PartitioningColumnsGetter> getGetterPropertyClass(){
			return PartitioningColumnsGetter.class;
		}
		@Override
		public Class<ReferenceColumnCollection> getValueClass(){
			return ReferenceColumnCollection.class;
		}
		@Override
		public ReferenceColumnCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PartitioningColumnsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof PartitioningColumnsGetter;
		}
		@Override
		public List<StaxElementHandler> getXmlHandlers(){
			final List<StaxElementHandler> handlers=CommonUtils.list();
			handlers.add(new ReferenceColumnCollectionXmlReaderHandler(){
				@Override
				public String getLocalName(){
					return getLabel();
				}
				@Override
				protected ReferenceColumnCollection getInstance(final Object parentObject,
						final String name, final String schemaName, final ReferenceColumnCollection obj) {
					if (parentObject instanceof Partitioning) {
						final Partitioning parent = (Partitioning) parentObject;
						return parent.getPartitioningColumns();
					}
					return obj;
				}
			});
			return handlers;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public ReferenceColumnCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PartitioningColumnsGetter)obj).getPartitioningColumns();
			}
			return null;
		}
	},
	SUB_PARTITIONING_COLUMNS{
		@Override
		public final Class<?> getPropertyClass(){
			return SubPartitioningColumnsProperty.class;
		}
		@Override
		public final Class<SubPartitioningColumnsGetter> getGetterPropertyClass(){
			return SubPartitioningColumnsGetter.class;
		}
		@Override
		public Class<ReferenceColumnCollection> getValueClass(){
			return ReferenceColumnCollection.class;
		}
		@Override
		public ReferenceColumnCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SubPartitioningColumnsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof SubPartitioningColumnsGetter;
		}
		@Override
		public List<StaxElementHandler> getXmlHandlers(){
			final List<StaxElementHandler> handlers=CommonUtils.list();
			handlers.add(new ReferenceColumnCollectionXmlReaderHandler(){
				@Override
				public String getLocalName(){
					return getLabel();
				}
				@Override
				protected ReferenceColumnCollection getInstance(final Object parentObject,
						final String name, final String schemaName, final ReferenceColumnCollection obj) {
					if (parentObject instanceof Partitioning) {
						final Partitioning parent = (Partitioning) parentObject;
						return parent.getSubPartitioningColumns();
					}
					return obj;
				}
			});
			return handlers;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public ReferenceColumnCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SubPartitioningColumnsGetter)obj).getSubPartitioningColumns();
			}
			return null;
		}
	},
	PARTITIONS{
		@Override
		public final Class<?> getPropertyClass(){
			return PartitionsProperty.class;
		}
		@Override
		public final Class<PartitionsGetter> getGetterPropertyClass(){
			return PartitionsGetter.class;
		}
		@Override
		public Class<PartitionCollection> getValueClass(){
			return PartitionCollection.class;
		}
		@Override
		public PartitionCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PartitionsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof PartitionsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public PartitionCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PartitionsGetter)obj).getPartitions();
			}
			return null;
		}
	},
	SUB_PARTITIONS{
		@Override
		public final Class<?> getPropertyClass(){
			return SubPartitionsProperty.class;
		}
		@Override
		public final Class<SubPartitionsGetter> getGetterPropertyClass(){
			return SubPartitionsGetter.class;
		}
		@Override
		public Class<SubPartitionCollection> getValueClass(){
			return SubPartitionCollection.class;
		}
		@Override
		public SubPartitionCollection getDefaultValue(){
			return SchemaUtils.newInstanceAtSchemas(getValueClass());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SubPartitionsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof SubPartitionsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public SubPartitionCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SubPartitionsGetter)obj).getSubPartitions();
			}
			return null;
		}
	},
	REFERENCE_TABLE_SPACES("tableSpaces"){
		@Override
		public final Class<?> getPropertyClass(){
			return ReferenceTableSpacesProperty.class;
		}
		@Override
		public final Class<ReferenceTableSpacesGetter> getGetterPropertyClass(){
			return ReferenceTableSpacesGetter.class;
		}
		@Override
		public Class<ReferenceTableSpaceCollection> getValueClass(){
			return ReferenceTableSpaceCollection.class;
		}
		@Override
		public ReferenceTableSpaceCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ReferenceTableSpacesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof ReferenceTableSpacesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public ReferenceTableSpaceCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ReferenceTableSpacesGetter)obj).getTableSpaces();
			}
			return null;
		}
	},
	TABLES{
		@Override
		public final Class<?> getPropertyClass(){
			return TablesProperty.class;
		}
		@Override
		public final Class<TablesGetter> getGetterPropertyClass(){
			return TablesGetter.class;
		}
		@Override
		public Class<TableCollection> getValueClass(){
			return TableCollection.class;
		}
		@Override
		public TableCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof TablesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof TablesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public TableCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((TablesGetter)obj).getTables();
			}
			return null;
		}
	}
	,
	VIEWS{
		@Override
		public final Class<?> getPropertyClass(){
			return ViewsProperty.class;
		}
		@Override
		public final Class<ViewsGetter> getGetterPropertyClass(){
			return ViewsGetter.class;
		}
		@Override
		public Class<ViewCollection> getValueClass(){
			return ViewCollection.class;
		}
		@Override
		public ViewCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ViewsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof ViewsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public ViewCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ViewsGetter)obj).getViews();
			}
			return null;
		}
	}
	,
	MVIEWS{
		@Override
		public final Class<?> getPropertyClass(){
			return MviewsProperty.class;
		}
		@Override
		public final Class<MviewsGetter> getGetterPropertyClass(){
			return MviewsGetter.class;
		}
		@Override
		public Class<MviewCollection> getValueClass(){
			return MviewCollection.class;
		}
		@Override
		public MviewCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof MviewsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof MviewsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public MviewCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((MviewsGetter)obj).getMviews();
			}
			return null;
		}
	},
	MASKS{
		@Override
		public final Class<?> getPropertyClass(){
			return MasksProperty.class;
		}
		@Override
		public final Class<MasksGetter> getGetterPropertyClass(){
			return MasksGetter.class;
		}
		@Override
		public Class<MaskCollection> getValueClass(){
			return MaskCollection.class;
		}
		@Override
		public MaskCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof MasksProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof MasksGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public MaskCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((MasksGetter)obj).getMasks();
			}
			return null;
		}
	},
	EXTERNAL_TABLES{
		@Override
		public final Class<?> getPropertyClass(){
			return ExternalTablesProperty.class;
		}
		@Override
		public final Class<ExternalTablesGetter> getGetterPropertyClass(){
			return ExternalTablesGetter.class;
		}
		@Override
		public Class<ExternalTableCollection> getValueClass(){
			return ExternalTableCollection.class;
		}
		@Override
		public ExternalTableCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ExternalTablesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof ExternalTablesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public ExternalTableCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ExternalTablesGetter)obj).getExternalTables();
			}
			return null;
		}
	},
	MVIEW_LOGS{
		@Override
		public final Class<?> getPropertyClass(){
			return MviewLogsProperty.class;
		}
		@Override
		public final Class<MviewLogsGetter> getGetterPropertyClass(){
			return MviewLogsGetter.class;
		}
		@Override
		public Class<MviewLogCollection> getValueClass(){
			return MviewLogCollection.class;
		}
		@Override
		public MviewLogCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof MviewLogsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof MviewLogsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public MviewLogCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((MviewLogsGetter)obj).getMviewLogs();
			}
			return null;
		}
	},
	PROCEDURES{
		@Override
		public final Class<?> getPropertyClass(){
			return ProceduresProperty.class;
		}
		@Override
		public final Class<ProceduresGetter> getGetterPropertyClass(){
			return ProceduresGetter.class;
		}
		@Override
		public Class<ProcedureCollection> getValueClass(){
			return ProcedureCollection.class;
		}
		@Override
		public ProcedureCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ProceduresProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof ProceduresGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public ProcedureCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ProceduresGetter)obj).getProcedures();
			}
			return null;
		}
	},
	FUNCTIONS{
		@Override
		public final Class<?> getPropertyClass(){
			return FunctionsProperty.class;
		}
		@Override
		public final Class<FunctionsGetter> getGetterPropertyClass(){
			return FunctionsGetter.class;
		}
		@Override
		public Class<FunctionCollection> getValueClass(){
			return FunctionCollection.class;
		}
		@Override
		public FunctionCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof FunctionsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof FunctionsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public FunctionCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((FunctionsGetter)obj).getFunctions();
			}
			return null;
		}
	},
	PACKAGES{
		@Override
		public final Class<?> getPropertyClass(){
			return PackagesProperty.class;
		}
		@Override
		public final Class<PackagesGetter> getGetterPropertyClass(){
			return PackagesGetter.class;
		}
		@Override
		public Class<PackageCollection> getValueClass(){
			return PackageCollection.class;
		}
		@Override
		public PackageCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PackagesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof PackagesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public PackageCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PackagesGetter)obj).getPackages();
			}
			return null;
		}
	},
	PACKAGE_BODIES{
		@Override
		public final Class<?> getPropertyClass(){
			return PackageBodiesProperty.class;
		}
		@Override
		public final Class<PackageBodiesGetter> getGetterPropertyClass(){
			return PackageBodiesGetter.class;
		}
		@Override
		public Class<PackageBodyCollection> getValueClass(){
			return PackageBodyCollection.class;
		}
		@Override
		public PackageBodyCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PackageBodiesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof PackageBodiesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public PackageBodyCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PackageBodiesGetter)obj).getPackageBodies();
			}
			return null;
		}
	},
	TRIGGERS{
		@Override
		public final Class<?> getPropertyClass(){
			return TriggersProperty.class;
		}
		@Override
		public final Class<TriggersGetter> getGetterPropertyClass(){
			return TriggersGetter.class;
		}
		@Override
		public Class<TriggerCollection> getValueClass(){
			return TriggerCollection.class;
		}
		@Override
		public TriggerCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof TriggersProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof TriggersGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public TriggerCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((TriggersGetter)obj).getTriggers();
			}
			return null;
		}
	},
	SEQUENCES{
		@Override
		public final Class<?> getPropertyClass(){
			return SequencesProperty.class;
		}
		@Override
		public final Class<SequencesGetter> getGetterPropertyClass(){
			return SequencesGetter.class;
		}
		@Override
		public Class<SequenceCollection> getValueClass(){
			return SequenceCollection.class;
		}
		@Override
		public SequenceCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SequencesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof SequencesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public SequenceCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SequencesGetter)obj).getSequences();
			}
			return null;
		}
	}
	,
	DB_LINKS{
		@Override
		public final Class<?> getPropertyClass(){
			return DbLinksProperty.class;
		}
		@Override
		public final Class<DbLinksGetter> getGetterPropertyClass(){
			return DbLinksGetter.class;
		}
		@Override
		public Class<DbLinkCollection> getValueClass(){
			return DbLinkCollection.class;
		}
		@Override
		public DbLinkCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DbLinksProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof DbLinksGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public DbLinkCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DbLinksGetter)obj).getDbLinks();
			}
			return null;
		}
	},
	TABLE_LINKS{
		@Override
		public final Class<?> getPropertyClass(){
			return TableLinksProperty.class;
		}
		@Override
		public final Class<TableLinksGetter> getGetterPropertyClass(){
			return TableLinksGetter.class;
		}
		@Override
		public Class<TableLinkCollection> getValueClass(){
			return TableLinkCollection.class;
		}
		@Override
		public TableLinkCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof TableLinksProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof TableLinksGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public TableLinkCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((TableLinksGetter)obj).getTableLinks();
			}
			return null;
		}
	}
	,
	SYNONYMS{
		@Override
		public final Class<?> getPropertyClass(){
			return SynonymsProperty.class;
		}
		@Override
		public final Class<SynonymsGetter> getGetterPropertyClass(){
			return SynonymsGetter.class;
		}
		@Override
		public Class<SynonymCollection> getValueClass(){
			return SynonymCollection.class;
		}
		@Override
		public SynonymCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SynonymsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof SynonymsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public SynonymCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SynonymsGetter)obj).getSynonyms();
			}
			return null;
		}
	}
	,
	DOMAINS{
		@Override
		public final Class<?> getPropertyClass(){
			return DomainsProperty.class;
		}
		@Override
		public final Class<DomainsGetter> getGetterPropertyClass(){
			return DomainsGetter.class;
		}
		@Override
		public Class<DomainCollection> getValueClass(){
			return DomainCollection.class;
		}
		@Override
		public DomainCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DomainsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof DomainsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public DomainCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DomainsGetter)obj).getDomains();
			}
			return null;
		}
	},
	TYPES{
		@Override
		public final Class<?> getPropertyClass(){
			return TypesProperty.class;
		}
		@Override
		public final Class<TypesGetter> getGetterPropertyClass(){
			return TypesGetter.class;
		}
		@Override
		public Class<TypeCollection> getValueClass(){
			return TypeCollection.class;
		}
		@Override
		public TypeCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof TypesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof TypesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public TypeCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((TypesGetter)obj).getTypes();
			}
			return null;
		}
	}
	,
	TYPE_BODIES{
		@Override
		public final Class<?> getPropertyClass(){
			return TypeBodiesProperty.class;
		}
		@Override
		public final Class<TypeBodiesGetter> getGetterPropertyClass(){
			return TypeBodiesGetter.class;
		}
		@Override
		public Class<TypeBodyCollection> getValueClass(){
			return TypeBodyCollection.class;
		}
		@Override
		public TypeBodyCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof TypeBodiesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof TypeBodiesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public TypeBodyCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((TypeBodiesGetter)obj).getTypeBodies();
			}
			return null;
		}
	}
	,
	RULES{
		@Override
		public final Class<?> getPropertyClass(){
			return RulesProperty.class;
		}
		@Override
		public final Class<RulesGetter> getGetterPropertyClass(){
			return RulesGetter.class;
		}
		@Override
		public Class<RuleCollection> getValueClass(){
			return RuleCollection.class;
		}
		@Override
		public RuleCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof RulesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof RulesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public RuleCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((RulesGetter)obj).getRules();
			}
			return null;
		}
	},
	CONSTANTS{
		@Override
		public final Class<?> getPropertyClass(){
			return ConstantsProperty.class;
		}
		@Override
		public final Class<ConstantsGetter> getGetterPropertyClass(){
			return ConstantsGetter.class;
		}
		@Override
		public Class<ConstantCollection> getValueClass(){
			return ConstantCollection.class;
		}
		@Override
		public ConstantCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ConstantsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof ConstantsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public ConstantCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ConstantsGetter)obj).getConstants();
			}
			return null;
		}
	},
	EVENTS{
		@Override
		public final Class<?> getPropertyClass(){
			return EventsProperty.class;
		}
		@Override
		public final Class<EventsGetter> getGetterPropertyClass(){
			return EventsGetter.class;
		}
		@Override
		public Class<EventCollection> getValueClass(){
			return EventCollection.class;
		}
		@Override
		public EventCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof EventsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof EventsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public EventCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((EventsGetter)obj).getEvents();
			}
			return null;
		}
	}
	,
	XML_SCHEMAS{
		@Override
		public final Class<?> getPropertyClass(){
			return XmlSchemasProperty.class;
		}
		@Override
		public final Class<XmlSchemasGetter> getGetterPropertyClass(){
			return XmlSchemasGetter.class;
		}
		@Override
		public Class<XmlSchemaCollection> getValueClass(){
			return XmlSchemaCollection.class;
		}
		@Override
		public XmlSchemaCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof XmlSchemasProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof XmlSchemasGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public XmlSchemaCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((XmlSchemasGetter)obj).getXmlSchemas();
			}
			return null;
		}
	},
	OPERATORS{
		@Override
		public final Class<?> getPropertyClass(){
			return OperatorsProperty.class;
		}
		@Override
		public final Class<OperatorsGetter> getGetterPropertyClass(){
			return OperatorsGetter.class;
		}
		@Override
		public Class<OperatorCollection> getValueClass(){
			return OperatorCollection.class;
		}
		@Override
		public OperatorCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OperatorsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof OperatorsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public OperatorCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OperatorsGetter)obj).getOperators();
			}
			return null;
		}
	},
	OPERATOR_CLASSES{
		@Override
		public final Class<?> getPropertyClass(){
			return OperatorClassesProperty.class;
		}
		@Override
		public final Class<OperatorClassesGetter> getGetterPropertyClass(){
			return OperatorClassesGetter.class;
		}
		@Override
		public Class<OperatorClassCollection> getValueClass(){
			return OperatorClassCollection.class;
		}
		@Override
		public OperatorClassCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OperatorClassesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof OperatorClassesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public OperatorClassCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OperatorClassesGetter)obj).getOperatorClasses();
			}
			return null;
		}
	},
	DIMENSIONS{
		@Override
		public final Class<?> getPropertyClass(){
			return DimensionsProperty.class;
		}
		@Override
		public final Class<DimensionsGetter> getGetterPropertyClass(){
			return DimensionsGetter.class;
		}
		@Override
		public Class<DimensionCollection> getValueClass(){
			return DimensionCollection.class;
		}
		@Override
		public DimensionCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DimensionsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof DimensionsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public DimensionCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DimensionsGetter)obj).getDimensions();
			}
			return null;
		}
	},
	COLUMNS{
		@Override
		public final Class<?> getPropertyClass(){
			return ColumnsProperty.class;
		}
		@Override
		public final Class<ColumnsGetter> getGetterPropertyClass(){
			return ColumnsGetter.class;
		}
		@Override
		public Class<ColumnCollection> getValueClass(){
			return ColumnCollection.class;
		}
		@Override
		public ColumnCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ColumnsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof ColumnsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public ColumnCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ColumnsGetter)obj).getColumns();
			}
			return null;
		}
	}
	,
	ROWS{
		@Override
		public final Class<?> getPropertyClass(){
			return RowsProperty.class;
		}
		@Override
		public final Class<RowsGetter> getGetterPropertyClass(){
			return RowsGetter.class;
		}
		@Override
		public Class<RowCollection> getValueClass(){
			return RowCollection.class;
		}
		@Override
		public RowCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof RowsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof RowsGetter;
		}
		@Override
		public List<StaxElementHandler> getXmlHandlers(){
			return Collections.emptyList();
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public RowCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((RowsGetter)obj).getRows();
			}
			return null;
		}
	},
	CONSTRAINTS{
		@Override
		public final Class<?> getPropertyClass(){
			return ConstraintsProperty.class;
		}
		@Override
		public final Class<ConstraintsGetter> getGetterPropertyClass(){
			return ConstraintsGetter.class;
		}
		@Override
		public Class<ConstraintCollection> getValueClass(){
			return ConstraintCollection.class;
		}
		@Override
		public ConstraintCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ConstraintsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof ConstraintsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public ConstraintCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ConstraintsGetter)obj).getConstraints();
			}
			return null;
		}
	}
	,
	INDEXES{
		@Override
		public final Class<?> getPropertyClass(){
			return IndexesProperty.class;
		}
		@Override
		public final Class<IndexesGetter> getGetterPropertyClass(){
			return IndexesGetter.class;
		}
		@Override
		public Class<IndexCollection> getValueClass(){
			return IndexCollection.class;
		}
		@Override
		public IndexCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IndexesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof IndexesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public IndexCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IndexesGetter)obj).getIndexes();
			}
			return null;
		}
	}
	,
	INHERITS{
		@Override
		public final Class<?> getPropertyClass(){
			return InheritsProperty.class;
		}
		@Override
		public final Class<InheritsGetter> getGetterPropertyClass(){
			return InheritsGetter.class;
		}
		@Override
		public Class<InheritCollection> getValueClass(){
			return InheritCollection.class;
		}
		@Override
		public InheritCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof InheritsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof InheritsGetter;
		}
		@Override
		public List<StaxElementHandler> getXmlHandlers(){
			return Collections.emptyList();
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public InheritCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((InheritsGetter)obj).getInherits();
			}
			return null;
		}
	}
	,
	TABLE_SPACE_FILES{
		@Override
		public final Class<?> getPropertyClass(){
			return TableSpaceFilesProperty.class;
		}
		@Override
		public final Class<TableSpaceFilesGetter> getGetterPropertyClass(){
			return TableSpaceFilesGetter.class;
		}
		@Override
		public Class<TableSpaceFileCollection> getValueClass(){
			return TableSpaceFileCollection.class;
		}
		@Override
		public TableSpaceFileCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof TableSpaceFilesProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof TableSpaceFilesGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public TableSpaceFileCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((TableSpaceFilesGetter)obj).getTableSpaceFiles();
			}
			return null;
		}
	}
	,
	TYPE_COLUMNS("columns"){
		@Override
		public final Class<?> getPropertyClass(){
			return TypeColumnsProperty.class;
		}
		@Override
		public final Class<TypeColumnsGetter> getGetterPropertyClass(){
			return TypeColumnsGetter.class;
		}
		@Override
		public Class<TypeColumnCollection> getValueClass(){
			return TypeColumnCollection.class;
		}
		@Override
		public TypeColumnCollection getDefaultValue(){
			return this.getValueClass().cast(super.getDefaultValue());
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof TypeColumnsProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof TypeColumnsGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return super.setValueCollectionInternal(obj, value);
		}
		@Override
		public TypeColumnCollection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((TypeColumnsGetter)obj).getColumns();
			}
			return null;
		}
	}
	;
	
	private SchemaObjectProperties(final String label){
		this.label=label;
	}

	private SchemaObjectProperties(){
		this.label=StringUtils.snakeToCamel(this.name());
	}

	private SchemaObjectProperties(final Class<?> propertyClass){
		this.label=StringUtils.snakeToCamel(this.name());
	}

	private final String label;
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#getPropertyClass()
	 */
	@Override
	public Class<?> getPropertyClass(){
		return null;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#getGetterPropertyClass()
	 */
	@Override
	public Class<?> getGetterPropertyClass(){
		return null;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#getSetterPropertyClass()
	 */
	@Override
	public Class<?> getSetterPropertyClass(){
		return null;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#getValueClass()
	 */
	@Override
	public Class<?> getValueClass(){
		return null;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#getDefaultValue()
	 */
	@Override
	public Object getDefaultValue(){
		return SchemaUtils.newInstanceAtSchemas(getValueClass());
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#isGetterInstanceof(java.lang.Object)
	 */
	@Override
	public boolean isGetterInstanceof(final Object obj){
		if (getGetterPropertyClass()!=null){
			return getGetterPropertyClass().isInstance(obj);
		}
		return isInstanceof(obj);
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#isSetterInstanceof(java.lang.Object)
	 */
	@Override
	public boolean isSetterInstanceof(final Object obj){
		if (getSetterPropertyClass()!=null){
			return getSetterPropertyClass().isInstance(obj);
		}
		return isInstanceof(obj);
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#isInstanceof(java.lang.Object)
	 */
	@Override
	public boolean isInstanceof(final Object obj){
		if (getPropertyClass()!=null){
			return getPropertyClass().isInstance(obj);
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#isValueInstanceof(java.lang.Object)
	 */
	@Override
	public boolean isValueInstanceof(final Object obj){
		if (getValueClass()!=null){
			return getValueClass().isInstance(obj);
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#setValue(java.lang.Object, java.lang.Object)
	 */
	@Override
	public boolean setValue(final Object obj, final Object value){
		if (isSetterInstanceof(obj)){
			return setValueInternal(obj, value);
		}
		return false;
	}

	protected abstract boolean setValueInternal(Object obj, Object value);
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#getDependent()
	 */
	@Override
	public ISchemaProperty getDependent(){
		return null;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#isEnabled(java.lang.Object)
	 */
	@Override
	public boolean isEnabled(final Object obj){
		final ISchemaProperty dependent=getDependent();
		if (dependent!=null){
			return dependent.isEnabled(obj);
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#getValue(java.lang.Object)
	 */
	@Override
	public Object getValue(final Object obj){
		return null;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#getCloneValue(java.lang.Object)
	 */
	@Override
	public Object getCloneValue(final Object obj){
		final Object value=getValue(obj);
		if (value==null){
			return null;
		}
		if (value instanceof AbstractBaseDbObjectCollection){
			return ((AbstractBaseDbObjectCollection<?>)value).clone();
		}else if (value instanceof AbstractBaseDbObject){
				return ((AbstractBaseDbObject<?>)value).clone();
		}else if (value instanceof RowCollection){
			return ((RowCollection)value).clone();
		}else if (value instanceof Row){
			return ((Row)value).clone();
		}
		return null;
	}

	@Override
	public List<StaxElementHandler> getXmlHandlers(){
		final List<StaxElementHandler> handlers=CommonUtils.list();
		final Object obj=this.getDefaultValue();
		final StaxElementHandler handler=SchemaUtils.getStaxElementHandler(obj);
		if (handler!=null){
			handlers.add(handler);
		} else{
			return Collections.emptyList();
		}
		return handlers;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#getLabel()
	 */
	@Override
	public final String getLabel(){
		return label;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected boolean setValueCollectionInternal(final Object obj, final Object value){
		if (this.isValueInstanceof(value)){
			final DbObjectCollection targetObject=(DbObjectCollection)value;
			final DbObjectCollection ownCollection=(DbObjectCollection)this.getValue(obj);
			if (ownCollection==targetObject){
				return true;
			}
			if (ownCollection!=null){
				ownCollection.addAll(targetObject);
				return true;
			}
		}
		return false;
	}
	
	protected String toString(final Object obj){
		return Converters.getDefault().convertString(obj);
	}
	
}
