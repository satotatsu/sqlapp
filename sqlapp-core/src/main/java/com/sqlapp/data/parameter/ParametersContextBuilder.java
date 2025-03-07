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

package com.sqlapp.data.parameter;


import com.sqlapp.data.schemas.DbObjects;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.jdbc.sql.SqlComparisonOperator;

/**
 * Schema Paratmers Context
 * 
 * @author SATOH
 * 
 */
public class ParametersContextBuilder {

	private final ParametersContext parametersContext;
	
	private ParametersContextBuilder(ParametersContext parametersContext){
		this.parametersContext=parametersContext;
	}

	private ParametersContextBuilder(){
		this.parametersContext=new ParametersContext();
	}

	public static ParametersContextBuilder create(){
		return new ParametersContextBuilder();
	}

	public static ParametersContextBuilder create(ParametersContext parametersContext){
		return new ParametersContextBuilder(parametersContext);
	}

	private void put(String name, Object value){
		parametersContext.put(name, value);
	}

	private void put(DbObjects prop, Object value){
		parametersContext.put(prop.getCamelCaseNameLabel(), value);
	}

	private void put(ISchemaProperty prop, Object value){
		parametersContext.put(prop.getLabel(), value);
	}

	private void putOperatorValues(String name, SqlComparisonOperator op, Object arg){
		parametersContext.putOperatorValue(name, op, arg);
	}

	private void putOperatorValues(DbObjects prop, SqlComparisonOperator op, Object arg){
		parametersContext.putOperatorValue(prop.getCamelCaseNameLabel(), op, arg);
	}

	private void putOperatorValues(ISchemaProperty prop, SqlComparisonOperator op, Object arg){
		parametersContext.putOperatorValue(prop.getLabel(), op, arg);
	}
	
	private ParametersContextBuilder instance(){
		return this;
	}
	
	public ParametersContextBuilder tableName(String...arg){
		put(SchemaProperties.TABLE_NAME, arg);
		return instance();
	}

	public ParametersContextBuilder tableName(Object arg){
		put(SchemaProperties.TABLE_NAME, arg);
		return instance();
	}
	
