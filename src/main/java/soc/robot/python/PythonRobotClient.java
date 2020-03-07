/*
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017-2018 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * The maintainer of this program can be reached at jsettlers@nand.net
 */
package soc.robot.python;

import soc.baseclient.ServerConnectInfo;
import soc.game.SOCGame;
import soc.message.SOCGameTextMsg;
import soc.message.SOCMessage;
import soc.robot.SOCRobotBrain;
import soc.robot.general.SwynfelRobotClientInterface;
import soc.robot.sample3p.Sample3PClient;
import soc.util.CappedQueue;
import soc.util.SOCRobotParameters;

import java.util.List;

public class PythonRobotClient extends SwynfelRobotClientInterface
{
    private static final String RBCLASSNAME_SAMPLE = PythonRobotClient.class.getName();

    private static String file;

    public PythonRobotClient(final ServerConnectInfo sci, final String nn, final String pw, final String f)
            throws IllegalArgumentException
    {
        super(sci, nn, pw);
        file = f;
        rbclass = RBCLASSNAME_SAMPLE;
    }

    @Override
    public SOCRobotBrain createBrain
        (final SOCRobotParameters params, final SOCGame ga, final CappedQueue<SOCMessage> mq)
    {
        return new PythonRobotBrain(this, params, ga, mq, file);
    }

    /**
     * Main method.
     * @param args  Expected arguments: server hostname, port, bot username, bot password, server cookie, python file path
     */
    public static void main(String[] args)
    {
        if (args.length < 5)
        {
            System.err.println("Java Settlers python robotclient");
            System.err.println("usage: java " + RBCLASSNAME_SAMPLE + " hostname port_number bot_nickname password cookie python_file");

            return;
        }

        PythonRobotClient cli = new PythonRobotClient
                (new ServerConnectInfo(args[0], Integer.parseInt(args[1]), args[4]), args[2], args[3], args[5]);
        cli.init();
    }

    @Override
    public void debugPrintBrainStatus(String gameName, final boolean sendTextToGame)
    {
        SOCRobotBrain brain = robotBrains.get(gameName);
        if (brain == null)
            return;

        List<String> rbSta = brain.debugPrintBrainStatus();
        if (sendTextToGame)
            for (final String st : rbSta)
                put(SOCGameTextMsg.toCmd(gameName, nickname, st));
        else
            for (final String st : rbSta)
                System.err.println(st);
    }
}
