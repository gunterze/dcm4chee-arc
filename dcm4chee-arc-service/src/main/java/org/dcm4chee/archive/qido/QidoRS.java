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
package org.dcm4chee.archive.qido;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

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
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import javax.xml.transform.Templates;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.ElementDictionary;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4che.io.SAXTransformer;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Device;
import org.dcm4che.net.QueryOption;
import org.dcm4che.util.StringUtils;
import org.dcm4chee.archive.Archive;
import org.dcm4chee.archive.common.IDWithIssuer;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.query.dao.QueryService;
import org.dcm4chee.archive.util.BeanLocator;
import org.dcm4chee.archive.wado.MediaTypes;
import org.jboss.resteasy.plugins.providers.multipart.MultipartRelatedOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@Path("/qido-rs/{AETitle}")
public class QidoRS {

    private static final Logger LOG = LoggerFactory.getLogger(QidoRS.class);

    private static ElementDictionary DICT =
            ElementDictionary.getStandardElementDictionary();

    private volatile static Templates jsonTpls;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Context
    private HttpHeaders headers;

    @PathParam("AETitle")
    private String aet;

    @QueryParam("fuzzymatching")
    private boolean fuzzymatching;

    @QueryParam("offset")
    private int skipResults;

    @QueryParam("limit")
    private int maximumResults;

    @QueryParam("includefield")
    private List<String> includefield;

    private final Attributes keys = new Attributes(64);

    private boolean includeAll;

    private org.dcm4chee.archive.common.QueryParam queryParam;

    private QueryService queryService;

    private IDWithIssuer[] pids;

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

    @GET
    @Path("/studies")
    @Produces("multipart/related")
    public Response searchForStudiesXML() {
        return searchForStudies(Result.DICOM_XML);
    }

    @GET
    @Path("/studies")
    @Produces("application/json")
    public Response searchForStudiesJSON() {
        return searchForStudies(Result.JSON);
    }

    @GET
    @Path("/series")
    @Produces("multipart/related")
    public Response searchForSeriesXML() {
        return searchForSeries(Result.DICOM_XML);
    }

    @GET
    @Path("/series")
    @Produces("application/json")
    public Response searchForSeriesJSON() {
        return searchForSeries(Result.JSON);
    }

    @GET
    @Path("/instances")
    @Produces("multipart/related")
    public Response searchForInstancesXML() {
        return searchForInstances(Result.DICOM_XML);
    }

    @GET
    @Path("/instances")
    @Produces("application/json")
    public Response searchForInstancesJSON() {
        return searchForInstances(Result.JSON);
    }

    private Response searchForStudies(Result result) {
        addStudyIncludeFields();
        init();
        try {
            queryService.findStudies(pids, keys, queryParam);
            return result.response(this);
        } finally {
            cleanup();
        }
    }

    private Response searchForSeries(Result result) {
        addStudyIncludeFields();
        addSeriesIncludeFields();
        init();
        try {
            queryService.findSeries(pids, keys, queryParam);
            return result.response(this);
        } finally {
            cleanup();
        }
    }

    private Response searchForInstances(Result result) {
        addStudyIncludeFields();
        addSeriesIncludeFields();
        addInstanceIncludeFields();
        init();
        try {
            queryService.findInstances(pids, keys, queryParam);
            return result.response(this);
        } finally {
            cleanup();
        }
    }

    private void addStudyIncludeFields() {
        keys.setNull(Tag.StudyDate, VR.DA);
        keys.setNull(Tag.StudyTime, VR.TM);
        keys.setNull(Tag.AccessionNumber, VR.SH);
        keys.setNull(Tag.ModalitiesInStudy, VR.CS);
        keys.setNull(Tag.ReferringPhysicianName, VR.PN);
        keys.setNull(Tag.PatientName, VR.PN);
        keys.setNull(Tag.PatientID, VR.LO);
        keys.setNull(Tag.PatientBirthDate, VR.DA);
        keys.setNull(Tag.PatientSex, VR.CS);
        keys.setNull(Tag.StudyID, VR.SH);
        keys.setNull(Tag.StudyInstanceUID, VR.UI);
        keys.setNull(Tag.NumberOfStudyRelatedSeries, VR.IS);
        keys.setNull(Tag.NumberOfStudyRelatedInstances, VR.IS);
    }

