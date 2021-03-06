package org.mskcc.cbio.portal.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mskcc.cbio.portal.model.DBCancerType;
import org.mskcc.cbio.portal.model.DBClinicalField;
import org.mskcc.cbio.portal.model.DBClinicalPatientData;
import org.mskcc.cbio.portal.model.DBClinicalSampleData;
import org.mskcc.cbio.portal.model.DBGene;
import org.mskcc.cbio.portal.model.DBGeneAlias;
import org.mskcc.cbio.portal.model.DBGeneticAltRow;
import org.mskcc.cbio.portal.model.DBGeneticProfile;
import org.mskcc.cbio.portal.model.DBAltCount;
import org.mskcc.cbio.portal.model.DBAltCountInput;
import org.mskcc.cbio.portal.model.DBMutationData;
import org.mskcc.cbio.portal.model.DBPatient;
import org.mskcc.cbio.portal.model.DBSampleList;
import org.mskcc.cbio.portal.model.DBProfileData;
import org.mskcc.cbio.portal.model.DBProfileDataCaseList;
import org.mskcc.cbio.portal.model.DBSample;
import org.mskcc.cbio.portal.model.DBSimpleProfileData;
import org.mskcc.cbio.portal.model.DBStudy;
import org.mskcc.cbio.portal.persistence.CancerTypeMapper;
import org.mskcc.cbio.portal.persistence.ClinicalDataMapper;
import org.mskcc.cbio.portal.persistence.ClinicalFieldMapper;
import org.mskcc.cbio.portal.persistence.GeneAliasMapper;
import org.mskcc.cbio.portal.persistence.GeneMapper;
import org.mskcc.cbio.portal.persistence.GeneticProfileMapper;
import org.mskcc.cbio.portal.persistence.MutationMapper;
import org.mskcc.cbio.portal.persistence.SampleListMapper;
import org.mskcc.cbio.portal.persistence.PatientMapper;
import org.mskcc.cbio.portal.persistence.ProfileDataMapper;
import org.mskcc.cbio.portal.persistence.SampleMapper;
import org.mskcc.cbio.portal.persistence.StudyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author abeshoua
 */
@Service
public class ApiService {

	@Autowired
	private CancerTypeMapper cancerTypeMapper;
        @Autowired
        private MutationMapper mutationMapper;
	@Autowired
	private ClinicalDataMapper clinicalDataMapper;
	@Autowired
	private ClinicalFieldMapper clinicalFieldMapper;
	@Autowired
	private GeneMapper geneMapper;
	@Autowired
	private GeneAliasMapper geneAliasMapper;
	@Autowired
	private GeneticProfileMapper geneticProfileMapper;
	@Autowired
	private SampleListMapper sampleListMapper;
	@Autowired
	private PatientMapper patientMapper;
	@Autowired
	private ProfileDataMapper profileDataMapper;
	@Autowired
	private SampleMapper sampleMapper;
	@Autowired
	private StudyMapper studyMapper;

	@Transactional
	public List<DBCancerType> getCancerTypes() {
		return cancerTypeMapper.getAllCancerTypes();
	}

	@Transactional
	public List<DBCancerType> getCancerTypes(List<String> cancer_type_ids) {
		return cancerTypeMapper.getCancerTypes(cancer_type_ids);
	}

