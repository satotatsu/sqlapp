/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.DbUtils.getStringValue;

import java.sql.Connection;
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
import com.sqlapp.data.schemas.Setting;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Oracleのスキーマ読み込み
 * 
 * @author satoh
 * 
 */
public class OracleSchemaReader extends SchemaReader {

	protected OracleSchemaReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Schema> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final boolean dba = OracleMetadataUtils.hasSelectPrivilege(connection,
				this.getDialect(), "SYS", "DBA_USERS");
		SqlNode node = getSqlSqlNode(productVersionInfo);
		OracleMetadataUtils.setDba(dba, context);
		final List<Schema> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Schema obj = createSchema(rs, dba);
				result.add(obj);
			}
		});
		return result;
	}

	@Override
	protected void setSchemaBefore(Connection connection, Schema schema) {
		Setting setting = getSettings().get("nls_length_semantics");
		if (setting != null) {
			schema.setCharacterSemantics(setting.getValue());
		}
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("schemas.sql");
	}

	protected Schema createSchema(ExResultSet rs, boolean dba)
			throws SQLException {
		Schema obj = new Schema(getString(rs, "USERNAME"));
		obj.setCreatedAt(rs.getTimestamp("CREATED"));
		return obj;
	}

	@Override
	public String getCurrentSchemaName(Connection connection) {
		StringBuilder sql = new StringBuilder("SELECT");
		sql.append(" SYS_CONTEXT('USERENV','CURRENT_SCHEMA')");
		sql.append(" FROM DUAL");
		return getStringValue(connection, sql.toString());
	}

	@Override
	protected TableReader newTableReader() {
		return new OracleTableReader(this.getDialect());
	}

	@Override
	protected ViewReader newViewReader() {
		return new OracleViewReader(this.getDialect());
	}

	@Override
	protected MviewReader newMviewReader() {
		return new OracleMviewReader(this.getDialect());
	}

	@Override
	protected SequenceReader newSequenceReader() {
		return new OracleSequenceReader(this.getDialect());
	}

	@Override
	protected DbLinkReader newDbLinkReader() {
		return new OracleDbLinkReader(this.getDialect());
	}

	@Override
	protected DomainReader newDomainReader() {
		return new OracleDomainReader(this.getDialect());
	}

	@Override
	protected SynonymReader newSynonymReader() {
		return new OracleSynonymReader(this.getDialect());
	}

	@Override
	protected TableLinkReader newTableLinkReader() {
		return null;
	}

	@Override
	protected RuleReader newRuleReader() {
		return null;
	}

	@Override
	protected FunctionReader newFunctionReader() {
		return new OracleFunctionReader(this.getDialect());
	}

	@Override
	protected ProcedureReader newProcedureReader() {
		return new OracleProcedureReader(this.getDialect());
	}

	@Override
	protected PackageReader newPackageReader() {
		return new OraclePackageReader(this.getDialect());
	}

	@Override
	protected PackageBodyReader newPackageBodyReader() {
		return new OraclePackageBodyReader(this.getDialect());
	}

	@Override
	protected TypeReader newTypeReader() {
		return new OracleTypeReader(this.getDialect());
	}

	@Override
	protected TypeBodyReader newTypeBodyReader() {
		return new OracleTypeBodyReader(this.getDialect());
	}

	@Override
	protected TriggerReader newTriggerReader() {
		return new OracleTriggerReader(this.getDialect());
	}

	@Override
	protected ConstantReader newConstantReader() {
		return null;
	}

	@Override
	protected XmlSchemaReader newXmlSchemaReader() {
		return null;
	}

	@Override
	protected MviewLogReader newMviewLogReader() {
		return new OracleMviewLogReader(this.getDialect());
	}

	@Override
	protected OperatorReader newOperatorReader() {
		return new OracleOperatorReader(this.getDialect());
	}

	@Override
	protected ExternalTableReader newExternalTableReader() {
		return new OracleExternalTableReader(this.getDialect());
	}

	@Override
	protected OperatorClassReader newOperatorClassReader() {
		return null;
	}

	@Override
	protected EventReader newEventReader() {
		return null;
	}

	@Override
	protected DimensionReader newDimensionReader() {
		return new OracleDimensionReader(this.getDialect());
	}

	@Override
	protected MaskReader newMaskReader() {
		return null;
	}
}
