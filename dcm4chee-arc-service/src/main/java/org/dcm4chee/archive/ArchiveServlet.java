/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2012
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.archive;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.Properties;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.dcm4che.conf.api.DicomConfiguration;
import org.dcm4che.conf.api.hl7.HL7Configuration;
import org.dcm4che.conf.ldap.LdapDicomConfiguration;
import org.dcm4che.conf.ldap.audit.LdapAuditLoggerConfiguration;
import org.dcm4che.conf.ldap.audit.LdapAuditRecordRepositoryConfiguration;
import org.dcm4che.conf.ldap.hl7.LdapHL7Configuration;
import org.dcm4che.conf.prefs.PreferencesDicomConfiguration;
import org.dcm4che.conf.prefs.audit.PreferencesAuditLoggerConfiguration;
import org.dcm4che.conf.prefs.audit.PreferencesAuditRecordRepositoryConfiguration;
import org.dcm4che.conf.prefs.hl7.PreferencesHL7Configuration;
import org.dcm4che.util.SafeClose;
import org.dcm4che.util.StringUtils;
import org.dcm4chee.archive.conf.ldap.LdapArchiveConfiguration;
import org.dcm4chee.archive.conf.prefs.PreferencesArchiveConfiguration;
import org.dcm4chee.archive.dao.PatientService;
import org.dcm4chee.archive.jms.JMSService;
import org.dcm4chee.archive.mpps.dao.IANQueryService;
import org.dcm4chee.archive.mpps.dao.MPPSService;
import org.dcm4chee.archive.retrieve.dao.RetrieveService;
import org.dcm4chee.archive.stgcmt.dao.StgCmtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
public class ArchiveServlet extends HttpServlet {
    
    private static final Logger LOG = LoggerFactory.getLogger(ArchiveServlet.class);

    private ObjectInstance mbean;

    private Archive archive;

    private DicomConfiguration dicomConfig;

    private HL7Configuration hl7Config;
    
    @Resource(mappedName="java:/ConnectionFactory")
    private ConnectionFactory connFactory;

    @Resource(mappedName="java:/queue/mppsscu")
    private Queue mppsSCUQueue;

    @Resource(mappedName="java:/queue/ianscu")
    private Queue ianSCUQueue;

    @Resource(mappedName="java:/queue/stgcmtscp")
    private Queue stgcmtSCPQueue;

    @EJB
    private PatientService patientService;

    @EJB
    private StgCmtService stgCmtService;

    @EJB
    private MPPSService mppsService;

    @EJB
    private IANQueryService ianQueryService;

    @EJB
    private RetrieveService retrieveService;

    private JMSService jmsService;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            jmsService = new JMSService(connFactory);
            String ldapPropertiesURL = StringUtils.replaceSystemProperties(
                    System.getProperty(
                        "org.dcm4chee.archive.ldapPropertiesURL",
                        config.getInitParameter("ldapPropertiesURL")))
                    .replace('\\', '/');
            String deviceName = System.getProperty(
                    "org.dcm4chee.archive.deviceName",
                    config.getInitParameter("deviceName"));
            String jmxName = System.getProperty(
                    "org.dcm4chee.archive.jmxName",
                    config.getInitParameter("jmxName"));
            InputStream ldapConf = null;
            try {
                ldapConf = new URL(ldapPropertiesURL)
                    .openStream();
                Properties p = new Properties();
                p.load(ldapConf);
                LdapDicomConfiguration ldapConfig = new LdapDicomConfiguration(p);
                LdapHL7Configuration hl7Config = new LdapHL7Configuration();
                ldapConfig.addDicomConfigurationExtension(hl7Config);
                LdapArchiveConfiguration arcConfig = new LdapArchiveConfiguration();
                ldapConfig.addDicomConfigurationExtension(arcConfig);
                hl7Config.addHL7ConfigurationExtension(arcConfig);
                ldapConfig.addDicomConfigurationExtension(
                        new LdapAuditLoggerConfiguration());
                ldapConfig.addDicomConfigurationExtension(
                        new LdapAuditRecordRepositoryConfiguration());
                dicomConfig = ldapConfig;
                this.hl7Config = hl7Config;
            } catch(FileNotFoundException e) {
                LOG.info("Could not find " + ldapPropertiesURL
                        + " - use Java Preferences as Configuration Backend");
                PreferencesDicomConfiguration prefsConfig = new PreferencesDicomConfiguration();
                PreferencesHL7Configuration hl7Config = new PreferencesHL7Configuration();
                prefsConfig.addDicomConfigurationExtension(hl7Config);
                PreferencesArchiveConfiguration arcConfig = new PreferencesArchiveConfiguration();
                prefsConfig.addDicomConfigurationExtension(arcConfig);
                hl7Config.addHL7ConfigurationExtension(arcConfig);
                prefsConfig.addDicomConfigurationExtension(
                        new PreferencesAuditLoggerConfiguration());
                prefsConfig.addDicomConfigurationExtension(
                        new PreferencesAuditRecordRepositoryConfiguration());
                dicomConfig = prefsConfig;
                this.hl7Config = hl7Config;
            } finally {
                SafeClose.close(ldapConf);
            }
            archive = new Archive(dicomConfig, hl7Config,
                    dicomConfig.findDevice(deviceName),
                    patientService,
                    stgCmtService,
                    mppsService,
                    ianQueryService,
                    retrieveService,
                    jmsService,
                    mppsSCUQueue,
                    ianSCUQueue,
                    stgcmtSCPQueue);
            archive.start();
            mbean = ManagementFactory.getPlatformMBeanServer()
                    .registerMBean(archive, new ObjectName(jmxName));
        } catch (Exception e) {
            destroy();
            throw new ServletException(e);
        }
    }

    @Override
    public void destroy() {
        if (mbean != null)
            try {
                ManagementFactory.getPlatformMBeanServer()
                    .unregisterMBean(mbean.getObjectName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        if (archive != null)
            archive.stop();
        if (dicomConfig != null)
            dicomConfig.close();
        if (jmsService != null)
            jmsService.close();
    }

}
