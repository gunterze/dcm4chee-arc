package org.dcm4chee.archive.stgcmt.scp.impl;

import javax.jms.JMSException;
import javax.jms.Message;

public interface IStgCmtSCP {

    void sendNEventReport(Message msg) throws JMSException;

}
