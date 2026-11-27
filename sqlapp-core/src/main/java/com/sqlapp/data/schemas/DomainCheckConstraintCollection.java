/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import java.util.function.Supplier;

/** Optional domain CHECKs; the owning domain retains the concise single-check API. */
public final class DomainCheckConstraintCollection extends AbstractSchemaObjectCollection<DomainCheckConstraint>
		implements HasParent<Domain>, NewElement<DomainCheckConstraint, DomainCheckConstraintCollection> {
	private static final long serialVersionUID = 1L;
	public DomainCheckConstraintCollection() { }
	protected DomainCheckConstraintCollection(Domain domain) { super(domain); }
	@Override protected Supplier<DomainCheckConstraintCollection> newInstance() { return DomainCheckConstraintCollection::new; }
	@Override protected Supplier<DomainCheckConstraint> getElementSupplier() { return DomainCheckConstraint::new; }
	@Override public DomainCheckConstraint newElement() { return super.newElementInternal(); }
	@Override public Domain getParent() { return (Domain) super.getParent(); }
	@Override protected String getSimpleName() { return "checks"; }
	@Override public void sort() { }
	@Override protected boolean beforeAdd(DomainCheckConstraint check) {
		if (check.getName() == null || check.getName().isBlank()) {
			throw new IllegalArgumentException("Domain checks require a name; use Domain.check for an unnamed check");
		}
		if (contains(check.getName())) {
			throw new IllegalArgumentException("Duplicate domain check: " + check.getName());
		}
		return true;
	}
}
