package soc.robot.general;

import java.util.Random;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.robot.RobberStrategy;
import soc.robot.SOCRobotBrain;

public class RandomRobberStrategy extends RobberStrategy {
    public RandomRobberStrategy(SOCGame ga, SOCPlayer pl, SOCRobotBrain br, Random rand) {
        super(ga, pl, br, rand);
    }

    @Override
    public int getBestRobberHex() {
        final int[] hexes = game.getBoard().getLandHexCoords();

        final int prevRobberHex = game.getBoard().getRobberHex();

        int bestHex = hexes[rand.nextInt(hexes.length - 1)];// Picks random hex out of all but last
        if (bestHex == prevRobberHex) // If pick is previous Hex, than replace it with last
        {
            bestHex = hexes[hexes.length - 1];
        }
        return bestHex;
    }
}
