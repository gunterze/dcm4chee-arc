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
 * Portions created by the Initial Developer are Copyright (C) 2012
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

package org.dcm4chee.archive.util;

import java.util.IdentityHashMap;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class JMSService {

    public interface MessageCreator {
        Message createMessage(Session session) throws JMSException;
    }

    private final Connection conn;
    private final IdentityHashMap<MessageListener, Session> sessions =
            new IdentityHashMap<MessageListener, Session>();

    public JMSService(ConnectionFactory connFactory) throws JMSException {
        conn = connFactory.createConnection();
    }

    public void start() throws JMSException {
        conn.start();
    }

    public void stop() {
        try {
            conn.stop();
        } catch (JMSException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void close() throws JMSException {
        sessions.clear();
        conn.close();
    }

    public void sendMessage(Destination dest, MessageCreator creator, int delay)
            throws JMSException {
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        try {
            MessageProducer producer = session.createProducer(dest);
            Message msg = creator.createMessage(session);
            if (delay > 0)
                msg.setLongProperty("_HQ_SCHED_DELIVERY",
                        System.currentTimeMillis() + delay * 1000);
            producer.send(msg);
        } finally {
            session.close();
        }
    }

    public void addMessageListener(Destination dest, MessageListener listener)
            throws JMSException {
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        try {
            MessageConsumer consumer = session.createConsumer(dest);
            consumer.setMessageListener(listener);
            sessions.put(listener, session);
            session = null;
        } finally {
            if (session != null)
                session.close();
        }
    }

    public void removeMessageListener(MessageListener listener)
            throws JMSException {
        Session session = sessions.remove(listener);
        if (session != null)
            session.close();
    }
}
