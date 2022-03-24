/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.mysql.metadata;

import static com.sqlapp.util.DbUtils.getStringValue;

import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.information_schema.metadata.AbstractISSchemaReader;
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
import com.sqlapp.data.db.metadata.SequenceReader;
import com.sqlapp.data.db.metadata.SynonymReader;
import com.sqlapp.data.db.metadata.TableLinkReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.metadata.TriggerReader;
import com.sqlapp.data.db.metadata.TypeBodyReader;
import com.sqlapp.data.db.metadata.TypeReader;
import com.sqlapp.data.db.metadata.ViewReader;
import com.sqlapp.data.db.metadata.XmlSchemaReader;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.jdbc.ExResultSet;

/**
 * MySqlのスキーマ読み込み
 * 
 * @author satoh
 * 
 */
public class MySqlSchemaReader extends AbstractISSchemaReader {

	public MySqlSchemaReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Schema createSchema(ExResultSet rs) throws SQLException {
		Schema obj = super.createSchema(rs);
		obj.setCharacterSet(getString(rs, "DEFAULT_CHARACTER_SET_NAME"));
		obj.setCollation(getString(rs, "DEFAULT_COLLATION_NAME"));
		MySqlUtils.setCharacterSemantics(obj);
		return obj;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.AbstractSchemaReader#getCurrentSchemaName
	 * (java.sql.Connection)
	 */
	@Override
	public String getCurrentSchemaName(Connection connection) {
		return getStringValue(connection, "select DATABASE()");
	}

	@Override
	protected TableReader newTableReader() {
		return new MySqlTableReader(this.getDialect());
	}

	@Override
	protected ViewReader newViewReader() {
		return new MySqlViewReader(this.getDialect());
	}

	@Override
	protected MviewReader newMviewReader() {
		return null;
	}

	@Override
	protected SequenceReader newSequenceReader() {
		return null;
	}

	@Override
	protected DbLinkReader newDbLinkReader() {
		return null;
	}

	@Override
	protected DomainReader newDomainReader() {
		return null;
	}

	@Override
	protected SynonymReader newSynonymReader() {
		return null;
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
		return new MySqlFunction553Reader(this.getDialect());
	}

	@Override
	protected ProcedureReader newProcedureReader() {
		return new MySqlProcedure553Reader(this.getDialect());
	}

	@Override
	protected PackageReader newPackageReader() {
		return null;
	}

	@Override
	protected PackageBodyReader newPackageBodyReader() {
		return null;
	}

	@Override
	protected TypeReader newTypeReader() {
		return null;
	}

	@Override
	protected TypeBodyReader newTypeBodyReader() {
		return null;
	}

	@Override
	protected TriggerReader newTriggerReader() {
		return new MySqlTriggerReader(this.getDialect());
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
		return null;
	}

	@Override
	protected OperatorReader newOperatorReader() {
		return null;
	}

	@Override
	protected ExternalTableReader newExternalTableReader() {
		return null;
	}

	@Override
	protected OperatorClassReader newOperatorClassReader() {
		return null;
	}

	@Override
	protected EventReader newEventReader() {
		return new MySqlEventReader(this.getDialect());
	}

	@Override
	protected DimensionReader newDimensionReader() {
		return null;
	}

	@Override
	protected MaskReader newMaskReader() {
		return null;
	}
}
