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
 * Portions created by the Initial Developer are Copyright (C) 2011
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
package org.dcm4chee.archive.conf.provider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.api.DicomConfiguration;
import org.dcm4che.conf.api.IApplicationEntityCache;
import org.dcm4che.conf.api.hl7.HL7ApplicationCache;
import org.dcm4che.conf.api.hl7.HL7Configuration;
import org.dcm4che.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che.conf.ldap.LdapDicomConfiguration;
import org.dcm4che.conf.ldap.audit.LdapAuditLoggerConfiguration;
import org.dcm4che.conf.ldap.audit.LdapAuditRecordRepositoryConfiguration;
import org.dcm4che.conf.ldap.hl7.LdapHL7Configuration;
import org.dcm4che.conf.ldap.imageio.LdapImageReaderConfiguration;
import org.dcm4che.conf.ldap.imageio.LdapImageWriterConfiguration;
import org.dcm4che.conf.prefs.PreferencesDicomConfiguration;
import org.dcm4che.conf.prefs.audit.PreferencesAuditLoggerConfiguration;
import org.dcm4che.conf.prefs.audit.PreferencesAuditRecordRepositoryConfiguration;
import org.dcm4che.conf.prefs.hl7.PreferencesHL7Configuration;
import org.dcm4che.conf.prefs.imageio.PreferencesImageReaderConfiguration;
import org.dcm4che.conf.prefs.imageio.PreferencesImageWriterConfiguration;
import org.dcm4che.util.StreamUtils;
import org.dcm4chee.archive.conf.ldap.LdapArchiveConfiguration;
import org.dcm4chee.archive.conf.prefs.PreferencesArchiveConfiguration;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class ArchiveConfigurationFactory {

    private static final String LDAP_PROPERTIES_URL_PROPERTY =
            "org.dcm4chee.archive.ldapPropertiesURL";

    @Produces @ApplicationScoped
    public DicomConfiguration createDicomConfiguration()
            throws ConfigurationException {
        Properties ldapEnv = ldapEnv();
        return ldapEnv != null
                ? configure(new LdapDicomConfiguration(ldapEnv))
                : configure(new PreferencesDicomConfiguration());
        }

    public void disposeDicomConfiguration(@Disposes DicomConfiguration conf) {
        conf.close();
    }

    @Produces @ApplicationScoped
    public IApplicationEntityCache getApplicationEntityCache(DicomConfiguration conf) {
        return new ApplicationEntityCache(conf);
    }

    @Produces @ApplicationScoped
    public IHL7ApplicationCache getHL7ApplicationCache(DicomConfiguration conf) {
        return new HL7ApplicationCache(
                conf.getDicomConfigurationExtension(HL7Configuration.class));
    }

    private Properties ldapEnv() throws ConfigurationException {
        String ldapPropertiesURL = System.getProperty(
                LDAP_PROPERTIES_URL_PROPERTY);
        if (ldapPropertiesURL == null) {
            return null;
        }
        Properties p = new Properties();
        try ( InputStream in = StreamUtils.openFileOrURL(ldapPropertiesURL); ) {
            p.load(in);
        } catch (IOException e) {
            throw new ConfigurationException(e);
        }
        return p;
    }

    private DicomConfiguration configure(LdapDicomConfiguration ldapConfig) {
        LdapHL7Configuration hl7Config = new LdapHL7Configuration();
        ldapConfig.addDicomConfigurationExtension(hl7Config);
        LdapArchiveConfiguration arcConfig = new LdapArchiveConfiguration();
        ldapConfig.addDicomConfigurationExtension(arcConfig);
        hl7Config.addHL7ConfigurationExtension(arcConfig);
        ldapConfig.addDicomConfigurationExtension(
                new LdapAuditLoggerConfiguration());
        ldapConfig.addDicomConfigurationExtension(
                new LdapAuditRecordRepositoryConfiguration());
        ldapConfig.addDicomConfigurationExtension(
                new LdapImageReaderConfiguration());
        ldapConfig.addDicomConfigurationExtension(
                new LdapImageWriterConfiguration());
        return ldapConfig;
    }

    private DicomConfiguration configure(PreferencesDicomConfiguration prefsConfig) {
        PreferencesHL7Configuration hl7Config = new PreferencesHL7Configuration();
        prefsConfig.addDicomConfigurationExtension(hl7Config);
        PreferencesArchiveConfiguration arcConfig = new PreferencesArchiveConfiguration();
        prefsConfig.addDicomConfigurationExtension(arcConfig);
        hl7Config.addHL7ConfigurationExtension(arcConfig);
        prefsConfig.addDicomConfigurationExtension(
                new PreferencesAuditLoggerConfiguration());
        prefsConfig.addDicomConfigurationExtension(
                new PreferencesAuditRecordRepositoryConfiguration());
        prefsConfig.addDicomConfigurationExtension(
                new PreferencesImageReaderConfiguration());
        prefsConfig.addDicomConfigurationExtension(
                new PreferencesImageWriterConfiguration());
        return prefsConfig;
    }

}
