dcm4chee-arc
============

DICOM archive J2EE application for JBoss AS7.

Build
=====

Requirements
------------

* JDK 6 (or newer)
* maven [http://maven.apache.org]
* dcm4che [https://github.com/dcm4che/dcm4che]
* schema-export [https://github.com/dcm4che/schema-export]
* querydsl-jboss-modules [https://github.com/dcm4che/querydsl-jboss-modules]

Build Instructions
------------------

### Quick and dirty

To compile dcm4chee-arc, run maven with the following parameters in the source root directory:
`mvn [clean] install -D db={db2|firebird|h2|mssql|mysql|oracle|psql} [-D ldap={slapd|opends|apacheds}] [-D ds=java:/PacsDS]`

### Detailed

#### DicomConfiguration

dcm4chee-arc supports 2 ways for storing configuration data: i) XML Preferences and ii) LDAP data.

Maven parameters for specific configuration backends:

Preferences: `-P prefs` (default)

LDAP: `[-P ldap] -D ldap={slapd|opends|apacheds}`

Note: The LDAP server of the target system must be specified as a maven parameter using one of the three supported values. This will setup a default configuration for the particular system, which can be changed later on by editing the ldap configuration file.

##### Database

Supported database backends for storage of DICOM object related data are IBM DB2, Firebird, H2, Microsoft SQL, MySQL, Oracle and PostgreSQL. 

Maven parameters for specific database backends: `-D db={db2|firebird|h2|mssql|mysql|oracle|psql}`

#### JBoss AS7 Datasource

The default datasource expected to be configured in the JBoss AS7 configuration is `java:/PacsDS`.

To change to a different datasource, use: `-D ds=java:/NewDatasource`

Install Instructions
--------------------

### Configure DICOM Configuration

#### Using Preferences Backend
Use the `xml2prefs` tool from the dcm4che library to import a sample configuration from file _dcm4chee-arc-conf/src/main/config/prefs/sample-config.xml_

#### Using LDAP Backend

1. Import ldif schemas
    * dcm4che/dcm4che-conf/dcm4che-conf-ldap/src/main/config/{slapd|opends|apacheds}/dicom.ldif
    * dcm4che/dcm4che-conf/dcm4che-conf-ldap/src/main/config/{slapd|opends|apacheds}/dcm4che.ldif
    * dcm4che/dcm4che-conf/dcm4che-conf-ldap-hl7/src/main/config/{slapd|opends|apacheds}/dcm4che-hl7.ldif
    * dcm4chee-arc/dcm4chee-arc-conf/src/main/config/ldap/{slapd|opends|apacheds}/dcm4chee-archive.ldif
2. Import sample configuration 
    * dcm4chee-arc/dcm4chee-arc-conf/src/main/config/ldap/init.ldif
    * dcm4chee-arc/dcm4chee-arc-conf/src/main/config/ldap/init-config.ldif
    * dcm4chee-arc/dcm4chee-arc-conf/src/main/config/ldap/sample-config.ldif

### Configure database

1. Create a user (default _pacs/pacs_) and database (default _pacsdb_) for the archive
2. Create tables by importing the `create*.ddl` script from the _dcm4chee-arc/dcm4chee-arc-entity/target/_ directory (Oracle example: `SQL> @create-oracle.ddl`).

### Configure JBoss AS7

1. Start a JBoss AS7 intance with the `standalone-full.xml` profile (e.g. `jboss-as-7.1.1.Final $ ./bin/standalone.sh -c standalone-full.xml`).
2. Add JMS queues
    * Start JBoss command line interface (e.g. `jboss-as-7.1.1.Final $ ./bin/jboss-cli.sh`) and execute `connect` to connect to the JBoss instance.
    * execute:
        * `jms-queue add --queue-address=ianscuQueue --entries=queue/ianscu`
        * `jms-queue add --queue-address=stgcmtscpQueue --entries=queue/stgcmtscp`
        * `jms-queue add --queue-address=mppsscuQueue --entries=queue/mppsscu`
3. Setup datasource
    * Connect to `jboss-cli.sh` as above
    * execute: 
        * `data-source add --name=PacsDS --jndi-name=java:/PacsDS --connection-url=<jdbc url> --driver-name=<driver name> --user-name=pacs --password=pacs`
4. Deploy archive
    * Connect to `jboss-cli.sh` as above
    * execute: 
        * `deploy <path to dcm4chee-arc>/dcm4chee-arc-service/target/dcm4chee-arc-4.1.0-SNAPSHOT-oracle.war`
