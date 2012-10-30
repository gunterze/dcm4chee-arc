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

package org.dcm4chee.archive.query.dao;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4chee.archive.common.IDWithIssuer;
import org.dcm4chee.archive.common.QueryParam;
import org.dcm4chee.archive.entity.Availability;
import org.dcm4chee.archive.entity.QPatient;
import org.dcm4chee.archive.entity.QStudy;
import org.dcm4chee.archive.entity.Utils;
import org.dcm4chee.archive.util.query.Builder;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.StatelessSession;

import com.mysema.query.BooleanBuilder;
import com.mysema.query.jpa.hibernate.HibernateQuery;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
class StudyQuery extends AbstractQuery {

    public StudyQuery(QueryService queryService, IDWithIssuer[] pids,
            Attributes keys, QueryParam queryParam) {
        super(queryService, query(queryService.session(), pids, keys, queryParam),
                queryParam, false);
    }

    private static ScrollableResults query(StatelessSession session, IDWithIssuer[] pids,
            Attributes keys, QueryParam queryParam) {
        BooleanBuilder builder = new BooleanBuilder();
        Builder.addPatientLevelPredicates(builder, pids, keys, queryParam);
        Builder.addStudyLevelPredicates(builder, keys, queryParam);
        return new HibernateQuery(session)
            .from(QStudy.study)
            .innerJoin(QStudy.study.patient, QPatient.patient)
            .where(builder)
            .scroll(ScrollMode.FORWARD_ONLY,
                QStudy.study.pk,
                QStudy.study.numberOfStudyRelatedSeries,
                QStudy.study.numberOfStudyRelatedInstances,
                QStudy.study.modalitiesInStudy,
                QStudy.study.sopClassesInStudy,
                QStudy.study.retrieveAETs,
                QStudy.study.externalRetrieveAET,
                QStudy.study.availability,
                QStudy.study.encodedAttributes,
                QPatient.patient.encodedAttributes);
    }

    @Override
    protected Attributes toAttributes(ScrollableResults results) {
        Long studyPk = results.getLong(0);
        int studyRelatedSeries = results.getInteger(1);
        int studyRelatedInstances = results.getInteger(2);
        String modalitiesInStudy = results.getString(3);
        String sopClassesInStudy = results.getString(4);
        String retrieveAETs = results.getString(5);
        String externalRetrieveAET = results.getString(6);
        Availability availability = (Availability) results.get(7);
        byte[] studyAttributes = results.getBinary(8);
        byte[] patientAttributes = results.getBinary(9);
        Attributes attrs = new Attributes();
        Utils.decodeAttributes(attrs, patientAttributes);
        Utils.decodeAttributes(attrs, studyAttributes);
        if (studyRelatedInstances == -1) {
            int[] a = queryService.seriesService()
                    .calculateNumberOfStudyRelatedSeriesAndInstances(studyPk);
            studyRelatedSeries = a[0];
            studyRelatedInstances = a[1];
        };
        Utils.setStudyQueryAttributes(attrs,
                studyRelatedSeries,
                studyRelatedInstances,
                modalitiesInStudy,
                sopClassesInStudy);
        Utils.setRetrieveAET(attrs, retrieveAETs, externalRetrieveAET);
        Utils.setAvailability(attrs, availability);

        // skip match for empty Study
        if (attrs.getInt(Tag.NumberOfStudyRelatedInstances, 0) == 0)
            return null;

        return attrs;
    }

}
