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
 * Portions created by the Initial Developer are Copyright (C) 2011-2013
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

package org.dcm4chee.archive.store.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.IDWithIssuer;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4che.net.Status;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.soundex.FuzzyStr;
import org.dcm4che.util.StringUtils;
import org.dcm4che.util.TagUtils;
import org.dcm4chee.archive.code.CodeService;
import org.dcm4chee.archive.conf.AttributeFilter;
import org.dcm4chee.archive.conf.Entity;
import org.dcm4chee.archive.conf.StoreDuplicate;
import org.dcm4chee.archive.conf.StoreParam;
import org.dcm4chee.archive.entity.Availability;
import org.dcm4chee.archive.entity.Code;
import org.dcm4chee.archive.entity.ContentItem;
import org.dcm4chee.archive.entity.FileRef;
import org.dcm4chee.archive.entity.FileSystem;
import org.dcm4chee.archive.entity.FileSystemStatus;
import org.dcm4chee.archive.entity.Instance;
import org.dcm4chee.archive.entity.Issuer;
import org.dcm4chee.archive.entity.Patient;
import org.dcm4chee.archive.entity.ScheduledProcedureStep;
import org.dcm4chee.archive.entity.Series;
import org.dcm4chee.archive.entity.Study;
import org.dcm4chee.archive.entity.VerifyingObserver;
import org.dcm4chee.archive.issuer.IssuerService;
import org.dcm4chee.archive.patient.PatientService;
import org.dcm4chee.archive.request.RequestService;
import org.dcm4chee.archive.store.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@Stateless
public class StoreServiceImpl implements StoreService {

    final static Logger LOG = LoggerFactory.getLogger(StoreService.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @EJB
    private PatientService patientService;

    @EJB
    private IssuerService issuerService;

    @EJB
    private RequestService requestService;

    @EJB
    private CodeService codeService;

    @Override
    public FileSystem selectStorageFileSystem(String groupID, String defaultURI)
        throws DicomServiceException {
        TypedQuery<FileSystem> selectCurFileSystem =
                em.createNamedQuery(FileSystem.FIND_BY_GROUP_ID_AND_STATUS, FileSystem.class)
                .setParameter(1, groupID)
                .setParameter(2, FileSystemStatus.RW);
        try {
            return selectCurFileSystem.getSingleResult();
        } catch (NoResultException e) {
            List<FileSystem> resultList = 
                    em.createNamedQuery(FileSystem.FIND_BY_GROUP_ID, FileSystem.class)
                        .setParameter(1, groupID)
                        .getResultList();
            for (FileSystem fs : resultList) {
                if (fs.getStatus() == FileSystemStatus.Rw) {
                    fs.setStatus(FileSystemStatus.RW);
                    em.flush();
                    return fs;
                }
            }
            if (resultList.isEmpty() && defaultURI != null) {
                return initFileSystem(em, groupID, defaultURI);
            }
            throw new DicomServiceException(Status.OutOfResources,
                    "No writeable File System in File System Group " + groupID);
        }
    }

    private FileSystem initFileSystem(EntityManager em, String groupID,
            String defaultURI) {
        FileSystem fs = new FileSystem();
        fs.setGroupID(groupID);
        fs.setURI(StringUtils.replaceSystemProperties(defaultURI));
        fs.setAvailability(Availability.ONLINE);
        fs.setStatus(FileSystemStatus.RW);
        try {
            em.persist(fs);
            em.flush();
            return fs;
        } catch (PersistenceException e) {
            throw e;
        }
    }

    @Override
    public boolean store(StoreParam storeParam, String sourceAET,
            Attributes data, FileRef fileRef, Attributes modified)
                    throws DicomServiceException {
        try {
            Availability availability = fileRef.getFileSystem().getAvailability();
            Instance inst;
            try {
                inst = findInstance(em, data.getString(Tag.SOPInstanceUID, null));
                StoreDuplicate.Action storeDuplicate =
                        storeDuplicate(storeParam.getStoreDuplicates(), inst, fileRef);
                switch (inst.getAvailability()) {
                case REJECTED_FOR_QUALITY_REASONS_REJECTION_NOTE:
                case REJECTED_FOR_PATIENT_SAFETY_REASONS_REJECTION_NOTE:
                case INCORRECT_MODALITY_WORKLIST_ENTRY_REJECTION_NOTE:
                case DATA_RETENTION_PERIOD_EXPIRED_REJECTION_NOTE:
                    throw new DicomServiceException(Status.CannotUnderstand,
                            "subsequent occurrence of rejection note");
                case REJECTED_FOR_QUALITY_REASONS:
                case REJECTED_FOR_PATIENT_SAFETY_REASONS:
                case INCORRECT_MODALITY_WORKLIST_ENTRY:
                    throw new DicomServiceException(Status.CannotUnderstand,
                            "subsequent occurrence of rejected instance");
                case DATA_RETENTION_PERIOD_EXPIRED:
                    storeDuplicate = StoreDuplicate.Action.REPLACE;
                default:
                    break;
                }
                switch (storeDuplicate) {
                case IGNORE:
                    coerceAttributes(inst, data, modified);
                    return false;
                case STORE:
                    updateInstance(storeParam, inst, data, modified);
                    coerceAttributes(inst.getSeries(), data, modified);
                    break;
                case REPLACE:
                    inst.setReplaced(true);
                    inst = newInstance(em, storeParam, sourceAET, data,
                            availability, modified);
                    break;
                }
            } catch (NoResultException e) {
                inst = newInstance(em, storeParam, sourceAET, data,
                        availability, modified);
            }
            fileRef.setInstance(inst);
            em.persist(fileRef);
            em.flush();
            return true;
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e.getMessage());
        }
    }

