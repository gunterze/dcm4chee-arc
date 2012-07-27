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

package org.dcm4chee.archive.query;


import java.util.EnumSet;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Device;
import org.dcm4che.net.QueryOption;
import org.dcm4che.net.Status;
import org.dcm4che.net.pdu.ExtendedNegotiation;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.BasicCFindSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.net.service.QueryRetrieveLevel;
import org.dcm4che.net.service.QueryTask;
import org.dcm4che.util.AttributesValidator;
import org.dcm4chee.archive.conf.ArchiveApplicationEntity;
import org.dcm4chee.archive.conf.ArchiveDevice;
import org.dcm4chee.archive.conf.RejectionNote;
import org.dcm4chee.archive.pix.PIXConsumer;
import org.dcm4chee.archive.query.impl.QueryTaskImpl;
import org.dcm4chee.archive.util.RejectionNotes;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class CFindSCPImpl extends BasicCFindSCP {

    private final String[] qrLevels;
    private final QueryRetrieveLevel rootLevel;
    private final ApplicationEntityCache aeCache;
    private final PIXConsumer pixConsumer;

    public CFindSCPImpl(String sopClass, ApplicationEntityCache aeCache,
            PIXConsumer pixConsumer, String... qrLevels) {
        super(sopClass);
        this.aeCache = aeCache;
        this.pixConsumer = pixConsumer;
        this.qrLevels = qrLevels;
        this.rootLevel = QueryRetrieveLevel.valueOf(qrLevels[0]);
    }

    @Override
    protected QueryTask calculateMatches(Association as, PresentationContext pc,
            Attributes rq, Attributes keys) throws DicomServiceException {
        AttributesValidator validator = new AttributesValidator(keys);
        QueryRetrieveLevel level = QueryRetrieveLevel.valueOf(validator, qrLevels);
        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        ExtendedNegotiation extNeg = as.getAAssociateAC().getExtNegotiationFor(cuid);
        EnumSet<QueryOption> queryOpts = QueryOption.toOptions(extNeg);
        boolean relational = queryOpts.contains(QueryOption.RELATIONAL);
        level.validateQueryKeys(validator, rootLevel, relational);

        ArchiveApplicationEntity ae = (ArchiveApplicationEntity)
                as.getApplicationEntity();
        try {
            QueryParam queryParam = queryParam(as, ae, queryOpts);
            IDWithIssuer pid = IDWithIssuer.pidWithIssuer(keys,
                    queryParam.getDefaultIssuerOfPatientID());
            IDWithIssuer[] pids = pid == null 
                    ? IDWithIssuer.EMPTY
                    : pixConsumer.pixQuery(ae, pid);
            return new QueryTaskImpl(as, pc, rq, keys, level, pids, queryParam);
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }

    private QueryParam queryParam(Association as, ArchiveApplicationEntity ae,
            EnumSet<QueryOption> queryOpts) throws Exception {
        ArchiveDevice dev = ae.getArchiveDevice();
        QueryParam queryParam = new QueryParam();
        queryParam.setFuzzyStr(dev.getFuzzyStr());
        queryParam.setAttributeFilters(dev.getAttributeFilters());
        queryParam.setCombinedDatetimeMatching(queryOpts
                .contains(QueryOption.DATETIME));
        queryParam.setFuzzySemanticMatching(queryOpts
                .contains(QueryOption.FUZZY));
        queryParam.setMatchUnknown(ae.isMatchUnknown());
        queryParam.setRoles(roles(as));
        queryParam.setHideConceptNameCodes(RejectionNotes.codesForAction(
                ae.getRejectionNotes(),
                RejectionNote.Action.HIDE_REJECTION_NOTE));
        queryParam.setHideRejectionCodes(RejectionNotes.codesForAction(
                ae.getRejectionNotes(),
                RejectionNote.Action.HIDE_REJECTED_INSTANCES));
        queryParam.setShowEmptySeries(ae.isShowEmptySeries());
        queryParam.setShowEmptyStudy(ae.isShowEmptyStudy());
        queryParam.setReturnOtherPatientIDs(ae.isReturnOtherPatientIDs());

        ApplicationEntity sourceAE = aeCache.get(as.getRemoteAET());
        if (sourceAE != null) {
            Device sourcDevice = sourceAE.getDevice();
            queryParam.setDefaultIssuerOfPatientID(sourcDevice
                    .getIssuerOfPatientID());
            queryParam.setDefaultIssuerOfAccessionNumber(sourcDevice
                    .getIssuerOfAccessionNumber());
        }
        return queryParam;
    }

    private String[] roles(Association as) {
        // TODO Auto-generated method stub
        return null;
    }
}
