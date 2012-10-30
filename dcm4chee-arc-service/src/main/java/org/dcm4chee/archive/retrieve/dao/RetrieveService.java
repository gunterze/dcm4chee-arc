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

package org.dcm4chee.archive.retrieve.dao;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.net.service.InstanceLocator;
import org.dcm4chee.archive.common.IDWithIssuer;
import org.dcm4chee.archive.common.QueryParam;
import org.dcm4chee.archive.dao.SeriesService;
import org.dcm4chee.archive.entity.Instance;
import org.dcm4chee.archive.entity.QFileRef;
import org.dcm4chee.archive.entity.QFileSystem;
import org.dcm4chee.archive.entity.QInstance;
import org.dcm4chee.archive.entity.QPatient;
import org.dcm4chee.archive.entity.QSeries;
import org.dcm4chee.archive.entity.QStudy;
import org.dcm4chee.archive.entity.Utils;
import org.dcm4chee.archive.util.query.Builder;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.ejb.HibernateEntityManagerFactory;

import com.mysema.query.BooleanBuilder;
import com.mysema.query.jpa.hibernate.HibernateQuery;
import com.mysema.query.types.ExpressionUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@Stateless
public class RetrieveService {

    @PersistenceUnit
    private EntityManagerFactory emf;

    private StatelessSession session;

    @EJB
    private SeriesService seriesService;

    @PostConstruct
    public void init() {
        SessionFactory sessionFactory = ((HibernateEntityManagerFactory) emf).getSessionFactory();
        session = sessionFactory.openStatelessSession();
    }

    @PreDestroy
    public void destroy() {
        session.close();
    }

    public List<InstanceLocator> calculateMatches(IDWithIssuer[] pids,
            Attributes keys, QueryParam queryParam) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(Builder.pids(pids, false));
        builder.and(Builder.uids(QStudy.study.studyInstanceUID,
                keys.getStrings(Tag.StudyInstanceUID), false));
        builder.and(Builder.uids(QSeries.series.seriesInstanceUID,
                keys.getStrings(Tag.SeriesInstanceUID), false));
        builder.and(Builder.uids(QInstance.instance.sopInstanceUID,
                keys.getStrings(Tag.SOPInstanceUID), false));
        builder.and(QInstance.instance.replaced.isFalse());
        if (queryParam.isHideRejectedInstances())
            builder.and(QInstance.instance.rejectionFlags.eq(0));
        else
            builder.and(ExpressionUtils.or(
                    QInstance.instance.rejectionFlags.eq(0),
                    QInstance.instance.rejectionFlags.eq(
                            Instance.REJECTED_FOR_QUALITY_REASONS)));
        return locate(new HibernateQuery(session)
            .from(QInstance.instance)
            .leftJoin(QInstance.instance.fileRefs, QFileRef.fileRef)
            .leftJoin(QFileRef.fileRef.fileSystem, QFileSystem.fileSystem)
            .innerJoin(QInstance.instance.series, QSeries.series)
            .innerJoin(QSeries.series.study, QStudy.study)
            .innerJoin(QStudy.study.patient, QPatient.patient)
            .where(builder)
            .list(
                QFileRef.fileRef.transferSyntaxUID,
                QFileRef.fileRef.filePath,
                QFileSystem.fileSystem.uri,
                QSeries.series.pk,
                QInstance.instance.pk,
                QInstance.instance.sopClassUID,
                QInstance.instance.sopInstanceUID,
                QInstance.instance.retrieveAETs,
                QInstance.instance.externalRetrieveAET,
                QInstance.instance.encodedAttributes));
    }

    private List<InstanceLocator> locate(List<Object[]> tuples) {
        List<InstanceLocator> locators = new ArrayList<InstanceLocator>(tuples.size());
        long instPk = -1;
        long seriesPk = -1;
        Attributes seriesAttrs = null;
        for (Object[] tuple : tuples) {
            String tsuid = (String) tuple[0];
            String filePath = (String) tuple[1];
            String fsuri = (String) tuple[2];
            long nextSeriesPk = (Long) tuple[3];
            long nextInstPk = (Long) tuple[4];
            if (seriesPk != nextSeriesPk) {
                seriesAttrs = seriesService.getAttributes(nextSeriesPk, null);
                seriesPk = nextSeriesPk;
            }
            if (instPk != nextInstPk) {
                String cuid = (String) tuple[5];
                String iuid = (String) tuple[6];
                String retrieveAETs = (String) tuple[7];
                String externalRetrieveAET = (String) tuple[8];
                String uri;
                Attributes attrs;
                if (fsuri != null) {
                    uri = fsuri + filePath;
                    byte[] instAttrs = (byte[]) tuple[9];
                    attrs = new Attributes(seriesAttrs);
                    Utils.decodeAttributes(attrs, instAttrs);
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("aet:");
                    if (retrieveAETs != null) {
                        sb.append(retrieveAETs);
                    }
                    if (externalRetrieveAET != null) {
                        if (retrieveAETs != null)
                            sb.append('\\');
                        sb.append(externalRetrieveAET);
                    }
                    uri = sb.toString();
                    attrs = null;
                }
                locators.add(new InstanceLocator(cuid, iuid, tsuid, uri).setObject(attrs));
                instPk = nextInstPk;
            }
        }
        return locators ;
    }

    public String[] patientNamesOf(IDWithIssuer[] pids) {
        HashSet<String> c = new HashSet<String>(pids.length * 4 / 3 + 1);
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(Builder.pids(pids, false));
        builder.and(QPatient.patient.mergedWith.isNull());
        List<Object[]> tuples = new HibernateQuery(session)
            .from(QPatient.patient)
            .where(builder)
            .list(
                QPatient.patient.pk,
                QPatient.patient.encodedAttributes);
        for (Object[] tuple : tuples)
            c.add(Utils.decodeAttributes((byte[]) tuple[1])
                    .getString(Tag.PatientName));
        c.remove(null);
        return c.toArray(new String[c.size()]);
    }
}
