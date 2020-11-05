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
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.DbObjects;
import com.sqlapp.data.schemas.FunctionFamily;
import com.sqlapp.data.schemas.OperatorClass;

public abstract class FunctionFamilyReader extends
		MetadataReader<FunctionFamily, OperatorClass> {
	/**
	 * Operatorクラス名
	 */
	protected final static String OPERATOR_CLASS_NAME = "operator_class_name";
	/**
	 * オブジェクト名
	 */
	private String objectName = null;

	public String getObjectName() {
		return objectName;
	}

	public void setObjectName(String objectName) {
		this.objectName = objectName;
	}

	/**
	 * @param schemaName
	 *            the schemaName to set
	 */
	public FunctionFamilyReader setSchemaName(String schemaName) {
		this.schemaName = schemaName;
		return this;
	}

	/**
	 * スキーマ名
	 */
	private String schemaName = null;

	public String getSchemaName() {
		return schemaName;
	}

	protected FunctionFamilyReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	public void loadFull(Connection connection, OperatorClass target) {
		ParametersContext context = defaultParametersContext(connection);
		List<FunctionFamily> list = getAll(connection, context);
		OperatorClassReader.addFunctionFamilies(list, target);
	}

	@Override
	protected ParametersContext defaultParametersContext(Connection connection) {
		ParametersContext context = newParametersContext(connection,
				this.getCatalogName(), this.getSchemaName());
		context.put(getNameLabel(),
				nativeCaseString(connection, this.getObjectName()));
		return context;
	}

	protected String getNameLabel() {
		return DbObjects.OPERATOR_CLASS.getCamelCaseNameLabel();
	}

	protected String getObjectName(ParametersContext context) {
		return (String) context.get(getNameLabel());
	}

}
