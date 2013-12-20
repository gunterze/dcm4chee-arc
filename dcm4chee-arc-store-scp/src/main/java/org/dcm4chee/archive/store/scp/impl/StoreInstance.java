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
 * Portions created by the Initial Developer are Copyright (C) 2011-2013
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

package org.dcm4chee.archive.store.scp.impl;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.transform.Templates;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.BulkData;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.imageio.codec.CompressionRule;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.DicomOutputStream;
import org.dcm4che.io.SAXTransformer;
import org.dcm4che.io.DicomInputStream.IncludeBulkData;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.PDVInputStream;
import org.dcm4che.net.Status;
import org.dcm4che.net.TransferCapability;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.AttributesFormat;
import org.dcm4che.util.TagUtils;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.entity.FileRef;
import org.dcm4chee.archive.entity.FileSystem;
import org.dcm4chee.archive.store.Supplements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
class StoreInstance implements Closeable {

    private static Logger LOG = LoggerFactory.getLogger(StoreInstance.class);

    private final CStoreSCP scp;
    private final Association as;
    private final ApplicationEntity ae;
    private final ArchiveAEExtension aeExt;
    private final MessageDigest digest;
    private final FileSystem fs;
    private final Path fsPath;
    private final String sourceAET;
    private final String cuid;
    private final String iuid;
    private final String tsuid;
    private final Attributes fmi;
    private Path spoolPath;
    private File spoolFile;
    private String fileDigest;
    private Attributes attrs;
    private Attributes coercedAttrs;
    private Path storePath;

    public StoreInstance(CStoreSCP scp, Association as, PresentationContext pc,
            Attributes rq) throws DicomServiceException {
        this.sourceAET = as.getRemoteAET();
        this.cuid = rq.getString(Tag.AffectedSOPClassUID);
        this.iuid = rq.getString(Tag.AffectedSOPInstanceUID);
        this.tsuid = pc.getTransferSyntax();
        this.fmi = as.createFileMetaInformation(iuid, cuid, tsuid);
        this.scp = scp;
        this.as = as;
        this.ae = as.getApplicationEntity();
        this.aeExt = aeExtensionOf();
        this.digest = messageDigestOf();
        this.fs = selectStorageFileSystem();
        this.fsPath = fs.getPath();
    }

    private ArchiveAEExtension aeExtensionOf()
            throws DicomServiceException {
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        if (aeExt == null)
            throw new DicomServiceException(
                    Status.ProcessingFailure,
                    "No ArchiveAEExtension configured for "
                            + as.getLocalAET());
        return aeExt;
    }

    private MessageDigest messageDigestOf()
            throws DicomServiceException {
        String algorithm = aeExt.getDigestAlgorithm();
        try {
            return algorithm != null
                    ? MessageDigest.getInstance(algorithm)
                    : null;
        } catch (NoSuchAlgorithmException e) {
            throw new DicomServiceException(
                    Status.ProcessingFailure, e);
        }
    }

    private FileSystem selectStorageFileSystem() throws DicomServiceException {
        FileSystem fs = (FileSystem) as.getProperty(FileSystem.class.getName());
        if (fs == null) {
            fs = scp.getStoreService().selectStorageFileSystem(
                    aeExt.getFileSystemGroupID(),
                    aeExt.getInitFileSystemURI());
            as.setProperty(FileSystem.class.getName(), fs);
        }
        return fs;
    }

    private Path createSpoolPath() throws IOException {
        String spoolDirectoryPath = aeExt.getSpoolDirectoryPath();
        if (spoolDirectoryPath == null)
            return Files.createTempFile("dcm", ".dcm");

        Path dir = fsPath.resolve(spoolDirectoryPath)
                .resolve(sourceAET).resolve(cuid);
        return Files.createTempFile(Files.createDirectories(dir), "dcm", ".dcm");
    }

    public void spool(PDVInputStream data) throws IOException {
        spoolPath = createSpoolPath();
        spoolFile = spoolPath.toFile();
        try (DicomOutputStream out = digest == null
                ? new DicomOutputStream(spoolFile)
                : new DicomOutputStream(
                        new BufferedOutputStream(
                                new DigestOutputStream(
                                        new FileOutputStream(spoolFile), digest)),
                            UID.ExplicitVRLittleEndian)) {
            out.writeFileMetaInformation(fmi);
            data.copyTo(out);
            if (digest != null) {
                fileDigest = TagUtils.toHexString(digest.digest());
                digest.reset();
            }
        }
    }

