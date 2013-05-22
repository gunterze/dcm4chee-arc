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
 * Portions created by the Initial Developer are Copyright (C) 2013
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

package org.dcm4chee.archive.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.dcm4che.audit.AuditMessage;
import org.dcm4che.audit.AuditMessages;
import org.dcm4che.audit.Instance;
import org.dcm4che.audit.ParticipantObjectDescription;
import org.dcm4che.audit.ParticipantObjectDetail;
import org.dcm4che.audit.ParticipantObjectIdentification;
import org.dcm4che.audit.SOPClass;
import org.dcm4che.audit.AuditMessages.EventActionCode;
import org.dcm4che.audit.AuditMessages.EventID;
import org.dcm4che.audit.AuditMessages.EventOutcomeIndicator;
import org.dcm4che.audit.AuditMessages.ParticipantObjectIDTypeCode;
import org.dcm4che.audit.AuditMessages.ParticipantObjectTypeCode;
import org.dcm4che.audit.AuditMessages.ParticipantObjectTypeCodeRole;
import org.dcm4che.audit.AuditMessages.RoleIDCode;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.io.DicomOutputStream;
import org.dcm4che.net.Association;
import org.dcm4che.net.audit.AuditLogger;
import org.dcm4che.net.service.InstanceLocator;
import org.dcm4che.util.SafeClose;
import org.dcm4chee.archive.Archive;
import org.dcm4chee.archive.entity.InstanceFileRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class AuditUtils {

    protected static final Logger LOG = LoggerFactory.getLogger(AuditUtils.class);

    public static void logQuery(Association as, Attributes rq, Attributes keys) {
        AuditLogger logger = Archive.getInstance().getAuditLogger();
        if (logger == null || !logger.isInstalled())
            return;

        Calendar timeStamp = logger.timeStamp();
        AuditMessage msg = createQueryAuditMessage(logger, timeStamp, as, rq, keys);
        sendAuditMessage(logger, msg, timeStamp);
    }

    private static AuditMessage createQueryAuditMessage(AuditLogger logger, Calendar timeStamp, Association as,
            Attributes rq, Attributes keys) {
        AuditMessage msg = new AuditMessage();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DicomOutputStream dout = null;
        try {
            msg.setEventIdentification(AuditMessages.createEventIdentification(
                    EventID.Query, 
                    EventActionCode.Execute, 
                    timeStamp, 
                    EventOutcomeIndicator.Success, 
                    null));
            msg.getActiveParticipant().add(logger.createActiveParticipant(false, RoleIDCode.Destination));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    as.getRemoteAET(), 
                    AuditMessages.alternativeUserIDForAETitle(as.getRemoteAET()), 
                    null, 
                    true, 
                    as.getSocket().getInetAddress().getCanonicalHostName(),
                    AuditMessages.NetworkAccessPointTypeCode.MachineName, 
                    null));
            ParticipantObjectDetail pod = new ParticipantObjectDetail();
            pod.setType("TransferSyntax");
            pod.setValue(UID.ExplicitVRLittleEndian.getBytes());
            try {
                dout = new DicomOutputStream(bout, UID.ExplicitVRLittleEndian);
                dout.writeDataset(null, keys);
            } catch (IOException ignore) {}
            msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                    rq.getString(Tag.AffectedSOPClassUID), 
                    ParticipantObjectIDTypeCode.SOPClassUID, 
                    null, 
                    bout.toByteArray(), 
                    ParticipantObjectTypeCode.SystemObject, 
                    ParticipantObjectTypeCodeRole.Report, 
                    null, 
                    null, 
                    null, 
                    pod));
            msg.getAuditSourceIdentification().add(logger.createAuditSourceIdentification());
        } finally {
            SafeClose.close(bout);
            SafeClose.close(dout);
        }
        return msg;
    }

    public static void logInstanceStored(Association as, Attributes attrs,
            String eventOutcomeIndicator, String propertyName) {
        if (attrs == null)
            return;

        AuditLogger logger = Archive.getInstance().getAuditLogger();
        if (logger == null || !logger.isInstalled())
            return;

        String studyUID = attrs.getString(Tag.StudyInstanceUID);
        ParticipantObjectIdentification poid = null;
        AuditMessage msg = (AuditMessage) as.getProperty(propertyName);
        if (msg != null) {
            poid = msg.getParticipantObjectIdentification().get(0);
            if (!poid.getParticipantObjectID().equals(studyUID)) {
                sendAuditLogMessage(msg);
                poid = null;
            }
        }
        if (poid == null) {
            msg = createInstanceStoredAuditMessage(logger, as, attrs,
                    eventOutcomeIndicator);
            poid = msg.getParticipantObjectIdentification().get(0);
            as.setProperty(propertyName, msg);
        }
        SOPClass sc = getOrCreateSOPClass(
                poid.getParticipantObjectDescription(),
                attrs.getString(Tag.SOPClassUID));
        Instance instance = new Instance();
        instance.setUID(attrs.getString(Tag.SOPInstanceUID));
        sc.getInstance().add(instance);
        sc.setNumberOfInstances(sc.getInstance().size());
    }

    private static SOPClass getOrCreateSOPClass(
            ParticipantObjectDescription pod, String cuid) {
        for (SOPClass sc : pod.getSOPClass())
            if (sc.getUID().equals(cuid))
                return sc;

        SOPClass sc = new SOPClass();
        sc.setUID(cuid);
        pod.getSOPClass().add(sc);
        return sc;
    }

    private static AuditMessage createInstanceStoredAuditMessage(
            AuditLogger logger, Association as, Attributes ds,
            String eventOutcomeIndicator) {
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(
                AuditMessages.createEventIdentification(
                    EventID.DICOMInstancesTransferred, 
                    EventActionCode.Create, 
                    logger.timeStamp(), 
                    eventOutcomeIndicator, 
                    null));
        msg.getActiveParticipant().add(
                logger.createActiveParticipant(false, RoleIDCode.Source));
        msg.getActiveParticipant().add(
                AuditMessages.createActiveParticipant(
                    as.getRemoteAET(), 
                    AuditMessages.alternativeUserIDForAETitle(as.getRemoteAET()), 
                    null, 
                    true, 
                    as.getSocket().getInetAddress().getCanonicalHostName(),
                    AuditMessages.NetworkAccessPointTypeCode.MachineName, 
                    null, 
                    AuditMessages.RoleIDCode.Destination));
        msg.getParticipantObjectIdentification().add(
                AuditMessages.createParticipantObjectIdentification(
                    ds.getString(Tag.StudyInstanceUID), 
                    AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, 
                    null, 
                    null, 
                    AuditMessages.ParticipantObjectTypeCode.SystemObject, 
                    AuditMessages.ParticipantObjectTypeCodeRole.Report, 
                    null, 
                    null, 
                    new ParticipantObjectDescription()));
        msg.getParticipantObjectIdentification().add(
                AuditMessages.createParticipantObjectIdentification(
                    getPatientID(ds),
                    AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                    null,
                    null,
                    AuditMessages.ParticipantObjectTypeCode.Person,
                    AuditMessages.ParticipantObjectTypeCodeRole.Patient,
                    null,
                    null,
                    null));
        msg.getAuditSourceIdentification().add(
                logger.createAuditSourceIdentification());
        return msg;
    }

    public static void sendAuditLogMessage(AuditMessage msg) {
        if (msg == null)
            return;
    
        AuditLogger logger = Archive.getInstance().getAuditLogger();
        if (logger == null || !logger.isInstalled())
            return;

        Calendar timeStamp = logger.timeStamp();
        sendAuditMessage(logger, msg, timeStamp);
    }

    private static void sendAuditMessage(AuditLogger logger, AuditMessage msg,
            Calendar timeStamp) {
        try {
            if (LOG.isDebugEnabled())
                LOG.debug("Send Audit Log message: {}", AuditMessages.toXML(msg));
            logger.write(timeStamp, msg);
        } catch (Exception e) {
            LOG.error("Failed to write audit log message: {}", e.getMessage());
            LOG.debug(e.getMessage(), e);
        }
    }

    public static void logWADORetrieve(InstanceFileRef ref, Attributes attrs,
            HttpServletRequest request) {
        AuditLogger logger = Archive.getInstance().getAuditLogger();
        if (logger == null || !logger.isInstalled())
            return;

        Calendar timeStamp = logger.timeStamp();
        AuditMessage msg = createRetrieveLogMessage(logger, ref, attrs,
                request, timeStamp);
        sendAuditMessage(logger, msg, timeStamp);
    }

    private static AuditMessage createRetrieveLogMessage(AuditLogger logger,
            InstanceFileRef ref, Attributes attrs, HttpServletRequest request,
            Calendar timeStamp) {
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                EventID.DICOMInstancesTransferred, 
                EventActionCode.Read, 
                timeStamp, 
                EventOutcomeIndicator.Success, 
                null));
        msg.getActiveParticipant().add(logger.createActiveParticipant(false, RoleIDCode.Source));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                (request.getRemoteUser() != null)
                    ? request.getRemoteUser()
                    : "ANONYMOUS", 
                null, 
                null, 
                true, 
                request.getRemoteHost(), 
                AuditMessages.NetworkAccessPointTypeCode.MachineName, 
                null, 
                AuditMessages.RoleIDCode.Destination));
        ParticipantObjectDescription pod = createRetrieveObjectPOD(ref);
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                attrs.getString(Tag.StudyInstanceUID), 
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, 
                null, 
                null, 
                AuditMessages.ParticipantObjectTypeCode.SystemObject, 
                AuditMessages.ParticipantObjectTypeCodeRole.Report, 
                null, 
                null, 
                pod));
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                attrs.getString(Tag.PatientID),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                null,
                null,
                AuditMessages.ParticipantObjectTypeCode.Person,
                AuditMessages.ParticipantObjectTypeCodeRole.Patient,
                null,
                null,
                null));
        msg.getAuditSourceIdentification().add(logger.createAuditSourceIdentification());
        return msg;
    }

    private static ParticipantObjectDescription createRetrieveObjectPOD(
            InstanceFileRef ref) {
        ParticipantObjectDescription pod = new ParticipantObjectDescription();
        SOPClass sc = new SOPClass();
        sc.setUID(ref.sopClassUID);
        sc.setNumberOfInstances(1);
        Instance inst = new Instance();
        inst.setUID(ref.sopInstanceUID);
        sc.getInstance().add(inst);
        pod.getSOPClass().add(sc);
        return pod;
    }

    public static void logRetrieve(Association as, List<InstanceLocator> insts,
            ArrayList<String> failed) {
        AuditLogger logger = Archive.getInstance().getAuditLogger();
        if (insts.isEmpty() || logger == null || !logger.isInstalled())
            return;

        
        logRetrieve(logger, as, insts, failed, 
                EventOutcomeIndicator.Success, true);
        logRetrieve(logger, as, insts, failed,
                EventOutcomeIndicator.SeriousFailure, false);
    }

    private static void logRetrieve(AuditLogger logger, Association as,
            List<InstanceLocator> insts, ArrayList<String> failed,
            String eventOutcomeIndicator, boolean logSuccess) {
        if (failed.size() == (logSuccess ? insts.size() : 0))
            return;

        Calendar timeStamp = logger.timeStamp();
        AuditMessage msg = createRetrieveLogMessage(logger, as, 
                getPatientID((Attributes) insts.get(0).getObject()),
                timeStamp, eventOutcomeIndicator);
        for (InstanceLocator inst : insts) {
            if (failed.contains(inst.iuid) == logSuccess)
                continue;

            String studyUID = ((Attributes) inst.getObject())
                    .getString(Tag.StudyInstanceUID);
            ParticipantObjectIdentification poid = getOrCreatePOID(msg, inst, studyUID);
            SOPClass sc = getOrCreateSOPClass(
                    poid.getParticipantObjectDescription(),
                    inst.cuid);
            Instance instance = new Instance();
            instance.setUID(inst.iuid);
            sc.getInstance().add(instance);
            sc.setNumberOfInstances(sc.getInstance().size());
        }
        sendAuditMessage(logger, msg, timeStamp);
    }

    private static AuditMessage createRetrieveLogMessage(AuditLogger logger,
            Association as, String pid, Calendar timeStamp,
            String eventOutcomeIndicator) {
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                EventID.DICOMInstancesTransferred, 
                EventActionCode.Read, 
                timeStamp, 
                eventOutcomeIndicator, 
                null));
        msg.getActiveParticipant().add(logger.createActiveParticipant(false, RoleIDCode.Source));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                as.getRemoteAET(), 
                AuditMessages.alternativeUserIDForAETitle(as.getRemoteAET()), 
                null, 
                true, 
                as.getSocket().getInetAddress().getCanonicalHostName(),
                AuditMessages.NetworkAccessPointTypeCode.MachineName, 
                null, 
                AuditMessages.RoleIDCode.Destination));
        msg.getParticipantObjectIdentification().add(
                AuditMessages.createParticipantObjectIdentification(
                pid,
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                null,
                null,
                AuditMessages.ParticipantObjectTypeCode.Person,
                AuditMessages.ParticipantObjectTypeCodeRole.Patient,
                null,
                null,
                null));
        msg.getAuditSourceIdentification().add(logger.createAuditSourceIdentification());
        return msg;
    }

    private static String getPatientID(Attributes attrs) {
        String patID = attrs.getString(Tag.PatientID);
        if (patID == null)
            return "UNKNOWN";
        String issuer = attrs.getString(Tag.IssuerOfPatientID);
        return (issuer == null || issuer.length() == 0) 
                ? patID
                : patID + "^^^" + issuer;
    }

    private static ParticipantObjectIdentification getOrCreatePOID(AuditMessage msg, InstanceLocator instLoc, String studyUID) {
        for (ParticipantObjectIdentification poid : msg.getParticipantObjectIdentification())
            if (poid.getParticipantObjectID().equals(studyUID))
                return poid;

        ParticipantObjectDescription pod = new ParticipantObjectDescription();
        SOPClass sc = new SOPClass();
        sc.setUID(instLoc.cuid);
        pod.getSOPClass().add(sc);
        ParticipantObjectIdentification poid = AuditMessages.createParticipantObjectIdentification(
                studyUID, 
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, 
                null, 
                null, 
                AuditMessages.ParticipantObjectTypeCode.SystemObject, 
                AuditMessages.ParticipantObjectTypeCodeRole.Report, 
                null, 
                null, 
                pod);
        msg.getParticipantObjectIdentification().add(poid);
        return poid;
    }

}
