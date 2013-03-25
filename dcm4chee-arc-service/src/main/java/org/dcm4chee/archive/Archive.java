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

import java.util.Calendar;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.MessageListener;
import javax.jms.Queue;

import org.dcm4che.audit.AuditMessage;
import org.dcm4che.audit.AuditMessages;
import org.dcm4che.audit.AuditMessages.EventActionCode;
import org.dcm4che.audit.AuditMessages.EventID;
import org.dcm4che.audit.AuditMessages.EventOutcomeIndicator;
import org.dcm4che.audit.AuditMessages.EventTypeCode;
import org.dcm4che.audit.AuditMessages.RoleIDCode;
import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.api.DicomConfiguration;
import org.dcm4che.conf.api.hl7.HL7ApplicationCache;
import org.dcm4che.conf.api.hl7.HL7Configuration;
import org.dcm4che.data.Attributes;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Device;
import org.dcm4che.net.DeviceService;
import org.dcm4che.net.audit.AuditLogger;
import org.dcm4che.net.hl7.HL7Application;
import org.dcm4che.net.hl7.HL7DeviceExtension;
import org.dcm4che.net.hl7.HL7MessageListener;
import org.dcm4che.net.hl7.service.HL7ServiceRegistry;
import org.dcm4che.net.service.BasicCEchoSCP;
import org.dcm4che.net.service.DicomServiceRegistry;
import org.dcm4chee.archive.common.IDWithIssuer;
import org.dcm4chee.archive.conf.ArchiveDeviceExtension;
import org.dcm4chee.archive.hl7.PatientUpdateService;
import org.dcm4chee.archive.jms.JMSService;
import org.dcm4chee.archive.jms.JMSService.MessageCreator;
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
import org.dcm4chee.archive.wado.WadoAttributesCache;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@Dependent
public class Archive extends DeviceService implements ArchiveMBean {

    private static Archive instance;

    @Inject
    private JMSService jmsService;

    private DicomConfiguration dicomConfiguration;
    private ApplicationEntityCache aeCache;
    private HL7ApplicationCache hl7AppCache;

    @Inject
    private PIXConsumer pixConsumer;

    @Inject
    private CStoreSCP storeSCP;

    @Inject
    private StgCmtSCP stgCmtSCP;

    @Inject
    private MPPSSCP mppsSCP;

    @Inject
    private MPPSSCU mppsSCU;

    @Inject
    private IANSCU ianSCU;

    @Inject
    private CFindSCP.PatientRoot patientRootFindSCP;

    @Inject
    private CFindSCP.StudyRoot studyRootFindSCP;

    @Inject
    private CFindSCP.PatientStudyOnly patientStudyOnlyFindSCP;

    @Inject
    private CMoveSCP.PatientRoot patientRootMoveSCP;

    @Inject
    private CMoveSCP.StudyRoot studyRootMoveSCP;

    @Inject
    private CMoveSCP.PatientStudyOnly patientStudyOnlyMoveSCP;

    @Inject
    private CGetSCP.PatientRoot patientRootGetSCP;

    @Inject
    private CGetSCP.StudyRoot studyRootGetSCP;

    @Inject
    private CGetSCP.PatientStudyOnly patientStudyOnlyGetSCP;

    @Inject
    private CGetSCP.WithoutBulkData withoutBulkDataGetSCP;

    @Inject
    private MWLCFindSCP mwlFindSCP;

    @Inject
    private PatientUpdateService patientUpdateService;

    public Archive() {
        Archive.instance = this;
    }

    public void init(DicomConfiguration dicomConfiguration,
            HL7Configuration hl7Configuration, Device device)
                    throws JMSException {
        init(device);
        this.dicomConfiguration = dicomConfiguration;
        this.aeCache = new ApplicationEntityCache(dicomConfiguration);
        this.hl7AppCache = new HL7ApplicationCache(hl7Configuration);
        device.setDimseRQHandler(serviceRegistry());
        device.getDeviceExtension(HL7DeviceExtension.class)
            .setHL7MessageListener(hl7ServiceRegistry());
        setConfigurationStaleTimeout();
        AuditLogger.setDefaultLogger(
                device.getDeviceExtension(AuditLogger.class));
        jmsService.init();
    }

