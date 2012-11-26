DCM4CHEE Archive 4.x
====================
http://www.dcm4che.org
[Sources] (https://github.com/dcm4che/dcm4chee-arc)
[Issue Tracker] (http://www.dcm4che.org/jira/browse/ARCH)

DICOM Archive Java EE application running in JBoss AS 7.

This is a complete rewrite of DCM4CHEE Archive 2.x.

One major improvement to 2.x is the use of LDAP as central configuration,
compliant to the DICOM Application Configuration Management Profile.

This first Alpha version supports DICOM and HL7 Services required for
compliance with IHE Radiology Integration Profiles Scheduled Workflow (SWF),
Patient Information Reconciliation (PIR) and the new Imaging Object Change
Management (IOCM) Profile, including the new Multiple Identity Resolution
Option for these Profiles.

There are still major gaps compared to the functionallity of DCM4CHEE Archive 2.x:

- no Web-interface
- no WADO Service
- no compression/decompression
- no auto-routing
- no auto-switch of storage filesystems
- no HSM support
- no import of HL7 ORM messages in DICOM Modality Worklist

In long term, 4.x will provide the functionality of 2.x, and there will
be migration tools to upgrade existing installations of 2.x to 4.x.

Build
-----
After installation of [Maven 3](http://maven.apache.org):

    > mvn install -D db={db2|firebird|h2|mysql|oracle|psql|sqlserver}

Installation
------------
See INSTALL.md.

License
-------
* [Mozilla Public License Version 1.1](http://www.mozilla.org/MPL/1.1/)