    private void addSeriesIncludeFields() {
        keys.setNull(Tag.Modality, VR.CS);
        keys.setNull(Tag.SeriesDescription, VR.LO);
        keys.setNull(Tag.SeriesNumber, VR.IS);
        keys.setNull(Tag.SeriesInstanceUID, VR.UI);
        keys.setNull(Tag.NumberOfSeriesRelatedInstances, VR.IS);
        keys.setNull(Tag.PerformedProcedureStepStartDate, VR.DA);
        keys.setNull(Tag.PerformedProcedureStepStartTime, VR.TM);
        keys.setNull(Tag.RequestAttributesSequence, VR.SQ);
    }

    private void addInstanceIncludeFields() {
        keys.setNull(Tag.SOPClassUID, VR.UI);
        keys.setNull(Tag.SOPInstanceUID, VR.UI);
        keys.setNull(Tag.InstanceNumber, VR.IS);
        keys.setNull(Tag.Rows, VR.US);
        keys.setNull(Tag.Columns, VR.US);
        keys.setNull(Tag.BitsAllocated, VR.US);
        keys.setNull(Tag.NumberOfFrames, VR.IS);
    }

    private void init() {
        List<MediaType> acceptableMediaTypes = headers.getAcceptableMediaTypes();
        LOG.info("{} >> QIDO-RS[{}?{}, Accept={}]", new Object[] {
                this,
                request.getRequestURL(),
                request.getQueryString(),
                acceptableMediaTypes});

        Device device = Archive.getInstance().getDevice();
        ApplicationEntity ae = device.getApplicationEntity(aet);
        if (ae == null || !ae.isInstalled()
                || (ae.getAEExtension(ArchiveAEExtension.class)) == null)
            throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);

        includefield();

        for (Map.Entry<String, List<String>> qParam
                : uriInfo.getQueryParameters().entrySet()) {
            String name = qParam.getKey();
            if (isDicomAttribute(name))
                dicomAttribute(name, qParam.getValue());
        }

