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

import static com.sqlapp.util.CommonUtils.trim;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Assembly.PermissionSet;
import com.sqlapp.data.schemas.ForeignKeyConstraint.MatchOption;
import com.sqlapp.data.schemas.PartitionFunction.PatitionFunctionValues;
import com.sqlapp.data.schemas.Table.TableDataStoreType;
import com.sqlapp.data.schemas.Table.TableType;
import com.sqlapp.data.schemas.Type.MetaType;
import com.sqlapp.data.schemas.properties.*;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StringUtils;
import com.sqlapp.util.xml.EmptyTextSkipHandler;
import com.sqlapp.util.xml.SetHandler;
import com.sqlapp.util.xml.StaxElementHandler;
import com.sqlapp.util.xml.ValueHandler;

public enum SchemaProperties implements ISchemaProperty {
	ID(){
		@Override
		public final Class<?> getPropertyClass(){
			return IdProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IdProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IdProperty<?>)obj).setId(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IdProperty<?>)obj).getId();
			}
			return null;
		}
	},
	CATALOG_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return CatalogNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof CatalogNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((CatalogNameProperty<?>)obj).setCatalogName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((CatalogNameProperty<?>)obj).getCatalogName();
			}
			return null;
		}
	},
	SCHEMA_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return SchemaNameProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return SchemaNameGetter.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SchemaNameProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof SchemaNameGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((SchemaNameProperty<?>)obj).setSchemaName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SchemaNameGetter)obj).getSchemaName();
			}
			return null;
		}
	},
	DEFAULT_SCHEMA_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return DefaultSchemaNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DefaultSchemaNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DefaultSchemaNameProperty<?>)obj).setDefaultSchemaName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DefaultSchemaNameProperty<?>)obj).getDefaultSchemaName();
			}
			return null;
		}
	},
	DISPLAY_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return DisplayNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DisplayNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DisplayNameProperty<?>)obj).setDisplayName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DisplayNameProperty<?>)obj).getDisplayName();
			}
			return null;
		}
	},
	NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return NameProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return NameGetter.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof NameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((NameProperty<?>)obj).setName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((NameGetter<?>)obj).getName();
			}
			return null;
		}
	},
	SPECIFIC_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return SpecificNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SpecificNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((SpecificNameProperty<?>)obj).setSpecificName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SpecificNameProperty<?>)obj).getSpecificName();
			}
			return null;
		}
	},
	TABLE_SCHEMA_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return TableSchemaNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof TableSchemaNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((TableSchemaNameProperty<?>)obj).setTableSchemaName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((TableSchemaNameProperty<?>)obj).getTableSchemaName();
			}
			return null;
		}
	},
	TABLE_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return TableNameProperty.class;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof TableNameGetter;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof TableNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((TableNameProperty<?>)obj).setTableName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((TableNameProperty<?>)obj).getTableName();
			}
			return null;
		}
	},
	COLUMN_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return ColumnNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ColumnNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ColumnNameProperty<?>)obj).setColumnName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ColumnNameProperty<?>)obj).getColumnName();
			}
			return null;
		}
	},
	INDEX_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return IndexNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IndexNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IndexNameProperty<?>)obj).setIndexName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IndexNameProperty<?>)obj).getIndexName();
			}
			return null;
		}
	},
	SEQUENCE_SCHEMA_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return SequenceSchemaNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SequenceSchemaNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((SequenceSchemaNameProperty<?>)obj).setSequenceSchemaName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SequenceSchemaNameProperty<?>)obj).getSequenceSchemaName();
			}
			return null;
		}
	},
	SEQUENCE_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return SequenceNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SequenceNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((SequenceNameProperty<?>)obj).setSequenceName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SequenceNameProperty<?>)obj).getSequenceName();
			}
			return null;
		}
	},
	FUNCTION_SCHEMA_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return FunctionSchemaNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof FunctionSchemaNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((FunctionSchemaNameProperty<?>)obj).setFunctionSchemaName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((FunctionSchemaNameProperty<?>)obj).getFunctionSchemaName();
			}
			return null;
		}
	},
	FUNCTION_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return FunctionNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof FunctionNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((FunctionNameProperty<?>)obj).setFunctionName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((FunctionNameProperty<?>)obj).getFunctionName();
			}
			return null;
		}
	},
	TABLE_SPACE_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return TableSpaceNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof TableSpaceNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((TableSpaceNameProperty<?>)obj).setTableSpaceName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((TableSpaceNameProperty<?>)obj).getTableSpaceName();
			}
			return null;
		}
	},
	INDEX_TABLE_SPACE_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return IndexTableSpaceNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IndexTableSpaceNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IndexTableSpaceNameProperty<?>)obj).setIndexTableSpaceName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IndexTableSpaceNameProperty<?>)obj).getIndexTableSpaceName();
			}
			return null;
		}
	},
	LOB_TABLE_SPACE_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return LobTableSpaceNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof LobTableSpaceNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((LobTableSpaceNameProperty<?>)obj).setLobTableSpaceName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((LobTableSpaceNameProperty<?>)obj).getLobTableSpaceName();
			}
			return null;
		}
	},
	TEMPORARY_TABLE_SPACE_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return TemporaryTableSpaceNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof TemporaryTableSpaceNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((TemporaryTableSpaceNameProperty<?>)obj).setTemporaryTableSpaceName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((TemporaryTableSpaceNameProperty<?>)obj).getTemporaryTableSpaceName();
			}
			return null;
		}
	},
	DEFAULT_DIRECTORY_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return DefaultDirectoryNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DefaultDirectoryNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DefaultDirectoryNameProperty<?>)obj).setDefaultDirectoryName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DefaultDirectoryNameProperty<?>)obj).getDefaultDirectoryName();
			}
			return null;
		}
	},
	DIRECTORY_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return DirectoryNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DirectoryNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DirectoryNameProperty<?>)obj).setDirectoryName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DirectoryNameProperty<?>)obj).getDirectoryName();
			}
			return null;
		}
	},
	OBJECT_SCHEMA_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return ObjectSchemaNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ObjectSchemaNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ObjectSchemaNameProperty<?>)obj).setObjectSchemaName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ObjectSchemaNameProperty<?>)obj).getObjectSchemaName();
			}
			return null;
		}
	},
	OBJECT_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return ObjectNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ObjectNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ObjectNameProperty<?>)obj).setObjectName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ObjectNameProperty<?>)obj).getObjectName();
			}
			return null;
		}
	},
	TYPE_SCHEMA_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return TypeSchemaNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof TypeSchemaNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((TypeSchemaNameProperty<?>)obj).setTypeSchemaName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((TypeSchemaNameProperty<?>)obj).getTypeSchemaName();
			}
			return null;
		}
	},
	TYPE_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return TypeNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof TypeNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((TypeNameProperty<?>)obj).setTypeName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((TypeNameProperty<?>)obj).getTypeName();
			}
			return null;
		}
	},
	DB_LINK_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return DbLinkNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DbLinkNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DbLinkNameProperty<?>)obj).setDbLinkName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DbLinkNameProperty<?>)obj).getDbLinkName();
			}
			return null;
		}
	},
	PARTITION_FUNCTION_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return PartitionFunctionNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PartitionFunctionNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((PartitionFunctionNameProperty<?>)obj).setPartitionFunctionName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PartitionFunctionNameProperty<?>)obj).getPartitionFunctionName();
			}
			return null;
		}
	},
	PARTITION_SCHEME_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return PartitionSchemeNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PartitionSchemeNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((PartitionSchemeNameProperty<?>)obj).setPartitionSchemeName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PartitionSchemeNameProperty<?>)obj).getPartitionSchemeName();
			}
			return null;
		}
	},
	MEMBER_ROLE_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return MemberRoleNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof MemberRoleNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((MemberRoleNameProperty<?>)obj).setMemberRoleName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((MemberRoleNameProperty<?>)obj).getMemberRoleName();
			}
			return null;
		}
	},
	OPERATOR_CLASS_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return OperatorClassNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OperatorClassNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((OperatorClassNameProperty<?>)obj).setOperatorClassName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OperatorClassNameProperty<?>)obj).getOperatorClassName();
			}
			return null;
		}
	},
	OPERATOR_SCHEMA_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return OperatorSchemaNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OperatorSchemaNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((OperatorSchemaNameProperty<?>)obj).setOperatorSchemaName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OperatorSchemaNameProperty<?>)obj).getOperatorSchemaName();
			}
			return null;
		}
	},
	OPERATOR_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return OperatorNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OperatorNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((OperatorNameProperty<?>)obj).setOperatorName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OperatorNameProperty<?>)obj).getOperatorName();
			}
			return null;
		}
	},
	RELATED_TABLE_SCHEMA_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return RelatedTableSchemaNameProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return RelatedTableSchemaNameGetter.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof RelatedTableSchemaNameProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof RelatedTableSchemaNameGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((RelatedTableSchemaNameProperty<?>)obj).setRelatedTableSchemaName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((RelatedTableSchemaNameGetter)obj).getRelatedTableSchemaName();
			}
			return null;
		}
	},
	RELATED_TABLE_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return RelatedTableNameProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return RelatedTableNameGetter.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof RelatedTableNameProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof RelatedTableNameGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((RelatedTableNameProperty<?>)obj).setRelatedTableName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((RelatedTableNameGetter)obj).getRelatedTableName();
			}
			return null;
		}
	},
	MASTER_TABLE_SCHEMA_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return MasterTableSchemaNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof MasterTableSchemaNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((MasterTableSchemaNameProperty<?>)obj).setMasterTableSchemaName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((MasterTableSchemaNameProperty<?>)obj).getMasterTableSchemaName();
			}
			return null;
		}
	},
	MASTER_TABLE_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return MasterTableNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof MasterTableNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((MasterTableNameProperty<?>)obj).setMasterTableName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((MasterTableNameProperty<?>)obj).getMasterTableName();
			}
			return null;
		}
	},
	ORDINAL(){
		@Override
		public final Class<?> getPropertyClass(){
			return OrdinalProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return OrdinalGetter.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OrdinalProperty;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof OrdinalGetter;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			return false;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OrdinalGetter)obj).getOrdinal();
			}
			return null;
		}
	},
	PRIMARY_KEY(){
		@Override
		public final Class<?> getPropertyClass(){
			return PrimaryKeyProperty.class;
		}
		@Override
		public Class<?> getGetterPropertyClass(){
			return PrimaryKeyGetter.class;
		}
		@Override
		public Class<?> getSetterPropertyClass(){
			return PrimaryKeySetter.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof PrimaryKeyGetter;
		}
		@Override
		public boolean isSetterInstanceof(final Object obj){
			return obj instanceof PrimaryKeySetter;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PrimaryKeyProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((PrimaryKeyProperty<?>)obj).setPrimaryKey(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PrimaryKeyProperty<?>)obj).isPrimaryKey();
			}
			return null;
		}
	},
	UNIQUE(){
		@Override
		public final Class<?> getPropertyClass(){
			return UniqueProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof UniqueProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((UniqueProperty<?>)obj).setUnique(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((UniqueProperty<?>)obj).isUnique();
			}
			return null;
		}
	},
	TABLE_TYPE(){
		@Override
		public final Class<?> getPropertyClass(){
			return TableTypeProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return TableType.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof TableTypeProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final TableTypeProperty<?> cst=((TableTypeProperty<?>)obj);
			cst.setTableType(converters.convertObject(value, TableType.class));
			return true;
		}
		@Override
		public TableType getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((TableTypeProperty<?>)obj).getTableType();
			}
			return null;
		}
	},
	TABLE_DATA_STORE_TYPE(){
		@Override
		public final Class<?> getPropertyClass(){
			return TableDataStoreTypeProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return TableDataStoreType.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof TableDataStoreTypeProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final TableDataStoreTypeProperty<?> cst=((TableDataStoreTypeProperty<?>)obj);
			cst.setTableDataStoreType(converters.convertObject(value, TableDataStoreType.class));
			return true;
		}
		@Override
		public TableDataStoreType getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((TableDataStoreTypeProperty<?>)obj).getTableDataStoreType();
			}
			return null;
		}
	},
	INDEX_TYPE(){
		@Override
		public final Class<?> getPropertyClass(){
			return IndexTypeProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return IndexType.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IndexTypeProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final IndexTypeProperty<?> cst=((IndexTypeProperty<?>)obj);
			if (value instanceof IndexType){
				cst.setIndexType((IndexType)value);
			} else{
				cst.setIndexType(toString(value));
			}
			return true;
		}
		@Override
		public IndexType getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IndexTypeProperty<?>)obj).getIndexType();
			}
			return null;
		}
	},
	FUNCTION_TYPE(){
		@Override
		public final Class<?> getPropertyClass(){
			return FunctionTypeProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return FunctionType.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof FunctionTypeProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final FunctionTypeProperty<?> cst=((FunctionTypeProperty<?>)obj);
			cst.setFunctionType(converters.convertObject(value,FunctionType.class));
			return true;
		}
		@Override
		public FunctionType getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((FunctionTypeProperty<?>)obj).getFunctionType();
			}
			return null;
		}
	},
	META_TYPE(){
		@Override
		public final Class<?> getPropertyClass(){
			return MetaTypeProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return MetaType.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof MetaTypeProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((MetaTypeProperty<?>)obj).setMetaType(converters.convertObject(value, MetaType.class));
			return true;
		}
		@Override
		public MetaType getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((MetaTypeProperty<?>)obj).getMetaType();
			}
			return null;
		}
	},
	EVENT_TYPE(){
		@Override
		public final Class<?> getPropertyClass(){
			return EventTypeProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return EventType.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof EventTypeProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final EventTypeProperty<?> cst=((EventTypeProperty<?>)obj);
			if (value instanceof EventType){
				cst.setEventType((EventType)value);
			} else{
				cst.setEventType(toString(value));
			}
			return true;
		}
		@Override
		public EventType getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((EventTypeProperty<?>)obj).getEventType();
			}
			return null;
		}
	},
	ACCESS_TYPE(){
		@Override
		public final Class<?> getPropertyClass(){
			return AccessTypeProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof AccessTypeProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((AccessTypeProperty<?>)obj).setAccessType(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((AccessTypeProperty<?>)obj).getAccessType();
			}
			return null;
		}
	},
	FILE_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return FileNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof FileNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final FileNameProperty<?> cst=((FileNameProperty<?>)obj);
			cst.setFileName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((FileNameProperty<?>)obj).getFileName();
			}
			return null;
		}
	},
	USER_ID(){
		@Override
		public final Class<?> getPropertyClass(){
			return UserIdProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof UserIdProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((UserIdProperty<?>)obj).setUserId(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((UserIdProperty<?>)obj).getUserId();
			}
			return null;
		}
	},
	USER_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return UserNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof UserNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((UserNameProperty<?>)obj).setUserName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((UserNameProperty<?>)obj).getUserName();
			}
			return null;
		}
	},
	PASSWORD(){
		@Override
		public final Class<?> getPropertyClass(){
			return PasswordProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PasswordProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((PasswordProperty<?>)obj).setPassword(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PasswordProperty<?>)obj).getPassword();
			}
			return null;
		}
	},
	PASSWORD_ENCRYPTED(){
		@Override
		public final Class<?> getPropertyClass(){
			return PasswordEncryptedProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PasswordEncryptedProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((PasswordEncryptedProperty<?>)obj).setPasswordEncrypted(converters.convertObject(value, boolean.class));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PasswordEncryptedProperty<?>)obj).isPasswordEncrypted();
			}
			return null;
		}
	},
	LOGIN_USER_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return LoginUserNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof LoginUserNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((LoginUserNameProperty<?>)obj).setLoginUserName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((LoginUserNameProperty<?>)obj).getLoginUserName();
			}
			return null;
		}
	},
	OWNER_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return OwnerNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OwnerNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((OwnerNameProperty<?>)obj).setOwnerName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OwnerNameProperty<?>)obj).getOwnerName();
			}
			return null;
		}
	},
	STABLE(){
		@Override
		public final Class<?> getPropertyClass(){
			return StableProperty.class;
		}
		@Override
		public final Class<?> getValueClass(){
			return Boolean.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof StableProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((StableProperty<?>)obj).setStable(converters.convertObject(value, Boolean.class));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((StableProperty<?>)obj).getStable();
			}
			return null;
		}
	},
	ON_NULL_CALL(){
		@Override
		public final Class<?> getPropertyClass(){
			return OnNullCallProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return OnNullCall.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OnNullCallProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final OnNullCallProperty<?> cst=((OnNullCallProperty<?>)obj);
			if (value instanceof OnNullCall){
				cst.setOnNullCall((OnNullCall)value);
			} else{
				cst.setOnNullCall(converters.convertString(value));
			}
			return true;
		}
		@Override
		public OnNullCall getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OnNullCallProperty<?>)obj).getOnNullCall();
			}
			return null;
		}
	},
	DIRECTORY_PATH(){
		@Override
		public final Class<?> getPropertyClass(){
			return DirectoryPathProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DirectoryPathProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DirectoryPathProperty<?>)obj).setDirectoryPath(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DirectoryPathProperty<?>)obj).getDirectoryPath();
			}
			return null;
		}
	},
	FILE_PATH(){
		@Override
		public final Class<?> getPropertyClass(){
			return FilePathProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof FilePathProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((FilePathProperty<?>)obj).setFilePath(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((FilePathProperty<?>)obj).getFilePath();
			}
			return null;
		}
	},
	LOCATION(){
		@Override
		public final Class<?> getPropertyClass(){
			return LocationProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof LocationProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((LocationProperty<?>)obj).setLocation(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((LocationProperty<?>)obj).getLocation();
			}
			return null;
		}
	},
	AUTO_EXTENSIBLE(){
		@Override
		public final Class<?> getPropertyClass(){
			return AutoExtensibleProperty.class;
		}
		@Override
		public final Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public final Boolean getDefaultValue(){
			return Boolean.TRUE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof AutoExtensibleProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((AutoExtensibleProperty<?>)obj).setAutoExtensible(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((AutoExtensibleProperty<?>)obj).isAutoExtensible();
			}
			return null;
		}
	},
	REJECT_LIMIT(){
		@Override
		public final Class<?> getPropertyClass(){
			return RejectLimitProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof RejectLimitProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((RejectLimitProperty<?>)obj).setRejectLimit(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((RejectLimitProperty<?>)obj).getRejectLimit();
			}
			return null;
		}
	},
	ACCESS_PARAMETERS(){
		@Override
		public final Class<?> getPropertyClass(){
			return AccessParametersProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof AccessParametersProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((AccessParametersProperty<?>)obj).setAccessParameters(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((AccessParametersProperty<?>)obj).getAccessParameters();
			}
			return null;
		}
	},
	PROPERTY(){
		@Override
		public final Class<?> getPropertyClass(){
			return PropertyProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PropertyProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((PropertyProperty<?>)obj).setProperty(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PropertyProperty<?>)obj).getProperty();
			}
			return null;
		}
	},
	CLASS_NAME_PREFIX(){
		@Override
		public final Class<?> getPropertyClass(){
			return ClassNamePrefixProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ClassNamePrefixProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ClassNamePrefixProperty<?>)obj).setClassNamePrefix(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ClassNamePrefixProperty<?>)obj).getClassNamePrefix();
			}
			return null;
		}
	},
	CLASS_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return ClassNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ClassNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ClassNameProperty<?>)obj).setClassName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ClassNameProperty<?>)obj).getClassName();
			}
			return null;
		}
	},
	METHOD_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return MethodNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof MethodNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((MethodNameProperty<?>)obj).setMethodName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((MethodNameProperty<?>)obj).getMethodName();
			}
			return null;
		}
	},
	DRIVER_CLASS_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return DriverClassNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DriverClassNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DriverClassNameProperty<?>)obj).setDriverClassName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DriverClassNameProperty<?>)obj).getDriverClassName();
			}
			return null;
		}
	},
	DATA_SOURCE(){
		@Override
		public final Class<?> getPropertyClass(){
			return DataSourceProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DataSourceProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DataSourceProperty<?>)obj).setDataSource(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DataSourceProperty<?>)obj).getDataSource();
			}
			return null;
		}
	},
	CONNECTION_CATALOG(){
		@Override
		public final Class<?> getPropertyClass(){
			return ConnectionCatalogProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ConnectionCatalogProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ConnectionCatalogProperty<?>)obj).setConnectionCatalog(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ConnectionCatalogProperty<?>)obj).getConnectionCatalog();
			}
			return null;
		}
	},
	READONLY(){
		@Override
		public final Class<?> getPropertyClass(){
			return ReadonlyProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Boolean.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ReadonlyProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ReadonlyProperty<?>)obj).setReadonly(converters.convertObject(value, Boolean.class));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ReadonlyProperty<?>)obj).getReadonly();
			}
			return null;
		}
	},
	LANGUAGE(){
		@Override
		public final Class<?> getPropertyClass(){
			return LanguageProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof LanguageProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((LanguageProperty<?>)obj).setLanguage(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((LanguageProperty<?>)obj).getLanguage();
			}
			return null;
		}
	},
	DIRECTION(){
		@Override
		public final Class<?> getPropertyClass(){
			return DirectionProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return ParameterDirection.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DirectionProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final DirectionProperty<?> cst=((DirectionProperty<?>)obj);
			if (value instanceof ParameterDirection){
				cst.setDirection((ParameterDirection)value);
			} else{
				cst.setDirection(converters.convertString(value));
			}
			return true;
		}
		@Override
		public ParameterDirection getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DirectionProperty<?>)obj).getDirection();
			}
			return null;
		}
	},
	DATA_TYPE(){
		@Override
		public final Class<?> getPropertyClass(){
			return DataTypeProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return DataType.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DataTypeProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final DataTypeProperty<?> cst=((DataTypeProperty<?>)obj);
			if (value instanceof DataType){
				cst.setDataType((DataType)value);
			} else if (value instanceof Integer){
				cst.setDataType(DataType.valueOf((Integer)value));
			} else{
				cst.setDataType(converters.convertString(value));
			}
			return true;
		}
		@Override
		public DataType getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DataTypeProperty<?>)obj).getDataType();
			}
			return null;
		}
	},
	DATA_TYPE_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return DataTypeNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DataTypeNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DataTypeNameProperty<?>)obj).setDataTypeName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DataTypeNameProperty<?>)obj).getDataTypeName();
			}
			return null;
		}
	},
	LENGTH(){
		@Override
		public final Class<?> getPropertyClass(){
			return LengthProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Long.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof LengthProperty;
		}
		@Override
		public final SchemaProperties getDependent(){
			return DATA_TYPE;
		}
		@Override
		public boolean isEnabled(final Object obj){
			final SchemaProperties dependent=getDependent();
			if (dependent!=null&&dependent.isInstanceof(obj)){
				final DataType dataType=(DataType)dependent.getValue(obj);
				if (dataType==null||dataType.isFixedSize()){
					return true;
				}
				return dependent.isEnabled(obj);
			}
			return false;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((LengthProperty<?>)obj).setLength(converters.convertObject(value, Long.class));
			return true;
		}
		@Override
		public Long getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((LengthProperty<?>)obj).getLength();
			}
			return null;
		}
	},
	STRING_UNITS(){
		@Override
		public final Class<?> getPropertyClass(){
			return StringUnitsProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof StringUnitsProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final StringUnitsProperty<?> cst=((StringUnitsProperty<?>)obj);
			cst.setStringUnits(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((StringUnitsProperty<?>)obj).getStringUnits();
			}
			return null;
		}
	},
	OCTET_LENGTH(){
		@Override
		public final Class<?> getPropertyClass(){
			return OctetLengthProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Long.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OctetLengthProperty;
		}
		@Override
		public final SchemaProperties getDependent(){
			return LENGTH;
		}
		@Override
		public boolean isEnabled(final Object obj){
			final SchemaProperties dependent=getDependent();
			if (dependent!=null&&dependent.isInstanceof(obj)){
				final Long maxLength=(Long)dependent.getValue(obj);
				if (!CommonUtils.eq(maxLength, this.getValue(obj))){
					return true;
				}
				return dependent.isEnabled(obj);
			}
			return false;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((OctetLengthProperty<?>)obj).setOctetLength(converters.convertObject(value, Long.class));
			return true;
		}
		@Override
		public Long getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OctetLengthProperty<?>)obj).getOctetLength();
			}
			return null;
		}
	},
	PRECISION(){
		@Override
		public final Class<?> getPropertyClass(){
			return PrecisionProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Integer.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PrecisionProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((PrecisionProperty<?>)obj).setPrecision(converters.convertObject(value, Integer.class));
			return true;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PrecisionProperty<?>)obj).getPrecision();
			}
			return null;
		}
	},
	SCALE(){
		@Override
		public final Class<?> getPropertyClass(){
			return ScaleProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Integer.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ScaleProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ScaleProperty<?>)obj).setScale(converters.convertObject(value, Integer.class));
			return true;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ScaleProperty<?>)obj).getScale();
			}
			return null;
		}
	},
	ARRAY_DIMENSION(){
		@Override
		public final Class<?> getPropertyClass(){
			return ArrayDimensionProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return int.class;
		}
		@Override
		public Integer getDefaultValue(){
			return Integer.valueOf(0);
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ArrayDimensionProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ArrayDimensionProperty<?>)obj).setArrayDimension(toInt(value));
			return true;
		}
		@Override
		public boolean isEnabled(final Object obj){
			final Integer value=getValue(obj);
			return value!=null&&value.intValue()>0;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ArrayDimensionProperty<?>)obj).getArrayDimension();
			}
			return null;
		}
	},
	ARRAY_DIMENSION_LOWER_BOUND(){
		@Override
		public final Class<?> getPropertyClass(){
			return ArrayDimensionLowerBoundProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return int.class;
		}
		@Override
		public Integer getDefaultValue(){
			return Integer.valueOf(0);
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ArrayDimensionLowerBoundProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ArrayDimensionLowerBoundProperty<?>)obj).setArrayDimensionLowerBound(toInt(value));
			return true;
		}
		@Override
		public SchemaProperties getDependent(){
			return ARRAY_DIMENSION;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ArrayDimensionLowerBoundProperty<?>)obj).getArrayDimensionLowerBound();
			}
			return null;
		}
	},
	ARRAY_DIMENSION_UPPER_BOUND(){
		@Override
		public final Class<?> getPropertyClass(){
			return ArrayDimensionUpperBoundProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return int.class;
		}
		@Override
		public Integer getDefaultValue(){
			return Integer.valueOf(0);
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ArrayDimensionUpperBoundProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ArrayDimensionUpperBoundProperty<?>)obj).setArrayDimensionUpperBound(toInt(value));
			return true;
		}
		@Override
		public SchemaProperties getDependent(){
			return ARRAY_DIMENSION;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ArrayDimensionUpperBoundProperty<?>)obj).getArrayDimensionUpperBound();
			}
			return null;
		}
	},
	NOT_NULL(){
		@Override
		public final Class<?> getPropertyClass(){
			return NotNullProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof NotNullProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((NotNullProperty<?>)obj).setNotNull(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((NotNullProperty<?>)obj).isNotNull();
			}
			return null;
		}
	},
	DEFAULT_VALUE(){
		@Override
		public final Class<?> getPropertyClass(){
			return DefaultValueProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DefaultValueProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final DefaultValueProperty<?> cst=((DefaultValueProperty<?>)obj);
			cst.setDefaultValue(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DefaultValueProperty<?>)obj).getDefaultValue();
			}
			return null;
		}
	},
	IDENTITY(){
		@Override
		public final Class<?> getPropertyClass(){
			return IdentityProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IdentityProperty;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IdentityProperty<?>)obj).setIdentity(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IdentityProperty<?>)obj).isIdentity();
			}
			return null;
		}
	},
	IDENTITY_START_VALUE(){
		@Override
		public final Class<?> getPropertyClass(){
			return IdentityStartValueProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Long.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IdentityStartValueProperty;
		}
		@Override
		public final SchemaProperties getDependent(){
			return IDENTITY;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IdentityStartValueProperty<?>)obj).setIdentityStartValue(converters.convertObject(value, Long.class));
			return true;
		}
		@Override
		public Long getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IdentityStartValueProperty<?>)obj).getIdentityStartValue();
			}
			return null;
		}
	},
	IDENTITY_MAX_VALUE(){
		@Override
		public final Class<?> getPropertyClass(){
			return IdentityMaxValueProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Long.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IdentityMaxValueProperty;
		}
		@Override
		public final SchemaProperties getDependent(){
			return IDENTITY;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IdentityMaxValueProperty<?>)obj).setIdentityMaxValue(converters.convertObject(value, Long.class));
			return true;
		}
		@Override
		public Long getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IdentityMaxValueProperty<?>)obj).getIdentityMaxValue();
			}
			return null;
		}
	},
	IDENTITY_MIN_VALUE(){
		@Override
		public final Class<?> getPropertyClass(){
			return IdentityMinValueProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Long.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IdentityMinValueProperty;
		}
		@Override
		public final SchemaProperties getDependent(){
			return IDENTITY;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IdentityMinValueProperty<?>)obj).setIdentityMinValue(converters.convertObject(value, Long.class));
			return true;
		}
		@Override
		public Long getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IdentityMinValueProperty<?>)obj).getIdentityMinValue();
			}
			return null;
		}
	},
	IDENTITY_STEP(){
		@Override
		public final Class<?> getPropertyClass(){
			return IdentityStepProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Long.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IdentityStepProperty;
		}
		@Override
		public final SchemaProperties getDependent(){
			return IDENTITY;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IdentityStepProperty<?>)obj).setIdentityStep(converters.convertObject(value, Long.class));
			return true;
		}
		@Override
		public Long getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IdentityStepProperty<?>)obj).getIdentityStep();
			}
			return null;
		}
	},
	IDENTITY_LAST_VALUE(){
		@Override
		public final Class<?> getPropertyClass(){
			return IdentityLastValueProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Long.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IdentityLastValueProperty;
		}
		@Override
		public final SchemaProperties getDependent(){
			return IDENTITY;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IdentityLastValueProperty<?>)obj).setIdentityLastValue(converters.convertObject(value, Long.class));
			return true;
		}
		@Override
		public Long getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IdentityLastValueProperty<?>)obj).getIdentityLastValue();
			}
			return null;
		}
	},
	IDENTITY_CACHE(){
		@Override
		public final Class<?> getPropertyClass(){
			return IdentityCacheProperty.class;
		}
		@Override
		public final Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public final boolean isInstanceof(final Object obj){
			return obj instanceof IdentityCacheProperty;
		}
		@Override
		public final Boolean getDefaultValue(){
			return Boolean.TRUE;
		}
		@Override
		public final SchemaProperties getDependent(){
			return IDENTITY;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IdentityCacheProperty<?>)obj).setIdentityCache(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IdentityCacheProperty<?>)obj).isIdentityCache();
			}
			return null;
		}
	},
	IDENTITY_CACHE_SIZE(){
		@Override
		public final Class<?> getPropertyClass(){
			return IdentityCacheSizeProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Integer.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IdentityMaxValueProperty;
		}
		@Override
		public final SchemaProperties getDependent(){
			return IDENTITY;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IdentityCacheSizeProperty<?>)obj).setIdentityCacheSize(converters.convertObject(value, Integer.class));
			return true;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IdentityCacheSizeProperty<?>)obj).getIdentityCacheSize();
			}
			return null;
		}
	},
	IDENTITY_CYCLE(){
		@Override
		public final Class<?> getPropertyClass(){
			return IdentityCycleProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IdentityCycleProperty;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public final SchemaProperties getDependent(){
			return IDENTITY;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IdentityCycleProperty<?>)obj).setIdentityCycle(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IdentityCycleProperty<?>)obj).isIdentityCycle();
			}
			return null;
		}
	},
	IDENTITY_ORDER(){
		@Override
		public final Class<?> getPropertyClass(){
			return IdentityOrderProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IdentityOrderProperty;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public final SchemaProperties getDependent(){
			return IDENTITY;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IdentityOrderProperty<?>)obj).setIdentityOrder(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IdentityOrderProperty<?>)obj).isIdentityOrder();
			}
			return null;
		}
	},
	IDENTITY_GENERATION_TYPE(){
		@Override
		public final Class<?> getPropertyClass(){
			return IdentityGenerationTypeProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return IdentityGenerationType.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IdentityGenerationTypeProperty;
		}
		@Override
		public final SchemaProperties getDependent(){
			return IDENTITY;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IdentityGenerationTypeProperty<?>)obj).setIdentityGenerationType(converters.convertObject(value, IdentityGenerationType.class));
			return true;
		}
		@Override
		public IdentityGenerationType getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IdentityGenerationTypeProperty<?>)obj).getIdentityGenerationType();
			}
			return null;
		}
	},
	CHECK(){
		@Override
		public final Class<?> getPropertyClass(){
			return CheckProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof CheckProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final CheckProperty<?> cst=((CheckProperty<?>)obj);
			cst.setCheck(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((CheckProperty<?>)obj).getCheck();
			}
			return null;
		}
	},
	EXPRESSION(){
		@Override
		public final Class<?> getPropertyClass(){
			return ExpressionProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ExpressionProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final ExpressionProperty<?> cst=((ExpressionProperty<?>)obj);
			cst.setExpression(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ExpressionProperty<?>)obj).getExpression();
			}
			return null;
		}
	},
	MASKING_FUNCTION(){
		@Override
		public final Class<?> getPropertyClass(){
			return MaskingFunctionProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof MaskingFunctionProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final MaskingFunctionProperty<?> cst=((MaskingFunctionProperty<?>)obj);
			cst.setMaskingFunction(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((MaskingFunctionProperty<?>)obj).getMaskingFunction();
			}
			return null;
		}
	},
	FORMULA(){
		@Override
		public final Class<?> getPropertyClass(){
			return FormulaProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof FormulaProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final FormulaProperty<?> cst=((FormulaProperty<?>)obj);
			cst.setFormula(toString(value));
			return true;
		}
		@Override
		public boolean isEnabled(final Object obj){
			return !CommonUtils.isEmpty(getValue(obj));
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((FormulaProperty<?>)obj).getFormula();
			}
			return null;
		}
	},
	FORMULA_PERSISTED(){
		@Override
		public final Class<?> getPropertyClass(){
			return FormulaPersistedProperty.class;
		}
		@Override
		public final Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public final Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public final boolean isInstanceof(final Object obj){
			return obj instanceof FormulaPersistedProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((FormulaPersistedProperty<?>)obj).setFormulaPersisted(toBoolean(value));
			return true;
		}
		@Override
		public final SchemaProperties getDependent(){
			return FORMULA;
		}
		@Override
		public final Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((FormulaPersistedProperty<?>)obj).isFormulaPersisted();
			}
			return null;
		}
	},
	ADMIN(){
		@Override
		public final Class<?> getPropertyClass(){
			return AdminProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof AdminProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((AdminProperty<?>)obj).setAdmin(converters.convertObject(value, boolean.class));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((AdminProperty<?>)obj).isAdmin();
			}
			return null;
		}
	},
	HIDDEN(){
		@Override
		public final Class<?> getPropertyClass(){
			return HiddenProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof HiddenProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((HiddenProperty<?>)obj).setHidden(converters.convertObject(value, boolean.class));
			return true;
		}

		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((HiddenProperty<?>)obj).isHidden();
			}
			return null;
		}
	},
	CHARACTER_SEMANTICS(){
		@Override
		public final Class<?> getPropertyClass(){
			return CharacterSemanticsProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return CharacterSemantics.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof CharacterSemanticsProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final CharacterSemanticsProperty<?> cst=((CharacterSemanticsProperty<?>)obj);
			if (value instanceof CharacterSemantics){
				cst.setCharacterSemantics((CharacterSemantics)value);
			} else{
				cst.setCharacterSemantics(converters.convertString(value));
			}
			return true;
		}
		@Override
		public CharacterSemantics getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((CharacterSemanticsProperty<?>)obj).getCharacterSemantics();
			}
			return null;
		}
	},
	CHARACTER_SET(){
		@Override
		public final Class<?> getPropertyClass(){
			return CharacterSetProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof CharacterSetProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final CharacterSetProperty<?> cst=((CharacterSetProperty<?>)obj);
			cst.setCharacterSet(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((CharacterSetProperty<?>)obj).getCharacterSet();
			}
			return null;
		}
	},
	COLLATION(){
		@Override
		public final Class<?> getPropertyClass(){
			return CollationProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof CollationProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final CollationProperty<?> cst=((CollationProperty<?>)obj);
			cst.setCollation(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((CollationProperty<?>)obj).getCollation();
			}
			return null;
		}
	},
	ORDER(){
		@Override
		public final Class<?> getPropertyClass(){
			return OrderProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Order.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OrderProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final OrderProperty<?> cst=((OrderProperty<?>)obj);
			if (value instanceof Order){
				cst.setOrder((Order)value);
			} else{
				cst.setOrder(toString(value));
			}
			return true;
		}
		@Override
		public Order getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OrderProperty<?>)obj).getOrder();
			}
			return null;
		}
	},
	NULLS_ORDER(){
		@Override
		public final Class<?> getPropertyClass(){
			return NullsOrderProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return NullsOrder.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof NullsOrderProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final NullsOrderProperty<?> cst=((NullsOrderProperty<?>)obj);
			if (value instanceof NullsOrder){
				cst.setNullsOrder((NullsOrder)value);
			} else{
				cst.setNullsOrder(toString(value));
			}
			return true;
		}
		@Override
		public NullsOrder getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((NullsOrderProperty<?>)obj).getNullsOrder();
			}
			return null;
		}
	},
	INCLUDED_COLUMN(){
		@Override
		public final Class<?> getPropertyClass(){
			return IncludedColumnProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IncludedColumnProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IncludedColumnProperty<?>)obj).setIncludedColumn(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IncludedColumnProperty<?>)obj).isIncludedColumn();
			}
			return null;
		}
	},
	UPDATE_RULE(){
		@Override
		public final Class<?> getPropertyClass(){
			return UpdateRuleProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return CascadeRule.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof UpdateRuleProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final UpdateRuleProperty<?> cst=((UpdateRuleProperty<?>)obj);
			if (value instanceof CascadeRule){
				cst.setUpdateRule((CascadeRule)value);
			} else{
				cst.setUpdateRule(toString(value));
			}
			return true;
		}
		@Override
		public CascadeRule getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((UpdateRuleProperty<?>)obj).getUpdateRule();
			}
			return null;
		}
	},
	DELETE_RULE(){
		@Override
		public final Class<?> getPropertyClass(){
			return DeleteRuleProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return CascadeRule.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DeleteRuleProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final DeleteRuleProperty<?> cst=((DeleteRuleProperty<?>)obj);
			if (value instanceof CascadeRule){
				cst.setDeleteRule((CascadeRule)value);
			} else{
				cst.setDeleteRule(toString(value));
			}
			return true;
		}
		@Override
		public CascadeRule getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DeleteRuleProperty<?>)obj).getDeleteRule();
			}
			return null;
		}
	},
	MATCH_OPTION(){
		@Override
		public final Class<?> getPropertyClass(){
			return MatchOptionProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return MatchOption.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof MatchOptionProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final MatchOptionProperty<?> cst=((MatchOptionProperty<?>)obj);
			if (value instanceof MatchOption){
				cst.setMatchOption((MatchOption)value);
			} else{
				cst.setMatchOption(toString(value));
			}
			return true;
		}
		@Override
		public MatchOption getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((MatchOptionProperty<?>)obj).getMatchOption();
			}
			return null;
		}
	},
	LOW_VALUE(){
		@Override
		public final Class<?> getPropertyClass(){
			return LowValueProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof LowValueProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((LowValueProperty<?>)obj).setLowValue(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((LowValueProperty<?>)obj).getLowValue();
			}
			return null;
		}
	},
	LOW_VALUE_INCLUSIVE(){
		@Override
		public final Class<?> getPropertyClass(){
			return LowValueInclusiveProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.TRUE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof LowValueInclusiveProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((LowValueInclusiveProperty<?>)obj).setLowValueInclusive(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((LowValueInclusiveProperty<?>)obj).isLowValueInclusive();
			}
			return null;
		}
	},
	HIGH_VALUE(){
		@Override
		public final Class<?> getPropertyClass(){
			return HighValueProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof HighValueProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((HighValueProperty<?>)obj).setHighValue(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((HighValueProperty<?>)obj).getHighValue();
			}
			return null;
		}
	},
	HIGH_VALUE_INCLUSIVE(){
		@Override
		public final Class<?> getPropertyClass(){
			return HighValueInclusiveProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof HighValueInclusiveProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((HighValueInclusiveProperty<?>)obj).setHighValueInclusive(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((HighValueInclusiveProperty<?>)obj).isHighValueInclusive();
			}
			return null;
		}
	},
	COMPRESSION(){
		@Override
		public final Class<?> getPropertyClass(){
			return CompressionProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof CompressionProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((CompressionProperty<?>)obj).setCompression(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((CompressionProperty<?>)obj).isCompression();
			}
			return null;
		}
	},
	COMPRESSION_TYPE(){
		@Override
		public final Class<?> getPropertyClass(){
			return CompressionTypeProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return String.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof CompressionTypeProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((CompressionTypeProperty<?>)obj).setCompressionType(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((CompressionTypeProperty<?>)obj).getCompressionType();
			}
			return null;
		}
	},
	ON_UPDATE(){
		@Override
		public final Class<?> getPropertyClass(){
			return OnUpdateProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OnUpdateProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((OnUpdateProperty<?>)obj).setOnUpdate(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OnUpdateProperty<?>)obj).getOnUpdate();
			}
			return null;
		}
	},
	DEFERRABILITY(){
		@Override
		public final Class<?> getPropertyClass(){
			return DeferrabilityProperty.class;
		}
		@Override
		public final Class<?> getValueClass(){
			return Deferrability.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DeferrabilityProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final DeferrabilityProperty<?> cst=((DeferrabilityProperty<?>)obj);
			if (value instanceof Deferrability){
				cst.setDeferrability((Deferrability)value);
			} else{
				cst.setDeferrability(converters.convertString(value));
			}
			return true;
		}
		@Override
		public Deferrability getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DeferrabilityProperty<?>)obj).getDeferrability();
			}
			return null;
		}
	},
	DEFINER(){
		@Override
		public final Class<?> getPropertyClass(){
			return DefinerProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DefinerProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DefinerProperty<?>)obj).setDefiner(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DefinerProperty<?>)obj).getDefiner();
			}
			return null;
		}
	},
	//SEQUENCE
	MIN_VALUE(){
		@Override
		public final Class<?> getPropertyClass(){
			return MinValueProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return BigInteger.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof MinValueProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((MinValueProperty<?>)obj).setMinValue(converters.convertObject(value, BigInteger.class));
			return true;
		}
		@Override
		public BigInteger getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((MinValueProperty<?>)obj).getMinValue();
			}
			return null;
		}
	}
	,
	MAX_VALUE(){
		@Override
		public final Class<?> getPropertyClass(){
			return MaxValueProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return BigInteger.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof MaxValueProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((MaxValueProperty<?>)obj).setMaxValue(converters.convertObject(value, BigInteger.class));
			return true;
		}
		@Override
		public BigInteger getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((MaxValueProperty<?>)obj).getMaxValue();
			}
			return null;
		}
	}
	,
	INCREMENT_BY(){
		@Override
		public final Class<?> getPropertyClass(){
			return IncrementByProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return BigInteger.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IncrementByProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IncrementByProperty<?>)obj).setIncrementBy(converters.convertObject(value, BigInteger.class));
			return true;
		}
		@Override
		public BigInteger getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IncrementByProperty<?>)obj).getIncrementBy();
			}
			return null;
		}
	}
	,
	CACHE_SIZE(){
		@Override
		public final Class<?> getPropertyClass(){
			return CacheSizeProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Integer.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof CacheSizeProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((CacheSizeProperty<?>)obj).setCacheSize(converters.convertObject(value, Integer.class));
			return true;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((CacheSizeProperty<?>)obj).getCacheSize();
			}
			return null;
		}
	}
	,
	START_VALUE(){
		@Override
		public final Class<?> getPropertyClass(){
			return StartValueProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return BigInteger.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof StartValueProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((StartValueProperty<?>)obj).setStartValue(converters.convertObject(value, BigInteger.class));
			return true;
		}
		@Override
		public BigInteger getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((StartValueProperty<?>)obj).getStartValue();
			}
			return null;
		}
	}
	,
	LAST_VALUE(){
		@Override
		public final Class<?> getPropertyClass(){
			return LastValueProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return BigInteger.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof LastValueProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((LastValueProperty<?>)obj).setLastValue(converters.convertObject(value, BigInteger.class));
			return true;
		}
		@Override
		public BigInteger getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((LastValueProperty<?>)obj).getLastValue();
			}
			return null;
		}
	}
	,
	CYCLE(){
		@Override
		public final Class<?> getPropertyClass(){
			return CycleProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof CycleProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((CycleProperty<?>)obj).setCycle(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((CycleProperty<?>)obj).isCycle();
			}
			return null;
		}
	}
	,
	CACHE(){
		@Override
		public final Class<?> getPropertyClass(){
			return CacheProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.TRUE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof CacheProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((CacheProperty<?>)obj).setCache(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((CacheProperty<?>)obj).isCache();
			}
			return null;
		}
	}
	,
	SEQUENCE_ORDER("order"){
		@Override
		public final Class<?> getPropertyClass(){
			return SequenceOrderProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SequenceOrderProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((SequenceOrderProperty<?>)obj).setOrder(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SequenceOrderProperty<?>)obj).isOrder();
			}
			return null;
		}
	}
	,
	PARTITIONING_TYPE(){
		@Override
		public final Class<?> getPropertyClass(){
			return PartitioningTypeProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return PartitioningType.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PartitioningTypeProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((PartitioningTypeProperty<?>)obj).setPartitioningType(converters.convertObject(value, PartitioningType.class));
			return true;
		}
		@Override
		public PartitioningType getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PartitioningTypeProperty<?>)obj).getPartitioningType();
			}
			return null;
		}
	}
	,
	SUB_PARTITIONING_TYPE(){
		@Override
		public final Class<?> getPropertyClass(){
			return SubPartitioningTypeProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return PartitioningType.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SubPartitioningTypeProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((SubPartitioningTypeProperty<?>)obj).setSubPartitioningType(converters.convertObject(value, PartitioningType.class));
			return true;
		}
		@Override
		public PartitioningType getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SubPartitioningTypeProperty<?>)obj).getSubPartitioningType();
			}
			return null;
		}
	}
	,
	PARTITION_SIZE(){
		@Override
		public final Class<?> getPropertyClass(){
			return PartitionSizeProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Integer.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PartitionSizeProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((PartitionSizeProperty<?>)obj).setPartitionSize(converters.convertObject(value, Integer.class));
			return true;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PartitionSizeProperty<?>)obj).getPartitionSize();
			}
			return null;
		}
	}
	,
	SUB_PARTITION_SIZE(){
		@Override
		public final Class<?> getPropertyClass(){
			return SubPartitionSizeProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Integer.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SubPartitionSizeProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((SubPartitionSizeProperty<?>)obj).setSubPartitionSize(converters.convertObject(value, Integer.class));
			return true;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SubPartitionSizeProperty<?>)obj).getSubPartitionSize();
			}
			return null;
		}
	}
	//Trigger
	,
	EVENT_MANIPULATION(){
		@Override
		public final Class<?> getPropertyClass(){
			return EventManipulationProperty.class;
		}
		@Override
		public final Class<?> getValueClass(){
			return Set.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof EventManipulationProperty;
		}
		@SuppressWarnings("unchecked")
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final EventManipulationProperty<?> target=((EventManipulationProperty<?>)obj);
			if (value instanceof String){
				target.setEventManipulation((String)value);
			} else if (value instanceof Set){
				target.addEventManipulation((Set<String>)value);
			}
			return true;
		}
		@Override
		public Set<String> getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((EventManipulationProperty<?>)obj).getEventManipulation();
			}
			return null;
		}
	}
	,
	ACTION_TIMING(){
		@Override
		public final Class<?> getPropertyClass(){
			return ActionTimingProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ActionTimingProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ActionTimingProperty<?>)obj).setActionTiming(converters.convertString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ActionTimingProperty<?>)obj).getActionTiming();
			}
			return null;
		}
	}
	,
	ACTION_CONDITION(){
		@Override
		public final Class<?> getPropertyClass(){
			return ActionConditionProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ActionConditionProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ActionConditionProperty<?>)obj).setActionCondition(converters.convertString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ActionConditionProperty<?>)obj).getActionCondition();
			}
			return null;
		}
	}
	,
	ACTION_ORIENTATION(){
		@Override
		public final Class<?> getPropertyClass(){
			return ActionOrientationProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ActionOrientationProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ActionOrientationProperty<?>)obj).setActionOrientation(converters.convertString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ActionOrientationProperty<?>)obj).getActionOrientation();
			}
			return null;
		}
	}
	,
	ACTION_REFERENCE_OLD_ROW(){
		@Override
		public final Class<?> getPropertyClass(){
			return ActionReferenceOldRowProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ActionReferenceOldRowProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ActionReferenceOldRowProperty<?>)obj).setActionReferenceOldRow(converters.convertString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ActionReferenceOldRowProperty<?>)obj).getActionReferenceOldRow();
			}
			return null;
		}
	}
	,
	ACTION_REFERENCE_NEW_ROW(){
		@Override
		public final Class<?> getPropertyClass(){
			return ActionReferenceNewRowProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ActionReferenceNewRowProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ActionReferenceNewRowProperty<?>)obj).setActionReferenceNewRow(converters.convertString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ActionReferenceNewRowProperty<?>)obj).getActionReferenceNewRow();
			}
			return null;
		}
	}
	,
	BOUNDARY_VALUE_ON_RIGHT(){
		@Override
		public final Class<?> getPropertyClass(){
			return BoundaryValueOnRightProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.TRUE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof BoundaryValueOnRightProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((BoundaryValueOnRightProperty<?>)obj).setBoundaryValueOnRight(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((BoundaryValueOnRightProperty<?>)obj).isBoundaryValueOnRight();
			}
			return null;
		}
	},
	PARTITION_FUNCTION_VALUES("values"){
		@Override
		public final Class<?> getPropertyClass(){
			return PartitionFunctionValuesProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return PatitionFunctionValues.class;
		}
		@Override
		public PatitionFunctionValues getDefaultValue(){
			return new PatitionFunctionValues();
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PartitionFunctionValuesProperty;
		}
		@Override
		public PatitionFunctionValues getCloneValue(final Object obj){
			final PatitionFunctionValues value=getValue(obj);
			if (value==null){
				return null;
			}
			return value.clone();
		}
		@Override
		public List<StaxElementHandler> getXmlHandlers(){
			final List<StaxElementHandler> handlers=CommonUtils.list();
			handlers.add(new SetHandler());
			handlers.add(new ValueHandler());
			handlers.add(new EmptyTextSkipHandler());
			return handlers;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final PartitionFunctionValuesProperty<?> cst=(PartitionFunctionValuesProperty<?>)obj;
			if (value instanceof Collection){
				if (cst.getValues()==null){
					cst.setValues(getDefaultValue());
				}
				cst.getValues().addAll(((Collection<?>)value).stream().map(c->c.toString()).collect(Collectors.toList()));
				return true;
			} else if (value instanceof String){
				final String[] args=((String)value).split("\\s*,\\s*");
				if (cst.getValues()==null){
					cst.setValues(getDefaultValue());
				}
				cst.getValues().addAll(args);
				return true;
			}
			return false;
		}
		@Override
		public PatitionFunctionValues getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PartitionFunctionValuesProperty<?>)obj).getValues();
			}
			return null;
		}
	}
	,
	SUPPORT_NUMBER(){
		@Override
		public final Class<?> getPropertyClass(){
			return SupportNumberProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return int.class;
		}
		@Override
		public Integer getDefaultValue(){
			return Integer.valueOf(0);
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SupportNumberProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((SupportNumberProperty<?>)obj).setSupportNumber(converters.convertObject(value==null?getDefaultValue():value, int.class));
			return true;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SupportNumberProperty<?>)obj).getSupportNumber();
			}
			return null;
		}
	}
	,
	STRATEGY_NUMBER(){
		@Override
		public final Class<?> getPropertyClass(){
			return StrategyNumberProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return int.class;
		}
		@Override
		public Integer getDefaultValue(){
			return Integer.valueOf(0);
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof StrategyNumberProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((StrategyNumberProperty<?>)obj).setStrategyNumber(converters.convertObject(value==null?getDefaultValue():value, int.class));
			return true;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((StrategyNumberProperty<?>)obj).getStrategyNumber();
			}
			return null;
		}
	}
	,
	//Dimension
	LEVEL_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return LevelNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof LevelNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((LevelNameProperty<?>)obj).setLevelName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((LevelNameProperty<?>)obj).getLevelName();
			}
			return null;
		}
	},
	SKIP_WHEN_NULL(){
		@Override
		public final Class<?> getPropertyClass(){
			return SkipWhenNullProperty.class;
		}
		@Override
		public final Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public final Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SkipWhenNullProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((SkipWhenNullProperty<?>)obj).setSkipWhenNull(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SkipWhenNullProperty<?>)obj).isSkipWhenNull();
			}
			return null;
		}
	},
	//Event
	INTERVAL_VALUE(){
		@Override
		public final Class<?> getPropertyClass(){
			return IntervalValueProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Integer.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IntervalValueProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IntervalValueProperty<?>)obj).setIntervalValue(converters.convertObject(value, Integer.class));
			return true;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IntervalValueProperty<?>)obj).getIntervalValue();
			}
			return null;
		}
	},
	INTERVAL_FIELD(){
		@Override
		public final Class<?> getPropertyClass(){
			return IntervalFieldProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IntervalFieldProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final IntervalFieldProperty<?> cst=((IntervalFieldProperty<?>)obj);
			cst.setIntervalField(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IntervalFieldProperty<?>)obj).getIntervalField();
			}
			return null;
		}
	},
	ON_COMPLETION(){
		@Override
		public final Class<?> getPropertyClass(){
			return OnCompletionProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof OnCompletionProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final OnCompletionProperty<?> cst=((OnCompletionProperty<?>)obj);
			cst.setOnCompletion(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((OnCompletionProperty<?>)obj).getOnCompletion();
			}
			return null;
		}
	},
	// for Operator
	/**
	 * 交代演算子 for Postgres
	 */
	COMMUTATIVE_OPERATOR_SCHEMA_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return CommutativeOperatorSchemaNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof CommutativeOperatorSchemaNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final CommutativeOperatorSchemaNameProperty<?> cst=((CommutativeOperatorSchemaNameProperty<?>)obj);
			cst.setCommutativeOperatorSchemaName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((CommutativeOperatorSchemaNameProperty<?>)obj).getCommutativeOperatorSchemaName();
			}
			return null;
		}
	},
	/**
	 * 交代演算子 for Postgres
	 */
	COMMUTATIVE_OPERATOR_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return CommutativeOperatorNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof CommutativeOperatorNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final CommutativeOperatorNameProperty<?> cst=((CommutativeOperatorNameProperty<?>)obj);
			cst.setCommutativeOperatorName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((CommutativeOperatorNameProperty<?>)obj).getCommutativeOperatorName();
			}
			return null;
		}
	},
	/**
	 * 否定子(スキーマ名) for Postgres
	 */
	NEGATION_OPERATOR_SCHEMA_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return NegationOperatorSchemaNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof NegationOperatorSchemaNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final NegationOperatorSchemaNameProperty<?> cst=((NegationOperatorSchemaNameProperty<?>)obj);
			cst.setNegationOperatorSchemaName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((NegationOperatorSchemaNameProperty<?>)obj).getNegationOperatorSchemaName();
			}
			return null;
		}
	},
	/**
	 * 否定子 for Postgres
	 */
	NEGATION_OPERATOR_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return NegationOperatorNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof NegationOperatorNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final NegationOperatorNameProperty<?> cst=((NegationOperatorNameProperty<?>)obj);
			cst.setNegationOperatorName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((NegationOperatorNameProperty<?>)obj).getNegationOperatorName();
			}
			return null;
		}
	},
	/**
	 * 制約選択評価関数(スキーマ名) for Postgres
	 */
	RESTRICT_FUNCTION_SCHEMA_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return RestrictFunctionSchemaNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof RestrictFunctionSchemaNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final RestrictFunctionSchemaNameProperty<?> cst=((RestrictFunctionSchemaNameProperty<?>)obj);
			cst.setRestrictFunctionSchemaName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((RestrictFunctionSchemaNameProperty<?>)obj).getRestrictFunctionSchemaName();
			}
			return null;
		}
	},
	/**
	 * 制約選択評価関数 for Postgres
	 */
	RESTRICT_FUNCTION_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return RestrictFunctionNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof RestrictFunctionNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final RestrictFunctionNameProperty<?> cst=((RestrictFunctionNameProperty<?>)obj);
			cst.setRestrictFunctionName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((RestrictFunctionNameProperty<?>)obj).getRestrictFunctionName();
			}
			return null;
		}
	},
	/**
	 * 結合選択評価関数 (スキーマ名) for Postgres
	 */
	JOIN_FUNCTION_SCHEMA_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return JoinFunctionSchemaNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof JoinFunctionSchemaNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final JoinFunctionSchemaNameProperty<?> cst=((JoinFunctionSchemaNameProperty<?>)obj);
			cst.setJoinFunctionSchemaName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((JoinFunctionSchemaNameProperty<?>)obj).getJoinFunctionSchemaName();
			}
			return null;
		}
	},
	/**
	 * 結合選択評価関数  for Postgres
	 */
	JOIN_FUNCTION_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return JoinFunctionNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof JoinFunctionNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final JoinFunctionNameProperty<?> cst=((JoinFunctionNameProperty<?>)obj);
			cst.setJoinFunctionName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((JoinFunctionNameProperty<?>)obj).getJoinFunctionName();
			}
			return null;
		}
	},
	/**
	 * この演算子がハッシュ結合をサポートできることを示します for Postgres
	 */
	HASHES(){
		@Override
		public final Class<?> getPropertyClass(){
			return HashesProperty.class;
		}
		@Override
		public final Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public final Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof HashesProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final HashesProperty<?> cst=((HashesProperty<?>)obj);
			cst.setHashes(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((HashesProperty<?>)obj).isHashes();
			}
			return null;
		}
	},
	/**
	 * この演算子がマージ結合をサポートできることを示します for Postgres
	 */
	MERGES(){
		@Override
		public final Class<?> getPropertyClass(){
			return MergesProperty.class;
		}
		@Override
		public final Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public final Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof MergesProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final MergesProperty<?> cst=((MergesProperty<?>)obj);
			cst.setMerges(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((MergesProperty<?>)obj).isMerges();
			}
			return null;
		}
	},
	/**
	 *   for Oracle
	 */
	IMPLEMENTATION_TYPE_SCHEMA_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return ImplementationTypeSchemaNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ImplementationTypeSchemaNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final ImplementationTypeSchemaNameProperty<?> cst=((ImplementationTypeSchemaNameProperty<?>)obj);
			cst.setImplementationTypeSchemaName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ImplementationTypeSchemaNameProperty<?>)obj).getImplementationTypeSchemaName();
			}
			return null;
		}
	},
	/**
	 *   for Oracle
	 */
	IMPLEMENTATION_TYPE_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return ImplementationTypeNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ImplementationTypeNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final ImplementationTypeNameProperty<?> cst=((ImplementationTypeNameProperty<?>)obj);
			cst.setImplementationTypeName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ImplementationTypeNameProperty<?>)obj).getImplementationTypeName();
			}
			return null;
		}
	},
	PRIVILEGE_STATE("state"){
		@Override
		public final Class<?> getPropertyClass(){
			return PrivilegeStateProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return PrivilegeState.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PrivilegeStateProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((PrivilegeStateProperty<?>)obj).setState(converters.convertObject(value, PrivilegeState.class));
			return true;
		}
		@Override
		public PrivilegeState getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PrivilegeStateProperty<?>)obj).getState();
			}
			return null;
		}
	},
	HIERACHY(){
		@Override
		public final Class<?> getPropertyClass(){
			return HierachyProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.TRUE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof HierachyProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((HierachyProperty<?>)obj).setHierachy(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((HierachyProperty<?>)obj).isHierachy();
			}
			return null;
		}
	},
	STARTS(){
		@Override
		public final Class<?> getPropertyClass(){
			return StartsProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Timestamp.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof StartsProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((StartsProperty<?>)obj).setStarts(converters.convertObject(value, Timestamp.class));
			return true;
		}
		@Override
		public Timestamp getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((StartsProperty<?>)obj).getStarts();
			}
			return null;
		}
	},
	ENDS(){
		@Override
		public final Class<?> getPropertyClass(){
			return EndsProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Timestamp.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof EndsProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((EndsProperty<?>)obj).setEnds(converters.convertObject(value, Timestamp.class));
			return true;
		}
		@Override
		public Timestamp getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((EndsProperty<?>)obj).getEnds();
			}
			return null;
		}
	},
	EXECUTE_AT(){
		@Override
		public final Class<?> getPropertyClass(){
			return ExecuteAtProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Timestamp.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ExecuteAtProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ExecuteAtProperty<?>)obj).setExecuteAt(converters.convertObject(value, Timestamp.class));
			return true;
		}
		@Override
		public Timestamp getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ExecuteAtProperty<?>)obj).getExecuteAt();
			}
			return null;
		}
	},
	LAST_EXECUTED(){
		@Override
		public final Class<?> getPropertyClass(){
			return LastExecutedProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return Timestamp.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof LastExecutedProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((LastExecutedProperty<?>)obj).setLastExecuted(converters.convertObject(value, Timestamp.class));
			return true;
		}
		@Override
		public Timestamp getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((LastExecutedProperty<?>)obj).getLastExecuted();
			}
			return null;
		}
	},
	//Privilege
	GRANTOR_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return GrantorNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof GrantorNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final GrantorNameProperty<?> cst=((GrantorNameProperty<?>)obj);
			cst.setGrantorName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((GrantorNameProperty<?>)obj).getGrantorName();
			}
			return null;
		}
	},
	GRANTEE_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return GranteeNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof GranteeNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final GranteeNameProperty<?> cst=((GranteeNameProperty<?>)obj);
			cst.setGranteeName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((GranteeNameProperty<?>)obj).getGranteeName();
			}
			return null;
		}
	},
	PRIVILEGE(){
		@Override
		public final Class<?> getPropertyClass(){
			return PrivilegeProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PrivilegeProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final PrivilegeProperty<?> cst=((PrivilegeProperty<?>)obj);
			cst.setPrivilege(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PrivilegeProperty<?>)obj).getPrivilege();
			}
			return null;
		}
	},
	GRANTABLE(){
		@Override
		public final Class<?> getPropertyClass(){
			return GrantableProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof GrantableProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((GrantableProperty<?>)obj).setGrantable(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((GrantableProperty<?>)obj).isGrantable();
			}
			return null;
		}
	},
	PERMISSION_SET(){
		@Override
		public final Class<?> getPropertyClass(){
			return PermissionSetProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return PermissionSet.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PermissionSetProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final PermissionSetProperty<?> cst=((PermissionSetProperty<?>)obj);
			if (value instanceof PermissionSet){
				cst.setPermissionSet((PermissionSet)value);
			} else{
				cst.setPermissionSet(converters.convertString(value));
			}
			return true;
		}
		@Override
		public PermissionSet getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PermissionSetProperty<?>)obj).getPermissionSet();
			}
			return null;
		}
	},
	ENABLE(){
		@Override
		public final Class<?> getPropertyClass(){
			return EnableProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.TRUE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof EnableProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((EnableProperty<?>)obj).setEnable(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((EnableProperty<?>)obj).isEnable();
			}
			return null;
		}
	},
	DEFAULT(){
		@Override
		public final Class<?> getPropertyClass(){
			return DefaultProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DefaultProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DefaultProperty<?>)obj).setDefault(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DefaultProperty<?>)obj).isDefault();
			}
			return null;
		}
	},
	DETERMINISTIC(){
		@Override
		public final Class<?> getPropertyClass(){
			return DeterministicProperty.class;
		}
		@Override
		public Class<Boolean> getValueClass(){
			return Boolean.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DeterministicProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DeterministicProperty<?>)obj).setDeterministic(converters.convertObject(value, getValueClass()));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DeterministicProperty<?>)obj).getDeterministic();
			}
			return null;
		}
	},
	PARALLEL(){
		@Override
		public final Class<?> getPropertyClass(){
			return ParallelProperty.class;
		}
		@Override
		public Class<Boolean> getValueClass(){
			return Boolean.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ParallelProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ParallelProperty<?>)obj).setParallel(converters.convertObject(value, getValueClass()));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ParallelProperty<?>)obj).getParallel();
			}
			return null;
		}
	},
	SQL_DATA_ACCESS(){
		@Override
		public final Class<?> getPropertyClass(){
			return SqlDataAccessProperty.class;
		}
		@Override
		public Class<SqlDataAccess> getValueClass(){
			return SqlDataAccess.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SqlDataAccessProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final SqlDataAccessProperty<?> cst=((SqlDataAccessProperty<?>)obj);
			if (value instanceof SqlDataAccess){
				cst.setSqlDataAccess((SqlDataAccess)value);
			} else{
				cst.setSqlDataAccess(toString(value));
			}
			return true;
		}
		@Override
		public SqlDataAccess getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SqlDataAccessProperty<?>)obj).getSqlDataAccess();
			}
			return null;
		}
	},
	SQL_SECURITY(){
		@Override
		public final Class<?> getPropertyClass(){
			return SqlSecurityProperty.class;
		}
		@Override
		public Class<SqlSecurity> getValueClass(){
			return SqlSecurity.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SqlSecurityProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final SqlSecurityProperty<?> cst=((SqlSecurityProperty<?>)obj);
			if (value instanceof SqlSecurity){
				cst.setSqlSecurity((SqlSecurity)value);
			} else{
				cst.setSqlSecurity(converters.convertString(value));
			}
			return true;
		}
		@Override
		public SqlSecurity getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SqlSecurityProperty<?>)obj).getSqlSecurity();
			}
			return null;
		}
	},
	SAVEPOINT_LEVEL(){
		@Override
		public final Class<?> getPropertyClass(){
			return SavepointLevelProperty.class;
		}
		@Override
		public Class<SavepointLevel> getValueClass(){
			return SavepointLevel.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SavepointLevelProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final SavepointLevelProperty<?> cst=((SavepointLevelProperty<?>)obj);
			if (value instanceof SavepointLevel){
				cst.setSavepointLevel((SavepointLevel)value);
			} else{
				cst.setSavepointLevel(converters.convertString(value));
			}
			return true;
		}
		@Override
		public SavepointLevel getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SavepointLevelProperty<?>)obj).getSavepointLevel();
			}
			return null;
		}
	},
	MAX_DYNAMIC_RESULT_SETS(){
		@Override
		public final Class<?> getPropertyClass(){
			return MaxDynamicResultSetsProperty.class;
		}
		@Override
		public Class<Integer> getValueClass(){
			return Integer.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof MaxDynamicResultSetsProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((MaxDynamicResultSetsProperty<?>)obj).setMaxDynamicResultSets(converters.convertObject(value, getValueClass()));
			return true;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((MaxDynamicResultSetsProperty<?>)obj).getMaxDynamicResultSets();
			}
			return null;
		}
	},
	EXECUTE_AS(){
		@Override
		public final Class<?> getPropertyClass(){
			return ExecuteAsProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ExecuteAsProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ExecuteAsProperty<?>)obj).setExecuteAs(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ExecuteAsProperty<?>)obj).getExecuteAs();
			}
			return null;
		}
	},
	////////////////////////////////////////////////////////////
	/** (for Oracle)ROWID情報を記録するかを表す変数名 */
	SAVE_ROW_IDS(){
		@Override
		public final Class<?> getPropertyClass(){
			return SaveRowIdsProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SaveRowIdsProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((SaveRowIdsProperty<?>)obj).setSaveRowIds(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SaveRowIdsProperty<?>)obj).isSaveRowIds();
			}
			return null;
		}
	},
	/** (for Oracle)主キー情報を記録するかどうか? */
	SAVE_PRIMARY_KEY(){
		@Override
		public final Class<?> getPropertyClass(){
			return SavePrimaryKeyProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SavePrimaryKeyProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((SavePrimaryKeyProperty<?>)obj).setSavePrimaryKey(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SavePrimaryKeyProperty<?>)obj).isSavePrimaryKey();
			}
			return null;
		}
	},
	/** (for Oracle)オブジェクト表にオブジェクト識別子情報を記録するかどうか? */
	SAVE_OBJECT_ID(){
		@Override
		public final Class<?> getPropertyClass(){
			return SaveObjectIdProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SaveObjectIdProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((SaveObjectIdProperty<?>)obj).setSaveObjectId(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SaveObjectIdProperty<?>)obj).isSaveObjectId();
			}
			return null;
		}
	},
	/** (for Oracle)フィルタ列情報を記録するかどうか? */
	SAVE_FILTER_COLUMNS(){
		@Override
		public final Class<?> getPropertyClass(){
			return SaveFilterColumnsProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SaveFilterColumnsProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((SaveFilterColumnsProperty<?>)obj).setSaveFilterColumns(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SaveFilterColumnsProperty<?>)obj).isSaveFilterColumns();
			}
			return null;
		}
	},
	/** (for Oracle)追加の順序付け情報を提供する順序値を記録するかどうか? */
	SAVE_SEQUENCE(){
		@Override
		public final Class<?> getPropertyClass(){
			return SaveSequenceProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SaveSequenceProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((SaveSequenceProperty<?>)obj).setSaveSequence(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SaveSequenceProperty<?>)obj).isSaveSequence();
			}
			return null;
		}
	},
	/** (for Oracle)新旧両方の値を記録する（true）か?古い値を記録して新しい値は記録しない（false）か? */
	INCLUDE_NEW_VALUES(){
		@Override
		public final Class<?> getPropertyClass(){
			return IncludeNewValuesProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof IncludeNewValuesProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((IncludeNewValuesProperty<?>)obj).setIncludeNewValues(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((IncludeNewValuesProperty<?>)obj).isIncludeNewValues();
			}
			return null;
		}
	},
	/**
	 * 11gR2項目 マテリアライズド・ビュー・ログが非同期的に消去されるかどうか?
	 */
	PURGE_ASYNCHRONOUS(){
		@Override
		public final Class<?> getPropertyClass(){
			return PurgeAsynchronousProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PurgeAsynchronousProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((PurgeAsynchronousProperty<?>)obj).setPurgeAsynchronous(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PurgeAsynchronousProperty<?>)obj).isPurgeAsynchronous();
			}
			return null;
		}
	},
	/**
	 * 11gR2項目 マテリアライズド・ビュー・ログが遅延方式で消去されるかどうか?
	 */
	PURGE_DEFERRED(){
		@Override
		public final Class<?> getPropertyClass(){
			return PurgeDeferredProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PurgeDeferredProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((PurgeDeferredProperty<?>)obj).setPurgeDeferred(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PurgeDeferredProperty<?>)obj).isPurgeDeferred();
			}
			return null;
		}
	},
	/**
	 * 11gR2項目 遅延消去の場合、消去が開始された日付
	 */
	PURGE_START(){
		@Override
		public final Class<?> getPropertyClass(){
			return PurgeStartProperty.class;
		}
		@Override
		public Class<Timestamp> getValueClass(){
			return Timestamp.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PurgeStartProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((PurgeStartProperty<?>)obj).setPurgeStart(converters.convertObject(value, getValueClass()));
			return true;
		}
		@Override
		public Timestamp getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PurgeStartProperty<?>)obj).getPurgeStart();
			}
			return null;
		}
	},
	PURGE_INTERVAL(){
		@Override
		public final Class<?> getPropertyClass(){
			return PurgeIntervalProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof PurgeIntervalProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((PurgeIntervalProperty<?>)obj).setPurgeInterval(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((PurgeIntervalProperty<?>)obj).getPurgeInterval();
			}
			return null;
		}
	},
	/**
	 * 11gR2項目 マテリアライズド・ビュー・ログがコミットSCNベースかどうか?
	 */
	COMMIT_SCN_BASED(){
		@Override
		public final Class<?> getPropertyClass(){
			return CommitScnBasedProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof CommitScnBasedProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((CommitScnBasedProperty<?>)obj).setCommitScnBased(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((CommitScnBasedProperty<?>)obj).isCommitScnBased();
			}
			return null;
		}
	},
	VIRTUAL(){
		@Override
		public final Class<?> getPropertyClass(){
			return VirtualProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof VirtualProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((VirtualProperty<?>)obj).setVirtual(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((VirtualProperty<?>)obj).isVirtual();
			}
			return null;
		}
	},
	WHERE(){
		@Override
		public final Class<?> getPropertyClass(){
			return WhereProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof WhereProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((WhereProperty<?>)obj).setWhere(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((WhereProperty<?>)obj).getWhere();
			}
			return null;
		}
	},
	WHEN(){
		@Override
		public final Class<?> getPropertyClass(){
			return WhenProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof WhenProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((WhenProperty<?>)obj).setWhen(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((WhenProperty<?>)obj).getWhen();
			}
			return null;
		}
	},
	WITH(){
		@Override
		public final Class<?> getPropertyClass(){
			return WithProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof WithProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((WithProperty<?>)obj).setWith(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((WithProperty<?>)obj).getWith();
			}
			return null;
		}
	},
	PRODUCT_NAME(){
		@Override
		public final Class<?> getPropertyClass(){
			return ProductNameProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ProductNameProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ProductNameProperty<?>)obj).setProductName(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ProductNameProperty<?>)obj).getProductName();
			}
			return null;
		}
	},
	PRODUCT_MAJOR_VERSION(){
		@Override
		public final Class<?> getPropertyClass(){
			return ProductMajorVersionProperty.class;
		}
		@Override
		public final Class<Integer> getValueClass(){
			return Integer.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ProductMajorVersionProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ProductMajorVersionProperty<?>)obj).setProductMajorVersion(converters.convertObject(value, getValueClass()));
			return true;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ProductMajorVersionProperty<?>)obj).getProductMajorVersion();
			}
			return null;
		}
	},
	PRODUCT_MINOR_VERSION(){
		@Override
		public final Class<?> getPropertyClass(){
			return ProductMinorVersionProperty.class;
		}
		@Override
		public final Class<Integer> getValueClass(){
			return Integer.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ProductMinorVersionProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ProductMinorVersionProperty<?>)obj).setProductMinorVersion(converters.convertObject(value, getValueClass()));
			return true;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ProductMinorVersionProperty<?>)obj).getProductMinorVersion();
			}
			return null;
		}
	},
	PRODUCT_REVISION(){
		@Override
		public final Class<?> getPropertyClass(){
			return ProductRevisionProperty.class;
		}
		@Override
		public final Class<Integer> getValueClass(){
			return Integer.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ProductRevisionProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ProductRevisionProperty<?>)obj).setProductRevision(converters.convertObject(value, getValueClass()));
			return true;
		}
		@Override
		public Integer getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ProductRevisionProperty<?>)obj).getProductRevision();
			}
			return null;
		}
	},
	DISPLAY_REMARKS(){
		@Override
		public final Class<?> getPropertyClass(){
			return DisplayRemarksProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DisplayRemarksProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DisplayRemarksProperty<?>)obj).setDisplayRemarks(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DisplayRemarksProperty<?>)obj).getDisplayRemarks();
			}
			return null;
		}
	},
	REMARKS(){
		@Override
		public final Class<?> getPropertyClass(){
			return RemarksProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof RemarksProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((RemarksProperty<?>)obj).setRemarks(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((RemarksProperty<?>)obj).getRemarks();
			}
			return null;
		}
	},
	CONTENT(){
		@Override
		public final Class<?> getPropertyClass(){
			return ContentProperty.class;
		}
		@Override
		public final Class<byte[]> getValueClass(){
			return byte[].class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ContentProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final ContentProperty<?> val=(ContentProperty<?>)obj;
			if (value instanceof String){
				val.setContent((String)value);
			} else{
				val.setContent(converters.convertObject(value, getValueClass()));
			}
			return true;
		}
		@Override
		public byte[] getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ContentProperty<?>)obj).getContent();
			}
			return null;
		}
	},
	STATEMENT(){
		@Override
		public final Class<?> getPropertyClass(){
			return StatementProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return StatementGetter.class;
		}
		@Override
		public final Class<?> getSetterPropertyClass(){
			return StatementSetter.class;
		}
		@Override
		public Class<?> getValueClass(){
			return List.class;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof StatementGetter;
		}
		@Override
		public boolean isSetterInstanceof(final Object obj){
			return obj instanceof StatementSetter;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof StatementProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final StatementSetter<?> cst=(StatementSetter<?>)obj;
			if (value instanceof String){
				cst.setStatement((String)value);
				return true;
			}else if (value instanceof Collection){
				final Collection<?> c=(Collection<?>)value;
				cst.setStatement(c.stream().map(v->v.toString()).collect(Collectors.toList()));
				return true;
			}else if (value==null){
				cst.setStatement((String)value);
				return true;
			}
			return false;
		}
		@Override
		public List<String> getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((StatementProperty<?>)obj).getStatement();
			}
			return null;
		}
	},
	DEFINITION(){
		@Override
		public final Class<?> getPropertyClass(){
			return DefinitionProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return DefinitionGetter.class;
		}
		@Override
		public final Class<?> getSetterPropertyClass(){
			return DefinitionSetter.class;
		}
		@Override
		public Class<?> getValueClass(){
			return List.class;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof DefinitionGetter;
		}
		@Override
		public boolean isSetterInstanceof(final Object obj){
			return obj instanceof DefinitionSetter;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DefinitionProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final DefinitionSetter<?> cst=(DefinitionSetter<?>)obj;
			if (value instanceof String){
				cst.setDefinition((String)value);
				return true;
			}else if (value instanceof Collection){
				final Collection<?> c=(Collection<?>)value;
				cst.setDefinition(c.stream().map(v->v.toString()).collect(Collectors.toList()));
				return true;
			}else if (value==null){
				cst.setDefinition((String)value);
				return true;
			}
			return false;
		}
		@Override
		public List<String> getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DefinitionProperty<?>)obj).getDefinition();
			}
			return null;
		}
	},
	ROW_ID(){
		@Override
		public final Class<?> getPropertyClass(){
			return RowIdProperty.class;
		}
		@Override
		public Class<Long> getValueClass(){
			return Long.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof RowIdProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((RowIdProperty<?>)obj).setRowId(converters.convertObject(value, getValueClass()));
			return true;
		}
		@Override
		public Long getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((RowIdProperty<?>)obj).getRowId();
			}
			return null;
		}
	},
	DATA_SOURCE_INFO(){
		@Override
		public final Class<?> getPropertyClass(){
			return DataSourceInfoProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DataSourceInfoProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DataSourceInfoProperty<?>)obj).setDataSourceInfo(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DataSourceInfoProperty<?>)obj).getDataSourceInfo();
			}
			return null;
		}
	},
	DATA_SOURCE_DETAIL_INFO(){
		@Override
		public final Class<?> getPropertyClass(){
			return DataSourceDetailInfoProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DataSourceDetailInfoProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DataSourceDetailInfoProperty<?>)obj).setDataSourceDetailInfo(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DataSourceDetailInfoProperty<?>)obj).getDataSourceDetailInfo();
			}
			return null;
		}
	},
	DATA_SOURCE_ROW_NUMBER(){
		@Override
		public final Class<?> getPropertyClass(){
			return DataSourceRowNumberProperty.class;
		}
		@Override
		public Class<Long> getValueClass(){
			return Long.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DataSourceRowNumberProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DataSourceRowNumberProperty<?>)obj).setDataSourceRowNumber(converters.convertObject(value, getValueClass()));
			return true;
		}
		@Override
		public Long getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DataSourceRowNumberProperty<?>)obj).getDataSourceRowNumber();
			}
			return null;
		}
	},
	VALUES(){
		@Override
		public final Class<?> getPropertyClass(){
			return ValuesProperty.class;
		}
		@Override
		public final Class<?> getValueClass(){
			return Set.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ValuesProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final ValuesProperty<?> cst=(ValuesProperty<?>)obj;
			if (value instanceof Collection){
				final Collection<?> c=(Collection<?>)value;
				if (cst.getValues()!=null&&cst.getValues()!=value){
					cst.getValues().clear();
					cst.getValues().addAll(c.stream().map(v->v.toString()).collect(Collectors.toList()));
				}
				return true;
			}else if (value instanceof String){
				final String[] splits = ((String) value).split("\\s*,\\s*");
				final Set<String> setVal = CommonUtils.linkedSet(splits.length);
				for (final String val : splits) {
					setVal.add(trim(val));
				}
				cst.getValues().addAll(setVal);
				return true;
			}
			return false;
		}
		@Override
		public Set<String> getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ValuesProperty<?>)obj).getValues();
			}
			return null;
		}
	},
	VALUE(){
		@Override
		public final Class<?> getPropertyClass(){
			return ValueProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ValueProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ValueProperty<?>)obj).setValue(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ValueProperty<?>)obj).getValue();
			}
			return null;
		}
	},
	DISPLAY_VALUE(){
		@Override
		public final Class<?> getPropertyClass(){
			return DisplayValueProperty.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof DisplayValueProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((DisplayValueProperty<?>)obj).setDisplayValue(toString(value));
			return true;
		}
		@Override
		public String getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((DisplayValueProperty<?>)obj).getDisplayValue();
			}
			return null;
		}
	},
	VALID(){
		@Override
		public final Class<?> getPropertyClass(){
			return ValidProperty.class;
		}
		@Override
		public Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ValidProperty;
		}
		@Override
		public Object getDefaultValue(){
			return Boolean.TRUE;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ValidProperty<?>)obj).setValid(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ValidProperty<?>)obj).isValid();
			}
			return null;
		}
	},
	HAS_ERRORS(){
		@Override
		public final Class<?> getPropertyClass(){
			return HasErrorsProperty.class;
		}
		@Override
		public final Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public final Boolean getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof HasErrorsProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((HasErrorsProperty<?>)obj).setHasErrors(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((HasErrorsProperty<?>)obj).getHasErrors();
			}
			return null;
		}
	},
	ERROR_MESSAGES(){
		@Override
		public final Class<?> getPropertyClass(){
			return ErrorMessagesProperty.class;
		}
		@Override
		public final Class<?> getGetterPropertyClass(){
			return ErrorMessagesGetter.class;
		}
		@Override
		public final Class<?> getSetterPropertyClass(){
			return ErrorMessagesSetter.class;
		}
		@Override
		public Class<?> getValueClass(){
			return List.class;
		}
		@Override
		public boolean isGetterInstanceof(final Object obj){
			return obj instanceof ErrorMessagesGetter;
		}
		@Override
		public boolean isSetterInstanceof(final Object obj){
			return obj instanceof ErrorMessagesSetter;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ErrorMessagesProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final ErrorMessagesSetter<?> cst=(ErrorMessagesSetter<?>)obj;
			if (value instanceof String){
				cst.setErrorMessages((String)value);
				return true;
			}else if (value instanceof Collection){
				final Collection<?> c=(Collection<?>)value;
				cst.setErrorMessages(c.stream().map(v->v.toString()).collect(Collectors.toList()));
				return true;
			}else if (value==null){
				cst.setErrorMessages((String)value);
				return true;
			}
			return false;
		}
		@Override
		public List<String> getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ErrorMessagesGetter)obj).getErrorMessages();
			}
			return null;
		}
	},
	CASE_SENSITIVE(){
		@Override
		public final Class<?> getPropertyClass(){
			return CaseSensitiveProperty.class;
		}
		@Override
		public final Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof CaseSensitiveProperty;
		}
		@Override
		public final Object getDefaultValue(){
			return Boolean.TRUE;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((CaseSensitiveProperty<?>)obj).setCaseSensitive(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((CaseSensitiveProperty<?>)obj).isCaseSensitive();
			}
			return null;
		}
	},
	UNLOGGED(){
		@Override
		public final Class<?> getPropertyClass(){
			return UnloggedProperty.class;
		}
		@Override
		public final Class<?> getValueClass(){
			return boolean.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof UnloggedProperty;
		}
		@Override
		public final Object getDefaultValue(){
			return Boolean.FALSE;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((UnloggedProperty<?>)obj).setUnlogged(toBoolean(value));
			return true;
		}
		@Override
		public Boolean getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((UnloggedProperty<?>)obj).isUnlogged();
			}
			return null;
		}
	},
	LOCKED_AT(){
		@Override
		public final Class<?> getPropertyClass(){
			return LockedAtProperty.class;
		}
		@Override
		public Class<Timestamp> getValueClass(){
			return Timestamp.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof LockedAtProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((LockedAtProperty<?>)obj).setLockedAt(converters.convertObject(value, getValueClass()));
			return true;
		}
		@Override
		public Timestamp getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((LockedAtProperty<?>)obj).getLockedAt();
			}
			return null;
		}
	},	
	EXPIRED_AT(){
		@Override
		public final Class<?> getPropertyClass(){
			return ExpiredAtProperty.class;
		}
		@Override
		public Class<Timestamp> getValueClass(){
			return Timestamp.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof ExpiredAtProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((ExpiredAtProperty<?>)obj).setExpiredAt(converters.convertObject(value, getValueClass()));
			return true;
		}
		@Override
		public Timestamp getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((ExpiredAtProperty<?>)obj).getExpiredAt();
			}
			return null;
		}
	},	
	SPECIFICS(){
		@Override
		public final Class<?> getPropertyClass(){
			return SpecificsProperty.class;
		}
		@Override
		public Class<DbInfo> getValueClass(){
			return DbInfo.class;
		}
		@Override
		public DbInfo getDefaultValue(){
			return new DbInfo();
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof SpecificsProperty;
		}
		@Override
		public DbInfo getCloneValue(final Object obj){
			final DbInfo value=getValue(obj);
			if (value==null){
				return null;
			}
			return value.clone();
		}
		@Override
		public List<StaxElementHandler> getXmlHandlers(){
			final List<StaxElementHandler> handlers=CommonUtils.list();
			final StaxElementHandler handler = new DbInfoXmlReaderHandler() {
				@Override
				public String getLocalName() {
					return getLabel();
				}
				@Override
				protected DbInfo createNewInstance(final Object parentObject) {
					final DbInfo obj = getValue(parentObject);
					if (obj!=null) {
						return obj;
					}
					return getDefaultValue();
				}
			};
			handlers.add(handler);
			return handlers;
		}

		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final SpecificsProperty<?> cst=((SpecificsProperty<?>)obj);
			if (value instanceof DbInfo){
				cst.setSpecifics((DbInfo)value);
				return true;
			} else if (value instanceof Map){
				cst.setSpecifics((Map<?,?>)value);
				return true;
			} else if (value instanceof Map.Entry){
				final Map.Entry<?,?> entry=(Map.Entry<?,?>)value;
				cst.getSpecifics().put(toString(entry.getKey()), toString(entry.getValue()));
				return true;
			}
			return false;
		}
		
		@Override
		public DbInfo getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((SpecificsProperty<?>)obj).getSpecifics();
			}
			return null;
		}
	},
	STATISTICS(){
		@Override
		public final Class<?> getPropertyClass(){
			return StatisticsProperty.class;
		}
		@Override
		public Class<DbInfo> getValueClass(){
			return DbInfo.class;
		}
		@Override
		public DbInfo getDefaultValue(){
			return new DbInfo();
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof StatisticsProperty;
		}
		@Override
		public DbInfo getCloneValue(final Object obj){
			final DbInfo value=getValue(obj);
			if (value==null){
				return null;
			}
			return value.clone();
		}
		@Override
		public List<StaxElementHandler> getXmlHandlers(){
			final List<StaxElementHandler> handlers=CommonUtils.list();
			final StaxElementHandler handler = new DbInfoXmlReaderHandler() {
				@Override
				public String getLocalName() {
					return getLabel();
				}
				@Override
				protected DbInfo createNewInstance(final Object parentObject) {
					final DbInfo obj = getValue(parentObject);
					if (obj!=null) {
						return obj;
					}
					return getDefaultValue();
				}
			};
			handlers.add(handler);
			return handlers;
		}

		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			final StatisticsProperty<?> cst=((StatisticsProperty<?>)obj);
			if (value instanceof DbInfo){
				cst.setStatistics((DbInfo)value);
				return true;
			} else if (value instanceof Map){
				cst.setStatistics((Map<?,?>)value);
				return true;
			} else if (value instanceof Map.Entry){
				final Map.Entry<?,?> entry=(Map.Entry<?,?>)value;
				if (cst.getStatistics()==null){
					cst.setStatistics(this.getDefaultValue());
				}
				cst.getStatistics().put(toString(entry.getKey()), toString(entry.getValue()));
				return true;
			}
			return false;
		}
		
		@Override
		public DbInfo getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((StatisticsProperty<?>)obj).getStatistics();
			}
			return null;
		}
	},
	CREATED_AT(){
		@Override
		public final Class<?> getPropertyClass(){
			return CreatedAtProperty.class;
		}
		@Override
		public Class<Timestamp> getValueClass(){
			return Timestamp.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof CreatedAtProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((CreatedAtProperty<?>)obj).setCreatedAt(converters.convertObject(value, getValueClass()));
			return true;
		}
		@Override
		public Timestamp getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((CreatedAtProperty<?>)obj).getCreatedAt();
			}
			return null;
		}
	},
	LAST_ALTERED_AT(){
		@Override
		public final Class<?> getPropertyClass(){
			return LastAlteredAtProperty.class;
		}
		@Override
		public Class<Timestamp> getValueClass(){
			return Timestamp.class;
		}
		@Override
		public boolean isInstanceof(final Object obj){
			return obj instanceof LastAlteredAtProperty;
		}
		@Override
		protected final boolean setValueInternal(final Object obj, final Object value){
			((LastAlteredAtProperty<?>)obj).setLastAlteredAt(converters.convertObject(value, getValueClass()));
			return true;
		}
		@Override
		public Timestamp getValue(final Object obj){
			if (isGetterInstanceof(obj)){
				return ((LastAlteredAtProperty<?>)obj).getLastAlteredAt();
			}
			return null;
		}
	},
	;
	
	private SchemaProperties(final String label){
		this.label=label;
	}

	private SchemaProperties(){
		this.label=StringUtils.snakeToCamel(this.name());
	}

	private SchemaProperties(final Class<?> propertyClass){
		this.label=StringUtils.snakeToCamel(this.name());
	}

	private final String label;
	
	private static final Converters converters=new Converters();
	
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
		return String.class;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#getDefaultValue()
	 */
	@Override
	public Object getDefaultValue(){
		return null;
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
		if(Converters.getDefault().isConvertable(value.getClass())){
			return Converters.getDefault().copy(value);
		}
		if (value instanceof Set){
			return CommonUtils.cloneSet((Set<?>)value);
		}
		if (value instanceof List){
			return CommonUtils.cloneList((List<?>)value);
		}
		if (value instanceof Cloneable){
			Method method;
			try {
				method = value.getClass().getMethod("clone");
				return method.invoke(value);
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new RuntimeException(e);
			}
		}
		return value;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#getXmlHandlers()
	 */
	@Override
	public List<StaxElementHandler> getXmlHandlers(){
		if (Set.class.equals(getValueClass())){
			final List<StaxElementHandler> handlers=CommonUtils.list();
			handlers.add(new SetHandler());
			handlers.add(new ValueHandler());
			handlers.add(new EmptyTextSkipHandler());
			return handlers;
		}
		return Collections.emptyList();
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.properties.ISchemaProperty#getLabel()
	 */
	@Override
	public final String getLabel(){
		return label;
	}
	
	protected String toString(final Object obj){
		return converters.convertString(obj);
	}
	
	protected Boolean toBoolean(final Object value){
		return converters.convertObject(value==null?getDefaultValue():value, Boolean.class);
	}

	protected Integer toInteger(final Object value){
		return converters.convertObject(value==null?getDefaultValue():value, Integer.class);
	}

	protected int toInt(final Object value){
		return converters.convertObject(value==null?getDefaultValue():value, int.class);
	}

}
