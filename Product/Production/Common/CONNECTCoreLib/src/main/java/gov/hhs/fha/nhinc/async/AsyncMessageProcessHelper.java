/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011(Year date of delivery) United States Government, as represented by the Secretary of Health and Human Services.  All rights reserved.
 *
 */
package gov.hhs.fha.nhinc.async;

import gov.hhs.fha.nhinc.asyncmsgs.dao.AsyncMsgRecordDao;
import gov.hhs.fha.nhinc.asyncmsgs.model.AsyncMsgRecord;
import gov.hhs.fha.nhinc.common.nhinccommon.AssertionType;
import gov.hhs.fha.nhinc.nhinclib.NhincConstants;
import gov.hhs.fha.nhinc.transform.marshallers.JAXBContextHandler;
import gov.hhs.fha.nhinc.transform.subdisc.HL7AckTransforms;
import java.io.ByteArrayOutputStream;
import java.sql.Blob;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Hibernate;
import org.hl7.v3.MCCIIN000002UV01;
import org.hl7.v3.PIXConsumerMCCIIN000002UV01RequestType;
import org.hl7.v3.PRPAIN201305UV02;
import org.hl7.v3.RespondingGatewayPRPAIN201305UV02RequestType;
import org.hl7.v3.RespondingGatewayPRPAIN201306UV02RequestType;

/**
 * This class provides methods to manage the async message record during its lifecycle.
 *
 * @author richard.ettema
 */
public class AsyncMessageProcessHelper {

    private static Log log = LogFactory.getLog(AsyncMessageProcessHelper.class);

    /**
     * Used to add the Deferred Patient Discovery Request to the local gateway
     * asyncmsgs repository.  The direction indicates the role of the local
     * gateway; i.e. outbound == initiator, inbound == receiver/responder
     *
     * @param request
     * @param assertion
     * @param direction
     * @return true - success; false - error
     */
    public boolean addPatientDiscoveryRequest(PRPAIN201305UV02 request, AssertionType assertion, String direction) {
        log.debug("Begin AsyncMessageProcessHelper.addPatientDiscoveryRequest(assertion)...");

        RespondingGatewayPRPAIN201305UV02RequestType newRequest = new RespondingGatewayPRPAIN201305UV02RequestType();
        newRequest.setAssertion(assertion);
        newRequest.setPRPAIN201305UV02(request);

        log.debug("End AsyncMessageProcessHelper.addPatientDiscoveryRequest(assertion)...");

        return addPatientDiscoveryRequest(newRequest, direction);
    }

    /**
     * Used to add the Deferred Patient Discovery Request to the local gateway
     * asyncmsgs repository.  The direction indicates the role of the local
     * gateway; i.e. outbound == initiator, inbound == receiver/responder
     *
     * @param request
     * @param direction
     * @return true - success; false - error
     */
    public boolean addPatientDiscoveryRequest(RespondingGatewayPRPAIN201305UV02RequestType request, String direction) {
        log.debug("Begin AsyncMessageProcessHelper.addPatientDiscoveryRequest()...");

        boolean result = false;

        try {
            List<AsyncMsgRecord> asyncMsgRecs = new ArrayList<AsyncMsgRecord>();
            AsyncMsgRecord rec = new AsyncMsgRecord();
            AsyncMsgRecordDao instance = new AsyncMsgRecordDao();

            // Replace with message id from the assertion class
            rec.setMessageId(request.getAssertion().getMessageId());
            rec.setCreationTime(new Date());
            rec.setServiceName(NhincConstants.PATIENT_DISCOVERY_SERVICE_NAME);
            rec.setDirection(direction);
            rec.setCommunityId(getPatientDiscoveryMessageCommunityId(request, direction));
            rec.setStatus(AsyncMsgRecordDao.QUEUE_STATUS_REQPROCESS);

            if (direction.equals(AsyncMsgRecordDao.QUEUE_DIRECTION_OUTBOUND)) {
                rec.setResponseType(AsyncMsgRecordDao.QUEUE_RESPONSE_TYPE_AUTO);
            } else {
                //TODO DEFERRED POLICY CHECK GOES HERE - until then, set to AUTO
                rec.setResponseType(AsyncMsgRecordDao.QUEUE_RESPONSE_TYPE_AUTO);
            }

            rec.setMsgData(getBlobFromPRPAIN201305UV02RequestType(request));

            asyncMsgRecs.add(rec);

            result = instance.insertRecords(asyncMsgRecs);

            if (result == false) {
                log.error("Failed to insert asynchronous record in the database");
            }
        } catch (Exception e) {
            log.error("ERROR: Failed to add the async request to async msg repository.", e);
        }

        log.debug("End AsyncMessageProcessHelper.addPatientDiscoveryRequest()...");

        return result;
    }

