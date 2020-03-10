package soc.robot.python;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.robot.OpeningBuildStrategy;
import soc.robot.general.Utils;

public class PythonOpeningBuildStrategy extends OpeningBuildStrategy {

    protected PythonRobotBrain pythonBrain;
    protected Utils utils;

    public PythonOpeningBuildStrategy(SOCGame ga, SOCPlayer pl, PythonRobotBrain pythonBrain, Utils utils) {
        super(ga, pl);
        this.pythonBrain = pythonBrain;
        this.utils = utils;
    }

    private int pickSettlement() {
        utils.newState();
        utils.initialSettlementActions();

        Utils.SOCAction action = pythonBrain.chooseAction();

        return action.parameters[0];
    }

    @Override
    public int getPlannedInitRoadDestinationNode()
    {
        return 0;
    }

    @Override
    public int planInitialSettlements() {
        return pickSettlement();
    }

    @Override
    public int planSecondSettlement() {
        return pickSettlement();
    }

    @Override
    public int planInitRoad() {
        utils.newState();
        utils.initialRoadActions();

        Utils.SOCAction action = pythonBrain.chooseAction();

        return action.parameters[0];
    }
}
