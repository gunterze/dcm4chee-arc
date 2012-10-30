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
import org.dcm4chee.archive.common.IDWithIssuer;
import org.dcm4chee.archive.common.QueryParam;
import org.dcm4chee.archive.entity.Availability;
import org.dcm4chee.archive.entity.QPatient;
import org.dcm4chee.archive.entity.QSeries;
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
class SeriesQuery extends AbstractQuery {

    private Long studyPk;
    private Attributes studyAttrs;

    public SeriesQuery(QueryService queryService, IDWithIssuer[] pids,
            Attributes keys, QueryParam queryParam) {
        super(queryService, query(queryService.session(), pids, keys, queryParam), queryParam, false);
    }

    private static ScrollableResults query(StatelessSession session, IDWithIssuer[] pids,
            Attributes keys, QueryParam queryParam) {
        BooleanBuilder builder = new BooleanBuilder();
        Builder.addPatientLevelPredicates(builder, pids, keys, queryParam);
        Builder.addStudyLevelPredicates(builder, keys, queryParam);
        Builder.addSeriesLevelPredicates(builder, keys, queryParam);
        return new HibernateQuery(session)
            .from(QSeries.series)
            .innerJoin(QSeries.series.study, QStudy.study)
            .innerJoin(QStudy.study.patient, QPatient.patient)
            .where(builder)
            .scroll(ScrollMode.FORWARD_ONLY,
                QStudy.study.pk,
                QSeries.series.pk,
                QStudy.study.numberOfStudyRelatedSeries,
                QStudy.study.numberOfStudyRelatedInstances,
                QStudy.study.numberOfStudyRelatedRejectedInstances,
                QSeries.series.numberOfSeriesRelatedInstances,
                QSeries.series.numberOfSeriesRelatedRejectedInstances,
                QStudy.study.modalitiesInStudy,
                QStudy.study.sopClassesInStudy,
                QSeries.series.retrieveAETs,
                QSeries.series.externalRetrieveAET,
                QSeries.series.availability,
                QSeries.series.encodedAttributes,
                QStudy.study.encodedAttributes,
                QPatient.patient.encodedAttributes);
    }

    @Override
    protected Attributes toAttributes(ScrollableResults results) {
        Long studyPk = results.getLong(0);
        Long seriesPk = results.getLong(1);
        int[] numInsts = { results.getInteger(5), results.getInteger(6) };
        String retrieveAETs = results.getString(9);
        String externalRetrieveAET = results.getString(10);
        Availability availability = (Availability) results.get(11);
        byte[] seriesAttributes = results.getBinary(12);
        if (!studyPk.equals(this.studyPk)) {
            this.studyAttrs = toStudyAttributes(studyPk, results);
            this.studyPk = studyPk;
        }
        Attributes attrs = new Attributes(studyAttrs);
        Utils.decodeAttributes(attrs, seriesAttributes);
        if (numInsts[0] == -1)
            numInsts = queryService.seriesService()
                    .calculateNumberOfSeriesRelatedInstances(seriesPk);
        Utils.setSeriesQueryAttributes(attrs,
                queryParam.isHideRejectedInstances() ? numInsts[0]
                        : numInsts[0] + numInsts[1]);
        Utils.setRetrieveAET(attrs, retrieveAETs, externalRetrieveAET);
        Utils.setAvailability(attrs, availability);
        return attrs;
    }

    private Attributes toStudyAttributes(Long studyPk, ScrollableResults results) {
        int[] numInsts = { results.getInteger(2), results.getInteger(3), results.getInteger(4) };
        String modalitiesInStudy = results.getString(7);
        String sopClassesInStudy = results.getString(8);
        byte[] studyAttributes = results.getBinary(13);
        byte[] patientAttributes = results.getBinary(14);
        Attributes attrs = new Attributes();
        Utils.decodeAttributes(attrs, patientAttributes);
        Utils.decodeAttributes(attrs, studyAttributes);
        if (numInsts[1] == -1) {
            numInsts = queryService.seriesService()
                    .calculateNumberOfStudyRelatedSeriesAndInstances(studyPk);
        };
        Utils.setStudyQueryAttributes(attrs,
                numInsts[0],
                queryParam.isHideRejectedInstances() 
                        ? numInsts[1]
                        : numInsts[1] + numInsts[2],
                modalitiesInStudy,
                sopClassesInStudy);
        return attrs;
    }
}
