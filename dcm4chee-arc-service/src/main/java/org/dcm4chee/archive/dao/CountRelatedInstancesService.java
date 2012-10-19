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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Stateless;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import org.dcm4chee.archive.common.QueryParam;
import org.dcm4chee.archive.entity.QInstance;
import org.dcm4chee.archive.entity.QSeries;
import org.dcm4chee.archive.util.query.Builder;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.ejb.HibernateEntityManagerFactory;

import com.mysema.query.BooleanBuilder;
import com.mysema.query.jpa.hibernate.HibernateQuery;
import com.mysema.query.jpa.hibernate.HibernateSubQuery;
import com.mysema.query.types.Predicate;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@Stateless
public class CountRelatedInstancesService {

    @PersistenceUnit
    private EntityManagerFactory emf;

    private StatelessSession session;

    @PostConstruct
    public void init() {
        SessionFactory sessionFactory = ((HibernateEntityManagerFactory) emf).getSessionFactory();
        session = sessionFactory.openStatelessSession();
    }

    @PreDestroy
    public void destroy() {
        session.close();
    }

    public int[] countStudyRelatedSeriesAndInstances(Long studyPk,
            QueryParam queryParam) {
        return new int[] {
                countStudyRelatedSeries(studyPk, queryParam),
                countStudyRelatedInstances(studyPk, queryParam) };
    }

    public int countSeriesRelatedInstances(Long seriesPk, QueryParam queryParam) {
        return (int) new HibernateQuery(session)
            .from(QInstance.instance)
            .where(instancePredicate(QInstance.instance.series.pk.eq(seriesPk), queryParam))
            .count();
    }

    private int countStudyRelatedSeries(Long studyPk, QueryParam queryParam) {
        return (int) new HibernateQuery(session)
            .from(QSeries.series)
            .where(QSeries.series.study.pk.eq(studyPk),
                    instancesExists(QSeries.series, queryParam))
            .count();
    }

    private int countStudyRelatedInstances(Long studyPk, QueryParam queryParam) {
        return (int) new HibernateQuery(session)
            .from(QInstance.instance)
            .innerJoin(QInstance.instance.series, QSeries.series)
            .where(instancePredicate(QSeries.series.study.pk.eq(studyPk), queryParam))
            .count();
    }

    private Predicate instancesExists(QSeries series, QueryParam queryParam) {
        return new HibernateSubQuery()
            .from(QInstance.instance)
            .where(instancePredicate(QInstance.instance.series.eq(series), queryParam))
            .exists();
    }


    private Predicate instancePredicate(Predicate initial, QueryParam queryParam) {
        BooleanBuilder builder = new BooleanBuilder(initial);
        builder.and(QInstance.instance.replaced.isFalse());
        Builder.andNotInCodes(builder, QInstance.instance.conceptNameCode,
                queryParam.getHideConceptNameCodes());
        Builder.andNotInCodes(builder, QInstance.instance.rejectionCode,
                queryParam.getHideRejectionCodes());
        return builder;
    }
}
