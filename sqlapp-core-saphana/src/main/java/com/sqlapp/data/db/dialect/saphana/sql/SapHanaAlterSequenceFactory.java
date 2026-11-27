/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

/**
 * Applies the explicitly supplied sequence settings; START is a restart on
 * ALTER.
 */
public class SapHanaAlterSequenceFactory extends AbstractAlterSequenceFactory<SapHanaSqlBuilder> {
	@Override
	public java.util.List<com.sqlapp.data.db.sql.SqlOperation> createDiffSql(
			final com.sqlapp.data.schemas.DbObjectDifference difference) {
		var original = (Sequence) difference.getOriginal();
		var target = (Sequence) difference.getTarget();
		if (!java.util.Objects.equals(original.getName(), target.getName())
				|| !java.util.Objects.equals(original.getSchemaName(), target.getSchemaName())
				|| original.getDataType() != target.getDataType()
				|| !java.util.Objects.equals(original.getDataTypeName(), target.getDataTypeName()))
			throw new IllegalArgumentException("SAP HANA sequence identity/type changes require an explicit migration");
		if (original.getSpecifics().get("RESET_BY_QUERY") != null
				&& target.getSpecifics().get("RESET_BY_QUERY") == null)
			throw new IllegalArgumentException("SAP HANA RESET BY removal requires explicit DDL");
		var settings = target.clone();
		if (java.util.Objects.equals(original.getStartValue(), target.getStartValue()))
			settings.setStartValue((java.math.BigDecimal) null);
		return createSql(settings);
	}

	@Override
	protected void addCreateObject(final Sequence sequence, final SapHanaSqlBuilder builder) {
		if (sequence.getIncrementBy() != null && sequence.getIncrementBy().signum() == 0)
			throw new IllegalArgumentException("SAP HANA sequence increment must not be zero");
		if (sequence.getCacheSize() != null && sequence.getCacheSize() < 0)
			throw new IllegalArgumentException("SAP HANA sequence cache size must not be negative");
		builder._add("ALTER SEQUENCE").name(sequence, getOptions().isDecorateSchemaName());
		if (sequence.getStartValue() != null)
			builder._add(" RESTART WITH ")._add(sequence.getStartValue());
		if (sequence.getIncrementBy() != null)
			builder._add(" INCREMENT BY ")._add(sequence.getIncrementBy());
		if (sequence.getMinValue() != null)
			builder._add(" MINVALUE ")._add(sequence.getMinValue());
		if (sequence.getMaxValue() != null)
			builder._add(" MAXVALUE ")._add(sequence.getMaxValue());
		builder._add(sequence.isCycle() ? " CYCLE" : " NO CYCLE");
		if (!sequence.isCache() || sequence.getCacheSize() != null && sequence.getCacheSize() <= 1)
			builder._add(" NO CACHE");
		else if (sequence.getCacheSize() != null)
			builder._add(" CACHE ")._add(sequence.getCacheSize());
		String reset = sequence.getSpecifics().get("RESET_BY_QUERY");
		if (reset != null && !reset.isBlank())
			builder._add(" RESET BY ")._add(reset);
	}
}
