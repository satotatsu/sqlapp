/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.object.ReferenceColumnsProperty;
import com.sqlapp.util.xml.AbstractSetValue;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * Named NOT NULL constraint on one table column.
 */
public final class NotNullConstraint extends Constraint
		implements ReferenceColumnsProperty<NotNullConstraint> {
	private static final long serialVersionUID = 1L;
	private static final String NO_INHERIT = "noInherit";
	private static final String VALIDATED = "validated";
	private final ReferenceColumnCollection columns = new ReferenceColumnCollection();
	private boolean noInherit;
	private boolean validated = true;

	public NotNullConstraint() {
	}

	public NotNullConstraint(String name) {
		super(name);
	}

	public NotNullConstraint(String name, Column column) {
		super(name);
		setColumn(column);
	}

	@Override
	protected Supplier<Constraint> newInstance() {
		return NotNullConstraint::new;
	}

	@Override
	public ReferenceColumnCollection getColumns() {
		return columns;
	}

	public Column getColumn() {
		return columns.isEmpty() ? null : columns.get(0).getColumn();
	}

	public String getColumnName() {
		return columns.isEmpty() ? null : columns.get(0).getName();
	}

	public NotNullConstraint setColumn(Column column) {
		columns.clear();
		if (column != null) {
			columns.add(new ReferenceColumn(column));
			column.setNotNull(true);
		}
		return this;
	}

	public NotNullConstraint setColumnName(String columnName) {
		columns.clear();
		if (columnName != null) {
			columns.add(columnName);
		}
		return this;
	}

	public boolean isNoInherit() {
		return noInherit;
	}

	public NotNullConstraint setNoInherit(boolean noInherit) {
		this.noInherit = noInherit;
		return this;
	}

	public boolean isValidated() {
		return validated;
	}

	public NotNullConstraint setValidated(boolean validated) {
		this.validated = validated;
		return this;
	}

	void resetColumn(Table table) {
		columns.setTable(table);
		if (getColumn() != null) {
			getColumn().setNotNull(true);
		}
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		if (noInherit) {
			stax.writeAttribute(NO_INHERIT, true);
		}
		if (!validated) {
			stax.writeAttribute(VALIDATED, false);
		}
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		columns.writeXml(stax);
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof NotNullConstraint)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		NotNullConstraint val = (NotNullConstraint) obj;
		return noInherit == val.noInherit
				&& validated == val.validated
				&& eqColumnName(columns, val.columns)
				&& equalsHandler.equalsResult(this, obj);
	}

	private boolean eqColumnName(ReferenceColumnCollection left,
			ReferenceColumnCollection right) {
		return left.size() == right.size()
				&& (left.isEmpty()
						|| java.util.Objects.equals(left.get(0).getName(),
								right.get(0).getName()));
	}

	@Override
	protected void cloneProperties(Constraint clone) {
		super.cloneProperties(clone);
		NotNullConstraint copy = (NotNullConstraint) clone;
		copy.noInherit = noInherit;
		copy.validated = validated;
		if (!columns.isEmpty()) {
			copy.columns.add(columns.get(0).clone());
		}
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add("columnName", getColumnName());
		builder.add(NO_INHERIT, noInherit);
		builder.add(VALIDATED, validated);
		super.toStringDetail(builder);
	}

	@Override
	protected NotNullConstraint instance() {
		return this;
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<Constraint>
			getDbObjectXmlReaderHandler() {
		return new AbstractNamedObjectXmlReaderHandler<Constraint>(
				NotNullConstraint::new) {
			@Override
			protected void initializeSetValue() {
				super.initializeSetValue();
				register(NO_INHERIT,
						new AbstractSetValue<Constraint, Object>() {
							@Override
							public void setValue(Constraint target,
									String name, Object value) {
								((NotNullConstraint) target).setNoInherit(Boolean.parseBoolean(
										String.valueOf(value)));
							}
						});
				register(VALIDATED,
						new AbstractSetValue<Constraint, Object>() {
							@Override
							public void setValue(Constraint target,
									String name, Object value) {
								((NotNullConstraint) target).setValidated(Boolean.parseBoolean(
										String.valueOf(value)));
							}
						});
			}

			@Override
			protected ConstraintCollection toParent(Object parentObject) {
				return parentObject instanceof ConstraintCollection
						? (ConstraintCollection) parentObject : null;
			}
		};
	}
}
