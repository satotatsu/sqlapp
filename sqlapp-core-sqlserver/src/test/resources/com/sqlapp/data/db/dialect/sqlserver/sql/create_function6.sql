CREATE AGGREGATE Concatenate (@input NVARCHAR(4000))
RETURNS NVARCHAR(4000)
AS
EXTERNAL NAME StringUtilities.Microsoft.Samples.SqlServer.Concatenate