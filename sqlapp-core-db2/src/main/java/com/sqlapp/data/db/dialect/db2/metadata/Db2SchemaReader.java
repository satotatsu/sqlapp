/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.DbUtils.getStringValue;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ConstantReader;
import com.sqlapp.data.db.metadata.DbLinkReader;
import com.sqlapp.data.db.metadata.DimensionReader;
import com.sqlapp.data.db.metadata.DomainReader;
import com.sqlapp.data.db.metadata.EventReader;
import com.sqlapp.data.db.metadata.ExternalTableReader;
import com.sqlapp.data.db.metadata.FunctionReader;
import com.sqlapp.data.db.metadata.MaskReader;
import com.sqlapp.data.db.metadata.MviewLogReader;
import com.sqlapp.data.db.metadata.MviewReader;
import com.sqlapp.data.db.metadata.OperatorClassReader;
import com.sqlapp.data.db.metadata.OperatorReader;
import com.sqlapp.data.db.metadata.PackageBodyReader;
import com.sqlapp.data.db.metadata.PackageReader;
import com.sqlapp.data.db.metadata.ProcedureReader;
import com.sqlapp.data.db.metadata.RuleReader;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.db.metadata.SequenceReader;
import com.sqlapp.data.db.metadata.SynonymReader;
import com.sqlapp.data.db.metadata.TableLinkReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.metadata.TriggerReader;
import com.sqlapp.data.db.metadata.TypeBodyReader;
import com.sqlapp.data.db.metadata.TypeReader;
import com.sqlapp.data.db.metadata.ViewReader;
import com.sqlapp.data.db.metadata.XmlSchemaReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Schema;

public class Db2SchemaReader extends SchemaReader {

	protected Db2SchemaReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	public String getCurrentSchemaName(Connection connection) {
		StringBuilder sql = new StringBuilder("SELECT");
		sql.append(" CURRENT SCHEMA");
		sql.append(" FROM SYSIBM.SYSDUMMY1");
		return getStringValue(connection, sql.toString());
	}

	@Override
	protected List<Schema> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Schema> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Schema obj = createSchema(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("schemas.sql");
		return node;
	}

	protected Schema createSchema(ExResultSet rs) throws SQLException {
		Schema obj = new Schema(getString(rs, SCHEMA_NAME));
		obj.setRemarks(getString(rs, REMARKS));
		obj.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
		return obj;
	}

	@Override
	protected TableReader newTableReader() {
		return new Db2TableReader(this.getDialect());
	}

	@Override
	protected ViewReader newViewReader() {
		return new Db2ViewReader(this.getDialect());
	}

	@Override
	protected MviewReader newMviewReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected SequenceReader newSequenceReader() {
		return new Db2SequenceReader(this.getDialect());
	}

	@Override
	protected DbLinkReader newDbLinkReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected DomainReader newDomainReader() {
		return new Db2DomainReader(this.getDialect());
	}

	@Override
	protected TypeReader newTypeReader() {
		return new Db2TypeReader(this.getDialect());
	}

	@Override
	protected TypeBodyReader newTypeBodyReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected SynonymReader newSynonymReader() {
		return new Db2SynonymReader(this.getDialect());
	}

	@Override
	protected TableLinkReader newTableLinkReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected RuleReader newRuleReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected FunctionReader newFunctionReader() {
		return new Db2FunctionReader(this.getDialect());
	}

	@Override
	protected ProcedureReader newProcedureReader() {
		return new Db2ProcedureReader(this.getDialect());
	}

	@Override
	protected PackageReader newPackageReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected PackageBodyReader newPackageBodyReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected ConstantReader newConstantReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected TriggerReader newTriggerReader() {
		return new Db2TriggerReader(this.getDialect());
	}

	@Override
	protected MviewLogReader newMviewLogReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected XmlSchemaReader newXmlSchemaReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected OperatorReader newOperatorReader() {
		return null;
	}

	@Override
	protected OperatorClassReader newOperatorClassReader() {
		return null;
	}

	@Override
	protected ExternalTableReader newExternalTableReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected EventReader newEventReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected DimensionReader newDimensionReader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected MaskReader newMaskReader() {
		return null;
	}

}
