package uk.ac.lancs.sakai.assignmentsubmissionhook;

import org.apache.log4j.Logger;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.event.api.EventTrackingService;

public class AssignmentSubmissionHook {
	
	private Logger logger = Logger.getLogger(AssignmentSubmissionHook.class);
	
	public void init() {

		if (logger.isDebugEnabled()) logger.debug("Assignment submission hook init()");
		
		EventTrackingService eventTrackingService = (EventTrackingService) ComponentManager.get(EventTrackingService.class);
		
		if (eventTrackingService != null) {
			eventTrackingService.addObserver(new SubmissionObserver());
		} else {
			logger.error("EventTrackingService null");
		}
	}
}
