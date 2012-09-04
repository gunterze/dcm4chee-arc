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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.ejb.EJBException;
import javax.ejb.Remove;
import javax.ejb.Stateful;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import javax.sql.DataSource;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.net.service.QueryRetrieveLevel;
import org.dcm4chee.archive.common.IDWithIssuer;
import org.dcm4chee.archive.common.QueryParam;
import org.dcm4chee.archive.entity.QPatient;
import org.dcm4chee.archive.entity.Utils;
import org.dcm4chee.archive.util.query.Builder;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.ejb.HibernateEntityManagerFactory;

import com.mysema.query.BooleanBuilder;
import com.mysema.query.jpa.hibernate.HibernateQuery;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
@Stateful
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class QueryService {

    // injection specified in META-INF/ejb-jar.xml
    private DataSource dataSource;

    @PersistenceUnit
    private EntityManagerFactory emf;

    private StatelessSession session;

    private Connection connection;

    private AbstractQuery query;

    @PostConstruct
    protected void init() {
        SessionFactory sessionFactory = 
                ((HibernateEntityManagerFactory) emf).getSessionFactory();
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            throw new EJBException(e);
        }
        session = sessionFactory.openStatelessSession(connection);
    }

    final StatelessSession session() {
        return session;
    }

    public void find(QueryRetrieveLevel qrlevel, IDWithIssuer[] pids,
            Attributes keys, QueryParam queryParam) throws Exception {
        switch (qrlevel) {
        case PATIENT:
            findPatients(pids, keys, queryParam);
            break;
        case STUDY:
            findStudies(pids, keys, queryParam);
            break;
        case SERIES:
            findSeries(pids, keys, queryParam);
            break;
        case IMAGE:
            findInstances(pids, keys, queryParam);
            break;
        default:
            assert true;
        }
    }

    public void findPatients(IDWithIssuer[] pids, Attributes keys,
            QueryParam queryParam) {
        query = new PatientQuery(session, pids, keys, queryParam);
    }

    public void findStudies(IDWithIssuer[] pids, Attributes keys,
            QueryParam queryParam) {
        query = new StudyQuery(session, pids, keys, queryParam);
    }

    public void findSeries(IDWithIssuer[] pids, Attributes keys,
            QueryParam queryParam) {
        query = new SeriesQuery(session, pids, keys, queryParam);
    }

    public void findInstances(IDWithIssuer[] pids, Attributes keys,
            QueryParam queryParam) {
        query = new InstanceQuery(session, pids, keys, queryParam);
    }

    public boolean optionalKeyNotSupported() {
        checkResults();
        return query.optionalKeyNotSupported();
    }

    public boolean hasMoreMatches() {
        checkResults();
        return query.hasMoreMatches();
    }

    public Attributes nextMatch() {
        checkResults();
        return query.nextMatch();
    }

    private void checkResults() {
        if (query == null)
            throw new IllegalStateException("results not initalized");
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

    @Remove
    public void close() {
        StatelessSession s = session;
        Connection c = connection;
        connection = null;
        session = null;
        query = null;
        s.close();
        try {
            c.close();
        } catch (Exception e) {
            //TODO
        }
    }
}
