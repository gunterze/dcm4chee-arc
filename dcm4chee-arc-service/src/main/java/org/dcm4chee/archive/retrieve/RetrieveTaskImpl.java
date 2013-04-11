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

package org.dcm4chee.archive.retrieve;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;

import javax.xml.transform.Templates;

import org.dcm4che.audit.AuditMessage;
import org.dcm4che.audit.AuditMessages;
import org.dcm4che.audit.AuditMessages.EventActionCode;
import org.dcm4che.audit.AuditMessages.EventID;
import org.dcm4che.audit.AuditMessages.EventOutcomeIndicator;
import org.dcm4che.audit.AuditMessages.RoleIDCode;
import org.dcm4che.audit.Instance;
import org.dcm4che.audit.ParticipantObjectDescription;
import org.dcm4che.audit.ParticipantObjectIdentification;
import org.dcm4che.audit.SOPClass;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Issuer;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.imageio.codec.Decompressor;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.DicomInputStream.IncludeBulkData;
import org.dcm4che.io.SAXTransformer;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.DataWriter;
import org.dcm4che.net.DataWriterAdapter;
import org.dcm4che.net.Device;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.TransferCapability.Role;
import org.dcm4che.net.audit.AuditLogger;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.BasicRetrieveTask;
import org.dcm4che.net.service.InstanceLocator;
import org.dcm4che.util.SafeClose;
import org.dcm4che.util.StringUtils;
import org.dcm4chee.archive.Archive;
import org.dcm4chee.archive.common.IDWithIssuer;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.retrieve.dao.RetrieveService;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
class RetrieveTaskImpl extends BasicRetrieveTask {

    private final RetrieveService retrieveService;
    private final boolean withoutBulkData;
    private IDWithIssuer[] pids;
    private String[] patientNames;
    private boolean returnOtherPatientIDs;
    private boolean returnOtherPatientNames;
    private Issuer requestedIssuerOfPatientID;
    private Issuer requestedIssuerOfAccessionNumber;

    public RetrieveTaskImpl(BasicRetrieveTask.Service service, Association as,
            PresentationContext pc, Attributes rq, List<InstanceLocator> matches,
            IDWithIssuer[] pids, RetrieveService retrieveService,
            boolean withoutBulkData) {
        super(service, as, pc, rq, matches);
        this.pids = pids;
        this.retrieveService = retrieveService;
        this.withoutBulkData = withoutBulkData;
    }

    public void setDestinationDevice(Device destDevice) {
        this.requestedIssuerOfPatientID = destDevice.getIssuerOfPatientID();
        this.requestedIssuerOfAccessionNumber = destDevice.getIssuerOfAccessionNumber();
    }

    public void setReturnOtherPatientIDs(boolean returnOtherPatientIDs) {
        this.returnOtherPatientIDs = returnOtherPatientIDs;
    }

    public void setReturnOtherPatientNames(boolean returnOtherPatientNames) {
        this.returnOtherPatientNames = returnOtherPatientNames;
    }

    @Override
    protected String selectTransferSyntaxFor(Association storeas, InstanceLocator inst) {
        if (storeas.getTransferSyntaxesFor(inst.cuid).contains(inst.tsuid))
            return inst.tsuid;
        
        return UID.ExplicitVRLittleEndian;
    }

    @Override
    protected DataWriter createDataWriter(InstanceLocator inst, String tsuid)
            throws IOException {
        Attributes attrs;
        DicomInputStream in = new DicomInputStream(inst.getFile());
        try {
            if (withoutBulkData) {
                in.setIncludeBulkData(IncludeBulkData.NO);
                attrs = in.readDataset(-1, Tag.PixelData);
            } else {
                in.setIncludeBulkData(IncludeBulkData.LOCATOR);
                attrs = in.readDataset(-1, -1);
            }
        } finally {
            SafeClose.close(in);
        }
        attrs.addAll((Attributes) inst.getObject());
        if (!tsuid.equals(inst.tsuid))
            Decompressor.decompress(attrs, inst.tsuid);

        adjustPatientID(attrs);
        adjustAccessionNumber(attrs);
        ApplicationEntity ae = as.getApplicationEntity();
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        try {
            Templates tpl = aeExt.getAttributeCoercionTemplates(
                    inst.cuid, Dimse.C_STORE_RQ, Role.SCU, as.getRemoteAET());
            if (tpl != null)
                attrs.update(SAXTransformer.transform(attrs, tpl, false, false), null);
        } catch (Exception e) {
            throw new IOException(e);
        }
        return new DataWriterAdapter(attrs);
    }

