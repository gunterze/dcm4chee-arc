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

package org.dcm4chee.archive.mpps.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.net.Status;
import org.dcm4che.net.service.BasicMPPSSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4chee.archive.common.StoreParam;
import org.dcm4chee.archive.conf.AttributeFilter;
import org.dcm4chee.archive.conf.Entity;
import org.dcm4chee.archive.dao.CodeService;
import org.dcm4chee.archive.dao.PatientService;
import org.dcm4chee.archive.dao.RequestService;
import org.dcm4chee.archive.entity.Availability;
import org.dcm4chee.archive.entity.Instance;
import org.dcm4chee.archive.entity.Patient;
import org.dcm4chee.archive.entity.PerformedProcedureStep;
import org.dcm4chee.archive.entity.ScheduledProcedureStep;
import org.dcm4chee.archive.entity.Series;
import org.dcm4chee.archive.entity.Study;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
@Stateless
public class MPPSService {

    @PersistenceContext
    private EntityManager em;

    @EJB
    private PatientService patService;

    @EJB
    private CodeService codeService;

    @EJB
    private RequestService requestService;

    @EJB
    private IANQueryService ianQuery;

    public PerformedProcedureStep createPerformedProcedureStep(
            String sopInstanceUID, Attributes attrs, StoreParam storeParam)
                    throws DicomServiceException {
        try {
            find(sopInstanceUID);
            throw new DicomServiceException(Status.DuplicateSOPinstance)
                .setUID(Tag.AffectedSOPInstanceUID, sopInstanceUID);
        } catch (NoResultException e) {}
        AttributeFilter filter = storeParam.getAttributeFilter(Entity.PerformedProcedureStep);
        Patient patient = patService.findUniqueOrCreatePatient(
                attrs, storeParam, true, true);
        PerformedProcedureStep mpps = new PerformedProcedureStep();
        mpps.setSopInstanceUID(sopInstanceUID);
        mpps.setAttributes(attrs, filter);
        mpps.setScheduledProcedureSteps(
                getScheduledProcedureSteps(
                        attrs.getSequence(Tag.ScheduledStepAttributesSequence),
                        patient,
                        storeParam));
        mpps.setPatient(patient);
        em.persist(mpps);
        return mpps;
    }

    public PPSWithIAN updatePerformedProcedureStep(String sopInstanceUID,
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

        AttributeFilter filter = storeParam.getAttributeFilter(Entity.PerformedProcedureStep);
        Attributes attrs = pps.getAttributes();
        attrs.addAll(modified);
        pps.setAttributes(attrs, filter);
        Attributes ian = null;
        if (pps.getStatus() != PerformedProcedureStep.Status.IN_PROGRESS) {
            if (!attrs.containsValue(Tag.PerformedSeriesSequence))
                throw new DicomServiceException(Status.MissingAttributeValue)
                        .setAttributeIdentifierList(Tag.PerformedSeriesSequence);
            if (pps.getStatus() != PerformedProcedureStep.Status.DISCONTINUED) {
                Attributes reasonCode = pps.getAttributes().getNestedDataset(
                        Tag.PerformedProcedureStepDiscontinuationReasonCodeSequence);
                if (reasonCode != null && new org.dcm4che.data.Code(reasonCode)
                    .equalsIgnoreMeaning(storeParam.getIncorrectWorklistEntrySelectedCode()))
                    rejectPerformedSeries(
                            attrs.getSequence(Tag.PerformedSeriesSequence));
            }
            ian  = ianQuery.createIANforMPPS(pps);
        }
        em.merge(pps);
        return new PPSWithIAN(pps, ian);
    }

    private void rejectPerformedSeries(Sequence perfSeriesSeq) {
        HashSet<String> iuids = new HashSet<String>();
        for (Attributes perfSeries : perfSeriesSeq) {
            addRefSOPInstanceUIDs(iuids,
                    perfSeries.getSequence(Tag.ReferencedImageSequence));
            addRefSOPInstanceUIDs(iuids,
                    perfSeries.getSequence(Tag.ReferencedNonImageCompositeSOPInstanceSequence));
            List<Instance> insts =
                em.createNamedQuery(Instance.FIND_BY_SERIES_INSTANCE_UID, Instance.class)
                  .setParameter(1, perfSeries.getString(Tag.SeriesInstanceUID))
                  .getResultList();
            Series series = null;
            for (Instance inst : insts)
                if (iuids.contains(inst.getSopInstanceUID())) {
                    inst.setRejectionFlag(Instance.INCORRECT_MODALITY_WORKLIST_ENTRY);
                    inst.setAvailability(Availability.UNAVAILABLE);
                    series = inst.getSeries();
                }
            Study study = series.getStudy();
            series.setNumberOfSeriesRelatedInstances(-1);
            series.setNumberOfSeriesRelatedRejectedInstances(-1);
            study.setNumberOfStudyRelatedInstances(-1);
            study.setNumberOfStudyRelatedRejectedInstances(-1);
            iuids.clear();
        }
    }

    private void addRefSOPInstanceUIDs(HashSet<String> iuids, Sequence refImgs) {
        if (refImgs != null)
            for (Attributes ref : refImgs)
                iuids.add(ref.getString(Tag.ReferencedSOPInstanceUID));
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

}
