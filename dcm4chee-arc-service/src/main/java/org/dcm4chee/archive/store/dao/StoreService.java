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

package org.dcm4chee.archive.store.dao;

import static org.dcm4chee.archive.conf.RejectionNote.Action.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.ejb.EJB;
import javax.ejb.Remove;
import javax.ejb.Stateful;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContextType;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.net.Status;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.soundex.FuzzyStr;
import org.dcm4che.util.TagUtils;
import org.dcm4chee.archive.conf.AttributeFilter;
import org.dcm4chee.archive.conf.Entity;
import org.dcm4chee.archive.conf.RejectionNote;
import org.dcm4chee.archive.conf.RejectionNote.Action;
import org.dcm4chee.archive.conf.StoreDuplicate;
import org.dcm4chee.archive.dao.CodeService;
import org.dcm4chee.archive.dao.IssuerService;
import org.dcm4chee.archive.dao.PatientService;
import org.dcm4chee.archive.dao.RequestService;
import org.dcm4chee.archive.entity.Availability;
import org.dcm4chee.archive.entity.Code;
import org.dcm4chee.archive.entity.ContentItem;
import org.dcm4chee.archive.entity.FileRef;
import org.dcm4chee.archive.entity.FileSystem;
import org.dcm4chee.archive.entity.FileSystemStatus;
import org.dcm4chee.archive.entity.Instance;
import org.dcm4chee.archive.entity.Issuer;
import org.dcm4chee.archive.entity.Patient;
import org.dcm4chee.archive.entity.PerformedProcedureStep;
import org.dcm4chee.archive.entity.ScheduledProcedureStep;
import org.dcm4chee.archive.entity.Series;
import org.dcm4chee.archive.entity.Study;
import org.dcm4chee.archive.entity.VerifyingObserver;
import org.dcm4chee.archive.mpps.dao.IANQueryService;
import org.dcm4chee.archive.store.StoreParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
@Stateful
public class StoreService {

    private Logger LOG = LoggerFactory.getLogger(StoreService.class);

    @PersistenceContext(type=PersistenceContextType.EXTENDED)
    private EntityManager em;

    @EJB
    private CodeService codeService;

    @EJB
    private IssuerService issuerService;

    @EJB
    private PatientService patientService;

    @EJB
    private RequestService requestService;

    @EJB
    private IANQueryService ianQuery;

    private StoreParam storeParam;
    private FileSystem curFileSystem;
    private Series cachedSeries;
    private PerformedProcedureStep prevMpps;
    private PerformedProcedureStep curMpps;
    private Code curRejectionCode;
    private HashMap<String,HashSet<String>> rejectedInstances =
            new HashMap<String,HashSet<String>>();

    public void setStoreParam(StoreParam storeParam) {
        this.storeParam = storeParam;
    }

    public FileSystem getCurrentFileSystem() {
        return curFileSystem;
    }

    public Attributes createIANforPreviousMPPS() throws DicomServiceException {
        try {
            return createIANforMPPS(prevMpps, Collections.<String> emptySet());
        } finally {
            prevMpps = null;
        }
    }

    public Attributes createIANforCurrentMPPS()
            throws DicomServiceException {
        try {
            return createIANforMPPS(curMpps, Collections.<String> emptySet());
        } finally {
            curMpps = null;
        }
    }

    private Attributes createIANforMPPS(PerformedProcedureStep pps,
            Set<String> rejectedIUIDs) throws DicomServiceException {
        if (pps == null || pps.isInProgress())
            return null;

        return ianQuery.createIANforMPPS(pps,
                storeParam.getHideConceptNameCodes(), rejectedIUIDs);
    }