	public ParametersContextBuilder tableName(SqlComparisonOperator op, String...arg){
		putOperatorValues(SchemaProperties.TABLE_NAME, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder tableName(SqlComparisonOperator op, Object arg){
		putOperatorValues(SchemaProperties.TABLE_NAME, op, arg);
		return instance();
	}

	public ParametersContextBuilder schemaName(String...arg){
		put(SchemaProperties.SCHEMA_NAME, arg);
		return instance();
	}

	public ParametersContextBuilder schemaName(Object arg){
		put(SchemaProperties.SCHEMA_NAME, arg);
		return instance();
	}

	public ParametersContextBuilder schemaName(SqlComparisonOperator op, String...arg){
		putOperatorValues(SchemaProperties.SCHEMA_NAME, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder schemaName(SqlComparisonOperator op, Object arg){
		putOperatorValues(SchemaProperties.SCHEMA_NAME, op, arg);
		return instance();
	}

	public ParametersContextBuilder catalogName(String...arg){
		put(SchemaProperties.CATALOG_NAME, arg);
		return instance();
	}

	public ParametersContextBuilder catalogName(Object arg){
		put(SchemaProperties.CATALOG_NAME, arg);
		return instance();
	}

	public ParametersContextBuilder catalogName(SqlComparisonOperator op, String...arg){
		putOperatorValues(SchemaProperties.CATALOG_NAME, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder catalogName(SqlComparisonOperator op, Object arg){
		putOperatorValues(SchemaProperties.CATALOG_NAME, op, arg);
		return instance();
	}

	public ParametersContextBuilder columnName(String...arg){
		put(SchemaProperties.COLUMN_NAME, arg);
		return instance();
	}

	public ParametersContextBuilder columnName(Object arg){
		put(SchemaProperties.COLUMN_NAME, arg);
		return instance();
	}

	public ParametersContextBuilder columnName(SqlComparisonOperator op, String...arg){
		putOperatorValues(SchemaProperties.COLUMN_NAME, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder columnName(SqlComparisonOperator op, Object arg){
		putOperatorValues(SchemaProperties.COLUMN_NAME, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder functionName(String...arg){
		put(DbObjects.FUNCTION, arg);
		return instance();
	}

	public ParametersContextBuilder functionName(Object arg){
		put(DbObjects.FUNCTION, arg);
		return instance();
	}

	public ParametersContextBuilder functionName(SqlComparisonOperator op, String...arg){
		putOperatorValues(DbObjects.FUNCTION, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder functionName(SqlComparisonOperator op, Object arg){
		putOperatorValues(DbObjects.FUNCTION, op, arg);
		return instance();
	}

	public ParametersContextBuilder procedureName(String...arg){
		put(DbObjects.PROCEDURE, arg);
		return instance();
	}

	public ParametersContextBuilder procedureName(Object arg){
		put(DbObjects.PROCEDURE, arg);
		return instance();
	}

	public ParametersContextBuilder procedureName(SqlComparisonOperator op, String...arg){
		putOperatorValues(DbObjects.PROCEDURE, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder procedureName(SqlComparisonOperator op, Object arg){
		putOperatorValues(DbObjects.PROCEDURE, op, arg);
		return instance();
	}

	public ParametersContextBuilder packageName(String...arg){
		put(DbObjects.PACKAGE, arg);
		return instance();
	}

	public ParametersContextBuilder packageName(Object arg){
		put(DbObjects.PACKAGE, arg);
		return instance();
	}

	public ParametersContextBuilder packageName(SqlComparisonOperator op, String...arg){
		putOperatorValues(DbObjects.PACKAGE, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder packageName(SqlComparisonOperator op, Object arg){
		putOperatorValues(DbObjects.PACKAGE, op, arg);
		return instance();
	}

	public ParametersContextBuilder sequenceName(String...arg){
		put(DbObjects.SEQUENCE, arg);
		return instance();
	}

	public ParametersContextBuilder sequenceName(Object arg){
		put(DbObjects.SEQUENCE, arg);
		return instance();
	}

	public ParametersContextBuilder sequenceName(SqlComparisonOperator op, String...arg){
		putOperatorValues(DbObjects.SEQUENCE, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder sequenceName(SqlComparisonOperator op, Object arg){
		putOperatorValues(DbObjects.SEQUENCE, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder tableSpaceName(String...arg){
		put(DbObjects.TABLE_SPACE, arg);
		return instance();
	}

	public ParametersContextBuilder tableSpaceName(Object arg){
		put(DbObjects.TABLE_SPACE, arg);
		return instance();
	}

	public ParametersContextBuilder tableSpaceName(SqlComparisonOperator op, String...arg){
		putOperatorValues(DbObjects.TABLE_SPACE, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder tableSpaceName(SqlComparisonOperator op, Object arg){
		putOperatorValues(DbObjects.TABLE_SPACE, op, arg);
		return instance();
	}

	public ParametersContextBuilder objectName(String...arg){
		put(SchemaProperties.OBJECT_NAME, arg);
		return instance();
	}

	public ParametersContextBuilder objectName(Object arg){
		put(SchemaProperties.OBJECT_NAME, arg);
		return instance();
	}

	public ParametersContextBuilder objectName(SqlComparisonOperator op, String...arg){
		putOperatorValues(SchemaProperties.OBJECT_NAME, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder objectName(SqlComparisonOperator op, Object arg){
		putOperatorValues(SchemaProperties.OBJECT_NAME, op, arg);
		return instance();
	}

	public ParametersContextBuilder userName(String...arg){
		put(SchemaProperties.USER_NAME, arg);
		return instance();
	}

	public ParametersContextBuilder userName(Object arg){
		put(SchemaProperties.USER_NAME, arg);
		return instance();
	}

	public ParametersContextBuilder userName(SqlComparisonOperator op, String...arg){
		putOperatorValues(SchemaProperties.USER_NAME, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder userName(SqlComparisonOperator op, Object arg){
		putOperatorValues(SchemaProperties.USER_NAME, op, arg);
		return instance();
	}

	public ParametersContextBuilder operator(String...arg){
		put(DbObjects.OPERATOR, arg);
		return instance();
	}

	public ParametersContextBuilder operator(Object arg){
		put(DbObjects.OPERATOR, arg);
		return instance();
	}

	public ParametersContextBuilder operator(SqlComparisonOperator op, String...arg){
		putOperatorValues(DbObjects.OPERATOR, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder operator(SqlComparisonOperator op, Object arg){
		putOperatorValues(DbObjects.OPERATOR, op, arg);
		return instance();
	}

	public ParametersContextBuilder operatorClass(String...arg){
		put(DbObjects.OPERATOR_CLASS, arg);
		return instance();
	}

	public ParametersContextBuilder operatorClass(Object arg){
		put(DbObjects.OPERATOR_CLASS, arg);
		return instance();
	}

	public ParametersContextBuilder operatorClass(SqlComparisonOperator op, String...arg){
		putOperatorValues(DbObjects.OPERATOR_CLASS, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder operatorClass(SqlComparisonOperator op, Object arg){
		putOperatorValues(DbObjects.OPERATOR_CLASS, op, arg);
		return instance();
	}

	public ParametersContextBuilder assembly(String...arg){
		put(DbObjects.ASSEMBLY, arg);
		return instance();
	}

	public ParametersContextBuilder assembly(Object arg){
		put(DbObjects.ASSEMBLY, arg);
		return instance();
	}

	public ParametersContextBuilder assembly(SqlComparisonOperator op, String...arg){
		putOperatorValues(DbObjects.ASSEMBLY, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder assembly(SqlComparisonOperator op, Object arg){
		putOperatorValues(DbObjects.ASSEMBLY, op, arg);
		return instance();
	}

	private static final String CONSTRAINT_NAME="constraintName";
	
	public ParametersContextBuilder constraint(String...arg){
		put(CONSTRAINT_NAME, arg);
		return instance();
	}

	public ParametersContextBuilder constraint(Object arg){
		put(CONSTRAINT_NAME, arg);
		return instance();
	}

	public ParametersContextBuilder constraint(SqlComparisonOperator op, String...arg){
		putOperatorValues(CONSTRAINT_NAME, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder constraint(SqlComparisonOperator op, Object arg){
		putOperatorValues(CONSTRAINT_NAME, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder index(String...arg){
		put(SchemaProperties.INDEX_NAME, arg);
		return instance();
	}

	public ParametersContextBuilder index(Object arg){
		put(SchemaProperties.INDEX_NAME, arg);
		return instance();
	}

	public ParametersContextBuilder index(SqlComparisonOperator op, String...arg){
		putOperatorValues(SchemaProperties.INDEX_NAME, op, arg);
		return instance();
	}
	
	public ParametersContextBuilder index(SqlComparisonOperator op, Object arg){
		putOperatorValues(SchemaProperties.INDEX_NAME, op, arg);
		return instance();
	}
	
	
	public ParametersContext build(){
		return this.parametersContext;
	}
}
