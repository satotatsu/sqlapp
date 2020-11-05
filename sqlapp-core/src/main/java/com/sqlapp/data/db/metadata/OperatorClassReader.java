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

import static com.sqlapp.util.CommonUtils.eq;

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.DbObjects;
import com.sqlapp.data.schemas.FunctionFamily;
import com.sqlapp.data.schemas.OperatorClass;
import com.sqlapp.data.schemas.OperatorFamily;
import com.sqlapp.data.schemas.SchemaObjectProperties;

/**
 * OperatorClass読み込み
 * 
 * @author satoh
 * 
 */
public abstract class OperatorClassReader extends
		AbstractSchemaObjectReader<OperatorClass> {
	/**
	 * Operatorクラス名
	 */
	protected final static String OPERATOR_CLASS_NAME = "operator_class_name";

	protected OperatorClassReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaObjectProperties getSchemaObjectProperties(){
		return SchemaObjectProperties.OPERATOR_CLASSES;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.AbstractNamedMetadataFactory#getNameLabel
	 * ()
	 */
	@Override
	protected String getNameLabel() {
		return DbObjects.OPERATOR_CLASS.getCamelCaseNameLabel();
	}

	@Override
	public List<OperatorClass> getAllFull(Connection connection) {
		ParametersContext context = defaultParametersContext(connection);
		List<OperatorClass> operatorClassList = getAll(connection, context);
		loadAllMetadata(connection, operatorClassList);
		return operatorClassList;
	}

	protected void loadAllMetadata(Connection connection,
			List<OperatorClass> operatorClassList) {
		OperatorFamilyReader oReader = getOperatorFamilyReader();
		if (oReader != null) {
			List<OperatorFamily> operatorFamilyList = oReader
					.getAllFull(connection);
			for (OperatorClass operatorClass : operatorClassList) {
				addOperatorFamilies(operatorFamilyList, operatorClass);
			}
		}
		FunctionFamilyReader fReader = getFunctionFamilyReader();
		if (fReader != null) {
			List<FunctionFamily> functionFamilyList = fReader
					.getAllFull(connection);
			for (OperatorClass operatorClass : operatorClassList) {
				addFunctionFamilies(functionFamilyList, operatorClass);
			}
		}
	}

	protected static void addOperatorFamilies(
			List<OperatorFamily> operatorFamilyList, OperatorClass operatorClass) {
		for (OperatorFamily operatorFamily : operatorFamilyList) {
			if (!eq(operatorClass.getCatalogName(),
					operatorFamily.getCatalogName())) {
				continue;
			}
			if (!eq(operatorClass.getSchemaName(),
					operatorFamily.getSchemaName())) {
				continue;
			}
			if (!eq(operatorClass.getName(),
					operatorFamily.getOperatorClassName())) {
				continue;
			}
			operatorClass.getOperatorFamilies().add(operatorFamily);
		}
	}

	protected static void addFunctionFamilies(
			List<FunctionFamily> functionFamilyList, OperatorClass operatorClass) {
		for (FunctionFamily functionFamily : functionFamilyList) {
			if (!eq(operatorClass.getCatalogName(),
					functionFamily.getCatalogName())) {
				continue;
			}
			if (!eq(operatorClass.getSchemaName(),
					functionFamily.getSchemaName())) {
				continue;
			}
			if (!eq(operatorClass.getName(),
					functionFamily.getOperatorClassName())) {
				continue;
			}
			operatorClass.getFunctionFamilies().add(functionFamily);
		}
	}

	protected OperatorFamilyReader getOperatorFamilyReader() {
		OperatorFamilyReader reader = newOperatorFamilyReader();
		reader.setCatalogName(this.getCatalogName());
		reader.setSchemaName(this.getSchemaName());
		reader.setObjectName(this.getObjectName());
		setReaderParameter(reader);
		return reader;
	}

	protected void setReaderParameter(MetadataReader<?, ?> reader) {
		if (reader != null) {
			initializeChild(reader);
		}
	}

	protected abstract OperatorFamilyReader newOperatorFamilyReader();

	protected FunctionFamilyReader getFunctionFamilyReader() {
		FunctionFamilyReader reader = newFunctionFamilyReader();
		reader.setCatalogName(this.getCatalogName());
		reader.setSchemaName(this.getSchemaName());
		reader.setObjectName(this.getObjectName());
		setReaderParameter(reader);
		return reader;
	}

	protected abstract FunctionFamilyReader newFunctionFamilyReader();
}
