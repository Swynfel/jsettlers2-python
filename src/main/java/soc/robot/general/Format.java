package soc.robot.general;

import soc.game.SOCBoard;
import soc.robot.general.SwynfelRobotClientInterface;

import java.util.stream.Stream;

import static soc.game.SOCBoard.*;

public class Format {
    private static class Options {
        public int[] faces, edges, vertices;
        public boolean includeStatic;

        public Options(int[] faces, int[] edges, int[] vertices, boolean includeStatic) {
            this.faces = faces;
            this.edges = edges;
            this.vertices = vertices;
            this.includeStatic = includeStatic;
        }
    }

    public int[] faces, facesCoords, edges, vertices;
    boolean includeStatic;
    int faceCount, edgeCount, vertexCount;

    int totalStates;
    int faceStates, faceStateMultiplicity;
    int edgeStates, edgeStateMultiplicity;
    int vertexStates, vertexStateMultiplicity;
    int extraStates;

    int totalActions;
    int passActions;
    int faceActions, faceActionMultiplicity;
    int edgeActions, edgeActionMultiplicity;
    int vertexActions, vertexActionMultiplicity;
    int extraActions;

    // TODO: Read from file
    public int half_height = 7;
    public int height = 2 * half_height + 1;
    public int half_width = 12;
    public int width = 2 * half_width + 1;
    public int players;

    public static final String HEADER = "state.";

    public static final String FACE = "f.";
    public static final String EDGE = "e.";
    public static final String VERTEX = "v.";
    public static final String EXTRA = "x.";

    public static final String
            OPT_FACE_THIEF = "thief",

            OPT_EXTRA_MY_VP = "my_vp",
    //TODO OPT_EXTRA_OPP_VP = "opp_vp",
            OPT_EXTRA_GBL_DVP = "gbl_dvp",
    //TODO OPT_EXTRA_MY_DVP = "my_dvp",
    //TODO OPT_EXTRA_OPP_DVP = "opp_dvp",
            OPT_EXTRA_MY_PIECES = "my_pieces",
    //TODO OPT_EXTRA_OPP_PIECES = "opp_pieces",
            OPT_EXTRA_WHO_LR = "wholr",
            OPT_EXTRA_WHO_LA = "whola";

    public boolean
            fThief = false,

            xMyVp = false,
            xGblDvp = false,
            xMyPieces = false,
            xWhoLongestRoad = false,
            xWhoLargestArmy = false;

    public Format(Options qOptions, SwynfelRobotClientInterface client) {
        includeStatic = qOptions.includeStatic;

        faces = qOptions.faces;
        facesCoords = new int[faces.length];
        for(int i = 0; i < faces.length; i++) {
            facesCoords[i] = SOCBoard.numToHexID[faces[i]];
        }
        edges = qOptions.edges;
        vertices = qOptions.vertices;

        fThief = client.getBoolProp(HEADER + FACE + OPT_FACE_THIEF, fThief);

        xMyVp = client.getBoolProp(HEADER + EXTRA + OPT_EXTRA_MY_VP, xMyVp);
        xGblDvp = client.getBoolProp(HEADER + EXTRA + OPT_EXTRA_GBL_DVP, xGblDvp);
        xMyPieces = client.getBoolProp(HEADER + EXTRA + OPT_EXTRA_MY_PIECES, xMyPieces);
        xWhoLongestRoad = client.getBoolProp(HEADER + EXTRA + OPT_EXTRA_WHO_LR, xWhoLongestRoad);
        xWhoLargestArmy = client.getBoolProp(HEADER + EXTRA + OPT_EXTRA_WHO_LA, xWhoLargestArmy);
        players = client.getIntProp("players", 2);

        faceCount = faces.length;
        edgeCount = edges.length;
        vertexCount = vertices.length;

        faceStateMultiplicity = (includeStatic ? 2 : 0)
            + (fThief ? 1 : 0);
        faceStates = faceCount * faceStateMultiplicity;
        edgeStateMultiplicity = 1;
        edgeStates = edgeCount * edgeStateMultiplicity;
        vertexStateMultiplicity = includeStatic ? 2 : 1;
        vertexStates =  vertexCount * vertexStateMultiplicity;
        extraStates = 5 // resources
            + (xMyVp ? 1 : 0)
            + (xGblDvp ? 1 : 0)
            + (xMyPieces ? 3 : 0)
            + (xWhoLongestRoad ? 1 : 0)
            + (xWhoLargestArmy ? 1 : 0);
        totalStates = faceStates + edgeStates + vertexStates + extraStates;

        faceActionMultiplicity = players;
        faceActions = faceActionMultiplicity * faceCount;
        edgeActionMultiplicity = 1;
        edgeActions = edgeActionMultiplicity * edgeCount;
        vertexActionMultiplicity = 2;
        vertexActions = vertexActionMultiplicity * vertexCount;
        extraActions = 106;
        totalActions = passActions + faceActions + edgeActions + vertexActions + extraActions;
    }

