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
import VASSAL.command.ChangePiece;
import VASSAL.command.MovePiece;
import VASSAL.command.NullCommand;
import VASSAL.configure.NamedHotKeyConfigurer;
import VASSAL.counters.BasicPiece;
import VASSAL.counters.Decorator;
import VASSAL.counters.GamePiece;
import VASSAL.counters.Labeler;
import VASSAL.counters.PieceIterator;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.tools.NamedKeyStroke;

import javax.swing.JButton;
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
    protected static Map pbsSidesMap;   // PBS Sides  (≈ saslMap in SASLActivationChecker)
    protected static Map sacMap;        // Scenario Aid Card  (night rules)

    protected VASLGameInterface VASLGameInterface;

    ArrayList<GamePiece> movingFactionA = new ArrayList<>();
    ArrayList<GamePiece> movingFactionB = new ArrayList<>();

    final ArrayList<GamePiece> pieceList = new ArrayList<>();

    private static final java.util.regex.Pattern ACTIVATION_LABEL_PATTERN =
        java.util.regex.Pattern.compile("\\n?\\*ACTIVATE\\? \\(R=\\d+\\)\\*");

    private String factionANat1 = "";  // da regione NatA1 su PBS Sides
    private String factionANat2 = "";  // da regione NatA2 su PBS Sides
    private String factionBNat1 = "";  // da regione NatB1 su PBS Sides
    private String factionBNat2 = "";  // da regione NatB2 su PBS Sides
    private int    nvr          = -1;

    // -------------------------------------------------------------------------
    // Key bindings
    // -------------------------------------------------------------------------
    private static final String NAME                 = "Name";
    private static final String CLEAR_FLARES_KEY     = "ClearFlaresKey";
    private static final String CHECK_ACTIVATIONS_KEY = "CheckActivationsKey";

    private NamedKeyStroke clearFlaresKey      = new NamedKeyStroke("d85f6a40"); // CTL+ALT+X
    private NamedKeyStroke checkActivationsKey = new NamedKeyStroke("d85f6a41"); // CTL+ALT+S

    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public static void setMap(ASLMap m) {
        mainMap = m;
    }