    private StoreDuplicate.Action storeDuplicate(
            List<StoreDuplicate> storeDuplicates,
            Instance inst, FileRef newFileRef)
            throws DicomServiceException {
        final String fsGroupID = newFileRef.getFileSystem().getGroupID();
        final String digest = newFileRef.getDigest();
        Collection<FileRef> files = inst.getFileRefs();
        boolean noFiles = files.isEmpty();
        boolean equalsChecksum = false;
        boolean equalsFileSystemGroupID = false;
        for (FileRef fileRef : files) {
            if (!equalsFileSystemGroupID 
                    && fsGroupID.equals(fileRef.getFileSystem().getGroupID()))
                equalsFileSystemGroupID = true;
            if (!equalsChecksum && digest != null && digest.equals(fileRef.getDigest()))
                equalsChecksum = true;
        }
        for (StoreDuplicate sd : storeDuplicates)
            if (sd.getCondition().matches(noFiles, equalsChecksum, equalsFileSystemGroupID))
                return sd.getAction();
        return StoreDuplicate.Action.IGNORE;
    }

    private static void updateInstance(StoreParam storeParam,
            Instance inst, Attributes data, Attributes modified) {
        Attributes instAttrs = inst.getAttributes();
        final AttributeFilter filter = storeParam.getAttributeFilter(Entity.Instance);
        Attributes updated = new Attributes();
        if (instAttrs.updateSelected(data, updated, filter.getSelection())) {
            inst.setAttributes(data, filter, storeParam.getFuzzyStr());
        }
    }

    private static void coerceAttributes(Instance inst,
            Attributes data, Attributes modified) {
        coerceAttributes(inst.getSeries(), data, modified);
        data.update(inst.getAttributes(), modified);
    }

    private static void coerceAttributes(Series series,
            Attributes data, Attributes modified) {
        Study study = series.getStudy();
        Patient patient = study.getPatient();
        data.update(patient.getAttributes(), modified);
        data.update(study.getAttributes(), modified);
        data.update(series.getAttributes(), modified);
    }

