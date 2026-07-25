/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 */
package com.sqlapp.data.db.command.viewpoint;

import java.io.File;

import com.sqlapp.data.schemas.viewpoint.SchemaViewpoints;
import com.sqlapp.util.YamlConverter;

public class SchemaViewpointsIO {

	private final YamlConverter converter = new YamlConverter();

	public SchemaViewpoints read(File file) {
		return converter.fromJsonString(file, SchemaViewpoints.class);
	}

	public void write(File file, SchemaViewpoints viewpoints) {
		converter.writeJsonValue(file, viewpoints);
	}
}
