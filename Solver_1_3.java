package com.clyde.hobart.rubikssolver.utilities;

import android.support.annotation.Nullable;
import android.util.Log;

import com.clyde.hobart.rubikssolver.utilities.math.Vector;
import com.clyde.hobart.rubikssolver.utilities.math.ZMath;
import com.clyde.hobart.rubikssolver.utilities.pieces.Center;
import com.clyde.hobart.rubikssolver.utilities.pieces.Corner;
import com.clyde.hobart.rubikssolver.utilities.pieces.Edge;
import com.clyde.hobart.rubikssolver.utilities.pieces.MasterEdge;
import com.clyde.hobart.rubikssolver.utilities.pieces.Piece;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
/**
 * @author Zeke Baker, zbaker@live.com
 * @version 1.2
 * My first attempt at a generalized solver for cubes of any order size 2+
 * The solving methods for the 3x3 utilize algorithms from Rubik's that I used to first learn how to solve it years ago.
 * The solving methods for all higher order cubes (centers and edges, parity issues included) are from the image that you find
 *   50,000 times when you search "5x5 edge parity" on Google Images (http://imgur.com/wsTqj)
 */
public class Solver_1_3
{
    private static final String TAG = "Solver_1_3";
    private LinkedList<Move> moves, oTrail;  //tracks moves of slices; orientationTrail, tracks moves turning whole cube
    private Map<Vector, CubeColor> all;  //Map of all location Vectors to CubeColors
    private Map<Vector, Map<Vector, CubeColor>> faceLocCol;  //Map of face Vectors to Map of location Vectors to CubeColors
    private Set<Center> centers,  //Set of all Centers
            wFresh, yFresh, rFresh, oFresh, bFresh, gFresh;  // Used to solve the centers for higher order cubes, "Fresh" refers to whether or not they have been addressed and inserted into their correct spot.
    private Set<Corner> corners;  //Set of all Corners
    private Set<Edge> edges;  //Set of all Edges
    private Set<MasterEdge> masterEdges;  //Set of all MasterEdges (groups of edges that have the same color list and always are along the same edge (save for even-order edge parity situations)
    private Set<Piece> pieces;  //Set of all Pieces
    private Vector wFace, yFace, rFace, oFace, bFace, gFace; //orientation Vectors for the faces
    private char[][] colors;  //initial state of the cube
    private int size;  //size/order of cube

