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
package org.dcm4chee.archive.series.impl;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.dcm4che.data.Attributes;
import org.dcm4chee.archive.conf.QueryParam;
import org.dcm4chee.archive.entity.Availability;
import org.dcm4chee.archive.entity.Instance;
import org.dcm4chee.archive.entity.PatientStudySeriesAttributes;
import org.dcm4chee.archive.entity.QueryPatientStudySeriesAttributes;
import org.dcm4chee.archive.entity.Series;
import org.dcm4chee.archive.entity.Study;
import org.dcm4chee.archive.series.SeriesService;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@Stateless
public class SeriesServiceBean implements SeriesService {

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Override
    public Attributes getAttributes(Long seriesPk) {
        PatientStudySeriesAttributes result = (PatientStudySeriesAttributes)
                em.createNamedQuery(Series.PATIENT_STUDY_SERIES_ATTRIBUTES)
                  .setParameter(1, seriesPk)
                  .getSingleResult();
        return result.getAttributes();
    }

    @Override
    public Attributes getAttributes(Long seriesPk, QueryParam queryParam) {
        QueryPatientStudySeriesAttributes result = (QueryPatientStudySeriesAttributes)
                em.createNamedQuery(Series.QUERY_PATIENT_STUDY_SERIES_ATTRIBUTES)
                  .setParameter(1, seriesPk)
                  .getSingleResult();
        Attributes attrs = result.getAttributes();
        if (result.isNumberOfSeriesRelatedInstancesInitialized())
            result.initNumberOfSeriesRelatedInstances(
                    calculateNumberOfSeriesRelatedInstances(seriesPk));
        if (result.isNumberOfStudyRelatedInstancesInitialized())
            result.initNumberOfStudyRelatedInstances(
                    calculateNumberOfStudyRelatedInstances(result.getStudyPk()));
        result.setQueryAttributes(attrs, queryParam.isShowRejectedInstances());
        return attrs;
    }

    @Override
    public int[] calculateNumberOfSeriesRelatedInstances(Long seriesPk) {
        int num = em.createNamedQuery(Instance.NUMBER_OF_SERIES_RELATED_INSTANCES, Long.class)
                .setParameter(1, seriesPk)
                .setParameter(2, Availability.OFFLINE)
                .getSingleResult().intValue();
        int numA = em.createNamedQuery(Instance.NUMBER_OF_SERIES_RELATED_INSTANCES, Long.class)
                .setParameter(1, seriesPk)
                .setParameter(2, Availability.REJECTED_FOR_QUALITY_REASONS)
                .getSingleResult().intValue();
        em.createNamedQuery(Series.UPDATE_NUMBER_OF_INSTANCES)
            .setParameter(1, num)
            .setParameter(2, numA)
            .setParameter(3, seriesPk)
            .executeUpdate();
        return new int[] { num, numA };
    }

    @Override
    public int[] calculateNumberOfStudyRelatedInstances(Long studyPk) {
        int numSeries = em.createNamedQuery(Series.NUMBER_OF_SERIES, Long.class)
                .setParameter(1, studyPk)
                .setParameter(2, Availability.OFFLINE)
                .getSingleResult().intValue();
        int numSeriesA = em.createNamedQuery(Series.NUMBER_OF_SERIES, Long.class)
                .setParameter(1, studyPk)
                .setParameter(2, Availability.REJECTED_FOR_QUALITY_REASONS)
                .getSingleResult().intValue();
        int numInstances = em.createNamedQuery(
                    Instance.NUMBER_OF_STUDY_RELATED_INSTANCES, Long.class)
                .setParameter(1, studyPk)
                .setParameter(2, Availability.OFFLINE)
                .getSingleResult().intValue();
        int numInstancesA = em.createNamedQuery(
                    Instance.NUMBER_OF_STUDY_RELATED_INSTANCES, Long.class)
                .setParameter(1, studyPk)
                .setParameter(2, Availability.REJECTED_FOR_QUALITY_REASONS)
                .getSingleResult().intValue();
        em.createNamedQuery(Study.UPDATE_NUMBER_OF_INSTANCES)
            .setParameter(1, numSeries)
            .setParameter(2, numSeriesA)
            .setParameter(3, numInstances)
            .setParameter(4, numInstancesA)
            .setParameter(5, studyPk)
            .executeUpdate();
        return new int[] { numSeries, numSeriesA, numInstances, numInstancesA };
    }

}
