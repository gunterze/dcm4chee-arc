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
        super(queryService, query(queryService.session(), pids, keys, queryParam),
                queryParam, false,
                QStudy.study.pk,                         // (0)
                QSeries.series.pk,                       // (1)
                QStudy.study.numberOfSeries,             // (2)
                QStudy.study.numberOfSeriesA,            // (3)
                QStudy.study.numberOfInstances,          // (4)
                QStudy.study.numberOfInstancesA,         // (5)
                QSeries.series.numberOfInstances,        // (6)
                QSeries.series.numberOfInstancesA,       // (7)
                QStudy.study.modalitiesInStudy,          // (8)
                QStudy.study.sopClassesInStudy,          // (9)
                QSeries.series.retrieveAETs,             // (10)
                QSeries.series.externalRetrieveAET,      // (11)
                QSeries.series.availability,             // (12)
                QSeries.series.encodedAttributes,        // (13)
                QStudy.study.encodedAttributes,          // (14)
                QPatient.patient.encodedAttributes);     // (15)
    }

    private static HibernateQuery query(StatelessSession session, IDWithIssuer[] pids,
            Attributes keys, QueryParam queryParam) {
        BooleanBuilder builder = new BooleanBuilder();
        Builder.addPatientLevelPredicates(builder, pids, keys, queryParam);
        Builder.addStudyLevelPredicates(builder, keys, queryParam);
        Builder.addSeriesLevelPredicates(builder, keys, queryParam);
        return new HibernateQuery(session)
            .from(QSeries.series)
            .innerJoin(QSeries.series.study, QStudy.study)
            .innerJoin(QStudy.study.patient, QPatient.patient)
            .where(builder);
    }

    @Override
    protected Attributes toAttributes(ScrollableResults results) {
        Long studyPk = results.getLong(0);
        Long seriesPk = results.getLong(1);
        int[] a = {
                results.getInteger(6),  // series.numberOfInstances
                results.getInteger(7)}; // series.numberOfInstancesA
        String retrieveAETs = results.getString(10);
        String externalRetrieveAET = results.getString(11);
        Availability availability = (Availability) results.get(12);
        byte[] seriesAttributes = results.getBinary(13);
        if (!studyPk.equals(this.studyPk)) {
            this.studyAttrs = toStudyAttributes(studyPk, results);
            this.studyPk = studyPk;
        }
        Attributes attrs = new Attributes(studyAttrs);
        Utils.decodeAttributes(attrs, seriesAttributes);
        if (a[0] == -1)
            a = queryService.seriesService()
                    .calculateNumberOfSeriesRelatedInstances(seriesPk);
        int numberOfSeriesRelatedInstances = 
                queryParam.isShowRejectedInstances() ? a[1] : a[0];

        // skip match for empty Series
        if (numberOfSeriesRelatedInstances == 0)
            return null;

        Utils.setSeriesQueryAttributes(attrs,
                numberOfSeriesRelatedInstances);
        Utils.setRetrieveAET(attrs, retrieveAETs, externalRetrieveAET);
        Utils.setAvailability(attrs, availability);

        return attrs;
    }

    private Attributes toStudyAttributes(Long studyPk, ScrollableResults results) {
        int[] a = {
                results.getInteger(2),  // study.numberOfSeries
                results.getInteger(3),  // study.numberOfSeriesA
                results.getInteger(4),  // study.numberOfInstances
                results.getInteger(5)}; // study.numberOfInstancesA
        String modalitiesInStudy = results.getString(8);
        String sopClassesInStudy = results.getString(9);
        byte[] studyAttributes = results.getBinary(14);
        byte[] patientAttributes = results.getBinary(15);
        Attributes attrs = new Attributes();
        Utils.decodeAttributes(attrs, patientAttributes);
        Utils.decodeAttributes(attrs, studyAttributes);
        if ((a[0] | a[1] | a[2] | a[3]) < 0)
            a = queryService.seriesService()
                    .calculateNumberOfStudyRelatedInstances(studyPk);

        boolean showRejectedInstances = queryParam.isShowRejectedInstances();
        Utils.setStudyQueryAttributes(attrs,
                showRejectedInstances ? a[1] : a[0],
                queryParam.isShowRejectedInstances() ? a[3] : a[2],
                modalitiesInStudy,
                sopClassesInStudy);

        return attrs;
    }
}
