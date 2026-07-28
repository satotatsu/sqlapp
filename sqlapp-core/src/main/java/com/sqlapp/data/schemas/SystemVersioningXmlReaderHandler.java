/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas;

class SystemVersioningXmlReaderHandler extends AbstractBaseDbObjectXmlReaderHandler<SystemVersioning> {

	SystemVersioningXmlReaderHandler() {
		super(SystemVersioning::new);
	}

	@Override
	protected SystemVersioning createNewInstance(Object parentObject) {
		SystemVersioning result = createNewInstance();
		if (parentObject instanceof Table) {
			result.setParent((Table) parentObject);
		}
		return result;
	}
}