        @Transactional
	public List<Map<String, String>> getMutationsCounts(Map<String,String[]> customizedAttrs, String type, Boolean per_study, List<String> studyIds, List<String> genes, List<Integer> starts, List<Integer> ends, List<String> echo) {

            List<Map<String, String>> results = new ArrayList<Map<String, String>>();
            Map<String,String> result;
            for(int i = 0;i < genes.size();i++)
            {
                
                if(echo == null)
                {
                    echo = new ArrayList<String>();
                    echo.add("gene");
                    for(String key: customizedAttrs.keySet())
                    {
                        echo.add(key);
                    }
                }
               List<DBAltCount> eles = mutationMapper.getMutationsCounts(type, genes.get(i), (starts == null ? null : starts.get(i)), (ends == null ? null : ends.get(i)), studyIds, per_study); 
               for(DBAltCount ele: eles )
               {
                   result = new HashMap<String,String>();
                   for(String key: customizedAttrs.keySet()){
                       if(echo.contains(key))result.put(key, customizedAttrs.get(key)[i]);
                   }
                   if(echo.contains("gene"))result.put("gene", genes.get(i));
                   if(starts != null)
                   {
                       if(echo.contains("start"))result.put("start", starts.get(i).toString());
                   }
                   if(ends != null)
                   {
                       if(echo.contains("end"))result.put("end", ends.get(i).toString());
                   }

                    if(type.equals("count"))
                    {
                        result.put("count", Integer.toString(ele.count));
                    }
                    else if(type.equals("frequency"))
                    {
                        result.put("frequency", Double.toString(ele.frequency));
                    }

                   if(per_study)result.put("studyID", ele.studyID);
                   results.add(result);
               }  
               
            }
            
		return results;
	}
        @Transactional
	public List<Map<String, String>> getMutationsCountsJSON(DBAltCountInput body) {
            
            String type = body.type;
            Boolean per_study = body.per_study;
            List<String> echo = body.echo;
            List<String> studyIds = body.studyId;
            List<Map<String, String>> data = body.data;
            List<Map<String, String>> results = new ArrayList<Map<String, String>>();
            Map<String,String> result;
            for(int i = 0;i < data.size();i++)
            {
                
                Map<String, String> item = data.get(i);
                if(echo == null)
                {
                    echo = new ArrayList<String>();
                    for(String key: item.keySet())
                    {
                        echo.add(key);
                    }
                }
                List<DBAltCount> eles = mutationMapper.getMutationsCounts(type, item.get("gene"), (item.get("start") == null ? null : Integer.parseInt(item.get("start"))), (item.get("end") == null ? null : Integer.parseInt(item.get("end"))), studyIds, per_study) ;
                for(DBAltCount ele: eles)
                {
                    result = new HashMap<String,String>();
                    for(String key: item.keySet())
                    {
                        if(echo.contains(key))result.put(key, item.get(key));
                    }
                   if(type.equals("count"))
                   {
                        result.put("count", Integer.toString(ele.count));
                   }
                   else if(type.equals("frequency"))
                   {
                       result.put("frequency", Double.toString(ele.frequency));
                   }
                   if(per_study)result.put("studyID", ele.studyID);
                   results.add(result);
                }
                
               
            }   
		return results;

	}
        
	@Transactional
	public List<DBClinicalSampleData> getSampleClinicalData(String study_id, List<String> attribute_ids) {
		return clinicalDataMapper.getSampleClinicalDataByStudyAndAttribute(study_id, attribute_ids);
	}
	@Transactional
	public List<DBClinicalSampleData> getSampleClinicalData(String study_id, List<String> attribute_ids, List<String> sample_ids) {
		return clinicalDataMapper.getSampleClinicalDataBySampleAndAttribute(study_id, attribute_ids, sample_ids);
	}

	@Transactional
	public List<DBClinicalPatientData> getPatientClinicalData(String study_id, List<String> attribute_ids) {
		return clinicalDataMapper.getPatientClinicalDataByStudyAndAttribute(study_id, attribute_ids);
	}
	@Transactional
	public List<DBClinicalPatientData> getPatientClinicalData(String study_id, List<String> attribute_ids, List<String> patient_ids) {
		return clinicalDataMapper.getPatientClinicalDataByPatientAndAttribute(study_id, attribute_ids, patient_ids);
	}

	@Transactional
	public List<DBClinicalField> getClinicalAttributes() {
		return clinicalFieldMapper.getAllClinicalFields();
	}
	@Transactional
	public List<DBClinicalField> getClinicalAttributes(List<String> attr_ids) {
		return clinicalFieldMapper.getClinicalFieldsById(attr_ids);
	}
	@Transactional
	public List<DBClinicalField> getSampleClinicalAttributes() {
		return clinicalFieldMapper.getAllSampleClinicalFields();
	}

	@Transactional
	public List<DBClinicalField> getSampleClinicalAttributes(String study_id) {
		List<Integer> internal_sample_ids = sampleMapper.getSampleInternalIdsByStudy(study_id);
		return getSampleClinicalAttributesByInternalIds(internal_sample_ids);
	}

	@Transactional
	public List<DBClinicalField> getSampleClinicalAttributes(String study_id, List<String> sample_ids) {
		List<Integer> internal_sample_ids = sampleMapper.getSampleInternalIdsBySample(study_id, sample_ids);
		return getSampleClinicalAttributesByInternalIds(internal_sample_ids);
	}

	@Transactional
	public List<DBClinicalField> getSampleClinicalAttributesByInternalIds(List<Integer> sample_ids) {
	    return clinicalFieldMapper.getSampleClinicalFieldsBySampleInternalIds(sample_ids);
	}

	@Transactional
	public List<DBClinicalField> getPatientClinicalAttributes() {
		return clinicalFieldMapper.getAllPatientClinicalFields();
	}

	@Transactional
	public List<DBClinicalField> getPatientClinicalAttributes(String study_id) {
		List<Integer> internal_patient_ids = patientMapper.getPatientInternalIdsByStudy(study_id);
		return clinicalFieldMapper.getPatientClinicalFieldsByPatientInternalIds(internal_patient_ids);
	}

