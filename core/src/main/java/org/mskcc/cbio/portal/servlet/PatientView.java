package org.mskcc.cbio.portal.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;
import org.mskcc.cbio.cgds.dao.*;
import org.mskcc.cbio.cgds.model.*;
import org.mskcc.cbio.cgds.util.AccessControl;
import org.mskcc.cbio.portal.remote.ConnectionManager;
import org.mskcc.cbio.portal.util.SkinUtil;
import org.mskcc.cbio.portal.util.XDebug;
import org.owasp.validator.html.PolicyException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 *
 * @author jj
 */
public class PatientView extends HttpServlet {
    private static Logger logger = Logger.getLogger(PatientView.class);
    public static final String ERROR = "error";
    public static final String PATIENT_ID = "case_id";
    public static final String OTHER_STUDIES_WITH_SAME_PATIENT_ID = "other_studies_with_same_patient_id";
    public static final String PATIENT_CASE_OBJ = "case_obj";
    public static final String CANCER_STUDY = "cancer_study";
    public static final String HAS_SEGMENT_DATA = "has_segment_data";
    public static final String MUTATION_PROFILE = "mutation_profile";
    public static final String CNA_PROFILE = "cna_profile";
    public static final String MRNA_PROFILE = "mrna_profile";
    public static final String NUM_CASES_IN_SAME_STUDY = "num_cases";
    public static final String NUM_CASES_IN_SAME_MUTATION_PROFILE = "num_cases_mut";
    public static final String NUM_CASES_IN_SAME_CNA_PROFILE = "num_cases_cna";
    public static final String NUM_CASES_IN_SAME_MRNA_PROFILE = "num_cases_mrna";
    public static final String PATIENT_INFO = "patient_info";
    public static final String DISEASE_INFO = "disease_info";
    public static final String PATIENT_STATUS = "patient_status";
    public static final String CLINICAL_DATA = "clinical_data";
    public static final String TISSUE_IMAGES = "tissue_images";
    public static final String PATH_REPORT_URL = "path_report_url";
    
    public static final String DRUG_TYPE = "drug_type";
    public static final String DRUG_TYPE_CANCER_DRUG = "cancer_drug";
    public static final String DRUG_TYPE_FDA_ONLY = "fda_approved";
    
    private ServletXssUtil servletXssUtil;
    
    private static final DaoClinicalData daoClinicalData = new DaoClinicalData();
    private static final DaoClinicalFreeForm daoClinicalFreeForm = new DaoClinicalFreeForm();

    // class which process access control to cancer studies
    private AccessControl accessControl;

    /**
     * Initializes the servlet.
     *
     * @throws ServletException Serlvet Init Error.
     */
    @Override
    public void init() throws ServletException {
        super.init();
        try {
            servletXssUtil = ServletXssUtil.getInstance();
                        ApplicationContext context = 
                                new ClassPathXmlApplicationContext("classpath:applicationContext-security.xml");
                        accessControl = (AccessControl)context.getBean("accessControl");
        } catch (PolicyException e) {
            throw new ServletException (e);
        }
    }
    
