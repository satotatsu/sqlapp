CREATE OR REPLACE FUNCTION add ($1 INT, $2 INT)
RETURNS INT
AS $$
select $1 + $2;
$$
IMMUTABLE
RETURNS NULL ON NULL INPUT
SECURITY INVOKER