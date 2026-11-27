/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import java.util.function.Supplier;

/** Schema-owned physical projections, separate from views and indexes. */
public final class ProjectionCollection extends AbstractSchemaObjectCollection<Projection>
		implements HasParent<Schema>, NewElement<Projection, ProjectionCollection> {
	private static final long serialVersionUID = 1L;

	protected ProjectionCollection() {
	}

	protected ProjectionCollection(Schema schema) {
		super(schema);
	}

	@Override
	protected Supplier<ProjectionCollection> newInstance() {
		return ProjectionCollection::new;
	}

	@Override
	protected Supplier<Projection> getElementSupplier() {
		return Projection::new;
	}

	@Override
	public Schema getParent() {
		return getSchema();
	}

	@Override
	public Projection newElement() {
		return newElementInternal();
	}

	@Override
	public ProjectionCollection clone() {
		return (ProjectionCollection) super.clone();
	}
}