    /** 
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        XDebug xdebug = new XDebug( request );
        request.setAttribute(QueryBuilder.XDEBUG_OBJECT, xdebug);
        
        //  Get patient ID
        String patientID = servletXssUtil.getCleanInput (request, PATIENT_ID);
        String cancerStudyId = servletXssUtil.getCleanInput (request, QueryBuilder.CANCER_STUDY_ID);

        request.setAttribute(QueryBuilder.HTML_TITLE, "Patient "+patientID);
        request.setAttribute(PATIENT_ID, patientID);
        request.setAttribute(QueryBuilder.CANCER_STUDY_ID, cancerStudyId);
        
        try {
            if (validate(request)) {
                setGeneticProfiles(request);
                setClinicalInfo(request);
                setNumCases(request);
            }
            RequestDispatcher dispatcher =
                    getServletContext().getRequestDispatcher("/WEB-INF/jsp/tumormap/patient_view/patient_view.jsp");
            dispatcher.forward(request, response);
        
        } catch (DaoException e) {
            xdebug.logMsg(this, "Got Database Exception:  " + e.getMessage());
            forwardToErrorPage(request, response,
                               "An error occurred while trying to connect to the database.", xdebug);
        } 
    }
    
    private boolean validate(HttpServletRequest request) throws DaoException {
        
        request.setAttribute(HAS_SEGMENT_DATA, Boolean.FALSE); // by default; in case return false;
        
        String caseIdsStr = (String) request.getAttribute(PATIENT_ID);
        if (caseIdsStr == null || caseIdsStr.isEmpty()) {
            request.setAttribute(ERROR, "Please specify at least one case ID. ");
            return false;
        }
        String[] caseIds = caseIdsStr.split(" +");
        
        String cancerStudyId = (String) request.getAttribute(QueryBuilder.CANCER_STUDY_ID);
        if (cancerStudyId==null) {
            request.setAttribute(ERROR, "Please specify cancer study ID. ");
            return false;
        }
        
        CancerStudy cancerStudy = DaoCancerStudy.getCancerStudyByStableId(cancerStudyId);
        if (cancerStudy==null) {
            request.setAttribute(ERROR, "We have no information about cancer study "+cancerStudyId);
            return false;
        }

        LinkedHashSet<Case> cases = new LinkedHashSet<Case>(caseIds.length);
        for (String caseId : caseIds) {
            Case _case = DaoCase.getCase(caseId, cancerStudy.getInternalId());
            if (_case != null) {
                cases.add(_case);
            }
        }

        if (cases.isEmpty()) {
            request.setAttribute(ERROR, "We have no information about patients "+caseIdsStr);
            return false;
        }
        
        String cancerStudyIdentifier = cancerStudy.getCancerStudyStableId();
        
        if (accessControl.isAccessibleCancerStudy(cancerStudyIdentifier).size() != 1) {
            request.setAttribute(ERROR,
                    "You are not authorized to view the cancer study with id: '" +
                    cancerStudyIdentifier + "'. ");
            return false;
        }
        
        request.setAttribute(PATIENT_CASE_OBJ, cases);
        request.setAttribute(CANCER_STUDY, cancerStudy);
        
        request.setAttribute(HAS_SEGMENT_DATA, DaoCopyNumberSegment
                .segmentDataExistForCancerStudy(cancerStudy.getInternalId()));
        return true;
    }
    
    private void setGeneticProfiles(HttpServletRequest request) throws DaoException {
        CancerStudy cancerStudy = (CancerStudy)request.getAttribute(CANCER_STUDY);
        GeneticProfile mutProfile = cancerStudy.getMutationProfile();
        if (mutProfile!=null) {
            request.setAttribute(MUTATION_PROFILE, mutProfile);
            request.setAttribute(NUM_CASES_IN_SAME_MUTATION_PROFILE, 
                    DaoCaseProfile.countCasesInProfile(mutProfile.getGeneticProfileId()));
        }
        
        GeneticProfile cnaProfile = cancerStudy.getCopyNumberAlterationProfile(true);
        if (cnaProfile!=null) {
            request.setAttribute(CNA_PROFILE, cnaProfile);
            request.setAttribute(NUM_CASES_IN_SAME_CNA_PROFILE, 
                    DaoCaseProfile.countCasesInProfile(cnaProfile.getGeneticProfileId()));
        }
        
        GeneticProfile mrnaProfile = cancerStudy.getMRnaZscoresProfile();
        if (mrnaProfile!=null) {
            request.setAttribute(MRNA_PROFILE, mrnaProfile);
            request.setAttribute(NUM_CASES_IN_SAME_MRNA_PROFILE, 
                    DaoCaseProfile.countCasesInProfile(mrnaProfile.getGeneticProfileId()));
        }
    }
    
    private void setNumCases(HttpServletRequest request) throws DaoException {
        CancerStudy cancerStudy = (CancerStudy)request.getAttribute(CANCER_STUDY);
        request.setAttribute(NUM_CASES_IN_SAME_STUDY,DaoCase.countCases(cancerStudy.getInternalId()));
    }
    
    private void setClinicalInfo(HttpServletRequest request) throws DaoException {
        LinkedHashSet<Case> cases = (LinkedHashSet<Case>)request.getAttribute(PATIENT_CASE_OBJ);
        
        CancerStudy cancerStudy = (CancerStudy)request.getAttribute(CANCER_STUDY);
        String patient = null;
        ClinicalData cd = null;
        Map<String,ClinicalFreeForm> clinicalFreeForms = null;
        Map<String,Map<String,String>> clinicalData = new LinkedHashMap<String,Map<String,String>>();
        for (Case _case : cases) {
            patient = _case.getCaseId();
            cd = daoClinicalData.getCase(cancerStudy.getInternalId(),patient);
            clinicalFreeForms = getClinicalFreeform(cancerStudy.getInternalId(),patient);
            clinicalData.put(patient,mergeClinicalData(cd, clinicalFreeForms));
        }
        request.setAttribute(CLINICAL_DATA, clinicalData);
        
//        if (cases.length>1) {
//            return;
//        }
//        
//        // for one case
//        
//        // patient info
//        StringBuilder patientInfo = new StringBuilder();
//        
//        String gender = guessClinicalData(clinicalFreeForms, new String[]{"gender"});
//        if (gender==null) {
//            gender = inferGenderFromCancerType(cancerStudy.getTypeOfCancerId());
//        }
//        if (gender!=null) {
//            patientInfo.append(gender);
//        }
//        Double age = clinicalData==null?null:cd.getAgeAtDiagnosis();
//        if (age!=null) {
//            if (gender!=null) {
//                patientInfo.append(", ");
//            }
//            patientInfo.append(age.intValue()).append(" years old");
//        }
//        
//        request.setAttribute(PATIENT_INFO, patientInfo.toString());
//        
//        // disease info
//        StringBuilder diseaseInfo = new StringBuilder();
//        diseaseInfo.append("<a href=\"study.do?cancer_study_id=")
//                .append(cancerStudy.getCancerStudyStableId()).append("\">")
//                .append(cancerStudy.getName())
//                .append("</a>");
//        
//        String state = guessClinicalData(clinicalFreeForms,
//                new String[]{"disease state"});
//        if (state!=null) {
//            String strState = state;
//            if (state.equals("Metastatic")) {
//                strState = "<font color='red'>"+state+"</font>";
//            } else if (state.equals("Primary")) {
//                strState = "<font color='green'>"+state+"</font>";
//            }
//            
//            diseaseInfo.append(", ").append(strState);
//        
//            if (state.equals("Metastatic")) {
//                String loc = guessClinicalData(clinicalFreeForms,
//                        new String[]{"tumor location"});
//                if (loc!=null) {
//                    diseaseInfo.append(", Tumor location: ").append(loc);
//                }
//            }
//        }
//        
//        String gleason = guessClinicalData(clinicalFreeForms,
//                new String[]{"gleason score","overall_gleason_score"});
//        if (gleason!=null) {
//            diseaseInfo.append(", Gleason: ").append(gleason);
//        } 
//        
//        String primaryGleason = guessClinicalData(clinicalFreeForms,
//                new String[]{"primary_gleason_grade"});
//        String secondaryGleason = guessClinicalData(clinicalFreeForms,
//                new String[]{"secondary_gleason_grade"});
//        if (primaryGleason!=null && secondaryGleason!=null) {
//            diseaseInfo.append(" (" + primaryGleason + "+" + secondaryGleason + ")");
//        }
//        
//        String histology = guessClinicalData(clinicalFreeForms,
//                new String[]{"histology", "histological_type"});
//        if (histology!=null) {
//            diseaseInfo.append(", ").append(histology);
//        }
//        
//        String stage = guessClinicalData(clinicalFreeForms, 
//                new String[]{"tumor_stage","2009stagegroup","TUMORSTAGE"});
//        if (stage!=null && !stage.equalsIgnoreCase("unknown")) {
//            diseaseInfo.append(", ").append(stage); 
//        }
//        
//        String grade = guessClinicalData(clinicalFreeForms,
//                new String[]{"tumor_grade", "tumorgrade"});
//        if (grade!=null) {
//            diseaseInfo.append(", ").append(grade);
//        }
//        
//        // TODO: this is a hacky way to include the information in prad_mich
//        String etsRafSpink1Status = guessClinicalData(clinicalFreeForms,
//                new String[]{"ETS/RAF/SPINK1 status"});
//        if (etsRafSpink1Status!=null) {
//            diseaseInfo.append(", ").append(etsRafSpink1Status);
//        }
//        
//        // TODO: this is a hacky way to include the information in prad_broad
//        String tmprss2ErgFusionStatus = guessClinicalData(clinicalFreeForms,
//                new String[]{"TMPRSS2-ERG Fusion Status"});
//        if (tmprss2ErgFusionStatus!=null) {
//            diseaseInfo.append(", TMPRSS2-ERG Fusion: ").append(tmprss2ErgFusionStatus);
//        }
//        
//        // TODO: this is a hacky way to include the information in prad_mskcc
//        String ergFusion = guessClinicalData(clinicalFreeForms,
//                new String[]{"ERG-fusion aCGH"});
//        if (ergFusion!=null) {
//            diseaseInfo.append(", ERG-fusion aCGH: ").append(ergFusion);
//        }
//        
//        // TODO: this is a hacky way to include the serum psa information for prad
//        String serumPsa = guessClinicalData(clinicalFreeForms,
//                new String[]{"Serum PSA (ng/mL)","Serum PSA"});
//        if (serumPsa!=null) {
//            diseaseInfo.append(", Serum PSA: ").append(serumPsa);
//        }
//        
//        request.setAttribute(DISEASE_INFO, diseaseInfo.toString());
//        
//        // patient status
//        String oss = cd==null?null:cd.getOverallSurvivalStatus();
//        String dfss = cd==null?null:cd.getDiseaseFreeSurvivalStatus();
//        Double osm = cd==null?null:cd.getOverallSurvivalMonths();
//        Double dfsm = cd==null?null:cd.getDiseaseFreeSurvivalMonths();
//        StringBuilder patientStatus = new StringBuilder();
//        if (oss!=null && !oss.equalsIgnoreCase("unknown")) {
//            patientStatus.append("<font color='")
//                    .append(oss.equalsIgnoreCase("Living")||oss.equalsIgnoreCase("Alive") ? "green":"red")
//                    .append("'>")
//                    .append(oss)
//                    .append("</font>");
//            if (osm!=null) {
//                patientStatus.append(" (").append(osm.intValue()).append(" months)");
//            }
//        }
//        if (dfss!=null && !dfss.equalsIgnoreCase("unknown")) {
//            if (patientStatus.length()!=0) {
//                patientStatus.append(", ");
//            }
//            
//            patientStatus.append("<font color='")
//                    .append(dfss.equalsIgnoreCase("DiseaseFree") ? "green":"red")
//                    .append("'>")
//                    .append(dfss)
//                    .append("</font>");
//            if (dfsm!=null) {
//                patientStatus.append(" (").append(dfsm.intValue()).append(" months)");
//            }
//        }
//        
//        request.setAttribute(PATIENT_STATUS, patientStatus.toString());
        
        // images
        List<String> tisImages = getTissueImages(cancerStudy.getCancerStudyStableId(), patient);
        if (tisImages!=null) {
            request.setAttribute(TISSUE_IMAGES, tisImages);
        }
        
        // path report
        String typeOfCancer = cancerStudy.getTypeOfCancerId();
        if (cancerStudy.getCancerStudyStableId().contains(typeOfCancer+"_tcga")) {
            String pathReport = getTCGAPathReport(typeOfCancer, patient);
            if (pathReport!=null) {
                request.setAttribute(PATH_REPORT_URL, pathReport);
            }
        }
    }
    
    private Map<String,ClinicalFreeForm> getClinicalFreeform(int cancerStudyId, String patient) throws DaoException {
        List<ClinicalFreeForm> list = daoClinicalFreeForm.getCasesById(cancerStudyId, patient);
        Map<String,ClinicalFreeForm> map = new HashMap<String,ClinicalFreeForm>(list.size());
        for (ClinicalFreeForm cff : list) {
            map.put(cff.getParamName().toLowerCase(), cff);
        }
        return map;
    }
    
    private Map<String,String> mergeClinicalData(ClinicalData cd, Map<String,ClinicalFreeForm> cffs) {
        Map<String,String> map = new HashMap<String,String>();
        if (cd!=null&&cd.getAgeAtDiagnosis()!=null) {
            map.put("age",cd.getAgeAtDiagnosis().toString());
        }
        if (cd!=null&&cd.getOverallSurvivalStatus()!=null) {
            map.put("overall_survival_status", cd.getOverallSurvivalStatus());
        }
        if (cd!=null&&cd.getOverallSurvivalMonths()!=null) {
            map.put("overall_survival_months", Long.toString(Math.round(cd.getOverallSurvivalMonths())));
        }
        if (cd!=null&&cd.getDiseaseFreeSurvivalStatus()!=null) {
            map.put("disease-free_survival_status", cd.getDiseaseFreeSurvivalStatus());
        }
        if (cd!=null&&cd.getDiseaseFreeSurvivalMonths()!=null) {
            map.put("disease-free_survival_months", Long.toString(Math.round(cd.getDiseaseFreeSurvivalMonths())));
        }
        
        for (ClinicalFreeForm cff : cffs.values()) {
            map.put(cff.getParamName().toLowerCase(), cff.getParamValue());
        }
        
        return map;
    }
    
    // Map<StudyId, Map<CaseId, List<ImageName>>>
    private static Map<String,Map<String,List<String>>> tissueImages
            = new HashMap<String,Map<String,List<String>>>();
    private synchronized List<String> getTissueImages(String cancerStudyId, String caseId) {
            
        Map<String,List<String>> map = tissueImages.get(cancerStudyId);
        if (map==null) {
            map = new HashMap<String,List<String>>();
            tissueImages.put(cancerStudyId, map);
            
            String imageListUrl = SkinUtil.getTumorTissueImageUrl(cancerStudyId)+"image_list.txt";
            if (imageListUrl==null) {
                return null;
            }
        
            MultiThreadedHttpConnectionManager connectionManager =
                    ConnectionManager.getConnectionManager();
            HttpClient client = new HttpClient(connectionManager);
            GetMethod method = new GetMethod(imageListUrl);

            try {
                int statusCode = client.executeMethod(method);
                if (statusCode == HttpStatus.SC_OK) {
                    BufferedReader bufReader = new BufferedReader(
                            new InputStreamReader(method.getResponseBodyAsStream()));
                    for (String line=bufReader.readLine(); line!=null; line=bufReader.readLine()) {
                        String[] parts = line.split("\t");
                        String cId = parts[0];
                        String imageName = parts[1];
                        List<String> list = map.get(cId);
                        if (list==null) {
                            list = new ArrayList<String>();
                            map.put(cId, list);
                        }
                        list.add(imageName);
                    }
                } else {
                    //  Otherwise, throw HTTP Exception Object
                    logger.error(statusCode + ": " + HttpStatus.getStatusText(statusCode)
                            + " Base URL:  " + cancerStudyId);
                }
            } catch (Exception ex) {
                logger.error(ex.getMessage());
            } finally {
                //  Must release connection back to Apache Commons Connection Pool
                method.releaseConnection();
            }
        }
        
        return map.get(caseId);
    }
    
    // Map<TypeOfCancer, Map<CaseId, List<ImageName>>>
    private static Map<String,Map<String,String>> pathologyReports
            = new HashMap<String,Map<String,String>>();
    static final Pattern tcgaPathReportDirLinePattern = Pattern.compile("<a href=[^>]+>([^/]+/)</a>");
    static final Pattern tcgaPathReportPdfLinePattern = Pattern.compile("<a href=[^>]+>([^/]+\\.pdf)</a>");
    static final Pattern tcgaPathReportPattern = Pattern.compile("^(TCGA-..-....).+");
    private synchronized String getTCGAPathReport(String typeOfCancer, String caseId) {
        Map<String,String> map = pathologyReports.get(typeOfCancer);
        if (map==null) {
            map = new HashMap<String,String>();
            pathologyReports.put(typeOfCancer, map);
            
            String pathReportUrl = SkinUtil.getTCGAPathReportUrl(typeOfCancer);
            if (pathReportUrl!=null) {
                List<String> pathReportDirs = extractLinksByPattern(pathReportUrl,tcgaPathReportDirLinePattern);
                for (String dir : pathReportDirs) {
                    String url = pathReportUrl+dir;
                    List<String> pathReports = extractLinksByPattern(url,tcgaPathReportPdfLinePattern);
                    for (String report : pathReports) {
                        Matcher m = tcgaPathReportPattern.matcher(report);
                        if (m.find()) {
                            if (m.groupCount()>0) {
                                String exist = map.put(m.group(1), url+report);
                                if (exist!=null) {
                                    String msg = "Multiple Pathology reports for "+m.group(1)+": \n\t"
                                            + exist + "\n\t" + url+report;
                                    System.err.println(url);
                                    logger.error(msg);
                                }
                            }
                        }
                    }
                }
            }

        }
        
        return map.get(caseId);
    }
    
    private static List<String> extractLinksByPattern(String reportsUrl, Pattern p) {
        MultiThreadedHttpConnectionManager connectionManager =
                ConnectionManager.getConnectionManager();
        HttpClient client = new HttpClient(connectionManager);
        GetMethod method = new GetMethod(reportsUrl);
        try {
            int statusCode = client.executeMethod(method);
            if (statusCode == HttpStatus.SC_OK) {
                BufferedReader bufReader = new BufferedReader(
                        new InputStreamReader(method.getResponseBodyAsStream()));
                List<String> dirs = new ArrayList<String>();
                for (String line=bufReader.readLine(); line!=null; line=bufReader.readLine()) {
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        if (m.groupCount()>0) {
                            dirs.add(m.group(1));
                        }
                    }
                }
                return dirs;
            } else {
                //  Otherwise, throw HTTP Exception Object
                logger.error(statusCode + ": " + HttpStatus.getStatusText(statusCode)
                        + " Base URL:  " + reportsUrl);
            }
        } catch (Exception ex) {
            logger.error(ex.getMessage());
        } finally {
            //  Must release connection back to Apache Commons Connection Pool
            method.releaseConnection();
        }
        
        return Collections.emptyList();
    }
    
    private void forwardToErrorPage(HttpServletRequest request, HttpServletResponse response,
                                    String userMessage, XDebug xdebug)
            throws ServletException, IOException {
        request.setAttribute("xdebug_object", xdebug);
        request.setAttribute(QueryBuilder.USER_ERROR_MESSAGE, userMessage);
        RequestDispatcher dispatcher =
                getServletContext().getRequestDispatcher("/WEB-INF/jsp/error.jsp");
        dispatcher.forward(request, response);
    }
    
    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /** 
     * Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Returns a short description of the servlet.
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>
}