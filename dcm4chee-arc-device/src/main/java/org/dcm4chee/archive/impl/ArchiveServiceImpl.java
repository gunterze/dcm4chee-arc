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
package org.dcm4chee.archive.impl;

import java.io.File;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;

import org.dcm4che.conf.api.DicomConfiguration;
import org.dcm4che.net.Device;
import org.dcm4che.net.DeviceService;
import org.dcm4che.net.hl7.HL7DeviceExtension;
import org.dcm4che.net.hl7.service.HL7Service;
import org.dcm4che.net.hl7.service.HL7ServiceRegistry;
import org.dcm4che.net.service.BasicCEchoSCP;
import org.dcm4che.net.service.DicomService;
import org.dcm4che.net.service.DicomServiceRegistry;
import org.dcm4chee.archive.ArchiveService;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@ApplicationScoped
@Typed(ArchiveService.class)
public class ArchiveServiceImpl extends DeviceService implements ArchiveService {

    private static final String DEVICE_NAME_PROPERTY =
            "org.dcm4chee.archive.deviceName";
    private static final String DEF_DEVICE_NAME =
            "dcm4chee-arc";
    private static String[] JBOSS_PROPERITIES = {
        "jboss.home",
        "jboss.modules",
        "jboss.server.base",
        "jboss.server.config",
        "jboss.server.data",
        "jboss.server.deploy",
        "jboss.server.log",
        "jboss.server.temp",
    };

    private final String deviceName = System.getProperty(DEVICE_NAME_PROPERTY,
            DEF_DEVICE_NAME);

    @Inject
    private Instance<DicomService> dicomServices;

    @Inject
    private Instance<HL7Service> hl7Services;

    @Inject
    private DicomConfiguration conf;

    private final DicomService echoscp = new BasicCEchoSCP();

    private final DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();

    private final HL7ServiceRegistry hl7ServiceRegistry = new HL7ServiceRegistry();

    // force eager initialisation (only works if EAR contains WAR)
    void startup(@Observes @Initialized(ApplicationScoped.class) Object o) {
    }

    private static void addJBossDirURLSystemProperties() {
        for (String key : JBOSS_PROPERITIES) {
            String url = new File(System.getProperty(key + ".dir"))
                .toURI().toString();
            System.setProperty(key + ".url", url.substring(0, url.length()-1));
        }
    }

    @PostConstruct
    public void init() {
        addJBossDirURLSystemProperties();
        try {
            Device device = conf.findDevice(deviceName);
            setDevice(device);
            serviceRegistry.addDicomService(echoscp);
            for (DicomService service : dicomServices) {
                serviceRegistry.addDicomService(service);
            }
            for (HL7Service service : hl7Services) {
                hl7ServiceRegistry.addHL7Service(service);
            }
            device.setDimseRQHandler(serviceRegistry);
            HL7DeviceExtension hl7Extension = 
                    device.getDeviceExtension(HL7DeviceExtension.class);
            if (hl7Extension != null) {
                hl7Extension.setHL7MessageListener(hl7ServiceRegistry);
            }
            start();
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (isRunning())
            stop();

        serviceRegistry.removeDicomService(echoscp);
        for (DicomService service : dicomServices) {
            serviceRegistry.removeDicomService(service);
        }
        for (HL7Service service : hl7Services) {
            hl7ServiceRegistry.removeHL7Service(service);
        }
    }

    @Override
    public void start() throws Exception {
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public void reload() throws Exception {
        device.reconfigure(conf.findDevice(deviceName));
        device.rebindConnections();
    }

}
