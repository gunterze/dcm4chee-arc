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
package org.dcm4chee.archive.stow;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import javax.xml.transform.Templates;
import javax.xml.transform.stream.StreamResult;

import org.apache.log4j.lf5.util.StreamUtils;
import org.dcm4che.conf.api.ConfigurationNotFoundException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Attributes.Visitor;
import org.dcm4che.data.BulkData;
import org.dcm4che.data.Fragments;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.DicomInputStream.IncludeBulkData;
import org.dcm4che.io.DicomOutputStream;
import org.dcm4che.io.SAXReader;
import org.dcm4che.io.SAXTransformer;
import org.dcm4che.mime.MultipartInputStream;
import org.dcm4che.mime.MultipartParser;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Device;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.TransferCapability;
import org.dcm4che.net.TransferCapability.Role;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.AttributesFormat;
import org.dcm4che.util.SafeClose;
import org.dcm4che.util.TagUtils;
import org.dcm4chee.archive.Archive;
import org.dcm4chee.archive.common.StoreParam;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.entity.FileRef;
import org.dcm4chee.archive.entity.FileSystem;
import org.dcm4chee.archive.store.Supplements;
import org.dcm4chee.archive.store.dao.StoreService;
import org.dcm4chee.archive.util.BeanLocator;
import org.dcm4chee.archive.util.FileUtils;
import org.dcm4chee.archive.wado.MediaTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@Path("/stow-rs/{AETitle}")
public class StowRS implements MultipartParser.Handler, StreamingOutput {

    private static final int TRANSFER_SYNTAX_NOT_SUPPORTED = 0xC122;
    private static final int DIFF_STUDY_INSTANCE_UID = 0xC123;

    private static final Logger LOG = LoggerFactory.getLogger(StowRS.class);

    //TODO replace my Tag.WarningReason when defined
    private static final int TagWarningReason = 0x00081196;

    private String name;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @HeaderParam("Content-Type")
    private MediaType contentType;

    @PathParam("AETitle")
    private String aet;

    private String wadoURL;

    private CreatorType creatorType;

    private String studyInstanceUID;

    private ApplicationEntity ae;

    private ArchiveAEExtension aeExt;

    private StoreParam storeParam;

    private StoreService storeService;

    private MultipartParser parser;

    private FileSystem storeDir;

    private File spoolDir;

    private MessageDigest digest;

    private AttributesFormat filePathFormat;

    private final List<FileInfo> files = new ArrayList<FileInfo>();

    private final Map<String, FileInfo> bulkdata =
            new HashMap<String,FileInfo>();

    private final Attributes response = new Attributes();

    private Sequence sopSequence;

    private Sequence failedSOPSequence;

    @Override
    public String toString() {
        if (name == null) {
            if (request == null)
                return super.toString();

            name = request.getRemoteHost() + ':' + request.getRemotePort();
        }
        return name;
    }

    @POST
    @Path("/studies")
    @Consumes({"multipart/related","multipart/form-data"})
    public Response storeInstances(InputStream in) throws DicomServiceException {
        return storeInstances(null, in);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}")
    @Consumes("multipart/related")
    public Response storeInstances(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            InputStream in) throws DicomServiceException {
        LOG.info("{} >> STOW-RS[{}, Content-Type={}]", new Object[] {
                this,
                request.getRequestURL(),
                contentType });
        init(studyInstanceUID);
        try {
            try {
                parser.parse(in, this);
            } catch (IOException e) {
                throw new WebApplicationException(e, Status.BAD_REQUEST);
            }
            initResponse();
            creatorType.storeInstances(this);
            return response();
        } finally {
            cleanup();
        }
    }

    private Response response() {
        if (sopSequence.isEmpty())
            throw new WebApplicationException(Status.CONFLICT);

        return Response.status(
                failedSOPSequence == null ? Status.OK : Status.ACCEPTED)
                .entity(this)
                .type(MediaTypes.APPLICATION_DICOM_XML_TYPE)
                .build();
    }

