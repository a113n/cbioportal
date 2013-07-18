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
import org.apache.commons.lang.StringUtils;
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
    public static final String CASE_ID = "case_id";
    public static final String PATIENT_ID_ATTR_NAME = "PATIENT_ID";
    public static final String PATIENT_CASE_OBJ = "case_obj";
    public static final String CANCER_STUDY = "cancer_study";
    public static final String HAS_SEGMENT_DATA = "has_segment_data";
    public static final String HAS_ALLELE_FREQUENCY_DATA = "has_allele_frequency_data";
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
        String patientID = request.getParameter(CASE_ID);
        String cancerStudyId = request.getParameter(QueryBuilder.CANCER_STUDY_ID);

        request.setAttribute(QueryBuilder.HTML_TITLE, "Patient "+patientID);
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

    /**
     *
     * Tests whether there is allele frequency data for a patient in a cancer study.
     * It gets all the mutations and then checks the values for allele frequency.
     *
     * @return Boolean
     *
     * @author Gideon Dresdner
     */
    public boolean hasAlleleFrequencyData(CancerStudy cancerStudy, String patientId, GeneticProfile mutationProfile) throws DaoException {
        if (mutationProfile==null) {
            return false;
        }
        
        List<ExtendedMutation> mutations
                = DaoMutation.getMutations(mutationProfile.getGeneticProfileId(), patientId);

        for (ExtendedMutation mutation : mutations) {
            if (mutation.getTumorAltCount() != -1) {
                return true;
            }
        }

        return false;
    }

    private boolean validate(HttpServletRequest request) throws DaoException {
        
        // by default; in case return false;
        request.setAttribute(HAS_SEGMENT_DATA, Boolean.FALSE);
        request.setAttribute(HAS_ALLELE_FREQUENCY_DATA, Boolean.FALSE);
        
        String caseIdsStr = request.getParameter(CASE_ID);
        if (caseIdsStr == null || caseIdsStr.isEmpty()) {
            request.setAttribute(ERROR, "Please specify at least one case ID. ");
            return false;
        }
        String[] patientIds = caseIdsStr.split(" +");
        
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

        LinkedHashSet<Case> cases = new LinkedHashSet<Case>(patientIds.length);
        List<String> sampleIds = new ArrayList<String>();
        for (String patientId : patientIds) {
            Case _case = DaoCase.getCase(patientId, cancerStudy.getInternalId());
            if (_case != null) {
                cases.add(_case);
                sampleIds.add(_case.getCaseId());
            } else {
                List<String> samples = daoClinicalFreeForm.getCaseIdsByAttribute(
                    cancerStudy.getInternalId(), PATIENT_ID_ATTR_NAME, patientId);
                for (String sample : samples) {
                    _case = DaoCase.getCase(sample, cancerStudy.getInternalId());
                    if (_case != null) {
                        cases.add(_case);
                        sampleIds.add(_case.getCaseId());
                    }
                }
            }
        }

        if (cases.isEmpty()) {
            request.setAttribute(ERROR, "We have no information about patients "+caseIdsStr);
            return false;
        }
        
        
        request.setAttribute(CASE_ID, StringUtils.join(sampleIds, " "));
        
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
        request.setAttribute(HAS_ALLELE_FREQUENCY_DATA, patientIds.length>1 ? Boolean.FALSE :
                hasAlleleFrequencyData(cancerStudy, patientIds[0], cancerStudy.getMutationProfile(patientIds[0])));
        
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
        Map<String,ClinicalFreeForm> clinicalFreeForms = Collections.emptyMap();
        Map<String,Map<String,String>> clinicalData = new LinkedHashMap<String,Map<String,String>>();
        for (Case _case : cases) {
            patient = _case.getCaseId();
            cd = daoClinicalData.getCase(cancerStudy.getInternalId(),patient);
            clinicalFreeForms = getClinicalFreeform(cancerStudy.getInternalId(),patient);
            clinicalData.put(patient,mergeClinicalData(cd, clinicalFreeForms));
        }
        request.setAttribute(CLINICAL_DATA, clinicalData);
        
        if (cases.size()>1) {
            return;
        }
        
        // images
        String tisImageUrl = getTissueImageIframeUrl(cancerStudy.getCancerStudyStableId(), patient);
        if (tisImageUrl!=null) {
            request.setAttribute(TISSUE_IMAGES, tisImageUrl);
        }
        
        // path report
        String typeOfCancer = cancerStudy.getTypeOfCancerId();
        if (cancerStudy.getCancerStudyStableId().contains(typeOfCancer+"_tcga")) {
            String pathReport = getTCGAPathReport(typeOfCancer, patient);
            if (pathReport!=null) {
                request.setAttribute(PATH_REPORT_URL, pathReport);
            }
        }
        
        // other cases with the same patient id
        ClinicalFreeForm cff = clinicalFreeForms.get(PATIENT_ID_ATTR_NAME.toLowerCase());
        if (cff!=null) {
            String patientId = cff.getParamValue();
            List<String> samples = daoClinicalFreeForm.getCaseIdsByAttribute(
                    cancerStudy.getInternalId(), PATIENT_ID_ATTR_NAME, patientId);
            if (samples.size()>1) {
                request.setAttribute(PATIENT_ID_ATTR_NAME, patientId);
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
    
    private String getTissueImageIframeUrl(String cancerStudyId, String caseId) {
        if (!caseId.toUpperCase().startsWith("TCGA-")) {
            return null;
        }
        
        // test if images exist for the case
        String metaUrl = SkinUtil.getDigitalSlideArchiveMetaUrl(caseId);
        MultiThreadedHttpConnectionManager connectionManager =
                    ConnectionManager.getConnectionManager();
        HttpClient client = new HttpClient(connectionManager);
        GetMethod method = new GetMethod(metaUrl);

        Pattern p = Pattern.compile("<data total_count='([0-9]+)'>");
        try {
            int statusCode = client.executeMethod(method);
            if (statusCode == HttpStatus.SC_OK) {
                BufferedReader bufReader = new BufferedReader(
                        new InputStreamReader(method.getResponseBodyAsStream()));
                for (String line=bufReader.readLine(); line!=null; line=bufReader.readLine()) {
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        int count = Integer.parseInt(m.group(1));
                        return count>0 ? SkinUtil.getDigitalSlideArchiveIframeUrl(caseId) : null;
                    }
                }
                
            } else {
                //  Otherwise, throw HTTP Exception Object
                logger.error(statusCode + ": " + HttpStatus.getStatusText(statusCode)
                        + " Base URL:  " + metaUrl);
            }
        } catch (Exception ex) {
            logger.error(ex.getMessage());
        } finally {
            //  Must release connection back to Apache Commons Connection Pool
            method.releaseConnection();
        }
        
        return null;
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
