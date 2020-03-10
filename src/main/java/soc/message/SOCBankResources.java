package soc.message;

import soc.game.*;

import java.util.StringTokenizer;

public class SOCBankResources extends SOCMessage
        implements SOCMessageForGame
{


    private String ga;
    public SOCResourceSet rs;

    @Override
    public String toCmd() {
        return BANKRESOURCES + sep + ga + sep2
            + rs.getAmount(SOCResourceConstants.CLAY) + sep2
            + rs.getAmount(SOCResourceConstants.ORE) + sep2
            + rs.getAmount(SOCResourceConstants.SHEEP) + sep2
            + rs.getAmount(SOCResourceConstants.WHEAT) + sep2
            + rs.getAmount(SOCResourceConstants.WOOD);
    }

    @Override
    public String toString() {
        return "SOCBankResources:game=" + ga + "|resources=" + rs;
    }

    @Override
    public String getGame() {
        return ga;
    }

    public SOCBankResources(String ga, SOCResourceSet resources) {
        messageType = BANKRESOURCES;
        this.ga = ga;
        this.rs = resources;
    }

    public static SOCBankResources from(SOCGame game) {
        SOCResourceSet bankResources = new SOCResourceSet(19,19,19,19,19,0);
        for(SOCPlayer p : game.getPlayers()) {
            for(int res = SOCResourceConstants.CLAY; res < SOCResourceConstants.UNKNOWN; res++) {
                bankResources.add(-p.getResources().getAmount(res), res);
            }
        }
        return new SOCBankResources(game.getName(), bankResources);
    }

    /**
     * Parse the command String into a Discard message
     *
     * @param s   the String to parse
     * @return    a Discard message, or null if the data is garbled
     */
    public static SOCBankResources parseDataStr(String s)
    {
        String ga;
        int cl;
        int or;
        int sh;
        int wh;
        int wo;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            cl = Integer.parseInt(st.nextToken());
            or = Integer.parseInt(st.nextToken());
            sh = Integer.parseInt(st.nextToken());
            wh = Integer.parseInt(st.nextToken());
            wo = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCBankResources(ga, new SOCResourceSet(cl, or, sh, wh, wo, 0));
    }
}
