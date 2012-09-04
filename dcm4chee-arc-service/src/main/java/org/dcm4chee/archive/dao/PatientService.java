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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import org.dcm4chee.archive.common.IDWithIssuer;
import org.dcm4chee.archive.common.StoreParam;
import org.dcm4chee.archive.conf.AttributeFilter;
import org.dcm4chee.archive.conf.Entity;
import org.dcm4chee.archive.entity.Issuer;
import org.dcm4chee.archive.entity.Patient;
import org.dcm4chee.archive.entity.PerformedProcedureStep;
import org.dcm4chee.archive.entity.Study;
import org.dcm4chee.archive.entity.Visit;
import org.dcm4chee.archive.exception.NonUniquePatientException;
import org.dcm4chee.archive.exception.PatientCircularMergedException;
import org.dcm4chee.archive.exception.PatientMergedException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
@Stateless
public class PatientService {

    @PersistenceContext
    private EntityManager em;

    @EJB
    private IssuerService issuerService;

    public Patient findUniqueOrCreatePatient(Attributes data,
            StoreParam storeParam, boolean followMergedWith,
            boolean mergeAttributes) {
        IDWithIssuer pid = IDWithIssuer.pidWithIssuer(data, null);
        if (pid == null)
            return createNewPatient(data, null, storeParam);

        Patient patient;
        try {
            patient = findPatient(pid);
            Patient mergedWith = patient.getMergedWith();
            if (mergedWith != null)
                if (followMergedWith)
                    patient = followMergedWith(patient);
                else
                    throw new PatientMergedException(
                            "" + patient + " merged with " + mergedWith);
            if (mergeAttributes)
                mergeAttributes(patient, data, pid, storeParam);
        } catch (NonUniqueResultException e) {
            patient = createNewPatient(data, pid, storeParam);
        } catch (NoResultException e) {
            patient = createNewPatient(data, pid, storeParam);
            // check if patient was inserted concurrently
            try {
                findPatient(pid);
            } catch (NonUniqueResultException e2) {
                em.remove(patient);
                return findUniqueOrCreatePatient(
                        data, storeParam, followMergedWith, mergeAttributes);
            }
        }
        return patient;
    }

    private Patient followMergedWith(Patient patient) {
        ArrayList<Patient> mergedPatients = new ArrayList<Patient>();
        Patient mergedWith;
        while ((mergedWith = patient.getMergedWith()) != null) {
            mergedPatients.add(patient);
            for (Patient mergedPatient : mergedPatients)
                if (mergedPatient == mergedWith)
                    throw new PatientCircularMergedException(
                            "" + patient + " circular merged with " + mergedWith);
            patient = mergedWith;
        };
        return patient;
    }

    public void mergeAttributes(Patient patient, Attributes data,
            StoreParam storeParam) {
        Attributes patientAttrs = patient.getAttributes();
        AttributeFilter filter = storeParam.getAttributeFilter(Entity.Patient);
        if (patientAttrs.mergeSelected(data, filter.getSelection())) {
            if (patient.getIssuerOfPatientID() == null) {
                IDWithIssuer pid = IDWithIssuer.pidWithIssuer(data, null);
                patient.setIssuerOfPatientID(findOrCreateIssuer(pid));
            }
            patient.setAttributes(patientAttrs, filter, storeParam.getFuzzyStr());
        }
    }

    private void mergeAttributes(Patient patient, Attributes data,
            IDWithIssuer pid, StoreParam storeParam) {
        Attributes patientAttrs = patient.getAttributes();
        AttributeFilter filter = storeParam.getAttributeFilter(Entity.Patient);
        if (patientAttrs.mergeSelected(data, filter.getSelection())) {
            if (patient.getIssuerOfPatientID() == null) {
                patient.setIssuerOfPatientID(findOrCreateIssuer(pid));
            }
            patient.setAttributes(patientAttrs, filter, storeParam.getFuzzyStr());
        }
    }

    private Issuer findOrCreateIssuer(IDWithIssuer pid) {
        if (pid == null || pid.issuer == null)
            return null;

        return issuerService.findOrCreate(new Issuer(pid.issuer));
    }