        queryService = BeanLocator.lookup(QueryService.class);
        queryParam = org.dcm4chee.archive.common.QueryParam.valueOf(
                ae, queryOpts(), accessControlIDs());
        IDWithIssuer pid = IDWithIssuer.pidWithIssuer(keys,
                queryParam.getDefaultIssuerOfPatientID());
        this.pids = Archive.getInstance().pixQuery(ae, pid);
    }

    private static boolean isDicomAttribute(String name) {
        switch (name.charAt(0)) {
        case 'f':
            return !name.equals("fuzzymatching");
        case 'l':
            return !name.equals("limit");
        case 'o':
            return !name.equals("offset");
        case 'i':
            return !name.equals("includefield");
        }
        return true;
    }

    private EnumSet<QueryOption> queryOpts() {
        EnumSet<QueryOption> queryOpts = EnumSet.of(
                QueryOption.RELATIONAL,
                QueryOption.DATETIME,
                QueryOption.TIMEZONE);
        if (fuzzymatching)
            queryOpts.add(QueryOption.FUZZY);
        return queryOpts ;
    }

    private String[] accessControlIDs() {
        // TODO Auto-generated method stub
        return null;
    }

    private void includefield() {
        for (String attrPath : includefield) {
            if (attrPath.equals("all")) {
                includeAll = true;
                return;
            }
            int[] tagPath = parseTagPath(attrPath);
            int tag = tagPath[tagPath.length-1];
            nestedKeys(tagPath).setNull(tag, DICT.vrOf(tag));
        }
    }

    private void dicomAttribute(String attrPath, List<String> values) {
        int[] tagPath = parseTagPath(attrPath);
        int tag = tagPath[tagPath.length-1];
        nestedKeys(tagPath).setString(tag, DICT.vrOf(tag),
                values.toArray(new String[values.size()]));
    }

    private Attributes nestedKeys(int[] tags) {
        Attributes item = keys;
        for (int i = 0; i < tags.length-1; i++) {
            int tag = tags[i];
            Sequence sq = item.getSequence(tag);
            if (sq == null)
                sq = item.newSequence(tag, 1);
            if (sq.isEmpty())
                sq.add(new Attributes());
            item = sq.get(0);
        }
        return item;
    }

    private static int[] parseTagPath(String attrPath) {
        return parseTagPath(StringUtils.split(attrPath, '.'));
    }

    private static int[] parseTagPath(String[] attrPath) {
        int[] tags = new int[attrPath.length];
        for (int i = 0; i < tags.length; i++)
            tags[i] = parseTag(attrPath[i]);
        return tags;
    }

    private static int parseTag(String tagOrKeyword) {
        try {
            return Integer.parseInt(tagOrKeyword, 16);
        } catch (IllegalArgumentException e) {
            int tag = DICT.tagForKeyword(tagOrKeyword);
            if (tag == -1)
                throw new IllegalArgumentException(tagOrKeyword);
            return tag;
        }
    }

    private void cleanup() {
        if (queryService != null)
            queryService.close();
    }

    private enum Result {
        DICOM_XML {
            @Override
            Response response(QidoRS service) {
                return service.writeXML();
            }
        },
        JSON {
            @Override
            Response response(QidoRS service) {
                return service.writeJSON();
            }
        };
        
        abstract Response response(QidoRS service);
    }

    private Response writeXML() {
        MultipartRelatedOutput output = new MultipartRelatedOutput();
        int count = 0;
        while (queryService.hasMoreMatches()) {
            final Attributes match = filter(queryService.nextMatch());
            final int partNumber = ++count;
            output.addPart(new StreamingOutput() {

                @Override
                public void write(OutputStream out) throws IOException,
                        WebApplicationException {
                    LOG.info("{} << {}:QIDO-RS[Content-Type=application/dicom+xml]",
                            QidoRS.this, partNumber);
                    try {
                        SAXTransformer.getSAXWriter(new StreamResult(out)).write(match);
                    } catch (Exception e) {
                        throw new WebApplicationException(e);
                    }
                }},
                MediaTypes.APPLICATION_DICOM_XML_TYPE);
        }
        return Response.ok().entity(output).build();
    }

    private Response writeJSON() {
        final ArrayList<Attributes> matches = new ArrayList<Attributes>();
        while (queryService.hasMoreMatches()) {
            matches.add(filter(queryService.nextMatch()));
        }
        StreamingOutput output = new StreamingOutput(){

            @Override
            public void write(OutputStream out) throws IOException {
                for (int i = 0, n=matches.size(); i < n; i++) {
                    out.write(i == 0 ? '[' : ',');
                    LOG.info("{} << {}:QIDO-RS[Content-Type=application/json]",
                            QidoRS.this, i+1);
                     try {
                        SAXTransformer.getSAXWriter(jsonTpls(), new StreamResult(out))
                            .write(matches.get(i));
                    } catch (Exception e) {
                        throw new WebApplicationException(e);
                    }
                }
                out.write(']');
            }
        };
        return Response.ok().entity(output).build();
    }

    private static Templates jsonTpls() throws Exception {
        Templates jsonTpls0 = jsonTpls;
        if (jsonTpls == null)
            jsonTpls = jsonTpls0 = SAXTransformer.newTemplates(new StreamSource(
                    QidoRS.class.getResource("json_compact.xsl").toString()));
        return jsonTpls0;
    }

    private Attributes filter(Attributes match) {
        if (includeAll)
            return match;

        Attributes filtered = new Attributes(match.size());
        filtered.addSelected(match, Tag.SpecificCharacterSet,
                Tag.RetrieveAETitle, Tag.InstanceAvailability);
        filtered.addSelected(match, keys);
        return filtered;
    }

}
