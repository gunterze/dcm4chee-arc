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

import java.util.NoSuchElementException;

import org.dcm4che.data.Attributes;
import org.dcm4chee.archive.entity.QInstance;
import org.dcm4chee.archive.entity.QSeries;
import org.dcm4chee.archive.entity.Utils;
import org.dcm4chee.archive.query.util.Builder;
import org.dcm4chee.archive.query.util.QueryParam;
import org.hibernate.Query;
import org.hibernate.ScrollableResults;
import org.hibernate.StatelessSession;

import com.mysema.query.BooleanBuilder;
import com.mysema.query.jpa.hibernate.HibernateQuery;
import com.mysema.query.jpa.hibernate.HibernateSubQuery;
import com.mysema.query.types.Predicate;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
abstract class AbstractQuery {

    private static final String UPDATE_STUDY = "update Study s "
            + "set s.numberOfStudyRelatedSeries = ?, "
                + "s.numberOfStudyRelatedInstances = ? "
            + "where s.pk = ?";

    private static final String UPDATE_SERIES = "update Series s "
            + "set s.numberOfSeriesRelatedInstances = ? "
            + "where s.pk = ?";

    protected final StatelessSession session;
    private final ScrollableResults results;
    private final QueryParam queryParam;
    private final boolean optionalKeyNotSupported;

    private CachedNumber numberOfStudyRelatedSeries;
    private CachedNumber numberOfStudyRelatedInstances;
    private CachedNumber numberOfSeriesRelatedInstances;
    private Query updateStudy;
    private Query updateSeries;

    private boolean hasNext;


    protected AbstractQuery(StatelessSession session, ScrollableResults results,
            QueryParam queryParam, boolean optionalKeyNotSupported) {
        this.session = session;
        this.results = results;
        this.queryParam = queryParam;
        this.optionalKeyNotSupported = optionalKeyNotSupported;
        hasNext = results.next();
    }

    public final boolean optionalKeyNotSupported() {
        return optionalKeyNotSupported;
    }

    public boolean hasMoreMatches() {
        return hasNext;
    }

    public Attributes nextMatch() {
        if (!hasNext)
            throw new NoSuchElementException();
        Attributes attrs = toAttributes(results);
        hasNext = results.next();
        return attrs;
    }

    protected abstract  Attributes toAttributes(ScrollableResults results);

    protected void setStudyQueryAttributes(Long studyPk, Attributes attrs,
            int numberOfStudyRelatedSeries,
            int numberOfStudyRelatedInstances,
            String modalitiesInStudy,
            String sopClassesInStudy) {
        Boolean rejectedInstances = null;
        if (numberOfStudyRelatedSeries == -1) {
            if (this.numberOfStudyRelatedSeries != null
                    && this.numberOfStudyRelatedSeries.pk == studyPk)
                numberOfStudyRelatedSeries = this.numberOfStudyRelatedSeries.n;
            else {
                rejectedInstances = hasStudyRejectedInstances(studyPk);
                numberOfStudyRelatedSeries =
                        (int) countStudyRelatedSeriesOf(studyPk, rejectedInstances);
                this.numberOfStudyRelatedSeries =
                        new CachedNumber(studyPk, numberOfStudyRelatedSeries);
            }
        }
        if (numberOfStudyRelatedInstances == -1) {
            if (this.numberOfStudyRelatedInstances != null
                    && this.numberOfStudyRelatedInstances.pk == studyPk)
                numberOfStudyRelatedInstances = this.numberOfStudyRelatedInstances.n;
            else {
                if (rejectedInstances == null)
                    rejectedInstances = hasStudyRejectedInstances(studyPk);
                numberOfStudyRelatedInstances =
                        (int) countStudyRelatedInstancesOf(studyPk, rejectedInstances);
                this.numberOfStudyRelatedInstances =
                        new CachedNumber(studyPk, numberOfStudyRelatedInstances);
            }
        }
        if (rejectedInstances != null && !rejectedInstances)
            updateStudy()
                .setInteger(0, numberOfStudyRelatedSeries)
                .setInteger(1, numberOfStudyRelatedInstances)
                .setLong(2, studyPk)
                .executeUpdate();
        Utils.setStudyQueryAttributes(attrs,
                numberOfStudyRelatedSeries,
                numberOfStudyRelatedInstances,
                modalitiesInStudy,
                sopClassesInStudy);
    }

