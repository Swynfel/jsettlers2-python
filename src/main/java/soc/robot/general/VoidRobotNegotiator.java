package soc.robot.general;

import soc.game.SOCTradeOffer;
import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotNegotiator;

public class VoidRobotNegotiator extends SOCRobotNegotiator
{
    public VoidRobotNegotiator(SOCRobotBrain br)
    {
    	super(br);
    }

    public int considerOffer2(SOCTradeOffer offer, final int receiverNum)
    {
    	return REJECT_OFFER;
    }

}
