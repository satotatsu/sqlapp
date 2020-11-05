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

GO

INSERT INTO apps (id, created_at, updated_at) VALUES (1, '2016-12-31', '2018-12-31')

GO 2
