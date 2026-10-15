/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import java.util.function.Supplier;
import javax.xml.stream.XMLStreamException;
import com.sqlapp.data.schemas.properties.DeferrabilityProperty;
import com.sqlapp.data.schemas.properties.EnableProperty;
import com.sqlapp.data.schemas.properties.ExpressionProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/** A named CHECK belonging to a domain rather than a table. */
public final class DomainCheckConstraint extends AbstractSchemaObject<DomainCheckConstraint>
		implements HasParent<DomainCheckConstraintCollection>, ExpressionProperty<DomainCheckConstraint>,
		DeferrabilityProperty<DomainCheckConstraint>, EnableProperty<DomainCheckConstraint> {
	private static final long serialVersionUID = 1L;
	private String expression;
	private Deferrability deferrability;
	private boolean enable = true;

	public DomainCheckConstraint() { }
	public DomainCheckConstraint(String name, String expression) {
		super(name);
		this.expression = expression;
	}
	@Override protected Supplier<DomainCheckConstraint> newInstance() { return DomainCheckConstraint::new; }
	@Override public String getExpression() { return expression; }
	@Override public DomainCheckConstraint setExpression(String expression) { this.expression = expression; return this; }
	@Override public Deferrability getDeferrability() { return deferrability; }
	@Override public DomainCheckConstraint setDeferrability(Deferrability value) { deferrability = value; return this; }
	@Override public boolean isEnable() { return enable; }
	@Override public DomainCheckConstraint setEnable(boolean value) { enable = value; return this; }
	@Override public DomainCheckConstraintCollection getParent() { return (DomainCheckConstraintCollection) super.getParent(); }
	@Override protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.EXPRESSION, expression);
		builder.add(SchemaProperties.DEFERRABILITY, deferrability);
		builder.add(SchemaProperties.ENABLE, enable);
	}

	@Override
	public boolean equals(Object obj, EqualsHandler handler) {
		if (!(obj instanceof DomainCheckConstraint other) || !super.equals(obj, handler)) return false;
		return equals(SchemaProperties.EXPRESSION, other, handler)
				&& equals(SchemaProperties.DEFERRABILITY, other, handler)
				&& equals(SchemaProperties.ENABLE, other, handler) && handler.equalsResult(this, obj);
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax) throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.EXPRESSION, this);
		stax.writeAttribute(SchemaProperties.DEFERRABILITY, this);
		if (!enable) stax.writeAttribute(SchemaProperties.ENABLE, this);
	}
}
