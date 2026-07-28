/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas;

class TemporalPeriodCollectionXmlReaderHandler
		extends AbstractNamedObjectCollectionXmlReaderHandler<TemporalPeriodCollection> {

	TemporalPeriodCollectionXmlReaderHandler() {
		super(TemporalPeriodCollection::new);
		setChild(new TemporalPeriodXmlReaderHandler());
	}

}
