package uk.ac.lancs.sakai.assignmentsubmissionhook;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;

import org.apache.log4j.Logger;
import org.sakaiproject.assignment.api.Assignment;
import org.sakaiproject.assignment.api.AssignmentConstants;
import org.sakaiproject.assignment.api.AssignmentService;
import org.sakaiproject.assignment.api.AssignmentSubmission;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.event.api.Event;
import org.sakaiproject.user.api.UserDirectoryService;

/**
 * Listens for assignment submission events and adds an entry in the 
 * quartz job's queue so the LUSI flag will be set.
 * 
 * @author fisha
 */
public class SubmissionObserver implements Observer {
	
	private Logger logger = Logger.getLogger(SubmissionObserver.class);
	
	private AssignmentService assignmentService = null;
	private SecurityService securityService = null;
	private UserDirectoryService userDirectoryService = null;
	private SqlService sqlService;
	
	private DateFormat iso8061Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'+00:00'");
	
	public SubmissionObserver() {
		assignmentService = (AssignmentService) ComponentManager.get(AssignmentService.class);
		securityService = (SecurityService) ComponentManager.get(SecurityService.class);
		userDirectoryService = (UserDirectoryService) ComponentManager.get(UserDirectoryService.class);
		sqlService = (SqlService) ComponentManager.get(SqlService.class);
	}

	public void update(Observable o, Object arg) {
		
		if(arg instanceof Event) {
			Event e = (Event) arg;
			String name = e.getEvent();
			if(AssignmentConstants.EVENT_SUBMIT_ASSIGNMENT_SUBMISSION.equals(name)) {
				String reference = e.getResource();
				String[] parts = reference.split(Entity.SEPARATOR);
				if(parts.length < 6) {
					logger.warn("Unrecognised reference '" + reference + "'. Ignoring ...");
					return;
				}
				String assignmentId = parts[4];
				String submissionId = parts[5];
				
				SecurityAdvisor advisor = new SecurityAdvisor() {
					public SecurityAdvice isAllowed(String arg0, String arg1, String arg2) {
						return SecurityAdvice.ALLOWED;
					}
				};
				
				Connection conn = null;
				PreparedStatement pst = null;
				
				try {
					securityService.pushAdvisor(advisor);
					
					Assignment assignment = assignmentService.getAssignment(assignmentId);
					
					String workYearId = assignment.getProperties().getProperty("workId");
					
					if(workYearId == null || workYearId.equals("")) {
						if(logger.isDebugEnabled()) logger.debug("Not a LUSI provisioned Assignment. Ignoring ...");
						return;
					}
					
					AssignmentSubmission submission = assignmentService.getSubmission(submissionId);
					String login  = userDirectoryService.getUserEid(submission.getSubmitterIdString());
					Date sd = new Date(submission.getTimeSubmitted().getTime());
					String submittedDate = iso8061Format.format(sd);
					
					if(logger.isDebugEnabled()) logger.debug("WORK ID: " + workYearId + ". LOGIN: " + login + ".SUBMITTED DATE: " + submittedDate);
					
					String[] tokens = workYearId.split("-");
					String workId = tokens[0];
					String yearId = tokens[1];
					
					conn = sqlService.borrowConnection();
					
					pst = conn.prepareStatement("INSERT INTO LUSI_ESUBMISSION_JOBS (STUDENTUSERNAME,YEARIDENTITY,WORKIDENTITY,ESUBMISSIONTIME) VALUES(?,?,?,?)");
					pst.setString(1,login);
					pst.setString(2,yearId);
					pst.setString(3,workId);
					pst.setString(4,submittedDate);
					pst.executeUpdate();
				} catch (Exception e1) {
					logger.error("Failed to add esubmission flag to db",e1);
				} finally {
					securityService.popAdvisor(advisor);
					
					if(pst != null) {
						try {
							pst.close();
						} catch(Exception e2) {}
					}
					if(conn != null) {
						sqlService.returnConnection(conn);
					}
				}
			}
		}
	}
}
