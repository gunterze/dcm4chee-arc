package org.dcm4chee.archive.retrieve.scp.impl;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Typed;

import org.dcm4che.data.UID;
import org.dcm4che.net.service.DicomService;

@ApplicationScoped
@Typed(DicomService.class)
public class PatientStudyOnlyCMoveSCP extends CMoveSCP {
    public PatientStudyOnlyCMoveSCP() {
        super(UID.PatientStudyOnlyQueryRetrieveInformationModelMOVERetired,
                "PATIENT", "STUDY");
    }
}