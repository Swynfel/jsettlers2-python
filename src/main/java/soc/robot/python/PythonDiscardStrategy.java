package soc.robot.python;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceSet;
import soc.robot.DiscardStrategy;
import soc.robot.SOCPossiblePiece;
import soc.robot.SOCRobotBrain;
import soc.robot.general.Utils;

import java.util.Random;
import java.util.Stack;

public class PythonDiscardStrategy extends DiscardStrategy {

    PythonRobotBrain pythonBrain;
    Utils utils;

    public PythonDiscardStrategy(SOCGame ga, SOCPlayer pl, PythonRobotBrain pythonBrain, Utils utils, Random rand) {
        super(ga, pl, pythonBrain, rand);
        this.pythonBrain = pythonBrain;
        this.utils = utils;
    }

    @Override
    public SOCResourceSet discard(final int numDiscards, Stack<SOCPossiblePiece> _buildingPlan) {

        utils.newState();
        utils.discardActions();

        Utils.SOCAction action = pythonBrain.chooseAction();

        assert action.type == Utils.SOCAction.KEEP;
        assert action.parameters.length == 5;

        SOCResourceSet kept = Utils.jsettlersResources(
                action.parameters[0],
                action.parameters[1],
                action.parameters[2],
                action.parameters[3],
                action.parameters[4]
        );
        SOCResourceSet discarded = ourPlayerData.getResources().copy();
        discarded.subtract(kept);

        // Add cards randomly is discarding too much
        for (int discardedCards = discarded.getTotal(); discardedCards > numDiscards; discardedCards--) {
            int pos = rand.nextInt(discardedCards);
            int resourceTypePicked = -1;
            while (pos >= 0 && resourceTypePicked < 4) {
                resourceTypePicked++;
                pos -= discarded.getAmount(resourceTypePicked);
            }
            discarded.subtract(1, resourceTypePicked + 1);
        }
        return discarded;
    }
}
