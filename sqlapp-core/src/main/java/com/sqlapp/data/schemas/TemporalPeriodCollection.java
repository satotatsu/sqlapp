/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas;

import java.util.function.Supplier;

/**
 * Temporal periods belonging to a table.
 */
public class TemporalPeriodCollection extends AbstractSchemaObjectCollection<TemporalPeriod>
		implements HasParent<Table>, NewElement<TemporalPeriod, TemporalPeriodCollection> {

	private static final long serialVersionUID = 1L;

	protected TemporalPeriodCollection() {
	}

	protected TemporalPeriodCollection(Table table) {
		super(table);
	}

	@Override
	protected Supplier<TemporalPeriodCollection> newInstance() {
		return TemporalPeriodCollection::new;
	}

	@Override
	public TemporalPeriodCollection clone() {
		return (TemporalPeriodCollection) super.clone();
	}

	@Override
	protected Supplier<TemporalPeriod> getElementSupplier() {
		return TemporalPeriod::new;
	}

	@Override
	public TemporalPeriod newElement() {
		return newElementInternal();
	}

	@Override
	protected String getSimpleName() {
		return "periods";
	}

	@Override
	public Table getParent() {
		return (Table) super.getParent();
	}
}
