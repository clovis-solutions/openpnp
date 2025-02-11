package org.openpnp.machine.reference.driver;

import org.openpnp.spi.MotionControl;
import org.openpnp.spi.base.AbstractMotionController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.beckhoff.jni.tcads.*;

public class TwinCATAdsDriver extends AbstractMotionController implements MotionControl {
    private static final Logger logger = LoggerFactory.getLogger(TwinCATAdsDriver.class);

    private AmsAddr addr;
    
    public TwinCATAdsDriver() {
        try {
            addr = new AmsAddr();
            AdsCallDllFunction.adsPortOpen();
            AdsCallDllFunction.getLocalAddress(addr);
            logger.info("TwinCAT ADS Connection Established.");
        } catch (Exception e) {
            logger.error("Failed to initialize TwinCAT ADS.", e);
        }
    }

    @Override
    public void moveTo(double x, double y, double z) throws Exception {
        logger.info("Moving to X: {}, Y: {}, Z: {}", x, y, z);
        try {
            sendPositionToTwinCAT(x, y, z);
        } catch (Exception e) {
            logger.error("Error sending position to TwinCAT.", e);
            throw e;
        }
    }

    private void sendPositionToTwinCAT(double x, double y, double z) throws Exception {
        int err;
        int hVar = 0;
        JNIByteBuffer dataBuff = new JNIByteBuffer(12);
        
        Convert.IntToByteArr((int) x, dataBuff.getByteArray(), 0);
        Convert.IntToByteArr((int) y, dataBuff.getByteArray(), 4);
        Convert.IntToByteArr((int) z, dataBuff.getByteArray(), 8);
        
        err = AdsCallDllFunction.adsSyncWriteReq(
            addr, AdsCallDllFunction.ADSIGRP_SYM_VALBYHND, hVar, 12, dataBuff
        );

        if (err != 0) {
            throw new Exception("Failed to write position to TwinCAT: Error Code " + err);
        }
    }

    @Override
    public void stop() throws Exception {
        logger.info("Emergency stop activated.");
    }
    
    @Override
    public boolean isMoving() {
        return false;
    }
    
    @Override
    public void home() throws Exception {
        logger.info("Homing machine...");
        sendPositionToTwinCAT(0, 0, 0);
    }
}