    /**
     * Process an acknowledgement error for the asyncmsgs record
     *
     * @param messageId
     * @param newStatus
     * @param ack
     * @return true - success; false - error
     */
    public boolean processAck(String messageId, String newStatus, String errorStatus, MCCIIN000002UV01 ack) {
        log.debug("Begin AsyncMessageProcessHelper.processAck()...");

        boolean result = false;

        try {
            if (isAckError(ack)) {
                newStatus = errorStatus;
            }
            AsyncMsgRecordDao instance = new AsyncMsgRecordDao();

            List<AsyncMsgRecord> records = instance.queryByMessageId(messageId);
            if (records != null && records.size() > 0) {
                records.get(0).setStatus(newStatus);
                records.get(0).setAckData(getBlobFromMCCIIN000002UV01(ack));
                instance.save(records.get(0));
            }

            // Success if we got this far
            result = true;
        } catch (Exception e) {
            log.error("ERROR: Failed to update the async request.", e);
        }

        log.debug("End AsyncMessageProcessHelper.processAck()...");

        return result;
    }

    /**
     * Process the new status for the asyncmsgs record
     * 
     * @param messageId
     * @param newStatus
     * @return true - success; false - error
     */
    public boolean processMessageStatus(String messageId, String newStatus) {
        log.debug("Begin AsyncMessageProcessHelper.processMessageStatus()...");

        boolean result = false;

        try {
            AsyncMsgRecordDao instance = new AsyncMsgRecordDao();

            List<AsyncMsgRecord> records = instance.queryByMessageId(messageId);
            if (records != null && records.size() > 0) {
                records.get(0).setStatus(newStatus);
                instance.save(records.get(0));
            }

            // Success if we got this far
            result = true;
        } catch (Exception e) {
            log.error("ERROR: Failed to update the async request status.", e);
        }

        log.debug("End AsyncMessageProcessHelper.processMessageStatus()...");

        return result;
    }

    /**
     * Process an acknowledgement error for the asyncmsgs record
     *
     * @param messageId
     * @param newStatus
     * @param ack
     * @return true - success; false - error
     */
    public boolean processPatientDiscoveryResponse(String messageId, String newStatus, String errorStatus, RespondingGatewayPRPAIN201306UV02RequestType response) {
        log.debug("Begin AsyncMessageProcessHelper.processPatientDiscoveryResponse()...");

        boolean result = false;

        try {
            if (response == null) {
                newStatus = errorStatus;
            }
            AsyncMsgRecordDao instance = new AsyncMsgRecordDao();

            List<AsyncMsgRecord> records = instance.queryByMessageId(messageId);
            if (records != null && records.size() > 0) {
                records.get(0).setResponseTime(new Date());

                // Calculate the duration in milliseconds
                Long duration = null;
                duration = records.get(0).getResponseTime().getTime() - records.get(0).getCreationTime().getTime();
                records.get(0).setDuration(duration);

                records.get(0).setStatus(newStatus);
                records.get(0).setRspData(getBlobFromPRPAIN201306UV02RequestType(response));
                instance.save(records.get(0));
            }

            // Success if we got this far
            result = true;
        } catch (Exception e) {
            log.error("ERROR: Failed to update the async response.", e);
        }

        log.debug("End AsyncMessageProcessHelper.processPatientDiscoveryResponse()...");

        return result;
    }

    private Blob getBlobFromMCCIIN000002UV01(MCCIIN000002UV01 ack) {
        Blob asyncMessage = null; //Not Implemented

        try {
            JAXBContextHandler oHandler = new JAXBContextHandler();
            JAXBContext jc = oHandler.getJAXBContext("org.hl7.v3");
            Marshaller marshaller = jc.createMarshaller();
            ByteArrayOutputStream baOutStrm = new ByteArrayOutputStream();
            baOutStrm.reset();
            org.hl7.v3.ObjectFactory factory = new org.hl7.v3.ObjectFactory();
            PIXConsumerMCCIIN000002UV01RequestType request = factory.createPIXConsumerMCCIIN000002UV01RequestType();
            request.setMCCIIN000002UV01(ack);
            JAXBElement<PIXConsumerMCCIIN000002UV01RequestType> oJaxbElement = factory.createPIXConsumerMCCIIN000002UV01Request(request);
            baOutStrm.close();
            marshaller.marshal(oJaxbElement, baOutStrm);
            byte[] buffer = baOutStrm.toByteArray();
            asyncMessage = Hibernate.createBlob(buffer);
        } catch (Exception e) {
            log.error("Exception during Blob conversion :" + e.getMessage());
            e.printStackTrace();
        }

        return asyncMessage;
    }