	@Transactional
	public List<DBClinicalField> getPatientClinicalAttributes(String study_id, List<String> patient_ids) {
		List<Integer> internal_patient_ids = patientMapper.getPatientInternalIdsByPatient(study_id, patient_ids);
		return clinicalFieldMapper.getPatientClinicalFieldsByPatientInternalIds(internal_patient_ids);
	}

	@Transactional
	public List<DBClinicalField> getPatientClinicalAttributesByInternalIds(List<Integer> patient_ids) {
	    return clinicalFieldMapper.getPatientClinicalFieldsByPatientInternalIds(patient_ids);
	}
    
	@Transactional
	public List<DBGene> getGenes() {
		return geneMapper.getAllGenes();
	}

	@Transactional
	public List<DBGene> getGenes(List<String> hugo_gene_symbols) {
		return geneMapper.getGenesByHugo(hugo_gene_symbols);
	}

	@Transactional
	public List<DBGeneAlias> getGenesAliases() {
		return geneAliasMapper.getAllGenesAliases();
	}

	@Transactional
	public List<DBGeneAlias> getGenesAliases(List<Long> entrez_gene_ids) {
		return geneAliasMapper.getGenesAliasesByEntrez(entrez_gene_ids);
	}
	
	@Transactional
	public List<DBGeneticProfile> getGeneticProfiles() {
		return geneticProfileMapper.getAllGeneticProfiles();
	}

	@Transactional
	public List<DBGeneticProfile> getGeneticProfiles(String study_id) {
		return geneticProfileMapper.getGeneticProfilesByStudy(study_id);
	}

	@Transactional
	public List<DBGeneticProfile> getGeneticProfiles(List<String> genetic_profile_ids) {
		return geneticProfileMapper.getGeneticProfiles(genetic_profile_ids);
	}

        @Transactional
        private List<DBSampleList> addSampleIdsToSampleLists(List<DBSampleList> incomplete_lists) {
            for (DBSampleList l : incomplete_lists) {
                List<DBSample> sample_list = sampleListMapper.getSampleIds(l.id);
                l.sample_ids = new ArrayList<>();
                for (DBSample samp : sample_list) {
                    l.sample_ids.add(samp.id);
                }
            }
            return incomplete_lists;
        }
	@Transactional
	public List<DBSampleList> getSampleLists() {
		return addSampleIdsToSampleLists(sampleListMapper.getAllIncompleteSampleLists());
	}

	@Transactional
	public List<DBSampleList> getSampleLists(String study_id) {
		return addSampleIdsToSampleLists(sampleListMapper.getIncompleteSampleListsByStudy(study_id));
	}

	@Transactional
	public List<DBSampleList> getSampleLists(List<String> sample_list_ids) {
		return addSampleIdsToSampleLists(sampleListMapper.getIncompleteSampleLists(sample_list_ids));
	}

	
	@Transactional
	public List<DBPatient> getPatients(String study_id) {
		return patientMapper.getPatientsByStudy(study_id);
	}

	@Transactional
	public List<DBPatient> getPatientsByPatient(String study_id, List<String> patient_ids) {
		return patientMapper.getPatientsByPatient(study_id, patient_ids);
	}
	
	@Transactional
	public List<DBPatient> getPatientsBySample(String study_id, List<String> sample_ids) {
		return patientMapper.getPatientsBySample(study_id, sample_ids);
	}

    @Transactional
    public List<Integer> getPatientInternalIdsByStudy(String study_id) {
        return patientMapper.getPatientInternalIdsByStudy(study_id);
    }

    @Transactional
    public List<Integer> getSampleInternalIds(String study_id) {
        return sampleMapper.getSampleInternalIdsByStudy(study_id);
    }
    
	@Transactional
	public List<DBProfileData> getGeneticProfileData(List<String> genetic_profile_ids, List<String> genes) {
		return getGeneticProfileData(genetic_profile_ids, genes, null, null);
	}
        
        @Transactional
	public List<DBProfileData> getGeneticProfileDataBySampleList(List<String> genetic_profile_ids, List<String> genes, String sample_list_id) {
		return getGeneticProfileData(genetic_profile_ids, genes, null, sample_list_id);
	}
        
        @Transactional
        public List<DBProfileData> getGeneticProfileDataBySample(List<String> genetic_profile_ids, List<String> genes, List<String> sample_ids) {
            return getGeneticProfileData(genetic_profile_ids, genes, sample_ids, null);
        }

