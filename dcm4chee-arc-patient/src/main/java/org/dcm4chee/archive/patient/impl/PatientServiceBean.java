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

package org.dcm4chee.archive.patient.impl;

import java.util.ArrayList;
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
import org.dcm4che.data.IDWithIssuer;
import org.dcm4che.soundex.FuzzyStr;
import org.dcm4chee.archive.conf.AttributeFilter;
import org.dcm4chee.archive.entity.Issuer;
import org.dcm4chee.archive.entity.Patient;
import org.dcm4chee.archive.issuer.IssuerService;
import org.dcm4chee.archive.patient.PatientCircularMergedException;
import org.dcm4chee.archive.patient.PatientMergedException;
import org.dcm4chee.archive.patient.PatientService;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@Stateless
public class PatientServiceBean implements PatientService {

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @EJB
    private IssuerService issuerService;

    @Override
    public Patient findUniqueOrCreatePatient(
            AttributeFilter filter, FuzzyStr fuzzyStr,
            Attributes data, boolean followMergedWith, boolean mergeAttributes) {
        try {
            Patient patient = findUniqueOrCreatePatient(em, filter, fuzzyStr,
                    data, followMergedWith, mergeAttributes);
            return patient;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } 
    }

    private Patient findUniqueOrCreatePatient(EntityManager em,
            AttributeFilter filter, FuzzyStr fuzzyStr, Attributes data,
            boolean followMergedWith, boolean mergeAttributes) {
        IDWithIssuer pid = IDWithIssuer.fromPatientIDWithIssuer(data);
        if (pid == null)
            return createNewPatient(em, filter, fuzzyStr, data, null);

        Patient patient;
        try {
            patient = findPatient(em, pid);
            Patient mergedWith = patient.getMergedWith();
            if (mergedWith != null)
                if (followMergedWith)
                    patient = followMergedWith(patient);
                else
                    throw new PatientMergedException(
                            "" + patient + " merged with " + mergedWith);
            if (mergeAttributes)
                mergeAttributes(filter, fuzzyStr, patient, data, pid);
        } catch (NonUniqueResultException e) {
            patient = createNewPatient(em, filter, fuzzyStr, data, pid);
        } catch (NoResultException e) {
            patient = createNewPatient(em, filter, fuzzyStr, data, pid);
            // TO DO check if patient was inserted concurrently
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

    private Issuer findOrCreateIssuer(IDWithIssuer pid) {
        if (pid == null || pid.getIssuer() == null)
            return null;

        return issuerService.findOrCreate(new Issuer(pid.getIssuer()));
    }

    private void mergeAttributes(AttributeFilter filter, FuzzyStr fuzzyStr,
            Patient patient, Attributes data, IDWithIssuer pid) {
        Attributes patientAttrs = patient.getAttributes();
        if (patientAttrs.mergeSelected(data, filter.getSelection())) {
            if (patient.getIssuerOfPatientID() == null) {
                patient.setIssuerOfPatientID(findOrCreateIssuer(pid));
            }
            patient.setAttributes(patientAttrs, filter, fuzzyStr);
        }
    }

    private Patient createNewPatient(EntityManager em,
            AttributeFilter filter, FuzzyStr fuzzyStr,
            Attributes attrs, IDWithIssuer pid) {
        Patient patient = new Patient();
        patient.setIssuerOfPatientID(findOrCreateIssuer(pid));
        patient.setAttributes(attrs, filter, fuzzyStr);
        em.persist(patient);
        return patient;
    }

    private List<Patient> findPatients(EntityManager em, IDWithIssuer pid) {
        if (pid.getID() == null)
            throw new IllegalArgumentException("Missing pid");

        TypedQuery<Patient> query = em.createNamedQuery(
                    Patient.FIND_BY_PATIENT_ID, Patient.class)
                .setParameter(1, pid.getID());
        List<Patient> list = query.getResultList();
        if (pid.getIssuer() != null) {
            for (Iterator<Patient> it = list.iterator(); it.hasNext();) {
                Patient pat = (Patient) it.next();
                Issuer issuer2 = pat.getIssuerOfPatientID();
                if (issuer2 != null) {
                    if (issuer2.matches(pid.getIssuer()))
                        return Collections.singletonList(pat);
                    else
                        it.remove();
                }
            }
        }
        return list;
    }

    private Patient findPatient(EntityManager em, IDWithIssuer pid) {
        List<Patient> list = findPatients(em, pid);
        if (list.isEmpty())
            throw new NoResultException();
        if (list.size() > 1)
            throw new NonUniqueResultException();
        return list.get(0);
    }

}