    public static Archive getInstance() {
        return Archive.instance;
    }

    private HL7MessageListener hl7ServiceRegistry() {
        HL7ServiceRegistry serviceRegistry = new HL7ServiceRegistry();
        serviceRegistry.addHL7Service(patientUpdateService);
        return serviceRegistry ;
    }

    @Override
    public void reload() throws Exception {
        device.reconfigure(dicomConfiguration.findDevice(device.getDeviceName()));
        setConfigurationStaleTimeout();
        device.rebindConnections();
    }

    private void setConfigurationStaleTimeout() {
        ArchiveDeviceExtension ext = device.getDeviceExtension(ArchiveDeviceExtension.class);
        int staleTimeout = ext.getConfigurationStaleTimeout();
        aeCache.setStaleTimeout(staleTimeout);
        hl7AppCache.setStaleTimeout(staleTimeout);
        WadoAttributesCache.INSTANCE.setStaleTimeout(ext.getWadoAttributesStaleTimeout());
    }

    private DicomServiceRegistry serviceRegistry() {
        DicomServiceRegistry services = new DicomServiceRegistry();
        services.addDicomService(new BasicCEchoSCP());
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
        log(AuditMessages.EventTypeCode.ApplicationStart);
    }

    @Override
    public void stop() {
        super.stop();
        jmsService.stop();
        mppsSCU.stop();
        ianSCU.stop();
        stgCmtSCP.stop();
        log(EventTypeCode.ApplicationStop);
    }

   private void log(EventTypeCode eventType) {
        AuditLogger logger = AuditLogger.getDefaultLogger();
        if (logger != null && logger.isInstalled()) {
            Calendar timeStamp = logger.timeStamp();
            try {
                logger.write(timeStamp,
                        createApplicationActivityMessage(logger, timeStamp, eventType));
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private AuditMessage createApplicationActivityMessage(AuditLogger logger,
            Calendar timeStamp, EventTypeCode eventType) {
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                EventID.ApplicationActivity, 
                EventActionCode.Execute,
                timeStamp,
                EventOutcomeIndicator.Success,
                null,
                eventType));
        msg.getAuditSourceIdentification().add(
                logger.createAuditSourceIdentification());
        msg.getActiveParticipant().add(
                logger.createActiveParticipant(true, RoleIDCode.Application));
        return msg ;
    }

    public void close() {
        if (isRunning())
            stop();
        if (dicomConfiguration != null)
            dicomConfiguration.close();
        jmsService.close();
    }

    public ApplicationEntity findApplicationEntity(String aet)
            throws ConfigurationException {
        return aeCache.findApplicationEntity(aet);
    }

    public HL7Application findHL7Application(String name)
            throws ConfigurationException {
        return hl7AppCache.findHL7Application(name);
    }

    public void addMessageListener(Queue queue, MessageListener listener)
            throws JMSException {
        jmsService.addMessageListener(queue, listener);
    }

    public void removeMessageListener(MessageListener listener)
            throws JMSException {
        jmsService.removeMessageListener(listener);
    }

    public void sendMessage(Queue queue, MessageCreator messageCreator,
            int delay) throws JMSException {
        jmsService.sendMessage(queue, messageCreator, delay);
    }

    public void scheduleIAN(String localAET, String remoteAET, Attributes ian) {
        ianSCU.scheduleIAN(localAET, remoteAET, ian, 0, 0);
    }

    public void scheduleMPPSCreate(String localAET, String remoteAET,
            String iuid, Attributes mpps) {
        mppsSCU.scheduleForwardMPPS(localAET, remoteAET, iuid, mpps,
                true, 0, 0);
    }

    public void scheduleMPPSSet(String localAET, String remoteAET,
            String iuid, Attributes mpps) {
        mppsSCU.scheduleForwardMPPS(localAET, remoteAET, iuid, mpps,
                false, 0, 0);
    }

    public IDWithIssuer[] pixQuery(ApplicationEntity ae, IDWithIssuer pid) {
        return pixConsumer.pixQuery(ae, pid);
    }

}
