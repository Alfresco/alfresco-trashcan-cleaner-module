This library adds a scheduled job that will empty your Alfresco trashcan according to configuration. The following properties can be configured in alfresco-global.properties:

trashcan.cron=0 30 * * * ?
trashcan.daysToKeep=P1D
trashcan.deleteBatchCount=1000

In the above configuration the scheduled process will clean all deleted items older than one day to a maximum of 1000 (each execution) each hour at the middle of the hour (30 minutes).

To enable debug logging:

log4j.logger.org.alfresco.trashcan=debug