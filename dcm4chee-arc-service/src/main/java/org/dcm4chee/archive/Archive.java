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

import javax.jms.Queue;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.api.hl7.HL7ApplicationCache;
import org.dcm4che.conf.api.hl7.HL7Configuration;
import org.dcm4che.data.UID;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.DeviceService;
import org.dcm4che.net.hl7.HL7MessageListener;
import org.dcm4che.net.hl7.service.HL7ServiceRegistry;
import org.dcm4che.net.service.DicomServiceRegistry;
import org.dcm4chee.archive.conf.ArchiveApplicationEntity;
import org.dcm4chee.archive.conf.ArchiveDevice;
import org.dcm4chee.archive.conf.RejectionNote;
import org.dcm4chee.archive.dao.CodeService;
import org.dcm4chee.archive.dao.PatientService;
import org.dcm4chee.archive.entity.Code;
import org.dcm4chee.archive.hl7.PatientUpdateService;
import org.dcm4chee.archive.jms.JMSService;
import org.dcm4chee.archive.mpps.IANSCU;
import org.dcm4chee.archive.mpps.MPPSSCP;
import org.dcm4chee.archive.mpps.MPPSSCU;
import org.dcm4chee.archive.mpps.dao.MPPSService;
import org.dcm4chee.archive.mwl.MWLCFindSCP;
import org.dcm4chee.archive.pix.PIXConsumer;
import org.dcm4chee.archive.query.CFindSCP;
import org.dcm4chee.archive.retrieve.CGetSCP;
import org.dcm4chee.archive.retrieve.CMoveSCP;
import org.dcm4chee.archive.retrieve.dao.RetrieveService;
import org.dcm4chee.archive.stgcmt.StgCmtSCP;
import org.dcm4chee.archive.stgcmt.dao.StgCmtService;
import org.dcm4chee.archive.store.CStoreSCP;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class Archive extends DeviceService<ArchiveDevice> implements ArchiveMBean {

    static final String PATIENT = "PATIENT";
    static final String STUDY = "STUDY";
    static final String SERIES = "SERIES";
    static final String IMAGE = "IMAGE";

    private final HL7Configuration dicomConfiguration;
    private final CodeService codeService;
    private final CStoreSCP storeSCP;
    private final StgCmtSCP stgCmtSCP;
    private final MPPSSCP mppsSCP;
    private final CFindSCP patientRootFindSCP;
    private final CFindSCP studyRootFindSCP;
    private final CFindSCP patientStudyOnlyFindSCP;
    private final CMoveSCP patientRootMoveSCP;
    private final CMoveSCP studyRootMoveSCP;
    private final CMoveSCP patientStudyOnlyMoveSCP;
    private final CGetSCP patientRootGetSCP;
    private final CGetSCP studyRootGetSCP;
    private final CGetSCP patientStudyOnlyGetSCP;
    private final CGetSCP withoutBulkDataGetSCP;
    private final MWLCFindSCP mwlFindSCP;
    private final ApplicationEntityCache aeCache;
    private final HL7ApplicationCache hl7AppCache;
    private final PIXConsumer pixConsumer;
    private final JMSService jmsService;
    private final MPPSSCU mppsSCU;
    private final IANSCU ianSCU;

    public Archive(HL7Configuration dicomConfiguration, String deviceName,
            CodeService codeService,
            PatientService patientService,
            StgCmtService stgCmtService,
            MPPSService mppsService,
            RetrieveService retrieveService,
            JMSService jmsService,
            Queue mppsSCUQueue,
            Queue ianSCUQueue,
            Queue stgcmtSCPQueue)
            throws ConfigurationException, Exception {
        this.dicomConfiguration = dicomConfiguration;
        this.codeService = codeService;
        this.aeCache = new ApplicationEntityCache(dicomConfiguration);
        this.hl7AppCache = new HL7ApplicationCache(dicomConfiguration);
        this.pixConsumer = new PIXConsumer(hl7AppCache);
        this.jmsService = jmsService;
        this.mppsSCU = new MPPSSCU(aeCache, jmsService, mppsSCUQueue);
        this.ianSCU = new IANSCU(aeCache, jmsService, ianSCUQueue);
        this.storeSCP = new CStoreSCP(aeCache, ianSCU);
        this.stgCmtSCP = new StgCmtSCP(aeCache, stgCmtService,
                jmsService, stgcmtSCPQueue);
        this.mppsSCP = new MPPSSCP(aeCache, mppsSCU, ianSCU, mppsService);
        this.patientRootFindSCP = new CFindSCP(
                UID.PatientRootQueryRetrieveInformationModelFIND,
                aeCache, pixConsumer, PATIENT, STUDY, SERIES, IMAGE);
        this.studyRootFindSCP = new CFindSCP(
                UID.StudyRootQueryRetrieveInformationModelFIND,
                aeCache, pixConsumer, STUDY, SERIES, IMAGE);
        this.patientStudyOnlyFindSCP = new CFindSCP(
                UID.PatientStudyOnlyQueryRetrieveInformationModelFINDRetired,
                aeCache, pixConsumer, PATIENT, STUDY);
        this.patientRootMoveSCP = new CMoveSCP(
                UID.PatientRootQueryRetrieveInformationModelMOVE,
                aeCache, pixConsumer, retrieveService, PATIENT, STUDY, SERIES, IMAGE);
        this.studyRootMoveSCP = new CMoveSCP(
                UID.StudyRootQueryRetrieveInformationModelMOVE,
                aeCache, pixConsumer, retrieveService, STUDY, SERIES, IMAGE);
        this.patientStudyOnlyMoveSCP = new CMoveSCP(
                UID.PatientStudyOnlyQueryRetrieveInformationModelMOVERetired,
                aeCache, pixConsumer, retrieveService, PATIENT, STUDY);
        this.patientRootGetSCP = new CGetSCP(
                UID.PatientRootQueryRetrieveInformationModelGET,
                aeCache, pixConsumer, retrieveService, PATIENT, STUDY, SERIES, IMAGE);
        this.studyRootGetSCP = new CGetSCP(
                UID.StudyRootQueryRetrieveInformationModelGET,
                aeCache, pixConsumer, retrieveService, STUDY, SERIES, IMAGE);
        this.patientStudyOnlyGetSCP = new CGetSCP(
                UID.PatientStudyOnlyQueryRetrieveInformationModelGETRetired,
                aeCache, pixConsumer, retrieveService, PATIENT, STUDY);
        this.withoutBulkDataGetSCP = new CGetSCP(
                UID.CompositeInstanceRetrieveWithoutBulkDataGET,
                aeCache, pixConsumer, retrieveService, IMAGE)
                .withoutBulkData(true);
        this.mwlFindSCP = new MWLCFindSCP(
                UID.ModalityWorklistInformationModelFIND,
                aeCache, pixConsumer);
        init((ArchiveDevice) dicomConfiguration.findDevice(deviceName));
        device.setHL7MessageListener(hl7ServiceRegistry(patientService));
        setConfigurationStaleTimeout();
        loadRejectionNoteCodes();
   }

    private HL7MessageListener hl7ServiceRegistry(PatientService patientService) {
        HL7ServiceRegistry serviceRegistry = new HL7ServiceRegistry();
        serviceRegistry.addHL7Service(new PatientUpdateService(patientService,
                "ADT^A02", "ADT^A03", "ADT^A06", "ADT^A07", "ADT^A08", "ADT^A40"));
        return serviceRegistry ;
    }

    @Override
    public void reloadConfiguration() throws Exception {
        device.reconfigure(dicomConfiguration.findDevice(device.getDeviceName()));
        setConfigurationStaleTimeout();
        loadRejectionNoteCodes();
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
        services.addDicomService(storeSCP);
        services.addDicomService(stgCmtSCP);
        services.addDicomService(mwlFindSCP);
        services.addDicomService(mppsSCP);
        services.addDicomService(patientRootFindSCP);
        services.addDicomService(studyRootFindSCP);
        services.addDicomService(patientStudyOnlyFindSCP);
        services.addDicomService(patientRootMoveSCP);
        services.addDicomService(studyRootMoveSCP);
        services.addDicomService(patientStudyOnlyMoveSCP);
        services.addDicomService(patientRootGetSCP);
        services.addDicomService(studyRootGetSCP);
        services.addDicomService(patientStudyOnlyGetSCP);
        services.addDicomService(withoutBulkDataGetSCP);
        return services;
    }

    @Override
    public void start() throws Exception {
        super.start();
        mppsSCU.start(device);
        ianSCU.start(device);
        stgCmtSCP.start(device);
        jmsService.start();
    }

    @Override
    public void stop() {
        super.stop();
        jmsService.stop();
        mppsSCU.stop();
        ianSCU.stop();
        stgCmtSCP.stop();
    }

}