    public static class Coord {
        public int x;
        public int y;

        public Coord(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public Coord with(Format format) {
            return new Coord(x + format.half_width, y + format.half_height);
        }
    }

    public static final Coord[] HEXES = { new Coord(-4,-4), new Coord(0,-4), new Coord(4,-4), new Coord(-6,-2), new Coord(-2,-2), new Coord(2,-2), new Coord(6,-2), new Coord(-8,0), new Coord(-4,0), new Coord(0,0), new Coord(4,0), new Coord(8,0), new Coord(-6,2), new Coord(-2,2), new Coord(2,2), new Coord(6,2), new Coord(-4,4), new Coord(0,4), new Coord(4,4) };
    public static final Coord[] PATHS = { new Coord(-5,-5), new Coord(-3,-5), new Coord(-1,-5), new Coord(1,-5), new Coord(3,-5), new Coord(5,-5), new Coord(-6,-4), new Coord(-2,-4), new Coord(2,-4), new Coord(6,-4), new Coord(-7,-3), new Coord(-5,-3), new Coord(-3,-3), new Coord(-1,-3), new Coord(1,-3), new Coord(3,-3), new Coord(5,-3), new Coord(7,-3), new Coord(-8,-2), new Coord(-4,-2), new Coord(0,-2), new Coord(4,-2), new Coord(8,-2), new Coord(-9,-1), new Coord(-7,-1), new Coord(-5,-1), new Coord(-3,-1), new Coord(-1,-1), new Coord(1,-1), new Coord(3,-1), new Coord(5,-1), new Coord(7,-1), new Coord(9,-1), new Coord(-10,0), new Coord(-6,0), new Coord(-2,0), new Coord(2,0), new Coord(6,0), new Coord(10,0), new Coord(-9,1), new Coord(-7,1), new Coord(-5,1), new Coord(-3,1), new Coord(-1,1), new Coord(1,1), new Coord(3,1), new Coord(5,1), new Coord(7,1), new Coord(9,1), new Coord(-8,2), new Coord(-4,2), new Coord(0,2), new Coord(4,2), new Coord(8,2), new Coord(-7,3), new Coord(-5,3), new Coord(-3,3), new Coord(-1,3), new Coord(1,3), new Coord(3,3), new Coord(5,3), new Coord(7,3), new Coord(-6,4), new Coord(-2,4), new Coord(2,4), new Coord(6,4), new Coord(-5,5), new Coord(-3,5), new Coord(-1,5), new Coord(1,5), new Coord(3,5), new Coord(5,5) };
    public static final Coord[] INTERSECTIONS = { new Coord(-6,-5), new Coord(-4,-5), new Coord(-2,-5), new Coord(0,-5), new Coord(2,-5), new Coord(4,-5), new Coord(6,-5), new Coord(-8,-3), new Coord(-6,-3), new Coord(-4,-3), new Coord(-2,-3), new Coord(0,-3), new Coord(2,-3), new Coord(4,-3), new Coord(6,-3), new Coord(8,-3), new Coord(-10,-1), new Coord(-8,-1), new Coord(-6,-1), new Coord(-4,-1), new Coord(-2,-1), new Coord(0,-1), new Coord(2,-1), new Coord(4,-1), new Coord(6,-1), new Coord(8,-1), new Coord(10,-1), new Coord(-10,1), new Coord(-8,1), new Coord(-6,1), new Coord(-4,1), new Coord(-2,1), new Coord(0,1), new Coord(2,1), new Coord(4,1), new Coord(6,1), new Coord(8,1), new Coord(10,1), new Coord(-8,3), new Coord(-6,3), new Coord(-4,3), new Coord(-2,3), new Coord(0,3), new Coord(2,3), new Coord(4,3), new Coord(6,3), new Coord(8,3), new Coord(-6,5), new Coord(-4,5), new Coord(-2,5), new Coord(0,5), new Coord(2,5), new Coord(4,5), new Coord(6,5) };


