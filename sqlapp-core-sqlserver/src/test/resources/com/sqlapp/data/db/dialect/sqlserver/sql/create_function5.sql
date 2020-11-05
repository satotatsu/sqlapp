CREATE FUNCTION ReadEventLog (@logname NVARCHAR(100))
RETURNS TABLE
(logTime datetime
,Message nvarchar(4000)
,Category nvarchar(4000)
,InstanceId bigint)
AS
EXTERNAL NAME tvfEventLog.TabularEventLog.InitMethod