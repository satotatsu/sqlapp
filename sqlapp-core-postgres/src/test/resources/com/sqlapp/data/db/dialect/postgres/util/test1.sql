
CREATE TABLE employee (id INT, 
                       name VARCHAR(10), 
                       salary DECIMAL(9,2));

INSERT INTO employee VALUES (1, 'aaaa', 35000), 
                            (2, 'bbbb', 35000);

/*comment1*/                          
                            
CREATE TABLE former_employee (id INT, name VARCHAR(10));

/*comment2
 * 
 * 
 */                          


CREATE FUNCTION check_p(name TEXT, pass TEXT)
RETURNS BOOLEAN AS $$
DECLARE passed BOOLEAN;
BEGIN
        SELECT  (pwd = $2) INTO pass
        FROM    pwds
        WHERE   name = $1;
        RETURN passed;
END;
$$  LANGUAGE plpgsql
    SECURITY DEFINER
    -- 信頼できるスキーマ、その後にpg_tempという順でsearch_pathを安全に設定します。
    SET search_path = admin, pg_temp;
 
 SELECT * FROM employee;
 
CREATE FUNCTION check_p2(name TEXT, pass TEXT)
RETURNS BOOLEAN AS '
DECLARE passed BOOLEAN;
BEGIN
        SELECT  (pwd = $2) INTO pass
        FROM    pwds
        WHERE   name = ''aaa'';
        RETURN passed;
END;
'  LANGUAGE plpgsql
    SECURITY DEFINER
    -- 信頼できるスキーマ、その後にpg_tempという順でsearch_pathを安全に設定します。
    SET search_path = admin, pg_temp;
