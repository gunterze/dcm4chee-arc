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
 * Portions created by the Initial Developer are Copyright (C) 2012
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

package org.dcm4chee.archive.mpps;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Issuer;
import org.dcm4che.data.Tag;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Status;
import org.dcm4che.net.service.BasicMPPSSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4chee.archive.conf.ArchiveApplicationEntity;
import org.dcm4chee.archive.mpps.dao.MPPSService;
import org.dcm4chee.archive.store.StoreParam;
import org.dcm4chee.archive.store.Supplements;
import org.dcm4chee.archive.util.BeanLocator;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class MPPSSCP extends BasicMPPSSCP {

    private final ApplicationEntityCache aeCache;
    private final MPPSSCU mppsSCU;
    private final MPPSService mppsService;
    private final IANSCU ianSCU;

    public  MPPSSCP(ApplicationEntityCache aeCache, MPPSSCU mppsSCU,
            IANSCU ianSCU) {
       this.aeCache = aeCache;
       this.mppsSCU = mppsSCU;
       this.ianSCU = ianSCU;
       this.mppsService = BeanLocator.lookup(MPPSService.class);
    }

    @Override
    protected Attributes create(Association as, Attributes rq,
            Attributes rqAttrs, Attributes rsp) throws DicomServiceException {
        String localAET = as.getLocalAET();
        String sourceAET = as.getRemoteAET();
        String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
        ArchiveApplicationEntity ae = (ArchiveApplicationEntity) as.getApplicationEntity();
        try {
            ApplicationEntity sourceAE = aeCache.get(sourceAET);
            if (sourceAE != null)
                Supplements.supplementMPPS(rqAttrs, sourceAE.getDevice());
            mppsService.createPerformedProcedureStep(iuid , rqAttrs, StoreParam.valueOf(ae));
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
        for (String remoteAET : ae.getForwardMPPSDestinations())
            if (matchIssuerOfPatientID(remoteAET, rqAttrs))
                mppsSCU.scheduleForwardMPPS(localAET, remoteAET, iuid, rqAttrs, true, 0, 0);
        return null;
    }

    private boolean matchIssuerOfPatientID(String remoteAET, Attributes rqAttrs) {
        String issuerOfPatientID = rqAttrs.getString(Tag.IssuerOfPatientID);
        if (issuerOfPatientID == null)
            return true;
        ApplicationEntity remoteAE = null;
        try {
            remoteAE = aeCache.get(remoteAET);
        } catch (ConfigurationException e) {
        }
        return remoteAE == null
                || new Issuer(issuerOfPatientID,
                        rqAttrs.getNestedDataset(Tag.IssuerOfPatientIDQualifiersSequence))
                    .matches(remoteAE.getDevice().getIssuerOfPatientID());
    }

    @Override
    protected Attributes set(Association as, Attributes rq, Attributes rqAttrs,
            Attributes rsp) throws DicomServiceException {
        String localAET = as.getLocalAET();
        String iuid = rq.getString(Tag.RequestedSOPInstanceUID);
        ArchiveApplicationEntity ae = (ArchiveApplicationEntity) as.getApplicationEntity();
        PPSWithIAN ppsWithIAN;
        try {
            ppsWithIAN = mppsService.updatePerformedProcedureStep(iuid, rqAttrs,
                    StoreParam.valueOf(ae));
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
        for (String remoteAET : ae.getForwardMPPSDestinations())
            if (matchIssuerOfPatientID(remoteAET, ppsWithIAN.pps.getPatient().getAttributes()))
                mppsSCU.scheduleForwardMPPS(localAET, remoteAET, iuid, rqAttrs, false, 0, 0);
        if (ppsWithIAN.ian != null)
            for (String remoteAET : ae.getIANDestinations())
                ianSCU.scheduleIAN(localAET, remoteAET, ppsWithIAN.ian, 0, 0);
        return null;
    }

}
