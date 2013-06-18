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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.ejb.EJB;
import javax.xml.transform.Templates;

import org.dcm4che.audit.AuditMessage;
import org.dcm4che.audit.AuditMessages.EventOutcomeIndicator;
import org.dcm4che.conf.api.ConfigurationNotFoundException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.BulkData;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.imageio.codec.CompressionRule;
import org.dcm4che.imageio.codec.Compressor;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.DicomInputStream.IncludeBulkData;
import org.dcm4che.io.DicomOutputStream;
import org.dcm4che.io.SAXTransformer;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.PDVInputStream;
import org.dcm4che.net.Status;
import org.dcm4che.net.TransferCapability;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.BasicCStoreSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.AttributesFormat;
import org.dcm4che.util.SafeClose;
import org.dcm4che.util.TagUtils;
import org.dcm4chee.archive.Archive;
import org.dcm4chee.archive.common.StoreParam;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.entity.FileRef;
import org.dcm4chee.archive.entity.FileSystem;
import org.dcm4chee.archive.entity.Instance;
import org.dcm4chee.archive.mpps.dao.IANQueryService;
import org.dcm4chee.archive.store.dao.StoreService;
import org.dcm4chee.archive.util.AuditUtils;
import org.dcm4chee.archive.util.BeanLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class CStoreSCP extends BasicCStoreSCP {

    private static final String STORE_SERVICE_PROPERTY = CStoreSCP.class.getName();
    private static final String AUDIT_MESSAGE_SUCCESS = "InstanceStoredSuccess";
    private static final String AUDIT_MESSAGE_FAILURE = "InstanceStoredFailed";

    private static final Logger LOG = LoggerFactory.getLogger(CStoreSCP.class);


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
    protected void store(Association as, PresentationContext pc,
            Attributes rq, PDVInputStream data, Attributes rsp)
            throws IOException {

        ApplicationEntity ae = as.getApplicationEntity();
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        String sourceAET = as.getRemoteAET();
        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
        String tsuid = pc.getTransferSyntax();
        Attributes fmi = as.createFileMetaInformation(iuid, cuid, tsuid);
        Attributes attrs = null;
        File spoolFile = null;
        File destFile = null;
        try {
            MessageDigest digest = createMessageDigest(
                    aeExt.getDigestAlgorithm());

            StoreService store = initStoreService(as, ae, aeExt);

            FileSystem storeDir = store.getCurrentFileSystem();
            destFile = spoolFile =
                    newFile(storeDir, aeExt.getSpoolFilePathFormat(), fmi);
            storeTo(as, fmi, data, spoolFile, digest);
            attrs = parse(spoolFile);
            if (attrs.bigEndian())
                attrs = new Attributes(attrs, false);
            AttributesFormat filePathFormat = aeExt.getStorageFilePathFormat();
            if (filePathFormat != null) {
                File f = newFile(storeDir, filePathFormat, attrs);
                CompressionRule compressionRule =
                        findCompressionRules(aeExt, sourceAET, attrs);
                if (compressionRule != null) {
                    try {
                        MessageDigest digest2 = createMessageDigest(
                                aeExt.getDigestAlgorithm());
                        CStoreSCP.compress(as, fmi, attrs, tsuid,
                                compressionRule, spoolFile, f, digest2);
                        tsuid = compressionRule.getTransferSyntax();
                        digest = digest2;
                        CStoreSCP.deleteFile(as, spoolFile);
                    } catch (IOException e) {
                        LOG.info("Compression failed:", e);
                        deleteFile(as, f);
                        CStoreSCP.renameTo(as, spoolFile, f);
                    }
                } else {
                    CStoreSCP.renameTo(as, spoolFile, f);
                }
                destFile = f;
            }
            Attributes modified = coerceAttributes(aeExt, sourceAET, cuid, attrs);
            FileRef fileRef = store.addFileRef(sourceAET, attrs, modified, destFile, 
                    digest(digest), tsuid);
            if (!modified.isEmpty())
                onCoercionOfDataElements(as, aeExt, attrs, modified, rsp);
            if (fileRef != null) {
                if (aeExt.hasIANDestinations())
                    scheduleIANs(store, ae, attrs, fileRef.getInstance());
            } else {
               deleteFile(as, destFile);
            }
            AuditUtils.logInstanceStored(as, attrs,
                    EventOutcomeIndicator.Success, AUDIT_MESSAGE_SUCCESS);
        } catch (Exception e) {
            if (destFile != null) {
                AuditUtils.logInstanceStored(as, attrs,
                        EventOutcomeIndicator.SeriousFailure,
                        AUDIT_MESSAGE_FAILURE);
                if (aeExt.isPreserveSpoolFileOnFailure()) {
                    if (destFile != spoolFile)
                        renameTo(as, destFile, spoolFile);
                } else {
                    deleteFile(as, destFile);
                }
            }
            if (e instanceof DicomServiceException) {
                throw (DicomServiceException) e;
            } else {
                throw new DicomServiceException(Status.ProcessingFailure, e);
            }
        }
    }

    private static CompressionRule findCompressionRules(ArchiveAEExtension aeExt,
            String sourceAET, Attributes attrs) {
        if (!(attrs.getValue(Tag.PixelData) instanceof BulkData))
            return null;

        return aeExt.getCompressionRules().findCompressionRule(sourceAET, attrs);
    }

    private static void compress(Association as, Attributes fmi,
            Attributes attrs, String tsuid, CompressionRule rule, File src,
            File dest, MessageDigest digest) throws IOException {
        LOG.info("{}: M-COMPRESS {} to {}", new Object[]{ as, src, dest });
        dest.getParentFile().mkdirs();
        Compressor compressor = new Compressor(attrs, tsuid);
        DicomOutputStream out = null;
        try {
            compressor.compress(rule.getTransferSyntax(),
                    rule.getImageWriteParams());
            if (digest == null)
                out = new DicomOutputStream(dest);
            else {
                out = new DicomOutputStream(
                        new BufferedOutputStream(
                            new DigestOutputStream(
                                    new FileOutputStream(dest), digest)),
                        UID.ExplicitVRLittleEndian);
            }
            fmi.setString(Tag.TransferSyntaxUID, VR.UI, tsuid);
            out.writeDataset(fmi, attrs);
        } finally {
            SafeClose.close(out);
            compressor.close();
        }
    }

    private void scheduleIANs(StoreService store, ApplicationEntity ae,
            Attributes attrs, Instance instance) {
        for (Attributes ian : store.createIANsforPreviousMPPS())
            scheduleIAN(ae, ian);

        switch (instance.getAvailability()) {
        case REJECTED_FOR_QUALITY_REASONS_REJECTION_NOTE:
        case REJECTED_FOR_PATIENT_SAFETY_REASONS_REJECTION_NOTE:
        case DATA_RETENTION_PERIOD_EXPIRED_REJECTION_NOTE:
            scheduleIAN(ae, ianQueryService.createIANforRejectionNote(attrs));
            break;
        case INCORRECT_MODALITY_WORKLIST_ENTRY_REJECTION_NOTE:
            for (Attributes ian : ianQueryService
                    .createIANsforIncorrectModalityWorklistEntry(attrs))
                scheduleIAN(ae, ian);
            break;
        default:
            break;
        }
    }

    private void onCoercionOfDataElements(Association as,
            ArchiveAEExtension aeExt, Attributes attrs, Attributes modified,
            Attributes rsp) {
        if (LOG.isInfoEnabled()) {
            LOG.info("{}:Coercion of Data Elements:\n{}\nto:\n{}",
                    new Object[] { 
                        as,
                        modified,
                        new Attributes(attrs, attrs.bigEndian(), modified.tags())
                    });
        }
        if (!aeExt.isSuppressWarningCoercionOfDataElements()) {
            rsp.setInt(Tag.Status, VR.US, Status.CoercionOfDataElements);
            rsp.setInt(Tag.OffendingElement, VR.AT, modified.tags());
        }
    }

    private Attributes coerceAttributes(ArchiveAEExtension aeExt,
            String sourceAET, String cuid, Attributes attrs) throws Exception {
        Attributes modified = new Attributes();
        Templates tpl = aeExt.getAttributeCoercionTemplates(cuid,
                Dimse.C_STORE_RQ, TransferCapability.Role.SCP, sourceAET);
        if (tpl != null)
            attrs.update(SAXTransformer.transform(attrs, tpl, false, false),
                    modified);
        try {
            ApplicationEntity sourceAE = Archive.getInstance()
                    .findApplicationEntity(sourceAET);
            Supplements.supplementComposite(attrs, sourceAE.getDevice());
        } catch (ConfigurationNotFoundException e) {
        }
        return modified;
    }

    private MessageDigest createMessageDigest(String algorithm)
            throws NoSuchAlgorithmException {
        return algorithm != null
                ? MessageDigest.getInstance(algorithm)
                : null;
    }

    private static File newFile(FileSystem fs, AttributesFormat filePathFormat,
            Attributes fmi) {
        File file;
        synchronized (filePathFormat) {
            file = new File(fs.getDirectory(), filePathFormat.format(fmi));
        }
        while (file.exists()) {
            file = new File(file.getParentFile(),
                    Integer.toString(LazyInitialization.nextInt()));
        }
        return file;
    }

    private void storeTo(Association as, Attributes fmi, 
            PDVInputStream data, File file, MessageDigest digest)
                    throws IOException  {
        LOG.info("{}: M-WRITE {}", as, file);
        file.getParentFile().mkdirs();
        DicomOutputStream out;
        if (digest == null)
            out = new DicomOutputStream(file);
        else {
            out = new DicomOutputStream(
                    new BufferedOutputStream(
                        new DigestOutputStream(
                                new FileOutputStream(file), digest)),
                    UID.ExplicitVRLittleEndian);
        }
        try {
            out.writeFileMetaInformation(fmi);
            data.copyTo(out);
        } finally {
            SafeClose.close(out);
        }
    }

    private static void renameTo(Association as, File from, File dest)
            throws IOException {
        LOG.info("{}: M-RENAME {} to {}", new Object[]{ as, from, dest });
        dest.getParentFile().mkdirs();
        if (!from.renameTo(dest))
            throw new IOException("Failed to rename " + from + " to " + dest);
    }

    private static Attributes parse(File file) throws IOException {
        DicomInputStream in = new DicomInputStream(file);
        try {
            in.setIncludeBulkData(IncludeBulkData.URI);
            return in.readDataset(-1, -1);
        } finally {
            SafeClose.close(in);
        }
    }

    private static void deleteFile(Association as, File file) {
        if (file.delete())
            LOG.info("{}: M-DELETE {}", as, file);
        else
            LOG.warn("{}: M-DELETE {} failed!", as, file);
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

    private StoreService initStoreService(Association as, ApplicationEntity ae,
            ArchiveAEExtension aeExt) throws DicomServiceException {
        StoreService store =
                    (StoreService) as.getProperty(STORE_SERVICE_PROPERTY);
        if (store == null) {
            String fsGroupID = aeExt.getFileSystemGroupID();
            if (fsGroupID == null)
                throw new DicomServiceException(Status.OutOfResources,
                        "No File System Group ID configured for "
                                + ae.getAETitle());
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
            if (aeExt.hasIANDestinations()) {
                for (Attributes ian : store.createIANsforCurrentMPPS()) {
                    scheduleIAN(ae, ian);
                }
            }
            store.close();
        }
    }

    @Override
    public void onClose(Association as) {
        closeStoreService(as);
        AuditUtils.sendAuditLogMessage(
                (AuditMessage) as.clearProperty(AUDIT_MESSAGE_SUCCESS));
        AuditUtils.sendAuditLogMessage(
                (AuditMessage) as.clearProperty(AUDIT_MESSAGE_FAILURE));
    }

}