	@Transactional
	public List<DBProfileData> getGeneticProfileData(List<String> genetic_profile_ids, List<String> genes, List<String> sample_ids, String sample_list_id) {
		List<DBGeneticProfile> profiles = getGeneticProfiles(genetic_profile_ids);
		List<String> mutation_profiles = new ArrayList<>();
		List<String> non_mutation_profiles = new ArrayList<>();
		for (DBGeneticProfile p : profiles) {
			if (p.genetic_alteration_type.equals("MUTATION_EXTENDED")) {
				mutation_profiles.add(p.id);
			} else {
				non_mutation_profiles.add(p.id);
			}
		}
		List<DBProfileData> ret = new ArrayList<>();
		if (!mutation_profiles.isEmpty()) {
			List<DBMutationData> to_add;
			if (sample_ids == null && sample_list_id == null) {
				to_add = profileDataMapper.getMutationData(mutation_profiles, genes);
			} else if (sample_list_id == null) {
				to_add = profileDataMapper.getMutationDataBySample(mutation_profiles, genes, sample_ids);
			} else {
                                to_add = profileDataMapper.getMutationDataBySampleList(mutation_profiles, genes, sample_list_id);
                        }
			ret.addAll(to_add);
		}
		if (!non_mutation_profiles.isEmpty()) {
			List<DBGeneticAltRow> genetic_alt_rows = profileDataMapper.getGeneticAlterationRow(non_mutation_profiles, genes);
			List<DBProfileDataCaseList> ordered_sample_lists = profileDataMapper.getProfileCaseLists(non_mutation_profiles);
                        
			Set<String> desired_samples = new HashSet<>();
                        String queried_sample_list_id = null;
                        if (sample_list_id != null) {
                            List<String> sample_list_ids = new LinkedList<>();
                            sample_list_ids.add(sample_list_id);
                            List<DBSampleList> sample_lists = getSampleLists(sample_list_ids);
                            for (DBSampleList list: sample_lists) {
                                desired_samples.addAll(list.sample_ids);
                            }
                            queried_sample_list_id = sample_list_id;
                        }
			if (sample_ids != null) {
				for (String sample: sample_ids) {
					desired_samples.add(sample);
				}
			}
			Map<String, String> sample_order_map = new HashMap<>();
			Map<String, String> stable_sample_id_map = new HashMap<>();
			for (DBProfileDataCaseList sample_list : ordered_sample_lists) {
				String[] list = sample_list.ordered_sample_list.split(",");
				String key_prefix = sample_list.genetic_profile_id + "~";
				for (int i = 0; i < list.length; i++) {
					if (!list[i].equals("")) {
						sample_order_map.put(key_prefix + i, list[i]);
					}
				}
			}
			List<String> internal_sample_ids = new ArrayList<>();
			internal_sample_ids.addAll(sample_order_map.values());
			List<DBSample> samples = sampleMapper.getSamplesByInternalId(internal_sample_ids);
			for (DBSample sample: samples) {
				stable_sample_id_map.put(sample.internal_id, sample.id);
			}
			for (DBGeneticAltRow row : genetic_alt_rows) {
				String[] values = row.values.split(",");
				String key_prefix = row.genetic_profile_id + "~";
				for (int i = 0; i < values.length; i++) {
					if (!values[i].equals("")) {
						String sample_id = stable_sample_id_map.get(sample_order_map.get(key_prefix + i));
						if (desired_samples.contains(sample_id) || desired_samples.isEmpty()) {
							DBSimpleProfileData datum = new DBSimpleProfileData();
							datum.sample_id = sample_id;
							datum.genetic_profile_id = row.genetic_profile_id;
							datum.study_id = row.study_id;
							datum.hugo_gene_symbol = row.hugo_gene_symbol;
							datum.entrez_gene_id = row.entrez_gene_id;
							datum.profile_data = values[i];
                                                        if (queried_sample_list_id != null) {
                                                            datum.sample_list_id = queried_sample_list_id;
                                                        }
							ret.add(datum);
						}
					}
				}
			}
		}
		return ret;
	}
	
	@Transactional
	public List<DBSample> getSamples(String study_id) {
		return sampleMapper.getSamplesByStudy(study_id);
	}

	@Transactional
	public List<DBSample> getSamplesBySample(String study_id, List<String> sample_ids) {
		return sampleMapper.getSamplesBySample(study_id, sample_ids);
	}
        
        @Transactional
        public List<DBSample> getSamplesByPatient(String study_id, List<String> patient_ids) {
                return sampleMapper.getSamplesByPatient(study_id, patient_ids);
        }
	
	@Transactional
	public List<DBStudy> getStudies() {
		return studyMapper.getAllStudies();
	}
	
	@Transactional
	public List<DBStudy> getStudies(List<String> study_ids) {
		return studyMapper.getStudies(study_ids);
	}


}
