use master/*#schemaNameSuffix*/;
SET SESSION FOREIGN_KEY_CHECKS=0;

/* Create Tables */


CREATE TABLE apps
(
	id bigint NOT NULL,
	created_at datetime,
	updated_at datetime,
	PRIMARY KEY (id),
	UNIQUE (created_at)
);


CREATE TABLE app_icons
(
	id bigint NOT NULL,
	binary_data mediumblob NOT NULL,
	PRIMARY KEY (id)
);