    private Instance newInstance(EntityManager em, StoreParam storeParam,
            String sourceAET, Attributes data, Availability availability,
            Attributes modified) throws DicomServiceException {
//        Availability rnAvailability =
//                storeParam.getRejectionNoteAvailability(data);
//        if (rnAvailability != null) {
//            processRejectionNote(data, rnAvailability);
//        }
        Series series = findOrCreateSeries(em, storeParam, sourceAET, data,
                availability);
//        Availability availability = rnAvailability != null
//                    ? Availability.availabilityOfRejectedObject(rnAvailability)
//                    : fsAvailability;
        coerceAttributes(series, data, modified);
        if (!modified.isEmpty() && storeParam.isStoreOriginalAttributes()) {
            Attributes item = new Attributes(4);
            Sequence origAttrsSeq =
                    data.ensureSequence(Tag.OriginalAttributesSequence, 1);
            origAttrsSeq.add(item);
            item.setDate(Tag.AttributeModificationDateTime, VR.DT, new Date());
            item.setString(Tag.ModifyingSystem, VR.LO,
                    storeParam.getModifyingSystem());
            item.setString(Tag.SourceOfPreviousValues, VR.LO, sourceAET);
            item.newSequence(Tag.ModifiedAttributesSequence, 1).add(modified);
        }
        Instance inst = new Instance();
        inst.setSeries(series);
        inst.setConceptNameCode(singleCode(data, Tag.ConceptNameCodeSequence));
        inst.setVerifyingObservers(createVerifyingObservers(
                data.getSequence(Tag.VerifyingObserverSequence),
                storeParam.getFuzzyStr()));
        inst.setContentItems(
                createContentItems(data.getSequence(Tag.ContentSequence)));
        inst.setRetrieveAETs(storeParam.getRetrieveAETs());
        inst.setExternalRetrieveAET(storeParam.getExternalRetrieveAET());
        inst.setAvailability(availability);
        inst.setAttributes(data,
                storeParam.getAttributeFilter(Entity.Instance),
                storeParam.getFuzzyStr());
        em.persist(inst);
        return inst;
    }

    private Series findOrCreateSeries(EntityManager em, StoreParam storeParam,
            String sourceAET, Attributes data, Availability availability)
                    throws DicomServiceException {
        String seriesIUID = data.getString(Tag.SeriesInstanceUID);
        Series series;
//        updateRefPPS(data, storeContext);
//        checkRefPPS(data, storeContext);
        try {
            series = findSeries(em, seriesIUID);
        } catch (NoResultException e) {
            series = new Series();
            Study study = findOrCreateStudy(em, storeParam, data,
                    availability);
            series.setStudy(study);
            series.setInstitutionCode(
                    singleCode(data, Tag.InstitutionCodeSequence));
            series.setScheduledProcedureSteps(
                    getScheduledProcedureSteps(
                            data.getSequence(Tag.RequestAttributesSequence),
                            data, study.getPatient(), storeParam));
            series.setSourceAET(sourceAET);
            series.setRetrieveAETs(storeParam.getRetrieveAETs());
            series.setExternalRetrieveAET(storeParam.getExternalRetrieveAET());
            series.setAvailability(availability);
            series.setAttributes(data,
                    storeParam.getAttributeFilter(Entity.Series),
                    storeParam.getFuzzyStr());
            em.persist(series);
            return series;
        }
        Study study = series.getStudy();
        mergeSeriesAttributes(storeParam, series, data, availability);
        mergeStudyAttributes(storeParam, study, data, availability);
        mergePatientAttributes(storeParam, study.getPatient(), data);
        return series;
    }

