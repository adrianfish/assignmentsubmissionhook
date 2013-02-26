package uk.ac.lancs.sakai.assignmentsubmissionhook;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.db.api.SqlService;

public class LusiSubmissionQuartzJob implements StatefulJob {
	
	private Logger logger = Logger.getLogger(LusiSubmissionQuartzJob.class);
	
	private String wsUsername;
	private String wsPassword;
	private String wsUrl;
	
	private SqlService sqlService;
	
	public void init() throws Exception {
		sqlService = (SqlService) ComponentManager.get(SqlService.class);
		
		wsUsername = ServerConfigurationService.getString("lusiWSUsername",null);
		wsPassword = ServerConfigurationService.getString("lusiWSPassword",null);
		wsUrl = ServerConfigurationService.getString("lusiWSUrl",null);
		
		if(wsUsername == null || wsPassword == null || wsUrl == null) {
			logger.error("lusiWSUsername, lusiWSPassword and lusiWSUrl must be specified in sakai.properties.");
			throw new Exception("lusiWSUsername, lusiWSPassword and lusiWSUrl must be specified in sakai.properties.");
		}
	}

	public void execute(JobExecutionContext context) throws JobExecutionException {
		Connection conn = null;
		Statement st = null;
		List<PreparedStatement> removeStatements = new ArrayList<PreparedStatement>();
		List<PreparedStatement> updateStatements = new ArrayList<PreparedStatement>();
		
		try {
			conn = sqlService.borrowConnection();
			st = conn.createStatement();
			ResultSet rs = st.executeQuery("SELECT * FROM LUSI_ESUBMISSION_JOBS WHERE ATTEMPTS < 5");
			while(rs.next()) {
				String studentUsername = rs.getString("STUDENTUSERNAME");
				String yearIdentity = rs.getString("YEARIDENTITY");
				String workIdentity = rs.getString("WORKIDENTITY");
				String esubmissionTime = rs.getString("ESUBMISSIONTIME");
				int attempts = rs.getInt("ATTEMPTS");
					
				StringBuilder dataBuffer = new StringBuilder("Username=" + URLEncoder.encode(wsUsername, "UTF-8"));
				dataBuffer.append("&Password=" + URLEncoder.encode(wsPassword, "UTF-8"));
				dataBuffer.append("&StudentUsername=" + URLEncoder.encode(studentUsername, "UTF-8"));
				dataBuffer.append("&YearIdentity=" + URLEncoder.encode(yearIdentity, "UTF-8"));
				dataBuffer.append("&WorkIdentity=" + URLEncoder.encode(workIdentity, "UTF-8"));
				dataBuffer.append("&ElectronicSubmissionDateTime=" + URLEncoder.encode(esubmissionTime,"UTF-8"));
				
				String data = dataBuffer.toString();
				logger.debug(data);
				URL url = new URL(wsUrl);
				HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
				httpConn.setDoOutput(true);
				OutputStreamWriter wr = new OutputStreamWriter(httpConn.getOutputStream());
				wr.write(data);
				wr.flush();
				if(httpConn.getResponseCode() == 200) {
					// Success, remove the job from the db
					PreparedStatement pst = conn.prepareStatement("DELETE FROM LUSI_ESUBMISSION_JOBS WHERE STUDENTUSERNAME = ? AND YEARIDENTITY = ? AND WORKIDENTITY = ? AND ESUBMISSIONTIME = ?");
					pst.setString(1,studentUsername);
					pst.setString(2,yearIdentity);
					pst.setString(3,workIdentity);
					pst.setString(4,esubmissionTime);
					removeStatements.add(pst);
				} else {
					logger.warn("Failed to set e-submission flag for workid '" + workIdentity + "' in LUSI");
					// Failure. Increment the attempt column.
					attempts += 1;
					PreparedStatement pst = conn.prepareStatement("UPDATE LUSI_ESUBMISSION_JOBS SET ATTEMPTS = ? WHERE STUDENTUSERNAME = ? AND YEARIDENTITY = ? AND WORKIDENTITY = ? AND ESUBMISSIONTIME = ?");
					pst.setInt(1, attempts);
					pst.setString(2,studentUsername);
					pst.setString(3,yearIdentity);
					pst.setString(4,workIdentity);
					pst.setString(5,esubmissionTime);
					updateStatements.add(pst);
				}
				httpConn.disconnect();
			} // while(rs.next())
			
			for(PreparedStatement pst : removeStatements) {
				pst.executeUpdate();
			}
			
			for(PreparedStatement pst : updateStatements) {
				pst.executeUpdate();
			}
		} catch(Exception e) {
			logger.error("Failed to submit assignment flag to LUSI. Reason: " + e.getMessage());
		} finally {
			if(st != null) {
				try {
					st.close();
				} catch (Exception e) {}
			}
			for(PreparedStatement pst : removeStatements) {
				try {
					pst.close();
				} catch (Exception e) {}
			}
			for(PreparedStatement pst : updateStatements) {
				try {
					pst.close();
				} catch (Exception e) {}
			}
			
			sqlService.returnConnection(conn);
		}
	}
}
