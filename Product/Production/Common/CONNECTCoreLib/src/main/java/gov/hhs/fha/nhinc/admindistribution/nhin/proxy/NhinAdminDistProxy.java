/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gov.hhs.fha.nhinc.admindistribution.nhin.proxy;
import oasis.names.tc.emergency.edxl.de._1.EDXLDistribution;
import gov.hhs.fha.nhinc.common.nhinccommon.AssertionType;
import gov.hhs.fha.nhinc.common.nhinccommon.NhinTargetSystemType;
/**
 *
 * @author dunnek
 */
public interface NhinAdminDistProxy {
    public void sendAlertMessage(EDXLDistribution body, AssertionType assertion, NhinTargetSystemType target);
}