//    public boolean isEnabled() {
//        getGameModule().getChatter().send(
//                "*** PBS isEnabled(): checking PBS extension presence"
//        );
//        return isPBSExtensionPresent();
//    }

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

        JButton resetButton = new JButton("PBS Reset");
        resetButton.setToolTipText("Rimuove i marker *ACTIVATE?* dalle pedine");
        resetButton.addActionListener(e -> pieceListClear());
        getGameModule().getToolBar().add(resetButton);

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
        String id = null;
        if (c instanceof MovePiece) {
            id = ((MovePiece) c).getId();
        } else if (c instanceof ChangePiece) {
            id = ((ChangePiece) c).getId();
        }
        if (id != null) {
            for (GamePiece p : GameModule.getGameModule().getGameState().getAllPieces()) {
                if (id.equals(p.getId()) && !acc.contains(p)) {
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

        if ((pbsSidesMap == null) && !isPBSExtensionPresent()) {
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
                    debugCanActivate(currentPiece);
                    if (canActivate(currentPiece)) {
                        clear = clearMovingCounters(clear);
                        if (isFactionA(currentPiece)) movingFactionA.add(currentPiece);
                        else                          movingFactionB.add(currentPiece);
                    }
                }
            } else {
                debugCanActivate(piece);
                if (canActivate(piece)) {
                    clear = clearMovingCounters(clear);
                    if (isFactionA(piece)) movingFactionA.add(piece);
                    else                   movingFactionB.add(piece);
                }
            }
        }
        getGameModule().getChatter().send(
            "*** PBS updateView: movingA=" + movingFactionA.size()
            + " movingB=" + movingFactionB.size()
        );

        generateFlareList();
    }

    private void generateFlareList() {
        pieceListClear();

        if (!movingFactionA.isEmpty() || !movingFactionB.isEmpty()) {
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
        if (!isOnboard(piece)) return;

        // Faction A moved → spot faction B pieces in LOS
        if (!movingFactionA.isEmpty() && isFactionB(piece)) {
            for (GamePiece mover : movingFactionA) {
                if (mover == piece) continue;
                int range = losRange(mover, piece);
                if (range >= 0) setPieceSpotted(piece, range);
            }
        }

        // Faction B moved → spot faction A pieces in LOS
        if (!movingFactionB.isEmpty() && isFactionA(piece)) {
            for (GamePiece mover : movingFactionB) {
                if (mover == piece) continue;
                int range = losRange(mover, piece);
                if (range >= 0) setPieceSpotted(piece, range);
            }
        }
    }

    private int losRange(GamePiece mover, GamePiece target) {
        if (mover == null || target == null) return -1;
        Location l1 = VASLGameInterface.getLocation(mover);
        Location l2 = VASLGameInterface.getLocation(target);
        if (l1 == null || l2 == null) return -1;
        LOSResult losResult = new LOSResult();
        mainMap.getVASLMap().LOS(l1, false, l2, false, losResult, VASLGameInterface);
        if (losResult.isBlocked()) return -1;
        int range = losResult.getRange();
        if (nvr >= 0) {
            if (illuminated(target)) {
                if (!illuminated(mover)) return -1;
            } else if (range > nvr && !illuminated(mover)) {
                return -1;
            }
        }
        return range;
    }

    // viewer = la pedina della fazione opposta che potrebbe reagire
    private void setPieceSpotted(GamePiece viewer, int range) {
        if (Decorator.getInnermost(viewer).getName().isEmpty()) return;
        if (!(viewer instanceof Decorator || viewer instanceof BasicPiece)) return;
        if (pieceList.contains(viewer)) return;

        pieceList.add(viewer);
        Labeler labeler = (Labeler) Decorator.getDecorator(viewer, Labeler.class);
        if (labeler != null) {
            String current = labeler.getLabel();
            if (current == null) current = "";
            String activation = "*ACTIVATE? (R=" + range + ")*";
            labeler.setLabel(current.isEmpty() ? activation : current + "\n" + activation);
        }
    }

    private void pieceListClear() {
        if (mainMap == null) return;
        for (GamePiece piece : mainMap.getPieces()) {
            if (piece instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) piece).getPiecesIterator()); pi.hasMoreElements(); ) {
                    stripActivationLabel(pi.nextPiece());
                }
            } else {
                stripActivationLabel(piece);
            }
        }
        pieceList.clear();
        mainMap.repaint();
    }

    private void stripActivationLabel(GamePiece piece) {
        Labeler labeler = (Labeler) Decorator.getDecorator(piece, Labeler.class);
        if (labeler == null) return;
        String current = labeler.getLabel();
        if (current == null || !current.contains("*ACTIVATE?")) return;
        labeler.setLabel(ACTIVATION_LABEL_PATTERN.matcher(current).replaceAll(""));
    }

    private boolean clearMovingCounters(boolean clear) {
        if (clear) {
            movingFactionA.clear();
            movingFactionB.clear();
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Piece classification
    // -------------------------------------------------------------------------

    private void debugCanActivate(GamePiece piece) {
        String name = Decorator.getInnermost(piece).getName();
        String nat  = getNationality(piece);
        boolean onboard  = isOnboard(piece);
        boolean isA      = isFactionA(piece);
        boolean isB      = isFactionB(piece);
        boolean isUnit   = onboard && VASLGameInterface.isUnitCounter(piece);
        getGameModule().getChatter().send(
            "*** canActivate '" + name + "'"
            + " nat='" + nat + "'"
            + " onboard=" + onboard
            + " isA=" + isA + " isB=" + isB
            + " isUnit=" + isUnit
            + " fA1='" + factionANat1 + "' fA2='" + factionANat2
            + "' fB1='" + factionBNat1 + "' fB2='" + factionBNat2 + "'"
        );
    }

    private boolean canActivate(GamePiece piece) {
        if (!isOnboard(piece)) return false;
        if (!isFactionA(piece) && !isFactionB(piece)) return false;
        if (piece.getName().contains("?")) return true;
        if (!VASLGameInterface.isUnitCounter(piece)) return false;
        return !piece.getName().contains("broken")
            && !piece.getName().contains("Berserk")
            && !piece.getName().contains("Prisoner");
    }

    private boolean isFactionA(GamePiece piece) {
        String nat = getNationality(piece);
        return !nat.isEmpty() && (nat.equals(factionANat1) || nat.equals(factionANat2));
    }

    private boolean isFactionB(GamePiece piece) {
        String nat = getNationality(piece);
        return !nat.isEmpty() && (nat.equals(factionBNat1) || nat.equals(factionBNat2));
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

    private void updateNationalities() {
        if (pbsSidesMap == null) return;
        GamePiece[] p = pbsSidesMap.getPieces();
        getGameModule().getChatter().send(
            "*** PBS updateNationalities(): pbsMap ha " + p.length + " pezzi"
        );
        for (GamePiece gp : p) {
            java.awt.Point pos = gp.getPosition();
            String pieceName = Decorator.getInnermost(gp).getName();
            getGameModule().getChatter().send(
                "***   pezzo: '" + pieceName + "' pos=(" + (int)pos.getX() + "," + (int)pos.getY() + ")"
            );
        }
        for (String rn : new String[]{"NatA1","NatA2","NatB1","NatB2"}) {
            VASSAL.build.module.map.boardPicker.board.Region r = pbsSidesMap.findRegion(rn);
            if (r == null) {
                getGameModule().getChatter().send("***   regione '" + rn + "': NOT FOUND");
            } else {
                getGameModule().getChatter().send(
                    "***   regione '" + rn + "' origin=("
                    + (int)r.getOrigin().getX() + "," + (int)r.getOrigin().getY() + ")"
                );
            }
        }
        factionANat1 = nationalityOfPieceInRegion(p, factionANat1, "NatA1");
        factionANat2 = nationalityOfPieceInRegion(p, factionANat2, "NatA2");
        factionBNat1 = nationalityOfPieceInRegion(p, factionBNat1, "NatB1");
        factionBNat2 = nationalityOfPieceInRegion(p, factionBNat2, "NatB2");
        getGameModule().getChatter().send(
            "*** PBS updateNationalities(): A1='" + factionANat1 + "' A2='" + factionANat2
            + "' B1='" + factionBNat1 + "' B2='" + factionBNat2 + "'"
        );
    }

    private String checkPieceLocation(GamePiece piece, String regionName) {
        java.awt.Point currentPoint = piece.getPosition();
        VASSAL.build.module.map.boardPicker.board.Region region = pbsSidesMap.findRegion(regionName);
        if (region == null) return "";
        java.awt.Point origin = region.getOrigin();
        boolean match = (currentPoint.getX() == origin.getX()) && (currentPoint.getY() == origin.getY());
        getGameModule().getChatter().send(
            "***   checkPieceLocation: '" + Decorator.getInnermost(piece).getName()
            + "' pos=(" + (int)currentPoint.getX() + "," + (int)currentPoint.getY() + ")"
            + " region='" + regionName + "' origin=(" + (int)origin.getX() + "," + (int)origin.getY() + ")"
            + " match=" + match
        );
        if (match) {
            return getNationality(piece);
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
        getGameModule().getChatter().send(
                "*** PBS isPBSExtensionPresent(): searching for PBS Sides.."
        );
        for (Buildable b : getGameModule().getBuildables()) {
            if (b instanceof Map && ((Map) b).getMapName().equals("PBS Sides")) {
                pbsSidesMap = (Map) b;
                getGameModule().getChatter().send(
                        "*** PBS isPBSExtensionPresent(): found PBS Sides"
                );
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
        if (!gameStarting) return;

        // pbsMap: cerca la mappa "PBS Sides" tra tutte le mappe caricate
        isPBSExtensionPresent();

        // mainMap: se non già impostato via addTo(), cercalo tra tutte le Map attive
        if (mainMap == null) {
            for (Map m : Map.getMapList()) {
                if (m instanceof ASLMap) {
                    mainMap = (ASLMap) m;
                    mainMap.addDrawComponent(this);
                    mainMap.getView().addKeyListener(this);
                    break;
                }
            }
        }

        getGameModule().getChatter().send(
            "*** PBS setup(): mainMap=" + (mainMap != null ? mainMap.getMapName() : "null")
            + " pbsMap=" + (pbsSidesMap != null ? pbsSidesMap.getMapName() : "null")
        );
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
            movingFactionA.clear();
            movingFactionB.clear();
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
