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

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4chee.archive.common.StoreParam;
import org.dcm4chee.archive.conf.AttributeFilter;
import org.dcm4chee.archive.conf.Entity;
import org.dcm4chee.archive.entity.Issuer;
import org.dcm4chee.archive.entity.Patient;
import org.dcm4chee.archive.entity.RequestedProcedure;
import org.dcm4chee.archive.entity.ScheduledProcedureStep;
import org.dcm4chee.archive.entity.ScheduledStationAETitle;
import org.dcm4chee.archive.entity.ServiceRequest;
import org.dcm4chee.archive.entity.Visit;
import org.dcm4chee.archive.exception.EntityAlreadyExistsException;
import org.dcm4chee.archive.exception.PatientMismatchException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
@Stateless
public class RequestService {

    @PersistenceContext
    private EntityManager em;

    @EJB
    private IssuerService issuerService;

    public ScheduledProcedureStep findOrCreateScheduledProcedureStep(
            Attributes attrs, Patient patient, StoreParam storeParam) {
        return findOrCreateScheduleProcedureStep(attrs, patient, storeParam,
                false);
    }

    public ScheduledProcedureStep createScheduledProcedureStep(
            Attributes attrs, Patient patient, StoreParam storeParam) {
        return findOrCreateScheduleProcedureStep(attrs, patient, storeParam,
                true);
    }

    private Issuer issuer(Attributes item) {
        if (item == null)
            return null;

        return issuerService.findOrCreate(new Issuer(item));
    }


