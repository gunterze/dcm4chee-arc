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
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.BulkData;
import org.dcm4che.data.Fragments;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.imageio.codec.Decompressor;
import org.dcm4che.imageio.codec.TransferSyntaxType;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.DicomInputStream.IncludeBulkData;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Device;
import org.dcm4che.util.SafeClose;
import org.dcm4che.util.StringUtils;
import org.dcm4chee.archive.Archive;
import org.dcm4chee.archive.dao.SeriesService;
import org.dcm4chee.archive.entity.InstanceFileRef;
import org.dcm4chee.archive.wado.dao.WadoService;
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

    private List<MediaType> mediaTypes;

    private String name;

    @Override
    public String toString() {
        if (name == null) {
            if (request == null)
                return super.toString();

            name = request.getRemoteHost() + ':' + request.getRemotePort();
        }
        return name;
    }

    private String toBulkDataURI(String uri) {
        return uriInfo.getBaseUri() + "wado/" + aet + "/bulkdata/" + uri;
    }


    private void init() {
        List<MediaType> acceptableMediaTypes = headers.getAcceptableMediaTypes();
        LOG.info("{} >> WADO-RS[{}, Accept={}]", new Object[] {
                this,
                request.getRequestURL(),
                acceptableMediaTypes});

        Device device = Archive.getInstance().getDevice();
        ApplicationEntity ae = device.getApplicationEntity(aet);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);

        this.mediaTypes = new ArrayList<MediaType>(acceptableMediaTypes.size());
        for (MediaType mediaType : acceptableMediaTypes)
            if (mediaType.isWildcardType())
                mediaTypes.add(mediaType);
            else if (mediaType.isCompatible(MediaTypes.MULTIPART_RELATED_TYPE))
                try {
                    mediaTypes.add(
                            MediaType.valueOf(mediaType.getParameters().get("type")));
                } catch (IllegalArgumentException e) {
                    throw new WebApplicationException(Status.BAD_REQUEST);
                }
    }

    private String selectDicomTransferSyntaxes(String ts) {
        String littleEndianTS = null;
        for (MediaType mediaType : mediaTypes) {
            if (mediaType.isCompatible(MediaTypes.APPLICATION_DICOM_TYPE)) {
                String ts1 = mediaType.getParameters().get("transfer-syntax");
                if (ts1 == null || ts1.equals(ts))
                    return ts;

                if (littleEndianTS == null
                        && (ts1.equals(UID.ExplicitVRLittleEndian)
                         || ts1.equals(UID.ImplicitVRLittleEndian)))
                    littleEndianTS = ts1;
            }
        }
        return (littleEndianTS != null
                && TransferSyntaxType.forUID(ts) != TransferSyntaxType.MPEG)
                    ? littleEndianTS
                    : null;
    }

    private MediaType selectBulkdataMediaTypeForTransferSyntax(String ts) {
        MediaType requiredMediaType = null;
        try {
            requiredMediaType = MediaTypes.forTransferSyntax(ts);
        } catch (IllegalArgumentException e) {}
        if (requiredMediaType == null)
            return null;

        boolean defaultTS = !requiredMediaType.getParameters()
                .containsKey("transfer-syntax");
        for (MediaType mediaType : mediaTypes) {
            if (mediaType.isWildcardType())
                return requiredMediaType;

            if (mediaType.isCompatible(requiredMediaType)) {
                String ts1 = mediaType.getParameters().get("transfer-syntax");
                if (ts1 == null ? defaultTS : ts1.equals(ts))
                    return requiredMediaType;
            }
        }
        return (!MediaTypes.isMultiframeMediaType(requiredMediaType)
                    && isAccepted(MediaType.APPLICATION_OCTET_STREAM_TYPE))
                ? MediaType.APPLICATION_OCTET_STREAM_TYPE
                : null;
    }

    private boolean isApplicationDicomPreferred() {
        return mediaTypes.get(0).isCompatible(MediaTypes.APPLICATION_DICOM_TYPE);
    }

    private boolean isAccepted(MediaType type) {
        for (MediaType mediaType : mediaTypes) {
            if (mediaType.isCompatible(type))
                return true;
        }
        return false;
    }

    @GET
    @Path("/studies/{StudyInstanceUID}")
    @Produces("multipart/related")
    public Response retrieveStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID) {
        init();
        return retrieve(wadoService.locate(studyInstanceUID));
    }

    @GET
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}")
    @Produces("multipart/related")
    public Response retrieveSeries(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID) {
        init();
        return retrieve(wadoService.locate(studyInstanceUID, seriesInstanceUID));
    }

    @GET
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/{SOPInstanceUID}")
    @Produces("multipart/related")
    public Response retrieveInstance(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID,
            @PathParam("SOPInstanceUID") String sopInstanceUID) {
        init();
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
        init();
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
            @QueryParam("transferSyntax") String transferSyntax,
            @QueryParam("offset") int offset,
            @QueryParam("length") int length) {

        init();
        return (length <= 0)
                ? retrievePixelData(bulkDataURI)
                : retrieveBulkData(
                        new BulkData(bulkDataURI, transferSyntax, offset, length));
        
    }

    @GET
    @Path("/studies/{StudyInstanceUID}/metadata")
    @Produces("multipart/related")
    public Response retrieveMetadata(
            @PathParam("StudyInstanceUID") String studyInstanceUID) {
        init();
        return retrieveMetadata(wadoService.locate(studyInstanceUID));
    }

    private Response retrieve(List<InstanceFileRef> refs) {
        if (refs.isEmpty())
            throw new WebApplicationException(Status.NOT_FOUND);

        MultipartRelatedOutput output = new MultipartRelatedOutput();
        int failed = 0;
        if (isApplicationDicomPreferred()) {
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

    private Response retrieve(InstanceFileRef ref) {
        if (ref == null)
            throw new WebApplicationException(Status.NOT_FOUND);

        MultipartRelatedOutput output = new MultipartRelatedOutput();
        int status = STATUS_OK;
        if (isApplicationDicomPreferred()) {
            addDicomObjectTo(ref, output);
        } else {
            status = addPixelDataTo(ref.uri, output);
        }

        if (output.getParts().isEmpty())
            throw new WebApplicationException(Status.NOT_ACCEPTABLE);

        return Response.status(status).entity(output).build();
    }

    private Response retrievePixelData(String fileURI, int... frames) {
        MultipartRelatedOutput output = new MultipartRelatedOutput();
        
        int status = addPixelDataTo(fileURI, output, frames);

        if (output.getParts().isEmpty())
            throw new WebApplicationException(Status.NOT_ACCEPTABLE);

        return Response.status(status).entity(output).build();
    }

    private Response retrieveBulkData(BulkData bulkData) {
        if (!isAccepted(MediaType.APPLICATION_OCTET_STREAM_TYPE))
            throw new WebApplicationException(Status.NOT_ACCEPTABLE);

        String contentLocation = uriInfo.getRequestUri().toString();
        MultipartRelatedOutput output = new MultipartRelatedOutput();
        OutputPart part = output.addPart(new BulkDataOutput(bulkData,
                    MediaType.APPLICATION_OCTET_STREAM_TYPE, contentLocation,
                    LOG, this, output.getParts().size()+1),
                MediaType.APPLICATION_OCTET_STREAM_TYPE);
        part.getHeaders().add(CONTENT_LOCATION, contentLocation);

        return Response.ok(output).build();
    }


    private Response retrieveMetadata(List<InstanceFileRef> refs) {
        if (refs.isEmpty())
            throw new WebApplicationException(Status.NOT_FOUND);

        if (!isAccepted(MediaTypes.APPLICATION_DICOM_XML_TYPE))
            throw new WebApplicationException(Status.NOT_ACCEPTABLE);

        MultipartRelatedOutput output = new MultipartRelatedOutput();
        for (InstanceFileRef ref : refs)
            addMetadataTo(ref, output);

        return Response.ok(output).build();
    }

    private boolean addDicomObjectTo(final InstanceFileRef ref,
            MultipartRelatedOutput output) {
        String tsuid = selectDicomTransferSyntaxes(ref.transferSyntaxUID);
        if (tsuid == null) {
            return false;
        }
        Attributes attrs = ref.getAttributes(WadoAttributesCache.INSTANCE
                .getAttributes(seriesService, ref.seriesPk));
        MediaType mediaType = MediaType.valueOf(
                "application/dicom;transfer-syntax=" + tsuid);
        output.addPart(
                new DicomObjectOutput(ref, attrs, tsuid, mediaType, LOG, this,
                        output.getParts().size()+1),
                mediaType);
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
                LOG.info("Failed to retrieve Pixel Data of Instance[uid={}]: Requested Transfer Syntax not supported",
                        iuid);
                return STATUS_NOT_ACCEPTABLE;
            }

            if (MediaTypes.isMultiframeMediaType(mediaType) && frameList.length > 0) {
                LOG.info("Failed to retrieve Frame Pixel Data of Instance[uid={}]: Not supported for Content-Type={}",
                        iuid, mediaType);
                return STATUS_NOT_ACCEPTABLE;
            }

            Attributes ds = dis.readDataset(-1, -1);
            Object pixeldata = ds.getValue(Tag.PixelData);
            if (pixeldata == null) {
                LOG.info("Failed to retrieve Pixel Data of Instance[uid={}]: Not an image",
                        iuid);
                return STATUS_NOT_ACCEPTABLE;
            }

            int frames = ds.getInt(Tag.NumberOfFrames, 1);
            int[] adjustedFrameList = adjustFrameList(iuid, frameList, frames);

            String bulkDataURI = toBulkDataURI(fileURI);
            if (pixeldata instanceof Fragments) {
                Fragments bulkData = (Fragments) pixeldata;
                if (mediaType == MediaType.APPLICATION_OCTET_STREAM_TYPE) {
                    addDecompressedPixelDataTo(new Decompressor(ds, dis.getTransferSyntax()),
                            adjustedFrameList, output, bulkDataURI);
                } else {
                    addCompressedPixelDataTo(bulkData, frames,
                            adjustedFrameList, output, mediaType, bulkDataURI);
                }
            } else {
                BulkData bulkData = (BulkData) pixeldata;
                addUncompressedPixelDataTo(bulkData, ds,
                        adjustedFrameList, output, bulkDataURI);
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
        LOG.info("Failed to retrieve Frames {} of Pixel Data of Instance[uid={}]: NumberOfFrames={}",
                new Object[] { Arrays.toString(skipped), iuid, frames });
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
            int[] frameList, MultipartRelatedOutput output, String bulkDataURI) {
        if (frameList.length == 0) {
            OutputPart part = output.addPart(
                new DecompressedPixelDataOutput(decompressor, -1,
                        MediaType.APPLICATION_OCTET_STREAM_TYPE,
                        bulkDataURI, LOG, this, output.getParts().size()+1),
                MediaType.APPLICATION_OCTET_STREAM_TYPE);
            part.getHeaders().add(CONTENT_LOCATION, bulkDataURI);
        } else for (int frame : frameList) {
            String contentLocation = bulkDataURI + "/frames/" + frame;
            OutputPart part = output.addPart(
                new DecompressedPixelDataOutput(decompressor, frame-1, 
                        MediaType.APPLICATION_OCTET_STREAM_TYPE,
                        contentLocation, LOG, this, output.getParts().size()+1),
                MediaType.APPLICATION_OCTET_STREAM_TYPE);
            part.getHeaders().add(CONTENT_LOCATION, contentLocation);
        }
    }

    private void addCompressedPixelDataTo(Fragments fragments, int frames,
            int[] adjustedFrameList, MultipartRelatedOutput output,
            MediaType mediaType, String bulkDataURI) {
        if (frames == 1 || MediaTypes.isMultiframeMediaType(mediaType)) {
            OutputPart part = output.addPart(
                    new CompressedPixelDataOutput(
                            fragments, mediaType, bulkDataURI, LOG, this,
                            output.getParts().size()+1),
                    mediaType);
            part.getHeaders().add(CONTENT_LOCATION, bulkDataURI);
        } else if (adjustedFrameList.length == 0) {
            for (int frame = 1; frame <= frames; frame++) {
                String contentLocation = bulkDataURI + "/frames/" + frame;
                OutputPart part = output.addPart(
                        new BulkDataOutput(
                                (BulkData) fragments.get(frame),
                                mediaType, contentLocation, LOG, this,
                                output.getParts().size()+1),
                        mediaType);
                part.getHeaders().add(CONTENT_LOCATION, contentLocation);
            }
        } else {
            for (int frame : adjustedFrameList) {
                String contentLocation = bulkDataURI + "/frames/" + frame;
                OutputPart part = output.addPart(
                        new BulkDataOutput(
                                (BulkData) fragments.get(frame),
                                mediaType, contentLocation, LOG, this,
                                output.getParts().size()+1),
                        mediaType);
                part.getHeaders().add(CONTENT_LOCATION,
                        contentLocation);
            }
        }
    }

    private void addUncompressedPixelDataTo(BulkData bulkData, Attributes ds,
            int[] adjustedFrameList, MultipartRelatedOutput output,
            String bulkDataURI) {
        if (adjustedFrameList.length == 0) {
            OutputPart part = output.addPart(
                    new BulkDataOutput(bulkData,
                            MediaType.APPLICATION_OCTET_STREAM_TYPE,
                            bulkDataURI, LOG, this,
                            output.getParts().size()+1),
                    MediaType.APPLICATION_OCTET_STREAM_TYPE);
            part.getHeaders().add(CONTENT_LOCATION, bulkDataURI);
        } else {
            int rows = ds.getInt(Tag.Rows, 0);
            int cols = ds.getInt(Tag.Columns, 0);
            int samples = ds.getInt(Tag.SamplesPerPixel, 0);
            int bitsAllocated = ds.getInt(Tag.BitsAllocated, 8);
            int frameLength = rows * cols * samples * (bitsAllocated>>>3);
            for (int frame : adjustedFrameList) {
                String contentLocation = bulkDataURI + "/frames/" + frame;
                OutputPart part = output.addPart(
                    new BulkDataOutput(new BulkData(
                                bulkData.uri,
                                bulkData.transferSyntax,
                                bulkData.offset + (frame-1) * frameLength,
                                frameLength),
                            MediaType.APPLICATION_OCTET_STREAM_TYPE,
                            contentLocation, LOG, this,
                            output.getParts().size()+1),
                    MediaType.APPLICATION_OCTET_STREAM_TYPE);
                part.getHeaders().add(CONTENT_LOCATION, contentLocation);
            }
        }
    }

    private void addMetadataTo(InstanceFileRef ref,
            MultipartRelatedOutput output) {
        Attributes attrs = ref.getAttributes(WadoAttributesCache.INSTANCE
                .getAttributes(seriesService, ref.seriesPk));
        output.addPart(new DicomXMLOutput(ref, toBulkDataURI(ref.uri), attrs,
                MediaTypes.APPLICATION_DICOM_XML_TYPE, LOG, this,
                output.getParts().size()+1),
                MediaTypes.APPLICATION_DICOM_XML_TYPE);
    }

}
