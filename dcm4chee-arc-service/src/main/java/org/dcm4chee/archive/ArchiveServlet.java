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

package org.dcm4chee.archive;

import java.lang.management.ManagementFactory;

import javax.annotation.Resource;
import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.dcm4che.conf.api.hl7.HL7Configuration;

@SuppressWarnings("serial")
public class ArchiveServlet extends HttpServlet {

    private ObjectInstance mbean;

    private Archive archive;

    private HL7Configuration dicomConfig;
    
    @Resource(mappedName="java:/ConnectionFactory")
    private ConnectionFactory connFactory;

    @Resource(mappedName="java:/queue/mppsscu")
    private Queue mppsSCUQueue;

    @Resource(mappedName="java:/queue/ianscu")
    private Queue ianSCUQueue;

    @Resource(mappedName="java:/queue/stgcmtscp")
    private Queue stgcmtSCPQueue;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            dicomConfig = (HL7Configuration) Class.forName(
                    config.getInitParameter("dicomConfigurationClass"), false,
                    Thread.currentThread().getContextClassLoader()).newInstance();
            archive = new Archive(dicomConfig,
                    config.getInitParameter("deviceName"),
                    connFactory, mppsSCUQueue, ianSCUQueue, stgcmtSCPQueue);
            archive.start();
            mbean = ManagementFactory.getPlatformMBeanServer()
                    .registerMBean(archive, 
                            new ObjectName(config.getInitParameter("jmxName")));
        } catch (Exception e) {
            destroy();
            throw new ServletException(e);
        }
    }

    @Override
    public void destroy() {
        if (mbean != null)
            try {
                ManagementFactory.getPlatformMBeanServer()
                    .unregisterMBean(mbean.getObjectName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        if (archive != null)
            archive.stop();
        if (dicomConfig != null)
            dicomConfig.close();
    }

}
