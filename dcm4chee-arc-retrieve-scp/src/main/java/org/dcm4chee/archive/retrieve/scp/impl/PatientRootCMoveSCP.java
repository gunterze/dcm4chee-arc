package org.dcm4chee.archive.retrieve.scp.impl;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Typed;

import org.dcm4che.data.UID;
import org.dcm4che.net.service.DicomService;

@ApplicationScoped
@Typed(DicomService.class)
public class PatientRootCMoveSCP extends CMoveSCP {
    public PatientRootCMoveSCP() {
        super(UID.PatientRootQueryRetrieveInformationModelMOVE,
                "PATIENT", "STUDY", "SERIES", "IMAGE");
    }
}