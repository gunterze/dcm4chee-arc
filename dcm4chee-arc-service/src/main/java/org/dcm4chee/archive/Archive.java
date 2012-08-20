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
import org.dcm4chee.archive.mpps.MPPSSCPImpl;
import org.dcm4chee.archive.mwl.MWLCFindSCPImpl;
import org.dcm4chee.archive.pix.PIXConsumer;
import org.dcm4chee.archive.query.CFindSCPImpl;
import org.dcm4chee.archive.retrieve.CGetSCPImpl;
import org.dcm4chee.archive.retrieve.CMoveSCPImpl;
import org.dcm4chee.archive.store.CStoreSCPImpl;
import org.dcm4chee.archive.util.BeanLocator;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class Archive extends DeviceService<ArchiveDevice> implements ArchiveMBean {

    static final String DEVICE_NAME = "org.dcm4chee.archive.deviceName";
    static final String JMX_NAME = "org.dcm4chee.archive.jmxName";

    static final String PATIENT = "PATIENT";
    static final String STUDY = "STUDY";
    static final String SERIES = "SERIES";
    static final String IMAGE = "IMAGE";

    private final HL7Configuration dicomConfiguration;
    private final CodeService codeService;
    private final ApplicationEntityCache aeCache;
    private final HL7ApplicationCache hl7AppCache;
    private final PIXConsumer pixConsumer;

    public Archive(HL7Configuration dicomConfiguration, String deviceName)
            throws ConfigurationException, Exception {
        this.dicomConfiguration = dicomConfiguration;
        this.codeService = BeanLocator.lookup(CodeService.class);
        this.aeCache = new ApplicationEntityCache(dicomConfiguration);
        this.hl7AppCache = new HL7ApplicationCache(dicomConfiguration);
        this.pixConsumer = new PIXConsumer(hl7AppCache);
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
                new CStoreSCPImpl(aeCache));
        services.addDicomService(
                new CFindSCPImpl(
                        UID.PatientRootQueryRetrieveInformationModelFIND,
                        aeCache, pixConsumer, PATIENT, STUDY, SERIES, IMAGE));
        services.addDicomService(
                new CFindSCPImpl(
                        UID.StudyRootQueryRetrieveInformationModelFIND,
                        aeCache, pixConsumer, STUDY, SERIES, IMAGE));
        services.addDicomService(
                new CFindSCPImpl(
                        UID.PatientStudyOnlyQueryRetrieveInformationModelFINDRetired,
                        aeCache, pixConsumer, PATIENT, STUDY));
        services.addDicomService(
                new CMoveSCPImpl(
                        UID.PatientRootQueryRetrieveInformationModelMOVE,
                        aeCache, pixConsumer, PATIENT, STUDY, SERIES, IMAGE));
        services.addDicomService(
                new CMoveSCPImpl(
                        UID.StudyRootQueryRetrieveInformationModelMOVE,
                        aeCache, pixConsumer, STUDY, SERIES, IMAGE));
        services.addDicomService(
                new CMoveSCPImpl(
                        UID.PatientStudyOnlyQueryRetrieveInformationModelMOVERetired,
                        aeCache, pixConsumer, PATIENT, STUDY));
        services.addDicomService(
                new CGetSCPImpl(
                        UID.PatientRootQueryRetrieveInformationModelGET,
                        aeCache, pixConsumer, PATIENT, STUDY, SERIES, IMAGE));
        services.addDicomService(
                new CGetSCPImpl(
                        UID.StudyRootQueryRetrieveInformationModelGET,
                        aeCache, pixConsumer, STUDY, SERIES, IMAGE));
        services.addDicomService(
                new CGetSCPImpl(
                        UID.PatientStudyOnlyQueryRetrieveInformationModelGETRetired,
                        aeCache, pixConsumer,
                        PATIENT, STUDY));
        services.addDicomService(
                new CGetSCPImpl(
                        UID.CompositeInstanceRetrieveWithoutBulkDataGET,
                        aeCache, pixConsumer, IMAGE)
                .withoutBulkData(true));
        services.addDicomService(
                new MWLCFindSCPImpl(
                        UID.ModalityWorklistInformationModelFIND,
                        aeCache, pixConsumer));
        services.addDicomService(
                new MPPSSCPImpl(aeCache));
        return services;
    }

}
