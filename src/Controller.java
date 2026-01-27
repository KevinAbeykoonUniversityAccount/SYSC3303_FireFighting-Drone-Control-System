
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * @author Aryan Singh 101299776
 */
public class Controller implements ActionListener, Serializable {
    private Scheduler model;
    private DroneSwarmFrame view; // Changed to concrete type for button access

    /**
     */
    public Controller(Scheduler model, DroneSwarmFrame view) {
        this.model = model;
        this.view = view;

    }

    /**
     * Responds to all button click events in the view.
     * Determines which button was pressed and delegates
     * the action to the appropriate handler.
     *
     * @param e the {@link ActionEvent} triggered by a button press
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();


    }
}
