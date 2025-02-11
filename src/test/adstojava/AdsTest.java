package adstojava;
import de.beckhoff.jni.tcads.*;

public class AdsTest {
    public static void main(String[] args) {
        try {
            AmsAddr addr = new AmsAddr();
            AdsCallDllFunction.adsPortOpen();
            AdsCallDllFunction.getLocalAddress(addr);
            System.out.println("TwinCAT 3 ADS Connection Established.");
            AdsCallDllFunction.adsPortClose();
        } catch (Exception e) {
            System.err.println("Error Connecting to TwinCAT 3: " + e.getMessage());
        }
    }
}
