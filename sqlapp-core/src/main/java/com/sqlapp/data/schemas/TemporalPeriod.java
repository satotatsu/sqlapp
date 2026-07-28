/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.EndColumnNameProperty;
import com.sqlapp.data.schemas.properties.PeriodTypeProperty;
import com.sqlapp.data.schemas.properties.StartColumnNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * A SQL temporal period defined by start and end columns.
 */
public class TemporalPeriod extends AbstractSchemaObject<TemporalPeriod>
		implements HasParent<TemporalPeriodCollection>
		, PeriodTypeProperty<TemporalPeriod>
		, StartColumnNameProperty<TemporalPeriod>
		, EndColumnNameProperty<TemporalPeriod> {

	private static final long serialVersionUID = 1L;

	private TemporalPeriodType periodType = (TemporalPeriodType) SchemaProperties.PERIOD_TYPE.getDefaultValue();
	private String startColumnName;
	private String endColumnName;

	public TemporalPeriod() {
	}

	public TemporalPeriod(String name) {
		super(name);
	}

	@Override
	public TemporalPeriod setName(String name) {
		String originalName = this.getName();
		super.setName(name);
		Table table = this.getAncestor(Table.class);
		if (table != null && table.getSystemVersioning() != null
				&& CommonUtils.eq(table.getSystemVersioning().getPeriodName(), originalName)) {
			table.getSystemVersioning().setPeriodName(name);
		}
		return this;
	}

	@Override
	protected Supplier<TemporalPeriod> newInstance() {
		return TemporalPeriod::new;
	}

	@Override
	protected String getSimpleName() {
		return "period";
	}

	@Override
	public TemporalPeriodType getPeriodType() {
		return periodType;
	}

	@Override
	public TemporalPeriod setPeriodType(TemporalPeriodType periodType) {
		this.periodType = periodType;
		return this;
	}

	@Override
	public String getStartColumnName() {
		return startColumnName;
	}

	@Override
	public TemporalPeriod setStartColumnName(String startColumnName) {
		this.startColumnName = startColumnName;
		return this;
	}

	@Override
	public String getEndColumnName() {
		return endColumnName;
	}

	@Override
	public TemporalPeriod setEndColumnName(String endColumnName) {
		this.endColumnName = endColumnName;
		return this;
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax) throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.PERIOD_TYPE, this);
		stax.writeAttribute(SchemaProperties.START_COLUMN_NAME, this);
		stax.writeAttribute(SchemaProperties.END_COLUMN_NAME, this);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.PERIOD_TYPE, this.getPeriodType());
		builder.add(SchemaProperties.START_COLUMN_NAME, this.getStartColumnName());
		builder.add(SchemaProperties.END_COLUMN_NAME, this.getEndColumnName());
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof TemporalPeriod)) {
			return false;
		}
		TemporalPeriod val = (TemporalPeriod) obj;
		if (!equals(SchemaProperties.PERIOD_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.START_COLUMN_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.END_COLUMN_NAME, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	public TemporalPeriodCollection getParent() {
		return (TemporalPeriodCollection) super.getParent();
	}
}
