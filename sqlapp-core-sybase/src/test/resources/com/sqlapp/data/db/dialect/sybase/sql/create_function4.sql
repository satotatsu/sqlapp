CREATE FUNCTION dbo.len_s (@str NVARCHAR(4000))
RETURNS BIGINT
AS
EXTERNAL NAME SurrogateStringFunction.Microsoft.Samples.SqlServer.SurrogateStringFunction.LenS