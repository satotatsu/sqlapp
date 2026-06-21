package com.sqlapp.data.db.command.export.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sqlapp.data.schemas.rowiterator.DataFormat;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExportConfig {
	/** format */
	@JsonProperty(index = 0)
	private DataFormat format;
	/** SQL */
	@JsonProperty(index = 1)
	private String query;
	/** file encoding */
	@JsonProperty(index = 2)
	private String encoding = "UTF-8";
	/** file header */
	@JsonProperty(index = 3)
	private boolean header = true;
}