    private static final int[] FACES_REGULAR = {16, 10, 5,  // Look at SOCBoard.numToHexID for actual ids
                                              23, 17, 11, 6,
                                            29, 24, 18, 12, 7,
                                              30, 25, 19, 13,
                                                31, 26, 20};

	private static final int[] EDGES_REGULAR = {0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
                                             0x32,       0x34,       0x36,       0x38,
                                          0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
                                       0x52,       0x54,       0x56,       0x58,       0x5a,
                                    0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x6b,
                                 0x72,       0x74,       0x76,       0x78,       0x7a,       0x7c,
                                    0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a, 0x8b, 0x8c,
                                       0x94,       0x96,       0x98,       0x9a,       0x9c,
                                          0xa5, 0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xab, 0xac,
                                             0xb6,       0xb8,       0xba,       0xbc,
                                                 0xc7, 0xc8, 0xc9, 0xca, 0xcb, 0xcc};

	private static final int[] VERTICES_REGULAR = {0x32, 0x23, 0x34, 0x25, 0x36, 0x27, 0x38,
                                             0x52, 0x43, 0x54, 0x45, 0x56, 0x47, 0x58, 0x49, 0x5a,
                                       0x72, 0x63, 0x74, 0x65, 0x76, 0x67, 0x78, 0x69, 0x7a, 0x6b, 0x7c,
                                       0x83, 0x94, 0x85, 0x96, 0x87, 0x98, 0x89, 0x9a, 0x8b, 0x9c, 0x8d,
                                             0xa5, 0xb6, 0xa7, 0xb8, 0xa9, 0xba, 0xab, 0xbc, 0xad,
                                                   0xc7, 0xd8, 0xc9, 0xda, 0xcb, 0xdc, 0xcd};

	private static final int[] VERTICES_REGULAR_OLD =  {0x23, 0x25, 0x27,
                                                     0x32, 0x34, 0x36, 0x38,
                                                     0x43, 0x45, 0x47, 0x49,
                                                  0x52, 0x54, 0x56, 0x58, 0x5a,
                                                  0x63, 0x65, 0x67, 0x69, 0x6b,
                                               0x72, 0x74, 0x76, 0x78, 0x7a, 0x7c,
                                               0x83, 0x85, 0x87, 0x89, 0x8b, 0x8d,
                                                  0x94, 0x96, 0x98, 0x9a, 0x9c,
                                                  0xa5, 0xa7, 0xa9, 0xab, 0xad,
                                                     0xb6, 0xb8, 0xba, 0xbc,
                                                     0xc7, 0xc9, 0xcb, 0xcd,
                                                        0xd8, 0xda, 0xdc};

    private static final int[] FACES_MINI = {10, 5,
                                           17, 11, 6,
                                         24, 18, 12, 7,
                                           25, 19, 13};

