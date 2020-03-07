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
        utils.newState(utils.encodePossibleSettlementOpeningActions(game, ourPlayerData));

        boolean[] possibleActions = utils.getActions();
        int actionId = pythonBrain.chooseAction(possibleActions);
        if(!possibleActions[actionId]) {
            int k = 0;
            while(!possibleActions[k]) {
                k++;
            }
            System.out.print("ERROR, picked action " + actionId +" that isn't legal, replaced by first valid action (" + k + ")");
            actionId = k;
        }

        Utils.SOCAction action = new Utils.SOCAction(utils.format, game.getBoard(), actionId);

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
        utils.newState(utils.encodePossibleRoadOpeningActions(game, ourPlayerData));

        boolean[] possibleActions = utils.getActions();
        int actionId = pythonBrain.chooseAction(possibleActions);
        if(!possibleActions[actionId]) {
            int k = 0;
            while(!possibleActions[k]) {
                k++;
            }
            System.out.print("ERROR, picked action " + actionId +" that isn't legal, replaced by first valid action (" + k + ")");
            actionId = k;
        }

        Utils.SOCAction action = new Utils.SOCAction(utils.format, game.getBoard(), actionId);

        return action.parameters[0];
    }
}
