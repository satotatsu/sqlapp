/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 */
package com.sqlapp.data.schemas.viewpoint;

import java.util.List;
import java.util.ArrayList;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaViewpoint {

	private String id;

	private String name;

	private String description;

	private String color;

	private List<String> tables = new ArrayList<>();
}
