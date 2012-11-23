package org.jembi.rhea.transformers;

import ihe.iti.atna.ATNAUtil;
import ihe.iti.atna.AuditMessage;
import ihe.iti.atna.EventIdentificationType;

import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.mule.api.MuleMessage;
import org.mule.api.transformer.TransformerException;
import org.mule.transformer.AbstractMessageTransformer;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v25.message.RSP_K23;
import ca.uhn.hl7v2.parser.EncodingNotSupportedException;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.parser.Parser;

/**
 * Processes the response from an ITI-9 PIX Query request
 * and the returns the affinity domain identifier as a string.
 */
public class PIXQueryResponseTransformer extends AbstractMessageTransformer {

	@Override
	public Object transformMessage(MuleMessage message, String outputEncoding)
			throws TransformerException {
		try {
			String response = message.getPayloadAsString();
			
			// Strip MLLP chars
			response = response.replace("\013", "");
			response = response.replace("\034", "");
			
			String pid = parseResponse(response);
			
			// send auditing message
			String request = (String)message.getSessionProperty("PIX Request");
			String at = generateATNAMessage(request, pid);
			//TODO send
			
			return pid;
			
		} catch (EncodingNotSupportedException e) {
			throw new TransformerException(this, e);
		} catch (HL7Exception e) {
			throw new TransformerException(this, e);
		} catch (Exception e) { // Pokemon exception handling, when you just gotta catch them all!
			throw new TransformerException(this, e);
		}
	}

	
	protected String parseResponse(String response) throws EncodingNotSupportedException, HL7Exception {
		Parser parser = new GenericParser();
		RSP_K23 msg = (RSP_K23)parser.parse(response);
		
		int numIds = msg.getQUERY_RESPONSE().getPID().getPid3_PatientIdentifierListReps();
		if (numIds < 1)
			return null;
		
		return msg.getQUERY_RESPONSE().getPID().getPatientIdentifierList(0).getCx1_IDNumber().getValue();
	}
	
	protected String generateATNAMessage(String request, String patientId) throws JAXBException {
		AuditMessage res = new AuditMessage();
		
		EventIdentificationType eid = new EventIdentificationType();
		eid.setEventID( ATNAUtil.buildCodedValueType("DCM", "110112", "Query") );
		eid.setEventActionCode("E");
		eid.getEventTypeCode().add( ATNAUtil.buildCodedValueType("IHE Transactions", "ITI-9", "PIX Query") );
		res.setEventIdentification(eid);
		
		String ip = "";
		try {
			ip = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) { /* shouldn't happen since we're referencing localhost */ }
		res.getActiveParticipant().add( ATNAUtil.buildActiveParticipant("SOME_FACILITY|OpenHIM", true, ip, (short)2, "DCM", "110153", "Source"));
		res.getActiveParticipant().add( ATNAUtil.buildActiveParticipant("CR1|MOH_CAAT", true, "shr", (short)1, "DCM", "110152", "Destination"));
		res.getParticipantObjectIdentification().add(
			ATNAUtil.buildParticipantObjectIdentificationType(patientId +  "^^^&ECID&ISO", (short)1, (short)1, "RFC-3881", "2", "PatientNumber", null)
		);
		res.getParticipantObjectIdentification().add(
			ATNAUtil.buildParticipantObjectIdentificationType(
				UUID.randomUUID().toString(), (short)2, (short)24, "IHE Transactions", "ITI-21", "ITI21", request
			)
		);
		
		JAXBContext jc = JAXBContext.newInstance("ihe.iti.atna");
		Marshaller marshaller = jc.createMarshaller();
		StringWriter sw = new StringWriter();
		marshaller.marshal(res, sw);
		return sw.toString();
	}
	

}
