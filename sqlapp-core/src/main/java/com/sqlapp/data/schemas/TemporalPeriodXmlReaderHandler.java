/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas;

class TemporalPeriodXmlReaderHandler extends AbstractNamedObjectXmlReaderHandler<TemporalPeriod> {

	TemporalPeriodXmlReaderHandler() {
		super(TemporalPeriod::new);
	}

	@Override
	protected TemporalPeriodCollection toParent(Object parentObject) {
		if (parentObject instanceof TemporalPeriodCollection) {
			return (TemporalPeriodCollection) parentObject;
		}
		if (parentObject instanceof Table) {
			return ((Table) parentObject).getTemporalPeriods();
		}
		return null;
	}
}
