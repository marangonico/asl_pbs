package VASL.build.module;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.command.MovePiece;
import VASSAL.command.NullCommand;

import javax.swing.SwingUtilities;

/**
 * ASLPBSChecker intercetta tutti i movimenti di pedine (drag&drop e tastiera)
 * registrandosi come CommandEncoder sul GameModule.
 *
 * Il flusso VASSAL per ogni movimento è:
 *   movePieces() → Command → GameModule.sendAndLog()
 *     → server.sendToOthers() → encodeCommand() → encode() [qui]
 *     → logger.log()         → encodeCommand() → encode() [qui]
 *
 * encode() restituisce null: non consumiamo il comando, lasciamo che
 * BasicCommandEncoder faccia la serializzazione reale.
 */
public class ASLPBSChecker extends AbstractConfigurable implements CommandEncoder {

    @Override
    public void addTo(Buildable parent) {
        Command c = new NullCommand();
        c.append(new VASSAL.build.module.Chatter.DisplayText(
                GameModule.getGameModule().getChatter(),
                "* ASLPBSChecker caricato"
        ));
        c.execute();

        GameModule.getGameModule().addCommandEncoder(this);
    }

    @Override
    public void removeFrom(Buildable parent) {
        GameModule.getGameModule().removeCommandEncoder(this);
    }

    // -------------------------------------------------------------------------
    // CommandEncoder
    // -------------------------------------------------------------------------

    /**
     * Visitiamo l'albero dei comandi cercando MovePiece.
     * Ritorniamo sempre null: non siamo noi a serializzare.
     */
    @Override
    public String encode(Command c) {
        visitCommands(c);
        return null;
    }

    @Override
    public Command decode(String s) {
        return null;
    }

    private void visitCommands(Command c) {
        if (c == null || c.isNull()) {
            return;
        }
        if (c instanceof MovePiece) {
            onPieceMoved((MovePiece) c);
        }
        for (Command sub : c.getSubCommands()) {
            visitCommands(sub);
        }
    }

    private void onPieceMoved(final MovePiece move) {
        final String mapId = move.getNewMapId();
        SwingUtilities.invokeLater(() ->
                GameModule.getGameModule().getChatter().send(
                        "*** post-move su " + mapId
                )
        );
    }

    // -------------------------------------------------------------------------
    // AbstractConfigurable boilerplate
    // -------------------------------------------------------------------------

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