	public static final int[] EDGES_MINI =    {0x24, 0x25, 0x26, 0x27,
                                             0x34,       0x36,       0x38,
                                          0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
                                       0x54,       0x56,       0x58,       0x5a,
                                    0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x6b,
                                 0x74,       0x76,       0x78,       0x7a,       0x7c,
                                    0x85, 0x86, 0x87, 0x88, 0x89, 0x8a, 0x8b, 0x8c,
                                       0x96,       0x98,       0x9a,       0x9c,
                                          0xa7, 0xa8, 0xa9, 0xaa, 0xab, 0xac};

	public static final int[] VERTICES_MINI = {0x25, 0x27,
                                             0x34, 0x36, 0x38,
                                             0x45, 0x47, 0x49,
                                          0x54, 0x56, 0x58, 0x5a,
                                          0x65, 0x67, 0x69, 0x6b,
                                       0x74, 0x76, 0x78, 0x7a, 0x7c,
                                       0x85, 0x87, 0x89, 0x8b, 0x8d,
                                          0x96, 0x98, 0x9a, 0x9c,
                                          0xa7, 0xa9, 0xab, 0xad,
                                             0xb8, 0xba, 0xbc};

	public static final int[] MINI_LANDHEX = {SHEEP_HEX, WOOD_HEX, CLAY_HEX,
                                          ORE_HEX, WOOD_HEX, CLAY_HEX, SHEEP_HEX,
                                               CLAY_HEX, WHEAT_HEX, WOOD_HEX,
                                                   ORE_HEX, WHEAT_HEX};

	public static final int[] MINI_NUMPATH = {5, 6, 7,
                                           10, 11, 12, 13,
                                             17, 18, 19,
                                               24, 25};


    public static final int[][] MINI_NUMPATHS = {MINI_NUMPATH,
                                                new int[] {
                                                        13, 19, 25,
                                                        7, 12, 18, 24,
                                                        6, 11, 17,
                                                        5, 10
                                                },
                                                new int[] {
                                                        24, 17, 10,
                                                        25, 18, 11, 5,
                                                        19, 12, 6,
                                                        13, 7
                                                },new int[] {
                                                        7, 6, 5,
                                                        13, 12, 11, 10,
                                                        19, 18, 17,
                                                        25, 24
                                                },
                                                new int[] {
                                                        25, 19, 13,
                                                        24, 18, 12, 7,
                                                        17, 11, 6,
                                                        10, 5
                                                },
                                                new int[] {
                                                        10, 17, 24,
                                                        5, 11, 18, 25,
                                                        6, 12, 19,
                                                        7, 13
                                                }};

	public static final int[] MINI_NUMBERS = {9, 3, 4,
                                            4, 5, 10, 8,
                                             10, 11, 9,
                                               6, 5};

	public static final int[] MINI_PORTS_TYPES = {0, WHEAT_PORT, ORE_PORT, 0, SHEEP_PORT, 0, CLAY_PORT, WOOD_PORT};

	public static final int[] MINI_PORTS_FACING = {
            FACING_SE, FACING_SW, FACING_W, FACING_NW,
            FACING_NE, FACING_E, FACING_SE, FACING_SW
    };

    public static final int[] MINI_PORTS_HEXNUM = {
            2, 8, 20, 31,
            29, 16, 4, 1
    };

    public static final int[] MINI_PORTS_EDGES = {
            0x6B, 0x9C, 0xAA, 0xA7,
            0x74, 0x44, 0x25, 0x38
    };

    public static final Options REGULAR_FORMAT = new Options(FACES_REGULAR, EDGES_REGULAR, VERTICES_REGULAR, true);
    public static final Options FIXED_MINI_FORMAT = new Options(FACES_MINI, EDGES_MINI, VERTICES_MINI, false);
    public static final Options MINI_FORMAT = new Options(FACES_MINI, EDGES_MINI, VERTICES_MINI, true);

	public static Format getFormat(SwynfelRobotClientInterface client) {
        if(client.getBoolProp("mini", false)) { // If mini
            return new Format(MINI_FORMAT, client);
        }
	    return new Format(REGULAR_FORMAT, client);
    }
}