    private void init(String studyInstanceUID) throws DicomServiceException {
        String boundary = contentType.getParameters().get("boundary");
        if (boundary == null)
            throw new WebApplicationException(Status.BAD_REQUEST);

        this.studyInstanceUID = studyInstanceUID;
        this.creatorType = CreatorType.valueOf(contentType);
        parser = new MultipartParser(boundary);

        Device device = Archive.getInstance().getDevice();
        ae = device.getApplicationEntity(aet);
        if (ae == null || !ae.isInstalled()
                || (aeExt = ae.getAEExtension(ArchiveAEExtension.class)) == null)
            throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);

        String digestAlgorithm = aeExt.getDigestAlgorithm();
        if (digestAlgorithm != null) {
            try {
                digest = MessageDigest.getInstance(digestAlgorithm);
            } catch (NoSuchAlgorithmException e) {
                throw new DicomServiceException(
                        org.dcm4che.net.Status.ProcessingFailure, e);
            }
        }
        filePathFormat = aeExt.getStorageFilePathFormat();
        if (filePathFormat == null)
            throw new DicomServiceException(org.dcm4che.net.Status.ProcessingFailure,
                    "No Storage File Path Format configured for "
                            + ae.getAETitle());
        String fsGroupID = aeExt.getFileSystemGroupID();
        if (fsGroupID == null)
            throw new DicomServiceException(org.dcm4che.net.Status.ProcessingFailure,
                    "No File System Group ID configured for "
                            + ae.getAETitle());

