### Alfresco Trashcan Cleaner Module
This module adds a scheduled job that will empty your Alfresco trashcan according to configuration. The following properties can be configured in alfresco-global.properties:

~~~
# cron schedule for the Trashcan Cleaner job
# to disable, set it to something like trashcan-cleaner.cron=* * * * * ? 2099
trashcan-cleaner.cron=0 30 2 * * ?

# the period for which trashcan items are kept (in java.time.Duration format)
# default is 1 month
trashcan-cleaner.keepPeriod=P1M

# how many trashcan items to delete per job run
trashcan-cleaner.deleteBatchCount=1000
~~~

In the above configuration the scheduled process will clean all deleted items older than one day to a maximum of 1000 (each execution) each hour at the middle of the hour (30 minutes).

To enable debug logging:

~~~
log4j.logger.org.alfresco.trashcan=debug
~~~
### Building and testing
The project can be built and tested by running Maven command:
~~~
mvn clean install
~~~
The tests require a database connection (PostgreSQL by default), see [pom.xml](pom.xml) for details.

### Artifacts
The artifacts can be obtained by:
* downloading from [Alfresco repository](https://artifacts.alfresco.com/nexus/content/groups/public)
* getting as Maven dependency by adding the dependency to your pom file:
~~~
<dependency>
  <groupId>org.alfresco</groupId>
  <artifactId>alfresco-trashcan-cleaner</artifactId>
  <version>version</version>
</dependency>
~~~
and Alfresco repository:
~~~
<repository>
  <id>alfresco-maven-repo</id>
  <url>https://artifacts.alfresco.com/nexus/content/groups/public</url>
</repository>
~~~
The SNAPSHOT version of the artifact is **never** published.

### Contributing guide
Please use [this guide](CONTRIBUTING.md) to make a contribution to the project.