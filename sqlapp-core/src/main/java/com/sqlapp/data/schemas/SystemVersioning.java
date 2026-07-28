/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.EnableProperty;
import com.sqlapp.data.schemas.properties.HistoryTableNameProperty;
import com.sqlapp.data.schemas.properties.HistoryTableSchemaNameProperty;
import com.sqlapp.data.schemas.properties.ImplicitProperty;
import com.sqlapp.data.schemas.properties.PeriodNameProperty;
import com.sqlapp.data.schemas.properties.TransactionIdColumnNameProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * System-versioning configuration for a table.
 */
public class SystemVersioning extends AbstractDbObject<SystemVersioning>
		implements HasParent<Table>
		, EnableProperty<SystemVersioning>
		, ImplicitProperty<SystemVersioning>
		, PeriodNameProperty<SystemVersioning>
		, HistoryTableSchemaNameProperty<SystemVersioning>
		, HistoryTableNameProperty<SystemVersioning>
		, TransactionIdColumnNameProperty<SystemVersioning> {

	private static final long serialVersionUID = 1L;

	private boolean enable = (Boolean) SchemaProperties.ENABLE.getDefaultValue();
	private boolean implicit = (Boolean) SchemaProperties.IMPLICIT.getDefaultValue();
	private String periodName = (String) SchemaProperties.PERIOD_NAME.getDefaultValue();
	private String historyTableSchemaName;
	private String historyTableName;
	private String transactionIdColumnName;

	@Override
	protected Supplier<SystemVersioning> newInstance() {
		return SystemVersioning::new;
	}

	@Override
	protected String getSimpleName() {
		return "systemVersioning";
	}

	@Override
	public boolean isEnable() {
		return enable;
	}

	@Override
	public SystemVersioning setEnable(boolean enable) {
		this.enable = enable;
		return this;
	}

	@Override
	public boolean isImplicit() {
		return implicit;
	}

	@Override
	public SystemVersioning setImplicit(boolean implicit) {
		this.implicit = implicit;
		return this;
	}

	@Override
	public String getPeriodName() {
		return periodName;
	}

	@Override
	public SystemVersioning setPeriodName(String periodName) {
		this.periodName = periodName;
		return this;
	}

	@Override
	public String getHistoryTableSchemaName() {
		return historyTableSchemaName;
	}

	@Override
	public SystemVersioning setHistoryTableSchemaName(String historyTableSchemaName) {
		this.historyTableSchemaName = historyTableSchemaName;
		return this;
	}

	@Override
	public String getHistoryTableName() {
		return historyTableName;
	}

	@Override
	public SystemVersioning setHistoryTableName(String historyTableName) {
		this.historyTableName = historyTableName;
		return this;
	}

	@Override
	public String getTransactionIdColumnName() {
		return transactionIdColumnName;
	}

	@Override
	public SystemVersioning setTransactionIdColumnName(String transactionIdColumnName) {
		this.transactionIdColumnName = transactionIdColumnName;
		return this;
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax) throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		if (!this.isEnable()) {
			stax.writeAttribute(SchemaProperties.ENABLE, this);
		}
		if (this.isImplicit()) {
			stax.writeAttribute(SchemaProperties.IMPLICIT, this);
		}
		stax.writeAttribute(SchemaProperties.PERIOD_NAME, this);
		stax.writeAttribute(SchemaProperties.HISTORY_TABLE_SCHEMA_NAME, this);
		stax.writeAttribute(SchemaProperties.HISTORY_TABLE_NAME, this);
		stax.writeAttribute(SchemaProperties.TRANSACTION_ID_COLUMN_NAME, this);
	}

	@Override
	protected void toString(ToStringBuilder builder) {
		builder.add(SchemaProperties.ENABLE, this.isEnable());
		builder.add(SchemaProperties.IMPLICIT, this.isImplicit());
		builder.add(SchemaProperties.PERIOD_NAME, this.getPeriodName());
		builder.add(SchemaProperties.HISTORY_TABLE_SCHEMA_NAME, this.getHistoryTableSchemaName());
		builder.add(SchemaProperties.HISTORY_TABLE_NAME, this.getHistoryTableName());
		builder.add(SchemaProperties.TRANSACTION_ID_COLUMN_NAME, this.getTransactionIdColumnName());
		super.toString(builder);
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof SystemVersioning)) {
			return false;
		}
		SystemVersioning val = (SystemVersioning) obj;
		if (!equals(SchemaProperties.ENABLE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.IMPLICIT, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PERIOD_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.HISTORY_TABLE_SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.HISTORY_TABLE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.TRANSACTION_ID_COLUMN_NAME, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	public Table getParent() {
		return (Table) super.getParent();
	}

	@Override
	public int compareTo(SystemVersioning o) {
		return 0;
	}
}
