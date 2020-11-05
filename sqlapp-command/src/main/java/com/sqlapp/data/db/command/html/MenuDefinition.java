/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command.html;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sqlapp.data.schemas.Assembly;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnPrivilege;
import com.sqlapp.data.schemas.Constant;
import com.sqlapp.data.schemas.DbLink;
import com.sqlapp.data.schemas.Dimension;
import com.sqlapp.data.schemas.Directory;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Event;
import com.sqlapp.data.schemas.ExternalTable;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Mask;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.MviewLog;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.Operator;
import com.sqlapp.data.schemas.OperatorClass;
import com.sqlapp.data.schemas.PartitionFunction;
import com.sqlapp.data.schemas.PartitionScheme;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.PublicDbLink;
import com.sqlapp.data.schemas.PublicSynonym;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.RolePrivilege;
import com.sqlapp.data.schemas.RoutinePrivilege;
import com.sqlapp.data.schemas.Rule;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaPrivilege;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Setting;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableLink;
import com.sqlapp.data.schemas.TableSpace;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.data.schemas.User;
import com.sqlapp.data.schemas.UserPrivilege;
import com.sqlapp.data.schemas.View;
import com.sqlapp.data.schemas.XmlSchema;
import com.sqlapp.util.CommonUtils;

public enum MenuDefinition {
	General(){
		@Override
		public boolean hasData(Catalog catalog){
			return true;
		}
		public String getHtmlName(){
			return "index.html";
		}
	},
	Schemas(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Schema> list=getDatas(catalog);
			return list.size()>0;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Schema> list=catalog.getSchemas().stream().collect(Collectors.toList());
			return (List<S>)list;
		}
		@Override
		public boolean hasDetails(){
			return true;
		}
	},
	Tables(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Table> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Table> list=getSchemaObjectList(catalog, s->s.getTables().stream());
			return (List<S>)list;
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	Columns(){
		@Override
		public boolean hasData(Catalog catalog){
			return true;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Table> tables=Tables.getDatas(catalog);
			List<Column> list=tables.stream().flatMap(table->table.getColumns().stream()).collect(Collectors.toList());
			Collections.sort(list, new ColumnComparator());
			return (List<S>)list;
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Tables;
		}
	},
	Indexes(){
		@Override
		public boolean hasData(Catalog catalog){
			return true;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Table> tables=Tables.getDatas(catalog);
			List<Index> list=tables.stream().flatMap(table->table.getIndexes().stream()).collect(Collectors.toList());
			return (List<S>)list;
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Tables;
		}
	},
	Relationships(){
		@Override
		public boolean hasData(Catalog catalog){
			return true;
		}
	},
	Functions(){
		@Override
		public boolean hasData(Catalog catalog){
			List<com.sqlapp.data.schemas.Function> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<com.sqlapp.data.schemas.Function> list=getSchemaObjectList(catalog, s->s.getFunctions().stream());
			return (List<S>)list;
		}
		
		@Override
		public boolean hasDetails(){
			return true;
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	Procedures(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Procedure> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Procedure> list=getSchemaObjectList(catalog, s->s.getProcedures().stream());
			return (List<S>)list;
		}
		
		@Override
		public boolean hasDetails(){
			return true;
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	Constants(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Constant> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Constant> list=getSchemaObjectList(catalog, s->s.getConstants().stream());
			return (List<S>)list;
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	Packages(){
		@Override
		public boolean hasData(Catalog catalog){
			List<com.sqlapp.data.schemas.Package> list=getDatas(catalog);
			return list.size()>0;
		}
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<com.sqlapp.data.schemas.Package> list=getSchemaObjectList(catalog, s->s.getPackages().stream());
			return (List<S>)list;
		}
		@Override
		public boolean hasDetails(){
			return true;
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	Triggers(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Trigger> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Trigger> list=getSchemaObjectList(catalog, s->s.getTriggers().stream());
			return (List<S>)list;
		}

		@Override
		public boolean hasDetails(){
			return true;
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	Views(){
		@Override
		public boolean hasData(Catalog catalog){
			List<View> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Table> list=getSchemaObjectList(catalog, s->s.getViews().stream());
			return (List<S>)list;
		}
		
		@Override
		public boolean hasDetails(){
			return true;
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	Mviews(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Mview> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Table> list=getSchemaObjectList(catalog, s->s.getMviews().stream());
			return (List<S>)list;
		}
		
		@Override
		public boolean hasDetails(){
			return true;
		}

		@Override
		public String getDisplayName(){
			return "Materialized View";
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	MviewLogs(){
		@Override
		public boolean hasData(Catalog catalog){
			List<MviewLog> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<MviewLog> list=getSchemaObjectList(catalog, s->s.getMviewLogs().stream());
			return (List<S>)list;
		}
		@Override
		public boolean hasDetails(){
			return true;
		}
		@Override
		public String getDisplayName(){
			return "Materialized View Logs";
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	Masks(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Mask> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Mask> list=getSchemaObjectList(catalog, s->s.getMasks().stream());
			return (List<S>)list;
		}
		@Override
		public boolean hasDetails(){
			return true;
		}
		@Override
		public String getDisplayName(){
			return "Masks";
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	TableLinks(){
		@Override
		public boolean hasData(Catalog catalog){
			List<TableLink> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<TableLink> list=getSchemaObjectList(catalog, s->s.getTableLinks().stream());
			return (List<S>)list;
		}
		
		@Override
		public String getDisplayName(){
			return "Table Links";
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	ExternalTables(){
		@Override
		public boolean hasData(Catalog catalog){
			List<ExternalTable> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<ExternalTable> list=getSchemaObjectList(catalog, s->s.getExternalTables().stream());
			return (List<S>)list;
		}

		@Override
		public String getDisplayName(){
			return "External Tables";
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	Sequences(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Sequence> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Sequence> list=getSchemaObjectList(catalog, s->s.getSequences().stream());
			return (List<S>)list;
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	Synonyms(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Synonym> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Synonym> list=getSchemaObjectList(catalog, s->s.getSynonyms().stream());
			return (List<S>)list;
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	PublicSynonyms(){
		@Override
		public boolean hasData(Catalog catalog){
			List<PublicSynonym> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<PublicSynonym> list=catalog.getPublicSynonyms().stream().collect(Collectors.toList());
			return (List<S>)list;
		}

		@Override
		public String getDisplayName(){
			return "Public Synonyms";
		}

	},
	DbLinks(){
		@Override
		public boolean hasData(Catalog catalog){
			List<DbLink> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<DbLink> list=getSchemaObjectList(catalog, s->s.getDbLinks().stream());
			return (List<S>)list;
		}

		@Override
		public String getDisplayName(){
			return "DB Links";
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	PublicDbLinks(){
		@Override
		public boolean hasData(Catalog catalog){
			List<PublicDbLink> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<PublicDbLink> list=catalog.getPublicDbLinks().stream().collect(Collectors.toList());
			return (List<S>)list;
		}

		@Override
		public String getDisplayName(){
			return "Public DB Links";
		}

	},
	Domains(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Domain> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Domain> list=getSchemaObjectList(catalog, s->s.getDomains().stream());
			return (List<S>)list;
		}
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	Types(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Type> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Type> list=getSchemaObjectList(catalog, s->s.getTypes().stream());
			return (List<S>)list;
		}
		
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	TableSpaces(){
		@Override
		public boolean hasData(Catalog catalog){
			List<TableSpace> list=getDatas(catalog);
			return list.size()>0;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<TableSpace> list=catalog.getTableSpaces().stream().collect(Collectors.toList());
			return (List<S>)list;
		}
		@Override
		public boolean hasDetails(){
			return true;
		}
		@Override
		public String getDisplayName(){
			return "Table Spaces";
		}
	},
	Rules(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Rule> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Rule> list=getSchemaObjectList(catalog, s->s.getRules().stream());
			return (List<S>)list;
		}
		
		@Override
		public boolean hasDetails(){
			return true;
		}

		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	Events(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Event> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Event> list=getSchemaObjectList(catalog, s->s.getEvents().stream());
			return (List<S>)list;
		}

		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	Directories(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Directory> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Directory> list=catalog.getDirectories().stream().collect(Collectors.toList());
			return (List<S>)list;
		}
	},
	Operators(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Operator> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Operator> list=getSchemaObjectList(catalog, s->s.getOperators().stream());
			return (List<S>)list;
		}
		
		@Override
		public boolean hasDetails(){
			return true;
		}

		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	OperatorClasses(){
		@Override
		public boolean hasData(Catalog catalog){
			List<OperatorClass> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<OperatorClass> list=getSchemaObjectList(catalog, s->s.getOperatorClasses().stream());
			return (List<S>)list;
		}
		
		@Override
		public boolean hasDetails(){
			return true;
		}
		
		@Override
		public String getDisplayName(){
			return "Operator Classes";
		}

		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	PartitionFunctions(){
		@Override
		public boolean hasData(Catalog catalog){
			List<PartitionFunction> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<PartitionFunction> list=catalog.getPartitionFunctions().stream().collect(Collectors.toList());
			return (List<S>)list;
		}
		@Override
		public boolean hasDetails(){
			return true;
		}
		@Override
		public String getDisplayName(){
			return "Partition Functions";
		}
	},
	PartitionSchemes(){
		@Override
		public boolean hasData(Catalog catalog){
			List<PartitionScheme> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<PartitionScheme> list=catalog.getPartitionSchemes().stream().collect(Collectors.toList());
			return (List<S>)list;
		}
		@Override
		public boolean hasDetails(){
			return true;
		}
		@Override
		public String getDisplayName(){
			return "Partition Schemes";
		}
	},
	XmlSchemas(){
		@Override
		public boolean hasData(Catalog catalog){
			List<XmlSchema> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<XmlSchema> list=getSchemaObjectList(catalog, s->s.getXmlSchemas().stream());
			return (List<S>)list;
		}

		@Override
		public String getDisplayName(){
			return "XML Schemas";
		}

		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}

		@Override
		public boolean hasDetails(){
			return true;
		}
	},
	Assemblies(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Assembly> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Assembly> list=catalog.getAssemblies().stream().collect(Collectors.toList());
			return (List<S>)list;
		}
		
		@Override
		public boolean hasDetails(){
			return true;
		}
	},
	Dimensions(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Dimension> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Dimension> list=getSchemaObjectList(catalog, s->s.getDimensions().stream());
			return (List<S>)list;
		}

		@Override
		public int getNestLevel(){
			return 2;
		}
		
		@Override
		public MenuDefinition getParent(){
			return MenuDefinition.Schemas;
		}
	},
	Users(){
		@Override
		public boolean hasData(Catalog catalog){
			List<User> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<User> list=catalog.getUsers().stream().collect(Collectors.toList());
			return (List<S>)list;
		}
	},
	Roles(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Role> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Role> list=catalog.getRoles().stream().collect(Collectors.toList());
			return (List<S>)list;
		}
	},
	UserPrivileges(){
		@Override
		public boolean hasData(Catalog catalog){
			List<UserPrivilege> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<UserPrivilege> list=catalog.getUserPrivileges().stream().collect(Collectors.toList());
			return (List<S>)list;
		}
		
		@Override
		public String getDisplayName(){
			return "User Privileges";
		}
	},
	SchemaPrivileges(){
		@Override
		public boolean hasData(Catalog catalog){
			List<SchemaPrivilege> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<SchemaPrivilege> list=catalog.getSchemaPrivileges().stream().collect(Collectors.toList());
			return (List<S>)list;
		}
		
		@Override
		public String getDisplayName(){
			return "Schema Privileges";
		}
	},
	ObjectPrivileges(){
		@Override
		public boolean hasData(Catalog catalog){
			List<ObjectPrivilege> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<ObjectPrivilege> list=catalog.getObjectPrivileges().stream().collect(Collectors.toList());
			return (List<S>)list;
		}
		
		@Override
		public String getDisplayName(){
			return "Object Privileges";
		}
	},
	RoutinePrivileges(){
		@Override
		public boolean hasData(Catalog catalog){
			List<RoutinePrivilege> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<RoutinePrivilege> list=catalog.getRoutinePrivileges().stream().collect(Collectors.toList());
			return (List<S>)list;
		}
		
		@Override
		public String getDisplayName(){
			return "Routine Privileges";
		}
	},
	ColumnPrivileges(){
		@Override
		public boolean hasData(Catalog catalog){
			List<ColumnPrivilege> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<ColumnPrivilege> list=catalog.getColumnPrivileges().stream().collect(Collectors.toList());
			return (List<S>)list;
		}
		
		@Override
		public String getDisplayName(){
			return "Column Privileges";
		}
	},
	RolePrivileges(){
		@Override
		public boolean hasData(Catalog catalog){
			List<RolePrivilege> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<RolePrivilege> list=catalog.getRolePrivileges().stream().collect(Collectors.toList());
			return (List<S>)list;
		}
		
		@Override
		public String getDisplayName(){
			return "Role Privileges";
		}
	},
	Settings(){
		@Override
		public boolean hasData(Catalog catalog){
			List<Setting> list=getDatas(catalog);
			return list.size()>0;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <S> List<S> getDatas(Catalog catalog){
			List<Setting> list=catalog.getSettings().stream().collect(Collectors.toList());
			return (List<S>)list;
		}
	},
	;
	
	private List<MenuDefinition> nest=null;
	
	public MenuDefinition getParent(){
		return null;
	}

	public String getHtmlName(){
		return this.toString().toLowerCase()+".html";
	}
	
	public List<MenuDefinition> getNest(){
		if (nest!=null){
			return nest;
		}
		List<MenuDefinition> list=CommonUtils.list();
		createNest(this, list);
		List<MenuDefinition> reverseList=CommonUtils.list();
		for(int i=list.size()-1;i>=0;i--){
			reverseList.add(list.get(i));
		}
		nest= Collections.unmodifiableList(reverseList);
		return nest;
	}

	private void createNest(MenuDefinition current, List<MenuDefinition> result){
		if (current!=null){
			result.add(current);
			createNest(current.getParent(), result);
		}
	}

	public int getNestLevel(){
		return getNest().size();
	}
	
	public boolean hasDetails(){
		return false;
	}
	
	public boolean hasData(Catalog catalog){
		return true;
	}
	
	public <S> List<S> getDatas(Catalog catalog){
		return Collections.emptyList();
	}
	
	protected <R, S extends Stream<R>> List<R> getSchemaObjectList(Catalog catalog, Function<? super Schema, S> func){
		List<R> list=catalog.getSchemas().stream().flatMap(func).collect(Collectors.toList());
		return list;
	}
	
	public String getDisplayName(){
		return toString();
	}
	
	public Menu toMenu(){
		Menu menu=new Menu();
		menu.setId(this.name());
		menu.setName(this.getDisplayName());
		menu.setUrl(this.getHtmlName());
		menu.setMenuDefinition(this);
		return menu;
	}
	
	public static List<Menu> toMenus(Catalog catalog){
		List<Menu> result=CommonUtils.list();
		for(MenuDefinition menuDefinition:values()){
			if (menuDefinition.hasData(catalog)){
				result.add(menuDefinition.toMenu());
			}
		}
		return result;
	}
	
	public static MenuDefinition parse(String text){
		for(MenuDefinition def:values()){
			if (def.toString().equalsIgnoreCase(text)){
				return def;
			}
		}
		return null;
	}
	
	static class ColumnComparator implements Comparator<Column>{

		@Override
		public int compare(Column o1, Column o2) {
			int ret=CommonUtils.compare(o1.getName(), o2.getName());
			if (ret!=0){
				return ret;
			}
			ret=CommonUtils.compare(o1.getSchemaName(), o2.getSchemaName());
			if (ret!=0){
				return ret;
			}
			ret=CommonUtils.compare(o1.getTableName(), o2.getTableName());
			return ret;
		}
	}
	
}