    private Collection<ScheduledProcedureStep> getScheduledProcedureSteps(
            Sequence requestAttrsSeq, Attributes data, Patient patient,
            StoreParam storeParam) {
        if (requestAttrsSeq == null)
            return null;
        ArrayList<ScheduledProcedureStep> list =
                new ArrayList<ScheduledProcedureStep>(requestAttrsSeq.size());
        for (Attributes requestAttrs : requestAttrsSeq) {
            if (requestAttrs.containsValue(Tag.ScheduledProcedureStepID)
                    && requestAttrs.containsValue(Tag.RequestedProcedureID)
                    && (requestAttrs.containsValue(Tag.AccessionNumber)
                            || data.contains(Tag.AccessionNumber))) {
                Attributes attrs = new Attributes(data.bigEndian(),
                        data.size() + requestAttrs.size());
                attrs.addAll(data);
                attrs.addAll(requestAttrs);
                ScheduledProcedureStep sps =
                        requestService.findOrCreateScheduledProcedureStep(
                                attrs, patient, storeParam);
                list.add(sps);
            }
        }
        return list;
    }

    private void mergePatientAttributes(StoreParam storeParam,
            Patient patient, Attributes data) {
        Attributes patientAttrs = patient.getAttributes();
        AttributeFilter filter = storeParam.getAttributeFilter(Entity.Patient);
        if (patientAttrs.mergeSelected(data, filter.getSelection())) {
            if (patient.getIssuerOfPatientID() == null) {
                IDWithIssuer pid = IDWithIssuer.fromPatientIDWithIssuer(data);
                if (pid != null && pid.getIssuer() != null) {
                    patient.setIssuerOfPatientID(
                            issuerService.findOrCreate(new Issuer(pid.getIssuer())));
                }
            }
            patient.setAttributes(patientAttrs, filter, storeParam.getFuzzyStr());
        }
    }

    private void mergeSeriesAttributes(StoreParam storeParam,
            Series series, Attributes data, Availability availability) {
        series.retainRetrieveAETs(storeParam.getRetrieveAETs());
        series.retainExternalRetrieveAET(storeParam.getExternalRetrieveAET());
        series.floorAvailability(availability);
        series.resetNumberOfInstances();
        Attributes seriesAttrs = series.getAttributes();
        AttributeFilter seriesFilter = storeParam.getAttributeFilter(Entity.Series);
        if (seriesAttrs.mergeSelected(data, seriesFilter.getSelection())) {
            series.setAttributes(seriesAttrs, seriesFilter, storeParam.getFuzzyStr());
        }
    }

    private Study findOrCreateStudy(EntityManager em, StoreParam storeParam,
            Attributes data, Availability availability) {
        Study study;
        try {
            study = findStudy(em, data.getString(Tag.StudyInstanceUID));
            mergeStudyAttributes(storeParam, study, data, availability);
            mergePatientAttributes(storeParam, study.getPatient(), data);
        } catch (NoResultException e) {
            study = new Study();
            Patient patient = patientService.findUniqueOrCreatePatient(
                    storeParam.getAttributeFilter(Entity.Patient),
                    storeParam.getFuzzyStr(),
                    data, true, true);
            study.setPatient(patient);
            study.setProcedureCodes(codeList(data, Tag.ProcedureCodeSequence));
            study.setIssuerOfAccessionNumber(issuer(
                    data.getNestedDataset(Tag.IssuerOfAccessionNumberSequence)));
            study.setModalitiesInStudy(data.getString(Tag.Modality, null));
            study.setSOPClassesInStudy(data.getString(Tag.SOPClassUID, null));
            study.setRetrieveAETs(storeParam.getRetrieveAETs());
            study.setExternalRetrieveAET(storeParam.getExternalRetrieveAET());
            study.setAvailability(availability);
            study.setAttributes(data, 
                    storeParam.getAttributeFilter(Entity.Study),
                    storeParam.getFuzzyStr());
            em.persist(study);
        }
        return study;
    }

