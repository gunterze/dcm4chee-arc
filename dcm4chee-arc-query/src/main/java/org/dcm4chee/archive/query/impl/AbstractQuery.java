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

package org.dcm4chee.archive.query.impl;

import java.sql.SQLException;
import java.util.NoSuchElementException;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.IDWithIssuer;
import org.dcm4chee.archive.conf.QueryParam;
import org.dcm4chee.archive.query.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.StatelessSession;

import com.mysema.query.jpa.hibernate.HibernateQuery;
import com.mysema.query.types.Expression;
import com.mysema.query.types.OrderSpecifier;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public abstract class AbstractQuery implements Query {

    protected final QueryServiceBean service;

    protected StatelessSession session;

    protected ScrollableResults results;

    private boolean hasMoreMatches;

    protected QueryParam queryParam;

    private  HibernateQuery query;

    private boolean optionalKeyNotSupported;

    public AbstractQuery(QueryServiceBean service) {
        this.service = service;
    }

    Query init(IDWithIssuer[] pids, Attributes keys, QueryParam queryParam)
            throws SQLException {
        this.queryParam = queryParam;
//        connection = service.getConnection();
        session = service.openStatelessSession();
        query = createQuery(pids, keys);
        return this;
    }

    protected abstract Expression<?>[] select();

    protected abstract HibernateQuery createQuery(IDWithIssuer[] pids, Attributes keys);

    protected abstract Attributes toAttributes(ScrollableResults results);

    @Override
    public void executeQuery() {
        results = query.scroll(ScrollMode.FORWARD_ONLY, select());
        hasMoreMatches = results.next();
    }

    @Override
    public long count() {
        checkQuery();
        return query.count();
    }

    @Override
    public void limit(long limit) {
        checkQuery();
        query.limit(limit);
    }

    @Override
    public void offset(long offset) {
        checkQuery();
        query.offset(offset);
    }

    @Override
    public void orderBy(OrderSpecifier<?>... orderSpecifiers) {
        checkQuery();
        query.orderBy(orderSpecifiers);
    }

    @Override
    public boolean optionalKeyNotSupported() {
        return optionalKeyNotSupported;
    }

    @Override
    public boolean hasMoreMatches() {
        return hasMoreMatches;
    }

    @Override
    public Attributes nextMatch() {
        if (!hasMoreMatches)
            throw new NoSuchElementException();
        Attributes attrs = toAttributes(results);
        hasMoreMatches = results.next();
        return attrs;
    }

    private void checkQuery() {
        if (query == null)
            throw new IllegalStateException("query not initalized");
    }

    @Override
    public void close() {
        StatelessSession s = session;
        session = null;
        query = null;
        results = null;
        if (s != null)
            s.close();
    }

}
