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
import java.util.List;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.net.Status;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4chee.archive.entity.Availability;
import org.dcm4chee.archive.entity.Instance;
import org.dcm4chee.archive.entity.PerformedProcedureStep;
import org.dcm4chee.archive.entity.SOPInstanceReference;
import org.dcm4chee.archive.entity.Utils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
@Stateless
public class IANQueryService {

    @PersistenceContext
    private EntityManager em;

    public Attributes createIANforMPPS(PerformedProcedureStep mpps)
            throws DicomServiceException {
        Sequence perfSeriesSeq = mpps.getAttributes()
                .getSequence(Tag.PerformedSeriesSequence);
        if (perfSeriesSeq == null || perfSeriesSeq.isEmpty())
            return null;

        Attributes ian = new Attributes(3);
        Attributes refPPS = new Attributes(3);
        ian.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 1).add(refPPS);
        refPPS.setString(Tag.ReferencedSOPClassUID, VR.UI,
                UID.ModalityPerformedProcedureStepSOPClass);
        refPPS.setString(Tag.ReferencedSOPInstanceUID, VR.UI,
                mpps.getSopInstanceUID());
        refPPS.setNull(Tag.PerformedWorkitemCodeSequence, VR.SQ);
        Sequence refSeriesSeq = ian.newSequence(Tag.ReferencedSeriesSequence, perfSeriesSeq.size());
        String studyInstanceUID = null;
        for (Attributes perfSeries : perfSeriesSeq) {
            Sequence refImgs =
                    perfSeries.getSequence(Tag.ReferencedImageSequence);
            Sequence refNonImgs =
                    perfSeries.getSequence(Tag.ReferencedNonImageCompositeSOPInstanceSequence);
            int seriesSize = 0;
            if (refImgs != null)
                seriesSize += refImgs.size();
            if (refNonImgs != null)
                seriesSize += refNonImgs.size();
            if (seriesSize == 0)
                continue;

            String seriesInstanceUID = perfSeries.getString(Tag.SeriesInstanceUID);
            @SuppressWarnings("unchecked")
            List<SOPInstanceReference> storedSOPs =
                em.createNamedQuery(
                        Instance.SOP_INSTANCE_REFERENCE_BY_SERIES_INSTANCE_UID)
                  .setParameter(1, seriesInstanceUID)
                  .getResultList();
            if (storedSOPs.isEmpty())
                return null;

            String studyInstanceUID0 = storedSOPs.get(0).studyInstanceUID;
            if (studyInstanceUID == null)
                ian.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID = studyInstanceUID0);
            else if (!studyInstanceUID.equals(studyInstanceUID0))
                throw new DicomServiceException(Status.ProcessingFailure,
                        "Series referenced by MPPS belong to multiple Studies");

