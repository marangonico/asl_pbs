package VASL.build.module;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASL.build.module.map.ASLPieceMover;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.command.Command;
import VASSAL.command.NullCommand;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.util.Iterator;

public class ASLPBSChecker extends AbstractConfigurable implements DropTargetListener {

    private String currentMapName = "";

    @Override
    public void addTo(Buildable parent) {
        Command c = new NullCommand();
        c.append(new VASSAL.build.module.Chatter.DisplayText(
                GameModule.getGameModule().getChatter(),
                "* ASLPBSChecker caricato"
        ));
        c.execute();

        for (Iterator<Buildable> it = GameModule.getGameModule().getBuildables().iterator(); it.hasNext(); ) {
            Object o = it.next();
            if (o instanceof Map) {
                hookMap((Map) o);
            }
        }
    }

    private void hookMap(Map map) {
        if (map.getView() == null) {
            return;
        }

        currentMapName = map.getMapName();

        ASLPieceMover.AbstractDragHandler.makeDropTarget(
                map.getView(),
                DnDConstants.ACTION_MOVE,
                this
        );

        GameModule.getGameModule().getChatter().send(
                "* hook candidato: " + map.getMapName()
        );
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
        postDropAction(currentMapName);
    }

    public void postDropAction(final String mapName) {
        SwingUtilities.invokeLater(() ->
                GameModule.getGameModule().getChatter().send(
                        "*** post-drop su " + mapName
                )
        );
    }

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
    }

    @Override
    public void dragOver(DropTargetDragEvent dtde) {
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent dtde) {
    }

    @Override
    public void dragExit(DropTargetEvent dte) {
    }

    @Override
    public void removeFrom(Buildable parent) {
    }

    @Override
    public Class<?>[] getAttributeTypes() {
        return new Class<?>[] { String.class };
    }

    @Override
    public String[] getAttributeNames() {
        return new String[] { "Name" };
    }

    @Override
    public String[] getAttributeDescriptions() {
        return new String[] { "Name" };
    }

    @Override
    public String getAttributeValueString(String key) {
        return "ASL PBS Checker";
    }

    @Override
    public void setAttribute(String key, Object value) {
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