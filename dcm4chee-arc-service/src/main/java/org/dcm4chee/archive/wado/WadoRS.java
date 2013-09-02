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
package org.dcm4chee.archive.wado;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ejb.EJB;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.BulkData;
import org.dcm4che.data.Fragments;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.imageio.codec.Decompressor;
import org.dcm4che.imageio.codec.ImageReaderFactory;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.DicomInputStream.IncludeBulkData;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Device;
import org.dcm4che.util.SafeClose;
import org.dcm4che.util.StringUtils;
import org.dcm4chee.archive.Archive;
import org.dcm4chee.archive.dao.SeriesService;
import org.dcm4chee.archive.entity.InstanceFileRef;
import org.dcm4chee.archive.resteasy.LogInterceptor;
import org.dcm4chee.archive.wado.dao.WadoService;
import org.jboss.resteasy.plugins.providers.multipart.ContentIDUtils;
import org.jboss.resteasy.plugins.providers.multipart.MultipartRelatedOutput;
import org.jboss.resteasy.plugins.providers.multipart.OutputPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@Path("/wado/{AETitle}")
public class WadoRS {

    private static final int STATUS_OK = 200;
    private static final int STATUS_PARTIAL_CONTENT = 206;
    private static final int STATUS_NOT_ACCEPTABLE = 406;
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_ID = "Content-ID";
    private static final String CONTENT_LOCATION = "Content-Location";

    private static final Logger LOG = LoggerFactory.getLogger(WadoRS.class);

    public static final class FrameList {
        final int[] frames;
        public FrameList(String s) {
            String[] ss = StringUtils.split(s, ',');
            int[] values = new int[ss.length];
            for (int i = 0; i < ss.length; i++) {
                try {
                    if ((values[i] = Integer.parseInt(ss[i])) <= 0)
                        throw new WebApplicationException(Status.BAD_REQUEST);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(s);
                }
            }
            this.frames = values;
        }
    }

    @EJB
    private SeriesService seriesService;

    @EJB
    private WadoService wadoService;

    @PathParam("AETitle")
    private String aet;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Context
    private HttpHeaders headers;

    private boolean acceptAll;

    private boolean acceptZip;

    private boolean acceptDicomXML;

    private boolean acceptDicom;

    private boolean acceptOctetStream;

    private boolean acceptBulkdata;

    private List<String> acceptedTransferSyntaxes;

    private List<MediaType> acceptedBulkdataMediaTypes;

    private String method;

    private String toBulkDataURI(String uri) {
        return uriInfo.getBaseUri() + "wado/" + aet + "/bulkdata/" + uri;
    }


    private void init(String method) {
        this.method = method;
        List<MediaType> acceptableMediaTypes = headers.getAcceptableMediaTypes();
        Device device = Archive.getInstance().getDevice();
        ApplicationEntity ae = device.getApplicationEntity(aet);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);

        this.acceptedTransferSyntaxes =
                new ArrayList<String>(acceptableMediaTypes.size());

        this.acceptedBulkdataMediaTypes =
                new ArrayList<MediaType>(acceptableMediaTypes.size());