            Attributes refSeries = new Attributes(2);
            refSeriesSeq.add(refSeries);
            Sequence refSOPs = refSeries.newSequence(Tag.ReferencedSOPSequence, seriesSize);
            refSeries.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);

            if (refImgs != null)
                if (!containsAll(storedSOPs, refImgs, refSOPs))
                    return null;
            if (refNonImgs != null)
                if (!containsAll(storedSOPs, refNonImgs, refSOPs))
                    return null;
        }
        return ian;
    }

    private static boolean containsAll(List<SOPInstanceReference> storedSOPs,
            Sequence ppsRefs, Sequence refSOPs) {
        for (Attributes ppsRef : ppsRefs) {
            SOPInstanceReference sopRef = findBySOPInstanceUID(storedSOPs,
                    ppsRef.getString(Tag.ReferencedSOPInstanceUID));
            if (sopRef == null)
                return false;
            if (!sopRef.sopClassUID.equals(
                    ppsRef.getString(Tag.ReferencedSOPClassUID)))
                new DicomServiceException(Status.ProcessingFailure,
                        "Mismatch of SOP Class UID of referenced Instance");

            refSOPs.add(makeRefSOPItem(sopRef));
        }
        return true;
    }

    private static Attributes makeRefSOPItem(SOPInstanceReference sopRef) {
        Attributes refSOP = new Attributes(4);
        Utils.setRetrieveAET(refSOP, sopRef.retrieveAETs, sopRef.externalRetrieveAET);
        refSOP.setString(Tag.InstanceAvailability, VR.CS, sopRef.availability.toCodeString());
        refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI, sopRef.sopClassUID);
        refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, sopRef.sopInstanceUID);
        return refSOP;
    }

    private static SOPInstanceReference findBySOPInstanceUID(
            List<SOPInstanceReference> storedSOPs, String iuid) {
        for (SOPInstanceReference sopRef : storedSOPs)
            if (sopRef.sopInstanceUID.equals(iuid))
                return sopRef;
        return null;
    }

    public Attributes createIANforRejectionNote(Attributes rejectionNote,
            Availability rejection) {
        String studyIUID = rejectionNote.getString(Tag.StudyInstanceUID);

        Attributes ian = new Attributes(3);
        Attributes rnRefPPS = rejectionNote
                .getNestedDataset(Tag.ReferencedPerformedProcedureStepSequence);
        if (rnRefPPS != null) {
            Attributes refPPS = new Attributes(3);
            refPPS.addAll(rnRefPPS);
            refPPS.setNull(Tag.PerformedWorkitemCodeSequence, VR.SQ);
            ian.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 1)
               .add(refPPS);
        } else
            ian.setNull(Tag.ReferencedPerformedProcedureStepSequence, VR.SQ);
        Sequence refSeriesSeq = ian.newSequence(Tag.ReferencedSeriesSequence, 10);
        ian.setString(Tag.StudyInstanceUID, VR.UI, studyIUID);
        @SuppressWarnings("unchecked")
        List<SOPInstanceReference> storedSOPs =
            em.createNamedQuery(
                    Instance.SOP_INSTANCE_REFERENCE_BY_STUDY_INSTANCE_UID)
              .setParameter(1, studyIUID )
              .getResultList();
        int size = storedSOPs.size();
        for (SOPInstanceReference storedSOP : storedSOPs) {
            if (storedSOP.availability.available()
                    || storedSOP.availability == rejection)
                refSeries(refSeriesSeq, storedSOP.seriesInstanceUID, size)
                    .getSequence(Tag.ReferencedSOPSequence)
                    .add(makeRefSOPItem(storedSOP));
        }
        return ian;
    }

    public List<Attributes> createIANsforIncorrectModalityWorklistEntry(
            Attributes rejectionNote) {
        String studyIUID = rejectionNote.getString(Tag.StudyInstanceUID);
        List<Attributes> ians = new ArrayList<Attributes>();
        @SuppressWarnings("unchecked")
        List<SOPInstanceReference> storedSOPs =
            em.createNamedQuery(
                    Instance.SOP_INSTANCE_REFERENCE_BY_STUDY_INSTANCE_UID)
              .setParameter(1, studyIUID )
              .getResultList();
        int size = storedSOPs.size();
        for (SOPInstanceReference storedSOP : storedSOPs) {
            if (storedSOP.availability.available() || storedSOP.availability
                        == Availability.INCORRECT_MODALITY_WORKLIST_ENTRY) {
                Sequence refSeriesSeq = ian(ians, storedSOP)
                        .getSequence(Tag.ReferencedSeriesSequence);
                
                refSeries(refSeriesSeq, storedSOP.seriesInstanceUID, size)
                        .getSequence(Tag.ReferencedSOPSequence)
                        .add(makeRefSOPItem(storedSOP));
            }
        }
        return ians;
    }

    private Attributes refSeries(Sequence refSeriesSeq, String seriesIUID, int size) {
        for (Attributes refSeries : refSeriesSeq)
            if (refSeries.getString(Tag.SeriesInstanceUID).equals(seriesIUID))
                return refSeries;
        
        Attributes refSeries = new Attributes(2);
        refSeriesSeq.add(refSeries);
        refSeries.newSequence(Tag.ReferencedSOPSequence, size);
        refSeries.setString(Tag.SeriesInstanceUID, VR.UI, seriesIUID);
        return refSeries ;
    }

    private Attributes ian(List<Attributes> ians, SOPInstanceReference storedSOP) {
        for (Attributes ian : ians)
            if (equalsRefPPS(ian.getNestedDataset(Tag.ReferencedPerformedProcedureStepSequence), storedSOP))
                return ian;

        Attributes ian = new Attributes(3);
        if (storedSOP.performedProcedureStepClassUID != null 
                && storedSOP.performedProcedureStepInstanceUID != null) {
            Attributes refPPS = new Attributes(3);
            refPPS.setString(Tag.ReferencedSOPClassUID, VR.UI,
                    storedSOP.performedProcedureStepClassUID);
            refPPS.setString(Tag.ReferencedSOPInstanceUID, VR.UI,
                    storedSOP.performedProcedureStepInstanceUID);
            refPPS.setNull(Tag.PerformedWorkitemCodeSequence, VR.SQ);
            ian.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 1)
               .add(refPPS);
        } else {
            ian.setNull(Tag.ReferencedPerformedProcedureStepSequence, VR.SQ);
        }
        ian.newSequence(Tag.ReferencedSeriesSequence, 10);
        ian.setString(Tag.StudyInstanceUID, VR.UI, storedSOP.studyInstanceUID);
        ians.add(ian);
        return ian;
    }

    private boolean equalsRefPPS(Attributes refPPS, SOPInstanceReference storedSOP) {
        if (refPPS != null)
            return refPPS.getString(Tag.ReferencedSOPClassUID)
                    .equals(storedSOP.performedProcedureStepClassUID)
                && refPPS.getString(Tag.ReferencedSOPInstanceUID)
                    .equals(storedSOP.performedProcedureStepInstanceUID);
        else
            return storedSOP.performedProcedureStepClassUID == null
                || storedSOP.performedProcedureStepInstanceUID == null;
    }

}
