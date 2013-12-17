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

package org.dcm4chee.archive.query.impl;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.IDWithIssuer;
import org.dcm4chee.archive.entity.Availability;
import org.dcm4chee.archive.entity.QPatient;
import org.dcm4chee.archive.entity.QStudy;
import org.dcm4chee.archive.entity.Utils;
import org.dcm4chee.archive.query.builder.QueryBuilder;
import org.hibernate.ScrollableResults;

import com.mysema.query.BooleanBuilder;
import com.mysema.query.jpa.hibernate.HibernateQuery;
import com.mysema.query.types.Expression;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
class StudyQuery extends AbstractQuery {

    private static final Expression<?>[] SELECT = {
        QStudy.study.pk,                        // (0)
        QStudy.study.numberOfSeries,            // (1)
        QStudy.study.numberOfSeriesA,           // (2)
        QStudy.study.numberOfInstances,         // (3)
        QStudy.study.numberOfInstancesA,        // (4)
        QStudy.study.modalitiesInStudy,         // (5)
        QStudy.study.sopClassesInStudy,         // (6)
        QStudy.study.retrieveAETs,              // (7)
        QStudy.study.externalRetrieveAET,       // (8)
        QStudy.study.availability,              // (9)
        QStudy.study.encodedAttributes,         // (10)
        QPatient.patient.encodedAttributes      // (11)
    };

    public StudyQuery(QueryServiceBean qsf) {
        super(qsf);
    }

    @Override
    protected Expression<?>[] select() {
        return SELECT;
    }

    @Override
    protected HibernateQuery createQuery(IDWithIssuer[] pids, Attributes keys) {
        BooleanBuilder builder = new BooleanBuilder();
        QueryBuilder.addPatientLevelPredicates(builder, pids, keys, queryParam);
        QueryBuilder.addStudyLevelPredicates(builder, keys, queryParam);
        return new HibernateQuery(session)
            .from(QStudy.study)
            .innerJoin(QStudy.study.patient, QPatient.patient)
            .where(builder);
    }

    @Override
    public Attributes toAttributes(ScrollableResults results) {
        Long studyPk = results.getLong(0);
        int[] a = {
                results.getInteger(1),  // study.numberOfSeries
                results.getInteger(2),  // study.numberOfSeriesA
                results.getInteger(3),  // study.numberOfInstances
                results.getInteger(4)}; // study.numberOfInstancesA
        String modalitiesInStudy = results.getString(5);
        String sopClassesInStudy = results.getString(6);
        String retrieveAETs = results.getString(7);
        String externalRetrieveAET = results.getString(8);
        Availability availability = (Availability) results.get(9);
        byte[] studyAttributes = results.getBinary(10);
        byte[] patientAttributes = results.getBinary(11);
        Attributes attrs = new Attributes();
        Utils.decodeAttributes(attrs, patientAttributes);
        Utils.decodeAttributes(attrs, studyAttributes);
        if (a[2] == -1)
            a = service.getSeriesService()
                    .calculateNumberOfStudyRelatedInstances(studyPk);

        boolean showRejectedInstances = queryParam.isShowRejectedInstances();
        int numberOfStudyRelatedInstances = showRejectedInstances ? a[3] : a[2];

        // skip match for empty Study
        if (numberOfStudyRelatedInstances == 0)
            return null;

        Utils.setStudyQueryAttributes(attrs,
                showRejectedInstances ? a[1] : a[0],
                numberOfStudyRelatedInstances,
                modalitiesInStudy,
                sopClassesInStudy);
        Utils.setRetrieveAET(attrs, retrieveAETs, externalRetrieveAET);
        Utils.setAvailability(attrs, availability);

        return attrs;
    }

}