    public void process(Attributes rsp) throws DicomServiceException {
        try {
            this.attrs = parse();
            this.storePath = createStorePath();
            if (!compress()) {
                storePath = move(spoolPath, storePath);
                spoolPath = null;
            }
            coerceAttributes();
            updateDB();
            checkCoercedAttributes(rsp);
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        } 
    }

    private Attributes parse() throws IOException {
        try (DicomInputStream in = new DicomInputStream(spoolFile)) {
            in.setIncludeBulkData(IncludeBulkData.URI);
            return in.readDataset(-1, -1);
        }
    }

    private Path createStorePath() throws IOException {
        AttributesFormat filePathFormat = aeExt.getStorageFilePathFormat();
        if (filePathFormat == null)
            throw new DicomServiceException(
                    Status.ProcessingFailure,
                    "No StorageFilePathFormat configured for "
                            + aeExt.getApplicationEntity().getAETitle());
        String path;
        synchronized (filePathFormat) {
            path = filePathFormat.format(attrs);
        }
        Path storePath = fsPath.resolve(
                path.replace('/', File.separatorChar));
        Files.createDirectories(storePath.getParent());
        return storePath;
    }

    private boolean compress() {
        if (!(attrs.getValue(Tag.PixelData) instanceof BulkData))
            return false;

        CompressionRule compressionRule = aeExt.getCompressionRules()
                .findCompressionRule(sourceAET, attrs);
        if (compressionRule == null)
            return false;
        
        try {
            storePath = createFile(storePath);
            scp.getCompressionService()
                    .compress(compressionRule, spoolFile,
                            storePath.toFile(), digest, fmi, attrs);
            if (digest != null) {
                fileDigest = TagUtils.toHexString(digest.digest());
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private Path move(Path source, Path target)
            throws IOException {
        for (;;) {
            try {
                return Files.move(source, target);
            } catch (FileAlreadyExistsException e) {
                target = target.resolveSibling(
                        target.getFileName().toString() + '-');
            }
        }
    }

    private Path createFile(Path path) throws IOException {
        for (;;) {
            try {
                return Files.createFile(path);
            } catch (FileAlreadyExistsException e) {
                path = path.resolveSibling(
                        path.getFileName().toString() + '-');
            }
        }
    }

    private void updateDB() throws DicomServiceException {
        File file = storePath.toFile();
        FileRef fileRef = new FileRef(
                fs,
                unixFilePath(),
                fmi.getString(Tag.TransferSyntaxUID),
                file.length(), 
                fileDigest);
        if (scp.getStoreService().store(aeExt.getStoreParam(), 
                sourceAET, attrs, fileRef, coercedAttrs)) {
            storePath = null;
        }
    }

    private void coerceAttributes() throws Exception {
        Attributes modified = new Attributes();
        Templates tpl = aeExt.getAttributeCoercionTemplates(cuid,
                Dimse.C_STORE_RQ, TransferCapability.Role.SCP, sourceAET);
        if (tpl != null)
            attrs.update(SAXTransformer.transform(attrs, tpl, false, false),
                    modified);
        ApplicationEntity sourceAE = scp.getApplicationEntityCache()
                    .get(sourceAET);
        if (sourceAE != null)
            Supplements.supplementComposite(attrs, sourceAE.getDevice());
        coercedAttrs = modified;
    }

    private void checkCoercedAttributes(Attributes rsp) {
        if (coercedAttrs.isEmpty())
            return;

        if (LOG.isInfoEnabled()) {
            LOG.info("{}:Coercion of Data Elements:\n{}\nto:\n{}",
                    new Object[] { 
                        as,
                        coercedAttrs,
                        new Attributes(attrs, attrs.bigEndian(), coercedAttrs.tags())
                    });
        }
        if (!aeExt.isSuppressWarningCoercionOfDataElements()) {
            rsp.setInt(Tag.Status, VR.US, Status.CoercionOfDataElements);
            rsp.setInt(Tag.OffendingElement, VR.AT, coercedAttrs.tags());
        }
    }

    private String unixFilePath() {
        return fsPath.relativize(storePath).toString()
            .replace(File.separatorChar, '/');
    }

    @Override
    public void close() throws IOException {
        if (spoolPath != null)
            Files.deleteIfExists(spoolPath);
        if (storePath != null)
            Files.deleteIfExists(storePath);
    }
}