        storeService = BeanLocator.lookup(StoreService.class);
        storeService.setStoreParam(storeParam = StoreParam.valueOf(ae));
        storeDir = storeService.selectFileSystem(
                fsGroupID, aeExt.getInitFileSystemURI());
        String spoolDirectoryPath = aeExt.getSpoolDirectoryPath();
        if (spoolDirectoryPath != null) {
            try {
                spoolDir = new File(storeDir.getDirectory(),
                        spoolDirectoryPath 
                        + File.separatorChar
                        + URLEncoder.encode(request.getRemoteHost(), "UTF-8")
                        + File.separatorChar
                        + hashCode());
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError(e);
            }
            spoolDir.mkdirs();
        }
    }

    public void initResponse() {
        wadoURL = uriInfo.getBaseUri() + "wado-rs/" + aet + "/studies/";
        if (studyInstanceUID != null)
            response.setString(Tag.RetrieveURI, VR.UT, wadoURL + studyInstanceUID);
        else
            response.setNull(Tag.RetrieveURI, VR.UT);
        sopSequence = response.newSequence(Tag.ReferencedSOPSequence, files.size());
    }

    @Override
    public void bodyPart(int partNumber, MultipartInputStream in) throws IOException {
        Map<String, String> headerParams = in.readHeaderParams();
            String mediaTypeStr = headerParams.get("content-type");
            String bulkdataURI = headerParams.get("content-location");
            LOG.info("{} >> {}:STOW-RS[Content-Type={}, Content-Location={}]",
                    new Object[] {
                    this,
                    partNumber,
                    mediaTypeStr,
                    bulkdataURI });
        try {
            MediaType mediaType = mediaTypeStr != null
                    ? MediaType.valueOf(mediaTypeStr)
                   : MediaType.TEXT_PLAIN_TYPE;
            if (creatorType.accept(mediaType, bulkdataURI)) {
                if (in.isZIP()) {
                    ZipInputStream zip = new ZipInputStream(in);
                    ZipEntry zipEntry;
                    while ((zipEntry = zip.getNextEntry()) != null) {
                        if (!zipEntry.isDirectory())
                            storeFile(zip, mediaType, bulkdataURI);
                    }
                } else {
                    storeFile(in, mediaType, bulkdataURI);
                }
            } else {
                LOG.info("{}: Ignore Part with Content-Type={}", this, mediaType);
                in.skipAll();
            }
        } catch (IllegalArgumentException e) {
            LOG.info("{}: Ignore Part with illegal Content-Type={}", this,
                    mediaTypeStr);
            in.skipAll();
        }
    }

    private void storeFile(InputStream in, MediaType mediaType,
            String bulkdataURI) throws IOException {
        File file = File.createTempFile("dcm",
                creatorType.fileSuffix(mediaType),
                spoolDir);
        LOG.info("{}: M-WRITE {}", this, file);
        OutputStream out = new FileOutputStream(file);
        try {
            MessageDigest digest = creatorType.digest(this.digest); 
            if (digest != null)
                out = new BufferedOutputStream(
                        new DigestOutputStream(out, digest));
            StreamUtils.copy(in, out);
        } finally {
            SafeClose.close(out);
        }
        FileInfo fileInfo = new FileInfo(file, mediaType);
        if (creatorType.isBulkdata(mediaType))
            bulkdata.put(bulkdataURI, fileInfo);
        else {
            if (digest != null)
                fileInfo.digest = digest.digest();
            files.add(fileInfo);
        }
    }

    private void writeDicomInstance(File file, Attributes fmi,
            Attributes dataset) throws IOException {
        LOG.info("{}: M-WRITE {}", this, file);
        file.getParentFile().mkdirs();
        OutputStream out = new FileOutputStream(file);
        try {
            MessageDigest digest = creatorType.digest(this.digest); 
            if (digest != null)
                out = new DigestOutputStream(out, digest);
            @SuppressWarnings("resource")
            DicomOutputStream dos =
                    new DicomOutputStream(out, UID.ExplicitVRLittleEndian);
            dos.writeDataset(fmi, dataset);
            dos.flush();
        } finally {
            SafeClose.close(out);
        }
    }


    private enum CreatorType {
        DicomCreator {
            @Override
            void storeInstances(StowRS stowRS) {
                stowRS.storeDicomInstances();
            }

            @Override
            boolean accept(MediaType mediaType, String bulkdataURI) {
                String type = mediaType.getType();
                String subtype = mediaType.getSubtype();
                return type.equalsIgnoreCase("application")
                        && (subtype.equalsIgnoreCase("dicom")
                         || subtype.equalsIgnoreCase("octet-stream")
                         || subtype.equalsIgnoreCase("zip")
                         || subtype.equalsIgnoreCase("x-zip"));
            }

            @Override
            boolean isBulkdata(MediaType mediaType) {
                return false;
            }

            @Override
            MessageDigest digest(MessageDigest digest) {
                return digest;
            }

            @Override
            String fileSuffix(MediaType mediaType) {
                return ".dcm";
            }
        },
        MetadataBulkdataCreator {
            @Override
            void storeInstances(StowRS stowRS) {
                stowRS.storeMetadataAndBulkData();
            }

            @Override
            boolean accept(MediaType mediaType, String bulkdataURI) {
                 return !(isBulkdata(mediaType) && bulkdataURI == null);
            }

            @Override
            boolean isBulkdata(MediaType mediaType) {
                String type = mediaType.getType();
                String subtype = mediaType.getSubtype();
                return !(type.equalsIgnoreCase("application")
                        && subtype.equalsIgnoreCase("dicom+xml"));
            }

            @Override
            MessageDigest digest(MessageDigest digest) {
                return null;
            }

            @Override
            String fileSuffix(MediaType mediaType) {
                String subtype = mediaType.getSubtype();
                return "." + subtype.substring(subtype.indexOf('+')+1)
                        .toLowerCase();
            }
        };

        abstract void storeInstances(StowRS stowRS);
        abstract boolean accept(MediaType mediaType, String bulkdataURI);
        abstract boolean isBulkdata(MediaType mediaType);
        abstract MessageDigest digest(MessageDigest digest);
        abstract String fileSuffix(MediaType mediaType);

        public static CreatorType valueOf(MediaType contentType) {
            if (contentType.getSubtype().equalsIgnoreCase("form-data"))
                return DicomCreator;

            String type = contentType.getParameters().get("type");
            if (type == null)
                throw new WebApplicationException(Status.BAD_REQUEST);
            if (type.equalsIgnoreCase(MediaTypes.APPLICATION_DICOM))
                return DicomCreator;
            else if (type.equalsIgnoreCase(MediaTypes.APPLICATION_DICOM_XML))
                return MetadataBulkdataCreator;

            throw new WebApplicationException(Status.UNSUPPORTED_MEDIA_TYPE);
        }

    }

    private static class FileInfo {
        final File file;
        final MediaType mediaType;
        byte[] digest;
        Attributes attrs;
        FileInfo(File file, MediaType mediaType) {
            this.file = file;
            this.mediaType = mediaType;
        }
    }

    private Attributes readFileMetaInformation(File file) throws IOException {
        DicomInputStream in = new DicomInputStream(file);
        try {
            return in.readFileMetaInformation();
        } finally {
            SafeClose.close(in);
        }
    }

    private void storeDicomInstances() {
        for (FileInfo fileInfo : files) {
            try {
                fileInfo.attrs = readFileMetaInformation(fileInfo.file);
            } catch (IOException e) {
                throw new WebApplicationException(e, Status.BAD_REQUEST);
            }
        }

        for (FileInfo fileInfo : files) {
            if (!checkTransferCapability(fileInfo.attrs))
                continue;

            File destFile = null;
            try {
                Attributes fmi;
                Attributes attrs;
                DicomInputStream in = new DicomInputStream(fileInfo.file);
                try {
                    in.setIncludeBulkData(IncludeBulkData.URI);
                    fmi = in.readFileMetaInformation();
                    attrs = in.readDataset(-1, -1);
                } finally {
                    SafeClose.close(in);
                }
                validate(attrs);

                if (attrs.bigEndian())
                    attrs = new Attributes(attrs, false);

                destFile = destinationFile(attrs);
                renameTo(fileInfo.file, destFile);
                storeDicomInstance(destFile, fmi, attrs, fileInfo.digest);
            } catch (Exception e) {
                LOG.info("Storage Failed:", e);

                int failureReason = e instanceof DicomServiceException
                        ? ((DicomServiceException) e).getStatus()
                        : org.dcm4che.net.Status.ProcessingFailure;
                storageFailed(fileInfo.attrs, failureReason);

                if (destFile != null && destFile.exists())
                    deleteFile(destFile);
            }
        }
    }

    private void validate(Attributes attrs)
            throws DicomServiceException {
        if (studyInstanceUID != null
                && !studyInstanceUID.equals(attrs.getString(Tag.StudyInstanceUID)))
            throw new DicomServiceException(DIFF_STUDY_INSTANCE_UID);
    }

    private boolean checkTransferCapability(Attributes fmi) {
        TransferCapability tc = ae.getTransferCapabilityFor(
                fmi.getString(Tag.MediaStorageSOPClassUID), Role.SCP);
        if (tc == null) {
            storageFailed(fmi, org.dcm4che.net.Status.SOPclassNotSupported);
            return false;
        }
        if (!tc.containsTransferSyntax(fmi.getString(Tag.TransferSyntaxUID))) {
            storageFailed(fmi, TRANSFER_SYNTAX_NOT_SUPPORTED);
            return false;
        }
        return true;
    }

    private void storageFailed(Attributes fmi, int failureReason) {
        Attributes sopRef = new Attributes(3);
        sopRef.setString(Tag.ReferencedSOPClassUID, VR.UI,
                fmi.getString(Tag.MediaStorageSOPClassUID));
        sopRef.setString(Tag.ReferencedSOPInstanceUID, VR.UI,
                fmi.getString(Tag.MediaStorageSOPInstanceUID));
        sopRef.setInt(Tag.FailureReason, VR.US, failureReason);
        if (failedSOPSequence == null)
            failedSOPSequence = response.newSequence(Tag.FailedSOPSequence, files.size());
        failedSOPSequence.add(sopRef);
    }

    private void storeDicomInstance(File f, Attributes fmi, Attributes attrs,
            byte[] digest) throws Exception {
        String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
        String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
        String tsuid = fmi.getString(Tag.TransferSyntaxUID);
        String sourceAET = fmi.getString(Tag.SourceApplicationEntityTitle);
        Attributes modified = coerceAttributes(sourceAET , cuid, attrs);
        FileRef fileRef = storeService.addFileRef(sourceAET, attrs,
                modified, f, 
                digest != null
                    ? TagUtils.toHexString(digest)
                    : null,
                tsuid);

        if (fileRef == null)
            deleteFile(f);

        Attributes sopRef = new Attributes(5);
        sopRef.setString(Tag.ReferencedSOPClassUID, VR.UI, cuid);
        sopRef.setString(Tag.ReferencedSOPInstanceUID, VR.UI, iuid);
        sopRef.setString(Tag.RetrieveURI, VR.UT, wadoURL
                + attrs.getString(Tag.StudyInstanceUID) + "/series/"
                + attrs.getString(Tag.SeriesInstanceUID) + "/instances/"
                + iuid);
        if (!modified.isEmpty()) {
            sopRef.setInt(TagWarningReason, VR.US,
                          org.dcm4che.net.Status.CoercionOfDataElements);
            if (storeParam.isStoreOriginalAttributes()) {
                Sequence seq = attrs.getSequence(Tag.OriginalAttributesSequence);
                sopRef.newSequence(Tag.OriginalAttributesSequence, 1)
                    .add(new Attributes(seq.get(seq.size()-1)));
            }
        }
        sopSequence.add(sopRef);
    }

    private void storeMetadataAndBulkData() {
        for (FileInfo fileInfo : files) {
            try {
                fileInfo.attrs = SAXReader.parse(fileInfo.file.toURI().toString());
            } catch (Exception e) {
                throw new WebApplicationException(e, Status.BAD_REQUEST);
            }
        }
        for (FileInfo fileInfo : files) {
            String tsuid = resolveBulkdata(fileInfo.attrs);
            Attributes fmi = fileInfo.attrs.createFileMetaInformation(tsuid);
            if (!checkTransferCapability(fmi))
                continue;

            File destFile = destinationFile(fileInfo.attrs);
            try {
                validate(fileInfo.attrs);
                writeDicomInstance(destFile, fmi, fileInfo.attrs);
                storeDicomInstance(destFile, fmi, fileInfo.attrs,
                        digest != null ? digest.digest() : null);
            } catch (Exception e) {
                LOG.info("Storage Failed:", e);

                int failureReason = e instanceof DicomServiceException
                        ? ((DicomServiceException) e).getStatus()
                        : org.dcm4che.net.Status.ProcessingFailure;
                storageFailed(fmi, failureReason);

                if (destFile.exists())
                    deleteFile(destFile);

            }
        }
    }

    private String resolveBulkdata(final Attributes attrs) {
        final String[] tsuids = { UID.ExplicitVRLittleEndian };
        attrs.accept(new Visitor() {
            @Override
            public void visit(Attributes attrs, int tag, VR vr, Object value) {
                if (value instanceof Sequence) {
                    Sequence sq = (Sequence) value;
                    for (Attributes item : sq)
                        resolveBulkdata(item);
                } else if (value instanceof BulkData) {
                    FileInfo fileInfo = bulkdata.get(((BulkData) value).uri);
                    if (fileInfo != null) {
                        String tsuid = MediaTypes.transferSyntaxOf(fileInfo.mediaType);
                        BulkData bd = new BulkData(
                                fileInfo.file.toURI().toString(),
                                0, (int) fileInfo.file.length(),
                                attrs.bigEndian());
                        if (tsuid.equals(UID.ExplicitVRLittleEndian)) {
                            attrs.setValue(tag, vr, bd);
                        } else {
                            Fragments frags = attrs.newFragments(tag, vr, 2);
                            frags.add(null);
                            frags.add(bd);
                            tsuids[0] = tsuid;
                        }
                    }
                }
            }
        });
        return tsuids[0];
    }

    private File destinationFile(Attributes attrs) {
        File destFile;
        synchronized (filePathFormat) {
            destFile = new File(storeDir.getDirectory(), filePathFormat.format(attrs));
        }
        return FileUtils.ensureNotExists(destFile);
    }

    private Attributes coerceAttributes(
            String sourceAET, String cuid, Attributes attrs) throws Exception {
        Attributes modified = new Attributes();
        if (sourceAET != null) {
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
        }
        return modified;
    }

    private void cleanup() {
        if (storeService != null)
            storeService.close();

        if (spoolDir != null) {
            for (File f : spoolDir.listFiles())
                deleteFile(f);
            spoolDir.delete();
        }
    }

    private void deleteFile(File file) {
        if (file.delete())
            LOG.info("{}: M-DELETE {}", this, file);
        else
            LOG.warn("{}: M-DELETE {} failed!", this, file);
    }

    private void renameTo(File from, File dest)
            throws IOException {
        LOG.info("{}: M-RENAME {} to {}", new Object[]{ this, from, dest });
        dest.getParentFile().mkdirs();
        if (!from.renameTo(dest))
            throw new IOException("Failed to rename " + from + " to " + dest);
    }

    @Override
    public void write(OutputStream out) throws IOException,
            WebApplicationException {
        try {
            SAXTransformer.getSAXWriter(new StreamResult(out)).write(response);
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

}
