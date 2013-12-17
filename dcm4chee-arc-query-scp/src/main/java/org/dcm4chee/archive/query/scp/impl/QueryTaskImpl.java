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

package org.dcm4chee.archive.query.scp.impl;

import java.sql.SQLException;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.IDWithIssuer;
import org.dcm4che.data.Issuer;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4che.net.Association;
import org.dcm4che.net.Status;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.BasicQueryTask;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.StringUtils;
import org.dcm4chee.archive.conf.QueryParam;
import org.dcm4chee.archive.patient.PatientService;
import org.dcm4chee.archive.query.Query;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
class QueryTaskImpl extends BasicQueryTask {

    private final Query query;
    private final IDWithIssuer[] pids;
    private final String[] patientNames;
    private final Issuer requestedIssuerOfPatientID;
    private final Issuer requestedIssuerOfAccessionNumber;
    private final boolean returnOtherPatientIDs;
    private final boolean returnOtherPatientNames;
    private final boolean skipMatchesWithoutPatientID;

    public QueryTaskImpl(Association as, PresentationContext pc, Attributes rq,
            Attributes keys, IDWithIssuer[] pids, QueryParam queryParam,
            boolean skipMatchesWithoutPatientID, Query query, 
            PatientService queryPatientNamesService)
            throws Exception {
        super(as, pc, rq, keys);
        this.query = query;
        this.pids = pids;
        this.requestedIssuerOfPatientID = StringUtils.maskNull(
                Issuer.fromIssuerOfPatientID(keys),
                queryParam.getDefaultIssuerOfPatientID());
        this.requestedIssuerOfAccessionNumber = StringUtils.maskNull(
                Issuer.valueOf(keys.getNestedDataset(
                        Tag.IssuerOfAccessionNumberSequence)),
                queryParam.getDefaultIssuerOfAccessionNumber());
        this.returnOtherPatientIDs = queryParam.isReturnOtherPatientIDs()
                && keys.contains(Tag.OtherPatientIDsSequence);
        this.returnOtherPatientNames = queryParam.isReturnOtherPatientNames()
                && keys.contains(Tag.OtherPatientNames);
        this.patientNames = returnOtherPatientNames && pids.length > 1 
                ? queryPatientNamesService.patientNamesOf(pids)
                : null;
        this.skipMatchesWithoutPatientID = skipMatchesWithoutPatientID;
     }

    @Override
    protected Attributes adjust(Attributes match) {
        if (match == null)
            return null;

        adjustPatientID(match);
        if (skipMatchesWithoutPatientID && !match.containsValue(Tag.PatientID))
            return null;

        adjustAccessionNumber(match);
        Attributes filtered = new Attributes(match.size());
        filtered.setString(Tag.QueryRetrieveLevel, VR.CS,
                keys.getString(Tag.QueryRetrieveLevel, null));
        filtered.addSelected(match, Tag.SpecificCharacterSet,
                Tag.RetrieveAETitle, Tag.InstanceAvailability);
        filtered.addSelected(match, keys);
        return filtered;
     }

    private void adjustPatientID(Attributes match) {
        IDWithIssuer pid = IDWithIssuer.fromPatientIDWithIssuer(match);
        if (pid == null)
            return;

        if (pids.length > 1) {
            pids[0].toPatientIDWithIssuer(match);
        } else if (requestedIssuerOfPatientID != null
                && !requestedIssuerOfPatientID.matches(pid.getIssuer())) {
            match.setNull(Tag.PatientID, VR.LO);
            requestedIssuerOfPatientID.toIssuerOfPatientID(match);
        }
        if (returnOtherPatientIDs)
            if (pids.length > 0)
                addOtherPatientIDs(match, pids);
            else
                addOtherPatientIDs(match, pid);
        if (returnOtherPatientNames)
            if (patientNames != null)
                match.setString(Tag.OtherPatientNames, VR.PN, patientNames);
            else
                match.setString(Tag.OtherPatientNames, VR.PN,
                        match.getString(Tag.PatientName));
    }

    private static void addOtherPatientIDs(Attributes attrs, IDWithIssuer... pids) {
        Sequence seq = attrs.newSequence(Tag.OtherPatientIDsSequence, pids.length);
        for (IDWithIssuer pid : pids)
            if (pid.getIssuer() != null)
                seq.add(pid.toPatientIDWithIssuer(null));
        if (seq.isEmpty())
            attrs.remove(Tag.OtherPatientIDsSequence);
    }

    private void adjustAccessionNumber(Attributes match) {
        adjustAccessionNumber(match, keys);
        Sequence rqAttrsSeq = match.getSequence(Tag.RequestAttributesSequence);
        if (rqAttrsSeq != null) {
            Attributes rqAttrsKeys = keys.getNestedDataset(Tag.RequestAttributesSequence);
            if (rqAttrsKeys != null && rqAttrsKeys.isEmpty())
                rqAttrsKeys = null;
            for (Attributes rqAttrs : rqAttrsSeq)
                adjustAccessionNumber(rqAttrs, rqAttrsKeys);
        }
    }

    private void adjustAccessionNumber(Attributes match, Attributes keys) {
        if (requestedIssuerOfAccessionNumber == null
                || keys != null && !keys.contains(Tag.AccessionNumber)
                || !match.containsValue(Tag.AccessionNumber))
            return;

        if (!requestedIssuerOfAccessionNumber.matches(Issuer.valueOf(
                match.getNestedDataset(Tag.IssuerOfAccessionNumberSequence)))) {
            match.setNull(Tag.AccessionNumber, VR.SH);
            match.remove(Tag.IssuerOfAccessionNumberSequence);
        }
    }

    @Override
    protected void close() {
         try {
            query.close();
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
//         AuditUtils.logQuery(as, rq, keys);
    }

    @Override
    protected boolean hasMoreMatches() throws DicomServiceException {
        try {
            return query.hasMoreMatches();
        }  catch (Exception e) {
            throw wrapException(Status.UnableToProcess, e);
        }
    }

    @Override
    protected Attributes nextMatch() throws DicomServiceException {
        try {
            return query.nextMatch();
        }  catch (Exception e) {
            throw wrapException(Status.UnableToProcess, e);
        }
    }

    @Override
    protected boolean optionalKeyNotSupported(Attributes match) {
        return query.optionalKeyNotSupported();
    }
}
