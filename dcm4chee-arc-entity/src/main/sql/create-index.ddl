    create index code_idx on code (code_value, code_designator, code_version);

    create index content_item_instance_fk_idx on content_item (instance_fk);
    create index content_item_rel_type_idx on content_item (rel_type);
    create index content_item_name_fk_idx on content_item (name_fk);
    create index content_item_code_fk_idx on content_item (code_fk);
    create index content_item_text_value_idx on content_item (text_value);

    create index file_ref_instance_fk_idx on file_ref (instance_fk);
    create index file_ref_filesystem_fk_idx on file_ref (filesystem_fk);

    create index fs_next_fk_idx on filesystem (next_fk);
    create index fs_group_id_idx on filesystem (fs_group_id);
    create index fs_status_idx on filesystem (fs_status);

    create index inst_series_fk_idx on instance (series_fk);
    create index inst_sop_iuid_idx on instance (sop_iuid);
    create index inst_sop_cuid_idx on instance (sop_cuid);
    create index inst_no_idx on instance (inst_no);
    create index inst_content_date_idx on instance (content_date);
    create index inst_content_time_idx on instance (content_time);
    create index inst_sr_verified_idx on instance (sr_verified);
    create index inst_sr_complete_idx on instance (sr_complete);
    create index inst_availability on instance (availability);
    create index inst_srcode_fk_idx on instance (srcode_fk);
    create index inst_custom1_idx on instance (inst_custom1);
    create index inst_custom2_idx on instance (inst_custom2);
    create index inst_custom3_idx on instance (inst_custom3);
    create index inst_replaced_idx on instance (replaced);

    create index issuer_entity_id_idx on issuer (entity_id);
    create index issuer_entity_uid_idx on issuer (entity_uid, entity_uid_type);

    create index pat_id_idx on patient (pat_id);
    create index pat_id_issuer_fk_idx on patient (pat_id_issuer_fk);
    create index pat_name_idx on patient (pat_name);
    create index pat_p_name_idx on patient (pat_p_name);
    create index pat_i_name_idx on patient (pat_i_name);
    create index pat_fn_sx_idx on patient (pat_fn_sx);
    create index pat_gn_sx_idx on patient (pat_gn_sx);
    create index pat_birthdate_idx on patient (pat_birthdate);
    create index pat_sex_idx on patient (pat_sex);
    create index pat_custom1_idx on patient (pat_custom1);
    create index pat_custom2_idx on patient (pat_custom2);
    create index pat_custom3_idx on patient (pat_custom3);
    create index pat_merge_fk_idx on patient (merge_fk);

    create index pps_sop_iuid_idx on pps (sop_iuid);
    create index pps_patient_fk_idx on pps (patient_fk);

    create index rel_pps_sps_pps_fk_idx on rel_pps_sps (pps_fk);
    create index rel_pps_sps_sps_fk_idx on rel_pps_sps (sps_fk);

    create index rel_series_sps_series_fk_idx on rel_series_sps (series_fk);
    create index rel_series_sps_sps_fk_idx on rel_series_sps (sps_fk);

    create index rel_study_pcode_study_fk_idx on rel_study_pcode (study_fk);
    create index rel_study_pcode_pcode_fk_idx on rel_study_pcode (pcode_fk);

    create index req_proc_study_iuid_idx on req_proc (study_iuid);
    create index req_proc_request_fk_idx on req_proc (request_fk);

    create index req_physician_idx on request (req_physician);
    create index req_phys_i_name_idx on request (req_phys_i_name);
    create index req_phys_p_name_idx on request (req_phys_p_name);
    create index req_phys_gn_sx_idx on request (req_phys_gn_sx);
    create index req_phys_fn_sx_idx on request (req_phys_fn_sx);
    create index req_accession_no_idx on request (accession_no);
    create index req_accno_issuer_fk_idx on request (accno_issuer_fk);
    create index req_service_idx on request (req_service);
    create index req_visit_fk_idx on request (visit_fk);

    create index series_study_fk_idx on series (study_fk);
    create index series_iuid_idx on series (series_iuid);
    create index series_no_idx on series (series_no);
    create index series_modality_idx on series (modality);
    create index series_station_name_idx on series (station_name);
    create index series_pps_start_date_idx on series (pps_start_date);
    create index series_pps_start_time_idx on series (pps_start_time);
    create index series_perf_phys_name_idx on series (perf_phys_name);
    create index series_perf_phys_p_name_idx on series (perf_phys_p_name);
    create index series_perf_phys_i_name_idx on series (perf_phys_i_name);
    create index series_perf_phys_fn_sx_idx on series (perf_phys_fn_sx);
    create index series_perf_phys_gn_sx_idx on series (perf_phys_gn_sx);
    create index series_body_part_idx on series (body_part);
    create index series_laterality_idx on series (laterality);
    create index series_desc_idx on series (series_desc);
    create index series_institution_idx on series (institution);
    create index series_inst_code_fk_idx on series (inst_code_fk);
    create index series_department_idx on series (department);
    create index series_custom1_idx on series (series_custom1);
    create index series_custom2_idx on series (series_custom2);
    create index series_custom3_idx on series (series_custom3);

    create index sps_id_idx on sps (sps_id);
    create index sps_req_proc_fk_idx on sps (req_proc_fk);
    create index sps_status_idx on sps (sps_status);
    create index sps_start_date_idx on sps (sps_start_date);
    create index sps_start_time_idx on sps (sps_start_time);
    create index sps_modality_idx on sps (modality);
    create index sps_perf_phys_name_idx on sps (perf_phys_name);
    create index sps_perf_phys_p_name_idx on sps (perf_phys_p_name);
    create index sps_perf_phys_i_name_idx on sps (perf_phys_i_name);
    create index sps_perf_phys_fn_sx_idx on sps (perf_phys_fn_sx);
    create index sps_perf_phys_gn_sx_idx on sps (perf_phys_gn_sx);

    create index sps_station_aet_sps_fk_idx on sps_station_aet (sps_fk);
    create index sps_station_aet_station_aet_idx on sps_station_aet (station_aet);

    create index study_patient_fk_idx on study (patient_fk);
    create index study_iuid_idx on study (study_iuid);
    create index study_id_idx on study (study_id);
    create index study_date_idx on study (study_date);
    create index study_time_idx on study (study_time);
    create index study_accession_no_idx on study (accession_no);
    create index study_accno_issuer_fk_idx on study (accno_issuer_fk);
    create index study_desc_idx on study (study_desc);
    create index study_ref_physician_idx on study (ref_physician);
    create index study_ref_phys_p_name_idx on study (ref_phys_p_name);
    create index study_ref_phys_i_name_idx on study (ref_phys_i_name);
    create index study_ref_phys_fn_sx_idx on study (ref_phys_fn_sx);
    create index study_ref_phys_gn_sx_idx on study (ref_phys_gn_sx);
    create index study_custom1_idx on study (study_custom1);
    create index study_custom2_idx on study (study_custom2);
    create index study_custom3_idx on study (study_custom3);
    create index study_access_control_id_idx on study (access_control_id);

    create index vo_instance_fk_idx on verify_observer (instance_fk);
    create index vo_verify_datetime_idx on verify_observer (verify_datetime);
    create index vo_observer_name_idx on verify_observer (observer_name);
    create index vo_observer_p_name_idx on verify_observer (observer_p_name);
    create index vo_observer_i_name_idx on verify_observer (observer_i_name);
    create index vo_observer_fn_sx_idx on verify_observer (observer_fn_sx);
    create index vo_observer_gn_sx_idx on verify_observer (observer_gn_sx);

    create index visit_patient_fk_idx on visit (patient_fk);
    create index visit_admission_id_idx on visit (admission_id);
    create index visit_admission_issuer_fk_idx on visit (admission_issuer_fk);
