DCM4CHEE Archive 4.x
====================
Sources: https://github.com/dcm4che/dcm4chee-arc  
Binaries: https://sourceforge.net/projects/dcm4che/files/dcm4chee-arc4  
Issue Tracker: http://www.dcm4che.org/jira/browse/ARCH  

DICOM Archive Java EE application running in JBoss AS 7.

This is a complete rewrite of [DCM4CHEE Archive 2.x](http://www.dcm4che.org/confluence/display/ee2/Home).

One major improvement to 2.x is the use of LDAP as central configuration,
compliant to the DICOM Application Configuration Management Profile,
specified in [DICOM 2011, Part 15][1], Annex H.

[1]: ftp://medical.nema.org/medical/dicom/2011/11_15pu.pdf

This first Alpha version supports DICOM and HL7 Services required for
compliance with IHE Radiology Workflow Integration Profiles:

- [Scheduled Workflow (SWF)][2]
- [Patient Information Reconciliation (PIR)][3]
- [Imaging Object Change Management (IOCM)][4]

for IHE Actor Image Manager/Archive, including the new 

- [Multiple Identity Resolution Option][5]

for these Profiles.

[2]: http://wiki.ihe.net/index.php?title=Scheduled_Workflow
[3]: http://wiki.ihe.net/index.php?title=Patient_Information_Reconciliation
[4]: http://www.ihe.net/Technical_Framework/upload/IHE_RAD_Suppl_IOCM_Rev1-1_TI_2011-05-17.pdf
[5]: http://www.ihe.net/Technical_Framework/upload/IHE_RAD_Suppl_MIMA.pdf

There are still major gaps compared to the functionality of DCM4CHEE Archive 2.x:

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
See [INSTALL.md](https://github.com/OpenRadStack/dcm4chee-arc/blob/master/INSTALL.md).

License
-------
* [Mozilla Public License Version 1.1](http://www.mozilla.org/MPL/1.1/)
