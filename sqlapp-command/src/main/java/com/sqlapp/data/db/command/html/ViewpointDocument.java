/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 */
package com.sqlapp.data.db.command.html;

import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.schemas.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * HTML representation of one resolved Schema viewpoint.
 */
@Getter
@Setter
public class ViewpointDocument {

	private String id;

	private String name;

	private String description;

	private String color;

	private String diagramFileName;

	private String tabId;

	private RelationImageHolder image;

	private List<Table> tables = new ArrayList<>();

	public boolean contains(Table table) {
		return tables.contains(table);
	}
}
