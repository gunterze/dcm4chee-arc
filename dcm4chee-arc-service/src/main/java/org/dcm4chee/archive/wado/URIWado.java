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
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.ejb.EJB;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.UID;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.DicomInputStream.IncludeBulkData;
import org.dcm4che.io.DicomOutputStream;
import org.dcm4che.util.SafeClose;
import org.dcm4che.util.StringUtils;
import org.dcm4chee.archive.dao.SeriesService;
import org.dcm4chee.archive.entity.InstanceFileRef;
import org.dcm4chee.archive.wado.dao.WadoService;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@Path("/wado")
public class URIWado {

    public enum Anonymize { yes }

    public enum Annotation { patient, technique }

    public static final class Strings {
        final String[] values;
        public Strings(String s) {
            values = StringUtils.split(s, ',');
        }
    }

    public static final class ContentTypes {
        final MediaType[] values;
        public ContentTypes(String s) {
            String[] ss = StringUtils.split(s, ',');
            values = new MediaType[ss.length];
            for (int i = 0; i < ss.length; i++)
                values[i] = MediaType.valueOf(ss[i]);
        }
    }

    public static final class Annotations {
        final Annotation[] values;
        public Annotations(String s) {
            String[] ss = StringUtils.split(s, ',');
            values = new Annotation[ss.length];
            for (int i = 0; i < ss.length; i++)
                values[i] = Annotation.valueOf(ss[i]);
        }
    }

    public static final class Region {
        final double left;
        final double top;
        final double right;
        final double bottom;
        public Region(String s) {
            String[] ss = StringUtils.split(s, ',');
            if (ss.length != 4)
                throw new IllegalArgumentException(s);
            left = Double.parseDouble(ss[0]);
            top = Double.parseDouble(ss[1]);
            right = Double.parseDouble(ss[2]);
            bottom = Double.parseDouble(ss[3]);
            if (left < 0. || right > 1. || top < 0. || bottom > 1.
                    || left >= right || top >= bottom)
                throw new IllegalArgumentException(s);
        }
    }

    @EJB
    private SeriesService seriesService;

    @EJB
    private WadoService instanceService;

    @Context
    private HttpServletRequest request;

    @Context
    private HttpHeaders headers;

    @QueryParam("requestType")
    private String requestType;

    @QueryParam("studyUID")
    private String studyUID;

    @QueryParam("seriesUID")
    private String seriesUID;

    @QueryParam("objectUID")
    private String objectUID;

    @QueryParam("contentType")
    private ContentTypes contentType;

    @QueryParam("charset")
    private Strings charset;

    @QueryParam("anonymize")
    private Anonymize anonymize;

    @QueryParam("annotation")
    private Annotations annotation;

    @QueryParam("rows")
    private int rows;

    @QueryParam("columns")
    private int columns;

    @QueryParam("region")
    private Region region;

    @QueryParam("windowCenter")
    private double windowCenter;

    @QueryParam("windowWidth")
    private double windowWidth;

    @QueryParam("frameNumber")
    private int frameNumber;

    @QueryParam("imageQuality")
    private int imageQuality;

    @QueryParam("presentationUID")
    private String presentationUID;

    @QueryParam("presentationSeriesUID")
    private String presentationSeriesUID;

    @QueryParam("transferSyntax")
    private String transferSyntax;

    @GET
    public Response retrieve() throws WebApplicationException {
        checkRequest();
        InstanceFileRef ref =
                instanceService.locate(studyUID, seriesUID, objectUID);
        if (ref == null)
            throw new WebApplicationException(Status.NOT_FOUND);

        Attributes attrs = ref.getAttributes(WadoAttributesCache.INSTANCE
                    .getAttributes(seriesService, ref.seriesPk));

        MediaType mediaType = selectMediaType(
                MediaTypes.supportedMediaTypesOf(ref, attrs));

        if (!isAccepted(mediaType))
            throw new WebApplicationException(Status.NOT_ACCEPTABLE);

        return (mediaType == MediaTypes.APPLICATION_DICOM_TYPE)
                ? retrieveNativeDicomObject(ref.uri, attrs)
                : retrieveRenderedDicomObject(ref.uri, attrs, mediaType);
    }

    private void checkRequest()
            throws WebApplicationException {
        if (!"WADO".equals(requestType))
            throw new WebApplicationException(Status.BAD_REQUEST);
        if (studyUID == null || seriesUID == null || objectUID == null)
            throw new WebApplicationException(Status.BAD_REQUEST);
        boolean applicationDicom = false;
        if (contentType != null) {
            for (MediaType mediaType : contentType.values) {
                if (!isAccepted(mediaType))
                    throw new WebApplicationException(Status.BAD_REQUEST);
                if (MediaTypes.isDicomApplicationType(mediaType))
                    applicationDicom = true;
            }
        }
        if (applicationDicom 
                ? (annotation != null || rows != 0 || columns != 0
                    || region != null || windowCenter != 0 || windowWidth != 0
                    || frameNumber != 0 || imageQuality != 0
                    || presentationUID != null || presentationSeriesUID != null)
                : (anonymize != null || transferSyntax != null 
                    || presentationUID != null && presentationSeriesUID == null))
            throw new WebApplicationException(Status.BAD_REQUEST);
    }

    private boolean isAccepted(MediaType mediaType) {
        for (MediaType accepted : headers.getAcceptableMediaTypes())
            if (mediaType.isCompatible(accepted))
                return true;
        return false;
    }

    private MediaType selectMediaType(List<MediaType> supported) {
        if (contentType != null)
            for (MediaType desiredType : contentType.values)
                for (MediaType supportedType : supported)
                    if (MediaTypes.equalsIgnoreParams(supportedType, desiredType))
                        return supportedType;
        return supported.get(0);
    }

    private Response retrieveNativeDicomObject(final String uri, final Attributes attrs) {
        return Response.ok(new StreamingOutput() {
            
            @Override
            public void write(OutputStream out) throws IOException,
                    WebApplicationException {
                DicomInputStream dis;
                try {
                    dis = new DicomInputStream(
                            new File(new URI(uri)));
                } catch (URISyntaxException e) {
                    throw new WebApplicationException(e);
                }
                try {
                    dis.setIncludeBulkData(IncludeBulkData.LOCATOR);
                    Attributes dataset = dis.readDataset(-1, -1);
                    dataset.addAll(attrs);
                    Attributes fmi = 
                            dataset.createFileMetaInformation(UID.ExplicitVRLittleEndian);
                    @SuppressWarnings("resource")
                    DicomOutputStream dos =
                            new DicomOutputStream(out, UID.ExplicitVRLittleEndian);
                    dos.writeDataset(fmi, dataset);
                } finally {
                    SafeClose.close(dis);
                }
            }
        }, MediaTypes.APPLICATION_DICOM_TYPE).build();
    }

    private Response retrieveRenderedDicomObject(final String uri,
            final Attributes attrs, MediaType mediaType) {
        throw new WebApplicationException(501);
    }


}