    private Blob getBlobFromPRPAIN201305UV02RequestType(RespondingGatewayPRPAIN201305UV02RequestType request) {
        Blob asyncMessage = null; //Not Implemented

        try {
            JAXBContextHandler oHandler = new JAXBContextHandler();
            JAXBContext jc = oHandler.getJAXBContext("org.hl7.v3");
            Marshaller marshaller = jc.createMarshaller();
            ByteArrayOutputStream baOutStrm = new ByteArrayOutputStream();
            baOutStrm.reset();
            org.hl7.v3.ObjectFactory factory = new org.hl7.v3.ObjectFactory();
            JAXBElement<RespondingGatewayPRPAIN201305UV02RequestType> oJaxbElement = factory.createRespondingGatewayPRPAIN201305UV02Request(request);
            baOutStrm.close();
            marshaller.marshal(oJaxbElement, baOutStrm);
            byte[] buffer = baOutStrm.toByteArray();
            asyncMessage = Hibernate.createBlob(buffer);
        } catch (Exception e) {
            log.error("Exception during Blob conversion :" + e.getMessage());
            e.printStackTrace();
        }

        return asyncMessage;
    }

    private Blob getBlobFromPRPAIN201306UV02RequestType(RespondingGatewayPRPAIN201306UV02RequestType request) {
        Blob asyncMessage = null; //Not Implemented

        try {
            JAXBContextHandler oHandler = new JAXBContextHandler();
            JAXBContext jc = oHandler.getJAXBContext("org.hl7.v3");
            Marshaller marshaller = jc.createMarshaller();
            ByteArrayOutputStream baOutStrm = new ByteArrayOutputStream();
            baOutStrm.reset();
            org.hl7.v3.ObjectFactory factory = new org.hl7.v3.ObjectFactory();
            JAXBElement<RespondingGatewayPRPAIN201306UV02RequestType> oJaxbElement = factory.createRespondingGatewayPRPAIN201306UV02Request(request);
            baOutStrm.close();
            marshaller.marshal(oJaxbElement, baOutStrm);
            byte[] buffer = baOutStrm.toByteArray();
            asyncMessage = Hibernate.createBlob(buffer);
        } catch (Exception e) {
            log.error("Exception during Blob conversion :" + e.getMessage());
            e.printStackTrace();
        }

        return asyncMessage;
    }

    private boolean isAckError(MCCIIN000002UV01 ack) {
        boolean result = false;

        if (ack.getAcknowledgement() != null &&
                ack.getAcknowledgement().size() > 0 &&
                ack.getAcknowledgement().get(0).getTypeCode() != null &&
                ack.getAcknowledgement().get(0).getTypeCode().getCode() != null &&
                ack.getAcknowledgement().get(0).getTypeCode().getCode().equals(HL7AckTransforms.ACK_TYPE_CODE_ERROR)) {
            result = true;
        }

        return result;
    }