        for (MediaType mediaType : acceptableMediaTypes) {
            if (mediaType.isWildcardType())
                acceptAll = true;
            else if (mediaType.isCompatible(MediaTypes.APPLICATION_ZIP_TYPE))
                acceptZip = true;
            else if (mediaType.isCompatible(MediaTypes.MULTIPART_RELATED_TYPE)) {
                try {
                    MediaType relatedType = MediaType.valueOf(
                            mediaType.getParameters().get("type"));
                    if (relatedType.isCompatible(
                            MediaTypes.APPLICATION_DICOM_TYPE)) {
                        acceptDicom = true;
                        acceptedTransferSyntaxes.add(
                                relatedType.getParameters().get("transfer-syntax"));
                    } else if (relatedType.isCompatible(
                            MediaTypes.APPLICATION_DICOM_XML_TYPE)) {
                        acceptDicomXML = true;
                    } else {
                        acceptBulkdata = true;
                        if (relatedType.isCompatible(
                                MediaType.APPLICATION_OCTET_STREAM_TYPE))
                            acceptOctetStream = true;
                        acceptedBulkdataMediaTypes.add(relatedType);
                    }
                } catch (IllegalArgumentException e) {
                    throw new WebApplicationException(Status.BAD_REQUEST);
                }
            }
        }
    }

    private String selectDicomTransferSyntaxes(String ts) {
        for (String ts1 : acceptedTransferSyntaxes) {
            if (ts1 == null || ts1.equals(ts))
                return ts;
        }
        if (ImageReaderFactory.canDecompress(ts)) {
            if (acceptedTransferSyntaxes.contains(UID.ExplicitVRLittleEndian)) {
                return UID.ExplicitVRLittleEndian;
            }
            if (acceptedTransferSyntaxes.contains(UID.ImplicitVRLittleEndian)) {
                return UID.ImplicitVRLittleEndian;
            }
        }
        return null;
    }

    private MediaType selectBulkdataMediaTypeForTransferSyntax(String ts) {
        MediaType requiredMediaType = null;
        try {
            requiredMediaType = MediaTypes.forTransferSyntax(ts);
        } catch (IllegalArgumentException e) {}
        if (requiredMediaType == null)
            return null;

        if (acceptAll)
            return requiredMediaType;

        boolean defaultTS = !requiredMediaType.getParameters()
                .containsKey("transfer-syntax");
        for (MediaType mediaType : acceptedBulkdataMediaTypes) {
            if (mediaType.isCompatible(requiredMediaType)) {
                String ts1 = mediaType.getParameters().get("transfer-syntax");
                if (ts1 == null ? defaultTS : ts1.equals(ts))
                    return requiredMediaType;
            }
        }
        if (acceptOctetStream && ImageReaderFactory.canDecompress(ts)) {
            return MediaType.APPLICATION_OCTET_STREAM_TYPE;
        }
        return null;
    }

    @GET
    @Path("/studies/{StudyInstanceUID}")
    public Response retrieveStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID) {
        init("retrieveStudy");
        return retrieve(wadoService.locate(studyInstanceUID));
    }

    @GET
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}")
    public Response retrieveSeries(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID) {
        init("retrieveSeries");
        return retrieve(wadoService.locate(studyInstanceUID, seriesInstanceUID));
    }

    @GET
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/{SOPInstanceUID}")
    public Response retrieveInstance(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID,
            @PathParam("SOPInstanceUID") String sopInstanceUID) {
        init("retrieveInstance");
        return retrieve(wadoService.locate(
                studyInstanceUID, seriesInstanceUID, sopInstanceUID));
    }

    @GET
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/{SOPInstanceUID}/frames/{FrameList}")
    @Produces("multipart/related")
    public Response retrieveFrame(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID,
            @PathParam("SOPInstanceUID") String sopInstanceUID,
            @PathParam("FrameList") FrameList frameList) {
        init("retrieveFrame");
        InstanceFileRef ref = wadoService.locate(
                        studyInstanceUID, seriesInstanceUID, sopInstanceUID);
        if (ref == null)
            throw new WebApplicationException(Status.NOT_FOUND);

        return retrievePixelData(ref.uri, frameList.frames);
    }

    @GET
    @Path("/bulkdata/{BulkDataURI:.*}")
    @Produces("multipart/related")
    public Response retrieveBulkdata(
            @PathParam("BulkDataURI") String bulkDataURI,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("length") @DefaultValue("-1") int length) {

        init("retrieveBulkdata");
        return (length <= 0)
                ? retrievePixelData(bulkDataURI)
                : retrieveBulkData(
                        new BulkData(bulkDataURI, offset, length, false));
        
    }

    @GET
    @Path("/studies/{StudyInstanceUID}/metadata")
    @Produces("multipart/related")
    public Response retrieveMetadata(
            @PathParam("StudyInstanceUID") String studyInstanceUID) {
        init("retrieveMetadata");
        return retrieveMetadata(wadoService.locate(studyInstanceUID));
    }

    private Response retrieve(List<InstanceFileRef> refs) {
        if (refs.isEmpty())
            throw new WebApplicationException(Status.NOT_FOUND);

        if (acceptDicom || acceptBulkdata) {
            MultipartRelatedOutput output = new MultipartRelatedOutput();
            int failed = 0;
            if (acceptedBulkdataMediaTypes.isEmpty()) {
                for (InstanceFileRef ref : refs)
                    if (!addDicomObjectTo(ref, output))
                        failed++;
            } else {
                for (InstanceFileRef ref : refs)
                    if (addPixelDataTo(ref.uri, output) != STATUS_OK)
                        failed++;
            }
    
            if (output.getParts().isEmpty())
                throw new WebApplicationException(Status.NOT_ACCEPTABLE);
    
            int status = failed > 0 ? STATUS_PARTIAL_CONTENT : STATUS_OK;
            return Response.status(status).entity(output).build();
        }

        if (!acceptZip && !acceptAll)
            throw new WebApplicationException(Status.NOT_ACCEPTABLE);

        ZipOutput output = new ZipOutput();
        for (InstanceFileRef ref : refs) {
            Attributes attrs = ref
                    .getAttributes(WadoAttributesCache.INSTANCE
                    .getAttributes(seriesService, ref.seriesPk));
            output.addEntry(
                    new DicomObjectOutput(ref, attrs, ref.transferSyntaxUID));
        }
        return Response.ok().entity(output)
                .type(MediaTypes.APPLICATION_ZIP_TYPE).build();
    }

    private Response retrieve(InstanceFileRef ref) {
        if (ref == null)
            throw new WebApplicationException(Status.NOT_FOUND);

        if (acceptDicom || acceptBulkdata) {
            int status = STATUS_OK;
            MultipartRelatedOutput output = new MultipartRelatedOutput();
            if (acceptBulkdata) {
                addDicomObjectTo(ref, output);
            } else {
                status = addPixelDataTo(ref.uri, output);
            }

            if (output.getParts().isEmpty())
                throw new WebApplicationException(Status.NOT_ACCEPTABLE);

            return Response.status(status).entity(output).build();
        }

        if (!acceptZip && !acceptAll)
            throw new WebApplicationException(Status.NOT_ACCEPTABLE);

        ZipOutput output = new ZipOutput();
        Attributes attrs = ref
                .getAttributes(WadoAttributesCache.INSTANCE
                .getAttributes(seriesService, ref.seriesPk));
        output.addEntry(
                new DicomObjectOutput(ref, attrs, ref.transferSyntaxUID));
        return Response.ok().entity(output)
                .type(MediaTypes.APPLICATION_ZIP_TYPE).build();
    }

    private Response retrievePixelData(String fileURI, int... frames) {
        MultipartRelatedOutput output = new MultipartRelatedOutput();
        
        int status = addPixelDataTo(fileURI, output, frames);

        if (output.getParts().isEmpty())
            throw new WebApplicationException(Status.NOT_ACCEPTABLE);

        return Response.status(status).entity(output).build();
    }

    private Response retrieveBulkData(BulkData bulkData) {
        if (!acceptOctetStream)
            throw new WebApplicationException(Status.NOT_ACCEPTABLE);

        MultipartRelatedOutput output = new MultipartRelatedOutput();
        addPart(output,
                new BulkDataOutput(bulkData),
                MediaType.APPLICATION_OCTET_STREAM_TYPE,
                uriInfo.getRequestUri().toString(),
                null);

        return Response.ok(output).build();
    }


    private Response retrieveMetadata(List<InstanceFileRef> refs) {
        if (refs.isEmpty())
            throw new WebApplicationException(Status.NOT_FOUND);

        if (!acceptDicomXML && !acceptAll)
            throw new WebApplicationException(Status.NOT_ACCEPTABLE);

        MultipartRelatedOutput output = new MultipartRelatedOutput();
        for (InstanceFileRef ref : refs)
            addMetadataTo(ref, output);

        return Response.ok(output).build();
    }

    private boolean addDicomObjectTo(InstanceFileRef ref,
            MultipartRelatedOutput output) {
        String tsuid = selectDicomTransferSyntaxes(ref.transferSyntaxUID);
        if (tsuid == null) {
            return false;
        }
        Attributes attrs = ref.getAttributes(WadoAttributesCache.INSTANCE
                .getAttributes(seriesService, ref.seriesPk));
        addPart(output,
                new DicomObjectOutput(ref, attrs, tsuid),
                MediaType.valueOf("application/dicom;transfer-syntax=" + tsuid),
                null,
                ref.sopInstanceUID);
        return true;
    }

    private int addPixelDataTo(String fileURI, MultipartRelatedOutput output,
            int... frameList) {
        DicomInputStream dis = null;
        try {
            dis = new DicomInputStream(new File(new URI(fileURI)));
            dis.setIncludeBulkData(IncludeBulkData.URI);
            Attributes fmi = dis.readFileMetaInformation();
            String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
            MediaType mediaType = selectBulkdataMediaTypeForTransferSyntax(
                    dis.getTransferSyntax());
            if (mediaType == null) {
                LOG.info("{}: Failed to retrieve Pixel Data of Instance[uid={}]: Requested Transfer Syntax not supported",
                        method, iuid);
                return STATUS_NOT_ACCEPTABLE;
            }

            if (MediaTypes.isMultiframeMediaType(mediaType) && frameList.length > 0) {
                LOG.info("{}: Failed to retrieve Frame Pixel Data of Instance[uid={}]: Not supported for Content-Type={}",
                        new Object[] {method, iuid, mediaType});
                return STATUS_NOT_ACCEPTABLE;
            }

            Attributes ds = dis.readDataset(-1, -1);
            Object pixeldata = ds.getValue(Tag.PixelData);
            if (pixeldata == null) {
                LOG.info("{}: Failed to retrieve Pixel Data of Instance[uid={}]: Not an image",
                        method, iuid);
                return STATUS_NOT_ACCEPTABLE;
            }

            int frames = ds.getInt(Tag.NumberOfFrames, 1);
            int[] adjustedFrameList = adjustFrameList(iuid, frameList, frames);

            String bulkDataURI = toBulkDataURI(fileURI);
            if (pixeldata instanceof Fragments) {
                Fragments bulkData = (Fragments) pixeldata;
                if (mediaType == MediaType.APPLICATION_OCTET_STREAM_TYPE) {
                    addDecompressedPixelDataTo(new Decompressor(ds, dis.getTransferSyntax()),
                            adjustedFrameList, output, bulkDataURI, iuid);
                } else {
                    addCompressedPixelDataTo(bulkData, frames,
                            adjustedFrameList, output, mediaType, bulkDataURI, iuid);
                }
            } else {
                BulkData bulkData = (BulkData) pixeldata;
                addUncompressedPixelDataTo(bulkData, ds,
                        adjustedFrameList, output, bulkDataURI, iuid);
            }
            return adjustedFrameList.length < frameList.length
                    ? STATUS_PARTIAL_CONTENT : STATUS_OK;
        } catch (FileNotFoundException e) {
            throw new WebApplicationException(Status.NOT_FOUND);
        } catch (IOException e) {
            throw new WebApplicationException(e);
        } catch (URISyntaxException e) {
            throw new WebApplicationException(e);
        } finally {
            SafeClose.close(dis);
        }
    }

    private int[] adjustFrameList(String iuid, int[] frameList, int frames) {
        int n = 0;
        for (int i = 0; i < frameList.length; i++) {
            if (frameList[i] <= frames)
                swap(frameList, n++, i);
        }
        if (n == frameList.length)
            return frameList;
        
        int[] skipped = new int[frameList.length - n];
        System.arraycopy(frameList, n, skipped, 0, skipped.length);
        LOG.info("{}, Failed to retrieve Frames {} of Pixel Data of Instance[uid={}]: NumberOfFrames={}",
                new Object[] { method, Arrays.toString(skipped), iuid, frames });
        if (n == 0)
            throw new WebApplicationException(Status.NOT_FOUND);

        return Arrays.copyOf(frameList, n);
    }

    private static void swap(int[] a, int i, int j) {
        if (i != j) {
            int tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
        }
    }

    private void addDecompressedPixelDataTo(Decompressor decompressor,
            int[] frameList, MultipartRelatedOutput output, String bulkDataURI,
            String iuid) {
        if (frameList.length == 0) {
            addPart(output,
                    new DecompressedPixelDataOutput(decompressor, -1),
                    MediaType.APPLICATION_OCTET_STREAM_TYPE,
                    bulkDataURI,
                    iuid);
        } else for (int frame : frameList) {
            addPart(output,
                    new DecompressedPixelDataOutput(decompressor, frame-1),
                    MediaType.APPLICATION_OCTET_STREAM_TYPE,
                    bulkDataURI + "/frames/" + frame,
                    iuid);
        }
    }

    private void addPart(MultipartRelatedOutput output,
            Object entity, MediaType mediaType, String contentLocation,
            String iuid) {
        OutputPart part = output.addPart(entity, mediaType);
        MultivaluedMap<String, Object> headerParams = part.getHeaders();
        headerParams.add(CONTENT_TYPE, mediaType);
        headerParams.add(CONTENT_ID, ContentIDUtils.generateContentID());
        if (contentLocation != null)
            headerParams.add(CONTENT_LOCATION, contentLocation);
        if (iuid != null)
            LOG.info("{}: Add Part #{} [uid={}]{}", new Object[] {
                    method,
                    output.getParts().size(),
                    iuid,
                    LogInterceptor.toString(headerParams) });
        else
            LOG.info("{}: Add Part #{}{}", new Object[] {
                    method,
                    output.getParts().size(), 
                    LogInterceptor.toString(headerParams) });
    }

    private void addCompressedPixelDataTo(Fragments fragments, int frames,
            int[] adjustedFrameList, MultipartRelatedOutput output,
            MediaType mediaType, String bulkDataURI, String iuid) {
        if (frames == 1 || MediaTypes.isMultiframeMediaType(mediaType)) {
            addPart(output,
                    new CompressedPixelDataOutput(fragments),
                    mediaType,
                    bulkDataURI,
                    iuid);
        } else if (adjustedFrameList.length == 0) {
            for (int frame = 1; frame <= frames; frame++) {
                addPart(output,
                        new BulkDataOutput((BulkData) fragments.get(frame)),
                        mediaType,
                        bulkDataURI + "/frames/" + frame,
                        iuid);
            }
        } else {
            for (int frame : adjustedFrameList) {
                addPart(output,
                        new BulkDataOutput((BulkData) fragments.get(frame)),
                        mediaType,
                        bulkDataURI + "/frames/" + frame,
                        iuid);
            }
        }
    }

    private void addUncompressedPixelDataTo(BulkData bulkData, Attributes ds,
            int[] adjustedFrameList, MultipartRelatedOutput output,
            String bulkDataURI, String iuid) {
        if (adjustedFrameList.length == 0) {
            addPart(output,
                    new BulkDataOutput(bulkData),
                    MediaType.APPLICATION_OCTET_STREAM_TYPE,
                    bulkDataURI,
                    iuid);
        } else {
            int rows = ds.getInt(Tag.Rows, 0);
            int cols = ds.getInt(Tag.Columns, 0);
            int samples = ds.getInt(Tag.SamplesPerPixel, 0);
            int bitsAllocated = ds.getInt(Tag.BitsAllocated, 8);
            int frameLength = rows * cols * samples * (bitsAllocated>>>3);
            for (int frame : adjustedFrameList) {
                addPart(output,
                    new BulkDataOutput(new BulkData(
                                bulkData.uriWithoutOffsetAndLength(),
                                bulkData.offset + (frame-1) * frameLength,
                                frameLength,
                                ds.bigEndian())),
                    MediaType.APPLICATION_OCTET_STREAM_TYPE,
                    bulkDataURI + "/frames/" + frame,
                    iuid);
            }
        }
    }

    private void addMetadataTo(InstanceFileRef ref,
            MultipartRelatedOutput output) {
        Attributes attrs = ref.getAttributes(WadoAttributesCache.INSTANCE
                .getAttributes(seriesService, ref.seriesPk));
        addPart(output,
                new DicomXMLOutput(ref, toBulkDataURI(ref.uri), attrs),
                MediaTypes.APPLICATION_DICOM_XML_TYPE,
                null, ref.sopInstanceUID);
    }

}
