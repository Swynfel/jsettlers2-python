package soc.robot.general;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceSet;
import soc.robot.DiscardStrategy;
import soc.robot.SOCPossiblePiece;
import soc.robot.SOCRobotBrain;

import java.util.Random;
import java.util.Stack;

public class RandomDiscardStrategy extends DiscardStrategy {

    public RandomDiscardStrategy(SOCGame ga, SOCPlayer pl, SOCRobotBrain br, Random rand) {
        super(ga, pl, br, rand);
    }

    @Override
    public SOCResourceSet discard(final int numDiscards, Stack<SOCPossiblePiece> _buildingPlan) {
        SOCResourceSet discarded = new SOCResourceSet();
        SOCResourceSet resources = ourPlayerData.getResources();
        int[] resourcesLeft = resources.getAmounts(false);
        int total = resources.getTotal();

        for (int k = 0; k < numDiscards; k++) {
            int pos = rand.nextInt(total);
            int resourceTypePicked = -1;
            while (pos >= 0 && resourceTypePicked < 4) {
                resourceTypePicked++;
                pos -= resourcesLeft[resourceTypePicked];
            }
            assert (pos < 0); // TODO
            resourcesLeft[resourceTypePicked]--;
            total--;
            discarded.add(1, resourceTypePicked + 1);
        }
        return discarded;
    }
}
