package soc.robot.general;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.robot.MonopolyStrategy;

public class VoidMonopolyStrategy extends MonopolyStrategy {

    public VoidMonopolyStrategy(SOCGame ga, SOCPlayer pl) {
        super(ga, pl);
    }

    public boolean decidePlayMonopoly() {
        return false;
    }

}
