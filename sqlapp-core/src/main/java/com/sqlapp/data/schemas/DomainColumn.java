/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import java.util.function.Supplier;

/** A typed component of a row-valued domain. */
public final class DomainColumn extends AbstractColumn<DomainColumn> implements HasParent<DomainColumnCollection> {
	private static final long serialVersionUID = 1L;

	public DomainColumn() {
	}

	public DomainColumn(String name) {
		super(name);
	}

	@Override
	protected Supplier<DomainColumn> newInstance() {
		return DomainColumn::new;
	}

	@Override
	public DomainColumnCollection getParent() {
		return (DomainColumnCollection) super.getParent();
	}
}
