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

package org.dcm4chee.archive.retrieve;

import static org.dcm4che.net.service.BasicRetrieveTask.Service.C_GET;

import java.util.EnumSet;
import java.util.List;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.QueryOption;
import org.dcm4che.net.Status;
import org.dcm4che.net.pdu.ExtendedNegotiation;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.BasicCGetSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.net.service.InstanceLocator;
import org.dcm4che.net.service.QueryRetrieveLevel;
import org.dcm4che.net.service.RetrieveTask;
import org.dcm4che.util.AttributesValidator;
import org.dcm4chee.archive.conf.ArchiveApplicationEntity;
import org.dcm4chee.archive.pix.PIXConsumer;
import org.dcm4chee.archive.query.util.IDWithIssuer;
import org.dcm4chee.archive.query.util.QueryParam;
import org.dcm4chee.archive.retrieve.dao.RetrieveService;
import org.dcm4chee.archive.util.BeanLocator;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class CGetSCP extends BasicCGetSCP {

    private final String[] qrLevels;
    private final QueryRetrieveLevel rootLevel;
    private boolean withoutBulkData;
    private final ApplicationEntityCache aeCache;
    private final PIXConsumer pixConsumer;
    private final RetrieveService retrieveService;

    public CGetSCP(String sopClass,
            ApplicationEntityCache aeCache,
            PIXConsumer pixConsumer,
            String... qrLevels) {
        super(sopClass);
        this.qrLevels = qrLevels;
        this.rootLevel = QueryRetrieveLevel.valueOf(qrLevels[0]);
        this.aeCache = aeCache;
        this.pixConsumer = pixConsumer;
        this.retrieveService = BeanLocator.lookup(RetrieveService.class);
    }

    public CGetSCP withoutBulkData(boolean withoutBulkData) {
        this.withoutBulkData = withoutBulkData;
        return this;
    }

    @Override
    protected RetrieveTask calculateMatches(Association as, PresentationContext pc,
            Attributes rq, Attributes keys) throws DicomServiceException {
        AttributesValidator validator = new AttributesValidator(keys);
        QueryRetrieveLevel level = QueryRetrieveLevel.valueOf(validator, qrLevels);
        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        ExtendedNegotiation extNeg = as.getAAssociateAC().getExtNegotiationFor(cuid);
        EnumSet<QueryOption> queryOpts = QueryOption.toOptions(extNeg);
        boolean relational = queryOpts.contains(QueryOption.RELATIONAL);
        level.validateRetrieveKeys(validator, rootLevel, relational);

        ArchiveApplicationEntity ae = (ArchiveApplicationEntity) as.getApplicationEntity();
        try {
            final ApplicationEntity sourceAE = aeCache.get(as.getRemoteAET());
            QueryParam queryParam = QueryParam.valueOf(ae, queryOpts, sourceAE, roles());
            List<InstanceLocator> matches = calculateMatches(rq, keys, queryParam);
            RetrieveTaskImpl retrieveTask = new RetrieveTaskImpl(
                    C_GET, as, pc, rq, matches,
                    pixConsumer, retrieveService, withoutBulkData);
            if (sourceAE != null)
                retrieveTask.setDestinationDevice(sourceAE.getDevice());
            retrieveTask.setSendPendingRSP(ae.isSendPendingCGet());
            retrieveTask.setReturnOtherPatientIDs(ae.isReturnOtherPatientIDs());
            retrieveTask.setReturnOtherPatientNames(ae.isReturnOtherPatientNames());
            return retrieveTask;
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }

    private List<InstanceLocator> calculateMatches(Attributes rq,
            Attributes keys, QueryParam queryParam) throws DicomServiceException {
        try {
            IDWithIssuer pid = IDWithIssuer.pidWithIssuer(keys,
                    queryParam.getDefaultIssuerOfPatientID());
            IDWithIssuer[] pids = pid != null ? new IDWithIssuer[] { pid } : null;
            return retrieveService.calculateMatches(pids, keys, queryParam);
        }  catch (Exception e) {
            throw new DicomServiceException(Status.UnableToCalculateNumberOfMatches, e);
        }
    }

    private String[] roles() {
        // TODO Auto-generated method stub
        return null;
    }

}