    private Patient createNewPatient(Attributes attrs, IDWithIssuer pid,
            StoreParam storeParam) {
        Patient patient = new Patient();
        patient.setIssuerOfPatientID(findOrCreateIssuer(pid));
        patient.setAttributes(attrs,
                storeParam.getAttributeFilter(Entity.Patient),
                storeParam.getFuzzyStr());
        em.persist(patient);
        return patient;
    }

    public Patient updateOrCreatePatient(Attributes data,
            StoreParam storeParam) {
        AttributeFilter filter = storeParam.getAttributeFilter(Entity.Patient);
        IDWithIssuer pid = IDWithIssuer.pidWithIssuer(data, null);
        Patient patient;
        try {
            patient = findPatient(pid);
            Patient mergedWith = patient.getMergedWith();
            if (mergedWith != null)
                throw new PatientMergedException("" + patient + " merged with " + mergedWith);
            Attributes patientAttrs = patient.getAttributes();
            Attributes modified = new Attributes();
            if (patientAttrs.updateSelected(data, modified, filter.getSelection())) {
                if (pid != null && pid.issuer != null)
                    patient.setIssuerOfPatientID(
                            findOrCreateIssuer(pid));
                patient.setAttributes(patientAttrs, filter, storeParam.getFuzzyStr());
            }
        } catch (NonUniqueResultException e) {
            throw new NonUniquePatientException(pid);
        } catch (NoResultException e) {
            patient = createNewPatient(data, pid, storeParam);
            try {
                findPatient(pid);
            } catch (NonUniqueResultException e2) {
                em.remove(patient);
                return updateOrCreatePatient(data, storeParam);
            }
        }
        return patient;
    }

    public void mergePatient(Attributes attrs, Attributes priorAttrs,
            StoreParam storeParam) {
        Patient prior = updateOrCreatePatient(priorAttrs, storeParam);
        Patient pat = updateOrCreatePatient(attrs, storeParam);
        mergePatient(pat, prior);
    }

    private void mergePatient(Patient pat, Patient prior) {
        if (pat == prior)
            throw new PatientCircularMergedException("Cannot merge "
                    + pat + " with itselfs");
        Collection<Study> studies = prior.getStudies();
        if (studies != null)
            for (Study study : studies)
                study.setPatient(pat);
        Collection<Visit> visits = prior.getVisits();
        if (visits != null)
            for (Visit visit : visits)
                visit.setPatient(pat);
        Collection<PerformedProcedureStep> ppss =
                prior.getPerformedProcedureSteps();
        if (ppss != null)
            for (PerformedProcedureStep pps : ppss)
                pps.setPatient(pat);
        prior.setMergedWith(pat);
    }

    public List<Patient> findPatients(IDWithIssuer pid) {
        if (pid.id == null)
            throw new IllegalArgumentException("Missing pid");

        TypedQuery<Patient> query = em.createNamedQuery(
                    Patient.FIND_BY_PATIENT_ID, Patient.class)
                .setParameter(1, pid.id);
        List<Patient> list = query.getResultList();
        if (pid.issuer != null) {
            for (Iterator<Patient> it = list.iterator(); it.hasNext();) {
                Patient pat = (Patient) it.next();
                Issuer issuer2 = pat.getIssuerOfPatientID();
                if (issuer2 != null) {
                    if (issuer2.matches(pid.issuer))
                        return Collections.singletonList(pat);
                    else
                        it.remove();
                }
            }
        }
        return list;
    }

    private Patient findPatient(IDWithIssuer pid) {
        List<Patient> list = findPatients(pid);
        if (list.isEmpty())
            throw new NoResultException();
        if (list.size() > 1)
            throw new NonUniqueResultException();
        return list.get(0);
    }

    public Patient deletePatient(IDWithIssuer pid) {
        List<Patient> list = findPatients(pid);
        if (list.isEmpty())
            return null;
        if (list.size() > 1)
            throw new NonUniqueResultException();
        Patient patient = list.get(0);
        em.remove(patient);
        return patient;
    }

}
