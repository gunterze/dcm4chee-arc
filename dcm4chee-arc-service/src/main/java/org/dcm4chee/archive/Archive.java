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

import java.lang.management.ManagementFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.DependsOn;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.net.ssl.KeyManager;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.hl7.HL7ApplicationCache;
import org.dcm4che.conf.api.hl7.HL7Configuration;
import org.dcm4che.data.UID;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Device;
import org.dcm4che.net.DeviceService;
import org.dcm4che.net.SSLManagerFactory;
import org.dcm4che.net.service.DicomServiceRegistry;
import org.dcm4chee.archive.conf.ArchiveApplicationEntity;
import org.dcm4chee.archive.conf.ArchiveDevice;
import org.dcm4chee.archive.conf.RejectionNote;
import org.dcm4chee.archive.dao.CodeService;
import org.dcm4chee.archive.entity.Code;
import org.dcm4chee.archive.pix.PIXConsumer;
import org.dcm4chee.archive.query.CFindSCPImpl;
import org.dcm4chee.archive.query.MWLCFindSCPImpl;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@Singleton
@LocalBean
@DependsOn("DicomConfiguration")
@Startup
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class Archive extends DeviceService<ArchiveDevice> implements ArchiveMBean {

    static final String DEVICE_NAME = "org.dcm4chee.archive.deviceName";
    static final String JMX_NAME = "org.dcm4chee.archive.jmxName";
    static final String KS_TYPE = "org.dcm4chee.archive.keyStoreType";
    static final String KS_URL = "org.dcm4chee.archive.keyStoreURL";
    static final String KS_PASSWORD = "org.dcm4chee.archive.storePassword";
    static final String KEY_PASSWORD = "org.dcm4chee.archive.keyPassword";

    static final String PATIENT = "PATIENT";
    static final String STUDY = "STUDY";
    static final String SERIES = "SERIES";
    static final String IMAGE = "IMAGE";

    @EJB(name="DicomConfiguration")
    HL7Configuration dicomConfiguration;

    @EJB
    CodeService codeService;

    private ObjectInstance mbean;
    private ApplicationEntityCache aeCache;
    private HL7ApplicationCache hl7AppCache;
    private PIXConsumer pixConsumer;

    @PostConstruct
    void init() {
        try {
            aeCache = new ApplicationEntityCache(dicomConfiguration);
            hl7AppCache = new HL7ApplicationCache(dicomConfiguration);
            pixConsumer = new PIXConsumer(hl7AppCache);
            super.init((ArchiveDevice) dicomConfiguration.findDevice(
                    System.getProperty(DEVICE_NAME, "dcm4chee-arc")));
            setConfigurationStaleTimeout();
            loadRejectionNoteCodes();
            mbean = ManagementFactory.getPlatformMBeanServer()
                    .registerMBean(this, new ObjectName(
                            System.getProperty(JMX_NAME, "dcm4chee:service=dcm4chee-arc")));
            start();
        } catch (Exception e) {
            destroy();
            throw new RuntimeException(e);
        }
        
    }

    @PreDestroy
    void destroy() {
        stop();
        if (mbean != null)
            try {
                ManagementFactory.getPlatformMBeanServer()
                    .unregisterMBean(mbean.getObjectName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        mbean = null;
        device = null;
    }

    @Override
    public Device unwrapDevice() {
        return device;
    }

    @Override
    public void reloadConfiguration() throws Exception {
        device.reconfigure(dicomConfiguration.findDevice(device.getDeviceName()));
        setConfigurationStaleTimeout();
        loadRejectionNoteCodes();
    }

    protected KeyManager keyManager() throws Exception {
        String url = System.getProperty(KS_URL, "resource:dcm4chee-arc-key.jks");
        String kstype = System.getProperty(KS_TYPE, "JKS");
        String kspw = System.getProperty(KS_PASSWORD, "secret");
        String keypw = System.getProperty(KEY_PASSWORD, kspw);
        return SSLManagerFactory.createKeyManager(kstype, url, kspw, keypw);
    }

    private void setConfigurationStaleTimeout() {
        int staleTimeout = device.getConfigurationStaleTimeout();
        aeCache.setStaleTimeout(staleTimeout);
        hl7AppCache.setStaleTimeout(staleTimeout);
    }

    private void loadRejectionNoteCodes() {
        for (ApplicationEntity ae : device.getApplicationEntities()) {
            for (RejectionNote rj : ((ArchiveApplicationEntity) ae).getRejectionNotes()) {
                if (!(rj.getCode() instanceof Code))
                    rj.setCode(codeService.findOrCreate(new Code(rj.getCode())));
            }
        }
    }

    @Override
    protected DicomServiceRegistry serviceRegistry() {
        DicomServiceRegistry services = super.serviceRegistry();
        services.addDicomService(
                new CFindSCPImpl(
                        UID.PatientRootQueryRetrieveInformationModelFIND,
                        aeCache, pixConsumer,
                        PATIENT, STUDY, SERIES, IMAGE));
        services.addDicomService(
                new CFindSCPImpl(
                        UID.StudyRootQueryRetrieveInformationModelFIND,
                        aeCache, pixConsumer,
                        STUDY, SERIES, IMAGE));
        services.addDicomService(
                new CFindSCPImpl(
                        UID.PatientStudyOnlyQueryRetrieveInformationModelFINDRetired,
                        aeCache, pixConsumer,
                        PATIENT, STUDY));
        services.addDicomService(
                new MWLCFindSCPImpl(
                        UID.ModalityWorklistInformationModelFIND,
                        aeCache, pixConsumer));
       return services;
    }

}
