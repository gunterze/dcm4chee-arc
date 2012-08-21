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

package org.dcm4chee.archive.dao;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4chee.archive.conf.AttributeFilter;
import org.dcm4chee.archive.conf.Entity;
import org.dcm4chee.archive.entity.Issuer;
import org.dcm4chee.archive.entity.Patient;
import org.dcm4chee.archive.entity.PerformedProcedureStep;
import org.dcm4chee.archive.entity.Study;
import org.dcm4chee.archive.entity.Visit;
import org.dcm4chee.archive.exception.NonUniquePatientException;
import org.dcm4chee.archive.exception.PatientMergedException;
import org.dcm4chee.archive.store.StoreParam;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
@Stateless
public class PatientService {

    @PersistenceContext
    private EntityManager em;

    @EJB
    private IssuerService issuerService;

    public Patient findPatient(String pid, Issuer issuer,
            StoreParam storeParam) {
        if (pid == null)
            throw new NonUniqueResultException();
        TypedQuery<Patient> query = em.createNamedQuery(
                    Patient.FIND_BY_PATIENT_ID, Patient.class)
                .setParameter(1, pid);
        List<Patient> list = query.getResultList();
        if (issuer != null) {
            for (Iterator<Patient> it = list.iterator(); it.hasNext();) {
                Patient pat = (Patient) it.next();
                Issuer issuer2 = pat.getIssuerOfPatientID();
                if (issuer2 != null) {
                    if (equals(issuer, issuer2))
                        return pat;
                    else
                        it.remove();
                }
            }
        }
        if (list.isEmpty())
            throw new NoResultException();
        if (list.size() > 1)
            throw new NonUniqueResultException();
        return list.get(0);
    }

    private Patient followMergedWith(Patient patient) {
        while (patient.getMergedWith() != null)
            patient = patient.getMergedWith();
        return patient;
    }

    public Patient createNewPatient(Attributes attrs, Issuer issuer,
            StoreParam storeParam) {
        Patient patient = new Patient();
        patient.setIssuerOfPatientID(issuer);
        patient.setAttributes(attrs,
                storeParam.getAttributeFilter(Entity.Patient),
                storeParam.getFuzzyStr());
        em.persist(patient);
        return patient;
    }

    public Patient findUniqueOrCreatePatient(Attributes data,
            StoreParam storeParam) {
        String pid = data.getString(Tag.PatientID);
        Issuer issuer = issuerOfPatientID(data);
        Patient patient;
        try {
            patient = followMergedWith(findPatient(pid, issuer, storeParam));
            updatePatient(patient, data, storeParam);
        } catch (NonUniqueResultException e) {
            patient = createNewPatient(data, issuer, storeParam);
        } catch (NoResultException e) {
            patient = createNewPatient(data, issuer, storeParam);
            try {
                findPatient(pid, issuer, storeParam);
            } catch (NonUniqueResultException e2) {
                em.remove(patient);
                return findUniqueOrCreatePatient(data, storeParam);
            }
        }
        return patient;
    }

    public static void updatePatient(Patient patient, Attributes data,
            StoreParam storeParam) {
        Attributes patientAttrs = patient.getAttributes();
        AttributeFilter filter = storeParam.getAttributeFilter(Entity.Patient);
        if (patientAttrs.mergeSelected(data, filter.getSelection()))
            patient.setAttributes(patientAttrs, filter, storeParam.getFuzzyStr());
    }

    private Issuer issuerOfPatientID(Attributes data) {
        String iopid = data.getString(Tag.IssuerOfPatientID);
        Attributes qualifiers =
                data.getNestedDataset(Tag.IssuerOfPatientIDQualifiersSequence);
        if (iopid == null && qualifiers == null)
            return null;

        return issuerService.findOrCreate(new Issuer(iopid, qualifiers));
    }

    public Patient updateOrCreatePatient(Attributes data,
            StoreParam storeParam) {
        AttributeFilter filter = storeParam.getAttributeFilter(Entity.Patient);
        String pid = data.getString(Tag.PatientID);
        Issuer issuer = issuerOfPatientID(data);
        Patient patient;
        try {
            patient = findPatient(pid, issuer, storeParam);
            Patient mergedWith = patient.getMergedWith();
            if (mergedWith != null)
                throw new PatientMergedException("" + patient + " merged with " + mergedWith);
            if (issuer != null && patient.getIssuerOfPatientID() == null)
                patient.setIssuerOfPatientID(issuer);
            Attributes patientAttrs = patient.getAttributes();
            Attributes modified = new Attributes();
            if (patientAttrs.updateSelected(data, modified, filter.getSelection())) {
                patient.setAttributes(patientAttrs, filter, storeParam.getFuzzyStr());
            }
        } catch (NonUniqueResultException e) {
            throw new NonUniquePatientException(pid, issuer);
        } catch (NoResultException e) {
            patient = createNewPatient(data, issuer, storeParam);
            try {
                findPatient(pid, issuer, storeParam);
            } catch (NonUniqueResultException e2) {
                em.remove(patient);
                return updateOrCreatePatient(data, storeParam);
            }
        }
        return patient;
    }

    private static boolean equals(Issuer issuer1, Issuer issuer2) {
        if (issuer1 == issuer2)
            return true;

        String entityID1 = issuer1.getLocalNamespaceEntityID();
        if (entityID1 != null && entityID1.equals(issuer2.getLocalNamespaceEntityID()))
            return true;

        String entityUID1 = issuer1.getUniversalEntityID();
        String entityType1 = issuer1.getUniversalEntityIDType();
        if (entityUID1 != null && entityType1 != null
                && entityUID1.equals(issuer2.getUniversalEntityID())
                && entityType1.equals(issuer2.getUniversalEntityIDType()))
            return true;

        return false;
    }

    public void mergePatient(Attributes attrs, Attributes merged,
            StoreParam storeParam) {
        Patient mergedPat = updateOrCreatePatient(merged, storeParam);
        Patient pat = updateOrCreatePatient(attrs, storeParam);
        Collection<Study> studies = mergedPat.getStudies();
        if (studies != null)
            for (Study study : studies)
                study.setPatient(pat);
        Collection<Visit> visits = mergedPat.getVisits();
        if (visits != null)
            for (Visit visit : visits)
                visit.setPatient(pat);
        Collection<PerformedProcedureStep> ppss =
                mergedPat.getPerformedProcedureSteps();
        if (ppss != null)
            for (PerformedProcedureStep pps : ppss)
                pps.setPatient(pat);
        mergedPat.setMergedWith(pat);
    }
}