    private void adjustPatientID(Attributes attrs) {
        IDWithIssuer pid = IDWithIssuer.pidWithIssuer(attrs, null);
        if (pid == null)
            return;

        if (pids.length == 0) {
            pids = Archive.getInstance().pixQuery(as.getApplicationEntity(), pid);
        }

        IDWithIssuer issuer = pidWithMatchingIssuer(pids, requestedIssuerOfPatientID);
        if (issuer != null) {
            issuer.toPIDWithIssuer(attrs);
        } else {
            attrs.setNull(Tag.PatientID, VR.LO);
            requestedIssuerOfPatientID.toIssuerOfPatientID(attrs);
        }
        if (returnOtherPatientIDs && pids.length > 0)
            IDWithIssuer.addOtherPatientIDs(attrs, pids);
        if (returnOtherPatientNames && hasPatientNames(attrs))
            attrs.setString(Tag.OtherPatientNames, VR.PN, patientNames);
    }

    private boolean hasPatientNames(Attributes attrs) {
        if (patientNames == null) {
            if (pids.length > 1)
                patientNames = retrieveService.patientNamesOf(pids);
            else {
                String patientName = attrs.getString(Tag.PatientName);
                patientNames = patientName != null
                        ? new String[] { patientName }
                        : StringUtils.EMPTY_STRING;
            }
        }
        return patientNames.length > 0;
    }

    private IDWithIssuer pidWithMatchingIssuer(IDWithIssuer[] pids, Issuer issuer) {
        if (issuer == null)
            return pids[0];

        for (IDWithIssuer pid : pids)
            if (issuer.matches(pid.issuer))
                return pid;

        return null;
    }

    private void adjustAccessionNumber(Attributes attrs) {
        if (requestedIssuerOfAccessionNumber == null)
            return;

        adjustAccessionNumber(attrs, requestedIssuerOfAccessionNumber);
        Sequence rqAttrsSeq = attrs.getSequence(Tag.RequestAttributesSequence);
        if (rqAttrsSeq != null)
            for (Attributes rqAttrs : rqAttrsSeq)
                adjustAccessionNumber(rqAttrs, requestedIssuerOfAccessionNumber);
    }

    private void adjustAccessionNumber(Attributes attrs, Issuer destIssuer) {
        if (!attrs.containsValue(Tag.AccessionNumber))
            return;

        Issuer issuer = Issuer.valueOf(
                attrs.getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
        if (issuer == null)
            return;
        
        if (!issuer.matches(destIssuer)) {
            attrs.setNull(Tag.AccessionNumber, VR.SH);
            attrs.remove(Tag.IssuerOfAccessionNumberSequence);
        }
    }

    @Override
    protected void close() {
        super.close();
        AuditLogger logger = Archive.getInstance().getAuditLogger();
        if (logger == null || !logger.isInstalled())
            return;

        log(logger, EventOutcomeIndicator.Success, true);
        log(logger, EventOutcomeIndicator.SeriousFailure, false);
    }

    private void log(AuditLogger logger, String eventOutcomeIndicator, boolean logSuccess) {
        if (failed.size() == (logSuccess ? insts.size() : 0))
            return;

        Calendar timeStamp = logger.timeStamp();
        AuditMessage msg = createRetrieveLogMessage(logger, timeStamp, eventOutcomeIndicator);
        for (InstanceLocator inst : insts) {
            if (failed.contains(inst.iuid) == logSuccess)
                continue;

            String studyUID = ((Attributes) inst.getObject()).getString(Tag.StudyInstanceUID);
            ParticipantObjectIdentification poid = getOrCreatePOID(msg, inst, studyUID);
            SOPClass sc = getOrCreateSOPClass(msg, inst, studyUID, poid);
            Instance instance = new Instance();
            instance.setUID(inst.iuid);
            sc.getInstance().add(instance);
            sc.setNumberOfInstances(sc.getInstance().size());
        }
        sendAuditLogMessage(logger, timeStamp, msg);
    }

    private AuditMessage createRetrieveLogMessage(AuditLogger logger, Calendar timeStamp, String eventOutcomeIndicator) {
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
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                pids[0].toString(),
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

    private ParticipantObjectIdentification getOrCreatePOID(AuditMessage msg, InstanceLocator instLoc, String studyUID) {
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

    private SOPClass getOrCreateSOPClass(AuditMessage msg, InstanceLocator instLoc, String studyUID,
            ParticipantObjectIdentification poid) {
        ParticipantObjectDescription pod = poid.getParticipantObjectDescription();
        List<SOPClass> scl = pod.getSOPClass();
        for (SOPClass sc : scl)
            if (sc.getUID().equals(instLoc.cuid))
                return sc;

        SOPClass sc = new SOPClass();
        sc.setUID(instLoc.cuid);
        poid.getParticipantObjectDescription().getSOPClass().add(sc);
        return sc;
    }

    private void sendAuditLogMessage(AuditLogger logger, Calendar timeStamp, AuditMessage msg) {
        try {
            if (LOG.isDebugEnabled())
                LOG.debug("Send DICOM Instance Transferred Audit Log message: {}", AuditMessages.toXML(msg));
            logger.write(timeStamp, msg);
        } catch (Exception e) {
            LOG.error("Failed to write audit log message: {}", e.getMessage());
            LOG.debug(e.getMessage(), e);
        }
    }

 }
