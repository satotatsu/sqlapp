CREATE FUNCTION ABS_JAVA (A INTEGER)
RETURNS INTEGER
SPECIFIC ABS_JAVA_INT
LANGUAGE JAVA
NOT DETERMINISTIC
NO SQL
CALLED ON NULL INPUT
EXTERNAL NAME 'CLASSPATH:java.lang.Math.abs'