    public Solver_1_3(int s)
    {
        size = s;
    }
    public Solver_1_3(int s, char[][] c)
    {
        this(s);
        colors = c;
    }
    public Solver_1_3(int s, char[][] c, LinkedList<Move> m)
    {
        this(s, c);
        moves = m;
    }
    public String solveMenuItemPress()
    {
        faceLocCol = new HashMap<>();
        try
        {
            faceLocCol.put(Vector.X_AXIS, generateLocColMap(size, size * 2));
            faceLocCol.put(Vector.NEG_X_AXIS, generateLocColMap(size, 0));
            faceLocCol.put(Vector.Y_AXIS, generateLocColMap(size, size * 3));
            faceLocCol.put(Vector.NEG_Y_AXIS, generateLocColMap(size, size));
            faceLocCol.put(Vector.Z_AXIS, generateLocColMap(0, size));
            faceLocCol.put(Vector.NEG_Z_AXIS, generateLocColMap(2 * size, size));
            rectify();
            all = new HashMap<>();
            for(Map<Vector, CubeColor> lC : faceLocCol.values())
                for(Map.Entry<Vector, CubeColor> locCol : lC.entrySet())
                    all.put(locCol.getKey(), locCol.getValue());
            generatePieces(true);
        }
        catch(Exception e)
        {
            return "Cube net not filled properly.";
        }
        try
        {
            solve();
        }
        catch(Exception e)
        {
            return "Impossible cube configuration.";
        }
        return null;
    }
    /**
     * Generates a txt file of a solved cube of the passed dimension
     * @param size of new solved cube to generate a txt file for.
     */
    public static void generateSolved(int size)
    {
        BufferedWriter output = null;
        try
        {
            output = new BufferedWriter(new FileWriter("text/input/" + size + "x" + size + "Solved.txt"));
        }
        catch(Exception e)
        {
            System.err.println("Trouble creating file. " + e);
        }
        try
        {
            for(int r = 0; r < size; r++)
            {
                for(int c = 0; c < size; c++)
                    output.write("W ");
                output.write("\n");
            }
            for(int r = 0; r < size; r++)
            {
                for(int c = 0; c < size; c++)
                    output.write("R ");
                for(int c = 0; c < size; c++)
                    output.write("B ");
                for(int c = 0; c < size; c++)
                    output.write("O ");
                for(int c = 0; c < size; c++)
                    output.write("G ");
                output.write("\n");
            }
            for(int r = 0; r < size; r++)
            {
                for(int c = 0; c < size; c++)
                    output.write("Y ");
                output.write("\n");
            }
        }
        catch(Exception e)
        {
            System.err.println("Trouble writing file. " + e);
        }
        try
        {
            output.close();
        }
        catch(Exception e)
        {
            System.err.println("Trouble closing writer. " + e);
        }
    }
    /**
     * From the given input file name the faceLocCol and all Maps are assigned their initial values
     * @param fN File name of cube to load. Assumed to be in the "input" folder
     * @param printColorNet determines whether or not the cube net will be printed after
     */
    public void generateAllFromFile(String fN, boolean printColorNet)
    {
        String fileName = fN;
        BufferedReader reader = null;
        colors = null;
        try
        {
            reader = new BufferedReader(new FileReader("text/input/" + fileName));
        }
        catch(Exception e)
        {
            System.err.println("Trouble opening file. " + e);
        }
        try
        {
            String readLine = reader.readLine();
            size = readLine.split(" ").length;
            colors = new char[3 * size][];
            int row = 0;
            while(readLine != null)
            {
                String[] stringArray = readLine.split(" ");
                char[] charArray = new char[stringArray.length];
                for(int i = 0; i < charArray.length; i++)
                    charArray[i] = stringArray[i].charAt(0);
                colors[row] = charArray;
                row++;
                readLine = reader.readLine();
            }
            if(printColorNet)
            {
                String s = "";
                for(int r = 0; r < colors.length; r++)
                {
                    for(int c = 0; c < colors[r].length; c++)
                    {
                        s += colors[r][c];
                        if(c < colors[r].length - 1)
                            s += " ";
                    }
                    if(r < colors.length - 1)
                        s += "\n";
                }
                System.out.println(s);
            }
        }
        catch(Exception e)
        {
            System.err.println("Trouble reading file. " + e);
        }
        faceLocCol = new HashMap<Vector, Map<Vector, CubeColor>>();
        faceLocCol.put(Vector.X_AXIS, generateLocColMap(size, size));
        faceLocCol.put(Vector.NEG_X_AXIS, generateLocColMap(size, 3 * size));
        faceLocCol.put(Vector.Y_AXIS, generateLocColMap(size, 2 * size));
        faceLocCol.put(Vector.NEG_Y_AXIS, generateLocColMap(size, 0));
        faceLocCol.put(Vector.Z_AXIS, generateLocColMap(0, 0));
        faceLocCol.put(Vector.NEG_Z_AXIS, generateLocColMap(2 * size, 0));
        rectify();
        all = new HashMap<Vector, CubeColor>();
        for(Map<Vector, CubeColor> lC : faceLocCol.values())
            for(Map.Entry<Vector, CubeColor> locCol : lC.entrySet())
                all.put(locCol.getKey(), locCol.getValue());
    }
    public int getSize()
    {
        return size;
    }
    public void setSize(int s)
    {
        size = s;
    }
    public List<Move> getMoves()
    {
        return moves;
    }
    /**
     * @return moves as a string with individual moves separated by spaces
     */
    public String getMovesString()
    {
        String movesString = "";
        for(Move m : moves)
            movesString += m + " ";
        return movesString;
    }
    /**
     * Sets moves based on movesString
     * @param movesString String of moves separated by spaces
     */
    public void setMoves(String movesString)
    {
        moves = new LinkedList<>();
        String[] movesArray = movesString.split(" ");
        for(String moveString : movesArray)
            moves.add(new Move(moveString, size));
    }
    public char[][] getColors()
    {
        return colors;
    }
    /**
     * @return colors as a string (left to right, top to bottom) with individual colors separated by spaces
     */
    public String getColorsString()
    {
        String colorsString = "";
        for(int r = 0; r < 3 * size; r++)
            for(int c = 0; c < 4 * size; c++)
                colorsString += colors[r][c];
        return colorsString;
    }
    /**
     * Sets colors based on colorsString
     * @param colorsString String of colors (left to right, top to bottom) separated by spaces
     */
    public void setColors(String colorsString)
    {
        colors = new char[3 * size][4 * size];
        for(int r = 0; r < 3 * size; r++)
            for(int c = 0; c < 4 * size; c++)
                colors[r][c] = colorsString.charAt((r * 4 * size) + c);
    }
    /**
     * @param rMin the initial row value to be read
     * @param cMin the initial column value to be read
     * @return Map of location Vectors to CubeColor representing the read face as if it were above the upper face of the cube
     */
    private Map<Vector, CubeColor> generateLocColMap(int rMin, int cMin)
    {
        Map<Vector, CubeColor> vecColMap = new HashMap<>();
        for(int r = 0; r < size; r++)
            for(int c = 0; c < size; c++)
            {
                CubeColor color;
                color = CubeColor.toCubeColor(colors[r + rMin][c + cMin]);
                vecColMap.put(new Vector(c - (1.0 * size - 1) / 2, (1.0 * size - 1) / 2 - r, 1.0 * size / 2), color);
            }
        return vecColMap;
    }
    /**
     * @param v Vector location to check for Map<Vector, CubeColor> entries near
     * @return Map of location Vectors to CubeColors of all points 0.5 units away from v
     */
    private Map<Vector, CubeColor> getLocColNear(Vector v)
    {
        Map<Vector, CubeColor> locColNear = new HashMap<>();
        for(Map.Entry<Vector, CubeColor> lC: all.entrySet())
            if(ZMath.round(v.subtract(lC.getKey()).magnitude(), -4) == 0.5)
                locColNear.put(lC.getKey(), lC.getValue());
        return locColNear;
    }
    /**
     * Generates the Pieces of the cube from all, sorts them into centers, edges, and corners, and initializes face vectors, moves, and oTrail
     */
    private void generatePieces(boolean forSolve) throws Exception
    {
        pieces = new HashSet<>();
        for(int x = 0; x < size; x++)
            for(int y = 0; y < size; y++)
                for(double z = (1.0 * size - 1) / -2; z < (1.0 * size) / 2; z += size - 1)
                    pieces.add(generatePiece(new Vector(x - (1.0 * size - 1) / 2, y - (1.0 * size - 1) / 2, z)));
        for(int x = 0; x < size; x++)
            for(double y = (1.0 * size - 1) / -2; y < (1.0 * size) / 2; y += size - 1)
                for(int z = 1; z < size - 1; z++)
                    pieces.add(generatePiece(new Vector(x - (1.0 * size - 1) / 2, y, z - (1.0 * size - 1) / 2)));
        for(double x = (1.0 * size - 1) / -2; x < (1.0 * size) / 2; x += size - 1)
            for(int y = 1; y < size - 1; y++)
                for(int z = 1; z < size - 1; z++)
                    pieces.add(generatePiece(new Vector(x, y - (1.0 * size - 1) / 2, z - (1.0 * size - 1) / 2)));
        centers = new HashSet<>();
        edges = new HashSet<>();
        corners = new HashSet<>();
        for(Piece p : pieces)
        {
            if(p instanceof Center)
                centers.add((Center)p);
            if(p instanceof Edge)
                edges.add((Edge)p);
            if(p instanceof Corner)
                corners.add((Corner)p);
        }
        moves = new LinkedList<>();
        oTrail = new LinkedList<>();
        if(forSolve)
        {
            if(size % 2 == 1)
            {
                bFace = Vector.ZERO_VEC;
                gFace = Vector.ZERO_VEC;
                oFace = Vector.ZERO_VEC;
                rFace = Vector.ZERO_VEC;
                wFace = Vector.ZERO_VEC;
                yFace = Vector.ZERO_VEC;
                Center wCent = null, rCent = null;
                for(Center c : centers)
                    if(c.getVector().equals(compressVector(c.getVector())))
                    {
                        if(c.getPrimary().equals(CubeColor.WHITE))
                            wCent = c;
                        else if(c.getPrimary().equals(CubeColor.RED))
                            rCent = c;
                    }
                int turns = (int)ZMath.round(wCent.getVector().angleBetween(Vector.Z_AXIS) * 2 / Math.PI, 0);
                if(turns == 2)
                    addToOTrail(Vector.X_AXIS, Math.PI);
                else if(turns == 1)
                    addToOTrail(wCent.getVector().cross(Vector.Z_AXIS), Math.PI / 2);
                turns = (int)ZMath.round(rCent.getVector().angleBetween(Vector.NEG_Y_AXIS) * 2 / Math.PI, 0);
                if(turns == 2)
                    addToOTrail(Vector.Z_AXIS, Math.PI);
                else if(turns == 1)
                    addToOTrail(Vector.NEG_Z_AXIS, Math.signum(rCent.getVector().getX()) * Math.PI / 2);
            }
            bFace = Vector.X_AXIS;
            gFace = Vector.NEG_X_AXIS;
            oFace = Vector.Y_AXIS;
            rFace = Vector.NEG_Y_AXIS;
            wFace = Vector.Z_AXIS;
            yFace = Vector.NEG_Z_AXIS;
            masterEdges = new HashSet<>();
            if(size % 2 == 1)
            {
                for(Edge e : edges)
                    if(e.getVector().equals(compressVector(e.getVector())))
                    {
                        List<Vector> orientations = new ArrayList<>();
                        orientations.add(e.getPrimaryOrientation());
                        orientations.add(e.getSecondaryOrientation());
                        masterEdges.add(new MasterEdge(e.getVector(), e.getColors(), orientations));
                    }
            }
            else
            {
                for(int p1 = 1; p1 < 3; p1++)
                    for(int p2 = 3; p2 < 7; p2++)
                    {
                        CubeColor primary = CubeColor.fromPriority(p1), secondary = CubeColor.fromPriority(p2);
                        List<CubeColor> colors = new ArrayList<>();
                        colors.add(primary);
                        colors.add(secondary);
                        List<Vector> orientations = new ArrayList<>();
                        orientations.add(getFace(primary));
                        orientations.add(getFace(secondary));
                        masterEdges.add(new MasterEdge(orientations.get(0).add(orientations.get(1)).scalar((size - 1.0) / 2), colors, orientations));
                    }
                for(int p1 = 3; p1 < 5; p1++)
                    for(int p2 = 5; p2 < 7; p2++)
                    {
                        CubeColor primary = CubeColor.fromPriority(p1), secondary = CubeColor.fromPriority(p2);
                        List<CubeColor> colors = new ArrayList<>();
                        colors.add(primary);
                        colors.add(secondary);
                        List<Vector> orientations = new ArrayList<>();
                        orientations.add(getFace(primary));
                        orientations.add(getFace(secondary));
                        masterEdges.add(new MasterEdge(orientations.get(0).add(orientations.get(1)).scalar((size - 1.0) / 2), colors, orientations));
                    }
            }
            for(MasterEdge mE : masterEdges)
                pieces.add(mE);
        }
    }
    /**
     * @param v Vector location of future Piece
     * @return Piece located at v
     */
    private Piece generatePiece(Vector v)
    {
        Map<Vector, CubeColor> locColNear = getLocColNear(v);
        List<CubeColor> colorList = new ArrayList<>();
        for(CubeColor col : locColNear.values())
            colorList.add(col);
        Collections.sort(colorList);
        List<Vector> oList = new ArrayList<>();
        if(colorList.size() == 1)
        {
            oList.add(compressVector(v).getUnit());
            return new Center(v, colorList, oList);
        }
        else if(colorList.size() == 2)
        {
            for(Vector loc : locColNear.keySet())
                if(colorList.get(0).equals(locColNear.get(loc)))
                {
                    oList.add(loc.subtract(v).scalar(2));
                    oList.add(oList.get(0).rotate(compressVector(v), Math.PI));
                }
            return new Edge(v, colorList, oList);
        }
        else
        {
            Set<Map.Entry<Vector, CubeColor>> locColNearSet = locColNear.entrySet();
            for(Map.Entry<Vector, CubeColor> locCol : locColNearSet)
                if(locCol.getValue().equals(CubeColor.WHITE) || locCol.getValue().equals(CubeColor.YELLOW))
                {
                    oList.add(compressVector(locCol.getKey()).getUnit());
                    oList.add(oList.get(0).rotate(v, 2 * Math.PI / 3));
                    oList.add(oList.get(0).rotate(v, 4 * Math.PI / 3));
                }
            for(Map.Entry<Vector, CubeColor> locCol : locColNearSet)
            {
                if(locCol.getKey().equals(v.add(oList.get(1).scalar(0.5))))
                    colorList.set(1, locCol.getValue());
                else if(locCol.getKey().equals(v.add(oList.get(2).scalar(0.5))))
                    colorList.set(2, locCol.getValue());
            }
            return new Corner(v, colorList, oList);
        }
    }
    /**
     * Adds new move to moves after first rotating all the corresponding pieces and all those located on the same axis further out from the center and then
     * running running the axis of rotation through all elements of oTrail, rotating backwards, so that the final axis is the same as the initial cube orientation
     * @param slice Vector location of slice to rotate
     * @param theta double angle to rotate slice
     */
    private void addToMoves(Vector slice, double theta)
    {
        aTMHelp(slice, theta);
        for(Move oMove : oTrail)
            slice = slice.rotate(oMove.getSlice(), oMove.getTheta() * -1);
        moves.add(new Move(slice, theta));
    }
    /**
     * Helper method for addToMoves(), rotates all pieces on the given slice and then recursively calls itself on the next slice out from the center
     * @param slice Vector slice of the cube to rotate
     * @param theta  double representing the angle to rotate
     */
    private void aTMHelp(Vector slice, double theta)
    {
        for(Piece p : pieces)
            if(p.inSlice(slice))
                p.rotate(slice, theta);
        if(slice.magnitude() < size / 2.0 - 1)
            aTMHelp(slice.getUnit().scalar(ZMath.round(slice.magnitude() + 1, -3)), theta);
    }
    /**
     * Adds a move to a fresh oTrail such that only one move needs to be done in reverse when addToOTrail is called in addToMoves
     * @param v Vector to rotate cube by
     * @param theta double angle to rotate the cube around v by
     */
    private void addToOTrail(Vector v, double theta)
    {
        for(Piece p : pieces)
        {
            p.rotate(v, theta);
        }
        bFace = bFace.rotate(v, theta);
        gFace = gFace.rotate(v, theta);
        oFace = oFace.rotate(v, theta);
        rFace = rFace.rotate(v, theta);
        wFace = wFace.rotate(v, theta);
        yFace = yFace.rotate(v, theta);

        if(wFace.posScalarMult(Vector.Z_AXIS))
        {
            if(rFace.posScalarMult(Vector.NEG_Y_AXIS))
            {
                oTrail = new LinkedList<>();
            }
            else if(rFace.posScalarMult(Vector.X_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(Vector.Z_AXIS, Math.PI / 2));
            }
            else if(rFace.posScalarMult(Vector.Y_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(Vector.Z_AXIS, Math.PI));
            }
            else if(rFace.posScalarMult(Vector.NEG_X_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(Vector.Z_AXIS, Math.PI / -2));
            }
        }
        else if(wFace.posScalarMult(Vector.NEG_Z_AXIS))
        {
            if(rFace.posScalarMult(Vector.X_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(new Vector(1, -1, 0), Math.PI));
            }
            else if(rFace.posScalarMult(Vector.Y_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(Vector.X_AXIS, Math.PI));
            }
            else if(rFace.posScalarMult(Vector.NEG_X_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(new Vector(1, 1, 0), Math.PI));
            }
            else if(rFace.posScalarMult(Vector.NEG_Y_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(Vector.Y_AXIS, Math.PI));
            }
        }
        else if(wFace.posScalarMult(Vector.Y_AXIS))
        {
            if(rFace.posScalarMult(Vector.Z_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(Vector.X_AXIS, Math.PI / -2));
            }
            else if(rFace.posScalarMult(Vector.X_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(new Vector(-1, 1, 1), 2 * Math.PI / 3));
            }
            else if(rFace.posScalarMult(Vector.NEG_Z_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(new Vector(0, 1, 1), Math.PI));
            }
            else if(rFace.posScalarMult(Vector.NEG_X_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(new Vector(1, 1, 1), -2 * Math.PI / 3));
            }
        }
        else if(wFace.posScalarMult(Vector.NEG_Y_AXIS))
        {
            if(rFace.posScalarMult(Vector.X_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(new Vector(1, -1, 1), 2 * Math.PI / 3));
            }
            else if(rFace.posScalarMult(Vector.Z_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(new Vector(0, -1, 1), Math.PI));
            }
            else if(rFace.posScalarMult(Vector.NEG_X_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(new Vector(1, 1, -1), 2 * Math.PI / 3));
            }
            else if(rFace.posScalarMult(Vector.NEG_Z_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(Vector.X_AXIS, Math.PI / 2));
            }
        }
        else if(wFace.posScalarMult(Vector.X_AXIS))
        {
            if(rFace.posScalarMult(Vector.Y_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(new Vector(1, 0, 1), Math.PI));
            }
            else if(rFace.posScalarMult(Vector.Z_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(new Vector(1, -1, 1), -2 * Math.PI / 3));
            }
            else if(rFace.posScalarMult(Vector.NEG_Y_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(Vector.Y_AXIS, Math.PI / 2));
            }
            else if(rFace.posScalarMult(Vector.NEG_Z_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(new Vector(1, 1, 1), 2 * Math.PI / 3));
            }
        }
        else if(wFace.posScalarMult(Vector.NEG_X_AXIS))
        {
            if(rFace.posScalarMult(Vector.Z_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(new Vector(1, 1, -1), -2 * Math.PI / 3));
            }
            else if(rFace.posScalarMult(Vector.Y_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(new Vector(1, 0, -1), Math.PI));
            }
            else if(rFace.posScalarMult(Vector.NEG_Z_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(new Vector(-1, 1, 1), -2 * Math.PI / 3));
            }
            else if(rFace.posScalarMult(Vector.NEG_Y_AXIS))
            {
                oTrail = new LinkedList<>();
                oTrail.add(new Move(Vector.Y_AXIS, Math.PI / -2));
            }
        }
    }
    /**
     * public method created for testing. Has no real purpose other than offering access to private variables and method during class construction
     */
    public void testAccess()
    {
        System.out.println(allToString(false, true, false));
        System.out.println(moves.size() + ", " + oTrail.size());
    }
    /**
     * @param listMoves determines whether or not the moves will be included in the string
     * @param net determines whether or not the net will be included in the string
     * @param pieceData determines whether or not the piece data will be included in the string
     * @return String made to the specifications of net and pieceData
     */
    private String allToString(boolean listMoves, boolean net, boolean pieceData)
    {
        String s = "";
        if(listMoves)
        {
            List<Move> arrayMoves = new ArrayList<>();
            arrayMoves.addAll(moves);
            for(int i = 0; i < arrayMoves.size(); i++)
            {
                if((i / 10) % 2 == 0)
                    s += String.format("%-6s", arrayMoves.get(i));
                else
                {
                    s += arrayMoves.get(i);
                    for(int j = 0; j < 6 - arrayMoves.get(i).toString().length(); j++)
                        s += "/";
                }
                if(i % 10 == 9 || i == arrayMoves.size() - 1)
                    s += "\n";
            }
        }
        if(net)
        {
            updateColors();
            for(int r = 0; r < 3 * size; r++)
            {
                for(int c = 0; c < 4 * size; c++)
                    s += colors[r][c] + " ";
                s += "\n";
            }
        }
        if(pieceData)
        {
            s += "Centers:";
            for(Center c : centers)
                s += "\n\t" + c;
            s += "\nEdges:";
            for(Edge e : edges)
                s += "\n\t" + e;
            s += "\nCorners:";
            for(Corner c: corners)
                s += "\n\t" + c;
        }
        return s;
    }
    /**
     * 	updates colors to the current status of the cube
     */
    private void updateColors()
    {
        updateFLC();
        unrectify();
        colors = new char[3 * size][4 * size];
        for(Map.Entry<Vector, CubeColor> locCol : faceLocCol.get(Vector.Z_AXIS).entrySet())
            colors[(int)ZMath.round((size - 1.0) / 2 - locCol.getKey().getY(), 0)][(int)ZMath.round(locCol.getKey().getX() + (size - 1.0) / 2, 0) + size] = locCol.getValue().toString().charAt(0);
        for(Map.Entry<Vector, CubeColor> locCol : faceLocCol.get(Vector.NEG_X_AXIS).entrySet())
            colors[(int)ZMath.round((size - 1.0) / 2 - locCol.getKey().getY(), 0) + size][(int)ZMath.round(locCol.getKey().getX() + (size - 1.0) / 2, 0)] = locCol.getValue().toString().charAt(0);
        for(Map.Entry<Vector, CubeColor> locCol : faceLocCol.get(Vector.NEG_Y_AXIS).entrySet())
            colors[(int)ZMath.round((size - 1.0) / 2 - locCol.getKey().getY(), 0) + size][(int)ZMath.round(locCol.getKey().getX() + (size - 1.0) / 2, 0) + size] = locCol.getValue().toString().charAt(0);
        for(Map.Entry<Vector, CubeColor> locCol : faceLocCol.get(Vector.X_AXIS).entrySet())
            colors[(int)ZMath.round((size - 1.0) / 2 - locCol.getKey().getY(), 0) + size][(int)ZMath.round(locCol.getKey().getX() + (size - 1.0) / 2, 0) + 2 * size] = locCol.getValue().toString().charAt(0);
        for(Map.Entry<Vector, CubeColor> locCol : faceLocCol.get(Vector.Y_AXIS).entrySet())
            colors[(int)ZMath.round((size - 1.0) / 2 - locCol.getKey().getY(), 0) + size][(int)ZMath.round(locCol.getKey().getX() + (size - 1.0) / 2, 0) + 3 * size] = locCol.getValue().toString().charAt(0);
        for(Map.Entry<Vector, CubeColor> locCol : faceLocCol.get(Vector.NEG_Z_AXIS).entrySet())
            colors[(int)ZMath.round((size - 1.0) / 2 - locCol.getKey().getY(), 0) + 2 * size][(int)ZMath.round(locCol.getKey().getX() + (size - 1.0) / 2, 0) + size] = locCol.getValue().toString().charAt(0);
    }
    /**
     * updates faceLocCol to the current status of the cube
     */
    private void updateFLC()
    {
        Map<Vector, CubeColor> fSCP = new HashMap<>(), bSCP = new HashMap<>(), lSCP = new HashMap<>(),
                rSCP = new HashMap<>(), uSCP = new HashMap<>(), dSCP = new HashMap<>();
        for(Piece p : pieces)
        {
            if(!(p instanceof MasterEdge))
            {
                int order = p.getOrder();
                List<Vector> orientations = p.getOrientations();
                List<CubeColor> colors = p.getColors();
                for(int i = 0; i < order; i++)
                {
                    if(orientations.get(i).equals(Vector.X_AXIS))
                        rSCP.put(p.getVector().add(orientations.get(i).scalar(0.5)), colors.get(i));
                    if(orientations.get(i).equals(Vector.NEG_X_AXIS))
                        lSCP.put(p.getVector().add(orientations.get(i).scalar(0.5)), colors.get(i));
                    if(orientations.get(i).equals(Vector.Y_AXIS))
                        bSCP.put(p.getVector().add(orientations.get(i).scalar(0.5)), colors.get(i));
                    if(orientations.get(i).equals(Vector.NEG_Y_AXIS))
                        fSCP.put(p.getVector().add(orientations.get(i).scalar(0.5)), colors.get(i));
                    if(orientations.get(i).equals(Vector.Z_AXIS))
                        uSCP.put(p.getVector().add(orientations.get(i).scalar(0.5)), colors.get(i));
                    if(orientations.get(i).equals(Vector.NEG_Z_AXIS))
                        dSCP.put(p.getVector().add(orientations.get(i).scalar(0.5)), colors.get(i));
                }
            }
        }
        faceLocCol.clear();
        faceLocCol.put(Vector.X_AXIS, rSCP);
        faceLocCol.put(Vector.NEG_X_AXIS, lSCP);
        faceLocCol.put(Vector.Y_AXIS, bSCP);
        faceLocCol.put(Vector.NEG_Y_AXIS, fSCP);
        faceLocCol.put(Vector.Z_AXIS, uSCP);
        faceLocCol.put(Vector.NEG_Z_AXIS, dSCP);
    }
    /**
     * @param v Vector to compress
     * @return compressed version of v (all values that aren't the largest component get set to zero,
     * ensuring that the compressed vector is a sum of the three axis vectors or their opposites
     */
    public static Vector compressVector(Vector v)
    {
        Vector vCopy = new Vector(v.getAll());
        double largestComp = 0;
        for(double comp : vCopy.getAll())
            if(ZMath.round(Math.abs(comp) - largestComp, -3) > 0)
                largestComp = Math.abs(comp);
        if(ZMath.round(largestComp - Math.abs(vCopy.getX()), -3) > 0)
            vCopy.setX(0);
        if(ZMath.round(largestComp - Math.abs(vCopy.getY()), -3) > 0)
            vCopy.setY(0);
        if(ZMath.round(largestComp - Math.abs(vCopy.getZ()), -3) > 0)
            vCopy.setZ(0);
        return vCopy;
    }
    /**
     * Shifts the values of faceLocCol from the positive z orientation to their correct orientation
     */
    private void rectify()
    {
        for(Map.Entry<Vector, Map<Vector, CubeColor>> fLC : faceLocCol.entrySet())
        {
            if(fLC.getKey().equals(Vector.X_AXIS))
                for(Vector loc: fLC.getValue().keySet())
                {
                    loc.setTo(loc.rotate(Vector.X_AXIS, Math.PI / 2));
                    loc.setTo(loc.rotate(Vector.Z_AXIS, Math.PI / 2));
                }
            else if(fLC.getKey().equals(Vector.NEG_X_AXIS))
                for(Vector loc: fLC.getValue().keySet())
                {
                    loc.setTo(loc.rotate(Vector.X_AXIS, Math.PI / 2));
                    loc.setTo(loc.rotate(Vector.Z_AXIS, 3 * Math.PI / 2));
                }
            else if(fLC.getKey().equals(Vector.Y_AXIS))
                for(Vector loc: fLC.getValue().keySet())
                {
                    loc.setTo(loc.rotate(Vector.X_AXIS, Math.PI / 2));
                    loc.setTo(loc.rotate(Vector.Z_AXIS, Math.PI));
                }
            else if(fLC.getKey().equals(Vector.NEG_Y_AXIS))
                for(Vector loc: fLC.getValue().keySet())
                {
                    loc.setTo(loc.rotate(Vector.X_AXIS, Math.PI / 2));
                }
            else if(fLC.getKey().equals(Vector.NEG_Z_AXIS))
                for(Vector loc: fLC.getValue().keySet())
                {
                    loc.setTo(loc.rotate(Vector.X_AXIS, Math.PI));
                }
        }
    }
    /**
     * Shifts the values of faceLocCol from their correct orientation to the positive z orientation for printing
     */
    private void unrectify()
    {
        for(Map.Entry<Vector, Map<Vector, CubeColor>> fLC : faceLocCol.entrySet())
        {
            if(fLC.getKey().equals(Vector.X_AXIS))
                for(Vector loc: fLC.getValue().keySet())
                {
                    loc.setTo(loc.rotate(Vector.Z_AXIS, Math.PI / -2));
                    loc.setTo(loc.rotate(Vector.X_AXIS, Math.PI / -2));
                }
            else if(fLC.getKey().equals(Vector.NEG_X_AXIS))
                for(Vector loc: fLC.getValue().keySet())
                {
                    loc.setTo(loc.rotate(Vector.Z_AXIS, -3 * Math.PI / 2));
                    loc.setTo(loc.rotate(Vector.X_AXIS, Math.PI / -2));
                }
            else if(fLC.getKey().equals(Vector.Y_AXIS))
                for(Vector loc: fLC.getValue().keySet())
                {
                    loc.setTo(loc.rotate(Vector.Z_AXIS, Math.PI));
                    loc.setTo(loc.rotate(Vector.X_AXIS, Math.PI / -2));
                }
            else if(fLC.getKey().equals(Vector.NEG_Y_AXIS))
                for(Vector loc: fLC.getValue().keySet())
                {
                    loc.setTo(loc.rotate(Vector.X_AXIS, Math.PI / -2));
                }
            else if(fLC.getKey().equals(Vector.NEG_Z_AXIS))
                for(Vector loc: fLC.getValue().keySet())
                {
                    loc.setTo(loc.rotate(Vector.X_AXIS, Math.PI));
                }
        }
    }
    /**
     * @param col CubeColor to retrieve the face of
     * @return the corresponding face vector of col
     */
    private Vector getFace(CubeColor col)
    {
        if(col.equals(CubeColor.BLUE))
            return bFace;
        else if(col.equals(CubeColor.GREEN))
            return gFace;
        else if(col.equals(CubeColor.ORANGE))
            return oFace;
        else if(col.equals(CubeColor.RED))
            return rFace;
        else if(col.equals(CubeColor.WHITE))
            return wFace;
        else
            return yFace;
    }
    /**
     * Generates n moves for the sake of testing/scrambling a cube
     * @param n number of moves to generate
     */
    public void generateMoves(int n)
    {
        int previousFace = 0;
        double previousSlice = 0;
        for(int i = 0; i < n; i++)
        {
            int axis = (int)(Math.random() * 3), dir = (int)(Math.random() * 2) * 2 - 1, angle = (int)(Math.random() * 3) + 1;
            double slice = (int)(Math.random() * (size / 2)) + (size % 2.0) / 2 + 0.5;
            if(angle == 3)
                angle = -1;
            while(axis * dir == previousFace && slice == previousSlice)
            {
                axis = (int)(Math.random() * 3);
                dir = (int)(Math.random() * 2) * 2 - 1;
                slice = (int)(Math.random() * (size / 2)) + (size % 2.0) / 2 + 0.5;
            }
//			addToMoves(Vector.X_AXIS.rotate(new Vector(1, 1, 1), axis * 2 * Math.PI / 3).scalar(dir * (size - 1.0) / 2), angle * Math.PI / 2);
            addToMoves(Vector.X_AXIS.rotate(new Vector(1, 1, 1), axis * 2 * Math.PI / 3).scalar(dir * slice), angle * Math.PI / 2);
            previousFace = axis * dir;
            previousSlice = slice;
        }
    }
    /**
     * @param mList list of moves to execute on the cube from state c
     * @param c state of the cube before executing LinkedList<Move> mList
     * @return state of the cube in char[][] after executing mList from c
     */
    public char[][] runMoves(LinkedList<Move> mList, char[][] c)
    {
        char[][] colorsOriginal = null;
        boolean colorsNull = (colors == null);
        if(!colorsNull)
            colorsOriginal = colors;
        moves = new LinkedList<>();
        oTrail = new LinkedList<>();
        colors = c;
        size = colors.length / 3;
        faceLocCol = new HashMap<>();
        faceLocCol.put(Vector.X_AXIS, generateLocColMap(size, size * 2));
        faceLocCol.put(Vector.NEG_X_AXIS, generateLocColMap(size, 0));
        faceLocCol.put(Vector.Y_AXIS, generateLocColMap(size, size * 3));
        faceLocCol.put(Vector.NEG_Y_AXIS, generateLocColMap(size, size));
        faceLocCol.put(Vector.Z_AXIS, generateLocColMap(0, size));
        faceLocCol.put(Vector.NEG_Z_AXIS, generateLocColMap(2 * size, size));rectify();
        all = new HashMap<>();
        for(Map<Vector, CubeColor> lC : faceLocCol.values())
            for(Map.Entry<Vector, CubeColor> locCol : lC.entrySet())
                all.put(locCol.getKey(), locCol.getValue());
        try{
            generatePieces(false);
        }
        catch (Exception e)
        {
            Log.e(TAG, "Couldn't generate pieces within runMoves()");
        }
        for(Move m : mList)
            addToMoves(m.getSlice(), m.getTheta());
        updateColors();
        if(colorsNull)
            return colors;
        else
        {
            c = colors;
            colors = colorsOriginal;
            return c;
        }
    }
    //Beginning of solve
    /**
     * Solves the cube and returns a String of the moves to solve it.
     * @return String of the moves to solve the cube
     */
    private String solve()
    {
        if(size > 3)
        {
            solveCenters();
            solveEdges();
            int turns = (int)ZMath.round(wFace.angleBetween(Vector.Z_AXIS) * 2 / Math.PI, 0);
            if(turns == 2)
                addToOTrail(Vector.X_AXIS, Math.PI);
            else if(turns == 1)
                addToOTrail(wFace.cross(Vector.Z_AXIS), Math.PI / 2);
            turns = (int)ZMath.round(rFace.angleBetween(Vector.NEG_Y_AXIS) * 2 / Math.PI, 0);
            if(turns == 2)
                addToOTrail(Vector.Z_AXIS, Math.PI);
            else if(turns == 1)
                addToOTrail(Vector.Z_AXIS, ZMath.round(rFace.cross(Vector.Z_AXIS).getZ(), 0) * Math.PI / 2);
        }
        if(size > 2)
            solve3x3();
        else if(size == 2)
            solve2x2();
        return allToString(true, false, false);
    }
    //Beginning of centers solve
    /**
     * Solves the cube as a 3x3. This assumes that the centers are solved already. Even-ordered edge parity issues may still persist at this point.
     */
    private void solveCenters()
    {
        wFresh = new HashSet<>();
        yFresh = new HashSet<>();
        rFresh = new HashSet<>();
        oFresh = new HashSet<>();
        bFresh = new HashSet<>();
        gFresh = new HashSet<>();
        for(Center c : centers)
        {
            if(c.getPrimary().equals(CubeColor.WHITE))
                wFresh.add(c);
            else if(c.getPrimary().equals(CubeColor.YELLOW))
                yFresh.add(c);
            else if(c.getPrimary().equals(CubeColor.RED))
                rFresh.add(c);
            else if(c.getPrimary().equals(CubeColor.ORANGE))
                oFresh.add(c);
            else if(c.getPrimary().equals(CubeColor.BLUE))
                bFresh.add(c);
            else
                gFresh.add(c);
        }
        addToOTrail(Vector.Y_AXIS, Math.PI / 2);
        solvePrimaryCenter(CubeColor.WHITE);
        addToOTrail(Vector.Y_AXIS, Math.PI);
        solvePrimaryCenter(CubeColor.YELLOW);
        solveRedOrBlueCenter(CubeColor.RED);
        addToOTrail(Vector.X_AXIS, Math.PI / 2);
        solveRedOrBlueCenter(CubeColor.BLUE);
        addToOTrail(Vector.X_AXIS, Math.PI / 2);
        solveOrangeAndGreenCenters();
    }
    /**
     * @param flatLoc The location of the desired center viewing the face as if it were in the xy plane facing up
     * @param col The CubeColor of the desired center
     * @return A Center that is the correct color and can exist at flatLoc on the upper face
     */
    @Nullable
    private Center getCenter(Vector flatLoc, CubeColor col)
    {
        Set<Center> colFresh = null;
        if(col.equals(CubeColor.WHITE))
            colFresh = wFresh;
        else if(col.equals(CubeColor.YELLOW))
            colFresh = yFresh;
        else if(col.equals(CubeColor.RED))
            colFresh = rFresh;
        else if(col.equals(CubeColor.ORANGE))
            colFresh = oFresh;
        else if(col.equals(CubeColor.BLUE))
            colFresh = bFresh;
        else if(col.equals(CubeColor.GREEN))
            colFresh = gFresh;
        for(Center c : colFresh)
        {
            Vector cFlatLoc = c.getVector();
            if(c.getPrimaryOrientation().equals(Vector.NEG_Z_AXIS))
                cFlatLoc = cFlatLoc.rotate(Vector.X_AXIS, Math.PI);
            else if(!c.getPrimaryOrientation().equals(Vector.Z_AXIS))
                cFlatLoc = cFlatLoc.rotate(compressVector(cFlatLoc).cross(Vector.Z_AXIS), Math.PI / 2);
            cFlatLoc = cFlatLoc.subtract(compressVector(cFlatLoc));
            for(int i = 0; i < 4; i++)
            {
                if(flatLoc.equals(cFlatLoc))
                    return c;
                cFlatLoc = cFlatLoc.rotate(Vector.Z_AXIS, Math.PI / 2);
            }
        }
        return null;
    }
    /**
     * Solves the col center (unit). It is assumed that colFace points towards the right at this point.
     * @param col CubeColor to solve the center of
     */
    private void solvePrimaryCenter(CubeColor col)
    {
        Set<Center> colFresh = null;
        if(col.equals(CubeColor.WHITE))
            colFresh = wFresh;
        else if(col.equals(CubeColor.YELLOW))
            colFresh = yFresh;
        if(size % 2 == 1)  //establishing the middle slice
        {
            for(int x = 1; x < size / 2; x++)
                for(int xSign = -1; xSign < 2; xSign += 2)
                {
                    Vector flatLoc = new Vector(x * xSign, 0, 0);
                    Center cent = getCenter(flatLoc, col);
                    sPCHelp(cent, flatLoc);
                    colFresh.remove(cent);
                }
            addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
            addToMoves(Vector.Y_AXIS, Math.PI / 2);
            addToMoves(Vector.NEG_Y_AXIS, Math.PI / -2);
            addToMoves(Vector.X_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
            addToMoves(Vector.Y_AXIS, Math.PI / -2);
            addToMoves(Vector.NEG_Y_AXIS, Math.PI / 2);
        }
        for(double y = ((size % 2) + 1.0) / 2; y < (size - 1.0) / 2; y++)
            for(int ySign = -1; ySign < 2; ySign += 2)
            {
                if(size % 2 == 1)  //establishes keystone center for row
                {
                    Vector flatLoc = new Vector(y * ySign, 0, 0);
                    Center cent = getCenter(flatLoc, col);
                    sPCHelp(cent, flatLoc);
                    colFresh.remove(cent);
                    addToMoves(cent.getVector().getZCompVec(), Math.PI / 2);
                }
                for(double x = ((size % 2) + 1.0) / 2; x < (size - 1.0) / 2; x++)
                    for(int xSign = -1; xSign < 2; xSign += 2)
                    {
                        Vector flatLoc = new Vector(x * xSign, y * ySign, 0);
                        Center cent = getCenter(flatLoc, col);
                        sPCHelp(cent, flatLoc);
                        colFresh.remove(cent);

                    }
                addToMoves(new Vector(0, y * ySign, 0), ySign * Math.PI / 2);  //insert row
                addToMoves(Vector.X_AXIS.scalar((size - 1.0) / 2), Math.PI);
                addToMoves(new Vector(0, y * ySign, 0), ySign * Math.PI / -2);
            }
    }
    /**
     * Moves cent to flatLoc on upper face
     * @param cent Center to put into flatLoc on upper face
     * @param flatLoc desired Vector location (in xy plane) of cent on upper face
     */
    private void sPCHelp(Center cent, Vector flatLoc)
    {
        if(!flatLoc.add(Vector.Z_AXIS.scalar((size - 1.0) / 2)).equals(cent.getVector()))
        {
            if(cent.getPrimaryOrientation().equals(Vector.NEG_X_AXIS))  //No need to correct zSlice stuff if cent is YELLOW here because if cent is YELLOW implies that white is complete and to left
            {
                if(cent.getVector().getZ() == 0)
                    addToMoves(Vector.NEG_X_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                if(cent.getVector().getY() == 0)
                {
                    Vector zSlice = cent.getVector().getZCompVec();
                    addToMoves(zSlice, Math.PI / 2);
                    addToMoves(cent.getVector().getYCompVec(), Math.PI / 2);
                    addToMoves(zSlice, Math.PI / -2);
                }
                else
                {
                    Vector zSlice = cent.getVector().getZCompVec();
                    addToMoves(zSlice, Math.PI / 2);
                    Vector loc = cent.getVector();
                    addToMoves(loc.getYCompVec(), Math.signum(loc.getX() * loc.getY() * loc.getZ()) * Math.PI / 2);
                    addToMoves(zSlice, Math.PI / -2);
                }
            }
            else if(cent.getPrimaryOrientation().equals(Vector.X_AXIS))
            {
                if(cent.getVector().getY() == 0)
                {
                    Vector zSlice = cent.getVector().getZCompVec(), zSliceNext = zSlice.getUnit().scalar(zSlice.magnitude() + 1);
                    addToMoves(zSlice, Math.PI / 2);
                    addToMoves(zSliceNext, Math.PI / -2);
                    if(cent.getPrimary().equals(CubeColor.YELLOW))
                    {
                        addToMoves(cent.getVector().getYCompVec(), Math.PI / 2);
                        addToMoves(zSlice, Math.PI / -2);
                        addToMoves(zSliceNext, Math.PI / 2);
                    }
                }
                else
                {
                    Vector xFace = cent.getVector().getXCompVec();
                    addToMoves(xFace, Math.PI / 2);
                    Vector zSlice = cent.getVector().getZCompVec(), zSliceNext = zSlice.getUnit().scalar(zSlice.magnitude() + 1);
                    addToMoves(zSlice, Math.PI / 2);
                    addToMoves(zSliceNext, Math.PI / -2);
                    if(cent.getPrimary().equals(CubeColor.YELLOW))
                    {
                        Vector centLoc = cent.getVector();
                        if(Math.abs(centLoc.getX()) < 0.001)
                            addToMoves(centLoc.getYCompVec(), Math.PI / 2);
                        else
                            addToMoves(centLoc.getYCompVec(), Math.signum(centLoc.getX() * centLoc.getY() * centLoc.getZ()) * Math.PI / 2);
                        addToMoves(zSlice, Math.PI / -2);
                        addToMoves(zSliceNext, Math.PI / 2);
                    }
                    addToMoves(xFace, Math.PI / -2);
                }
            }
            else if(cent.getPrimaryOrientation().equals(Vector.Z_AXIS))
            {
                if(Math.abs(flatLoc.getY() - cent.getVector().getY()) < 0.001)
                {
                    Vector xSlice = cent.getVector().getXCompVec(), xSliceNext = xSlice.getUnit().scalar(xSlice.magnitude() + 1);
                    addToMoves(xSlice, Math.PI / 2);
                    addToMoves(xSliceNext, Math.PI / -2);
                    int zSign = (int)Math.signum(cent.getVector().getZ());
                    if(zSign == 0)
                        addToMoves(compressVector(cent.getVector()), Math.PI);
                    else
                        addToMoves(compressVector(cent.getVector()), zSign * Math.PI / 2);
                    addToMoves(xSlice, Math.PI / -2);
                    addToMoves(xSliceNext, Math.PI / 2);
                }
                else
                {
                    boolean yNot0 = cent.getVector().getY() != 0;
                    if(yNot0)
                        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                    Vector xSlice = cent.getVector().getXCompVec(), xSliceNext = xSlice.getUnit().scalar(xSlice.magnitude() + 1);
                    addToMoves(xSlice, Math.PI / 2);
                    addToMoves(xSliceNext, Math.PI / -2);
                    if(yNot0)
                        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                }
            }
            Vector currentFlatLoc = cent.getVector();
            if(compressVector(currentFlatLoc).posScalarMult(Vector.NEG_Z_AXIS))
                currentFlatLoc = currentFlatLoc.rotate(Vector.X_AXIS, Math.PI);
            else
                currentFlatLoc = currentFlatLoc.rotate(Vector.X_AXIS, ZMath.round(currentFlatLoc.getYCompVec().cross(Vector.Z_AXIS).getUnit().getX(), 0) * Math.PI / 2);
            currentFlatLoc = currentFlatLoc.subtract(compressVector(currentFlatLoc));
            int turnsFromFL = (int)ZMath.round(currentFlatLoc.angleBetween(flatLoc) * 2 / Math.PI, 0);  //first change location on cent's local face
            if(turnsFromFL == 2)
                addToMoves(compressVector(cent.getVector()), Math.PI);
            else if(turnsFromFL == 1)
                addToMoves(compressVector(cent.getVector()), ZMath.round(currentFlatLoc.cross(flatLoc).getUnit().getZ(), 0) * Math.PI / 2);
            turnsFromFL = (int)ZMath.round(compressVector(cent.getVector()).angleBetween(Vector.Z_AXIS) * 2 / Math.PI, 0);  //then move cent into flatLoc around x-axis
            Vector xSlice = cent.getVector().getXCompVec(), xSliceNext = xSlice.getUnit().scalar(xSlice.magnitude() + 1);
            if(turnsFromFL == 2)
            {
                addToMoves(xSlice, Math.PI);
                addToMoves(xSliceNext, Math.PI);
            }
            else if(turnsFromFL == 1)
            {
                int dir = (int)Math.signum(cent.getVector().getY() * cent.getVector().getX());
                addToMoves(xSlice, dir * Math.PI / 2);
                addToMoves(xSliceNext, dir * Math.PI / -2);
            }
        }
    }
    /**
     * Solves the col center (unit). It is assumed that colFace points forward at this point
     * @param col CubeColor fo the center (unit) to solve
     */
    private void solveRedOrBlueCenter(CubeColor col)
    {
        Set<Center> colFresh;
        if(col.equals(CubeColor.RED))
            colFresh = rFresh;
        else
            colFresh = bFresh;
        if(size % 2 == 1)  //establishing the middle slice
        {
            for(int x = 1; x < size / 2; x++)
                for(int xSign = -1; xSign < 2; xSign += 2)
                {
                    Vector flatLoc = new Vector(x * xSign, 0, 0);
                    Center cent = getCenter(flatLoc, col);
                    sROBCHelp(cent, flatLoc);
                    colFresh.remove(cent);
                }
            addToMoves(Vector.X_AXIS, Math.PI / 2);
            addToMoves(Vector.NEG_X_AXIS, Math.PI / -2);
            addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
            addToMoves(Vector.X_AXIS, Math.PI / -2);
            addToMoves(Vector.NEG_X_AXIS, Math.PI / 2);
        }
        for(double y = ((size % 2) + 1.0) / 2; y < (size - 1.0) / 2; y++)
            for(int ySign = -1; ySign < 2; ySign += 2)
            {
                if(size % 2 == 1)  //establishes keystone center for row
                {
                    Vector flatLoc = new Vector(y * ySign, 0, 0);
                    Center cent = getCenter(flatLoc, col);
                    sROBCHelp(cent, flatLoc);
                    colFresh.remove(cent);
                    addToMoves(cent.getVector().getZCompVec(), Math.PI / -2);
                }
                for(double x = ((size % 2) + 1.0) / 2; x < (size - 1.0) / 2; x++)
                    for(int xSign = -1; xSign < 2; xSign += 2)
                    {
                        Vector flatLoc = new Vector(x * xSign, y * ySign, 0);
                        Center cent = getCenter(flatLoc, col);
                        sROBCHelp(cent, flatLoc);
                        colFresh.remove(cent);
                    }
                addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                addToMoves(new Vector(y * ySign, 0, 0), ySign * Math.PI / 2);  //insert row
                addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI);
                addToMoves(new Vector(y * ySign, 0, 0), ySign * Math.PI / -2);
            }
    }
    /**
     * Moves cent to flatLoc on upper face (unless flatLoc is in xz plane, in which case cent ends up reflected across yz plane)
     * @param cent Center to move
     * @param flatLoc Location to move cent to
     */
    private void sROBCHelp(Center cent, Vector flatLoc)
    {
        if((Math.abs(flatLoc.getY()) > 0.001 && !Vector.Z_AXIS.scalar((size - 1.0) / 2).add(flatLoc).equals(cent.getVector())) || (Math.abs(flatLoc.getY()) < 0.001 && !Vector.Z_AXIS.scalar((size - 1.0) / 2).subtract(flatLoc.getXCompVec()).equals(cent.getVector())))
        {
            if(cent.getPrimaryOrientation().equals(Vector.NEG_Y_AXIS))
            {
                boolean rotateNegY = false;
                if(Math.abs(cent.getVector().getX()) < 0.001)
                    rotateNegY = true;
                if(rotateNegY)
                    addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                Vector xSlice = cent.getVector().getXCompVec(), xSliceNext = xSlice.getUnit().scalar(xSlice.magnitude() + 1);
                addToMoves(xSlice, Math.PI);
                addToMoves(xSliceNext, Math.PI);
                if(rotateNegY || Math.abs(cent.getVector().getZ()) < 0.001)
                    addToMoves(cent.getVector().getYCompVec(), Math.PI / 2);
                else
                {
                    Vector centLoc = cent.getVector();
                    addToMoves(centLoc.getYCompVec(), Math.signum(centLoc.getX() * centLoc.getZ()) * Math.PI / -2);
                }
                addToMoves(xSlice, Math.PI);
                addToMoves(xSliceNext, Math.PI);
                if(rotateNegY)
                    addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
            }
            else if(cent.getPrimaryOrientation().equals(Vector.NEG_Z_AXIS))
            {
                boolean rotateNegZ = false;
                if(Math.abs(cent.getVector().getX()) < 0.001)
                    rotateNegZ = true;
                if(rotateNegZ)
                    addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                Vector xSlice = cent.getVector().getXCompVec(), xSliceNext = xSlice.getUnit().scalar(xSlice.magnitude() + 1);
                addToMoves(xSlice, Math.signum(xSlice.getX()) * Math.PI / 2);
                addToMoves(xSliceNext, Math.signum(xSlice.getX()) * Math.PI / -2);
                if(rotateNegZ || Math.abs(cent.getVector().getZ()) < 0.001)
                    addToMoves(cent.getVector().getYCompVec(), Math.PI / 2);
                else
                {
                    Vector centLoc = cent.getVector();
                    addToMoves(centLoc.getYCompVec(), Math.signum(centLoc.getX() * centLoc.getZ()) * Math.PI / -2);
                }
                addToMoves(xSlice, Math.signum(xSlice.getX()) * Math.PI / -2);
                addToMoves(xSliceNext, Math.signum(xSlice.getX()) * Math.PI / 2);
                if(rotateNegZ)
                    addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
            }
            else if(cent.getPrimaryOrientation().equals(Vector.Z_AXIS))
            {
                boolean rotateZ = false;
                if(Math.abs(cent.getVector().getX() - flatLoc.getX()) > 0.001 && Math.abs(cent.getVector().getY() - flatLoc.getY()) > 0.001 && Math.abs(cent.getVector().getY()) > 0.001)
                    rotateZ = true;
                if(rotateZ)
                    addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                Vector xSlice = cent.getVector().getXCompVec(), xSliceNext = xSlice.getUnit().scalar(xSlice.magnitude() + 1);
                addToMoves(xSlice, Math.signum(xSlice.getX()) * Math.PI / -2);
                addToMoves(xSliceNext, Math.signum(xSlice.getX()) * Math.PI / 2);
                if(Math.abs(cent.getVector().getZ()) < 0.001)
                    addToMoves(cent.getVector().getYCompVec(), Math.PI / 2);
                else
                {
                    Vector centLoc = cent.getVector();
                    addToMoves(centLoc.getYCompVec(), Math.signum(centLoc.getX() * centLoc.getZ()) * Math.PI / -2);
                }
                addToMoves(xSlice, Math.signum(xSlice.getX()) * Math.PI / 2);
                addToMoves(xSliceNext, Math.signum(xSlice.getX()) * Math.PI / -2);
                if(rotateZ)
                    addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
            }
            Vector currentFlatLoc = cent.getVector().rotate(Vector.X_AXIS, Math.PI / 2);
            currentFlatLoc = currentFlatLoc.subtract(compressVector(currentFlatLoc));
            int turnsFromFL = (int)ZMath.round(currentFlatLoc.angleBetween(flatLoc) * 2 / Math.PI, 0);
            if(turnsFromFL == 2)
                addToMoves(compressVector(cent.getVector()), Math.PI);
            else if(turnsFromFL == 1)
                addToMoves(compressVector(cent.getVector()), ZMath.round(currentFlatLoc.cross(flatLoc).getUnit().getZ(), 0) * Math.PI / 2);
            Vector xSlice = cent.getVector().getXCompVec(), xSliceNext = xSlice.getUnit().scalar(xSlice.magnitude() + 1);
            int xDir = (int)Math.signum(cent.getVector().getX());
            addToMoves(xSlice, xDir * Math.PI / 2);
            addToMoves(xSliceNext, xDir * Math.PI / -2);
            int zDir = (int)Math.signum(cent.getVector().getY() * cent.getVector().getX());
            if(zDir == 0)
                addToMoves(compressVector(cent.getVector()), Math.PI);
            else
                addToMoves(compressVector(cent.getVector()), zDir * Math.PI / 2);
            addToMoves(xSlice, xDir * Math.PI / -2);
            addToMoves(xSliceNext, xDir * Math.PI / 2);
            if(zDir != 0)
                addToMoves(compressVector(cent.getVector()), zDir * Math.PI / -2);
        }
    }
    /**
     * Solves the Orange and Green centers. It is assumed that Orange is forward and Green is up at this point.
     */
    private void solveOrangeAndGreenCenters()
    {
        for(double i = ((size % 2) + 1.0) / 2; i < (size - 1.0) / 2; i++)
        {
            Queue<Center> gOnOCents = new LinkedList<>(), oOnGCents = new LinkedList<>();
            for(Center oC : oFresh)
                if(sOAGCHelp(oC).equals(new Vector(i, i, 0)) && oC.getPrimaryOrientation().equals(Vector.Z_AXIS))
                    oOnGCents.add(oC);
            for(Center oC : oOnGCents)
                oFresh.remove(oC);
            for(Center gC : gFresh)
                if(sOAGCHelp(gC).equals(new Vector(i, i, 0)) && gC.getPrimaryOrientation().equals(Vector.NEG_Y_AXIS))
                    gOnOCents.add(gC);
            for(Center gC : gOnOCents)
                gFresh.remove(gC);
            while(!gOnOCents.isEmpty())
            {
                Vector oVec = oOnGCents.remove().getVector(), gVec = gOnOCents.remove().getVector();
                oVec = oVec.subtract(oVec.getZCompVec());
                gVec = gVec.subtract(gVec.getYCompVec());
                int oTurns = (int)ZMath.round(oVec.angleBetween(new Vector(-1, 1, 0)) * 2 / Math.PI, 0), gTurns = (int)ZMath.round(gVec.angleBetween(new Vector(-1, 0, 1)) * 2 / Math.PI, 0);
                if(oTurns == 2)
                    addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
                else if(oTurns == 1)
                    addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), ZMath.round(oVec.cross(new Vector(-1, 1, 0)).getUnit().getZ(), 0) * Math.PI / 2);
                if(gTurns == 2)
                    addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI);
                else if(gTurns == 1)
                    addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), ZMath.round(new Vector(-1, 0, 1).cross(gVec).getUnit().getY(), 0) * Math.PI / 2);
                addToMoves(new Vector(-i, 0, 0), Math.PI / 2);
                addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                addToMoves(new Vector(i, 0, 0), Math.PI / -2);
                addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                addToMoves(new Vector(-i, 0, 0), Math.PI / -2);
                addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                addToMoves(new Vector(i, 0, 0), Math.PI / 2);
            }
            if(size % 2 == 1)
            {
                for(Center oC : oFresh)
                    if(sOAGCHelp(oC).equals(new Vector(0, i, 0)) && oC.getPrimaryOrientation().equals(Vector.Z_AXIS))
                        oOnGCents.add(oC);
                for(Center oC : oOnGCents)
                    oFresh.remove(oC);
                for(Center gC : gFresh)
                    if(sOAGCHelp(gC).equals(new Vector(0, i, 0)) && gC.getPrimaryOrientation().equals(Vector.NEG_Y_AXIS))
                        gOnOCents.add(gC);
                for(Center gC : gOnOCents)
                    gFresh.remove(gC);
                while(!gOnOCents.isEmpty())
                {
                    Vector oVec = oOnGCents.remove().getVector(), gVec = gOnOCents.remove().getVector();
                    oVec = oVec.subtract(oVec.getZCompVec());
                    gVec = gVec.subtract(gVec.getYCompVec());
                    int oTurns = (int)ZMath.round(oVec.angleBetween(new Vector(0, i, 0)) * 2 / Math.PI, 0), gTurns = (int)ZMath.round(gVec.angleBetween(new Vector(0, 0, i)) * 2 / Math.PI, 0);
                    if(oTurns == 2)
                        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
                    else if(oTurns == 1)
                        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), ZMath.round(oVec.cross(new Vector(0, 1, 0)).getUnit().getZ(), 0) * Math.PI / 2);
                    if(gTurns == 2)
                        addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI);
                    else if(gTurns == 1)
                        addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), ZMath.round(new Vector(0, 0, 1).cross(gVec).getUnit().getY(), 0) * Math.PI / 2);
                    addToOTrail(Vector.X_AXIS, Math.PI / 2);
                    addToMoves(Vector.X_AXIS, Math.PI / -2);
                    addToMoves(new Vector(-i, 0, 0), Math.PI / 2);
                    addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                    addToMoves(new Vector(-i, 0, 0), Math.PI / -2);
                    addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                    addToOTrail(Vector.X_AXIS, Math.PI / -2);
                    addToMoves(Vector.X_AXIS, Math.PI / 2);
                    addToMoves(new Vector(-i, 0, 0), Math.PI / -2);
                    addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                    addToMoves(new Vector(-i, 0, 0), Math.PI / 2);
                }
            }
            for(double j = ((size % 2) + 1.0) / 2; j < i; j++)
            {
                for(Center oC : oFresh)
                    if(sOAGCHelp(oC).equals(new Vector(j, i, 0)) && oC.getPrimaryOrientation().equals(Vector.Z_AXIS))
                        oOnGCents.add(oC);
                for(Center oC : oOnGCents)
                    oFresh.remove(oC);
                for(Center gC : gFresh)
                    if(sOAGCHelp(gC).equals(new Vector(j, i, 0)) && gC.getPrimaryOrientation().equals(Vector.NEG_Y_AXIS))
                        gOnOCents.add(gC);
                for(Center gC : gOnOCents)
                    gFresh.remove(gC);
                while(!gOnOCents.isEmpty())
                {
                    Vector oVec = oOnGCents.remove().getVector(), gVec = gOnOCents.remove().getVector();
                    oVec = oVec.subtract(oVec.getZCompVec());
                    gVec = gVec.subtract(gVec.getYCompVec());
                    int oTurns = (int)ZMath.round(oVec.angleBetween(new Vector(j, i, 0)) * 2 / Math.PI, 0), gTurns = (int)ZMath.round(gVec.angleBetween(new Vector(j, 0, i)) * 2 / Math.PI, 0);
                    if(oTurns == 2)
                        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
                    else if(oTurns == 1)
                        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), ZMath.round(oVec.cross(new Vector(j, i, 0)).getUnit().getZ(), 0) * Math.PI / 2);
                    if(gTurns == 2)
                        addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI);
                    else if(gTurns == 1)
                        addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), ZMath.round(new Vector(j, 0, i).cross(gVec).getUnit().getY(), 0) * Math.PI / 2);
                    addToMoves(new Vector(j, 0, 0), Math.PI / 2);
                    addToMoves(new Vector(i, 0, 0), Math.PI / -2);
                    addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                    addToMoves(new Vector(i, 0, 0), Math.PI / 2);
                    addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                    addToMoves(new Vector(j, 0, 0), Math.PI / -2);
                    addToMoves(new Vector(i, 0, 0), Math.PI / 2);
                    addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                    addToMoves(new Vector(i, 0, 0), Math.PI / -2);
                }
                for(Center oC : oFresh)
                    if(sOAGCHelp(oC).equals(new Vector(-j, i, 0)) && oC.getPrimaryOrientation().equals(Vector.Z_AXIS))
                        oOnGCents.add(oC);
                for(Center oC : oOnGCents)
                    oFresh.remove(oC);
                for(Center gC : gFresh)
                    if(sOAGCHelp(gC).equals(new Vector(-j, i, 0)) && gC.getPrimaryOrientation().equals(Vector.NEG_Y_AXIS))
                        gOnOCents.add(gC);
                for(Center gC : gOnOCents)
                    gFresh.remove(gC);
                while(!gOnOCents.isEmpty())
                {
                    Vector oVec = oOnGCents.remove().getVector(), gVec = gOnOCents.remove().getVector();
                    oVec = oVec.subtract(oVec.getZCompVec());
                    gVec = gVec.subtract(gVec.getYCompVec());
                    int oTurns = (int)ZMath.round(oVec.angleBetween(new Vector(-j, i, 0)) * 2 / Math.PI, 0), gTurns = (int)ZMath.round(gVec.angleBetween(new Vector(-j, 0, i)) * 2 / Math.PI, 0);
                    if(oTurns == 2)
                        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
                    else if(oTurns == 1)
                        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), ZMath.round(oVec.cross(new Vector(-j, i, 0)).getUnit().getZ(), 0) * Math.PI / 2);
                    if(gTurns == 2)
                        addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI);
                    else if(gTurns == 1)
                        addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), ZMath.round(new Vector(-j, 0, i).cross(gVec).getUnit().getY(), 0) * Math.PI / 2);
                    addToMoves(new Vector(-j, 0, 0), Math.PI / -2);
                    addToMoves(new Vector(-i, 0, 0), Math.PI / 2);
                    addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                    addToMoves(new Vector(-i, 0, 0), Math.PI / -2);
                    addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                    addToMoves(new Vector(-j, 0, 0), Math.PI / 2);
                    addToMoves(new Vector(-i, 0, 0), Math.PI / -2);
                    addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                    addToMoves(new Vector(-i, 0, 0), Math.PI / 2);
                }
            }
        }
    }
    /**
     * The method for solving the diagonal centers is from JRCuber's video on solving the last two centers. Initially I approached this problem more loosely/intuitively,
     * but the formulaic nature of JRCuber's approach to this was far easier to implement. You can watch his video here: https://www.youtube.com/watch?v=4UnzSSNxcRc
     * @param c Center to orient the Vector of
     * @return c.vector, oriented to the upper face, and rotated so that c.vector.y < abs(c.vector.x)
     */
    private Vector sOAGCHelp(Center c)
    {
        Vector cVec = c.getVector();
        int turnsFromPosZ = (int)ZMath.round(compressVector(cVec).angleBetween(Vector.Z_AXIS) * 2 / Math.PI, 0);
        if(turnsFromPosZ == 1)
            cVec = cVec.rotate(Vector.NEG_X_AXIS, Math.PI / 2);
        cVec = cVec.subtract(cVec.getZCompVec());
        int turnsFromPosY;
        Vector targetVector;
        if(Math.abs(Math.abs(cVec.getX()) - Math.abs(cVec.getY())) < 0.001)
        {
            targetVector = new Vector(1, 1, 0);
            turnsFromPosY = (int)ZMath.round(cVec.angleBetween(targetVector) * 2 / Math.PI, 0);
        }
        else
        {
            targetVector = Vector.Y_AXIS;
            turnsFromPosY = (int)ZMath.round(compressVector(cVec).angleBetween(Vector.Y_AXIS) * 2 / Math.PI, 0);
        }
        if(turnsFromPosY == 2)
            cVec = cVec.rotate(Vector.Z_AXIS, Math.PI);
        else if(turnsFromPosY == 1)
            cVec = cVec.rotate(Vector.Z_AXIS, (int)ZMath.round(compressVector(cVec).cross(targetVector).getUnit().getZ(), 0) * Math.PI / 2);
        return cVec;
    }
    //End of centers solve, beginning of edges solve
    /**
     * Solves the edges of the cubes. Done after solving the centers of the cube
     */
    private void solveEdges()
    {
        Queue<MasterEdge> freshMEdges = new LinkedList<>();  //MasterEdges yet to be solved
        for(MasterEdge mE : masterEdges)
            freshMEdges.add(mE);
        while(freshMEdges.size() > 2)
        {
            MasterEdge mE = freshMEdges.remove(), junkME;
            Map<Integer, Edge> numEdge = new HashMap<>();
            for(Edge e : edges)
                if(e.getColors().equals(mE.getColors()))
                    numEdge.put(e.getNum(), e);
            for(int i = 1 + (size % 2); i < size - 1; i += 2)
                for(int sign = -1; sign < 2; sign += 2)
                {
                    Edge e = numEdge.get(sign * i);
                    junkME = freshMEdges.peek();
                    if(compressVector(e.getVector()).equals(junkME.getVector()))
                        junkME = ((LinkedList<MasterEdge>)freshMEdges).getLast();
                    sEHelp(mE, junkME, e);
                }
        }
        //Last two edges need to be solved differently so as not to disrupt the other 10
        MasterEdge mEA = freshMEdges.remove(), mEB = freshMEdges.remove();
        Map<Integer, Edge> numEdgeA = new HashMap<>(), numEdgeB = new HashMap<>();
        for(Edge e : edges)
        {
            if(e.getColors().equals(mEA.getColors()))
                numEdgeA.put(e.getNum(), e);
            else if(e.getColors().equals(mEB.getColors()))
                numEdgeB.put(e.getNum(), e);
        }
        if(mEA.getVector().getZ() < -0.001)
            addToOTrail(mEA.getVector().subtract(mEA.getVector().getZCompVec()), Math.PI);
        else if(Math.abs(mEA.getVector().getZ()) < 0.001)
            addToOTrail(Vector.X_AXIS, Math.signum(mEA.getVector().getY()) * Math.PI / 2);
        int turns = (int)ZMath.round(mEA.getVector().subtract(mEA.getVector().getZCompVec()).angleBetween(Vector.NEG_Y_AXIS) * 2 / Math.PI, 0);
        if(turns == 2)
            addToOTrail(Vector.Z_AXIS, Math.PI);
        else if(turns == 1)
            addToOTrail(Vector.Z_AXIS, ZMath.round(mEA.getVector().subtract(mEA.getVector().getZCompVec()).cross(Vector.NEG_Y_AXIS).getUnit().getZ(), 0) * Math.PI / 2);
        if(mEB.getVector().getZ() > 0.001 && !mEB.getVector().equals(mEA.getVector().rotate(Vector.Z_AXIS, Math.PI)))
            addToMoves(mEB.getVector().subtract(mEB.getVector().getZCompVec()), Math.PI);
        else if(Math.abs(mEB.getVector().getZ()) < 0.001)
            addToMoves(mEB.getVector().getXCompVec(), Math.signum(mEB.getVector().getX() * mEB.getVector().getY()) * Math.PI / -2);
        if(mEB.getVector().getZ() < -0.001)
        {
            turns = (int)ZMath.round(mEB.getVector().subtract(mEB.getVector().getZCompVec()).angleBetween(Vector.Y_AXIS) * 2 / Math.PI, 0);
            if(turns == 2)
                addToMoves(mEB.getVector().getZCompVec(), Math.PI);
            else if(turns == 1)
                addToMoves(mEB.getVector().getZCompVec(), ZMath.round(Vector.Y_AXIS.cross(mEB.getVector().subtract(mEB.getVector().getZCompVec())).getUnit().getZ(), 0) * Math.PI / 2);
            addToMoves(mEB.getVector().getYCompVec(), Math.PI);
        }
        if(mEA.getSecondaryOrientation().equals(Vector.Z_AXIS))
        {
            addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
            addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
            addToMoves(Vector.NEG_X_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
            addToOTrail(Vector.Z_AXIS, Math.PI / 2);
        }
        for(int i = 1 + (size % 2); i < size - 1; i += 2)
        {
            for(int sign = -1; sign < 2; sign += 2)
            {
                Edge e = numEdgeA.get(sign * i);
                if(!(mEA.getPrimaryOrientation().equals(e.getPrimaryOrientation()) && mEA.getSecondaryOrientation().equals(e.getSecondaryOrientation())))
                {
                    if(mEA.getVector().equals(compressVector(e.getVector())))
                        flippedParity(i / 2.0);
                    else
                    {
                        double x = e.getNum() / 2.0;
                        if(Math.abs(x - e.getVector().getX()) > 0.001)
                        {
                            addToMoves(Vector.Y_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                            addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                            addToMoves(Vector.X_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                            addToOTrail(Vector.Z_AXIS, Math.PI / 2);
                        }
                        if(x < 0)
                        {
                            addToOTrail(Vector.Z_AXIS, Math.PI);
                            crossedParity(-x);
                            addToOTrail(Vector.Z_AXIS, Math.PI);
                        }
                        else
                            crossedParity(x);
                    }
                }
            }
            Edge e = numEdgeB.get(i);
            if(e.getPrimaryOrientation().equals(mEB.getSecondaryOrientation()))
            {
                addToOTrail(Vector.Z_AXIS, Math.PI);
                flippedParity(Math.abs(e.getNum() / 2.0));
                addToOTrail(Vector.Z_AXIS, Math.PI);
            }
        }
    }
    /**
     * inserts e into mE, potentially disrupting the contents of junkME
     * @param mE MasterEdge that e belongs to
     * @param junkME MasterEdge that has yet to be solved, and is thus a junk MasterEdge at this point
     * @param e Edge piece to be inserted into mE
     */
    private void sEHelp(MasterEdge mE, MasterEdge junkME, Edge e)
    {
        List<Vector> orientations = mE.getOrientations();
        if(orientations.get(0).parallelTo(Vector.Z_AXIS))
            addToOTrail(orientations.get(1), orientations.get(0).dot(Vector.Z_AXIS) * Math.PI / 2);
        else if(orientations.get(1).parallelTo(Vector.Z_AXIS))
            addToOTrail(orientations.get(0), orientations.get(1).dot(Vector.NEG_Z_AXIS) * Math.PI / 2);
        if(orientations.get(0).cross(orientations.get(1)).equals(Vector.NEG_Z_AXIS))
            addToOTrail(Vector.X_AXIS, Math.PI);
        int turns = (int)ZMath.round(mE.getVector().angleBetween(new Vector(-1, -1, 0)) * 2 / Math.PI, 0);
        if(turns == 2)
            addToOTrail(Vector.Z_AXIS, Math.PI);
        else if(turns == 1)
            addToOTrail(Vector.Z_AXIS, ZMath.round(mE.getVector().cross(new Vector(-1, -1, 0)).getUnit().getZ(), 0) * Math.PI / 2);
        orientations = e.getOrientations();
        if(orientations.get(0).equals(mE.getSecondaryOrientation()) && orientations.get(1).equals(mE.getPrimaryOrientation()))
        {
            addToOTrail(Vector.Y_AXIS, Math.PI / 2);
            double x = Math.abs(e.getNum()) / 2.0;
            flippedParity(x);
        }
        else if(!(orientations.get(0).equals(Vector.NEG_X_AXIS) && orientations.get(1).equals(Vector.NEG_Y_AXIS)))
        {
            if(orientations.get(1).cross(orientations.get(0)).equals(Vector.Z_AXIS))
            {
                if(e.getVector().getX() > 0)
                    addToMoves(Vector.X_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                else
                    addToMoves(Vector.Y_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
            }
            else if(orientations.get(0).cross(orientations.get(1)).equals(Vector.Z_AXIS) && !compressVector(e.getVector()).equals(new Vector(1, -1, 0).scalar((size -1.0) / 2)))
                addToMoves(Vector.Y_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
            if(orientations.get(0).parallelTo(Vector.Z_AXIS))
            {
                Vector eVec = compressVector(e.getVector());
                turns = (int)ZMath.round(eVec.subtract(eVec.getZCompVec()).angleBetween(Vector.X_AXIS) * 2 / Math.PI, 0);
                if(turns == 2)
                    addToMoves(eVec.getZCompVec(), Math.PI);
                else if(turns == 1)
                    addToMoves(eVec.getZCompVec(), ZMath.round(eVec.subtract(eVec.getZCompVec()).cross(Vector.X_AXIS).getUnit().getZ() * Math.signum(eVec.getZ()), 0) * Math.PI / 2);
                addToMoves(Vector.X_AXIS.scalar((size - 1.0) / 2), Math.signum(eVec.getZ()) * Math.PI / 2);
            }
            else if(orientations.get(1).parallelTo(Vector.Z_AXIS))
            {
                Vector eVec = compressVector(e.getVector());
                turns = (int)ZMath.round(eVec.subtract(eVec.getZCompVec()).angleBetween(Vector.X_AXIS) * 2 / Math.PI, 0);
                if(turns == 2)
                    addToMoves(eVec.getZCompVec(), Math.PI);
                else if(turns == 1)
                    addToMoves(eVec.getZCompVec(), ZMath.round(eVec.subtract(eVec.getZCompVec()).cross(Vector.X_AXIS).getUnit().getZ() * Math.signum(eVec.getZ()), 0) * Math.PI / 2);
                int yDir = (int)Math.signum(eVec.getZ()), zDir = -yDir;
                addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), yDir * Math.PI / 2);
                addToMoves(eVec.getZCompVec(), zDir * Math.PI / 2);
                addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), yDir * Math.PI / -2);
            }
            if(junkME.getVector().getZ() < -0.001)
            {
                Vector jMEVec = junkME.getVector();
                turns = (int)ZMath.round(jMEVec.subtract(jMEVec.getZCompVec()).angleBetween(Vector.Y_AXIS) * 2 / Math.PI, 0);
                if(turns == 2)
                    addToMoves(jMEVec.getZCompVec(), Math.PI);
                else if(turns == 1)
                    addToMoves(jMEVec.getZCompVec(), ZMath.round(Vector.Y_AXIS.cross(jMEVec.subtract(jMEVec.getZCompVec())).getUnit().getZ(), 0) * Math.PI / 2);
                addToMoves(Vector.Y_AXIS.scalar((size - 1.0)/ 2), Math.PI);
            }
            else if(Math.abs(junkME.getVector().getZ()) < 0.001)
                addToMoves(Vector.Y_AXIS.scalar((size - 1.0)/ 2), Math.signum(junkME.getVector().getX()) * Math.PI / -2);
            Vector jMEVec = junkME.getVector();
            turns = (int)ZMath.round(jMEVec.subtract(jMEVec.getZCompVec()).angleBetween(Vector.NEG_Y_AXIS) * 2 / Math.PI, 0);
            if(turns == 2)
                addToMoves(jMEVec.getZCompVec(), Math.PI);
            else if(turns == 1)
                addToMoves(jMEVec.getZCompVec(), ZMath.round(jMEVec.subtract(jMEVec.getZCompVec()).cross(Vector.NEG_Y_AXIS).getUnit().getZ(), 0) * Math.PI / 2);
            Vector eVec = e.getVector();
            turns = (int)ZMath.round(mE.getVector().angleBetween(compressVector(eVec)) * 2 / Math.PI, 0);
            double theta;
            if(turns == 2)
                theta = Math.PI;
            else
                theta = ZMath.round(compressVector(eVec).cross(mE.getVector()).getUnit().getZ() * Math.signum(eVec.getZ()), 0) * Math.PI / 2;
            Vector zSlice = eVec.getZCompVec(), zSliceNext = zSlice.getUnit().scalar(zSlice.magnitude() + 1);
            addToMoves(zSlice, theta);
            addToMoves(zSliceNext, -theta);
            addToMoves(Vector.NEG_X_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
            addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
            addToMoves(Vector.NEG_X_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
            addToMoves(zSlice, -theta);
            addToMoves(zSliceNext, theta);
        }
    }
    /**
     * Parity case where the outer edge segments are criss-crossed. Switches the right outer edge segments
     * @param x x-value of the right outer edge
     */
    private void crossedParity(double x)
    {
        addToMoves(new Vector(-x, 0, 0), Math.PI / 2);
        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(new Vector(-x, 0, 0), Math.PI / 2);
        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(new Vector(-x, 0, 0), Math.PI / 2);
        addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(new Vector(x, 0, 0), Math.PI / -2);
        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(new Vector(x, 0, 0), Math.PI / 2);
        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(new Vector(-x, 0, 0), Math.PI);
    }
    /**
     * Parity case where the outer edge segments are flipped from their correct position. Flips them
     * @param x x-value of the outer edges
     */
    private void flippedParity(double x)
    {
        addToMoves(new Vector(x, 0, 0), Math.PI);
        addToMoves(Vector.Y_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(new Vector(-x, 0, 0), Math.PI / -2);
        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(new Vector(x, 0, 0), Math.PI / 2);
        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(new Vector(x, 0, 0), Math.PI / -2);
        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(new Vector(x, 0, 0), Math.PI / -2);
        addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(new Vector(-x, 0, 0), Math.PI / 2);
        addToMoves(Vector.Y_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(new Vector(x, 0, 0), Math.PI);
    }
    //End of edges solve, beginning of 3x3 solve
    /**
     * Parity case where two MasterEdges need to be switched
     */
    private void solve3x3()
    {
        whiteEdges();
        whiteCorners();
        addToOTrail(Vector.Y_AXIS, Math.PI);
        middleLayer();
        yellowEdgeOrientations();
        yellowCornerOrientations();
        yellowCornerLocations();
        yellowEdgeLocations();
    }
    /**
     * Solves the 2x2
     */
    private void solve2x2()
    {
        whiteCorners();
        addToOTrail(Vector.Y_AXIS, Math.PI);
        yellowCornerOrientations();
        yellowCornerLocations();
    }
    /**
     * Solves the white cross of the cube
     */
    private void whiteEdges()
    {
        int facesWhiteToUp = (int)ZMath.round(wFace.angleBetween(Vector.Z_AXIS) * 2 / Math.PI, 0);
        if(facesWhiteToUp == 2)
            addToOTrail(Vector.Y_AXIS, Math.PI);
        else if(facesWhiteToUp == 1)
            addToOTrail(wFace.cross(Vector.Z_AXIS), Math.PI / 2);
        whiteEdge(CubeColor.RED);
        whiteEdge(CubeColor.BLUE);
        whiteEdge(CubeColor.ORANGE);
        whiteEdge(CubeColor.GREEN);
    }
    /**
     * Solves the white cross of the cube for a specific color. It is assumed that the white center is up at this point.
     * @param col CubeColor of the MasterEdge to put in place.
     */
    private void whiteEdge(CubeColor col)
    {
        MasterEdge masterEdge = null;
        for(MasterEdge mE : masterEdges)
            if(mE.getPrimary().equals(CubeColor.WHITE) && mE.getSecondary().equals(col))
                masterEdge = mE;
        Vector colFace = getFace(col);
        if(!(wFace.equals(masterEdge.getPrimaryOrientation()) && colFace.equals(masterEdge.getSecondaryOrientation())))
        {
            Vector mEVec = masterEdge.getVector();
            if(masterEdge.getPrimaryOrientation().equals(Vector.Z_AXIS))  //white on top but not correct spot
                addToMoves(mEVec.subtract(mEVec.getZCompVec()), Math.PI);
            else if(masterEdge.getSecondaryOrientation().equals(Vector.Z_AXIS))  //col on top
            {
                if(masterEdge.getPrimaryOrientation().parallelTo(colFace))  //col across top from correct spot
                    addToMoves(mEVec.subtract(mEVec.getZCompVec()), Math.PI);
                else  //col in adjacent edge from correct spot
                {
                    int dir = (int)ZMath.round(colFace.cross(mEVec.subtract(mEVec.getZCompVec())).getUnit().getZ(), 0);  //determines direction of spin
                    addToMoves(mEVec.subtract(mEVec.getZCompVec()), dir * Math.PI / 2);
                    addToMoves(colFace.scalar((size - 1.0) / 2), dir * Math.PI / 2);
                }
            }
            else if(!(masterEdge.getPrimaryOrientation().equals(Vector.NEG_Z_AXIS) || masterEdge.getSecondaryOrientation().equals(Vector.NEG_Z_AXIS)))  //not on bottom
            {
                if(masterEdge.getSecondaryOrientation().equals(colFace))  //col on col face
                {
                    addToMoves(colFace.scalar((size - 1.0) / 2), ZMath.round(colFace.cross(mEVec.subtract(mEVec.getZCompVec())).getUnit().getZ(), 0) * Math.PI / 2);
                }
                else if(masterEdge.getSecondaryOrientation().equals(colFace.scalar(-1)))  //col on opposite of colFace
                {
                    addToMoves(mEVec.add(colFace.scalar((size - 1.0) / 2)), Math.PI);
                    Vector newMEVec = masterEdge.getVector();
                    addToMoves(colFace.scalar((size - 1.0) / 2), ZMath.round(colFace.cross(newMEVec.subtract(newMEVec.getZCompVec())).getUnit().getZ(), 0) * Math.PI / 2);
                    addToMoves(mEVec.add(colFace.scalar((size - 1.0) / 2)), Math.PI);
                }
                else if(masterEdge.getPrimaryOrientation().equals(colFace))  //white on colFace (one turn to put edge on bottom)
                    addToMoves(colFace.scalar((size - 1.0) / 2), ZMath.round(mEVec.subtract(mEVec.getZCompVec()).cross(colFace).getUnit().getZ(), 0) * Math.PI / 2);
                else  //white on opposite of colFace (three turns to put edge on bottom)
                {
                    addToMoves(colFace.scalar((1.0 - size) / 2), ZMath.round(colFace.cross(mEVec.subtract(mEVec.getZCompVec())).getUnit().getZ(), 0) * Math.PI / 2);
                    addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                    addToMoves(colFace.scalar((1.0 - size) / 2), ZMath.round(mEVec.subtract(mEVec.getZCompVec()).cross(colFace).getUnit().getZ(), 0) * Math.PI / 2);
                }
            }
            if(masterEdge.getPrimaryOrientation().equals(Vector.NEG_Z_AXIS))  //white on bottom
            {
                mEVec = masterEdge.getVector();
                int facesFromCF = (int)ZMath.round(mEVec.subtract(mEVec.getZCompVec()).angleBetween(colFace) * 2 / Math.PI, 0);
                if(facesFromCF == 2)
                    addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
                else if(facesFromCF == 1)
                    addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), colFace.cross(mEVec.subtract(mEVec.getZCompVec())).getUnit().getZ() * Math.PI / 2);
                addToMoves(colFace.scalar((size - 1.0) / 2), Math.PI);
            }
            else if(masterEdge.getSecondaryOrientation().equals(Vector.NEG_Z_AXIS))  //col on bottom
            {
                mEVec = masterEdge.getVector();
                Vector faceToRight = colFace.rotate(Vector.Z_AXIS, Math.PI / 2);
                int facesFromFTR = (int)ZMath.round(mEVec.subtract(mEVec.getZCompVec()).angleBetween(faceToRight) * 2 / Math.PI, 0);
                if(facesFromFTR == 2)
                    addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
                else if(facesFromFTR == 1)
                    addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), faceToRight.cross(mEVec.subtract(mEVec.getZCompVec())).getUnit().getZ() * Math.PI / 2);
                addToMoves(faceToRight.scalar((size - 1.0) / 2), Math.PI / -2);
                addToMoves(colFace.scalar((size - 1.0) / 2), Math.PI / 2);
                addToMoves(faceToRight.scalar((size - 1.0) / 2), Math.PI / 2);
            }
        }
    }
    /**
     * Solves the rest of the white face (the white corners) after the white cross is done
     */
    private void whiteCorners()
    {
        whiteCorner(CubeColor.RED);
        whiteCorner(CubeColor.BLUE);
        whiteCorner(CubeColor.ORANGE);
        whiteCorner(CubeColor.GREEN);
    }
    /**
     * Puts a white corner in the correct spot. It is assumed that the white center is up at this point.
     * @param secCol Secondary color of the desired corner (white is the primary)
     */
    private void whiteCorner(CubeColor secCol)
    {
        Corner corner = null;
        for(Corner c : corners)
            if(c.getPrimary().equals(CubeColor.WHITE) && c.getSecondary().equals(secCol))
                corner = c;
        CubeColor terCol = corner.getTertiary();
        Vector secFace = getFace(secCol);
        Vector terFace = getFace(terCol);
        if(!(corner.getPrimaryOrientation().equals(Vector.Z_AXIS) && corner.getSecondaryOrientation().equals(secFace) && corner.getTertiaryOrientation().equals(terFace)))
        {
            Vector cVec = corner.getVector();
            if(corner.getPrimaryOrientation().equals(wFace))  //white on top but not correct spot
            {
                Vector terAxisInit = corner.getTertiaryOrientation();
                addToMoves(terAxisInit.scalar((size - 1.0) / 2), Math.PI / 2);
                addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                addToMoves(terAxisInit.scalar((size - 1.0) / 2), Math.PI / -2);
            }
            else if(corner.getSecondaryOrientation().equals(Vector.Z_AXIS) || corner.getTertiaryOrientation().equals(Vector.Z_AXIS))  //corner on top
            {
                Vector whiteAxisInit = corner.getPrimaryOrientation();
                int dir = (int)ZMath.round(1 - 2 * whiteAxisInit.cross(corner.getSecondaryOrientation()).getUnit().getZ(), 0);
                addToMoves(whiteAxisInit.scalar((size - 1.0) / 2), dir * Math.PI / 2);
                addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), dir * Math.PI / 2);
                addToMoves(whiteAxisInit.scalar((size - 1.0) / 2), dir * Math.PI / -2);
            }
            if(corner.getPrimaryOrientation().equals(Vector.NEG_Z_AXIS))  //white on bottom
            {
                int turnsFromEdge = (int)ZMath.round(cVec.subtract(cVec.getZCompVec()).angleBetween(secFace.add(terFace)) * 2 / Math.PI, 0);
                if(turnsFromEdge == 2)
                    addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
                else if(turnsFromEdge == 1)
                    addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), ZMath.round(secFace.add(terFace).cross(cVec.subtract(cVec.getZCompVec())).getUnit().getZ(), 0) * Math.PI / 2);
                addToMoves(terFace.scalar((size - 1.0) / 2), Math.PI / 2);
                addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
                addToMoves(terFace.scalar((size - 1.0) / 2), Math.PI / -2);
                addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
            }
            cVec = corner.getVector();
            if(corner.getSecondaryOrientation().equals(Vector.NEG_Z_AXIS))  //secCol on bottom
            {
                Vector edgeToLeft = secFace.add(terFace).rotate(Vector.Z_AXIS, Math.PI / -2);
                int turnsFromETL = (int)ZMath.round(cVec.subtract(cVec.getZCompVec()).angleBetween(edgeToLeft) * 2 / Math.PI, 0);
                if(turnsFromETL == 2)
                    addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
                else if(turnsFromETL == 1)
                    addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), ZMath.round(edgeToLeft.cross(cVec.subtract(cVec.getZCompVec())).getUnit().getZ(), 0) * Math.PI / 2);
                addToMoves(terFace.scalar((size - 1.0) / 2), Math.PI / 2);
                addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                addToMoves(terFace.scalar((size - 1.0) / 2), Math.PI / -2);
            }
            else  //only other option is terCol on bottom (white on bottoms have been turned into terCol on bottoms)
            {
                Vector edgeToRight = secFace.add(terFace).rotate(Vector.Z_AXIS, Math.PI / 2);
                int turnsFromETR = (int)ZMath.round(cVec.subtract(cVec.getZCompVec()).angleBetween(edgeToRight) * 2 / Math.PI, 0);
                if(turnsFromETR == 2)
                    addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
                else if(turnsFromETR == 1)
                    addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), ZMath.round(edgeToRight.cross(cVec.subtract(cVec.getZCompVec())).getUnit().getZ(), 0) * Math.PI / 2);
                addToMoves(secFace.scalar((size - 1.0) / 2), Math.PI / -2);
                addToMoves(Vector.NEG_Z_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                addToMoves(secFace.scalar((size - 1.0) / 2), Math.PI / 2);
            }
        }

    }
    /**
     * Solves the middle layer of the cube
     */
    private void middleLayer()
    {
        middleEdge(CubeColor.RED, CubeColor.BLUE);
        middleEdge(CubeColor.RED, CubeColor.GREEN);
        middleEdge(CubeColor.ORANGE, CubeColor.BLUE);
        middleEdge(CubeColor.ORANGE, CubeColor.GREEN);
    }
    /**
     * Puts a middle edge piece into the correct spot. It is assumed that the yellow center is up at this point.
     * @param priCol primary CubeColor of the desired edge
     * @param secCol secondary CubeColor of the desired edge
     */
    private void middleEdge(CubeColor priCol, CubeColor secCol)
    {
        MasterEdge masterEdge = null;
        for(MasterEdge mE : masterEdges)
            if(mE.getPrimary().equals(priCol) && mE.getSecondary().equals(secCol))
                masterEdge = mE;
        Vector priFace = getFace(priCol), secFace = getFace(secCol);
        if(!(masterEdge.getPrimaryOrientation().equals(priFace) && masterEdge.getSecondaryOrientation().equals(secFace)))
        {
            Vector mEVec = masterEdge.getVector();
            if(!(masterEdge.getPrimaryOrientation().equals(Vector.Z_AXIS) || masterEdge.getSecondaryOrientation().equals(Vector.Z_AXIS)))  //masterEdge already in middle layer but not correctly
            {
                MasterEdge yellowEdge = null;
                for(MasterEdge mE : masterEdges)
                    if(mE.inSlice(Vector.Z_AXIS.scalar((size - 1.0) / 2)) && mE.getPrimary().equals(CubeColor.YELLOW))
                        yellowEdge = mE;
                Vector yEVec = yellowEdge.getVector();
                Vector prepFace = mEVec.rotate(Vector.Z_AXIS, 3 * Math.PI / 4).getUnit();
                int turnsFromPF = (int)ZMath.round(yEVec.subtract(yEVec.getZCompVec()).angleBetween(prepFace) * 2 / Math.PI, 0);
                if(turnsFromPF == 2)
                    addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
                else if(turnsFromPF == 1)
                    addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), ZMath.round(yEVec.subtract(yEVec.getZCompVec()).cross(prepFace).getUnit().getZ(), 0) * Math.PI / 2);
                Vector faceToLeft = mEVec.rotate(Vector.Z_AXIS, Math.PI / -4).getUnit();
                Vector faceToRight = mEVec.rotate(Vector.Z_AXIS, Math.PI / 4).getUnit();
                addToMoves(faceToLeft.scalar((size - 1.0) / 2), Math.PI / 2);
                addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                addToMoves(faceToLeft.scalar((size - 1.0) / 2), Math.PI / -2);
                addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                addToMoves(faceToRight.scalar((size - 1.0) / 2), Math.PI / -2);
                addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                addToMoves(faceToRight.scalar((size - 1.0) / 2), Math.PI / 2);
                mEVec = masterEdge.getVector();
            }
            Vector prepFace;
            if(masterEdge.getPrimaryOrientation().equals(Vector.Z_AXIS))  //priCol on top
                prepFace = priFace.scalar(-1);
            else
                prepFace = secFace.scalar(-1);
            int turnsFromPF = (int)ZMath.round(mEVec.subtract(mEVec.getZCompVec()).angleBetween(prepFace) * 2 / Math.PI, 0);
            if(turnsFromPF == 2)
                addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
            else if(turnsFromPF == 1)
                addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), ZMath.round(mEVec.subtract(mEVec.getZCompVec()).cross(prepFace).getUnit().getZ(), 0) * Math.PI / 2);
            Vector oppToPF = prepFace.scalar(-1);
            Vector adjToPF;
            if(priFace.equals(oppToPF))
                adjToPF = secFace;
            else
                adjToPF = priFace;
            int dir = (int)ZMath.round(oppToPF.cross(adjToPF).getZ(), 0);
            addToMoves(oppToPF.scalar((size - 1.0) / 2), dir * Math.PI / 2);
            addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), dir * Math.PI / -2);
            addToMoves(oppToPF.scalar((size - 1.0) / 2), dir * Math.PI / -2);
            addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), dir * Math.PI / -2);
            addToMoves(adjToPF.scalar((size - 1.0) / 2), dir * Math.PI / -2);
            addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), dir * Math.PI / 2);
            addToMoves(adjToPF.scalar((size - 1.0) / 2), dir * Math.PI / 2);
        }
    }
    /**
     * Solves the yellow cross of the cube (edges not necessarily in the right spot, but oriented correctly). It is assumed that the yellow center is up at this point
     */
    private void yellowEdgeOrientations()
    {
        List<MasterEdge> yellowEdges = new LinkedList<>();
        for(MasterEdge mE : masterEdges)
            if(mE.getPrimary().equals(CubeColor.YELLOW))
                yellowEdges.add(mE);
        int yellowOnTopCount = 0;
        for(MasterEdge yE : yellowEdges)
            if(yE.getPrimaryOrientation().equals(Vector.Z_AXIS))
                yellowOnTopCount++;
        while(yellowOnTopCount != 4)
        {
            if(yellowOnTopCount == 1 || yellowOnTopCount == 3)
            {
                flippedParity(0.5);
                for(MasterEdge yE : yellowEdges)
                    if(yE.getVector().equals(new Vector(0, -1, 1).scalar((size - 1.0) / 2)))
                        yE.rotate(yE.getVector(), Math.PI);
            }
            else if(yellowOnTopCount == 0)
            {
                addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                addToMoves(Vector.X_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                addToMoves(Vector.X_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
            }
            else if(yellowOnTopCount == 2)
            {
                Vector yellowSum = Vector.ZERO_VEC, yellowOnTop = null;
                for(MasterEdge mE : yellowEdges)
                    if(mE.getPrimaryOrientation().equals(Vector.Z_AXIS))
                    {
                        yellowSum = yellowSum.add(mE.getVector());
                        yellowOnTop = mE.getVector();
                    }
                yellowSum = yellowSum.subtract(yellowSum.getZCompVec());
                if(yellowSum.equals(Vector.ZERO_VEC))
                {
                    Vector tempFront = yellowOnTop.cross(Vector.Z_AXIS), tempRight = yellowOnTop.subtract(yellowOnTop.getZCompVec());
                    addToMoves(tempFront, Math.PI / -2);
                    addToMoves(tempRight, Math.PI / -2);
                    addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                    addToMoves(tempRight, Math.PI / 2);
                    addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                    addToMoves(tempFront, Math.PI / 2);
                }
                else
                {
                    Vector tempFront = yellowSum.rotate(Vector.Z_AXIS, Math.PI / -4).getUnit().scalar((1.0 - size) / 2),
                            tempRight = yellowSum.rotate(Vector.Z_AXIS, Math.PI / 4).getUnit().scalar((1.0 - size) / 2);
                    addToMoves(tempFront, Math.PI / -2);
                    addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
                    addToMoves(tempRight, Math.PI / -2);
                    addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
                    addToMoves(tempRight, Math.PI / 2);
                    addToMoves(tempFront, Math.PI / 2);
                }
            }
            yellowOnTopCount = 0;
            for(MasterEdge yE : yellowEdges)
                if(yE.getPrimaryOrientation().equals(Vector.Z_AXIS))
                    yellowOnTopCount++;
        }
    }
    /**
     * Solves the yellow corners of the cube (corners not necessarily in the right spot,  but oriented correctly).  It is assumed that the yellow center is up at this point
     */
    private void yellowCornerOrientations()
    {
        List<Corner> yellowCorners = new LinkedList<>();
        for(Corner c : corners)
            if(c.getPrimary().equals(CubeColor.YELLOW))
                yellowCorners.add(c);
        int yellowOnTopCount = 0;
        for(Corner yC : yellowCorners)
            if(yC.getPrimaryOrientation().equals(Vector.Z_AXIS))
                yellowOnTopCount++;
        while(yellowOnTopCount != 4)
        {
            Vector tempRight = null;
            if(yellowOnTopCount == 0)
            {
                Corner yellowLeft = null;
                for(Corner yC : yellowCorners)
                    if(yC.getTertiaryOrientation().equals(Vector.Z_AXIS))
                        yellowLeft = yC;
                tempRight = yellowLeft.getVector().subtract(yellowLeft.getVector().getZCompVec()).rotate(Vector.Z_AXIS, Math.PI / -4).getUnit().scalar((1.0 - size) / 2);
            }
            else if(yellowOnTopCount == 1)
            {
                Corner yellowTop = null;
                for(Corner yC : yellowCorners)
                    if(yC.getPrimaryOrientation().equals(Vector.Z_AXIS))
                        yellowTop = yC;
                tempRight = yellowTop.getVector().subtract(yellowTop.getVector().getZCompVec()).rotate(Vector.Z_AXIS, Math.PI / -4).getUnit().scalar((1.0 - size) / 2);
            }
            else if(yellowOnTopCount == 2)
            {
                Corner yellowFront = null;
                for(Corner yC : yellowCorners)
                    if(yC.getSecondaryOrientation().equals(Vector.Z_AXIS))
                        yellowFront = yC;
                tempRight = yellowFront.getVector().subtract(yellowFront.getVector().getZCompVec()).rotate(Vector.Z_AXIS, Math.PI / -4).getUnit().scalar((1.0 - size) / 2);
            }
            addToMoves(tempRight, Math.PI / -2);
            addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
            addToMoves(tempRight, Math.PI / 2);
            addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
            addToMoves(tempRight, Math.PI / -2);
            addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
            addToMoves(tempRight, Math.PI / 2);
            yellowOnTopCount = 0;
            for(Corner yC : yellowCorners)
                if(yC.getPrimaryOrientation().equals(Vector.Z_AXIS))
                    yellowOnTopCount++;
        }
    }
    /**
     * Solves the yellow corners of the cube into their correct orientation relative to each other. It is assumed that the yellow center is up at this point.
     */
    private void yellowCornerLocations()
    {
        List<Corner> yellowCorners = new ArrayList<>();
        List<Vector> yellowCornerVectors = new ArrayList<>();
        for(Corner c : corners)
            if(c.getPrimary().equals(CubeColor.YELLOW))
            {
                yellowCorners.add(c);
                yellowCornerVectors.add(c.getVector());
            }
        Vector tempBack = null;
        Corner leftCorner = yellowCorners.get(yellowCornerVectors.indexOf((new Vector(1, 1, 1)).scalar((size - 1.0) / 2))), rightCorner;
        int checkedSides = 0, correctSides = 0;
        while(checkedSides < 4)
        {
            rightCorner = yellowCorners.get(yellowCornerVectors.indexOf(leftCorner.getVector().rotate(Vector.Z_AXIS, Math.PI / 2)));
            if(leftCorner.getTertiary().equals(rightCorner.getSecondary()))
            {
                tempBack = leftCorner.getTertiaryOrientation().scalar((size - 1.0) / 2);
                correctSides++;
            }
            leftCorner = rightCorner;
            checkedSides++;
        }
        if(tempBack == null)
        {
            addToMoves(Vector.X_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
            addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
            addToMoves(Vector.X_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
            addToMoves(Vector.Y_AXIS.scalar((size - 1.0) / 2), Math.PI);
            addToMoves(Vector.X_AXIS.scalar((size - 1.0) / 2), Math.PI / -2);
            addToMoves(Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
            addToMoves(Vector.X_AXIS.scalar((size - 1.0) / 2), Math.PI / 2);
            addToMoves(Vector.Y_AXIS.scalar((size - 1.0) / 2), Math.PI);
            addToMoves(Vector.X_AXIS.scalar((size - 1.0) / 2), Math.PI);
            for(int i = 0; i < 4; i++)
                yellowCornerVectors.set(i, yellowCorners.get(i).getVector());
            leftCorner = yellowCorners.get(yellowCornerVectors.indexOf((new Vector(1, 1, 1)).scalar((size - 1.0) / 2)));
            checkedSides = 0;
            while(checkedSides < 4)
            {
                rightCorner = yellowCorners.get(yellowCornerVectors.indexOf(leftCorner.getVector().rotate(Vector.Z_AXIS, Math.PI / 2)));
                if(leftCorner.getTertiary().equals(rightCorner.getSecondary()))
                    tempBack = leftCorner.getTertiaryOrientation().scalar((size - 1.0) / 2);
                leftCorner = rightCorner;
                checkedSides++;
            }
        }
        if(correctSides != 4)
        {
            Vector tempFront = tempBack.scalar(-1), tempRight = tempFront.rotate(Vector.Z_AXIS, Math.PI / 2);
            addToMoves(tempRight, Math.PI / 2);
            addToMoves(tempFront, Math.PI / -2);
            addToMoves(tempRight, Math.PI / 2);
            addToMoves(tempBack, Math.PI);
            addToMoves(tempRight, Math.PI / -2);
            addToMoves(tempFront, Math.PI / 2);
            addToMoves(tempRight, Math.PI / 2);
            addToMoves(tempBack, Math.PI);
            addToMoves(tempRight, Math.PI);
        }
        Vector oriVec = yellowCorners.get(0).getSecondaryOrientation(), faceVec = getFace(yellowCorners.get(0).getSecondary());
        int turnsFromFV = (int)ZMath.round(oriVec.angleBetween(faceVec) * 2 / Math.PI, 0);
        if(turnsFromFV == 2)
            addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
        else if(turnsFromFV == 1)
            addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), ZMath.round(oriVec.cross(faceVec).getUnit().getZ(), 0) * Math.PI / 2);
    }
    /**
     * Finishes the solve
     */
    private void yellowEdgeLocations()
    {
        Set<MasterEdge> yellowEdges = new HashSet<>();
        for(MasterEdge mE : masterEdges)
            if(mE.getPrimary().equals(CubeColor.YELLOW))
                yellowEdges.add(mE);
        Vector tempFront = null;
        CubeColor tempFrontColor = null;
        int correctCount = 0;
        for(MasterEdge yE : yellowEdges)
            if(yE.getSecondaryOrientation().equals(getFace(yE.getSecondary())))
            {
                tempFront = yE.getSecondaryOrientation().scalar((1.0 - size) / 2);
                tempFrontColor = yE.getSecondary().opposite();
                correctCount++;
            }
        if(correctCount == 0)
        {
            yELHelp(Vector.NEG_X_AXIS.scalar((size - 1.0) / 2), Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Vector.X_AXIS.scalar((size - 1.0) / 2), -1);
            correctCount = 0;
            for(MasterEdge yE : yellowEdges)
                if(yE.getSecondaryOrientation().equals(getFace(yE.getSecondary())))
                {
                    tempFront = yE.getSecondaryOrientation().scalar((1.0 - size) / 2);
                    tempFrontColor = yE.getSecondary().opposite();
                    correctCount++;
                }
        }
        if(correctCount == 0)
        {
            permutedParity();
            yELHelp(Vector.NEG_X_AXIS.scalar((size - 1.0) / 2), Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Vector.X_AXIS.scalar((size - 1.0) / 2), -1);
            correctCount = 1;
        }
        if(correctCount == 2)
        {
            Vector correctSum = Vector.ZERO_VEC;
            Vector correct = null;
            for(MasterEdge yE : yellowEdges)
                if(yE.getSecondaryOrientation().equals(getFace(yE.getSecondary())))
                {
                    correctSum = correctSum.add(yE.getSecondaryOrientation());
                    correct = yE.getSecondaryOrientation();
                }
            if(correctSum.equals(Vector.ZERO_VEC))
            {
                int turns = (int)ZMath.round(correct.angleBetween(Vector.X_AXIS) * 2 / Math.PI, 0);
                if(turns == 1)
                    addToOTrail(Vector.Z_AXIS, Math.PI / 2);
                permutedParity();
            }
            else
            {
                int turns = (int)ZMath.round(correct.angleBetween(new Vector(1, -1, 0)) * 2 / Math.PI, 0);
                if(turns == 2)
                    addToOTrail(Vector.Z_AXIS, Math.PI);
                else if(turns == 1)
                    addToOTrail(Vector.Z_AXIS, ZMath.round(correct.cross(new Vector(1, -1, 0)).getUnit().getZ(), 0) * Math.PI / 2);
                permutedParity();
                addToOTrail(Vector.Z_AXIS, Math.PI / 2);
                yELHelp(Vector.NEG_X_AXIS.scalar((size - 1.0) / 2), Vector.NEG_Y_AXIS.scalar((size - 1.0) / 2), Vector.X_AXIS.scalar((size - 1.0) / 2), -1);
            }
        }
        if(correctCount == 1)
        {
            Vector futureFrontEdge = null;
            for(MasterEdge yE : yellowEdges)
                if(yE.getSecondary().equals(tempFrontColor))
                    futureFrontEdge = yE.getSecondaryOrientation();
            yELHelp(tempFront.rotate(Vector.Z_AXIS, Math.PI / -2), tempFront, tempFront.rotate(Vector.Z_AXIS, Math.PI / 2), (int)ZMath.round(futureFrontEdge.cross(tempFront).getUnit().getZ(), 0));
        }
    }
    /**
     * Commutes 3 edges such that they rotate in the direction of dir around the positive z axis
     * @param tempLeft temporary left face (the orientation of the cube doesn't change)
     * @param tempFront temporary front face (the orientation of the cube doesn't change)
     * @param tempRight temporary right face (the orientation of the cube doesn't change)
     * @param dir direction that the three yellow edges will spin (+1 goes with positive theta, vice versa)
     */
    private void yELHelp(Vector tempLeft, Vector tempFront, Vector tempRight, int dir)
    {
        addToMoves(tempFront, Math.PI);
        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), dir * Math.PI / 2);
        addToMoves(tempLeft, Math.PI / -2);
        addToMoves(tempRight, Math.PI / 2);
        addToMoves(tempFront, Math.PI);
        addToMoves(tempLeft, Math.PI / 2);
        addToMoves(tempRight, Math.PI / -2);
        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), dir * Math.PI / 2);
        addToMoves(tempFront, Math.PI);
    }
    /**
     * Solves the Centers. It is assumed that the position is at default at this point.
     */
    private void permutedParity()
    {
        addToMoves(new Vector(0.5, 0, 0), Math.PI);
        addToMoves(Vector.X_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(new Vector(0.5, 0, 0), Math.PI);
        addToMoves(Vector.X_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(new Vector(0, 0, 0.5), Math.PI);
        addToMoves(new Vector(0.5, 0, 0), Math.PI);
        addToMoves(Vector.X_AXIS.scalar((size - 1.0) / 2), Math.PI);
        addToMoves(new Vector(0, 0, 0.5), Math.PI);
        addToMoves(Vector.Z_AXIS.scalar((size - 1.0) / 2), Math.PI);
    }
}