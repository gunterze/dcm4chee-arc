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

import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.QueryOption;
import org.dcm4che.net.Status;
import org.dcm4che.net.pdu.ExtendedNegotiation;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.BasicCFindSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.net.service.QueryRetrieveLevel;
import org.dcm4che.net.service.QueryTask;
import org.dcm4chee.archive.Archive;
import org.dcm4chee.archive.common.IDWithIssuer;
import org.dcm4chee.archive.common.QueryParam;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class CFindSCP extends BasicCFindSCP {

    private final String[] qrLevels;
    private final QueryRetrieveLevel rootLevel;

    public CFindSCP(String sopClass, String... qrLevels) {
        super(sopClass);
        this.qrLevels = qrLevels;
        this.rootLevel = QueryRetrieveLevel.valueOf(qrLevels[0]);
    }

    @Override
    protected QueryTask calculateMatches(Association as, PresentationContext pc,
            Attributes rq, Attributes keys) throws DicomServiceException {
        QueryRetrieveLevel level = QueryRetrieveLevel.valueOf(keys, qrLevels);
        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        ExtendedNegotiation extNeg = as.getAAssociateAC().getExtNegotiationFor(cuid);
        EnumSet<QueryOption> queryOpts = QueryOption.toOptions(extNeg);
        boolean relational = queryOpts.contains(QueryOption.RELATIONAL);
        level.validateQueryKeys(keys, rootLevel, relational);

        ApplicationEntity ae = as.getApplicationEntity();
        try {
            QueryParam queryParam = QueryParam.valueOf(ae, queryOpts,
                    accessControlID(as));
            try {
                queryParam.setDefaultIssuer(
                        Archive.getInstance()
                                .findApplicationEntity(as.getRemoteAET())
                        .getDevice());
            } catch (ConfigurationException e) {
            }
            IDWithIssuer pid = IDWithIssuer.pidWithIssuer(keys,
                    queryParam.getDefaultIssuerOfPatientID());
            IDWithIssuer[] pids = Archive.getInstance().pixQuery(ae, pid);
            return new QueryTaskImpl(as, pc, rq, keys, level, pids, queryParam,
                    rootLevel == QueryRetrieveLevel.PATIENT);
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }

    private String[] accessControlID(Association as) {
        // TODO Auto-generated method stub
        return null;
    }

    public static class PatientRoot extends CFindSCP {
        public PatientRoot() {
            super(UID.PatientRootQueryRetrieveInformationModelFIND,
                    "PATIENT", "STUDY", "SERIES", "IMAGE");
        }
    }

    public static class StudyRoot extends CFindSCP {
        public StudyRoot() {
            super(UID.StudyRootQueryRetrieveInformationModelFIND,
                    "STUDY", "SERIES", "IMAGE");
        }
    }

    public static class PatientStudyOnly extends CFindSCP {
        public PatientStudyOnly() {
            super(UID.PatientStudyOnlyQueryRetrieveInformationModelFINDRetired,
                    "PATIENT", "STUDY");
        }
    }

}
