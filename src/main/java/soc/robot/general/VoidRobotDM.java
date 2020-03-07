package soc.robot.general;

import soc.game.*;
import soc.robot.*;
import soc.util.*;
import java.util.*;

public class VoidRobotDM extends SOCRobotDM {
    public VoidRobotDM(SOCRobotBrain br) {
    super(br);
    }

    public VoidRobotDM(SOCRobotParameters params,
                       HashMap<Integer, SOCPlayerTracker> pt,
                       SOCPlayerTracker opt,
                       SOCPlayer opd,
                       Stack<SOCPossiblePiece> bp) {
        super(params, pt, opt, opd, bp);
    }

    public void planStuff(final int strategy) {}
}

