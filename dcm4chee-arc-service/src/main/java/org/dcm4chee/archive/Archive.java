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

import javax.jms.ConnectionFactory;
import javax.jms.Queue;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.api.hl7.HL7ApplicationCache;
import org.dcm4che.conf.api.hl7.HL7Configuration;
import org.dcm4che.data.UID;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.DeviceService;
import org.dcm4che.net.service.DicomServiceRegistry;
import org.dcm4chee.archive.conf.ArchiveApplicationEntity;
import org.dcm4chee.archive.conf.ArchiveDevice;
import org.dcm4chee.archive.conf.RejectionNote;
import org.dcm4chee.archive.dao.CodeService;
import org.dcm4chee.archive.entity.Code;
import org.dcm4chee.archive.mpps.IANSCU;
import org.dcm4chee.archive.mpps.MPPSSCP;
import org.dcm4chee.archive.mpps.MPPSSCU;
import org.dcm4chee.archive.mwl.MWLCFindSCP;
import org.dcm4chee.archive.pix.PIXConsumer;
import org.dcm4chee.archive.query.CFindSCP;
import org.dcm4chee.archive.retrieve.CGetSCP;
import org.dcm4chee.archive.retrieve.CMoveSCP;
import org.dcm4chee.archive.stgcmt.StgCmtSCP;
import org.dcm4chee.archive.store.CStoreSCP;
import org.dcm4chee.archive.util.BeanLocator;
import org.dcm4chee.archive.util.JMSService;

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
    private final ApplicationEntityCache aeCache;
    private final HL7ApplicationCache hl7AppCache;
    private final PIXConsumer pixConsumer;
    private final JMSService jmsService;
    private final MPPSSCU mppsSCU;
    private final IANSCU ianSCU;
    private final StgCmtSCP stgCmtSCP;

    public Archive(HL7Configuration dicomConfiguration, String deviceName,
            ConnectionFactory connFactory,
            Queue mppsSCUQueue,
            Queue ianSCUQueue,
            Queue stgcmtSCPQueue)
            throws ConfigurationException, Exception {
        this.dicomConfiguration = dicomConfiguration;
        this.codeService = BeanLocator.lookup(CodeService.class);
        this.aeCache = new ApplicationEntityCache(dicomConfiguration);
        this.hl7AppCache = new HL7ApplicationCache(dicomConfiguration);
        this.pixConsumer = new PIXConsumer(hl7AppCache);
        this.jmsService = new JMSService(connFactory);
        this.mppsSCU = new MPPSSCU(aeCache, jmsService, mppsSCUQueue);
        this.ianSCU = new IANSCU(aeCache, jmsService, ianSCUQueue);
        this.stgCmtSCP = new StgCmtSCP(aeCache, jmsService, stgcmtSCPQueue);
        init((ArchiveDevice) dicomConfiguration.findDevice(deviceName));
        setConfigurationStaleTimeout();
        loadRejectionNoteCodes();
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
        services.addDicomService(
                new CStoreSCP(aeCache, ianSCU));
        services.addDicomService(
                new CFindSCP(
                        UID.PatientRootQueryRetrieveInformationModelFIND,
                        aeCache, pixConsumer, PATIENT, STUDY, SERIES, IMAGE));
        services.addDicomService(
                new CFindSCP(
                        UID.StudyRootQueryRetrieveInformationModelFIND,
                        aeCache, pixConsumer, STUDY, SERIES, IMAGE));
        services.addDicomService(
                new CFindSCP(
                        UID.PatientStudyOnlyQueryRetrieveInformationModelFINDRetired,
                        aeCache, pixConsumer, PATIENT, STUDY));
        services.addDicomService(
                new CMoveSCP(
                        UID.PatientRootQueryRetrieveInformationModelMOVE,
                        aeCache, pixConsumer, PATIENT, STUDY, SERIES, IMAGE));
        services.addDicomService(
                new CMoveSCP(
                        UID.StudyRootQueryRetrieveInformationModelMOVE,
                        aeCache, pixConsumer, STUDY, SERIES, IMAGE));
        services.addDicomService(
                new CMoveSCP(
                        UID.PatientStudyOnlyQueryRetrieveInformationModelMOVERetired,
                        aeCache, pixConsumer, PATIENT, STUDY));
        services.addDicomService(
                new CGetSCP(
                        UID.PatientRootQueryRetrieveInformationModelGET,
                        aeCache, pixConsumer, PATIENT, STUDY, SERIES, IMAGE));
        services.addDicomService(
                new CGetSCP(
                        UID.StudyRootQueryRetrieveInformationModelGET,
                        aeCache, pixConsumer, STUDY, SERIES, IMAGE));
        services.addDicomService(
                new CGetSCP(
                        UID.PatientStudyOnlyQueryRetrieveInformationModelGETRetired,
                        aeCache, pixConsumer,
                        PATIENT, STUDY));
        services.addDicomService(
                new CGetSCP(
                        UID.CompositeInstanceRetrieveWithoutBulkDataGET,
                        aeCache, pixConsumer, IMAGE)
                .withoutBulkData(true));
        services.addDicomService(
                new MWLCFindSCP(
                        UID.ModalityWorklistInformationModelFIND,
                        aeCache, pixConsumer));
        services.addDicomService(
                new MPPSSCP(aeCache, mppsSCU, ianSCU));
        services.addDicomService(stgCmtSCP);
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