    private void mergeStudyAttributes(StoreParam storeParam,
            Study study, Attributes data, Availability availability) {
        study.addModalityInStudy(data.getString(Tag.Modality, null));
        study.addSOPClassInStudy(data.getString(Tag.SOPClassUID, null));
        study.retainRetrieveAETs(storeParam.getRetrieveAETs());
        study.retainExternalRetrieveAET(storeParam.getExternalRetrieveAET());
        study.floorAvailability(availability);
        study.resetNumberOfInstances();
        AttributeFilter studyFilter = storeParam.getAttributeFilter(Entity.Study);
        Attributes studyAttrs = study.getAttributes();
        if (studyAttrs.mergeSelected(data, studyFilter.getSelection())) {
            study.setAttributes(studyAttrs, studyFilter, storeParam.getFuzzyStr());
        }
    }

    private Instance findInstance(EntityManager em, String sopIUID) {
        return em.createNamedQuery(
                    Instance.FIND_BY_SOP_INSTANCE_UID, Instance.class)
                 .setParameter(1, sopIUID).getSingleResult();
    }

    private Series findSeries(EntityManager em, String seriesIUID) {
        return em.createNamedQuery(
                    Series.FIND_BY_SERIES_INSTANCE_UID, Series.class)
                 .setParameter(1, seriesIUID)
                 .getSingleResult();
    }

    private Study findStudy(EntityManager em, String studyIUID) {
        return em.createNamedQuery(
                    Study.FIND_BY_STUDY_INSTANCE_UID, Study.class)
                 .setParameter(1, studyIUID)
                 .getSingleResult();
    }

    private Collection<VerifyingObserver> createVerifyingObservers(
            Sequence seq, FuzzyStr fuzzyStr) {
        if (seq == null || seq.isEmpty())
            return null;

        ArrayList<VerifyingObserver> list =
                new ArrayList<VerifyingObserver>(seq.size());
        for (Attributes item : seq)
            list.add(new VerifyingObserver(item, fuzzyStr));
        return list;
    }

    private Collection<ContentItem> createContentItems(Sequence seq) {
        if (seq == null || seq.isEmpty())
            return null;

        Collection<ContentItem> list = new ArrayList<ContentItem>(seq.size());
        for (Attributes item : seq) {
            String type = item.getString(Tag.ValueType);
            if ("CODE".equals(type)) {
                list.add(new ContentItem(
                        item.getString(Tag.RelationshipType).toUpperCase(),
                        singleCode(item, Tag.ConceptNameCodeSequence),
                        singleCode(item, Tag.ConceptCodeSequence)));
            } else if ("TEXT".equals(type)) {
                list.add(new ContentItem(
                        item.getString(Tag.RelationshipType).toUpperCase(),
                        singleCode(item, Tag.ConceptNameCodeSequence),
                        item.getString(Tag.TextValue, "*")));
            }
        }
        return list;
    }

    private Code singleCode(Attributes attrs, int seqTag) {
        Attributes item = attrs.getNestedDataset(seqTag);
        if (item != null)
            try {
                return codeService.findOrCreate(new Code(item));
            } catch (Exception e) {
                LOG.info("Illegal code item in Sequence {}:\n{}",
                        TagUtils.toString(seqTag), item);
            }
        return null ;
    }

    private Collection<Code> codeList(Attributes attrs, int seqTag) {
        Sequence seq = attrs.getSequence(seqTag);
        if (seq == null || seq.isEmpty())
            return Collections.emptyList();
        
        ArrayList<Code> list = new ArrayList<Code>(seq.size());
        for (Attributes item : seq) {
            try {
                list.add(codeService.findOrCreate(new Code(item)));
            } catch (Exception e) {
                LOG.info("Illegal code item in Sequence {}:\n{}",
                        TagUtils.toString(seqTag), item);
            }
        }
        return list;
    }

    private Issuer issuer(Attributes item) {
        if (item == null)
            return null;

        return issuerService.findOrCreate(new Issuer(item));
    }

}
