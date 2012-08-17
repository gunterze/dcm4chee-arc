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

package org.dcm4chee.archive.mwl.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.NoSuchElementException;

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
import org.dcm4chee.archive.entity.QPatient;
import org.dcm4chee.archive.entity.QRequestedProcedure;
import org.dcm4chee.archive.entity.QScheduledProcedureStep;
import org.dcm4chee.archive.entity.QServiceRequest;
import org.dcm4chee.archive.entity.QVisit;
import org.dcm4chee.archive.entity.Utils;
import org.dcm4chee.archive.query.util.Builder;
import org.dcm4chee.archive.query.util.IDWithIssuer;
import org.dcm4chee.archive.query.util.QueryParam;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.ejb.HibernateEntityManagerFactory;

import com.mysema.query.BooleanBuilder;
import com.mysema.query.jpa.hibernate.HibernateQuery;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
@Stateful
@TransactionAttribute(TransactionAttributeType.NEVER)
public class MWLQueryService {

    // injection specified in META-INF/ejb-jar.xml
    private DataSource dataSource;

    @PersistenceUnit
    private EntityManagerFactory emf;

    private Connection connection;

    private StatelessSession session;

    private ScrollableResults results;

    private boolean optionalKeyNotSupported;

    private boolean hasNext;

    @PostConstruct
    protected void init() {
        SessionFactory sessionFactory = ((HibernateEntityManagerFactory) emf).getSessionFactory();
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            throw new EJBException(e);
        }
        session = sessionFactory.openStatelessSession(connection);
    }

    public void findScheduledProcedureSteps(IDWithIssuer[] pids, Attributes keys,
            QueryParam queryParam) {
        BooleanBuilder builder = new BooleanBuilder();
        Builder.addPatientLevelPredicates(builder, pids, keys, queryParam);
        Builder.addServiceRequestPredicates(builder, keys, queryParam);
        Builder.addRequestedProcedurePredicates(builder, keys, queryParam);
        Attributes spsItem = keys.getNestedDataset(Tag.ScheduledProcedureStepSequence);
        Builder.addScheduledProcedureStepPredicates(builder, spsItem, queryParam);
        results = new HibernateQuery(session)
            .from(QScheduledProcedureStep.scheduledProcedureStep)
            .innerJoin(QScheduledProcedureStep.scheduledProcedureStep.requestedProcedure,
                    QRequestedProcedure.requestedProcedure)
            .innerJoin(QRequestedProcedure.requestedProcedure.serviceRequest,
                    QServiceRequest.serviceRequest)
            .innerJoin(QServiceRequest.serviceRequest.visit, QVisit.visit)
            .innerJoin(QVisit.visit.patient, QPatient.patient)
            .where(builder)
            .scroll(ScrollMode.FORWARD_ONLY,
                    QScheduledProcedureStep.scheduledProcedureStep.encodedAttributes,
                    QRequestedProcedure.requestedProcedure.encodedAttributes,
                    QServiceRequest.serviceRequest.encodedAttributes,
                    QVisit.visit.encodedAttributes,
                    QPatient.patient.encodedAttributes);
        hasNext = results.next();
    }

    public boolean optionalKeyNotSupported() {
        checkResults();
        return optionalKeyNotSupported;
    }

    public boolean hasMoreMatches() {
        checkResults();
        return hasNext;
    }

    public Attributes nextMatch() {
        checkResults();
        if (!hasNext)
            throw new NoSuchElementException();
        Attributes attrs = toAttributes(results);
        hasNext = results.next();
        return attrs;
    }

    private Attributes toAttributes(ScrollableResults results2) {
        byte[] spsAttributes = results.getBinary(0);
        byte[] reqProcAttributes = results.getBinary(1);
        byte[] requestAttributes = results.getBinary(2);
        byte[] visitAttributes = results.getBinary(3);
        byte[] patientAttributes = results.getBinary(4);
        Attributes attrs = new Attributes();
        Utils.decodeAttributes(attrs, patientAttributes);
        Utils.decodeAttributes(attrs, visitAttributes);
        Utils.decodeAttributes(attrs, requestAttributes);
        Utils.decodeAttributes(attrs, reqProcAttributes);
        Attributes spsItem = Utils.decodeAttributes(spsAttributes);
        attrs.newSequence(Tag.ScheduledProcedureStepSequence, 1).add(spsItem );
        return attrs;
    }

    @Remove
    public void close() {
        StatelessSession s = session;
        Connection c = connection;
        connection = null;
        session = null;
        results = null;
        s.close();
        try {
            c.close();
        } catch (SQLException e) {
            throw new EJBException(e);
        }
    }

    private void checkResults() {
        if (results == null)
            throw new IllegalStateException("results not initalized");
    }
}
