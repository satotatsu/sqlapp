/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-drsum.
 *
 * sqlapp-core-drsum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-drsum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-drsum.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.drsum.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
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

/** Dr.Sum has no schemas; one unnamed schema holds the database's tables and views. */
public class DrSumSchemaReader extends SchemaReader {

	protected DrSumSchemaReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Schema> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		return list(new Schema());
	}

	@Override
	protected TableReader newTableReader() {
		return new DrSumTableReader(getDialect());
	}

	@Override
	protected ViewReader newViewReader() {
		return new DrSumViewReader(getDialect());
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
		return null;
	}

	@Override
	protected ProcedureReader newProcedureReader() {
		return null;
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
		return null;
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
	protected OperatorClassReader newOperatorClassReader() {
		return null;
	}

	@Override
	protected ExternalTableReader newExternalTableReader() {
		return null;
	}

	@Override
	protected EventReader newEventReader() {
		return null;
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
