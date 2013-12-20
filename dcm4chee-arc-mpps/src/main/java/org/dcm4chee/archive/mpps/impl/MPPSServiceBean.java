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

package org.dcm4chee.archive.mpps.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.net.Status;
import org.dcm4che.net.service.BasicMPPSSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4chee.archive.conf.Entity;
import org.dcm4chee.archive.conf.StoreParam;
import org.dcm4chee.archive.entity.Instance;
import org.dcm4chee.archive.entity.Patient;
import org.dcm4chee.archive.entity.PerformedProcedureStep;
import org.dcm4chee.archive.entity.SOPInstanceReference;
import org.dcm4chee.archive.entity.ScheduledProcedureStep;
import org.dcm4chee.archive.entity.Utils;
import org.dcm4chee.archive.mpps.MPPSService;
import org.dcm4chee.archive.mpps.SOPClassMismatchException;
import org.dcm4chee.archive.patient.PatientService;
import org.dcm4chee.archive.request.RequestService;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
@Stateless
public class MPPSServiceBean implements MPPSService {

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @EJB
    private PatientService patService;

    @EJB
    private RequestService requestService;

    @Override
    public PerformedProcedureStep createPerformedProcedureStep(
            String sopInstanceUID, Attributes attrs, StoreParam storeParam)
                    throws DicomServiceException {
        try {
            find(sopInstanceUID);
            throw new DicomServiceException(Status.DuplicateSOPinstance)
                .setUID(Tag.AffectedSOPInstanceUID, sopInstanceUID);
        } catch (NoResultException e) {}
        Patient patient = patService.findUniqueOrCreatePatient(
                storeParam.getAttributeFilter(Entity.Patient),
                storeParam.getFuzzyStr(),
                attrs,
                true,
                true);
        PerformedProcedureStep mpps = new PerformedProcedureStep();
        mpps.setSopInstanceUID(sopInstanceUID);
        mpps.setAttributes(attrs,
                storeParam.getAttributeFilter(Entity.PerformedProcedureStep));
        mpps.setScheduledProcedureSteps(
                getScheduledProcedureSteps(
                        attrs.getSequence(Tag.ScheduledStepAttributesSequence),
                        patient,
                        storeParam));
        mpps.setPatient(patient);
        em.persist(mpps);
        return mpps;
    }

    @Override
    public PerformedProcedureStep updatePerformedProcedureStep(String sopInstanceUID,
            Attributes modified, StoreParam storeParam) throws DicomServiceException {
        PerformedProcedureStep pps;
        try {
            pps = find(sopInstanceUID);
        } catch (NoResultException e) {
            throw new DicomServiceException(Status.NoSuchObjectInstance)
                .setUID(Tag.AffectedSOPInstanceUID, sopInstanceUID);
        }
        if (pps.getStatus() != PerformedProcedureStep.Status.IN_PROGRESS)
            BasicMPPSSCP.mayNoLongerBeUpdated();

        Attributes attrs = pps.getAttributes();
        attrs.addAll(modified);
        pps.setAttributes(attrs,
                storeParam.getAttributeFilter(Entity.PerformedProcedureStep));
        if (pps.getStatus() != PerformedProcedureStep.Status.IN_PROGRESS) {
            if (!attrs.containsValue(Tag.PerformedSeriesSequence))
                throw new DicomServiceException(Status.MissingAttributeValue)
                        .setAttributeIdentifierList(Tag.PerformedSeriesSequence);
        }
        return pps;
    }

    private PerformedProcedureStep find(String sopInstanceUID) {
        return em.createNamedQuery(
                PerformedProcedureStep.FIND_BY_SOP_INSTANCE_UID,
                PerformedProcedureStep.class)
             .setParameter(1, sopInstanceUID)
             .getSingleResult();
    }

    private Collection<ScheduledProcedureStep> getScheduledProcedureSteps(
            Sequence ssaSeq, Patient patient, StoreParam storeParam) {
        ArrayList<ScheduledProcedureStep> list =
                new ArrayList<ScheduledProcedureStep>(ssaSeq.size());
        for (Attributes ssa : ssaSeq) {
            if (ssa.containsValue(Tag.ScheduledProcedureStepID)
                    && ssa.containsValue(Tag.RequestedProcedureID)
                    && ssa.containsValue(Tag.AccessionNumber)) {
                ScheduledProcedureStep sps =
                        requestService.findOrCreateScheduledProcedureStep(
                                ssa, patient, storeParam);
                list.add(sps);
            }
        }
        return list;
    }

