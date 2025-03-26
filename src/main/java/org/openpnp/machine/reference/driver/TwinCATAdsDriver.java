package org.openpnp.machine.reference.driver;

import java.util.ArrayList;
import java.util.List;

import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.model.AxesLocation;
import org.openpnp.model.LengthUnit;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Axis.Type;
import org.openpnp.spi.ControllerAxis;
import org.openpnp.spi.Machine;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.MotionPlanner.CompletionType;
import org.openpnp.model.Motion.MoveToCommand;
import org.pmw.tinylog.Logger;
import org.simpleframework.xml.Attribute;

import de.beckhoff.jni.tcads.AdsCallDllFunction;
import de.beckhoff.jni.tcads.AmsAddr;

public class TwinCATAdsDriver extends AbstractReferenceDriver {
    private static final int TIMEOUT = 5000;
    private static final int AMS_PORT = 851;

    private AmsAddr amsAddr;
    private boolean motionPending = false;

    @Attribute(required = false)
    boolean usingLetterVariables = true;

    public TwinCATAdsDriver() {
        amsAddr = new AmsAddr();
    }

    public void connect() throws Exception {
        long result = AdsCallDllFunction.adsPortOpen(); // Use static method
        if (result == 0) {
            Logger.info("TwinCAT ADS Port opened successfully.");
        } else {
            throw new Exception("Failed to open TwinCAT ADS Port. Error code: " + result);
        }

        result = AdsCallDllFunction.getLocalAddress(amsAddr); // Use static method
        if (result == 0) {
            amsAddr.setPort(AMS_PORT);
            Logger.info("TwinCAT ADS Connected to AMS Port: {}", AMS_PORT);
        } else {
            throw new Exception("Failed to get local AMS address. Error code: " + result);
        }
    }

    public void disconnect() {
        AdsCallDllFunction.adsPortClose(); // Use static method
        Logger.info("TwinCAT ADS Port closed.");
    }

    @Override
    public void home(Machine machine) throws Exception {
        Logger.info("Homing machine...");
        writeSymbol("MAIN.bHome", true);
        motionPending = true;
    }

    @Override
    public void moveTo(HeadMountable hm, MoveToCommand move) throws Exception {
        AxesLocation target = move.getLocation1();

        // Retrieve coordinates using the Axis object
        double x = target.getCoordinate(hm.getAxisX(), LengthUnit.Millimeters);
        double y = target.getCoordinate(hm.getAxisY(), LengthUnit.Millimeters);
        double z = target.getCoordinate(hm.getAxisZ(), LengthUnit.Millimeters);
        double r = target.getCoordinate(hm.getAxisRotation());

        Logger.info("Moving to X: {} Y: {} Z: {} R: {}", x, y, z, r);

        // Write the target positions to the ADS server
        writeSymbol("MAIN.xTargetPosition", x);
        writeSymbol("MAIN.yTargetPosition", y);
        writeSymbol("MAIN.zTargetPosition", z);
        writeSymbol("MAIN.rTargetPosition", r);

        // Trigger the move
        writeSymbol("MAIN.bMove", true);
        motionPending = true;
    }

    @Override
    public void waitForCompletion(HeadMountable hm, CompletionType completionType) throws Exception {
        boolean isMoving;
        do {
            isMoving = (boolean) readSymbol("MAIN.bMoving");
            Thread.sleep(100);
        } while (isMoving);
        motionPending = false;
    }

    @Override
    public void actuate(Actuator actuator, boolean on) throws Exception {
        Logger.info("Actuating {} to {}", actuator.getName(), on);
        writeSymbol("MAIN.bActuator", on);
    }

    @Override
    public void actuate(Actuator actuator, double value) throws Exception {
        Logger.info("Actuating {} to value {}", actuator.getName(), value);
        writeSymbol("MAIN.dActuator", value);
    }

    @Override
    public boolean isMotionPending() {
        return motionPending;
    }

    @Override
    public LengthUnit getUnits() {
        return LengthUnit.Millimeters;
    }

    @Override
    public boolean isUsingLetterVariables() {
        return true;
    }

    public void setUsingLetterVariables(boolean usingLetterVariables) {
        Object oldValue = this.usingLetterVariables;
        this.usingLetterVariables = usingLetterVariables;
        firePropertyChange("usingLetterVariables", oldValue, usingLetterVariables);
    }

    public List<String> getAxisVariables(ReferenceMachine machine) {
        List<String> variables = new ArrayList<>();
        if (usingLetterVariables) {
            for (ControllerAxis axis : getAxes(machine)) {
                String letter = axis.getLetter(); 
                if (letter != null && !letter.isEmpty()) {
                    variables.add(letter);
                }
            }
        }
        else {
            for (Type type : Type.values()) {
                variables.add(type.toString());
            }
        }
        return variables;
    }

    @Override
    public void setGlobalOffsets(Machine machine, AxesLocation axesLocation) throws Exception {
        Logger.info("Setting global offsets...");

        // Iterate over all axes and set their global offsets
        for (String variable : getAxisVariables((ReferenceMachine) machine)) {
            ControllerAxis axis = axesLocation.getAxisByVariable(this, variable);
            if (axis != null) {
                double offset;
                if (axis.getType() == Type.Rotation) {
                    // Handle rotation offsets without converting to driver units
                    offset = axesLocation.getCoordinate(axis);
                } else {
                    // Convert linear offsets to the appropriate units
                    offset = axesLocation.getCoordinate(axis, getUnits());
                }

                // Write the offset to the ADS server
                String symbolName = "MAIN." + variable.toLowerCase() + "GlobalOffset";
                writeSymbol(symbolName, offset);

                Logger.info("Set global offset for {}: {}", variable, offset);
            } else {
                Logger.warn("Axis variable {} is missing in the machine configuration.", variable);
            }
        }
    }

    @Override
    public AxesLocation getReportedLocation(long timeout) throws Exception {
        double x = (double) readSymbol("MAIN.xActualPosition");
        double y = (double) readSymbol("MAIN.yActualPosition");
        double z = (double) readSymbol("MAIN.zActualPosition");
        double r = (double) readSymbol("MAIN.rActualPosition");

        // Create an AxesLocation and explicitly add each axis and its coordinate
        AxesLocation location = new AxesLocation();
        location = location.put(new AxesLocation(getAxis(Type.X), x));
        location = location.put(new AxesLocation(getAxis(Type.Y), y));
        location = location.put(new AxesLocation(getAxis(Type.Z), z));
        location = location.put(new AxesLocation(getAxis(Type.Rotation), r));

        return location;
    }

    @Override
    public void setEnabled(boolean enabled) throws Exception {
        Logger.info("Setting machine enabled: {}", enabled);
        writeSymbol("MAIN.bEnable", enabled);
    }

    private void writeSymbol(String symbolName, Object value) throws Exception {
        int result = AdsCallDllFunction.adsSyncWriteReq(amsAddr, AdsCallDllFunction.ADSIGRP_SYM_VALBYHND, 0, value); // Use static method
        if (result != 0) {
            throw new Exception("Failed to write symbol: " + symbolName + ". Error code: " + result);
        }
    }

    private Object readSymbol(String symbolName) throws Exception {
        Object value = new Object();
        int result = AdsCallDllFunction.adsSyncReadReq(amsAddr, AdsCallDllFunction.ADSIGRP_SYM_VALBYHND, 0, value); // Use static method
        if (result != 0) {
            throw new Exception("Failed to read symbol: " + symbolName + ". Error code: " + result);
        }
        return value;
    }
}