    /**
     * Get the home community id of the communicating gateway
     * @param requestMessage
     * @param direction
     * @return String
     */
    private String getPatientDiscoveryMessageCommunityId(RespondingGatewayPRPAIN201305UV02RequestType requestMessage, String direction) {
        String communityId = "";
        boolean useReceiver = false;

        if (requestMessage != null && direction != null) {
            if (direction.equals(AsyncMsgRecordDao.QUEUE_DIRECTION_OUTBOUND)) {
                useReceiver = true;
            }

            if (useReceiver) {
                if (requestMessage.getPRPAIN201305UV02() != null &&
                        requestMessage.getPRPAIN201305UV02().getReceiver() != null &&
                        requestMessage.getPRPAIN201305UV02().getReceiver().size() > 0 &&
                        requestMessage.getPRPAIN201305UV02().getReceiver().get(0) != null &&
                        requestMessage.getPRPAIN201305UV02().getReceiver().get(0).getDevice() != null &&
                        requestMessage.getPRPAIN201305UV02().getReceiver().get(0).getDevice().getAsAgent() != null &&
                        requestMessage.getPRPAIN201305UV02().getReceiver().get(0).getDevice().getAsAgent().getValue() != null &&
                        requestMessage.getPRPAIN201305UV02().getReceiver().get(0).getDevice().getAsAgent().getValue().getRepresentedOrganization() != null &&
                        requestMessage.getPRPAIN201305UV02().getReceiver().get(0).getDevice().getAsAgent().getValue().getRepresentedOrganization().getValue() != null &&
                        requestMessage.getPRPAIN201305UV02().getReceiver().get(0).getDevice().getAsAgent().getValue().getRepresentedOrganization().getValue().getId() != null &&
                        requestMessage.getPRPAIN201305UV02().getReceiver().get(0).getDevice().getAsAgent().getValue().getRepresentedOrganization().getValue().getId().size() > 0 &&
                        requestMessage.getPRPAIN201305UV02().getReceiver().get(0).getDevice().getAsAgent().getValue().getRepresentedOrganization().getValue().getId().get(0).getRoot() != null) {
                    communityId = requestMessage.getPRPAIN201305UV02().getReceiver().get(0).getDevice().getAsAgent().getValue().getRepresentedOrganization().getValue().getId().get(0).getRoot();
                }
                // If represented organization is empty or null, check the device id
                if (communityId == null || communityId.equals("")) {
                    if (requestMessage.getPRPAIN201305UV02().getReceiver() != null &&
                            requestMessage.getPRPAIN201305UV02().getReceiver().size() > 0 &&
                            requestMessage.getPRPAIN201305UV02().getReceiver().get(0) != null &&
                            requestMessage.getPRPAIN201305UV02().getReceiver().get(0).getDevice() != null &&
                            requestMessage.getPRPAIN201305UV02().getReceiver().get(0).getDevice().getId() != null &&
                            requestMessage.getPRPAIN201305UV02().getReceiver().get(0).getDevice().getId().size() > 0 &&
                            requestMessage.getPRPAIN201305UV02().getReceiver().get(0).getDevice().getId().get(0) != null &&
                            requestMessage.getPRPAIN201305UV02().getReceiver().get(0).getDevice().getId().get(0).getRoot() != null) {
                        communityId = requestMessage.getPRPAIN201305UV02().getReceiver().get(0).getDevice().getId().get(0).getRoot();
                    }
                }
            } else {
                if (requestMessage.getPRPAIN201305UV02().getSender() != null &&
                        requestMessage.getPRPAIN201305UV02().getSender().getDevice() != null &&
                        requestMessage.getPRPAIN201305UV02().getSender().getDevice().getAsAgent() != null &&
                        requestMessage.getPRPAIN201305UV02().getSender().getDevice().getAsAgent().getValue() != null &&
                        requestMessage.getPRPAIN201305UV02().getSender().getDevice().getAsAgent().getValue().getRepresentedOrganization() != null &&
                        requestMessage.getPRPAIN201305UV02().getSender().getDevice().getAsAgent().getValue().getRepresentedOrganization().getValue() != null &&
                        requestMessage.getPRPAIN201305UV02().getSender().getDevice().getAsAgent().getValue().getRepresentedOrganization().getValue().getId() != null &&
                        requestMessage.getPRPAIN201305UV02().getSender().getDevice().getAsAgent().getValue().getRepresentedOrganization().getValue().getId().size() > 0 &&
                        requestMessage.getPRPAIN201305UV02().getSender().getDevice().getAsAgent().getValue().getRepresentedOrganization().getValue().getId().get(0).getRoot() != null) {
                    communityId = requestMessage.getPRPAIN201305UV02().getSender().getDevice().getAsAgent().getValue().getRepresentedOrganization().getValue().getId().get(0).getRoot();
                }
                // If represented organization is empty or null, check the device id
                if (communityId == null || communityId.equals("")) {
                    if (requestMessage.getPRPAIN201305UV02().getSender() != null &&
                            requestMessage.getPRPAIN201305UV02().getSender().getDevice() != null &&
                            requestMessage.getPRPAIN201305UV02().getSender().getDevice() != null &&
                            requestMessage.getPRPAIN201305UV02().getSender().getDevice().getId() != null &&
                            requestMessage.getPRPAIN201305UV02().getSender().getDevice().getId().size() > 0 &&
                            requestMessage.getPRPAIN201305UV02().getSender().getDevice().getId().get(0) != null &&
                            requestMessage.getPRPAIN201305UV02().getSender().getDevice().getId().get(0).getRoot() != null) {
                        communityId = requestMessage.getPRPAIN201305UV02().getSender().getDevice().getId().get(0).getRoot();
                    }
                }
            }
        }

        return communityId;
    }

}