    private ScheduledProcedureStep findOrCreateScheduleProcedureStep(
            Attributes attrs, Patient patient, StoreParam storeParam,
            boolean create) {
        String spsid = attrs.getString(Tag.ScheduledProcedureStepID);
        String rpid = attrs.getString(Tag.RequestedProcedureID);
        String accno = attrs.getString(Tag.AccessionNumber);
        Issuer isserOfAccNo = issuer(
                attrs.getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
        try {
            ScheduledProcedureStep sps = findScheduledProcedureStep(
                    spsid, rpid, accno, isserOfAccNo);
            if (create)
                throw new EntityAlreadyExistsException(sps.toString());
            PatientMismatchException.check(sps, patient,
                    sps.getRequestedProcedure().getServiceRequest().getVisit().getPatient());
            return sps;
        } catch (NoResultException e) {
            return createScheduledProcedureStep(attrs, patient, 
                    storeParam, spsid, rpid, accno, isserOfAccNo);
        }
    }

    private ScheduledProcedureStep createScheduledProcedureStep(
            Attributes attrs, Patient patient, StoreParam storeParam,
            String spsid, String rpid, String accno, Issuer isserOfAccNo) {
        ScheduledProcedureStep sps = new ScheduledProcedureStep();
        RequestedProcedure rp =
            findOrCreateRequestedProcedure(attrs, patient, storeParam,
                    rpid, accno, isserOfAccNo);
        sps.setRequestedProcedure(rp);
        sps.setScheduledStationAETs(
                createScheduledStationAETs(attrs.getStrings(Tag.ScheduledStationAETitle)));
        sps.setAttributes(attrs, storeParam.getAttributeFilter(Entity.ScheduledProcedureStep),
                storeParam.getFuzzyStr());
        em.persist(sps);
        return sps;
    }

    private Collection<ScheduledStationAETitle> createScheduledStationAETs(
            String[] aets) {
        if (aets == null || aets.length == 0)
            return null;

        ArrayList<ScheduledStationAETitle> list =
                new ArrayList<ScheduledStationAETitle>(aets.length);
        for (String aet : aets)
            list.add(new ScheduledStationAETitle(aet));
        return list ;
    }

    private ScheduledProcedureStep findScheduledProcedureStep(
            String spsid, String rpid, String accno, Issuer issuerOfAccNo) {
        TypedQuery<ScheduledProcedureStep> query = em.createNamedQuery(
                 issuerOfAccNo != null
                     ? ScheduledProcedureStep.FIND_BY_SPS_ID_WITH_ISSUER
                     : ScheduledProcedureStep.FIND_BY_SPS_ID_WITHOUT_ISSUER,
                 ScheduledProcedureStep.class)
                 .setParameter(1, spsid)
                 .setParameter(2, rpid)
                 .setParameter(3, accno);
        if (issuerOfAccNo != null)
            query.setParameter(4, issuerOfAccNo);
        return query.getSingleResult();
    }

    private Visit getVisit( Attributes attrs,
            Patient patient, StoreParam storeParam) {
        String admissionID = attrs.getString(Tag.AdmissionID);
        Issuer issuerOfAdmissionID = issuer(
                attrs.getNestedDataset(Tag.IssuerOfAdmissionIDSequence));
        AttributeFilter filter = storeParam.getAttributeFilter(Entity.Visit);

        if (admissionID == null) {
            return newVisit(attrs, patient, issuerOfAdmissionID, filter);
        }

        try {
            Visit visit = findVisit(admissionID, issuerOfAdmissionID);
            PatientMismatchException.check(visit, patient, visit.getPatient());
            return visit;
        } catch (NoResultException e) {
            return newVisit(attrs, patient, issuerOfAdmissionID, filter);
        }
    }

    private Visit newVisit( Attributes attrs,
            Patient patient, Issuer issuerOfAdmissionID, AttributeFilter filter) {
        Visit visit = new Visit();
        visit.setPatient(patient);
        visit.setIssuerOfAdmissionID(issuerOfAdmissionID);
        visit.setAttributes(attrs, filter);
        em.persist(visit);
        return visit;
    }

    private Visit findVisit( String admissionID, Issuer issuer) {
        TypedQuery<Visit> query = em.createNamedQuery(
                issuer != null
                    ? Visit.FIND_BY_ADMISSION_ID_WITH_ISSUER
                    : Visit.FIND_BY_ADMISSION_ID_WITHOUT_ISSUER,
                Visit.class)
                .setParameter(1, admissionID);
        if (issuer != null)
            query.setParameter(2, issuer);
        return query.getSingleResult();
    }

    private RequestedProcedure findOrCreateRequestedProcedure(
            Attributes attrs, Patient patient, StoreParam storeParam,
            String rpid, String accno, Issuer issuerOfAccNo) {
        try {
            RequestedProcedure rp = findRequestedProcedure(rpid, accno, issuerOfAccNo);
            PatientMismatchException.check(rp, patient,
                    rp.getServiceRequest().getVisit().getPatient());
            return rp;
        } catch (NoResultException e) {
            ServiceRequest rq = getServiceRequest(attrs, patient, storeParam,
                    accno, issuerOfAccNo);
            RequestedProcedure rp = new RequestedProcedure();
            rp.setServiceRequest(rq);
            rp.setAttributes(attrs, storeParam.getAttributeFilter(Entity.RequestedProcedure));
            em.persist(rp);
            return rp;
        }
    }

    private RequestedProcedure findRequestedProcedure(
            String rpid, String accno, Issuer issuerOfAccNo) {
        TypedQuery<RequestedProcedure> query = em.createNamedQuery(
                issuerOfAccNo != null
                    ? RequestedProcedure.FIND_BY_REQUESTED_PROCEDURE_ID_WITH_ISSUER
                    : RequestedProcedure.FIND_BY_REQUESTED_PROCEDURE_ID_WITHOUT_ISSUER,
                    RequestedProcedure.class)
                .setParameter(1, rpid)
                .setParameter(2, accno);
       if (issuerOfAccNo != null)
           query.setParameter(3, issuerOfAccNo);
       return query.getSingleResult();
    }

    private ServiceRequest getServiceRequest(
            Attributes attrs, Patient patient, StoreParam storeParam,
            String accno, Issuer issuerOfAccNo) {
        try {
            ServiceRequest request = findServiceRequest(accno, issuerOfAccNo);
            PatientMismatchException.check(request, patient,
                    request.getVisit().getPatient());
            return request;
        } catch (NoResultException e) {
            ServiceRequest request = new ServiceRequest();
            Visit visit = getVisit(attrs, patient, storeParam);
            request.setVisit(visit);
            request.setIssuerOfAccessionNumber(issuerOfAccNo);
            request.setAttributes(attrs, storeParam.getAttributeFilter(Entity.ServiceRequest),
                    storeParam.getFuzzyStr());
            em.persist(request);
            return request;
        }
    }

    private ServiceRequest findServiceRequest(
            String accno, Issuer issuerOfAccNo) {
        TypedQuery<ServiceRequest> query = em.createNamedQuery(
                issuerOfAccNo != null
                    ? ServiceRequest.FIND_BY_ACCESSION_NUMBER_WITH_ISSUER
                    : ServiceRequest.FIND_BY_ACCESSION_NUMBER_WITHOUT_ISSUER,
                    ServiceRequest.class)
                .setParameter(1, accno);
        if (issuerOfAccNo != null)
            query.setParameter(2, issuerOfAccNo);
        return query.getSingleResult();
    }

}
