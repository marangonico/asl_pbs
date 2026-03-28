package VASL.build.module;

import VASL.LOS.Map.LOSResult;
import VASL.LOS.Map.Location;
import VASL.LOS.VASLGameInterface;
import VASL.build.module.ASLMap;
import VASL.counters.ASLProperties;
import VASL.counters.Concealable;
import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.Drawable;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.command.MovePiece;
import VASSAL.command.NullCommand;
import VASSAL.configure.NamedHotKeyConfigurer;
import VASSAL.counters.BasicPiece;
import VASSAL.counters.Decorator;
import VASSAL.counters.GamePiece;
import VASSAL.counters.PieceIterator;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.tools.NamedKeyStroke;

import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

import static VASSAL.build.GameModule.getGameModule;

public class ASLPBSChecker extends AbstractConfigurable
        implements CommandEncoder, GameComponent, Drawable, KeyListener {

    public static ASLPBSChecker aslPBSChecker;

    protected static ASLMap mainMap;
    protected static Map pbsMap;   // PBS Tracker Sheet  (≈ saslMap in SASLActivationChecker)
    protected static Map sacMap;   // Scenario Aid Card  (night rules)

    protected VASLGameInterface VASLGameInterface;

    ArrayList<GamePiece> movingFriendlyCounters = new ArrayList<>();
    ArrayList<GamePiece> movingSuspectCounters  = new ArrayList<>();

    final ArrayList<GamePiece> pieceList = new ArrayList<>();

    private String friendlyNationality  = "";
    private String alliedNationalityOne = "";
    private String alliedNationalityTwo = "";
    private String enemyNationality     = "";
    private int    nvr                  = -1;

    // -------------------------------------------------------------------------
    // Key bindings
    // -------------------------------------------------------------------------
    private static final String NAME                 = "Name";
    private static final String CLEAR_FLARES_KEY     = "ClearFlaresKey";
    private static final String CHECK_ACTIVATIONS_KEY = "CheckActivationsKey";

    private NamedKeyStroke clearFlaresKey      = new NamedKeyStroke("d85f6a40"); // CTL+ALT+X
    private NamedKeyStroke checkActivationsKey = new NamedKeyStroke("d85f6a41"); // CTL+ALT+S

    // -------------------------------------------------------------------------
    // Nationalities / activation ranges  (identical to SASLActivationChecker)
    // -------------------------------------------------------------------------
    private static final String[] nationalities = {
        "None",
        "Allied Minors",
        "Axis Minors",
        "China",
        "Finland",
        "France",
        "Germany",
        "Great Britain",
        "Italy",
        "Japan",
        "Partisan",
        "Russia",
        "US/USMC 44+",
        "USMC 41-43",
        "BCFK",
        "CPVA",
        "KPA",
        "OUNC",
        "South Korea",
    };

    private static final int[][] activationRangesInfantry = {
        // 0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16
        {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // None
        {  2,  2,  3,  4,  5,  5,  6,  7,  7,  7,  7,  8,  8,  8,  8,  8,  8 }, // Allied Minors
        {  2,  2,  3,  4,  5,  6,  6,  7,  7,  8,  8,  8,  8,  8,  8,  8,  8 }, // Axis Minors
        {  2,  2,  3,  4,  5,  6,  6,  7,  7,  8,  8,  8,  8,  8,  8,  8,  8 }, // China
        {  2,  2,  3,  4,  5,  6,  6,  7,  7,  8,  8,  8,  8,  8,  8,  8,  8 }, // Finland
        {  2,  2,  3,  4,  5,  5,  6,  7,  7,  7,  7,  8,  8,  8,  8,  8,  8 }, // France (Vichy)
        {  2,  2,  3,  4,  4,  5,  5,  6,  6,  7,  7,  7,  7,  8,  8,  8,  8 }, // Germany
        {  2,  2,  3,  4,  4,  5,  6,  6,  7,  7,  7,  8,  8,  8,  8,  8,  8 }, // Great Britain
        {  2,  2,  3,  4,  5,  6,  6,  7,  7,  8,  8,  8,  8,  8,  8,  8,  8 }, // Italy
        {  2,  2,  3,  4,  5,  6,  6,  7,  7,  8,  8,  8,  8,  8,  8,  8,  8 }, // Japan
        {  2,  2,  4,  5,  6,  7,  7,  8,  8,  8,  8,  8,  8,  8,  8,  8,  8 }, // Partisan
        {  2,  2,  3,  4,  5,  6,  6,  7,  7,  8,  8,  8,  8,  8,  8,  8,  8 }, // Russia
        {  2,  2,  3,  4,  4,  5,  5,  6,  6,  7,  7,  7,  7,  8,  8,  8,  8 }, // US/USMC 44+
        {  2,  2,  3,  4,  4,  5,  6,  6,  7,  7,  7,  8,  8,  8,  8,  8,  8 }, // USMC 41-43
        {  2,  2,  3,  4,  4,  5,  5,  6,  6,  7,  7,  8,  8,  8,  8,  8,  8 }, // BCFK
        {  2,  2,  3,  4,  5,  6,  7,  8,  8,  8,  8,  8,  8,  8,  8,  8,  8 }, // CPVA
        {  2,  2,  3,  4,  5,  6,  6,  7,  7,  8,  8,  8,  8,  8,  8,  8,  8 }, // KPA
        {  2,  2,  3,  4,  5,  6,  7,  8,  8,  8,  8,  8,  8,  8,  8,  8,  8 }, // OUNC
        {  2,  2,  3,  4,  5,  6,  6,  7,  7,  8,  8,  8,  8,  8,  8,  8,  8 }, // South Korea
    };

    private static final int[][] activationRangesVehicles = {
        // 0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16
        {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // None
        {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Allied Minors
        {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Axis Minors
        {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // China
        {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Finland
        {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // France (Vichy)
        {  2,  2,  3,  4,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Germany
        {  2,  2,  3,  4,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Great Britain
        {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Italy
        {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Japan
        {  2,  2,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Partisan
        {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Russia
        {  2,  2,  3,  4,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // US/USMC 44+
        {  2,  2,  3,  4,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // USMC 41-43
        {  2,  2,  3,  4,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // BCFK
        {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // CPVA
        {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // KPA
        {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // OUNC
        {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // South Korea
    };

    enum CounterType {
        NON_VEHICLE, TRACKED_VEHICLE, VEHICLE
    }

    enum VehicleStatus {
        BUTTONED_UP, CREW_EXPOSED, UNARMORED
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public static void setMap(ASLMap m) {
        mainMap = m;
    }

    public boolean isEnabled() {
        return isPBSExtensionPresent();
    }

    @Override
    public void addTo(Buildable parent) {
        if (parent instanceof ASLMap) {
            mainMap = (ASLMap) parent;
            mainMap.addDrawComponent(this);
            mainMap.getView().addKeyListener(this);
        }
        if (parent instanceof Map) {
            setMap((ASLMap) parent);
        }
        getGameModule().getGameState().addGameComponent(this);
        getGameModule().addCommandEncoder(this);

        Command c = new NullCommand();
        c.append(new VASSAL.build.module.Chatter.DisplayText(
                getGameModule().getChatter(),
                "* ASLPBSChecker caricato"
        ));
        c.execute();
    }

    @Override
    public void removeFrom(Buildable parent) {
        getGameModule().removeCommandEncoder(this);
    }

    // -------------------------------------------------------------------------
    // CommandEncoder  — hook per tutti i movimenti (drag e tastiera)
    // -------------------------------------------------------------------------

    @Override
    public String encode(Command c) {
        List<GamePiece> movedPieces = collectMovedPieces(c);
        if (!movedPieces.isEmpty()) {
            runUpdate(movedPieces);
        }
        return null;
    }

    @Override
    public Command decode(String s) {
        return null;
    }

    private List<GamePiece> collectMovedPieces(Command c) {
        List<GamePiece> result = new ArrayList<>();
        visitForMoves(c, result);
        return result;
    }

    private void visitForMoves(Command c, List<GamePiece> acc) {
        if (c == null || c.isNull()) return;
        if (c instanceof MovePiece) {
            String id = ((MovePiece) c).getId();
            for (GamePiece p : GameModule.getGameModule().getGameState().getAllPieces()) {
                if (id.equals(p.getId())) {
                    acc.add(p);
                    break;
                }
            }
        }
        for (Command sub : c.getSubCommands()) {
            visitForMoves(sub, acc);
        }
    }

    // -------------------------------------------------------------------------
    // runUpdate  (stessa firma di SASLActivationChecker)
    // -------------------------------------------------------------------------

    public void runUpdate(List<GamePiece> allDraggedPieces) {
        for (GamePiece piece : allDraggedPieces) {
            getGameModule().getChatter().send(
                "*** PBS mossa: " + Decorator.getInnermost(piece).getName()
                + " [" + piece.getId() + "]"
            );
        }
        if (mainMap != null && mainMap.getVASLMap() != null) {
            updateView((ArrayList<GamePiece>) allDraggedPieces);
        }
    }

    // -------------------------------------------------------------------------
    // Core logic  (identico a SASLActivationChecker salvo i riferimenti alla mappa)
    // -------------------------------------------------------------------------

    private void updateView(ArrayList<GamePiece> movedunits) {
        boolean clear = true;

        if ((pbsMap == null) && !isPBSExtensionPresent()) {
            return;
        }
        if (mainMap == null || mainMap.getVASLMap() == null) {
            return;
        }

        updateNationalities();
        updateNightStatus();

        VASLGameInterface = new VASLGameInterface(mainMap, mainMap.getVASLMap());
        VASLGameInterface.updatePieces();

        for (GamePiece piece : movedunits) {
            if (piece instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) piece).getPiecesIterator()); pi.hasMoreElements(); ) {
                    GamePiece currentPiece = pi.nextPiece();
                    if (canActivate(currentPiece)) {
                        clear = clearMovingCounters(clear);
                        movingFriendlyCounters.add(currentPiece);
                    } else if (canBeActivated(currentPiece)) {
                        clear = clearMovingCounters(clear);
                        movingSuspectCounters.add(currentPiece);
                    }
                }
            } else if (canActivate(piece)) {
                clear = clearMovingCounters(clear);
                movingFriendlyCounters.add(piece);
            } else if (canBeActivated(piece)) {
                clear = clearMovingCounters(clear);
                movingSuspectCounters.add(piece);
            }
        }

        generateFlareList();
    }

    private void generateFlareList() {
        pieceListClear();

        if (!movingFriendlyCounters.isEmpty() || !movingSuspectCounters.isEmpty()) {
            GamePiece[] allPieces = mainMap.getPieces();
            for (GamePiece piece : allPieces) {
                if (piece instanceof Stack) {
                    for (PieceIterator pi = new PieceIterator(((Stack) piece).getPiecesIterator()); pi.hasMoreElements(); ) {
                        testActivation(pi.nextPiece());
                    }
                } else {
                    testActivation(piece);
                }
            }
        }
    }

    private void testActivation(GamePiece piece) {
        int range;
        if (isOnboard(piece)) {
            if (!movingFriendlyCounters.isEmpty()) {
                for (GamePiece testPiece : movingFriendlyCounters) {
                    if (!(testPiece == piece) && !isFriendlyUnit(piece) && piece.getName().contains("Suspect")) {
                        CounterType counterType = getCounterType(testPiece);
                        if ((range = losRange(testPiece, piece, counterType)) >= 0) {
                            setPieceSpotted(testPiece, piece, range, true, counterType);
                        }
                    }
                }
            } else if (!movingSuspectCounters.isEmpty()) {
                for (GamePiece testPiece : movingSuspectCounters) {
                    if (!(testPiece == piece) && isFriendlyUnit(piece) && !piece.getName().contains("Suspect")) {
                        if ((range = losRange(testPiece, piece, CounterType.NON_VEHICLE)) >= 0) {
                            setPieceSpotted(piece, testPiece, range, false, CounterType.NON_VEHICLE);
                        }
                    }
                }
            }
        }
    }

    private int losRange(GamePiece viewed, GamePiece viewer, CounterType counterType) {
        int range = -1;
        if (viewed != null && viewer != null) {
            Location l1 = VASLGameInterface.getLocation(viewed);
            Location l2 = VASLGameInterface.getLocation(viewer);
            if (l1 != null && l2 != null) {
                LOSResult losResult = new LOSResult();
                mainMap.getVASLMap().LOS(l1, false, l2, false, losResult, VASLGameInterface);
                if (!losResult.isBlocked() && (losResult.getRange() <= 16)) {
                    int myNvr = nvr;
                    range = losResult.getRange();
                    if (counterType != CounterType.NON_VEHICLE) {
                        double temp = nvr;
                        if (counterType == CounterType.TRACKED_VEHICLE) {
                            temp = (myNvr == 0) ? 2.0 : (temp * 2.0);
                        } else if (counterType == CounterType.VEHICLE) {
                            temp = (myNvr == 0) ? 1.0 : (temp * 1.5);
                        }
                        myNvr = (int) Math.round(temp);
                    }
                    if (myNvr >= 0) {
                        if (illuminated(viewer)) {
                            if (!illuminated(viewed)) {
                                range = -1;
                            }
                        } else if (range > myNvr) {
                            if (!illuminated(viewed)) {
                                range = -1;
                            }
                        }
                    }
                }
            }
        }
        return range;
    }

    private void setPieceSpotted(GamePiece viewed, GamePiece viewer, int range, boolean friendly, CounterType counterType) {
        if (!Decorator.getInnermost(viewer).getName().isEmpty()) {
            if (viewer instanceof Decorator || viewer instanceof BasicPiece) {
                if (!pieceList.contains(viewer)) {
                    pieceList.add(viewer);
                    if (friendly) {
                        int rangeBracket;
                        switch (counterType) {
                            case VEHICLE:
                            case TRACKED_VEHICLE:
                                rangeBracket = getVehicleRangeBracket(viewed, viewer, range);
                                break;
                            default:
                                rangeBracket = activationRangesInfantry[getActivationRangesIndex(getSuspectNationality(viewer))][range];
                                break;
                        }
                        if (rangeBracket != 0) {
                            viewer.setProperty("ActivationFlag", 2);
                            viewer.setProperty("RangeBracket", rangeBracket);
                        }
                    } else {
                        viewer.setProperty("ActivationFlag", 2);
                    }
                }
            }
        }
    }

    private int getVehicleRangeBracket(GamePiece viewed, GamePiece viewer, int range) {
        int result = activationRangesVehicles[getActivationRangesIndex(getSuspectNationality(viewer))][range];
        VehicleStatus vehicleStatus = VehicleStatus.UNARMORED;
        if (viewed.getName().contains("BU")) {
            vehicleStatus = VehicleStatus.BUTTONED_UP;
        } else if (viewed.getName().contains("CE")) {
            vehicleStatus = VehicleStatus.CREW_EXPOSED;
        }
        if ((result > 2) && (VehicleStatus.BUTTONED_UP == vehicleStatus)) {
            result = 0;
        }
        return result;
    }

    private void pieceListClear() {
        if (!pieceList.isEmpty()) {
            for (GamePiece piece : pieceList) {
                piece.setProperty("ActivationFlag", 1);
                piece.setProperty("RangeBracket", 1);
            }
            pieceList.clear();
        }
    }

    private boolean clearMovingCounters(boolean clear) {
        if (clear) {
            movingFriendlyCounters.clear();
            movingSuspectCounters.clear();
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Piece classification
    // -------------------------------------------------------------------------

    private boolean canActivate(GamePiece piece) {
        if (!isOnboard(piece) || !isFriendlyUnit(piece)) return false;
        if (piece.getName().contains("?")) return true;
        if (!VASLGameInterface.isUnitCounter(piece)) return false;
        return !piece.getName().contains("broken")
            && !piece.getName().contains("Berserk")
            && !piece.getName().contains("Prisoner");
    }

    private boolean canBeActivated(GamePiece piece) {
        if (!isOnboard(piece) || isFriendlyUnit(piece)) return false;
        return piece.getName().contains("Suspect");
    }

    CounterType getCounterType(GamePiece piece) {
        if (isTrackedVehicle(piece)) return CounterType.TRACKED_VEHICLE;
        if (isVehicle(piece))        return CounterType.VEHICLE;
        return CounterType.NON_VEHICLE;
    }

    boolean isTrackedVehicle(GamePiece piece) {
        Object prop = piece.getProperty("vehicleType");
        if (prop != null) {
            switch (prop.toString()) {
                case "ft": case "ft_a": case "ht": return true;
                default: break;
            }
        }
        return false;
    }

    boolean isVehicle(GamePiece piece) {
        Object prop = piece.getProperty("vehicleType");
        if (prop != null) {
            switch (prop.toString()) {
                case "ac": case "hd": case "mc": case "sk": case "tr": case "tr_a": return true;
                default: break;
            }
        }
        return false;
    }

    private boolean isFriendlyUnit(GamePiece piece) {
        String nationality = getNationality(piece);
        return !nationality.isEmpty()
            && (nationality.equals(friendlyNationality)
             || nationality.equals(alliedNationalityOne)
             || nationality.equals(alliedNationalityTwo));
    }

    private boolean isOnboard(GamePiece piece) {
        return (VASLGameInterface.getLocation(piece) != null);
    }

    // -------------------------------------------------------------------------
    // Nationality helpers
    // -------------------------------------------------------------------------

    private String getNationality(GamePiece piece) {
        Concealable c = (Concealable) Decorator.getDecorator(piece, Concealable.class);
        if (c != null && c.isMaskable()) {
            Object prop = c.getProperty(ASLProperties.NATIONALITY);
            if (prop != null) return prop.toString();
        }
        return "";
    }

    private String getEnemyNationality(GamePiece piece) {
        Object prop = piece.getProperty("SuspectNationality");
        return (prop != null) ? prop.toString() : "";
    }

    private String getSuspectNationality(GamePiece piece) {
        String nationality = "None";
        Object prop = piece.getProperty("SuspectNationality");
        if (prop != null) nationality = prop.toString();
        if (nationality.equals("None") && !enemyNationality.isEmpty()) {
            nationality = enemyNationality;
        }
        return nationality;
    }

    private int getActivationRangesIndex(String nationality) {
        for (int i = 0; i < nationalities.length; i++) {
            if (nationalities[i].equals(nationality)) return i;
        }
        return 0;
    }

    private void updateNationalities() {
        if (pbsMap == null) return;
        GamePiece[] p = pbsMap.getPieces();
        friendlyNationality  = nationalityOfPieceInRegion(p, friendlyNationality,  "FrNat");
        alliedNationalityOne = nationalityOfPieceInRegion(p, alliedNationalityOne, "AlNat1");
        alliedNationalityTwo = nationalityOfPieceInRegion(p, alliedNationalityTwo, "AlNat2");
        nationalityOfEnemy(p);
    }

    private String checkPieceLocation(GamePiece piece, String regionName) {
        java.awt.Point currentPoint = piece.getPosition();
        VASSAL.build.module.map.boardPicker.board.Region region = pbsMap.findRegion(regionName);
        if ((region != null)
                && (currentPoint.getX() == region.getOrigin().getX())
                && (currentPoint.getY() == region.getOrigin().getY())) {
            return regionName.equals("Enemy") ? getEnemyNationality(piece) : getNationality(piece);
        }
        return "";
    }

    private String nationalityOfPieceInRegion(GamePiece[] p, String currentNationality, String regionName) {
        String result     = currentNationality;
        String nationality = "";

        outerloop:
        for (GamePiece aP : p) {
            if (aP instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) aP).getPiecesIterator()); pi.hasMoreElements(); ) {
                    String temp = checkPieceLocation(pi.nextPiece(), regionName);
                    if (!temp.isEmpty()) { nationality = temp; break outerloop; }
                }
            } else {
                String temp = checkPieceLocation(aP, regionName);
                if (!temp.isEmpty()) { nationality = temp; break; }
            }
        }

        if (currentNationality.isEmpty()) {
            if (!nationality.isEmpty()) result = nationality;
        } else {
            if (nationality.isEmpty()) result = "";
        }
        return result;
    }

    private void nationalityOfEnemy(GamePiece[] p) {
        String nationality = "None";

        outerloop:
        for (GamePiece aP : p) {
            if (aP instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) aP).getPiecesIterator()); pi.hasMoreElements(); ) {
                    String temp = checkPieceLocation(pi.nextPiece(), "Enemy");
                    if (!temp.isEmpty()) { nationality = temp; break outerloop; }
                }
            } else {
                String temp = checkPieceLocation(aP, "Enemy");
                if (!temp.isEmpty()) { nationality = temp; break; }
            }
        }

        if (enemyNationality.isEmpty()) {
            if (!nationality.contains("None")) enemyNationality = nationality;
        } else {
            if (nationality.contains("None")) enemyNationality = "";
        }
    }

    // -------------------------------------------------------------------------
    // Night / illumination
    // -------------------------------------------------------------------------

    private void updateNightStatus() {
        if ((sacMap != null) || isSacExtensionPresent()) {
            nvr = getNvr();
        }
    }

    private int getNvr() {
        if (sacMap == null) return -1;
        GamePiece[] p = sacMap.getPieces();
        GamePiece nvrPiece = null;

        outerloop:
        for (GamePiece aP : p) {
            if (aP instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) aP).getPiecesIterator()); pi.hasMoreElements(); ) {
                    GamePiece temp = pi.nextPiece();
                    if (temp.getName().contains("NVR")) { nvrPiece = temp; break outerloop; }
                }
            } else {
                if (aP.getName().contains("NVR")) { nvrPiece = aP; break; }
            }
        }

        if (nvrPiece == null) return -1;
        String name = (String) nvrPiece.getProperty(BasicPiece.BASIC_NAME);
        if (name.equals("NVR"))  return 1;
        if (name.equals("NVR7")) return 7;
        return Integer.parseInt(name.replace("NVR ", ""));
    }

    private boolean illuminated(GamePiece piece) {
        GamePiece[] allPieces = mainMap.getPieces();
        outerloop:
        for (GamePiece tempPiece : allPieces) {
            if (tempPiece instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) tempPiece).getPiecesIterator()); pi.hasMoreElements(); ) {
                    if (illuminates(piece, pi.nextPiece())) break outerloop;
                }
            } else {
                if (illuminates(piece, tempPiece)) return true;
            }
        }
        return false;
    }

    private boolean illuminates(GamePiece target, GamePiece possibleIlluminator) {
        if (!isOnboard(possibleIlluminator)) return false;
        if (possibleIlluminator.getName().contains("Starshell") && range(target, possibleIlluminator) <= 3)
            return true;
        if (possibleIlluminator.getName().contains("IR") && range(target, possibleIlluminator) <= 6)
            return true;
        return possibleIlluminator.getName().contains("Blaze")
            && range(target, possibleIlluminator) <= getBlazeIlluminationRange(possibleIlluminator);
    }

    private int getBlazeIlluminationRange(GamePiece blaze) {
        switch (blaze.getName()) {
            case "Blaze": case "1-level Blaze": return 2;
            case "2-level Blaze":               return 4;
            case "3-level Blaze":               return 6;
            case "4-level Blaze":               return 8;
            default:                            return 0;
        }
    }

    private int range(GamePiece piece1, GamePiece piece2) {
        if (piece1 == null || piece2 == null) return -1;
        Location l1 = VASLGameInterface.getLocation(piece1);
        Location l2 = VASLGameInterface.getLocation(piece2);
        if (l1 == null || l2 == null) return -1;
        LOSResult losResult = new LOSResult();
        mainMap.getVASLMap().LOS(l1, false, l2, false, losResult, VASLGameInterface);
        return losResult.getRange();
    }

    // -------------------------------------------------------------------------
    // Extension detection
    // -------------------------------------------------------------------------

    private boolean isPBSExtensionPresent() {
        for (Buildable b : getGameModule().getBuildables()) {
            if (b instanceof Map && ((Map) b).getMapName().equals("PBS Tracker Sheet")) {
                pbsMap = (Map) b;
                return true;
            }
        }
        return false;
    }

    private boolean isSacExtensionPresent() {
        for (Buildable b : getGameModule().getBuildables()) {
            if (b instanceof Map && ((Map) b).getMapName().equals("Scenario Aid Card")) {
                sacMap = (Map) b;
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Selected pieces helper
    // -------------------------------------------------------------------------

    private ArrayList<GamePiece> getSelectedPieces() {
        ArrayList<GamePiece> temp = new ArrayList<>();
        for (GamePiece piece : GameModule.getGameModule().getGameState().getAllPieces()) {
            if (isSelected(piece)) temp.add(piece);
        }
        return temp;
    }

    private boolean isSelected(GamePiece p) {
        return Boolean.TRUE.equals(p.getProperty(Properties.SELECTED))
            && p.getId() != null && !"".equals(p.getId());
    }

    // -------------------------------------------------------------------------
    // GameComponent
    // -------------------------------------------------------------------------

    @Override
    public void setup(boolean gameStarting) {
    }

    @Override
    public Command getRestoreCommand() {
        return new NullCommand();
    }

    // -------------------------------------------------------------------------
    // Drawable
    // -------------------------------------------------------------------------

    @Override
    public void draw(Graphics graphics, Map map) {
    }

    @Override
    public boolean drawAboveCounters() {
        return true;
    }

    // -------------------------------------------------------------------------
    // KeyListener
    // -------------------------------------------------------------------------

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (clearFlaresKey.equals(NamedKeyStroke.of(e))) {
            movingFriendlyCounters.clear();
            movingSuspectCounters.clear();
            pieceListClear();
            e.consume();
        } else if (checkActivationsKey.equals(NamedKeyStroke.of(e))) {
            pieceListClear();
            ArrayList<GamePiece> selectedPieces = getSelectedPieces();
            if (!selectedPieces.isEmpty()) {
                updateView(selectedPieces);
            }
            e.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    // -------------------------------------------------------------------------
    // AbstractConfigurable boilerplate
    // -------------------------------------------------------------------------

    @Override
    public Class<?>[] getAttributeTypes() {
        return new Class<?>[] { String.class, NamedKeyStroke.class, NamedKeyStroke.class };
    }

    @Override
    public String[] getAttributeNames() {
        return new String[] { NAME, CLEAR_FLARES_KEY, CHECK_ACTIVATIONS_KEY };
    }

    @Override
    public String[] getAttributeDescriptions() {
        return new String[] { "ASLPBSChecker", "Clear Flares Key", "Check Activations Key" };
    }

    @Override
    public String getAttributeValueString(String key) {
        if (NAME.equals(key))                 return getConfigureName();
        if (CLEAR_FLARES_KEY.equals(key))     return NamedHotKeyConfigurer.encode(clearFlaresKey);
        if (CHECK_ACTIVATIONS_KEY.equals(key)) return NamedHotKeyConfigurer.encode(checkActivationsKey);
        return null;
    }

    @Override
    public void setAttribute(String key, Object value) {
        if (NAME.equals(key)) {
            if (value instanceof String) name = (String) value;
        } else if (CLEAR_FLARES_KEY.equals(key)) {
            if (value instanceof String) value = NamedHotKeyConfigurer.decode((String) value);
            clearFlaresKey = (NamedKeyStroke) value;
        } else if (CHECK_ACTIVATIONS_KEY.equals(key)) {
            if (value instanceof String) value = NamedHotKeyConfigurer.decode((String) value);
            checkActivationsKey = (NamedKeyStroke) value;
        }
    }

    @Override
    public HelpFile getHelpFile() {
        return null;
    }

    @Override
    public Class<?>[] getAllowableConfigureComponents() {
        return new Class[0];
    }
}
