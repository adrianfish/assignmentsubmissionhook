package uk.ac.lancs.sakai.assignmentsubmissionhook;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateTest {

    public static void main(String[] args) {
	
	    DateFormat iso8061Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        System.out.println(iso8061Format.format(new Date()));
    }
}