    @Override
    public List<Attributes> checkInstanceAvailability(String ppsInstanceUID,
            Attributes attrs) throws SOPClassMismatchException {
        Sequence perfSeriesSeq = attrs.getSequence(Tag.PerformedSeriesSequence);
        if (perfSeriesSeq == null || perfSeriesSeq.isEmpty())
            return Collections.emptyList();

        int numS = perfSeriesSeq.size();
        List<Attributes> ians = new ArrayList<Attributes>(numS);
        for (Attributes perfSeries : perfSeriesSeq) {
            Sequence refImgs =
                    perfSeries.getSequence(Tag.ReferencedImageSequence);
            Sequence refNonImgs =
                    perfSeries.getSequence(Tag.ReferencedNonImageCompositeSOPInstanceSequence);
            int numI = 0;
            if (refImgs != null)
                numI += refImgs.size();
            if (refNonImgs != null)
                numI += refNonImgs.size();
            if (numI == 0)
                continue;

            String seriesInstanceUID = perfSeries.getString(Tag.SeriesInstanceUID);
            @SuppressWarnings("unchecked")
            List<SOPInstanceReference> storedSeries =
                em.createNamedQuery(
                        Instance.SOP_INSTANCE_REFERENCE_BY_SERIES_INSTANCE_UID)
                  .setParameter(1, seriesInstanceUID)
                  .getResultList();
            if (storedSeries.isEmpty())
                continue;

            String studyInstanceUID = storedSeries.get(0).studyInstanceUID;
            Attributes ian = findByStudyInstanceUID(ians, studyInstanceUID);
            if (ian == null) {
                ian = new Attributes(3);
                Attributes refPPS = new Attributes(3);
                ian.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 1).add(refPPS);
                refPPS.setString(Tag.ReferencedSOPClassUID, VR.UI,
                        UID.ModalityPerformedProcedureStepSOPClass);
                refPPS.setString(Tag.ReferencedSOPInstanceUID, VR.UI,
                        ppsInstanceUID);
                refPPS.setNull(Tag.PerformedWorkitemCodeSequence, VR.SQ);
                ian.newSequence(Tag.ReferencedSeriesSequence, numS);
                ian.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
                ians.add(ian);
            }

            Attributes refSeries = new Attributes(2);
            ian.getSequence(Tag.ReferencedSeriesSequence).add(refSeries);
            Sequence ianRefs = refSeries.newSequence(Tag.ReferencedSOPSequence, numI);
            checkInstanceAvailability(ppsInstanceUID, refImgs, storedSeries, ianRefs);
            checkInstanceAvailability(ppsInstanceUID, refNonImgs, storedSeries, ianRefs);
            refSeries.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
        }
        return ians;
    }

    private Attributes findByStudyInstanceUID(List<Attributes> list,
            String studyInstanceUID) {
        for (Attributes ian : list)
            if (ian.getString(Tag.StudyInstanceUID).equals(studyInstanceUID))
                return ian;

        return null;
    }

    private void checkInstanceAvailability(String ppsInstanceUID,
            Sequence ppsRefs, List<SOPInstanceReference> storedSeries,
            Sequence ianRefs) throws SOPClassMismatchException {
        if (ppsRefs == null)
            return;

        for (Attributes ppsRef : ppsRefs) {
            SOPInstanceReference storedInst = findBySOPInstanceUID(storedSeries,
                    ppsRef.getString(Tag.ReferencedSOPInstanceUID));
            if (storedInst == null)
                continue;

            String cuid = ppsRef.getString(Tag.ReferencedSOPClassUID);
            if (!storedInst.sopClassUID.equals(cuid))
                throw new SOPClassMismatchException(
                        "SOP Class of stored Instance[iuid=" 
                        + storedInst.sopInstanceUID 
                        + ", cuid=" + storedInst.sopClassUID
                        + "] mismatch with SOP Reference[iuid=" 
                        + storedInst.sopInstanceUID + ", cuid=" + cuid
                        + "] in MPPS[uid=" + ppsInstanceUID + "]");

            Attributes ianRef = new Attributes(4);
            Utils.setRetrieveAET(ianRef,
                    storedInst.retrieveAETs, storedInst.externalRetrieveAET);
            ianRef.setString(Tag.InstanceAvailability, VR.CS,
                    storedInst.availability.toCodeString());
            ianRef.setString(Tag.ReferencedSOPClassUID, VR.UI,
                    storedInst.sopClassUID);
            ianRef.setString(Tag.ReferencedSOPInstanceUID, VR.UI,
                    storedInst.sopInstanceUID);
            ianRefs.add(ianRef);
        }
    }

    private SOPInstanceReference findBySOPInstanceUID(
            List<SOPInstanceReference> storedSOPs, String iuid) {
        for (SOPInstanceReference sopRef : storedSOPs)
            if (sopRef.sopInstanceUID.equals(iuid))
                return sopRef;
        return null;
    }

}