    public boolean addFileRef(String sourceAET, Attributes data, Attributes modified,
            File file, String digest, String tsuid)
            throws DicomServiceException {
        try {
            FileSystem fs = curFileSystem;
            Instance inst;
            try {
                inst = findInstance(data.getString(Tag.SOPInstanceUID, null));
                Code rnc = inst.getRejectionCode();
                RejectionNote rn = storeParam.getRejectionNote(rnc);
                if (containsAction(rn, NOT_ACCEPT_SUBSEQUENT_OCCURRENCE))
                        throw new DicomServiceException(Status.CannotUnderstand,
                                rnc.getCodeMeaning());
                switch (storeDuplicate(inst, digest, fs.getGroupID(), storeParam)) {
                case IGNORE:
                    coerceAttributes(inst, data, modified);
                    if (containsAction(rn, NOT_REJECT_SUBSEQUENT_OCCURRENCE))
                        inst.setRejectionCode(null);
                    return false;
                case STORE:
                    updateInstance(inst, data, modified, storeParam);
                    coerceAttributes(inst.getSeries(), data, modified);
                    if (containsAction(rn, NOT_REJECT_SUBSEQUENT_OCCURRENCE))
                        inst.setRejectionCode(null);
                    break;
                case REPLACE:
                    inst.setReplaced(true);
                    inst = newInstance(sourceAET, data, modified, fs.getAvailability());
                    if (!containsAction(rn, NOT_REJECT_SUBSEQUENT_OCCURRENCE))
                        inst.setRejectionCode(rnc);
                    break;
                }
            } catch (NoResultException e) {
                inst = newInstance(sourceAET, data, modified, fs.getAvailability());
            }
            String filePath = file.toURI().toString().substring(fs.getURI().length());
            FileRef fileRef = new FileRef(fs, filePath, tsuid, file.length(), digest);
            fileRef.setInstance(inst);
            em.persist(fileRef);
            em.flush();
            em.detach(fileRef);
            return true;
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e.getMessage());
        }

    }

    protected boolean containsAction(RejectionNote rn, final Action action) {
        return rn != null && rn.getActions().contains(action);
    }

    private StoreDuplicate.Action storeDuplicate(Instance inst, String digest,
            String fsGroupID, StoreParam storeParam) {
        Collection<FileRef> files = inst.getFileRefs();
        boolean noFiles = files.isEmpty();
        boolean equalsChecksum = false;
        boolean equalsFileSystemGroupID = false;
        for (FileRef fileRef : files) {
            if (!equalsFileSystemGroupID && fsGroupID.equals(fileRef.getFileSystem().getGroupID()))
                equalsFileSystemGroupID = true;
            if (!equalsChecksum && digest != null && digest.equals(fileRef.getDigest()))
                equalsChecksum = true;
        }
        return storeParam.getStoreDuplicate(noFiles, equalsChecksum, equalsFileSystemGroupID);
    }

    private static void updateInstance(Instance inst, Attributes data,
            Attributes modified, StoreParam storeParam) {
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

    public Instance newInstance(String sourceAET, Attributes data,
            Attributes modified, Availability availability)
                    throws DicomServiceException {
        try {
            rejectedInstances.clear();
            Code conceptNameCode = singleCode(data, Tag.ConceptNameCodeSequence);
            RejectionNote rn = storeParam.getRejectionNote(conceptNameCode);
            if (rn != null) {
                rejectRefInstances(data, rn);
            }
            Series series = findOrCreateSeries(sourceAET, data, availability);
            coerceAttributes(series, data, modified);
            if (!modified.isEmpty() && storeParam.isStoreOriginalAttributes()) {
                Attributes item = new Attributes(4);
                Sequence origAttrsSeq = data.ensureSequence(Tag.OriginalAttributesSequence, 1);
                origAttrsSeq.add(item);
                item.setDate(Tag.AttributeModificationDateTime, VR.DT, new Date());
                item.setString(Tag.ModifyingSystem, VR.LO, storeParam.getModifyingSystem());
                item.setString(Tag.SourceOfPreviousValues, VR.LO, sourceAET);
                item.newSequence(Tag.ModifiedAttributesSequence, 1).add(modified);
            }
            Instance inst = new Instance();
            inst.setSeries(series);
            inst.setConceptNameCode(conceptNameCode);
            inst.setRejectionCode(curRejectionCode);
            inst.setVerifyingObservers(createVerifyingObservers(
                    data.getSequence(Tag.VerifyingObserverSequence), storeParam.getFuzzyStr()));
            inst.setContentItems(createContentItems(data.getSequence(Tag.ContentSequence)));
            inst.setRetrieveAETs(storeParam.getRetrieveAETs());
            inst.setExternalRetrieveAET(storeParam.getExternalRetrieveAET());
            inst.setAvailability(availability);
            inst.setAttributes(data, 
                    storeParam.getAttributeFilter(Entity.Instance),
                    storeParam.getFuzzyStr());
            em.persist(inst);
            em.flush();
            em.detach(inst);
            return inst;
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e.getMessage());
        }
    }

    private void rejectRefInstances(Attributes data, RejectionNote rn)
            throws DicomServiceException {
        boolean hideRejectedInstances = rn.getActions()
                .contains(RejectionNote.Action.HIDE_REJECTED_INSTANCES);
        Code rejectionCcode = (Code) rn.getCode();
        HashMap<String,String> iuid2cuid = new HashMap<String,String>();
        Sequence refStudySeq = data.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (refStudySeq == null)
            rejectionFailed("Rejection failed: Missing Type 1 attribute");
        for (Attributes refStudy : refStudySeq) {
            String studyIUID = refStudy.getString(Tag.StudyInstanceUID);
            Sequence refSeriesSeq = refStudy.getSequence(Tag.ReferencedSeriesSequence);
            if (studyIUID == null || refSeriesSeq == null)
                rejectionFailed("Rejection failed: Missing Type 1 attribute");
            for (Attributes refSeries : refSeriesSeq) {
                String seriesIUID = refSeries.getString(Tag.SeriesInstanceUID);
                Sequence refSOPSeq = refSeries.getSequence(Tag.ReferencedSOPSequence);
                if (seriesIUID == null || refSOPSeq == null)
                    rejectionFailed("Rejection failed: Missing Type 1 attribute");
                for (Attributes refSOP : refSOPSeq) {
                    String refCUID = refSOP.getString(Tag.ReferencedSOPClassUID);
                    String refIUID = refSOP.getString(Tag.ReferencedSOPInstanceUID);
                    if (refCUID == null || refIUID == null)
                        rejectionFailed("Rejection failed: Missing Type 1 attribute");
                    iuid2cuid.put(refIUID, refCUID);
                }
                List<Instance> insts =
                    em.createNamedQuery(Instance.FIND_BY_SERIES_INSTANCE_UID, Instance.class)
                      .setParameter(1, seriesIUID)
                      .getResultList();
                if (!insts.isEmpty()) {
                    Series series = insts.get(0).getSeries();
                    Study study = series.getStudy();
                    if (!studyIUID.equals(study.getStudyInstanceUID()))
                        rejectionFailed("Rejection failed: Mismatch of Study Instance UID");
                    if (hideRejectedInstances
                            && UID.ModalityPerformedProcedureStepSOPClass
                                .equals(series.getPerformedProcedureStepClassUID())) {
                        String mppsiuid = series.getPerformedProcedureStepInstanceUID();
                        HashSet<String> iuids = rejectedInstances.get(mppsiuid);
                        if (iuids == null)
                            rejectedInstances.put(mppsiuid,
                                    iuids = new HashSet<String>(iuid2cuid.keySet()));
                        else
                            iuids.addAll(iuid2cuid.keySet());
                    }
                    for (Instance inst : insts) {
                        String refCUID = iuid2cuid.remove(inst.getSopInstanceUID());
                        if (refCUID != null) {
                            if (!refCUID.equals(inst.getSopClassUID()))
                                rejectionFailed("Rejection failed: Mismatch of SOP Class UID");
                            inst.setRejectionCode(rejectionCcode);
                        }
                    }
                    series.setNumberOfSeriesRelatedInstances(-1);
                    study.setNumberOfStudyRelatedSeries(-1);
                    study.setNumberOfStudyRelatedInstances(-1);
                }
                if (!iuid2cuid.isEmpty())
                    rejectionFailed("Rejection failed: No such referenced SOP Instances");
            }
        }
    }

    public List<Attributes> createIANsforRejectionNote() throws DicomServiceException {
        try {
            List<Attributes> ians = new ArrayList<Attributes>(rejectedInstances.size());
            for (Entry<String, HashSet<String>> entry : rejectedInstances.entrySet()) {
                Attributes ian = createIANforMPPS(findPPS(entry.getKey()), entry.getValue());
                if (ian != null)
                    ians.add(ian);
            }
            return ians;
        } finally {
            rejectedInstances.clear();
        }
    }

    private void rejectionFailed(String message) throws DicomServiceException {
        throw new DicomServiceException(Status.CannotUnderstand, message)
                .setOffendingElements(Tag.CurrentRequestedProcedureEvidenceSequence);
    }

    public FileSystem selectFileSystem(String groupID)
            throws DicomServiceException {
        try {
            return curFileSystem =
                    em.createNamedQuery(FileSystem.FIND_BY_GROUP_ID_AND_STATUS, FileSystem.class)
                        .setParameter(1, groupID)
                        .setParameter(2, FileSystemStatus.RW)
                        .getSingleResult();
        } catch (NoResultException e) {
            List<FileSystem> resultList = 
                    em.createNamedQuery(FileSystem.FIND_BY_GROUP_ID, FileSystem.class)
                        .setParameter(1, groupID)
                        .getResultList();
            if (resultList.isEmpty()) {
                FileSystem fs = new FileSystem();
                fs.setGroupID(groupID);
                fs.setURI(new File(System.getProperty("jboss.server.data.dir")).toURI().toString());
                fs.setAvailability(Availability.ONLINE);
                fs.setStatus(FileSystemStatus.RW);
                em.persist(fs);
                return curFileSystem = fs;
            }
            for (FileSystem fs : resultList) {
                if (fs.getStatus() == FileSystemStatus.Rw) {
                    fs.setStatus(FileSystemStatus.RW);
                    em.flush();
                    return curFileSystem;
                }
            }
            throw new DicomServiceException(Status.OutOfResources,
                    "No writeable File System in File System Group " + groupID);
        }
    }

    private Instance findInstance(String sopIUID) {
        return em.createNamedQuery(
                    Instance.FIND_BY_SOP_INSTANCE_UID, Instance.class)
                 .setParameter(1, sopIUID).getSingleResult();
    }

    private Series findSeries(String seriesIUID) {
        return em.createNamedQuery(
                    Series.FIND_BY_SERIES_INSTANCE_UID, Series.class)
                 .setParameter(1, seriesIUID)
                 .getSingleResult();
    }

    private Study findStudy(String studyIUID) {
        return em.createNamedQuery(
                    Study.FIND_BY_STUDY_INSTANCE_UID, Study.class)
                 .setParameter(1, studyIUID)
                 .getSingleResult();
    }

    private List<VerifyingObserver> createVerifyingObservers(Sequence seq, FuzzyStr fuzzyStr) {
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

    private Series findOrCreateSeries(String sourceAET, Attributes data,
            Availability availability) throws DicomServiceException {
        String seriesIUID = data.getString(Tag.SeriesInstanceUID, null);
        Series series = cachedSeries;
        if (series == null || !series.getSeriesInstanceUID().equals(seriesIUID)) {
            updateRefPPS(
                    data.getNestedDataset(Tag.ReferencedPerformedProcedureStepSequence),
                    storeParam);
            checkRefPPS(data);
            try {
                cachedSeries = series = findSeries(seriesIUID);
            } catch (NoResultException e) {
                cachedSeries = series = new Series();
                Study study = findOrCreateStudy(data, availability);
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
                series.setNumberOfSeriesRelatedInstances(-1);
                series.setAttributes(data,
                        storeParam.getAttributeFilter(Entity.Series),
                        storeParam.getFuzzyStr());
                em.persist(series);
                return series;
            }
        } else {
            checkRefPPS(data);
        }
        updateEntities(series, data, availability);
        return series;
    }

    private void updateEntities(Series series, Attributes data,
            Availability availability) {
        updateEntities(series.getStudy(), data, availability);
        series.retainRetrieveAETs(storeParam.getRetrieveAETs());
        series.retainExternalRetrieveAET(storeParam.getExternalRetrieveAET());
        series.floorAvailability(availability);
        series.setNumberOfSeriesRelatedInstances(-1);
        Attributes seriesAttrs = series.getAttributes();
        AttributeFilter seriesFilter = storeParam.getAttributeFilter(Entity.Series);
        if (seriesAttrs.mergeSelected(data, seriesFilter.getSelection())) {
            series.setAttributes(seriesAttrs, seriesFilter, storeParam.getFuzzyStr());
        }
    }

    private void updateRefPPS(Attributes refPPS, StoreParam storeParam) {
        String mppsIUID = refPPS != null
                && UID.ModalityPerformedProcedureStepSOPClass.equals(
                        refPPS.getString(Tag.ReferencedSOPClassUID))
                        ? refPPS.getString(Tag.ReferencedSOPInstanceUID)
                        : null;
        PerformedProcedureStep mpps = curMpps;
        if (mpps == null || !mpps.getSopInstanceUID().equals(mppsIUID)) {
            prevMpps = mpps;
            curMpps = mpps = findPPS(mppsIUID);
            curRejectionCode = null;
            if (mpps != null && mpps.isDiscontinued()) {
                Attributes reasonCode = mpps.getAttributes().getNestedDataset(
                        Tag.PerformedProcedureStepDiscontinuationReasonCodeSequence);
                RejectionNote rn = storeParam.getRejectionNote(reasonCode);
                if (rn != null)
                    curRejectionCode = (Code) rn.getCode();
            }
        }
    }

    private void checkRefPPS(Attributes data) throws DicomServiceException {
        PerformedProcedureStep mpps = curMpps;
        if (mpps == null || mpps.isInProgress())
            return;

        String seriesIUID = data.getString(Tag.SeriesInstanceUID);
        String sopIUID = data.getString(Tag.SOPInstanceUID);
        String sopCUID = data.getString(Tag.SOPClassUID);
        Sequence perfSeriesSeq = mpps.getAttributes()
                .getSequence(Tag.PerformedSeriesSequence);
        for (Attributes perfSeries : perfSeriesSeq) {
            if (seriesIUID.equals(perfSeries.getString(Tag.SeriesInstanceUID))) {
                if (containsRef(sopCUID, sopIUID,
                        perfSeries.getSequence(Tag.ReferencedImageSequence))
                 || containsRef(sopCUID, sopIUID,
                        perfSeries.getSequence(Tag.ReferencedNonImageCompositeSOPInstanceSequence)))
                    return;
                break;
            }
        }
        for (Attributes perfSeries : perfSeriesSeq) {
            if (containsRef(sopCUID, sopIUID,
                    perfSeries.getSequence(Tag.ReferencedImageSequence))
             || containsRef(sopCUID, sopIUID,
                    perfSeries.getSequence(Tag.ReferencedNonImageCompositeSOPInstanceSequence)))
            throw new DicomServiceException(Status.ProcessingFailure,
                        "Mismatch of Series Instance UID in Referenced PPS");
        }
        throw new DicomServiceException(Status.ProcessingFailure,
                    "No such Instance in Referenced PPS");
    }

    private boolean containsRef(String sopCUID, String sopIUID, Sequence refSOPs)
            throws DicomServiceException {
        if (refSOPs != null)
            for (Attributes refSOP : refSOPs)
                if (sopIUID.equals(refSOP.getString(Tag.ReferencedSOPInstanceUID)))
                    if (sopCUID.equals(refSOP.getString(Tag.ReferencedSOPClassUID)))
                        return true;
                    else
                        throw new DicomServiceException(Status.ProcessingFailure,
                                    "Mismatch of SOP Class UID in Referenced PPS");
        return false;
    }

    private PerformedProcedureStep findPPS(String mppsIUID) {
        if (mppsIUID != null)
            try {
                return em.createNamedQuery(
                        PerformedProcedureStep.FIND_BY_SOP_INSTANCE_UID,
                        PerformedProcedureStep.class)
                     .setParameter(1, mppsIUID)
                     .getSingleResult();
            } catch (NoResultException e) { }
        return null;
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

    @Remove
    public void close() {
        storeParam = null;
        curFileSystem = null;
        cachedSeries = null;
        prevMpps = null;
        curMpps = null;
        curRejectionCode = null;
        rejectedInstances.clear();
    }

    private Issuer issuer(Attributes item) {
        if (item == null)
            return null;

        return issuerService.findOrCreate(new Issuer(item));
    }

    private Study findOrCreateStudy(Attributes data, Availability availability) {
        Study study;
        try {
            study = findStudy(data.getString(Tag.StudyInstanceUID, null));
            updateEntities(study, data, availability);
        } catch (NoResultException e) {
            study = new Study();
            Patient patient = patientService.findUniqueOrCreatePatient(data, storeParam);
            study.setPatient(patient);
            study.setProcedureCodes(codeList(data, Tag.ProcedureCodeSequence));
            study.setIssuerOfAccessionNumber(issuer(
                    data.getNestedDataset(Tag.IssuerOfAccessionNumberSequence)));
            study.setModalitiesInStudy(data.getString(Tag.Modality, null));
            study.setSOPClassesInStudy(data.getString(Tag.SOPClassUID, null));
            study.setRetrieveAETs(storeParam.getRetrieveAETs());
            study.setExternalRetrieveAET(storeParam.getExternalRetrieveAET());
            study.setAvailability(availability);
            study.setNumberOfStudyRelatedSeries(-1);
            study.setNumberOfStudyRelatedInstances(-1);
            study.setAttributes(data, 
                    storeParam.getAttributeFilter(Entity.Study),
                    storeParam.getFuzzyStr());
            em.persist(study);
        }
        return study;
    }

    private void updateEntities(Study study, Attributes data,
            Availability availability) {
        PatientService.updatePatient(study.getPatient(), data, storeParam);
        study.addModalityInStudy(data.getString(Tag.Modality, null));
        study.addSOPClassInStudy(data.getString(Tag.SOPClassUID, null));
        study.retainRetrieveAETs(storeParam.getRetrieveAETs());
        study.retainExternalRetrieveAET(storeParam.getExternalRetrieveAET());
        study.floorAvailability(availability);
        study.setNumberOfStudyRelatedSeries(-1);
        study.setNumberOfStudyRelatedInstances(-1);
        AttributeFilter studyFilter = storeParam.getAttributeFilter(Entity.Study);
        Attributes studyAttrs = study.getAttributes();
        if (studyAttrs.mergeSelected(data, studyFilter.getSelection())) {
            study.setAttributes(studyAttrs, studyFilter, storeParam.getFuzzyStr());
        }
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

}
