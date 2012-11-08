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

import javax.xml.transform.Templates;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4che.io.SAXTransformer;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.Status;
import org.dcm4che.net.TransferCapability;
import org.dcm4che.net.service.BasicCStoreSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.AttributesFormat;
import org.dcm4che.util.TagUtils;
import org.dcm4chee.archive.common.StoreParam;
import org.dcm4chee.archive.conf.ArchiveApplicationEntity;
import org.dcm4chee.archive.entity.FileRef;
import org.dcm4chee.archive.entity.FileSystem;
import org.dcm4chee.archive.mpps.IANSCU;
import org.dcm4chee.archive.mpps.dao.IANQueryService;
import org.dcm4chee.archive.store.dao.StoreService;
import org.dcm4chee.archive.util.BeanLocator;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class CStoreSCP extends BasicCStoreSCP {

    private static final String STORE_SERVICE_PROPERTY =
            "org.dcm4chee.archive.store.impl.StoreService";

    private final ApplicationEntityCache aeCache;
    private final IANSCU ianSCU;
    private final IANQueryService ianQueryService;

    public CStoreSCP(ApplicationEntityCache aeCache, IANSCU ianSCU,
            IANQueryService ianQueryService) {
        super("*");
        this.aeCache = aeCache;
        this.ianSCU = ianSCU;
        this.ianQueryService = ianQueryService;
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
            ArchiveApplicationEntity ae =
                    (ArchiveApplicationEntity) as.getApplicationEntity();
            AttributesFormat filePathFormat = ae.getSpoolFilePathFormat();
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
        ArchiveApplicationEntity ae =
                (ArchiveApplicationEntity) as.getApplicationEntity();
        String algorithm = ae.getDigestAlgorithm();
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
        ArchiveApplicationEntity ae =
                (ArchiveApplicationEntity) as.getApplicationEntity();
        AttributesFormat filePathFormat = ae.getStorageFilePathFormat();
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
        if (ds.bigEndian())
            ds = new Attributes(ds, false);
        String sourceAET = as.getRemoteAET();
        String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
        ArchiveApplicationEntity ae =
                (ArchiveApplicationEntity) as.getApplicationEntity();
        try {
            Attributes modified = new Attributes();
            Templates tpl = ae.getAttributeCoercionTemplates(cuid,
                    Dimse.C_STORE_RQ, TransferCapability.Role.SCP, sourceAET);
            if (tpl != null)
                ds.update(SAXTransformer.transform(ds, tpl, false, false),
                        modified);
            ApplicationEntity sourceAE = aeCache.get(sourceAET);
            if (sourceAE != null)
                Supplements.supplementComposite(ds, sourceAE.getDevice());
            StoreService store = (StoreService) as.getProperty(
                            STORE_SERVICE_PROPERTY);
            FileRef fileRef = store.addFileRef(sourceAET, ds, modified, file, 
                    digest(digest), fmi.getString(Tag.TransferSyntaxUID));
            if (fileRef == null) {
                delete(as, file);
            } else if (ae.hasIANDestinations()) {
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
                if (!ae.isSuppressWarningCoercionOfDataElements()) {
                    rsp.setInt(Tag.Status, VR.US, Status.CoercionOfDataElements);
                    rsp.setInt(Tag.OffendingElement, VR.AT, modified.tags());
                }
            }
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure,
                    DicomServiceException.initialCauseOf(e));
        }
    }

    private String digest(MessageDigest digest) {
        return digest != null ? TagUtils.toHexString(digest.digest()) : null;
    }

    private StoreService initStoreService(Association as)
            throws DicomServiceException {
        StoreService store =
                    (StoreService) as.getProperty(STORE_SERVICE_PROPERTY);
        if (store == null) {
            ArchiveApplicationEntity ae = (ArchiveApplicationEntity) as.getApplicationEntity();
            String fsGroupID = ae.getFileSystemGroupID();
            if (fsGroupID == null)
                throw new IllegalStateException(
                        "No File System Group ID configured for " + ae.getAETitle());
            store = BeanLocator.lookup(StoreService.class);
            store.setStoreParam(StoreParam.valueOf(ae));
            store.selectFileSystem(fsGroupID);
            as.setProperty(STORE_SERVICE_PROPERTY, store);
        }
        return store;
    }

    private void closeStoreService(Association as) {
        StoreService store =
                (StoreService) as.clearProperty(STORE_SERVICE_PROPERTY);
        if (store != null) {
            ArchiveApplicationEntity ae =
                    (ArchiveApplicationEntity) as.getApplicationEntity();
            if (ae.hasIANDestinations())
                try {
                    scheduleIAN(ae, store.createIANforCurrentMPPS());
                } catch (Exception e) {
                    LOG.warn(as + ": Failed to create IAN for MPPS:", e);
                }
            store.close();
        }
    }

    private void scheduleIAN(ArchiveApplicationEntity ae, Attributes ian) {
        if (ian != null)
            for (String remoteAET : ae.getIANDestinations())
                ianSCU.scheduleIAN(ae.getAETitle(), remoteAET, ian, 0, 0);
    }

    @Override
    public void onClose(Association as) {
        closeStoreService(as);
    }

    @Override
    protected void cleanup(Association as, File spoolFile, File finalFile) {
        ArchiveApplicationEntity ae =
                (ArchiveApplicationEntity) as.getApplicationEntity();
        if (!ae.isPreserveSpoolFileOnFailure())
            super.cleanup(as, spoolFile, finalFile);
        else
            if (finalFile != null && finalFile.exists())
                rename(as, finalFile, spoolFile);
    }

}
