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

package org.dcm4chee.archive.store;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.List;

import javax.ejb.EJB;
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
import org.dcm4che.conf.api.ConfigurationNotFoundException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4che.io.SAXTransformer;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.Status;
import org.dcm4che.net.TransferCapability;
import org.dcm4che.net.audit.AuditLogger;
import org.dcm4che.net.service.BasicCStoreSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.AttributesFormat;
import org.dcm4che.util.TagUtils;
import org.dcm4chee.archive.Archive;
import org.dcm4chee.archive.common.StoreParam;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.entity.FileRef;
import org.dcm4chee.archive.entity.FileSystem;
import org.dcm4chee.archive.mpps.dao.IANQueryService;
import org.dcm4chee.archive.store.dao.StoreService;
import org.dcm4chee.archive.util.BeanLocator;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class CStoreSCP extends BasicCStoreSCP {

    private static final String STORE_SERVICE_PROPERTY = CStoreSCP.class.getName();
    private static final String AUDIT_MESSAGE_SUCCESS = "AuditMessageSuccess";
    private static final String AUDIT_MESSAGE_FAILURE = "AuditMessageFailure";

    private AuditLogger logger;

    @EJB
    private IANQueryService ianQueryService;

    public CStoreSCP() {
        super("*");
    }

    private static class LazyInitialization {
        static final SecureRandom random = new SecureRandom();

        static int nextInt() {
            int n = random.nextInt();
            return n < 0 ? -(n+1) : n;
        }
    }

    @Override
    protected File getSpoolFile(Association as, Attributes fmi)
            throws DicomServiceException {
        StoreService store = initStoreService(as);
        try {
            FileSystem fs = store.getCurrentFileSystem();
            ApplicationEntity ae = as.getApplicationEntity();
            ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
            AttributesFormat filePathFormat = aeExt.getSpoolFilePathFormat();
            File file;
            synchronized (filePathFormat) {
                file = new File(fs.getDirectory(), filePathFormat.format(fmi));
            }
            while (file.exists()) {
                file = new File(file.getParentFile(),
                        Integer.toString(LazyInitialization.nextInt()));
            }
            return file;
        } catch (Exception e) {
            LOG.warn(as + ": Failed to create file:", e);
            throw new DicomServiceException(Status.OutOfResources, e);
        }
    }

    @Override
    protected MessageDigest getMessageDigest(Association as) {
        ApplicationEntity ae = as.getApplicationEntity();
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        String algorithm = aeExt.getDigestAlgorithm();
        try {
            return algorithm != null 
                    ? MessageDigest.getInstance(algorithm)
                    : null;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected File getFinalFile(Association as, Attributes fmi, Attributes ds,
            File spoolFile) {
        ApplicationEntity ae = as.getApplicationEntity();
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        AttributesFormat filePathFormat = aeExt.getStorageFilePathFormat();
        if (filePathFormat == null)
            return spoolFile;

        StoreService store = (StoreService) as.getProperty(
                STORE_SERVICE_PROPERTY);
        File storeDir = store.getCurrentFileSystem().getDirectory();
        File dst;
        synchronized (filePathFormat) {
            dst = new File(storeDir, filePathFormat.format(ds));
        }
        while (dst.exists()) {
            dst = new File(dst.getParentFile(),
                    TagUtils.toHexString(LazyInitialization.nextInt()));
        }
        return dst;
    }

    @Override
     protected void process(Association as, Attributes fmi, Attributes ds,
            File file, MessageDigest digest, Attributes rsp)
            throws DicomServiceException {
        if (logger == null)
            logger = Archive.getInstance().getAuditLogger();
        if (ds.bigEndian())
            ds = new Attributes(ds, false);
        String sourceAET = as.getRemoteAET();
        String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
        ApplicationEntity ae = as.getApplicationEntity();
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        try {
            Attributes modified = new Attributes();
            Templates tpl = aeExt.getAttributeCoercionTemplates(cuid,
                    Dimse.C_STORE_RQ, TransferCapability.Role.SCP, sourceAET);
            if (tpl != null)
                ds.update(SAXTransformer.transform(ds, tpl, false, false),
                        modified);
            try {
                ApplicationEntity sourceAE = Archive.getInstance()
                        .findApplicationEntity(sourceAET);
                Supplements.supplementComposite(ds, sourceAE.getDevice());
            } catch (ConfigurationNotFoundException e) {
            }
            StoreService store = (StoreService) as.getProperty(
                            STORE_SERVICE_PROPERTY);
            FileRef fileRef = store.addFileRef(sourceAET, ds, modified, file, 
                    digest(digest), fmi.getString(Tag.TransferSyntaxUID));
            if (fileRef == null) {
                delete(as, file);
            } else if (aeExt.hasIANDestinations()) {
                scheduleIAN(ae, store.createIANforPreviousMPPS());
                switch (fileRef.getInstance().getAvailability()) {
                case REJECTED_FOR_QUALITY_REASONS_REJECTION_NOTE:
                case REJECTED_FOR_PATIENT_SAFETY_REASONS_REJECTION_NOTE:
                case DATA_RETENTION_PERIOD_EXPIRED_REJECTION_NOTE:
                    scheduleIAN(ae, ianQueryService.createIANforRejectionNote(ds));
                    break;
                case INCORRECT_MODALITY_WORKLIST_ENTRY_REJECTION_NOTE:
                    for (Attributes ian : ianQueryService
                            .createIANsforIncorrectModalityWorklistEntry(ds))
                        scheduleIAN(ae, ian);
                    break;
                default:
                    break;
                }
            }
            if (!modified.isEmpty()) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("{}:Coercion of Data Elements:\n{}\nto:\n{}",
                            new Object[] { 
                                as,
                                modified,
                                new Attributes(ds, ds.bigEndian(), modified.tags())
                            });
                }
                if (!aeExt.isSuppressWarningCoercionOfDataElements()) {
                    rsp.setInt(Tag.Status, VR.US, Status.CoercionOfDataElements);
                    rsp.setInt(Tag.OffendingElement, VR.AT, modified.tags());
                }
            }
            log(as, ds, EventOutcomeIndicator.Success, true);
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            log(as, ds, EventOutcomeIndicator.SeriousFailure, false);
            throw new DicomServiceException(Status.ProcessingFailure,
                    DicomServiceException.initialCauseOf(e));
        }
    }

    private void log(Association as, Attributes ds, String eventOutcomeIndicator, boolean success) {
        if (logger == null || !logger.isInstalled())
            return;

        Calendar timeStamp = logger.timeStamp();
        AuditMessage msg = (AuditMessage) as.getProperty(success ? AUDIT_MESSAGE_SUCCESS : AUDIT_MESSAGE_FAILURE);
        if (!(msg instanceof AuditMessage)) {
            as.setProperty(success ? AUDIT_MESSAGE_SUCCESS : AUDIT_MESSAGE_FAILURE,
                    createAuditMessage(as, ds, timeStamp, eventOutcomeIndicator));
            return;
        }
        String studyUID = ds.getString(Tag.StudyInstanceUID);
        ParticipantObjectIdentification poid = null;
        for (ParticipantObjectIdentification p : msg.getParticipantObjectIdentification())
            if (p.getParticipantObjectID().equals(studyUID))
                poid = p;
        if (poid == null) {
            sendAuditLogMessage(msg, timeStamp);
            msg = createAuditMessage(as, ds, timeStamp, eventOutcomeIndicator);
            as.setProperty(success ? AUDIT_MESSAGE_SUCCESS : AUDIT_MESSAGE_FAILURE,
                    createAuditMessage(as, ds, timeStamp, eventOutcomeIndicator));
            return;
        }
        SOPClass sc = getOrCreateSOPClass(msg, ds.getString(Tag.SOPClassUID), studyUID, poid);
        Instance instance = new Instance();
        instance.setUID(ds.getString(Tag.SOPInstanceUID));
        sc.getInstance().add(instance);
        sc.setNumberOfInstances(sc.getInstance().size());
        as.setProperty(success ? AUDIT_MESSAGE_SUCCESS : AUDIT_MESSAGE_FAILURE, msg);
    }

    private SOPClass getOrCreateSOPClass(AuditMessage msg, String cuid, String studyUID,
            ParticipantObjectIdentification poid) {
        ParticipantObjectDescription pod = poid.getParticipantObjectDescription();
        List<SOPClass> scl = pod.getSOPClass();
        for (SOPClass sc : scl)
            if (sc.getUID().equals(cuid))
                return sc;

        SOPClass sc = new SOPClass();
        sc.setUID(cuid);
        poid.getParticipantObjectDescription().getSOPClass().add(sc);
        return sc;
    }

    private AuditMessage createAuditMessage(Association as, Attributes ds, Calendar timeStamp,
            String eventOutcomeIndicator) {
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                EventID.DICOMInstancesTransferred, 
                EventActionCode.Create, 
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
        ParticipantObjectDescription pod = createObjectPOD(ds);
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                ds.getString(Tag.StudyInstanceUID), 
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, 
                null, 
                null, 
                AuditMessages.ParticipantObjectTypeCode.SystemObject, 
                AuditMessages.ParticipantObjectTypeCodeRole.Report, 
                null, 
                null, 
                pod));
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                getPatientIDString(ds),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                null,
                null,
                AuditMessages.ParticipantObjectTypeCode.Person,
                AuditMessages.ParticipantObjectTypeCodeRole.Patient,
                null,
                null,
                null));
        return msg;
    }

    private ParticipantObjectDescription createObjectPOD(Attributes ds) {
        ParticipantObjectDescription pod = new ParticipantObjectDescription();
        SOPClass sc = new SOPClass();
        sc.setUID(ds.getString(Tag.SOPClassUID));
        sc.setNumberOfInstances(1);
        Instance inst = new Instance();
        inst.setUID(ds.getString(Tag.SOPInstanceUID));
        sc.getInstance().add(inst);
        pod.getSOPClass().add(sc);
        return pod;
    }

    private String getPatientIDString(Attributes ds) {
        String id = ds.getString(Tag.PatientID);
        String issuer = ds.getString(Tag.IssuerOfPatientID);
        return issuer == null ? id : id + "^^^" + issuer;
    }

    private void sendAuditLogMessage(AuditMessage msg, Calendar timeStamp) {
        if (!(msg instanceof AuditMessage))
            return;
    
        try {
            if (LOG.isDebugEnabled())
                LOG.debug("Send Audit Log message: {}", AuditMessages.toXML(msg));
            logger.write(timeStamp, msg);
        } catch (Exception e) {
            LOG.error("Failed to write audit log message: {}", e.getMessage());
            LOG.debug(e.getMessage(), e);
        }
    }

    private void scheduleIAN(ApplicationEntity ae, Attributes ian) {
        Archive r = Archive.getInstance();
        if (ian != null)
        for (String remoteAET : ae.getAEExtension(ArchiveAEExtension.class)
                .getIANDestinations())
            r.scheduleIAN(ae.getAETitle(), remoteAET, ian);
    }

    private String digest(MessageDigest digest) {
        return digest != null ? TagUtils.toHexString(digest.digest()) : null;
    }

    private StoreService initStoreService(Association as)
            throws DicomServiceException {
        StoreService store =
                    (StoreService) as.getProperty(STORE_SERVICE_PROPERTY);
        if (store == null) {
            ApplicationEntity ae = as.getApplicationEntity();
            ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
            String fsGroupID = aeExt.getFileSystemGroupID();
            if (fsGroupID == null)
                throw new IllegalStateException(
                        "No File System Group ID configured for " + ae.getAETitle());
            store = BeanLocator.lookup(StoreService.class);
            store.setStoreParam(StoreParam.valueOf(ae));
            store.selectFileSystem(fsGroupID, aeExt.getInitFileSystemURI());
            as.setProperty(STORE_SERVICE_PROPERTY, store);
        }
        return store;
    }

    private void closeStoreService(Association as) {
        StoreService store =
                (StoreService) as.clearProperty(STORE_SERVICE_PROPERTY);
        if (store != null) {
            ApplicationEntity ae = as.getApplicationEntity();
            ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
            if (aeExt.hasIANDestinations())
                try {
                    scheduleIAN(ae, store.createIANforCurrentMPPS());
                } catch (Exception e) {
                    LOG.warn(as + ": Failed to create IAN for MPPS:", e);
                }
            store.close();
        }
    }

    @Override
    public void onClose(Association as) {
        closeStoreService(as);
        if (logger != null && logger.isInstalled()) {
            Calendar timeStamp = logger.timeStamp();
            sendAuditLogMessage((AuditMessage) as.getProperty(AUDIT_MESSAGE_SUCCESS), timeStamp);
            sendAuditLogMessage((AuditMessage) as.getProperty(AUDIT_MESSAGE_FAILURE), timeStamp);
        }
    }

    @Override
    protected void cleanup(Association as, File spoolFile, File finalFile) {
        ApplicationEntity ae = as.getApplicationEntity();
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        if (!aeExt.isPreserveSpoolFileOnFailure())
            super.cleanup(as, spoolFile, finalFile);
        else
            if (finalFile != null && finalFile.exists())
                rename(as, finalFile, spoolFile);
    }

}
