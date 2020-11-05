use appdata_common/*#schemaNameSuffix*/;
create TABLE sequence_numbers
(
	sequence_name varchar(127) not null,
	start_value bigint default 100000 not null,
	step bigint default 1 not null,
	current_value bigint default 100000 not null,
	cache_size bigint default 1000 not null,
	version_no bigint default 1 not null,
	primary key (sequence_name)
);

-- ###################################################################################################

--//@UNDO
use appdata_common/*#schemaNameSuffix*/;
DROP TABLE sequence_numbers;
