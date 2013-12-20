package org.dcm4chee.archive.stgcmt.scp.impl;

import javax.jms.JMSException;
import javax.jms.Message;

public interface StgCmtSCP {

    void sendNEventReport(Message msg) throws JMSException;

}
