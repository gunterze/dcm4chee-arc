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

package org.dcm4chee.archive.retrieve.impl;

import java.util.ArrayList;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.IDWithIssuer;
import org.dcm4che.data.Tag;
import org.dcm4che.net.service.InstanceLocator;
import org.dcm4chee.archive.conf.QueryParam;
import org.dcm4chee.archive.entity.Availability;
import org.dcm4chee.archive.entity.Utils;
import org.dcm4chee.archive.entity.QFileRef;
import org.dcm4chee.archive.entity.QFileSystem;
import org.dcm4chee.archive.entity.QInstance;
import org.dcm4chee.archive.entity.QPatient;
import org.dcm4chee.archive.entity.QSeries;
import org.dcm4chee.archive.entity.QStudy;
import org.dcm4chee.archive.query.builder.QueryBuilder;
import org.dcm4chee.archive.retrieve.RetrieveService;
import org.dcm4chee.archive.series.SeriesService;
import org.hibernate.Session;

import com.mysema.query.BooleanBuilder;
import com.mysema.query.Tuple;
import com.mysema.query.jpa.hibernate.HibernateQuery;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@Stateless
public class RetrieveServiceBean implements RetrieveService {

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @EJB
    private SeriesService seriesService;

    @Override
    public List<InstanceLocator> calculateMatches(IDWithIssuer[] pids,
            Attributes keys, QueryParam queryParam) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(QueryBuilder.pids(pids, false));
        builder.and(QueryBuilder.uids(QStudy.study.studyInstanceUID,
                keys.getStrings(Tag.StudyInstanceUID), false));
        builder.and(QueryBuilder.uids(QSeries.series.seriesInstanceUID,
                keys.getStrings(Tag.SeriesInstanceUID), false));
        builder.and(QueryBuilder.uids(QInstance.instance.sopInstanceUID,
                keys.getStrings(Tag.SOPInstanceUID), false));
        builder.and(QInstance.instance.replaced.isFalse());
        builder.and(QInstance.instance.availability.loe(
                queryParam.isShowRejectedInstances()
                    ? Availability.OFFLINE
                    : Availability.REJECTED_FOR_QUALITY_REASONS));
        return locate(new HibernateQuery(em.unwrap(Session.class))
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

    private List<InstanceLocator> locate(List<Tuple> tuples) {
        List<InstanceLocator> locators = new ArrayList<InstanceLocator>(tuples.size());
        long instPk = -1;
        long seriesPk = -1;
        Attributes seriesAttrs = null;
        for (Tuple tuple : tuples) {
            String tsuid = tuple.get(0, String.class);
            String filePath = tuple.get(1, String.class);
            String fsuri = tuple.get(2, String.class);
            long nextSeriesPk = tuple.get(3, long.class);
            long nextInstPk = tuple.get(4, long.class);
            if (seriesPk != nextSeriesPk) {
                seriesAttrs = seriesService.getAttributes(nextSeriesPk);
                seriesPk = nextSeriesPk;
            }
            if (instPk != nextInstPk) {
                String cuid = tuple.get(5, String.class);
                String iuid = tuple.get(6, String.class);
                String retrieveAETs = tuple.get(7, String.class);
                String externalRetrieveAET = tuple.get(8, String.class);
                String uri;
                Attributes attrs;
                if (fsuri != null) {
                    uri = fsuri + '/' + filePath;
                    byte[] instAttrs = tuple.get(9, byte[].class);
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
}
