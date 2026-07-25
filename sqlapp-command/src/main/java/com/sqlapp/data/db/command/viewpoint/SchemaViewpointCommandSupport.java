/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 */
package com.sqlapp.data.db.command.viewpoint;

import java.io.File;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.viewpoint.SchemaViewpointResolver;
import com.sqlapp.data.schemas.viewpoint.SchemaViewpointResolver.Resolution;
import com.sqlapp.exceptions.CommandException;

public class SchemaViewpointCommandSupport {

	public Resolution resolve(Catalog catalog, File viewpointsFile, String viewpointId) {
		if (viewpointsFile == null || !viewpointsFile.isFile()) {
			throw new CommandException("Schema viewpoints file does not exist: " + viewpointsFile);
		}
		try {
			return new SchemaViewpointResolver().resolve(catalog,
					new SchemaViewpointsIO().read(viewpointsFile), viewpointId);
		} catch (IllegalArgumentException e) {
			throw new CommandException("Invalid schema viewpoint selection: " + e.getMessage(), e);
		}
	}

	public void retainSelectedTables(Catalog catalog, Resolution resolution) {
		var selected = java.util.Collections.newSetFromMap(
				new java.util.IdentityHashMap<com.sqlapp.data.schemas.Table, Boolean>());
		selected.addAll(resolution.tables());
		catalog.getSchemas().forEach(schema -> schema.getTables().removeIf(table -> !selected.contains(table)));
	}
}
