CREATE PROCEDURE "new_customer" (firstname VARCHAR(50), lastname VARCHAR(50))
SPECIFIC NEW_CUSTOMER_10030
LANGUAGE SQL
NOT DETERMINISTIC
MODIFIES SQL DATA
NEW SAVEPOINT LEVEL
INSERT INTO CUSTOMERS VALUES(DEFAULT,FIRSTNAME,LASTNAME,CURRENT_TIMESTAMP)