    protected void setSeriesQueryAttributes(Long seriesPk, Attributes attrs,
            int numberOfSeriesRelatedInstances) {
        if (numberOfSeriesRelatedInstances == -1) {
            if (this.numberOfSeriesRelatedInstances != null
                    && this.numberOfSeriesRelatedInstances.pk == seriesPk)
                numberOfSeriesRelatedInstances = this.numberOfSeriesRelatedInstances.n;
            else {
                boolean rejectedInstances = hasSeriesRejectedInstancesOf(seriesPk);
                numberOfSeriesRelatedInstances =
                        (int) countSeriesRelatedInstancesOf(seriesPk, rejectedInstances);
                this.numberOfSeriesRelatedInstances =
                        new CachedNumber(seriesPk, numberOfSeriesRelatedInstances);
                if (!rejectedInstances)
                    updateSeries()
                        .setInteger(0, numberOfSeriesRelatedInstances)
                        .setLong(1, seriesPk)
                        .executeUpdate();
            }
        }
        Utils.setSeriesQueryAttributes(attrs, numberOfSeriesRelatedInstances);
    }

    private Query updateStudy() {
        if (updateStudy == null)
            updateStudy= session.createQuery(UPDATE_STUDY);
        return updateStudy;
    }

    private Query updateSeries() {
        if (updateSeries== null)
            updateSeries= session.createQuery(UPDATE_SERIES);
        return updateSeries;
    }

    private boolean hasStudyRejectedInstances(Long studyPk) {
        BooleanBuilder builder = new BooleanBuilder(QSeries.series.study.pk.eq(studyPk));
        builder.and(QInstance.instance.replaced.isFalse());
        builder.and(QInstance.instance.rejectionCode.isNotNull());
        return new HibernateQuery(session)
            .from(QInstance.instance)
            .innerJoin(QInstance.instance.series, QSeries.series)
            .where(builder)
            .count() > 0;
    }

    private boolean hasSeriesRejectedInstancesOf(Long seriesPk) {
        BooleanBuilder builder = new BooleanBuilder(QInstance.instance.series.pk.eq(seriesPk));
        builder.and(QInstance.instance.replaced.isFalse());
        builder.and(QInstance.instance.rejectionCode.isNotNull());
        return new HibernateQuery(session)
            .from(QInstance.instance)
            .where(builder)
            .count() > 0;
    }

    private long countStudyRelatedInstancesOf(Long studyPk, boolean rejectedInstances) {
        BooleanBuilder builder = new BooleanBuilder(QSeries.series.study.pk.eq(studyPk));
        builder.and(QInstance.instance.replaced.isFalse());
        if (rejectedInstances) {
            Builder.andNotInCodes(builder, QInstance.instance.conceptNameCode,
                    queryParam.getHideConceptNameCodes());
            Builder.andNotInCodes(builder, QInstance.instance.rejectionCode,
                    queryParam.getHideRejectionCodes());
        }
        return new HibernateQuery(session)
            .from(QInstance.instance)
            .innerJoin(QInstance.instance.series, QSeries.series)
            .where(builder)
            .count();
    }

    private long countStudyRelatedSeriesOf(Long studyPk, boolean rejectedInstances) {
        return new HibernateQuery(session)
            .from(QSeries.series)
            .where(QSeries.series.study.pk.eq(studyPk),
                    instancesExists(QSeries.series, rejectedInstances))
            .count();
    }

    private Predicate instancesExists(QSeries series, boolean rejectedInstances) {
        BooleanBuilder builder = new BooleanBuilder(QInstance.instance.series.eq(series));
        builder.and(QInstance.instance.replaced.isFalse());
        if (rejectedInstances) {
            Builder.andNotInCodes(builder, QInstance.instance.conceptNameCode,
                    queryParam.getHideConceptNameCodes());
            Builder.andNotInCodes(builder, QInstance.instance.rejectionCode,
                    queryParam.getHideRejectionCodes());
        }
        return new HibernateSubQuery()
            .from(QInstance.instance)
            .where(builder)
            .exists();
    }

    private long countSeriesRelatedInstancesOf(Long seriesPk, boolean rejectedInstances) {
        BooleanBuilder builder = new BooleanBuilder(QInstance.instance.series.pk.eq(seriesPk));
        builder.and(QInstance.instance.replaced.isFalse());
        if (rejectedInstances) {
            Builder.andNotInCodes(builder, QInstance.instance.conceptNameCode,
                    queryParam.getHideConceptNameCodes());
            Builder.andNotInCodes(builder, QInstance.instance.rejectionCode,
                    queryParam.getHideRejectionCodes());
        }
        return new HibernateQuery(session)
            .from(QInstance.instance)
            .where(builder)
            .count();
    }

    private static class CachedNumber {
        final long pk;
        final int n;
        public CachedNumber(long pk, int n) {
            this.pk = pk;
            this.n = n;
        }
     }

}
