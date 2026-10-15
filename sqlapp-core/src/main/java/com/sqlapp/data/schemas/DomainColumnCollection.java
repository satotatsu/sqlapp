/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import java.util.function.Supplier;

public final class DomainColumnCollection extends AbstractSchemaObjectCollection<DomainColumn>
		implements HasParent<Domain>, NewElement<DomainColumn, DomainColumnCollection> {
	private static final long serialVersionUID = 1L;

	public DomainColumnCollection() {
	}

	protected DomainColumnCollection(Domain domain) {
		super(domain);
	}

	@Override protected Supplier<DomainColumnCollection> newInstance() { return DomainColumnCollection::new; }
	@Override protected Supplier<DomainColumn> getElementSupplier() { return DomainColumn::new; }
	@Override public DomainColumn newElement() { return super.newElementInternal(); }
	@Override public Domain getParent() { return (Domain) super.getParent(); }
	@Override protected String getSimpleName() { return "columns"; }
	@Override public void sort() { }

	@Override
	protected boolean beforeAdd(DomainColumn column) {
		if (column.getName() == null || column.getName().isBlank()) {
			throw new IllegalArgumentException("Domain column requires a name");
		}
		if (contains(column.getName())) {
			throw new IllegalArgumentException("Duplicate domain column: " + column.getName());
		}
		return true;
	}
}
