/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gov.hhs.fha.nhinc.admindistribution;

import gov.hhs.fha.nhinc.auditrepository.AuditRepositoryLogger;
import gov.hhs.fha.nhinc.auditrepository.proxy.AuditRepositoryProxy;
import gov.hhs.fha.nhinc.auditrepository.proxy.AuditRepositoryProxyObjectFactory;
import gov.hhs.fha.nhinc.common.auditlog.LogEventRequestType;
import gov.hhs.fha.nhinc.common.nhinccommon.AcknowledgementType;
import oasis.names.tc.emergency.edxl.de._1.EDXLDistribution;
import gov.hhs.fha.nhinc.common.nhinccommon.AssertionType;
import gov.hhs.fha.nhinc.common.nhinccommon.NhinTargetCommunitiesType;
import gov.hhs.fha.nhinc.common.nhinccommon.NhinTargetSystemType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import gov.hhs.fha.nhinc.common.nhinccommonentity.RespondingGatewaySendAlertMessageType;
import gov.hhs.fha.nhinc.common.nhinccommonentity.RespondingGatewaySendAlertMessageSecuredType;

/**
 *
 * @author dunnek
 */
public class AdminDistributionAuditLogger {

    private AcknowledgementType audit(LogEventRequestType auditLogMsg, AssertionType assertion)
    {
        AuditRepositoryProxyObjectFactory auditRepoFactory = new AuditRepositoryProxyObjectFactory();
        AuditRepositoryProxy proxy = auditRepoFactory.getAuditRepositoryProxy();
        return proxy.auditLog(auditLogMsg, assertion);
    }


    public AcknowledgementType auditEntityAdminDist (RespondingGatewaySendAlertMessageType request, AssertionType assertion, String direction) {
        AcknowledgementType ack = new AcknowledgementType ();

        // Set up the audit logging request message
        AuditRepositoryLogger auditLogger = new AuditRepositoryLogger();
        LogEventRequestType auditLogMsg = auditLogger.logEntityAdminDist(request, assertion, direction);

        if (auditLogMsg != null) {
            ack = audit(auditLogMsg, assertion);
        }

        return ack;
    }
    public AcknowledgementType auditNhincAdminDist(EDXLDistribution body, AssertionType assertion, NhinTargetSystemType target, String direction)
    {
        AcknowledgementType ack = null;
        AuditRepositoryLogger auditLogger = new AuditRepositoryLogger();


        LogEventRequestType auditLogMsg = auditLogger.logNhincAdminDist(body, assertion,target, direction);

        if (auditLogMsg != null) {
            ack = audit(auditLogMsg, assertion);
        }
        return ack;
    }
 
}
